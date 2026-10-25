package com.diagramio.infrastructure.persistence.diagram;

import com.diagramio.domain.diagram.Diagram;
import org.springframework.stereotype.Component;

@Component
public class DiagramMapper {

    public Diagram toDomain(DiagramEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Diagram(
                entity.getId(),
                entity.getTitle(),
                entity.getDiagramType(),
                entity.getData(),
                entity.getVersion(),
                entity.isPublic(),
                entity.getOwnerId(),
                entity.getUpdatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public DiagramEntity toEntity(Diagram diagram) {
        if (diagram == null) {
            return null;
        }
        DiagramEntity entity = new DiagramEntity();
        entity.setId(diagram.getId());
        entity.setTitle(diagram.getTitle());
        entity.setDiagramType(diagram.getDiagramType());
        entity.setData(diagram.getData());
        entity.setVersion(diagram.getVersion());
        entity.setPublic(diagram.isPublic());
        entity.setOwnerId(diagram.getOwnerId());
        entity.setUpdatedBy(diagram.getUpdatedBy());
        entity.setCreatedAt(diagram.getCreatedAt());
        entity.setUpdatedAt(diagram.getUpdatedAt());
        return entity;
    }
}
