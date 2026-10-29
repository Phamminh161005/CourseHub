package com.diagramio.api.diagram.dto;

import com.diagramio.domain.diagram.Diagram;
import java.time.Instant;
import java.util.UUID;

public record DiagramSummaryResponse(
        UUID id,
        String title,
        String diagramType,
        Integer version,
        boolean isPublic,
        UUID ownerId,
        UUID updatedBy,
        Instant createdAt,
        Instant updatedAt) {

    public static DiagramSummaryResponse from(Diagram diagram) {
        return new DiagramSummaryResponse(diagram.getId(), diagram.getTitle(), diagram.getDiagramType().name(),
                diagram.getVersion(), diagram.isPublic(), diagram.getOwnerId(), diagram.getUpdatedBy(),
                diagram.getCreatedAt(), diagram.getUpdatedAt());
    }
}
