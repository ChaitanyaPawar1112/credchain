package com.credchain.modules.institution.domain;

/** Must match ck_institutions_status in V3. */
public enum InstitutionStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED
}