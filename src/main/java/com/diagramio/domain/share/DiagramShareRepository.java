package com.diagramio.domain.share;

import com.diagramio.domain.access.ShareRole;
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

    /**
     * Đổi quyền bằng một câu UPDATE có điều kiện. Trả false nếu link không còn (đã thu hồi)
     * hoặc không thuộc sơ đồ này; không bao giờ tạo lại link đã bị thu hồi.
     */
    boolean updateRole(UUID shareId, UUID diagramId, ShareRole role);

    void deleteById(UUID id);

    boolean existsByShareToken(String shareToken);
}
