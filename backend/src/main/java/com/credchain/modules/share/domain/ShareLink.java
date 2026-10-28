package com.credchain.modules.share.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** One named link to one certificate. */
@Getter
@Entity
@Table(name = "share_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShareLink {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "token", nullable = false, updatable = false, length = 43)
    private String token;

    @Column(name = "certificate_id", nullable = false, updatable = false)
    private UUID certificateId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "name", nullable = false, updatable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "last_viewed_at")
    private Instant lastViewedAt;

    public static ShareLink create(UUID certificateId, UUID studentId, String name, Instant now, Instant expiresAt) {
        ShareLink link = new ShareLink();
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        link.token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        link.certificateId = certificateId;
        link.studentId = studentId;
        link.name = name.strip();
        link.createdAt = now;
        link.expiresAt = expiresAt;
        return link;
    }

    public ShareLinkStatus status(Instant now) {
        if (revokedAt != null) {
            return ShareLinkStatus.REVOKED;
        }
        return expiresAt.isAfter(now) ? ShareLinkStatus.ACTIVE : ShareLinkStatus.EXPIRED;
    }

    public boolean isActive(Instant now) {
        return status(now) == ShareLinkStatus.ACTIVE;
    }

    /** Turning a link off is permanent; doing it twice changes nothing. */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public void recordView(Instant now) {
        viewCount++;
        lastViewedAt = now;
    }
}
