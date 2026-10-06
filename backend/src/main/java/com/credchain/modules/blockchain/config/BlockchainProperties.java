package com.credchain.modules.blockchain.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings for talking to the Ethereum network (app.blockchain.* in application.yaml).
 * Secrets (rpcUrl, adminPrivateKey) come from infra/.env and are never logged.
 */
@Validated
@ConfigurationProperties(prefix = "app.blockchain")
public record BlockchainProperties(

        /** Master switch. false = no Web3j client, no transactions (tests, offline dev). */
        boolean enabled,

        /** JSON-RPC endpoint, e.g. Alchemy. Contains an API key: secret. */
        String rpcUrl,

        /** Expected chain; the health check fails if the RPC is on a different network. */
        @Positive long chainId,

        /** Deployed CredentialRegistry address. */
        @NotNull
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "must be 0x followed by 40 hex characters")
        String contractAddress,

        /** Block the contract was deployed in: event scanning starts here. */
        @PositiveOrZero long deploymentBlock,

        /** Platform admin wallet key (adds issuers, funds institution wallets). Secret. */
        String adminPrivateKey,

        /** Blocks to wait after a transaction is mined before treating it as final. */
        @Min(1) @Max(64) int confirmations,

        @NotNull Duration receiptTimeout,

        @NotNull Duration receiptPollInterval,

        /** Safety limit: never send a transaction if the network fee is above this. */
        @Positive long maxFeePerGasGwei
) {

    /**
     * Normalise values read out of .env files:
     * trim accidental spaces/newlines, and accept private keys with or without the 0x prefix
     * (MetaMask exports them without it).
     */
    public BlockchainProperties {
        rpcUrl = rpcUrl == null ? null : rpcUrl.strip();
        contractAddress = contractAddress == null ? null : contractAddress.strip();
        adminPrivateKey = adminPrivateKey == null ? null : adminPrivateKey.strip();
        if (adminPrivateKey != null && adminPrivateKey.matches("^[0-9a-fA-F]{64}$")) {
            adminPrivateKey = "0x" + adminPrivateKey;
        }
    }

    private static final java.util.regex.Pattern HTTP_URL = java.util.regex.Pattern.compile("^https?://\\S+$");
    private static final java.util.regex.Pattern PRIVATE_KEY = java.util.regex.Pattern.compile("^0x[0-9a-fA-F]{64}$");

    @AssertTrue(message = "app.blockchain.rpc-url (BLOCKCHAIN_RPC_URL) must be an http(s) URL when blockchain is enabled")
    public boolean isRpcUrlValid() {
        return !enabled || (rpcUrl != null && HTTP_URL.matcher(rpcUrl).matches());
    }

    @AssertTrue(message = "app.blockchain.admin-private-key (BLOCKCHAIN_ADMIN_PRIVATE_KEY) must be 0x followed by 64 hex characters when blockchain is enabled")
    public boolean isAdminPrivateKeyValid() {
        return !enabled || (adminPrivateKey != null && PRIVATE_KEY.matcher(adminPrivateKey).matches());
    }

    /** Never print secrets, even by accident in a log or debugger. */
    @Override
    public String toString() {
        return "BlockchainProperties[enabled=" + enabled
                + ", rpcUrl=" + (rpcUrl == null || rpcUrl.isBlank() ? "<empty>" : "<hidden>")
                + ", chainId=" + chainId
                + ", contractAddress=" + contractAddress
                + ", deploymentBlock=" + deploymentBlock
                + ", adminPrivateKey=" + (adminPrivateKey == null || adminPrivateKey.isBlank() ? "<empty>" : "<hidden>")
                + ", confirmations=" + confirmations
                + ", receiptTimeout=" + receiptTimeout
                + ", receiptPollInterval=" + receiptPollInterval
                + ", maxFeePerGasGwei=" + maxFeePerGasGwei + "]";
    }
}