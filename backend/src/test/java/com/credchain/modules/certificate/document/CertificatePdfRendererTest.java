package com.credchain.modules.certificate.document;

import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.domain.CertificateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CertificatePdfRenderer")
class CertificatePdfRendererTest {

    private static final Instant NOW = Instant.parse("2026-10-07T09:00:00Z");
    private static final UUID INSTITUTION = UUID.randomUUID();
    private static final String ROOT = "0x" + "a".repeat(64);
    private static final String TX = "0x" + "b".repeat(64);
    private static final String HASH = "0x" + "c".repeat(64);
    private static final String ISSUER = "0x" + "d".repeat(40);
    private static final String CONTRACT = "0x1EA82E244e3b38Fc294075Ab03D30F5e3E2947b0";
    private static final String PAYLOAD = "{\"certificateNumber\":\"SIT-AUR-2026-000001\",\"v\":\"1\"}";
    private static final String PROOF = "[\"0x" + "e".repeat(64) + "\"]";
    private static final String VERIFY_URL = "http://localhost:5173/verify/" + HASH;

    private final CertificatePdfRenderer renderer = new CertificatePdfRenderer();

    @Test
    @DisplayName("printable certificate with the student's details and blockchain references")
    void rendersVisibleContent() throws Exception {
        byte[] pdf = renderer.render(document());

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        assertThat(text).contains("Shri Institute of Technology", "Asha Kulkarni", "2022CS001",
                "Bachelor of Technology", "Computer Science and Engineering", "CGPA 8.75 / 10",
                "30 June 2026", "SIT-AUR-2026-000001", "Ethereum Sepolia", HASH, TX);
        reader.close();
    }

    @Test
    @DisplayName("embeds the hashed payload, Merkle proof and chain data for later verification")
    void embedsVerificationData() throws Exception {
        PdfReader reader = new PdfReader(renderer.render(document()));
        Map<String, String> info = reader.getInfo();

        assertThat(info).containsEntry("CredChain-Version", "1")
                .containsEntry("CredChain-CertHash", HASH)
                .containsEntry("CredChain-Payload", PAYLOAD)
                .containsEntry("CredChain-Proof", PROOF)
                .containsEntry("CredChain-MerkleRoot", ROOT)
                .containsEntry("CredChain-ChainId", "11155111")
                .containsEntry("CredChain-Contract", CONTRACT)
                .containsEntry("Creator", "CredChain");
        reader.close();
    }

    @Test
    @DisplayName("refuses certificates that are not on-chain yet")
    void rejectsPendingCertificate() {
        CertificateBatch batch = batch();
        Certificate certificate = certificate(batch);
        certificate.markPending(PROOF);

        assertThatThrownBy(() -> CertificateDocument.of(certificate, batch, "Shri Institute of Technology", CONTRACT, VERIFY_URL))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- fixtures ----------

    private static CertificateDocument document() {
        CertificateBatch batch = batch();
        Certificate certificate = certificate(batch);
        certificate.markPending(PROOF);
        batch.markSubmitted(TX);
        batch.markAnchored(11_900_000L, NOW);
        certificate.markIssued();
        return CertificateDocument.of(certificate, batch, "Shri Institute of Technology", CONTRACT, VERIFY_URL);
    }

    private static CertificateBatch batch() {
        CertificateBatch batch = CertificateBatch.createDraft(INSTITUTION, "Convocation 2026", null, UUID.randomUUID(), NOW);
        ReflectionTestUtils.setField(batch, "id", UUID.randomUUID());
        batch.queue(ROOT, 1, ISSUER, 11155111L, UUID.randomUUID(), NOW);
        return batch;
    }

    private static Certificate certificate(CertificateBatch batch) {
        return Certificate.createDraft(INSTITUTION, batch.getId(), UUID.randomUUID(), "SIT-AUR-2026-000001",
                new Certificate.Content(CertificateType.DEGREE, "Bachelor of Technology", "Computer Science and Engineering",
                        "First Class with Distinction", new BigDecimal("8.75"), LocalDate.of(2026, 6, 30),
                        "Asha Kulkarni", "2022CS001"),
                "0x" + "1".repeat(64), PAYLOAD, HASH, UUID.randomUUID());
    }
}