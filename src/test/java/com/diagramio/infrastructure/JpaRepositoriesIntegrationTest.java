package com.diagramio.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.diagramio.domain.access.ShareRole;
import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.diagram.DiagramType;
import com.diagramio.domain.exception.ConflictException;
import com.diagramio.domain.exception.OptimisticLockConflictException;
import com.diagramio.domain.share.DiagramShare;
import com.diagramio.domain.version.DiagramVersion;
import com.diagramio.infrastructure.diagram.DiagramMapper;
import com.diagramio.infrastructure.diagram.DiagramRepositoryAdapter;
import com.diagramio.infrastructure.share.DiagramShareMapper;
import com.diagramio.infrastructure.share.DiagramShareRepositoryAdapter;
import com.diagramio.infrastructure.version.DiagramVersionRepositoryAdapter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Chạy Flyway + Hibernate (ddl-auto: validate) trên PostgreSQL thật.
 * Bỏ qua khi máy không có Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DiagramRepositoryAdapter.class, DiagramMapper.class, DiagramVersionRepositoryAdapter.class,
        DiagramShareRepositoryAdapter.class, DiagramShareMapper.class})
@Testcontainers(disabledWithoutDocker = true)
class JpaRepositoriesIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    /** Người dùng mẫu được seed trong V2. */
    private static final UUID SEED_USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String DATA = "{\"nodes\": [{\"id\": \"n1\"}], \"edges\": []}";

    @Autowired
    private DiagramRepositoryAdapter diagramRepository;

    @Autowired
    private DiagramVersionRepositoryAdapter versionRepository;

    @Autowired
    private DiagramShareRepositoryAdapter shareRepository;

    @Test
    void savesAndReadsSnapshotsNewestFirst() {
        Diagram diagram = newDiagram();
        versionRepository.save(snapshot(diagram.getId(), 1));
        DiagramVersion second = versionRepository.save(snapshot(diagram.getId(), 2));

        List<DiagramVersion> versions = versionRepository.findByDiagramId(diagram.getId());

        assertThat(versions).extracting(DiagramVersion::versionNumber).containsExactly(2, 1);
        assertThat(versionRepository.maxVersionNumber(diagram.getId())).isEqualTo(2);
        assertThat(versionRepository.findByIdAndDiagramId(second.id(), diagram.getId()))
                .get().satisfies(v -> {
                    assertThat(v.data()).contains("\"n1\"");
                    assertThat(v.createdBy()).isEqualTo(SEED_USER);
                });
    }

    @Test
    void maxVersionNumberIsZeroWithoutSnapshots() {
        assertThat(versionRepository.maxVersionNumber(newDiagram().getId())).isZero();
    }

    @Test
    void versionIsNotFoundThroughAnotherDiagram() {
        Diagram diagram = newDiagram();
        DiagramVersion v = versionRepository.save(snapshot(diagram.getId(), 1));

        assertThat(versionRepository.findByIdAndDiagramId(v.id(), newDiagram().getId())).isEmpty();
    }

    @Test
    void duplicateVersionNumberIsConflict() {
        Diagram diagram = newDiagram();
        versionRepository.save(snapshot(diagram.getId(), 1));

        assertThatThrownBy(() -> versionRepository.save(snapshot(diagram.getId(), 1)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateWithExpectedVersionSucceeds() {
        Diagram diagram = newDiagram();
        diagram.setData("{\"nodes\": [], \"edges\": [{\"id\": \"e1\"}]}");

        Diagram saved = diagramRepository.save(diagram);

        assertThat(saved.getVersion()).isEqualTo(2);
        Diagram stored = diagramRepository.findById(diagram.getId()).orElseThrow();
        assertThat(stored.getVersion()).isEqualTo(2);
        assertThat(stored.getData()).contains("\"e1\"");
    }

    @Test
    void staleUpdateIsRejectedAndRowIsUnchanged() {
        Diagram diagram = newDiagram();
        // Người khác đã lưu trước => CSDL lên version 2
        Diagram other = diagramRepository.findById(diagram.getId()).orElseThrow();
        other.setTitle("Renamed by someone else");
        diagramRepository.save(other);

        diagram.setData("{\"nodes\": [], \"edges\": []}"); // vẫn giữ version 1 đã đọc

        assertThatThrownBy(() -> diagramRepository.save(diagram))
                .isInstanceOf(OptimisticLockConflictException.class)
                .hasMessage("Diagram version conflict: requested 1, current 2");
        Diagram stored = diagramRepository.findById(diagram.getId()).orElseThrow();
        assertThat(stored.getVersion()).isEqualTo(2);
        assertThat(stored.getData()).contains("\"n1\"");
    }

    @Test
    void updateRoleChangesRoleOfExistingShare() {
        DiagramShare share = newShare(newDiagram().getId());

        assertThat(shareRepository.updateRole(share.getId(), share.getDiagramId(), ShareRole.EDIT)).isTrue();
        assertThat(shareRepository.findById(share.getId())).get()
                .extracting(DiagramShare::getRole).isEqualTo(ShareRole.EDIT);
    }

    @Test
    void roleUpdateAfterRevokeDoesNotRecreateTheLink() {
        DiagramShare share = newShare(newDiagram().getId());
        shareRepository.deleteById(share.getId());

        assertThat(shareRepository.updateRole(share.getId(), share.getDiagramId(), ShareRole.EDIT)).isFalse();
        assertThat(shareRepository.findByShareToken(share.getShareToken())).isEmpty();
    }

    @Test
    void roleUpdateThroughAnotherDiagramMatchesNothing() {
        DiagramShare share = newShare(newDiagram().getId());

        assertThat(shareRepository.updateRole(share.getId(), newDiagram().getId(), ShareRole.EDIT)).isFalse();
        assertThat(shareRepository.findById(share.getId())).get()
                .extracting(DiagramShare::getRole).isEqualTo(ShareRole.VIEW);
    }

    private DiagramShare newShare(UUID diagramId) {
        return shareRepository.save(new DiagramShare(UUID.randomUUID(), diagramId, "token-" + UUID.randomUUID(),
                ShareRole.VIEW, Instant.now().truncatedTo(ChronoUnit.MICROS)));
    }

    private Diagram newDiagram() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Diagram diagram = new Diagram(null, "Shop ERD", DiagramType.ERD, DATA, 1, false, SEED_USER, SEED_USER, now, now);
        return diagramRepository.save(diagram);
    }

    private DiagramVersion snapshot(UUID diagramId, int number) {
        return new DiagramVersion(UUID.randomUUID(), diagramId, number, DATA, "note " + number, SEED_USER,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
}
