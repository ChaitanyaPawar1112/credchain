package com.credchain.modules.certificate.api.dto;

import com.credchain.modules.certificate.domain.RevocationReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RevokeCertificateRequest(
        @NotNull RevocationReason reason,
        @Size(max = 500) String note
) {
}