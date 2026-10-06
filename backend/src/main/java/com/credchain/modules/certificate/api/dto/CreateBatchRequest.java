package com.credchain.modules.certificate.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** expiresAt is optional: null = the certificates never expire. */
public record CreateBatchRequest(
        @NotBlank @Size(max = 200) String title,
        @Future Instant expiresAt
) {
}