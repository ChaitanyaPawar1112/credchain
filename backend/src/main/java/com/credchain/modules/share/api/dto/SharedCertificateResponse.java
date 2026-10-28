package com.credchain.modules.share.api.dto;

import com.credchain.modules.verification.api.dto.VerificationResponse;

import java.time.Instant;

/**
 * What someone opening a share link sees: the live verification result of the certificate (never the link's name),
 * until when the link works, and whether the PDF can be downloaded through it.
 */
public record SharedCertificateResponse(
        VerificationResponse verification,
        Instant linkExpiresAt,
        boolean pdfAvailable
) {
}
