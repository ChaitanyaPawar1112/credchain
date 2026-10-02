package com.credchain.modules.user.domain;

/**
 * Must match the CHECK constraint ck_users_role in V2 migration.
 */
public enum Role {
    SUPER_ADMIN,
    INSTITUTION_ADMIN,
    STUDENT,
    VERIFIER
}