package com.diagramio.infrastructure.mapper;

import com.diagramio.domain.model.DiagramShare;
import com.diagramio.infrastructure.persistence.entity.DiagramShareEntity;
import org.springframework.stereotype.Component;

@Component
public class DiagramShareMapper {

    public DiagramShare toDomain(DiagramShareEntity entity) {
        if (entity == null) {
            return null;
        }
        return new DiagramShare(
                entity.getId(),
                entity.getDiagramId(),
                entity.getShareToken(),
                entity.getRole(),
                entity.getCreatedAt()
        );
    }

    public DiagramShareEntity toEntity(DiagramShare domain) {
        if (domain == null) {
            return null;
        }
        return new DiagramShareEntity(
                domain.getId(),
                domain.getDiagramId(),
                domain.getShareToken(),
                domain.getRole(),
                domain.getCreatedAt()
        );
    }
}
