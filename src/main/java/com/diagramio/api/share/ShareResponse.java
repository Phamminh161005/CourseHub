package com.diagramio.api.share;

import com.diagramio.domain.share.DiagramShare;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ShareResponse(
        @Schema(description = "Mã định danh liên kết chia sẻ")
        UUID id,

        @Schema(description = "Mã định danh sơ đồ")
        UUID diagramId,

        @Schema(description = "Mã token chia sẻ bảo mật")
        String shareToken,

        @Schema(description = "Quyền hạn chia sẻ (view hoặc edit)")
        String role,

        @Schema(description = "Thời gian tạo liên kết")
        Instant createdAt
) {
    public static ShareResponse fromDomain(DiagramShare share) {
        return new ShareResponse(
                share.getId(),
                share.getDiagramId(),
                share.getShareToken(),
                share.getRole().value(),
                share.getCreatedAt()
        );
    }
}
