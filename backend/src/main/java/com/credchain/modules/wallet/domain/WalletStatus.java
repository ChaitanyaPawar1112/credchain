package com.credchain.modules.wallet.domain;

/** On-chain state of an institution's issuer wallet. */
public enum WalletStatus {

    /** Waiting for the worker to fund the wallet and call addIssuer. */
    PENDING_ACTIVATION,

    /** Has ISSUER_ROLE on the contract: may issue credentials. */
    ACTIVE,

    /** Waiting for the worker to call removeIssuer (institution suspended). */
    PENDING_DEACTIVATION,

    /** ISSUER_ROLE removed on-chain. */
    INACTIVE;

    public boolean needsChainAction() {
        return this == PENDING_ACTIVATION || this == PENDING_DEACTIVATION;
    }
}