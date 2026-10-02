package com.credchain.modules.user.domain;

/**
 * Must match the CHECK constraint ck_users_status in V2 migration.
 */
public enum UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    DELETED
}