package com.credchain.modules.verification.api.dto;

import com.credchain.modules.verification.domain.CheckResult;
import com.credchain.modules.verification.domain.VerificationStatus;

import java.time.Instant;
import java.util.List;

/**
 * Result of checking an uploaded certificate PDF.
 * status is VALID / REVOKED / EXPIRED for a genuine file, FAKE otherwise.
 * record is the official CredChain record when the PDF points to a real certificate (also when the file was edited,
 * so the verifier can compare), and null when the PDF is not linked to any real certificate.
 * Never contains the secret salt embedded in the PDF.
 */
public record PdfVerificationResponse(
        VerificationStatus status,
        String message,
        String fileSha256,
        Instant checkedAt,
        List<Check> checks,
        VerificationResponse record
) {

    /** One step of the check, in the order it ran. */
    public record Check(String name, CheckResult result, String detail) {
    }
}
