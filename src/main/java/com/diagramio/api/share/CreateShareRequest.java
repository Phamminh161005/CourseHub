package com.diagramio.api.share;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateShareRequest(
        @Schema(description = "Quyền hạn chia sẻ: view (chỉ xem) hoặc edit (chỉnh sửa)", example = "view")
        @NotBlank(message = "Quyền chia sẻ không được để trống")
        @Pattern(regexp = "^(?i)(view|edit)$", message = "Quyền chia sẻ chỉ có thể là 'view' hoặc 'edit'")
        String role
) {
}
