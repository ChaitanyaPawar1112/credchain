package com.credchain.modules.certificate.domain;

/** Outcome of comparing a certificate in the database with the smart contract. */
public enum ChainCheckResult {

    /** The blockchain agrees with the database. */
    MATCH,

    /** The blockchain says something different: needs a human to look at it. */
    MISMATCH
}