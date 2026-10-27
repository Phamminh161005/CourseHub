package com.diagramio.domain.repository;

import com.diagramio.domain.model.Diagram;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface kho lưu trữ cho sơ đồ.
 * Thuần Java.
 */
public interface DiagramRepository {

    Optional<Diagram> findById(UUID id);

    Diagram save(Diagram diagram);
}
