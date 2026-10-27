package com.diagramio.domain.version;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Chỉ thêm và đọc: không có update/delete để giữ snapshot bất biến. */
public interface DiagramVersionRepository {

    /** Thêm snapshot mới; ném ConflictException nếu versionNumber đã tồn tại cho sơ đồ. */
    DiagramVersion save(DiagramVersion version);

    /** Các snapshot của sơ đồ, mới nhất trước. */
    List<DiagramVersion> findByDiagramId(UUID diagramId);

    Optional<DiagramVersion> findByIdAndDiagramId(UUID id, UUID diagramId);

    /** Số thứ tự snapshot lớn nhất của sơ đồ, 0 nếu chưa có. */
    int maxVersionNumber(UUID diagramId);
}
