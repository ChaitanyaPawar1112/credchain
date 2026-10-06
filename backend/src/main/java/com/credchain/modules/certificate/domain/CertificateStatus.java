package com.credchain.modules.certificate.domain;

public enum CertificateStatus {

    /** In a draft batch; can still be removed. */
    DRAFT,

    /** Batch sent to the blockchain, waiting for confirmation. */
    PENDING,

    /** Anchored on-chain: verifiable as VALID. */
    ISSUED,

    /** Revocation requested; waiting for the on-chain revoke. */
    REVOCATION_PENDING,

    /** Revoked on-chain. */
    REVOKED
}