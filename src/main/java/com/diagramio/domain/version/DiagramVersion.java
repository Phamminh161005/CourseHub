package com.diagramio.domain.version;

import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot bất biến của dữ liệu sơ đồ tại một thời điểm.
 * createdBy là null khi người tạo dùng link chia sẻ mà không đăng nhập.
 */
public record DiagramVersion(UUID id, UUID diagramId, int versionNumber, String data, String note,
                             UUID createdBy, Instant createdAt) {
}
