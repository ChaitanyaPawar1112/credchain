package com.credchain.modules.verification.application;

import com.credchain.common.exception.BusinessException;
import com.credchain.modules.blockchain.config.BlockchainProperties;
import com.credchain.modules.blockchain.contract.CredentialRegistryClient;
import com.credchain.modules.blockchain.infrastructure.BlockchainException;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.domain.CertificateType;
import com.credchain.modules.certificate.domain.RevocationReason;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.institution.infrastructure.InstitutionRepository;
import com.credchain.modules.verification.api.dto.VerificationResponse;
import com.credchain.modules.verification.domain.VerificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

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

@DisplayName("PublicVerificationService")
class PublicVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    private static final UUID INSTITUTION = UUID.randomUUID();
    private static final String HASH = "0x" + "2".repeat(64);

    private final CertificateRepository certificateRepository = mock(CertificateRepository.class);
    private final CertificateBatchRepository batchRepository = mock(CertificateBatchRepository.class);
    private final InstitutionRepository institutionRepository = mock(InstitutionRepository.class);
    private final BlockchainProperties blockchainProperties = mock(BlockchainProperties.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<CredentialRegistryClient> registryProvider = mock(ObjectProvider.class);
    private final CredentialRegistryClient registry = mock(CredentialRegistryClient.class);
    private final PublicVerificationService service = new PublicVerificationService(certificateRepository,
            batchRepository, institutionRepository, blockchainProperties, registryProvider,
            Clock.fixed(NOW, ZoneOffset.UTC));

    // ---------- decision rules (on-chain: null = not asked, 0 NOT_FOUND, 1 VALID, 2 REVOKED, 3 EXPIRED) ----------

    @Test
    @DisplayName("ISSUED + VALID on-chain = VALID; database only (chain not asked) = VALID")
    void valid() {
        assertThat(PublicVerificationService.decide(issued(), batch(null), 1, NOW)).isEqualTo(VerificationStatus.VALID);
        assertThat(PublicVerificationService.decide(issued(), batch(null), null, NOW)).isEqualTo(VerificationStatus.VALID);
    }

    @Test
    @DisplayName("revoked on-chain OR in the database (even still pending) = REVOKED")
    void revoked() {
        assertThat(PublicVerificationService.decide(issued(), batch(null), 2, NOW)).isEqualTo(VerificationStatus.REVOKED);
        Certificate pending = issued();
        pending.requestRevocation(RevocationReason.FRAUD, null, NOW);
        assertThat(PublicVerificationService.decide(pending, batch(null), 1, NOW)).isEqualTo(VerificationStatus.REVOKED);
        assertThat(PublicVerificationService.decide(pending, batch(null), null, NOW)).isEqualTo(VerificationStatus.REVOKED);
    }

    @Test
    @DisplayName("expiry date passed (database or chain) = EXPIRED; NOT_FOUND on-chain = NOT_FOUND")
    void expiredAndMissing() {
        assertThat(PublicVerificationService.decide(issued(), batch(NOW.minusSeconds(1)), null, NOW))
                .isEqualTo(VerificationStatus.EXPIRED);
        assertThat(PublicVerificationService.decide(issued(), batch(null), 3, NOW)).isEqualTo(VerificationStatus.EXPIRED);
        assertThat(PublicVerificationService.decide(issued(), batch(null), 0, NOW)).isEqualTo(VerificationStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("hash input: case and missing 0x are fine; anything else is a 400")
    void normalize() {
        assertThat(PublicVerificationService.normalize("  " + "AB".repeat(32) + " ")).isEqualTo("0x" + "ab".repeat(32));
        assertThatThrownBy(() -> PublicVerificationService.normalize("0x1234")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PublicVerificationService.normalize("not-a-hash")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PublicVerificationService.normalize(null)).isInstanceOf(BusinessException.class);
    }

    // ---------- with the blockchain ----------

    @Test
    @DisplayName("revoked on-chain but ISSUED in our database: REVOKED wins, blockchainChecked = true")
    void chainRevokedWins() {
        givenIssuedCertificate();
        when(registryProvider.getIfAvailable()).thenReturn(registry);
        when(registry.verifyInBatch(any(), any(), any())).thenReturn(CredentialRegistryClient.STATUS_REVOKED);

        VerificationResponse response = service.verifyByHash(HASH);

        assertThat(response.status()).isEqualTo(VerificationStatus.REVOKED);
        assertThat(response.blockchainChecked()).isTrue();
    }

    @Test
    @DisplayName("blockchain unreachable: falls back to the database and says so")
    void chainDownFallsBack() {
        givenIssuedCertificate();
        when(registryProvider.getIfAvailable()).thenReturn(registry);
        when(registry.verifyInBatch(any(), any(), any())).thenThrow(new BlockchainException("RPC unreachable"));

        VerificationResponse response = service.verifyByHash(HASH);

        assertThat(response.status()).isEqualTo(VerificationStatus.VALID);
        assertThat(response.blockchainChecked()).isFalse();
        assertThat(response.message()).contains("could not be checked");
    }

    @Test
    @DisplayName("in the database but NOT FOUND on-chain: NOT_FOUND, no certificate details shown")
    void inDatabaseButNotOnChain() {
        givenIssuedCertificate();
        when(registryProvider.getIfAvailable()).thenReturn(registry);
        when(registry.verifyInBatch(any(), any(), any())).thenReturn(CredentialRegistryClient.STATUS_NOT_FOUND);

        VerificationResponse response = service.verifyByHash(HASH);

        assertThat(response.status()).isEqualTo(VerificationStatus.NOT_FOUND);
        assertThat(response.certificate()).isNull();
    }

    // ---------- helpers ----------

    private void givenIssuedCertificate() {
        Certificate certificate = issued();
        CertificateBatch batch = batch(null);
        when(certificateRepository.findByCertHash(HASH)).thenReturn(Optional.of(certificate));
        when(batchRepository.findById(certificate.getBatchId())).thenReturn(Optional.of(batch));
        Institution institution = mock(Institution.class);
        when(institution.getName()).thenReturn("SIT College");
        when(institutionRepository.findById(INSTITUTION)).thenReturn(Optional.of(institution));
    }

    /** An anchored-style batch; expiresAt is set directly because the factory only accepts future dates. */
    private static CertificateBatch batch(Instant expiresAt) {
        CertificateBatch batch = CertificateBatch.createDraft(INSTITUTION, "Convocation", null, UUID.randomUUID(), NOW);
        batch.queue("0x" + "a".repeat(64), 1, "0x" + "b".repeat(40), 11155111L, UUID.randomUUID(), NOW);
        ReflectionTestUtils.setField(batch, "expiresAt", expiresAt);
        return batch;
    }

    private static Certificate issued() {
        Certificate certificate = Certificate.createDraft(INSTITUTION, UUID.randomUUID(), UUID.randomUUID(), "N-1",
                new Certificate.Content(CertificateType.DEGREE, "B.Tech", null, null, new BigDecimal("8.00"),
                        LocalDate.of(2026, 6, 30), "Student", "E1"),
                "0x" + "1".repeat(64), "{}", HASH, UUID.randomUUID());
        certificate.markPending("[]");
        certificate.markIssued();
        return certificate;
    }
}
