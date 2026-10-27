package com.diagramio.api.version;

import com.diagramio.domain.version.DiagramVersionService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CreateVersionRequest(
        @Schema(description = "Ghi chú phiên bản", example = "Bản thiết kế ERD Pha 1")
        @Size(max = DiagramVersionService.MAX_NOTE_LENGTH)
        String note,

        @Schema(description = "Version sơ đồ client đang thấy (không bắt buộc). Lệch với hiện tại thì 409, "
                + "tránh chụp nhầm nội dung người khác vừa sửa", example = "6")
        Integer version) {
}
