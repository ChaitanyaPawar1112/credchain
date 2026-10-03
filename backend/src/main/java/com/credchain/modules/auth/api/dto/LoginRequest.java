package com.credchain.modules.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "student@example.com") @NotBlank @Email String email,
        @Schema(example = "Str0ng@Pass") @NotBlank String password
) {
}