package com.diagramio.infrastructure.persistence.diagram;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataDiagramJpaRepository extends JpaRepository<DiagramEntity, UUID> {

    @Query("""
            select d from DiagramEntity d
            where d.ownerId = :ownerId
              and (:diagramType is null or d.diagramType like concat('%', :diagramType, '%'))
              and (:title is null or lower(d.title) like lower(concat('%', :title, '%')))
            """)
    Page<DiagramEntity> findByOwnerIdAndDiagramTypeContainingAndTitleContainingIgnoreCase(
            @Param("ownerId") UUID ownerId,
            @Param("diagramType") String diagramType,
            @Param("title") String title,
            Pageable pageable);

    Page<DiagramEntity> findByIsPublicTrue(Pageable pageable);
}
