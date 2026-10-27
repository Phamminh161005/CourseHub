package com.diagramio.api.diagram.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateDiagramRequest(
        @NotBlank String title,
        @NotNull @Pattern(regexp = "UML|ERD") String diagramType,
        @NotNull Integer version,
        @NotBlank String data,
        boolean isPublic) {
}
