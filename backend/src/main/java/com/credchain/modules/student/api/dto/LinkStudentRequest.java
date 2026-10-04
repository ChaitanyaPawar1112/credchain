package com.credchain.modules.student.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LinkStudentRequest(
        @Schema(example = "SIT-AUR") @NotBlank @Size(max = 20) String institutionCode,
        @Schema(example = "2022CS001") @NotBlank @Size(max = 50) String enrollmentNo,
        @Schema(example = "K7P2-M9QX") @NotBlank @Size(max = 20) String claimCode
) {
}