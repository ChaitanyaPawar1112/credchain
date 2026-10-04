package com.credchain.modules.student.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Returned once when a claim code is generated. Only its hash is stored. */
public record ClaimCodeResponse(
        UUID studentId,
        String enrollmentNo,
        String claimCode,
        Instant expiresAt,
        String note
) {
}