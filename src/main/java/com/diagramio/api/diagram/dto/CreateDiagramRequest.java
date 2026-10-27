package com.diagramio.api.diagram.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Sơ đồ mới luôn riêng tư và có data mặc định; muốn công khai thì owner gọi PUT sau khi tạo. */
public record CreateDiagramRequest(
        @NotBlank String title,
        @NotNull @Pattern(regexp = "UML|ERD") String diagramType) {
}
