package com.credchain.modules.share.api.dto;

import com.credchain.modules.share.domain.ShareLinkStatus;

import java.time.Instant;
import java.util.UUID;

/** One of the student's links, with how often it was opened. */
public record ShareLinkResponse(
        UUID id,
        String name,
        String url,
        UUID certificateId,
        String certificateNumber,
        String certificateTitle,
        ShareLinkStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        int viewCount,
        Instant lastViewedAt
) {
}
