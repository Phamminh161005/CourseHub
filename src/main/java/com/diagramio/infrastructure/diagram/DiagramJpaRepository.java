package com.diagramio.infrastructure.diagram;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagramJpaRepository extends JpaRepository<DiagramEntity, UUID> {

    Page<DiagramEntity> findByOwnerId(UUID ownerId, Pageable pageable);

    Page<DiagramEntity> findByOwnerIdAndDiagramType(
            UUID ownerId, String diagramType, Pageable pageable);

    Page<DiagramEntity> findByOwnerIdAndTitleContainingIgnoreCase(
            UUID ownerId, String title, Pageable pageable);

    Page<DiagramEntity> findByOwnerIdAndDiagramTypeAndTitleContainingIgnoreCase(
            UUID ownerId, String diagramType, String title,
            Pageable pageable);

    Page<DiagramEntity> findByIsPublicTrue(Pageable pageable);
}
