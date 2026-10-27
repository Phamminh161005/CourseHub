package com.diagramio.api.diagram.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateDiagramRequest(
        @NotBlank String title,
        @NotNull @Pattern(regexp = "UML|ERD") String diagramType,
        boolean isPublic) {
}
