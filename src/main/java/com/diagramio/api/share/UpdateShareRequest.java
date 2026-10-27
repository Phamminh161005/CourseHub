package com.diagramio.api.share;

import io.swagger.v3.oas.annotations.media.Schema;

/** role được kiểm tra một lần ở ShareRole.from (sai thì 400). */
public record UpdateShareRequest(
        @Schema(description = "Mức độ quyền chia sẻ mới: view (chỉ xem) hoặc edit (chỉnh sửa)", example = "edit",
                allowableValues = {"view", "edit"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String role
) {
}
