package com.credchain.modules.institution.domain;

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

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Getter
@Entity
@Table(name = "institutions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Institution extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "registration_number", nullable = false, length = 50)
    private String registrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private InstitutionType type;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "website", length = 255)
    private String website;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "country", nullable = false, length = 2)
    private String country;

    @Column(name = "postal_code", length = 12)
    private String postalCode;

    @Column(name = "contact_person_name", nullable = false, length = 150)
    private String contactPersonName;

    @Column(name = "contact_person_email", nullable = false, length = 255)
    private String contactPersonEmail;

    @Column(name = "wallet_address", length = 42)
    private String walletAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InstitutionStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    // ---------- Factory ----------

    /** A new application always starts as PENDING. */
    public static Institution apply(InstitutionProfile p) {
        Institution i = new Institution();
        i.name = p.name().trim();
        i.code = normalizeCode(p.code());
        i.registrationNumber = p.registrationNumber().trim();
        i.type = p.type();
        i.email = lower(p.email());
        i.phone = blankToNull(p.phone());
        i.website = blankToNull(p.website());
        i.addressLine = blankToNull(p.addressLine());
        i.city = p.city().trim();
        i.state = p.state().trim();
        i.country = (p.country() == null || p.country().isBlank()) ? "IN" : p.country().trim().toUpperCase(Locale.ROOT);
        i.postalCode = blankToNull(p.postalCode());
        i.contactPersonName = p.contactPersonName().trim();
        i.contactPersonEmail = lower(p.contactPersonEmail());
        i.walletAddress = normalizeWallet(p.walletAddress());
        i.status = InstitutionStatus.PENDING;
        return i;
    }

    // ---------- Lifecycle (state machine) ----------

    public void approve(UUID reviewerId, Instant now) {
        requireStatus(InstitutionStatus.PENDING);
        this.status = InstitutionStatus.APPROVED;
        this.rejectionReason = null;
        markReviewed(reviewerId, now);
    }

    public void reject(UUID reviewerId, String reason, Instant now) {
        requireStatus(InstitutionStatus.PENDING);
        this.status = InstitutionStatus.REJECTED;
        this.rejectionReason = reason.trim();
        markReviewed(reviewerId, now);
    }

    public void suspend(UUID reviewerId, Instant now) {
        requireStatus(InstitutionStatus.APPROVED);
        this.status = InstitutionStatus.SUSPENDED;
        markReviewed(reviewerId, now);
    }

    public void reinstate(UUID reviewerId, Instant now) {
        requireStatus(InstitutionStatus.SUSPENDED);
        this.status = InstitutionStatus.APPROVED;
        markReviewed(reviewerId, now);
    }

    public void changeWalletAddress(String walletAddress) {
        this.walletAddress = normalizeWallet(walletAddress);
    }

    public boolean isApproved() {
        return status == InstitutionStatus.APPROVED;
    }

    // ---------- helpers ----------

    private void requireStatus(InstitutionStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Institution is " + status + "; this action requires " + expected);
        }
    }

    private void markReviewed(UUID reviewerId, Instant now) {
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
    }

    public static String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }

    public static String normalizeWallet(String wallet) {
        return (wallet == null || wallet.isBlank()) ? null : wallet.trim().toLowerCase(Locale.ROOT);
    }

    private static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}