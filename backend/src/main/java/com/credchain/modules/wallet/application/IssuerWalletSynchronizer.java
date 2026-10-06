package com.credchain.modules.wallet.application;

import com.credchain.modules.blockchain.contract.CredentialRegistryClient;
import com.credchain.modules.blockchain.infrastructure.BlockchainException;
import com.credchain.modules.wallet.config.WalletSyncProperties;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.utils.Convert;

import java.math.BigInteger;
import java.time.Clock;

/**
 * Brings ONE wallet's on-chain state in line with its database status.
 * Always checks the chain first, so a run interrupted half-way never funds or grants twice.
 * Never throws: failures are recorded on the wallet with exponential backoff.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.blockchain", name = "enabled", havingValue = "true")
public class IssuerWalletSynchronizer {

    private final CredentialRegistryClient registry;
    private final WalletSyncProperties properties;
    private final Clock clock;

    /** @return true if the wallet reached its target state */
    public boolean sync(InstitutionWallet wallet) {

        try {
            switch (wallet.getStatus()) {
                case PENDING_ACTIVATION -> activate(wallet);
                case PENDING_DEACTIVATION -> deactivate(wallet);
                default -> { /* nothing to do */ }
            }
            return true;
        } catch (BlockchainException e) {
            fail(wallet, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected error while syncing wallet {}", wallet.getAddress(), e);
            fail(wallet, "Unexpected error: " + e.getClass().getSimpleName());
        }
        return false;
    }

    private void activate(InstitutionWallet wallet) {
        String address = wallet.getAddress();

        BigInteger minBalance = toWei(properties.minBalanceEth());
        if (registry.balanceOf(address).compareTo(minBalance) < 0) {
            String fundingTx = registry.fundFromPlatform(address, toWei(properties.fundingAmountEth())).getTransactionHash();
            wallet.recordFunding(fundingTx);
        }

        String grantTx = null;
        if (!registry.isIssuer(address)) {
            grantTx = registry.addIssuer(address).getTransactionHash();
        }

        wallet.markActivated(grantTx, clock.instant());
        log.info("Issuer wallet {} of institution {} is ACTIVE on-chain", address, wallet.getInstitutionId());

    }

    private void deactivate(InstitutionWallet wallet) {
        String address = wallet.getAddress();

        String revokeTx = null;
        if (registry.isIssuer(address)) {
            revokeTx = registry.removeIssuer(address).getTransactionHash();
        }

        wallet.markDeactivated(revokeTx);
        log.info("Issuer wallet {} of institution {} is INACTIVE on-chain", address, wallet.getInstitutionId());
    }

    private void fail(InstitutionWallet wallet, String reason) {
        wallet.recordFailure(reason, clock.instant());
        log.warn("Issuer wallet {} sync failed (attempt {}), next try at {}: {}",
                wallet.getAddress(), wallet.getAttempts(), wallet.getNextAttemptAt(), reason);
    }

    private static BigInteger toWei(java.math.BigDecimal eth) {
        return Convert.toWei(eth, Convert.Unit.ETHER).toBigIntegerExact();
    }
}