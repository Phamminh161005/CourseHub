package com.diagramio.infrastructure.mapper;

import com.diagramio.domain.model.Diagram;
import com.diagramio.infrastructure.persistence.entity.DiagramEntity;
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
                entity.getUpdatedAt()
        );
    }

    public DiagramEntity toEntity(Diagram domain) {
        if (domain == null) {
            return null;
        }
        return new DiagramEntity(
                domain.getId(),
                domain.getTitle(),
                domain.getDiagramType(),
                domain.getData(),
                domain.getVersion(),
                domain.isPublic(),
                domain.getOwnerId(),
                domain.getUpdatedBy(),
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}
