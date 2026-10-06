package com.credchain.modules.blockchain.infrastructure;

import java.util.Optional;

/**
 * Any failure while talking to the blockchain.
 * Messages are safe to log: they never contain the RPC URL or key material.
 * If a transaction was already broadcast, its hash is attached so callers can check it later.
 */
public class BlockchainException extends RuntimeException {

    private final String txHash;

    public BlockchainException(String message) {
        this(message, null);
    }

    public BlockchainException(String message, String txHash) {
        super(message);
        this.txHash = txHash;
    }

    public Optional<String> txHash() {
        return Optional.ofNullable(txHash);
    }
}