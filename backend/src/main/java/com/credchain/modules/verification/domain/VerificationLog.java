package com.credchain.modules.verification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One public check of a certificate. Written once, never changed. */
@Getter
@Entity
@Table(name = "verification_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationLog {

    static final int MAX_USER_AGENT = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, updatable = false, length = 10)
    private VerificationMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, updatable = false, length = 20)
    private VerificationStatus result;

    @Column(name = "blockchain_checked", nullable = false, updatable = false)
    private boolean blockchainChecked;

    /** The hash that was checked; null when an uploaded PDF had no usable hash. */
    @Column(name = "cert_hash", updatable = false, length = 66)
    private String certHash;

    /** The real certificate the check was about, when there is one. */
    @Column(name = "certificate_id", updatable = false)
    private UUID certificateId;

    @Column(name = "institution_id", updatable = false)
    private UUID institutionId;

    /** Browser / app that made the check (helps tell people from scripts). */
    @Column(name = "user_agent", updatable = false, length = MAX_USER_AGENT)
    private String userAgent;

    public static VerificationLog of(Instant checkedAt, VerificationMethod method, VerificationStatus result,
                                     boolean blockchainChecked, String certHash, UUID certificateId,
                                     UUID institutionId, String userAgent) {
        VerificationLog log = new VerificationLog();
        log.checkedAt = checkedAt;
        log.method = method;
        log.result = result;
        log.blockchainChecked = blockchainChecked;
        log.certHash = certHash;
        log.certificateId = certificateId;
        log.institutionId = institutionId;
        log.userAgent = (userAgent == null || userAgent.length() <= MAX_USER_AGENT)
                ? userAgent : userAgent.substring(0, MAX_USER_AGENT);
        return log;
    }
}
