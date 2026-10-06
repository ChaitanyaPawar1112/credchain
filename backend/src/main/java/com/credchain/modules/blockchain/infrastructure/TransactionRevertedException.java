package com.credchain.modules.blockchain.infrastructure;

/** The transaction was mined but the contract rejected it (receipt status = failed). */
public class TransactionRevertedException extends BlockchainException {

    public TransactionRevertedException(String message, String txHash) {
        super(message, txHash);
    }
}