package com.credchain.modules.certificate.domain;

import java.util.EnumSet;
import java.util.Set;

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
    REVOKED;

    /** Statuses of a certificate that is (or was) recorded on the blockchain. */
    public static final Set<CertificateStatus> ON_CHAIN = EnumSet.of(ISSUED, REVOCATION_PENDING, REVOKED);
}