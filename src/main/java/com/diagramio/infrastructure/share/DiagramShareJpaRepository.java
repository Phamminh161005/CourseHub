package com.diagramio.infrastructure.share;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagramShareJpaRepository extends JpaRepository<DiagramShareEntity, UUID> {

    List<DiagramShareEntity> findByDiagramId(UUID diagramId);

    Optional<DiagramShareEntity> findByShareToken(String shareToken);

    boolean existsByShareToken(String shareToken);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update DiagramShareEntity s set s.role = :role where s.id = :id and s.diagramId = :diagramId")
    int updateRole(@Param("id") UUID id, @Param("diagramId") UUID diagramId, @Param("role") String role);
}
