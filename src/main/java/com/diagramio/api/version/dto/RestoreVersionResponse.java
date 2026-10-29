package com.diagramio.api.version.dto;

import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.version.RestoreResult;
import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Trạng thái sơ đồ sau khi khôi phục (kèm version mới để client sửa tiếp) và
 * snapshot sao lưu tự động của trạng thái ngay trước khi khôi phục.
 */
public record RestoreVersionResponse(UUID diagramId, String title, String diagramType,
                                     @JsonRawValue
                                     @Schema(types = "object", implementation = Object.class, example = "{\"nodes\": [], \"edges\": []}")
                                     String data,
                                     Integer version, Instant updatedAt,
                                     DiagramVersionSummaryResponse backupVersion) {

    public static RestoreVersionResponse from(RestoreResult result) {
        Diagram d = result.diagram();
        return new RestoreVersionResponse(d.getId(), d.getTitle(), d.getDiagramType().name(), d.getData().json(),
                d.getVersion(), d.getUpdatedAt(), DiagramVersionSummaryResponse.from(result.backup()));
    }
}
