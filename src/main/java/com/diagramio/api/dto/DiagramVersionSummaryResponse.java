package com.diagramio.api.dto;

import com.diagramio.domain.version.DiagramVersion;
import java.time.Instant;
import java.util.UUID;

/** Một dòng trong danh sách lịch sử phiên bản (không kèm dữ liệu sơ đồ). */
public record DiagramVersionSummaryResponse(UUID id, int versionNumber, String note, UUID createdBy,
                                            Instant createdAt) {

    public static DiagramVersionSummaryResponse from(DiagramVersion v) {
        return new DiagramVersionSummaryResponse(v.id(), v.versionNumber(), v.note(), v.createdBy(), v.createdAt());
    }
}
