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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * One academic credential. Its content is a frozen snapshot: never edited after creation
 * (a draft is deleted and recreated instead), so the stored hash always matches the data.
 */
@Getter
@Entity
@Table(name = "certificates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Certificate extends BaseEntity {

    private static final Duration FIRST_RETRY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY = Duration.ofMinutes(30);
    private static final int MAX_TEXT = 500;

    @Column(name = "institution_id", nullable = false, updatable = false)
    private UUID institutionId;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private UUID batchId;

    @Column(name = "student_id", nullable = false, updatable = false)
    private UUID studentId;

    @Column(name = "certificate_number", nullable = false, updatable = false, length = 50)
    private String certificateNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 30)
    private CertificateType type;

    @Column(name = "title", nullable = false, updatable = false, length = 200)
    private String title;

    @Column(name = "program", updatable = false, length = 150)
    private String program;

    @Column(name = "grade", updatable = false, length = 100)
    private String grade;

    @Column(name = "cgpa", updatable = false, precision = 4, scale = 2)
    private BigDecimal cgpa;

    @Column(name = "awarded_on", nullable = false, updatable = false)
    private LocalDate awardedOn;

    @Column(name = "student_name", nullable = false, updatable = false, length = 150)
    private String studentName;

    @Column(name = "enrollment_no", nullable = false, updatable = false, length = 50)
    private String enrollmentNo;

    /** Secret random value mixed into the hash; printed only on the student's own certificate. */
    @Column(name = "salt", nullable = false, updatable = false, length = 66)
    private String salt;

    @Column(name = "canonical_payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String canonicalPayload;

    @Column(name = "cert_hash", nullable = false, updatable = false, length = 66)
    private String certHash;

    /** JSON array of sibling hashes, set when the batch is queued. */
    @Column(name = "merkle_proof", columnDefinition = "TEXT")
    private String merkleProof;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CertificateStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason", length = 30)
    private RevocationReason revocationReason;

    @Column(name = "revocation_note", length = MAX_TEXT)
    private String revocationNote;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_tx_hash", length = 66)
    private String revokeTxHash;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = MAX_TEXT)
    private String lastError;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    /** Where the PDF is stored (object storage key); null until the PDF worker has made it. */
    @Column(name = "pdf_key", length = 300)
    private String pdfKey;

    @Column(name = "pdf_generated_at")
    private Instant pdfGeneratedAt;

    /** Last time the reconciliation job compared this certificate with the blockchain. */
    @Column(name = "chain_checked_at")
    private Instant chainCheckedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "chain_check_result", length = 20)
    private ChainCheckResult chainCheckResult;

    @Column(name = "chain_check_note", length = MAX_TEXT)
    private String chainCheckNote;

    // ---------- Factory ----------

    /** Content fields that go into the hash. */
    public record Content(CertificateType type, String title, String program, String grade,
                          BigDecimal cgpa, LocalDate awardedOn, String studentName, String enrollmentNo) {
    }

    public static Certificate createDraft(UUID institutionId, UUID batchId, UUID studentId, String certificateNumber,
                                          Content content, String salt, String canonicalPayload, String certHash,
                                          UUID createdBy) {
        Certificate c = new Certificate();
        c.institutionId = Objects.requireNonNull(institutionId, "institutionId");
        c.batchId = Objects.requireNonNull(batchId, "batchId");
        c.studentId = Objects.requireNonNull(studentId, "studentId");
        c.certificateNumber = Objects.requireNonNull(certificateNumber, "certificateNumber");
        c.type = Objects.requireNonNull(content.type(), "type");
        c.title = Objects.requireNonNull(content.title(), "title");
        c.program = content.program();
        c.grade = content.grade();
        c.cgpa = content.cgpa();
        c.awardedOn = Objects.requireNonNull(content.awardedOn(), "awardedOn");
        c.studentName = Objects.requireNonNull(content.studentName(), "studentName");
        c.enrollmentNo = Objects.requireNonNull(content.enrollmentNo(), "enrollmentNo");
        c.salt = lower(Objects.requireNonNull(salt, "salt"));
        c.canonicalPayload = Objects.requireNonNull(canonicalPayload, "canonicalPayload");
        c.certHash = lower(Objects.requireNonNull(certHash, "certHash"));
        c.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        c.status = CertificateStatus.DRAFT;
        return c;
    }

    // ---------- Lifecycle ----------

    /** Batch queued: store this certificate's Merkle proof. */
    public void markPending(String merkleProofJson) {
        requireStatus(CertificateStatus.DRAFT);
        this.merkleProof = Objects.requireNonNull(merkleProofJson, "merkleProofJson");
        this.status = CertificateStatus.PENDING;
    }

    /** Batch confirmed on-chain. */
    public void markIssued() {
        requireStatus(CertificateStatus.PENDING);
        this.status = CertificateStatus.ISSUED;
    }

    public void requestRevocation(RevocationReason reason, String note, Instant now) {
        requireStatus(CertificateStatus.ISSUED);
        this.revocationReason = Objects.requireNonNull(reason, "reason");
        this.revocationNote = (note == null || note.isBlank()) ? null : truncate(note.trim());
        this.status = CertificateStatus.REVOCATION_PENDING;
        this.attempts = 0;
        this.lastError = null;
        this.nextAttemptAt = now;
    }

    public void markRevoked(String revokeTxHash, Instant now) {
        requireStatus(CertificateStatus.REVOCATION_PENDING);
        this.revokeTxHash = revokeTxHash == null ? null : lower(revokeTxHash);
        this.revokedAt = now;
        this.status = CertificateStatus.REVOKED;
        this.attempts = 0;
        this.lastError = null;
        this.nextAttemptAt = null;
    }

    /** True once the certificate is on-chain (ISSUED, or revoked later): only then can it have a PDF. */
    public boolean isOnChain() {
        return status == CertificateStatus.ISSUED
                || status == CertificateStatus.REVOCATION_PENDING
                || status == CertificateStatus.REVOKED;
    }

    public boolean hasPdf() {
        return pdfKey != null;
    }

    /** The PDF was generated and stored. */
    public void attachPdf(String pdfKey, Instant now) {
        if (!isOnChain()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Certificate is " + status + "; a PDF is only made once it is on-chain");
        }
        this.pdfKey = Objects.requireNonNull(pdfKey, "pdfKey");
        this.pdfGeneratedAt = now;
    }

    /** Result of the reconciliation job comparing this certificate with the blockchain. */
    public void recordChainCheck(ChainCheckResult result, String note, Instant now) {
        this.chainCheckResult = Objects.requireNonNull(result, "result");
        this.chainCheckNote = truncate(note);
        this.chainCheckedAt = now;
    }

    /** Exponential backoff for the revocation worker. */
    public void recordFailure(String error, Instant now) {
        attempts++;
        long factor = 1L << Math.min(attempts - 1, 16);
        Duration delay = FIRST_RETRY.multipliedBy(factor);
        nextAttemptAt = now.plus(delay.compareTo(MAX_RETRY) > 0 ? MAX_RETRY : delay);
        lastError = truncate(error);
    }

    // ---------- helpers ----------

    private void requireStatus(CertificateStatus expected) {
        if (status != expected) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Certificate is " + status + "; this action requires " + expected);
        }
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
    }
}