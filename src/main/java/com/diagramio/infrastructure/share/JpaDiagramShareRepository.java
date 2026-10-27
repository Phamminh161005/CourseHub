package com.diagramio.infrastructure.share;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaDiagramShareRepository extends JpaRepository<DiagramShareEntity, UUID> {

    List<DiagramShareEntity> findByDiagramId(UUID diagramId);

    Optional<DiagramShareEntity> findByShareToken(String shareToken);

    boolean existsByShareToken(String shareToken);
}
