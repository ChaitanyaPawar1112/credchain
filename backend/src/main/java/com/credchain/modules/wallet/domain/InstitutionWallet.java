package com.credchain.modules.wallet.domain;

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

/**
 * Custodial blockchain wallet of one institution.
 * The private key is stored only in encrypted form and is never returned by any getter used for APIs.
 */
@Getter
@Entity
@Table(name = "institution_wallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstitutionWallet extends BaseEntity {

    public static final String CUSTODY_CUSTODIAL = "CUSTODIAL";

    private static final Duration FIRST_RETRY = Duration.ofSeconds(30);
    private static final Duration MAX_RETRY = Duration.ofMinutes(30);

    private static final int MAX_ERROR_LENGTH = 500;

    @Column(name = "institution_id", nullable = false, updatable = false)
    private UUID institutionId;

    /** Lowercase 0x address. */
    @Column(name = "address", nullable = false, updatable = false, length = 42)
    private String address;

    @Getter(AccessLevel.NONE)
    @Column(name = "encrypted_private_key", nullable = false, updatable = false, length = 200)
    private String encryptedPrivateKey;

    @Column(name = "custody", nullable = false, updatable = false, length = 20)
    private String custody;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WalletStatus status;

    @Column(name = "funding_tx_hash", length = 66)
    private String fundingTxHash;

    @Column(name = "grant_tx_hash", length = 66)
    private String grantTxHash;

    @Column(name = "revoke_tx_hash", length = 66)
    private String revokeTxHash;

    @Column(name = "activated_at")
    private Instant activatedAt;


    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    // ---------- Factory ----------

    /** A new custodial wallet waits for the worker to fund it and grant ISSUER_ROLE. */
    public static InstitutionWallet createCustodial(UUID institutionId, String address,
                                                    String encryptedPrivateKey, Instant now) {
        InstitutionWallet w = new InstitutionWallet();
        w.institutionId = Objects.requireNonNull(institutionId, "institutionId");
        w.address = Objects.requireNonNull(address, "address").toLowerCase(Locale.ROOT);
        w.encryptedPrivateKey = Objects.requireNonNull(encryptedPrivateKey, "encryptedPrivateKey");
        w.custody = CUSTODY_CUSTODIAL;
        w.status = WalletStatus.PENDING_ACTIVATION;
        w.nextAttemptAt = now;
        return w;
    }

    /** Encryption context (AAD): binds the encrypted key to this institution. */
    public static String encryptionContext(UUID institutionId) {
        return "institution-wallet:" + institutionId;
    }

    /** Only for the signing service; never map this into an API response. */
    public String encryptedPrivateKey() {
        return encryptedPrivateKey;

    }

    // ---------- Lifecycle requests (from institution approve / suspend / reinstate) ----------

    public void requestActivation(Instant now) {
        if (status == WalletStatus.ACTIVE || status == WalletStatus.PENDING_ACTIVATION) {
            return;
        }
        status = WalletStatus.PENDING_ACTIVATION;
        resetRetries(now);
    }

    public void requestDeactivation(Instant now) {
        if (status == WalletStatus.INACTIVE || status == WalletStatus.PENDING_DEACTIVATION) {
            return;
        }
        status = WalletStatus.PENDING_DEACTIVATION;
        resetRetries(now);
    }

    // ---------- Results reported by the sync worker ----------

    public void recordFunding(String txHash) {
        this.fundingTxHash = normalizeHash(txHash);
    }

    public void markActivated(String grantTxHash, Instant now) {
        if (status != WalletStatus.PENDING_ACTIVATION) {
            return;   // state changed meanwhile (e.g. suspended): the next run handles it
        }
        if (grantTxHash != null) {
            this.grantTxHash = normalizeHash(grantTxHash);

        }
        status = WalletStatus.ACTIVE;
        activatedAt = now;
        clearRetries();
    }

    public void markDeactivated(String revokeTxHash) {
        if (status != WalletStatus.PENDING_DEACTIVATION) {
            return;
        }
        if (revokeTxHash != null) {
            this.revokeTxHash = normalizeHash(revokeTxHash);
        }
        status = WalletStatus.INACTIVE;
        clearRetries();
    }

    /** Exponential backoff: 30s, 1m, 2m, 4m ... capped at 30 minutes. */
    public void recordFailure(String error, Instant now) {
        attempts++;
        long factor = 1L << Math.min(attempts - 1, 16);
        Duration delay = FIRST_RETRY.multipliedBy(factor);
        nextAttemptAt = now.plus(delay.compareTo(MAX_RETRY) > 0 ? MAX_RETRY : delay);
        lastError = truncate(error);
    }

    public boolean isActive() {
        return status == WalletStatus.ACTIVE;
    }

    // ---------- helpers ----------


    private void resetRetries(Instant now) {
        attempts = 0;
        lastError = null;
        nextAttemptAt = now;
    }

    private void clearRetries() {
        attempts = 0;
        lastError = null;
        nextAttemptAt = null;
    }

    private static String normalizeHash(String hash) {
        return hash == null ? null : hash.toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}