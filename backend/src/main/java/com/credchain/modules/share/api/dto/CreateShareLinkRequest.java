package com.credchain.modules.share.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** name = who the link is for, only shown to the student (e.g. "Infosys HR"). */
public record CreateShareLinkRequest(
        @NotNull UUID certificateId,
        @NotBlank @Size(max = 100) String name,
        @Min(1) @Max(365) int expiresInDays
) {
}
