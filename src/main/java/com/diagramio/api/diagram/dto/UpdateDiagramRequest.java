package com.diagramio.api.diagram.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateDiagramRequest(
        @NotBlank @Size(min = 1, max = 255) String title,
        @NotNull @Pattern(regexp = "UML|ERD") String diagramType,
        @NotNull Integer version,
        @NotNull
        @Schema(types = "object", implementation = Object.class, example = "{\"nodes\": [], \"edges\": []}")
        JsonNode data,
        boolean isPublic) {
}
