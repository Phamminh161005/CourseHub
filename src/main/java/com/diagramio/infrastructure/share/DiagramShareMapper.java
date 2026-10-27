package com.diagramio.infrastructure.share;

import com.diagramio.domain.access.ShareRole;
import com.diagramio.domain.share.DiagramShare;
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
                ShareRole.from(entity.getRole()),
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
                domain.getRole().value(),
                domain.getCreatedAt()
        );
    }
}
