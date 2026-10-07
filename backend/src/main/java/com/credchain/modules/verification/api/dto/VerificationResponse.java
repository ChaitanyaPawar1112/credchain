package com.credchain.modules.verification.api.dto;

import com.credchain.modules.certificate.domain.CertificateType;
import com.credchain.modules.certificate.domain.RevocationReason;
import com.credchain.modules.verification.domain.VerificationStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Public verification result. Shows only what is printed on the certificate itself:
 * never the secret salt, the student's email or internal notes.
 */
public record VerificationResponse(
        String certHash,
        VerificationStatus status,
        String message,
        boolean blockchainChecked,
        Instant checkedAt,
        CertificateDetails certificate,
        Revocation revocation,
        BlockchainRecord blockchain
) {

    public record CertificateDetails(
            String certificateNumber,
            CertificateType type,
            String title,
            String program,
            String grade,
            String cgpa,
            LocalDate awardedOn,
            String studentName,
            String enrollmentNo,
            String institutionName,
            String institutionCode,
            Instant expiresAt
    ) {
    }

    public record Revocation(RevocationReason reason, Instant revokedAt) {
    }

    /** Where to check it yourself on a block explorer. */
    public record BlockchainRecord(
            Long chainId,
            String contractAddress,
            String merkleRoot,
            String issuerAddress,
            String txHash,
            Long blockNumber,
            Instant anchoredAt,
            String explorerTxUrl
    ) {
    }

    public static VerificationResponse notFound(String certHash, String message, boolean blockchainChecked, Instant now) {
        return new VerificationResponse(certHash, VerificationStatus.NOT_FOUND, message, blockchainChecked, now,
                null, null, null);
    }
}
