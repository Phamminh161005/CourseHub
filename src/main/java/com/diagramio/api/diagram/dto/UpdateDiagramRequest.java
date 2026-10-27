package com.diagramio.api.diagram.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateDiagramRequest(
        @NotBlank String title,
        @NotNull @Pattern(regexp = "UML|ERD") String diagramType,
        @NotNull Integer version,
        @NotNull
        @Schema(type = "object", example = "{\"nodes\": [], \"edges\": []}")
        JsonNode data,
        boolean isPublic) {
}
