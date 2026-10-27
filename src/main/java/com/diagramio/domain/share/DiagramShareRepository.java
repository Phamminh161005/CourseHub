package com.diagramio.domain.share;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface kho lưu trữ cho liên kết chia sẻ sơ đồ.
 * Thuần Java, được implement ở tầng infrastructure.
 */
public interface DiagramShareRepository {

    List<DiagramShare> findByDiagramId(UUID diagramId);

    Optional<DiagramShare> findById(UUID id);

    Optional<DiagramShare> findByShareToken(String shareToken);

    DiagramShare save(DiagramShare share);

    void deleteById(UUID id);

    boolean existsByShareToken(String shareToken);
}
