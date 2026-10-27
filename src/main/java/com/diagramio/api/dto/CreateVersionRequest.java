package com.diagramio.api.dto;

import com.diagramio.domain.version.DiagramVersionService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CreateVersionRequest(
        @Schema(description = "Ghi chú phiên bản", example = "Bản thiết kế ERD Pha 1")
        @Size(max = DiagramVersionService.MAX_NOTE_LENGTH)
        String note) {
}
