package com.diagramio.domain.diagram;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiagramTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-29T10:00:00Z");
    private static final DiagramData CONTENT = DiagramData.fromStorage("{\"nodes\": [{\"id\": \"A\"}], \"edges\": []}");

    private final UUID ownerId = UUID.randomUUID();
    private final Diagram diagram = new Diagram(UUID.randomUUID(), "Shop", DiagramType.ERD, DiagramData.EMPTY, 3,
            false, ownerId, ownerId, CREATED, CREATED);

    @Test
    void replaceContentChangesOnlyContentAndAuditFields() {
        UUID editor = UUID.randomUUID();

        diagram.replaceContent(CONTENT, editor, LATER);

        assertThat(diagram.getData()).isEqualTo(CONTENT);
        assertThat(diagram.getUpdatedBy()).isEqualTo(editor);
        assertThat(diagram.getUpdatedAt()).isEqualTo(LATER);
        assertThat(diagram.getTitle()).isEqualTo("Shop");
        assertThat(diagram.getDiagramType()).isEqualTo(DiagramType.ERD);
        assertThat(diagram.getVersion()).isEqualTo(3);
        assertThat(diagram.isPublic()).isFalse();
    }

    @Test
    void updateMetadataChangesOnlyMetadataAndAuditFields() {
        diagram.updateMetadata("Shop v2", DiagramType.UML, ownerId, LATER);

        assertThat(diagram.getTitle()).isEqualTo("Shop v2");
        assertThat(diagram.getDiagramType()).isEqualTo(DiagramType.UML);
        assertThat(diagram.getUpdatedAt()).isEqualTo(LATER);
        assertThat(diagram.getData()).isEqualTo(DiagramData.EMPTY);
        assertThat(diagram.getVersion()).isEqualTo(3);
    }

    @Test
    void editThroughLinkWithoutLoginRecordsNoUser() {
        diagram.replaceContent(CONTENT, null, LATER);

        assertThat(diagram.getUpdatedBy()).isNull();
    }
}
