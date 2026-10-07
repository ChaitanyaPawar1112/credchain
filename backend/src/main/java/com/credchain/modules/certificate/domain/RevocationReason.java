package com.credchain.modules.certificate.domain;

/** Mirrors CredentialRegistry.RevocationReason; chainCode is the uint8 sent to the contract. */
public enum RevocationReason {

    ISSUED_IN_ERROR(1),
    FRAUD(2),
    SUPERSEDED(3),
    OTHER(4);

    private final int chainCode;

    RevocationReason(int chainCode) {
        this.chainCode = chainCode;
    }

    public int chainCode() {
        return chainCode;
    }
}