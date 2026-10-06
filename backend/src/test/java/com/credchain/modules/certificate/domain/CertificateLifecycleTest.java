package com.credchain.modules.certificate.domain;

import com.credchain.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Certificate and batch lifecycle")
class CertificateLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final String ROOT = "0x" + "a".repeat(64);
    private static final String TX = "0x" + "b".repeat(64);
    private static final String ISSUER = "0x" + "c".repeat(40);
    private static final UUID USER = UUID.randomUUID();

    @Test
    @DisplayName("batch: DRAFT -> QUEUED -> SUBMITTED -> ANCHORED")
    void batchHappyPath() {
        CertificateBatch batch = CertificateBatch.createDraft(UUID.randomUUID(), "Convocation 2026", null, USER, NOW);

        batch.queue(ROOT, 3, ISSUER, 11155111L, USER, NOW);
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.QUEUED);
        assertThat(batch.getNextAttemptAt()).isEqualTo(NOW);


        batch.markSubmitted(TX);
        batch.markAnchored(123L, NOW);

        assertThat(batch.getStatus()).isEqualTo(BatchStatus.ANCHORED);
        assertThat(batch.getBlockNumber()).isEqualTo(123L);
        assertThat(batch.getNextAttemptAt()).isNull();
        assertThat(batch.expiresAtEpochSeconds()).isZero();
    }

    @Test
    @DisplayName("batch: cannot queue an empty batch, cannot anchor before submit, cannot edit once queued")
    void batchGuards() {
        CertificateBatch batch = CertificateBatch.createDraft(UUID.randomUUID(), "Empty", null, USER, NOW);
        assertThatThrownBy(() -> batch.queue(ROOT, 0, ISSUER, 1L, USER, NOW)).isInstanceOf(BusinessException.class);

        batch.queue(ROOT, 1, ISSUER, 1L, USER, NOW);
        assertThatThrownBy(() -> batch.markAnchored(1L, NOW)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(batch::requireDraft).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("batch: expiry must be in the future")
    void expiryInFuture() {
        assertThatThrownBy(() -> CertificateBatch.createDraft(UUID.randomUUID(), "Old", NOW.minusSeconds(1), USER, NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("certificate: DRAFT -> PENDING -> ISSUED -> REVOCATION_PENDING -> REVOKED")
    void certificateHappyPath() {
        Certificate cert = newCertificate();


        cert.markPending("[]");
        cert.markIssued();
        cert.requestRevocation(RevocationReason.ISSUED_IN_ERROR, "  wrong CGPA  ", NOW);
        assertThat(cert.getRevocationNote()).isEqualTo("wrong CGPA");
        cert.markRevoked(TX, NOW);

        assertThat(cert.getStatus()).isEqualTo(CertificateStatus.REVOKED);
        assertThat(cert.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("certificate: a draft cannot be revoked, a pending certificate cannot be issued twice")
    void certificateGuards() {
        Certificate cert = newCertificate();
        assertThatThrownBy(() -> cert.requestRevocation(RevocationReason.FRAUD, null, NOW))
                .isInstanceOf(BusinessException.class);

        cert.markPending("[]");
        cert.markIssued();
        assertThatThrownBy(cert::markIssued).isInstanceOf(BusinessException.class);
    }

    private static Certificate newCertificate() {
        Certificate.Content content = new Certificate.Content(
                CertificateType.DEGREE, "Bachelor of Technology", "Computer Science", "First Class",
                new BigDecimal("8.75"), LocalDate.of(2026, 6, 30), "Demo Student", "2022CS001");
        return Certificate.createDraft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "DEMO-2026-000001", content, "0x" + "1".repeat(64), "{}", "0x" + "2".repeat(64), USER);
    }
}