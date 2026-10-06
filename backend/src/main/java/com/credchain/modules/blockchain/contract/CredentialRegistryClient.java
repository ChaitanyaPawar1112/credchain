package com.credchain.modules.blockchain.contract;

import com.credchain.modules.blockchain.config.BlockchainProperties;
import com.credchain.modules.blockchain.infrastructure.TransactionSender;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;

/**
 * High-level CredentialRegistry operations.
 * Admin actions are signed by the platform admin wallet; issuing is signed by the institution's own wallet.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.blockchain", name = "enabled", havingValue = "true")
public class CredentialRegistryClient {

    private final TransactionSender sender;
    private final BlockchainProperties properties;
    private final Credentials platformAdminCredentials;

    public String contractAddress() {
        return properties.contractAddress();
    }


    public String platformAdminAddress() {
        return platformAdminCredentials.getAddress();
    }

    // ---------- reads ----------

    @SuppressWarnings("rawtypes")
    public boolean isIssuer(String account) {
        List<Type> result = sender.call(contractAddress(), CredentialRegistryAbi.isIssuer(account));
        return (Boolean) result.get(0).getValue();
    }

    public BigInteger balanceOf(String address) {
        return sender.balanceOf(address);
    }

    // ---------- admin (platform wallet) ----------

    public TransactionReceipt addIssuer(String issuer) {
        return sender.sendAndConfirm(platformAdminCredentials, contractAddress(), BigInteger.ZERO,
                CredentialRegistryAbi.encode(CredentialRegistryAbi.addIssuer(issuer)), "addIssuer(" + issuer + ")");
    }

    public TransactionReceipt removeIssuer(String issuer) {
        return sender.sendAndConfirm(platformAdminCredentials, contractAddress(), BigInteger.ZERO,
                CredentialRegistryAbi.encode(CredentialRegistryAbi.removeIssuer(issuer)), "removeIssuer(" + issuer + ")");
    }

    /** Sends ETH from the platform admin wallet (pays institution wallets' transaction fees). */
    public TransactionReceipt fundFromPlatform(String to, BigInteger amountWei) {
        return sender.sendAndConfirm(platformAdminCredentials, to, amountWei, null, "fund(" + to + ")");
    }


    // ---------- issuing (institution wallet) ----------

    /** Simulates, signs with the institution's key and broadcasts issueBatch. Returns the tx hash without waiting. */
    public String submitIssueBatch(Credentials issuer, String merkleRootHex, int count, long expiresAtEpochSeconds) {
        byte[] root = Numeric.hexStringToByteArray(merkleRootHex);
        return sender.send(issuer, contractAddress(), BigInteger.ZERO,
                CredentialRegistryAbi.encode(CredentialRegistryAbi.issueBatch(root, count, expiresAtEpochSeconds)),
                "issueBatch(" + merkleRootHex + ")");
    }

    /** Waits until a sent transaction is final (successful receipt + confirmations). */
    public TransactionReceipt awaitReceipt(String txHash, String label) {
        return sender.waitForConfirmation(txHash, label);
    }
}