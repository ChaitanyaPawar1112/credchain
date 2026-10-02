package com.credchain.modules.auth.api.dto;

import com.credchain.modules.user.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @Schema(example = "student@example.com")
        @NotBlank @Email @Size(max = 255)
        String email,

        @Schema(example = "Str0ng@Pass")
        @NotBlank
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$",
                message = "Password must be 8-72 characters with uppercase, lowercase, digit and special character")
        String password,

        @Schema(example = "Chaitanya Pawar")
        @NotBlank @Size(max = 150)
        String fullName,

        @Schema(example = "+919876543210")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone must be 10-15 digits, optional leading +")
        String phone,

        @Schema(example = "STUDENT", description = "Only STUDENT or VERIFIER can self-register")
        @NotNull
        Role role
) {
}