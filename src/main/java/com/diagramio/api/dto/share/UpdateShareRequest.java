package com.diagramio.api.dto.share;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateShareRequest(
        @Schema(description = "Mức độ quyền chia sẻ mới: view (chỉ xem) hoặc edit (chỉnh sửa)", example = "edit")
        @NotBlank(message = "Quyền chia sẻ không được để trống")
        @Pattern(regexp = "^(?i)(view|edit)$", message = "Quyền chia sẻ chỉ có thể là 'view' hoặc 'edit'")
        String role
) {
}
