package com.diagramio.domain.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.diagramio.domain.TransactionRunner;
import com.diagramio.domain.access.Caller;
import com.diagramio.domain.access.ShareRole;
import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.diagram.DiagramRepository;
import com.diagramio.domain.exception.ForbiddenException;
import com.diagramio.domain.exception.InvalidInputException;
import com.diagramio.domain.exception.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class DiagramVersionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");
    private static final String CURRENT_DATA = "{\"nodes\": [{\"id\": \"current\"}], \"edges\": []}";
    private static final String OLD_DATA = "{\"nodes\": [{\"id\": \"old\"}], \"edges\": []}";

    private final DiagramRepository diagramRepository = mock(DiagramRepository.class);
    private final DiagramVersionRepository versionRepository = mock(DiagramVersionRepository.class);
    private final TransactionRunner inlineTransaction = new TransactionRunner() {
        @Override
        public <T> T inTransaction(java.util.function.Supplier<T> work) {
            return work.get();
        }
    };
    private final DiagramVersionService service = new DiagramVersionService(diagramRepository, versionRepository,
            inlineTransaction, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID ownerId = UUID.randomUUID();
    private final UUID diagramId = UUID.randomUUID();
    private Diagram diagram;

    @BeforeEach
    void setUp() {
        diagram = new Diagram(diagramId, "Shop ERD", "ERD", CURRENT_DATA, 5, false, ownerId, ownerId,
                NOW.minusSeconds(3600), NOW.minusSeconds(60));
        when(diagramRepository.findById(diagramId)).thenReturn(Optional.of(diagram));
        when(versionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(diagramRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- createVersion ----------

    @Test
    void ownerCreatesSnapshotOfCurrentDataWithNextNumber() {
        when(versionRepository.maxVersionNumber(diagramId)).thenReturn(3);

        DiagramVersion created = service.createVersion(diagramId, "Bản thiết kế ERD Pha 1", Caller.user(ownerId));

        assertThat(created.diagramId()).isEqualTo(diagramId);
        assertThat(created.versionNumber()).isEqualTo(4);
        assertThat(created.data()).isEqualTo(CURRENT_DATA);
        assertThat(created.note()).isEqualTo("Bản thiết kế ERD Pha 1");
        assertThat(created.createdBy()).isEqualTo(ownerId);
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(created.id()).isNotNull();
    }

    @Test
    void firstSnapshotGetsNumberOne() {
        when(versionRepository.maxVersionNumber(diagramId)).thenReturn(0);

        DiagramVersion created = service.createVersion(diagramId, null, Caller.user(ownerId));

        assertThat(created.versionNumber()).isEqualTo(1);
        assertThat(created.note()).isNull();
    }

    @Test
    void editShareCanCreateSnapshotWithoutUser() {
        DiagramVersion created = service.createVersion(diagramId, "edit link",
                Caller.shareToken(diagramId, ShareRole.EDIT));

        assertThat(created.createdBy()).isNull();
        verify(versionRepository).save(any());
    }

    @Test
    void viewShareCannotCreateSnapshot() {
        assertThatThrownBy(() -> service.createVersion(diagramId, "x", Caller.shareToken(diagramId, ShareRole.VIEW)))
                .isInstanceOf(ForbiddenException.class);
        verify(versionRepository, never()).save(any());
    }

    @Test
    void strangerCannotCreateSnapshotEvenOnPublicDiagram() {
        diagram.setPublic(true);

        assertThatThrownBy(() -> service.createVersion(diagramId, "x", Caller.user(UUID.randomUUID())))
                .isInstanceOf(ForbiddenException.class);
        verify(versionRepository, never()).save(any());
    }

    @Test
    void noteLongerThan255IsRejected() {
        String note = "a".repeat(256);

        assertThatThrownBy(() -> service.createVersion(diagramId, note, Caller.user(ownerId)))
                .isInstanceOf(InvalidInputException.class);
        verify(versionRepository, never()).save(any());
    }

    @Test
    void createOnMissingDiagramIs404() {
        UUID missing = UUID.randomUUID();
        when(diagramRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createVersion(missing, "x", Caller.user(ownerId)))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- list / get ----------

    @Test
    void viewerCanListVersions() {
        DiagramVersion v = version(UUID.randomUUID(), 1, OLD_DATA);
        when(versionRepository.findByDiagramId(diagramId)).thenReturn(List.of(v));

        assertThat(service.listVersions(diagramId, Caller.shareToken(diagramId, ShareRole.VIEW))).containsExactly(v);
    }

    @Test
    void strangerCannotListVersionsOfPublicDiagram() {
        diagram.setPublic(true);

        assertThatThrownBy(() -> service.listVersions(diagramId, Caller.user(UUID.randomUUID())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getVersionReturnsSnapshot() {
        UUID versionId = UUID.randomUUID();
        DiagramVersion v = version(versionId, 2, OLD_DATA);
        when(versionRepository.findByIdAndDiagramId(versionId, diagramId)).thenReturn(Optional.of(v));

        assertThat(service.getVersion(diagramId, versionId, Caller.user(ownerId))).isEqualTo(v);
    }

    @Test
    void versionOfAnotherDiagramIs404() {
        UUID versionId = UUID.randomUUID();
        when(versionRepository.findByIdAndDiagramId(versionId, diagramId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVersion(diagramId, versionId, Caller.user(ownerId)))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- restore ----------

    @Test
    void restoreBacksUpCurrentStateThenCopiesSnapshotIntoDiagram() {
        UUID versionId = UUID.randomUUID();
        when(versionRepository.findByIdAndDiagramId(versionId, diagramId))
                .thenReturn(Optional.of(version(versionId, 2, OLD_DATA)));
        when(versionRepository.maxVersionNumber(diagramId)).thenReturn(7);

        RestoreResult result = service.restore(diagramId, versionId, Caller.user(ownerId));

        InOrder order = inOrder(versionRepository, diagramRepository);
        ArgumentCaptor<DiagramVersion> backup = ArgumentCaptor.forClass(DiagramVersion.class);
        order.verify(versionRepository).save(backup.capture());
        order.verify(diagramRepository).save(diagram);

        assertThat(backup.getValue().data()).isEqualTo(CURRENT_DATA);
        assertThat(backup.getValue().versionNumber()).isEqualTo(8);
        assertThat(backup.getValue().note()).isEqualTo("Auto backup before restoring version 2");

        assertThat(result.diagram().getData()).isEqualTo(OLD_DATA);
        assertThat(result.diagram().getVersion()).isEqualTo(6);
        assertThat(result.diagram().getUpdatedAt()).isEqualTo(NOW);
        assertThat(result.diagram().getUpdatedBy()).isEqualTo(ownerId);
        assertThat(result.backup()).isEqualTo(backup.getValue());
    }

    @Test
    void editShareCanRestore() {
        UUID versionId = UUID.randomUUID();
        when(versionRepository.findByIdAndDiagramId(versionId, diagramId))
                .thenReturn(Optional.of(version(versionId, 1, OLD_DATA)));

        RestoreResult result = service.restore(diagramId, versionId, Caller.shareToken(diagramId, ShareRole.EDIT));

        assertThat(result.diagram().getData()).isEqualTo(OLD_DATA);
        assertThat(result.diagram().getUpdatedBy()).isNull();
    }

    @Test
    void viewerCannotRestoreAndNothingIsWritten() {
        UUID versionId = UUID.randomUUID();

        assertThatThrownBy(() -> service.restore(diagramId, versionId, Caller.shareToken(diagramId, ShareRole.VIEW)))
                .isInstanceOf(ForbiddenException.class);
        verify(versionRepository, never()).save(any());
        verify(diagramRepository, never()).save(any());
        assertThat(diagram.getData()).isEqualTo(CURRENT_DATA);
    }

    @Test
    void restoringMissingVersionIs404AndNothingIsWritten() {
        UUID versionId = UUID.randomUUID();
        when(versionRepository.findByIdAndDiagramId(versionId, diagramId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restore(diagramId, versionId, Caller.user(ownerId)))
                .isInstanceOf(NotFoundException.class);
        verify(versionRepository, never()).save(any());
        verify(diagramRepository, never()).save(any());
    }

    private DiagramVersion version(UUID id, int number, String data) {
        return new DiagramVersion(id, diagramId, number, data, "note " + number, ownerId, NOW.minusSeconds(600));
    }
}
