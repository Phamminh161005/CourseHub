package com.diagramio.api.share;

import com.diagramio.domain.share.SharedDiagramView;
import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record SharedDiagramResponse(
        @Schema(description = "Mã định danh sơ đồ")
        UUID id,

        @Schema(description = "Tiêu đề sơ đồ")
        String title,

        @Schema(description = "Loại sơ đồ (UML hoặc ERD)")
        String diagramType,

        @Schema(description = "Cấu trúc dữ liệu các node và edge của sơ đồ")
        @JsonRawValue
        String data,

        @Schema(description = "Quyền hạn của token truy cập này (view hoặc edit)")
        String role,

        @Schema(description = "Phiên bản sơ đồ")
        int version,

        @Schema(description = "Trạng thái công khai")
        boolean isPublic,

        @Schema(description = "Thời gian tạo")
        Instant createdAt,

        @Schema(description = "Thời gian cập nhật gần nhất")
        Instant updatedAt
) {
    public static SharedDiagramResponse fromDomain(SharedDiagramView view) {
        return new SharedDiagramResponse(
                view.getDiagramId(),
                view.getTitle(),
                view.getDiagramType(),
                view.getData(),
                view.getRole().value(),
                view.getVersion(),
                view.isPublic(),
                view.getCreatedAt(),
                view.getUpdatedAt()
        );
    }
}
