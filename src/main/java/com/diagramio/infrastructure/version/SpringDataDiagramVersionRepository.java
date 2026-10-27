package com.diagramio.infrastructure.version;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataDiagramVersionRepository extends JpaRepository<DiagramVersionEntity, UUID> {

    List<DiagramVersionEntity> findByDiagramIdOrderByVersionNumberDesc(UUID diagramId);

    Optional<DiagramVersionEntity> findByIdAndDiagramId(UUID id, UUID diagramId);

    @Query("select coalesce(max(v.versionNumber), 0) from DiagramVersionEntity v where v.diagramId = :diagramId")
    int maxVersionNumber(@Param("diagramId") UUID diagramId);
}
