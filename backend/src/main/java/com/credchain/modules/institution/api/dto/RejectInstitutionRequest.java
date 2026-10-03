package com.credchain.modules.institution.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectInstitutionRequest(
        @Schema(example = "Registration number could not be verified with AISHE records")
        @NotBlank @Size(min = 10, max = 500)
        String reason
) {
}