package com.credchain.modules.certificate.application;

import com.credchain.modules.blockchain.contract.CredentialRegistryClient;
import com.credchain.modules.blockchain.infrastructure.BlockchainException;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.domain.CertificateStatus;
import com.credchain.modules.certificate.domain.CertificateType;
import com.credchain.modules.certificate.domain.ChainCheckResult;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ChainReconciler (blockchain vs database)")
class ChainReconcilerTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final UUID INSTITUTION = UUID.randomUUID();
    private static final String ROOT = "0x" + "a".repeat(64);

    private final CredentialRegistryClient registry = mock(CredentialRegistryClient.class);
    private final CertificateBatchRepository batchRepository = mock(CertificateBatchRepository.class);
    private final ChainReconciler reconciler = new ChainReconciler(registry, batchRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));

    // on-chain: 0 NOT_FOUND, 1 VALID, 2 REVOKED, 3 EXPIRED
    @ParameterizedTest(name = "{0} in database + {1} on-chain -> {2}")
    @CsvSource({
            "ISSUED, 1, MATCH",
            "ISSUED, 3, MATCH",
            "ISSUED, 2, MISMATCH",
            "ISSUED, 0, MISMATCH",
            "REVOCATION_PENDING, 1, MATCH",
            "REVOCATION_PENDING, 2, MATCH",
            "REVOCATION_PENDING, 0, MISMATCH",
            "REVOKED, 2, MATCH",
            "REVOKED, 1, MISMATCH",
            "REVOKED, 0, MISMATCH"
    })
    @DisplayName("comparison rules")
    void rules(CertificateStatus database, int onChain, ChainCheckResult expected) {
        assertThat(ChainReconciler.compare(database, onChain).result()).isEqualTo(expected);
    }

    @Test
    @DisplayName("VALID on-chain: MATCH recorded with the check time")
    void recordsMatch() {
        Certificate certificate = issuedCertificate();
        givenBatch(certificate);
        when(registry.verifyInBatch(any(), any(), any())).thenReturn(CredentialRegistryClient.STATUS_VALID);

        assertThat(reconciler.check(certificate)).isEqualTo(ChainCheckResult.MATCH);
        assertThat(certificate.getChainCheckResult()).isEqualTo(ChainCheckResult.MATCH);
        assertThat(certificate.getChainCheckedAt()).isEqualTo(NOW);
        assertThat(certificate.getStatus()).isEqualTo(CertificateStatus.ISSUED);   // never changed by the check
    }

    @Test
    @DisplayName("revoked outside the app: MISMATCH recorded, database status left alone")
    void recordsMismatch() {
        Certificate certificate = issuedCertificate();
        givenBatch(certificate);
        when(registry.verifyInBatch(any(), any(), any())).thenReturn(CredentialRegistryClient.STATUS_REVOKED);

        assertThat(reconciler.check(certificate)).isEqualTo(ChainCheckResult.MISMATCH);
        assertThat(certificate.getChainCheckNote()).isEqualTo("ISSUED in the database but REVOKED on-chain");
        assertThat(certificate.getStatus()).isEqualTo(CertificateStatus.ISSUED);
    }

    @Test
    @DisplayName("missing batch row: MISMATCH (broken data), no blockchain call needed")
    void missingBatch() {
        Certificate certificate = issuedCertificate();
        when(batchRepository.findById(certificate.getBatchId())).thenReturn(Optional.empty());

        assertThat(reconciler.check(certificate)).isEqualTo(ChainCheckResult.MISMATCH);
        assertThat(certificate.getChainCheckNote()).startsWith("Cannot check");
    }

    @Test
    @DisplayName("RPC down: error passed to the job, nothing recorded (checked again next run)")
    void rpcDown() {
        Certificate certificate = issuedCertificate();
        givenBatch(certificate);
        when(registry.verifyInBatch(any(), any(), any())).thenThrow(new BlockchainException("RPC unreachable"));

        assertThatThrownBy(() -> reconciler.check(certificate)).isInstanceOf(BlockchainException.class);
        assertThat(certificate.getChainCheckedAt()).isNull();
    }

    // ---------- helpers ----------

    private void givenBatch(Certificate certificate) {
        CertificateBatch batch = CertificateBatch.createDraft(INSTITUTION, "Convocation", null, UUID.randomUUID(), NOW);
        batch.queue(ROOT, 1, "0x" + "b".repeat(40), 11155111L, UUID.randomUUID(), NOW);
        when(batchRepository.findById(certificate.getBatchId())).thenReturn(Optional.of(batch));
    }

    private static Certificate issuedCertificate() {
        Certificate certificate = Certificate.createDraft(INSTITUTION, UUID.randomUUID(), UUID.randomUUID(), "N-1",
                new Certificate.Content(CertificateType.DEGREE, "B.Tech", null, null, new BigDecimal("8.00"),
                        LocalDate.of(2026, 6, 30), "Student", "E1"),
                "0x" + "1".repeat(64), "{}", "0x" + "2".repeat(64), UUID.randomUUID());
        certificate.markPending("[]");
        certificate.markIssued();
        return certificate;
    }
}