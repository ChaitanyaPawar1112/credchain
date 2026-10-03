package com.credchain.modules.auth.api.dto;

import com.credchain.common.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(

        @Schema(example = "Str0ng@Pass")
        @NotBlank
        String currentPassword,

        @Schema(example = "N3w@Str0ngPass")
        @StrongPassword
        String newPassword
) {
}