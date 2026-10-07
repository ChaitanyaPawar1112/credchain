package com.credchain.modules.certificate.domain;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** A group of certificates anchored on-chain with ONE Merkle root (one issueBatch transaction). */
@Getter
@Entity
@Table(name = "certificate_batches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CertificateBatch extends BaseEntity {

    private static final Duration FIRST_RETRY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY = Duration.ofMinutes(30);
    private static final int MAX_ERROR_LENGTH = 500;

    @Column(name = "institution_id", nullable = false, updatable = false)

    private UUID institutionId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BatchStatus status;

    @Column(name = "certificate_count", nullable = false)
    private int certificateCount;

    /** null = never expires. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "merkle_root", length = 66)
    private String merkleRoot;

    /** Institution wallet that signs issueBatch (snapshot at queue time). */
    @Column(name = "issuer_address", length = 42)
    private String issuerAddress;

    @Column(name = "chain_id")
    private Long chainId;

    @Column(name = "tx_hash", length = 66)
    private String txHash;

    @Column(name = "block_number")
    private Long blockNumber;


    @Column(name = "queued_by")
    private UUID queuedBy;

    @Column(name = "queued_at")
    private Instant queuedAt;

    @Column(name = "anchored_at")
    private Instant anchoredAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason", length = 30)
    private RevocationReason revocationReason;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    // ---------- Factory ----------

    public static CertificateBatch createDraft(UUID institutionId, String title, Instant expiresAt,
                                               UUID createdBy, Instant now) {

        requireFutureExpiry(expiresAt, now);
        CertificateBatch b = new CertificateBatch();
        b.institutionId = Objects.requireNonNull(institutionId, "institutionId");
        b.title = Objects.requireNonNull(title, "title").trim();
        b.expiresAt = expiresAt;
        b.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        b.status = BatchStatus.DRAFT;
        return b;
    }

    // ---------- Lifecycle ----------

    public boolean isDraft() {
        return status == BatchStatus.DRAFT;
    }

    public void requireDraft() {
        if (!isDraft()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Batch is " + status + "; certificates can only be changed while it is DRAFT");
        }
    }

    /** Freezes the batch: root and count are final, the worker will anchor it. */
    public void queue(String merkleRoot, int certificateCount, String issuerAddress, long chainId,
                      UUID queuedBy, Instant now) {
        requireDraft();
        if (certificateCount < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "A batch needs at least one certificate");
        }
        requireFutureExpiry(expiresAt, now);
        this.merkleRoot = lower(Objects.requireNonNull(merkleRoot, "merkleRoot"));

        this.certificateCount = certificateCount;
        this.issuerAddress = lower(Objects.requireNonNull(issuerAddress, "issuerAddress"));
        this.chainId = chainId;
        this.queuedBy = queuedBy;
        this.queuedAt = now;
        this.status = BatchStatus.QUEUED;
        this.attempts = 0;
        this.lastError = null;
        this.nextAttemptAt = now;
    }

    /** The issueBatch transaction was broadcast. */
    public void markSubmitted(String txHash) {
        requireStatus(BatchStatus.QUEUED);
        this.txHash = lower(Objects.requireNonNull(txHash, "txHash"));
        this.status = BatchStatus.SUBMITTED;
    }

    /** The transaction is confirmed: certificates are now verifiable. */
    public void markAnchored(long blockNumber, Instant now) {
        requireStatus(BatchStatus.SUBMITTED);
        this.blockNumber = blockNumber;
        this.anchoredAt = now;
        this.status = BatchStatus.ANCHORED;
        this.attempts = 0;
        this.lastError = null;
        this.nextAttemptAt = null;
    }

    /** A submitted transaction was dropped or reverted: try again from QUEUED. */
    public void returnToQueue(String reason, Instant now) {
        requireStatus(BatchStatus.SUBMITTED);

        this.txHash = null;
        this.status = BatchStatus.QUEUED;
        recordFailure(reason, now);
    }

    public void markRevoked(RevocationReason reason, Instant now) {
        requireStatus(BatchStatus.ANCHORED);
        this.revocationReason = Objects.requireNonNull(reason, "reason");
        this.revokedAt = now;
        this.status = BatchStatus.REVOKED;
    }

    /** Exponential backoff: 30s, 1m, 2m, 4m ... capped at 30 minutes. */
    public void recordFailure(String error, Instant now) {
        attempts++;
        long factor = 1L << Math.min(attempts - 1, 16);
        Duration delay = FIRST_RETRY.multipliedBy(factor);
        nextAttemptAt = now.plus(delay.compareTo(MAX_RETRY) > 0 ? MAX_RETRY : delay);
        lastError = (error == null || error.length() <= MAX_ERROR_LENGTH) ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    /** Expiry as the contract expects it: unix seconds, 0 = never. */
    public long expiresAtEpochSeconds() {
        return expiresAt == null ? 0L : expiresAt.getEpochSecond();
    }

    // ---------- helpers ----------

    private void requireStatus(BatchStatus expected) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Batch is " + status + "; this action requires " + expected);

        }
    }

    private static void requireFutureExpiry(Instant expiresAt, Instant now) {
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Expiry date must be in the future");
        }
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}