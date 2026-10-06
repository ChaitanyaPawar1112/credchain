package com.credchain.modules.certificate.application;

import com.credchain.modules.blockchain.contract.CredentialRegistryClient;
import com.credchain.modules.blockchain.infrastructure.BlockchainException;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.domain.CertificateStatus;
import com.credchain.modules.certificate.domain.CertificateType;
import com.credchain.modules.certificate.domain.RevocationReason;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CertificateRevoker")
class CertificateRevokerTest {

    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");
    private static final UUID INSTITUTION = UUID.randomUUID();
    private static final String ROOT = "0x" + "a".repeat(64);
    private static final String REVOKE_TX = "0x" + "c".repeat(64);
    private static final Credentials ISSUER = Credentials.create("0x" + "1".repeat(64));

    private final CredentialRegistryClient registry = mock(CredentialRegistryClient.class);
    private final CertificateBatchRepository batchRepository = mock(CertificateBatchRepository.class);
    private final InstitutionWalletRepository walletRepository = mock(InstitutionWalletRepository.class);
    private final InstitutionWalletService walletService = mock(InstitutionWalletService.class);
    private final CertificateRevoker revoker = new CertificateRevoker(registry, batchRepository, walletRepository,
            walletService, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("VALID on-chain: revokeBatchEntry signed by the institution, certificate REVOKED")
    void revokes() {
        Certificate certificate = revocationPendingCertificate();
        givenBatchAndWallet(certificate);
        when(registry.verifyInBatch(any(), any(), any())).thenReturn(CredentialRegistryClient.STATUS_VALID);
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash(REVOKE_TX);
        when(registry.revokeBatchEntry(eq(ISSUER), any(), any(), any(), eq(RevocationReason.ISSUED_IN_ERROR.chainCode())))
                .thenReturn(receipt);

        revoker.process(certificate);


        assertThat(certificate.getStatus()).isEqualTo(CertificateStatus.REVOKED);
        assertThat(certificate.getRevokeTxHash()).isEqualTo(REVOKE_TX);
    }

    @Test
    @DisplayName("already REVOKED on-chain (e.g. after a crash): nothing sent, marked REVOKED")
    void alreadyRevokedOnChain() {
        Certificate certificate = revocationPendingCertificate();
        givenBatchAndWallet(certificate);
        when(registry.verifyInBatch(any(), any(), any())).thenReturn(CredentialRegistryClient.STATUS_REVOKED);

        revoker.process(certificate);

        assertThat(certificate.getStatus()).isEqualTo(CertificateStatus.REVOKED);
        verify(registry, never()).revokeBatchEntry(any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("blockchain error: stays REVOCATION_PENDING with backoff")
    void failureBacksOff() {
        Certificate certificate = revocationPendingCertificate();
        givenBatchAndWallet(certificate);
        when(registry.verifyInBatch(any(), any(), any())).thenThrow(new BlockchainException("RPC unreachable"));

        revoker.process(certificate);

        assertThat(certificate.getStatus()).isEqualTo(CertificateStatus.REVOCATION_PENDING);
        assertThat(certificate.getAttempts()).isEqualTo(1);
        assertThat(certificate.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
    }

    // ---------- helpers ----------


    private void givenBatchAndWallet(Certificate certificate) {
        CertificateBatch batch = CertificateBatch.createDraft(INSTITUTION, "Convocation", null, UUID.randomUUID(), NOW);
        batch.queue(ROOT, 1, ISSUER.getAddress(), 11155111L, UUID.randomUUID(), NOW);
        when(batchRepository.findById(certificate.getBatchId())).thenReturn(Optional.of(batch));

        InstitutionWallet wallet = InstitutionWallet.createCustodial(INSTITUTION, ISSUER.getAddress(), "v1:test", NOW);
        wallet.markActivated(null, NOW);
        when(walletRepository.findByInstitutionId(INSTITUTION)).thenReturn(Optional.of(wallet));
        when(walletService.signingCredentials(wallet)).thenReturn(ISSUER);
    }

    private static Certificate revocationPendingCertificate() {
        Certificate certificate = Certificate.createDraft(INSTITUTION, UUID.randomUUID(), UUID.randomUUID(), "N-1",
                new Certificate.Content(CertificateType.DEGREE, "B.Tech", null, null, new BigDecimal("8.00"),
                        LocalDate.of(2026, 6, 30), "Student", "E1"),
                "0x" + "1".repeat(64), "{}", "0x" + "2".repeat(64), UUID.randomUUID());
        certificate.markPending("[]");
        certificate.markIssued();
        certificate.requestRevocation(RevocationReason.ISSUED_IN_ERROR, "Wrong CGPA", NOW);
        return certificate;
    }
}