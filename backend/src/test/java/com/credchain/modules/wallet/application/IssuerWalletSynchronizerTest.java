package com.credchain.modules.wallet.application;

import com.credchain.modules.blockchain.contract.CredentialRegistryClient;
import com.credchain.modules.blockchain.infrastructure.BlockchainException;
import com.credchain.modules.wallet.config.WalletSyncProperties;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import com.credchain.modules.wallet.domain.WalletStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("IssuerWalletSynchronizer")
class IssuerWalletSynchronizerTest {

    private static final String ADDRESS = "0x" + "ab".repeat(20);
    private static final String FUND_TX = "0x" + "1".repeat(64);
    private static final String GRANT_TX = "0x" + "2".repeat(64);

    private static final String REVOKE_TX = "0x" + "3".repeat(64);
    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

    private final CredentialRegistryClient registry = mock(CredentialRegistryClient.class);
    private final WalletSyncProperties properties =
            new WalletSyncProperties(5, new BigDecimal("0.003"), new BigDecimal("0.001"));
    private final IssuerWalletSynchronizer synchronizer =
            new IssuerWalletSynchronizer(registry, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("new wallet: funds it, grants ISSUER_ROLE, becomes ACTIVE")
    void fundsAndGrants() {
        InstitutionWallet wallet = newWallet();
        when(registry.balanceOf(ADDRESS)).thenReturn(BigInteger.ZERO);
        when(registry.fundFromPlatform(ADDRESS, wei("0.003"))).thenReturn(receipt(FUND_TX));
        when(registry.isIssuer(ADDRESS)).thenReturn(false);
        when(registry.addIssuer(ADDRESS)).thenReturn(receipt(GRANT_TX));

        assertThat(synchronizer.sync(wallet)).isTrue();

        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(wallet.getFundingTxHash()).isEqualTo(FUND_TX);
        assertThat(wallet.getGrantTxHash()).isEqualTo(GRANT_TX);
        assertThat(wallet.getActivatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("re-run after a crash: already funded and already issuer -> sends nothing")
    void idempotentAfterCrash() {
        InstitutionWallet wallet = newWallet();
        when(registry.balanceOf(ADDRESS)).thenReturn(wei("0.003"));
        when(registry.isIssuer(ADDRESS)).thenReturn(true);


        assertThat(synchronizer.sync(wallet)).isTrue();

        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        verify(registry, never()).fundFromPlatform(any(), any());
        verify(registry, never()).addIssuer(any());
    }

    @Test
    @DisplayName("suspended institution: removes ISSUER_ROLE, becomes INACTIVE")
    void deactivates() {
        InstitutionWallet wallet = newWallet();
        wallet.markActivated(GRANT_TX, NOW);
        wallet.requestDeactivation(NOW);
        when(registry.isIssuer(ADDRESS)).thenReturn(true);
        when(registry.removeIssuer(ADDRESS)).thenReturn(receipt(REVOKE_TX));

        assertThat(synchronizer.sync(wallet)).isTrue();

        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.INACTIVE);
        assertThat(wallet.getRevokeTxHash()).isEqualTo(REVOKE_TX);
    }

    @Test
    @DisplayName("blockchain error: stays pending, attempt recorded, retry in 30s")
    void failureBacksOff() {
        InstitutionWallet wallet = newWallet();
        when(registry.balanceOf(ADDRESS)).thenThrow(new BlockchainException("RPC unreachable"));

        assertThat(synchronizer.sync(wallet)).isFalse();

        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.PENDING_ACTIVATION);

        assertThat(wallet.getAttempts()).isEqualTo(1);
        assertThat(wallet.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(wallet.getLastError()).contains("RPC unreachable");
    }

    private static InstitutionWallet newWallet() {
        return InstitutionWallet.createCustodial(UUID.randomUUID(), ADDRESS, "v1:test-only", NOW);
    }

    private static TransactionReceipt receipt(String txHash) {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash(txHash);
        return receipt;
    }

    private static BigInteger wei(String eth) {
        return Convert.toWei(new BigDecimal(eth), Convert.Unit.ETHER).toBigIntegerExact();
    }
}