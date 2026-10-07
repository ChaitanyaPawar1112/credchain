package com.credchain.modules.verification.api.dto;

import com.credchain.common.api.PageResponse;
import com.credchain.modules.verification.domain.VerificationMethod;
import com.credchain.modules.verification.domain.VerificationStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Who checked which certificates: totals per result and the checks themselves (newest first). */
public record VerificationActivityResponse(
        long totalChecks,
        Map<VerificationStatus, Long> byResult,
        PageResponse<Entry> checks
) {

    /** One check. Certificate fields are empty when the check was not about a real certificate (FAKE, NOT_FOUND). */
    public record Entry(
            UUID id,
            Instant checkedAt,
            VerificationMethod method,
            VerificationStatus result,
            boolean blockchainChecked,
            String certHash,
            UUID certificateId,
            String certificateNumber,
            String studentName,
            UUID institutionId,
            String userAgent
    ) {
    }
}
