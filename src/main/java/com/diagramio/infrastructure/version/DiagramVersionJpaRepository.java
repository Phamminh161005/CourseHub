package com.diagramio.infrastructure.version;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiagramVersionJpaRepository extends JpaRepository<DiagramVersionEntity, UUID> {

    List<DiagramVersionEntity> findByDiagramIdOrderByVersionNumberDesc(UUID diagramId);

    Optional<DiagramVersionEntity> findByIdAndDiagramId(UUID id, UUID diagramId);

    @Query("select coalesce(max(v.versionNumber), 0) from DiagramVersionEntity v where v.diagramId = :diagramId")
    int maxVersionNumber(@Param("diagramId") UUID diagramId);

    /** SELECT ... FOR UPDATE: chờ đến khi giao dịch đang giữ dòng sơ đồ commit hoặc rollback. */
    @Query(value = "select id from diagrams where id = :diagramId for update", nativeQuery = true)
    List<UUID> lockDiagramRow(@Param("diagramId") UUID diagramId);
}
