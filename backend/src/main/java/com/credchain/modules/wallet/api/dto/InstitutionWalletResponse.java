package com.credchain.modules.wallet.api.dto;

import com.credchain.modules.wallet.domain.WalletStatus;

import java.time.Instant;
import java.util.UUID;

/** Public view of an institution's issuer wallet. Never contains key material. */
public record InstitutionWalletResponse(
        UUID institutionId,
        String address,
        String custody,
        WalletStatus status,
        boolean canIssue,
        String balanceEth,          // live from the chain; null if unavailable
        String fundingTxHash,
        String grantTxHash,
        String revokeTxHash,
        Instant activatedAt,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        String explorerUrl
) {
}