package com.diagramio.infrastructure.persistence.repository;

import com.diagramio.infrastructure.persistence.entity.DiagramEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaDiagramRepository extends JpaRepository<DiagramEntity, UUID> {
}
