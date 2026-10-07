package com.credchain.modules.certificate.application;

import com.credchain.modules.blockchain.contract.CredentialRegistryClient;
import com.credchain.modules.blockchain.infrastructure.BlockchainException;
import com.credchain.modules.blockchain.infrastructure.TransactionRevertedException;
import com.credchain.modules.certificate.domain.BatchStatus;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.domain.CertificateStatus;
import com.credchain.modules.certificate.domain.CertificateType;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.wallet.application.InstitutionWalletService;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BatchAnchorer")
class BatchAnchorerTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private static final UUID INSTITUTION = UUID.randomUUID();
    private static final String ROOT = "0x" + "a".repeat(64);
    private static final String TX = "0x" + "b".repeat(64);
    private static final Credentials ISSUER = Credentials.create("0x" + "1".repeat(64));

    private final CredentialRegistryClient registry = mock(CredentialRegistryClient.class);
    private final InstitutionWalletRepository walletRepository = mock(InstitutionWalletRepository.class);
    private final InstitutionWalletService walletService = mock(InstitutionWalletService.class);
    private final CertificateRepository certificateRepository = mock(CertificateRepository.class);
    private final BatchAnchorer anchorer = new BatchAnchorer(registry, walletRepository, walletService,
            certificateRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("queued batch: signed by the institution wallet, confirmed, certificates ISSUED")
    void anchorsQueuedBatch() {
        CertificateBatch batch = queuedBatch();
        Certificate certificate = pendingCertificate(batch);
        givenActiveWallet();
        when(registry.submitIssueBatch(ISSUER, ROOT, 1, 0L)).thenReturn(TX);
        when(registry.awaitReceipt(any(), any())).thenReturn(receipt(123));
        when(certificateRepository.findAllByBatchIdOrderByCertificateNumber(any())).thenReturn(List.of(certificate));

        anchorer.process(batch);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.ANCHORED);
        assertThat(batch.getTxHash()).isEqualTo(TX);
        assertThat(batch.getBlockNumber()).isEqualTo(123L);
        assertThat(certificate.getStatus()).isEqualTo(CertificateStatus.ISSUED);
    }

    @Test
    @DisplayName("wallet not active: nothing is sent, failure recorded, batch stays QUEUED")
    void walletNotActive() {
        CertificateBatch batch = queuedBatch();
        when(walletRepository.findByInstitutionId(INSTITUTION)).thenReturn(Optional.empty());

        anchorer.process(batch);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.QUEUED);
        assertThat(batch.getAttempts()).isEqualTo(1);
        verify(registry, never()).submitIssueBatch(any(), anyString(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("not confirmed in time: stays SUBMITTED with the same tx hash (never re-sent)")
    void confirmationTimeout() {
        CertificateBatch batch = queuedBatch();
        givenActiveWallet();
        when(registry.submitIssueBatch(ISSUER, ROOT, 1, 0L)).thenReturn(TX);
        when(registry.awaitReceipt(any(), any())).thenThrow(new BlockchainException("not mined yet", TX));

        anchorer.process(batch);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.SUBMITTED);
        assertThat(batch.getTxHash()).isEqualTo(TX);
        assertThat(batch.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    @DisplayName("reverted on-chain: back to QUEUED for a fresh attempt")
    void revertedGoesBackToQueue() {
        CertificateBatch batch = queuedBatch();
        givenActiveWallet();
        when(registry.submitIssueBatch(ISSUER, ROOT, 1, 0L)).thenReturn(TX);
        when(registry.awaitReceipt(any(), any())).thenThrow(new TransactionRevertedException("reverted", TX));

        anchorer.process(batch);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.QUEUED);
        assertThat(batch.getTxHash()).isNull();
        assertThat(batch.getLastError()).contains("reverted");
    }

    // ---------- helpers ----------

    private void givenActiveWallet() {
        InstitutionWallet wallet = InstitutionWallet.createCustodial(INSTITUTION, ISSUER.getAddress(), "v1:test", NOW);
        wallet.markActivated(null, NOW);
        when(walletRepository.findByInstitutionId(INSTITUTION)).thenReturn(Optional.of(wallet));
        when(walletService.signingCredentials(wallet)).thenReturn(ISSUER);
    }

    private static CertificateBatch queuedBatch() {
        CertificateBatch batch = CertificateBatch.createDraft(INSTITUTION, "Convocation", null, UUID.randomUUID(), NOW);
        batch.queue(ROOT, 1, ISSUER.getAddress(), 11155111L, UUID.randomUUID(), NOW);
        return batch;
    }

    private static Certificate pendingCertificate(CertificateBatch batch) {
        Certificate certificate = Certificate.createDraft(INSTITUTION, UUID.randomUUID(), UUID.randomUUID(), "N-1",
                new Certificate.Content(CertificateType.DEGREE, "B.Tech", null, null, new BigDecimal("8.00"),
                        LocalDate.of(2026, 6, 30), "Student", "E1"),
                "0x" + "1".repeat(64), "{}", "0x" + "2".repeat(64), UUID.randomUUID());
        certificate.markPending("[]");
        return certificate;
    }

    private static TransactionReceipt receipt(long block) {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash(TX);
        receipt.setBlockNumber("0x" + Long.toHexString(block));
        receipt.setStatus("0x1");
        return receipt;
    }
}