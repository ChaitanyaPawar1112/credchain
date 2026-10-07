package com.credchain.modules.verification.domain;

/** What a verifier is told about a certificate. */
public enum VerificationStatus {

    /** Recorded on the blockchain, not revoked, not expired. */
    VALID,

    /** The institution revoked it (on-chain, or the revocation is being sent right now). */
    REVOKED,

    /** Was valid, but its expiry date has passed. */
    EXPIRED,

    /** No certificate with this hash was issued (wrong link, typo, or a forged certificate). */
    NOT_FOUND,

    /** Upload check only: the PDF was not issued by CredChain, or was changed after it was issued. */
    FAKE
}
