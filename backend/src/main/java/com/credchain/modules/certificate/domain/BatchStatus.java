package com.credchain.modules.certificate.domain;

public enum BatchStatus {

    /** Being prepared: certificates can be added or removed. */
    DRAFT,

    /** Frozen, Merkle root computed, waiting for the worker to send issueBatch. */
    QUEUED,

    /** issueBatch transaction sent, waiting for confirmations. */
    SUBMITTED,

    /** Confirmed on-chain. */
    ANCHORED,

    /** The whole batch was revoked on-chain. */
    REVOKED
}