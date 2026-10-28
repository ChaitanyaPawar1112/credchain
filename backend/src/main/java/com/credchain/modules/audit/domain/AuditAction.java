package com.credchain.modules.audit.domain;

/** Everything the audit log records. New actions may be added; existing names never change (they are stored). */
public enum AuditAction {

    USER_REGISTERED(AuditCategory.ACCOUNT),
    LOGIN_SUCCEEDED(AuditCategory.ACCOUNT),
    /** Wrong email or password. */
    LOGIN_FAILED(AuditCategory.ACCOUNT),
    /** Right password, but the account is locked or not active. */
    LOGIN_BLOCKED(AuditCategory.ACCOUNT),
    PASSWORD_CHANGED(AuditCategory.ACCOUNT),
    LOGGED_OUT_EVERYWHERE(AuditCategory.ACCOUNT),

    INSTITUTION_APPLIED(AuditCategory.INSTITUTION),
    INSTITUTION_APPROVED(AuditCategory.INSTITUTION),
    INSTITUTION_REJECTED(AuditCategory.INSTITUTION),
    INSTITUTION_SUSPENDED(AuditCategory.INSTITUTION),
    INSTITUTION_REINSTATED(AuditCategory.INSTITUTION),

    STUDENT_ADDED(AuditCategory.STUDENT),
    STUDENTS_IMPORTED(AuditCategory.STUDENT),
    CLAIM_CODE_ISSUED(AuditCategory.STUDENT),
    STUDENT_ACCOUNT_LINKED(AuditCategory.STUDENT),

    BATCH_CREATED(AuditCategory.CERTIFICATE),
    BATCH_DELETED(AuditCategory.CERTIFICATE),
    BATCH_ISSUED(AuditCategory.CERTIFICATE),
    CERTIFICATE_REVOKED(AuditCategory.CERTIFICATE),

    TRUST_SCORES_RECOMPUTED(AuditCategory.SYSTEM);

    private final AuditCategory category;

    AuditAction(AuditCategory category) {
        this.category = category;
    }

    public AuditCategory category() {
        return category;
    }
}
