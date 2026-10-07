package com.credchain.modules.verification.api;

import com.credchain.modules.certificate.application.CertificatePdfService;
import com.credchain.modules.certificate.crypto.CertificateHasher;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import com.credchain.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Public verification by PDF upload (no login)")
class PdfVerificationIntegrationTest extends AbstractIntegrationTest {

    private static final String BATCHES = "/api/v1/institution/certificate-batches";
    private static final String CERTIFICATES = "/api/v1/institution/certificates";
    private static final String VERIFY_PDF = "/api/v1/public/verify/pdf";

    @Autowired
    private InstitutionWalletRepository walletRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateBatchRepository batchRepository;

    @Autowired
    private CertificatePdfService pdfService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("the original PDF is VALID: all five checks pass and no secret is shown")
    void originalIsValid() throws Exception {
        String admin = onboardInstitution("PDFV-ONE", "registrar@pdfvone.test");
        Certificate certificate = issuedCertificate(admin, "Asha Patil");
        byte[] pdf = downloadPdf(admin, certificate);

        upload(pdf)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.checks", hasSize(5)))
                .andExpect(jsonPath("$.checks[*].result", everyItem(is("PASSED"))))
                .andExpect(jsonPath("$.record.certHash").value(certificate.getCertHash()))
                .andExpect(jsonPath("$.record.certificate.studentName").value("Asha Patil"))
                .andExpect(content().string(not(containsString(certificate.getSalt()))));
    }

    @Test
    @DisplayName("printed text edited (e.g. a new CGPA drawn on the page): FAKE, with the official record to compare")
    void editedPrintedTextIsFake() throws Exception {
        String admin = onboardInstitution("PDFV-TWO", "registrar@pdfvtwo.test");
        Certificate certificate = issuedCertificate(admin, "Rahul Shinde");
        byte[] edited = edit(downloadPdf(admin, certificate), stamper -> {
            try {
                PdfContentByte canvas = stamper.getOverContent(1);
                canvas.beginText();
                canvas.setFontAndSize(BaseFont.createFont(), 12);
                canvas.showTextAligned(PdfContentByte.ALIGN_CENTER, "CGPA 9.90 / 10", 420, 200, 0);
                canvas.endText();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        upload(edited)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAKE"))
                .andExpect(jsonPath("$.checks[4].name").value("File unchanged since it was issued"))
                .andExpect(jsonPath("$.checks[4].result").value("FAILED"))
                .andExpect(jsonPath("$.record.certificate.cgpa").value("8.50"));
    }

    @Test
    @DisplayName("embedded certificate data edited (another name): FAKE at the hash check, no record shown")
    void editedDataIsFake() throws Exception {
        String admin = onboardInstitution("PDFV-THREE", "registrar@pdfvthree.test");
        Certificate certificate = issuedCertificate(admin, "Asha Patil");
        byte[] edited = editInfo(downloadPdf(admin, certificate), info ->
                info.put("CredChain-Payload", info.get("CredChain-Payload").replace("Asha Patil", "Someone Else")));

        upload(edited)
                .andExpect(jsonPath("$.status").value("FAKE"))
                .andExpect(jsonPath("$.checks[1].result").value("FAILED"))
                .andExpect(jsonPath("$.record").doesNotExist());
    }

    @Test
    @DisplayName("forged data with a matching new hash: FAKE at the Merkle proof check")
    void forgedHashIsFake() throws Exception {
        String admin = onboardInstitution("PDFV-FOUR", "registrar@pdfvfour.test");
        Certificate certificate = issuedCertificate(admin, "Asha Patil");
        byte[] forged = editInfo(downloadPdf(admin, certificate), info -> {
            String payload = info.get("CredChain-Payload").replace("Asha Patil", "Someone Else");
            info.put("CredChain-Payload", payload);
            info.put("CredChain-CertHash", CertificateHasher.hashOf(payload));
        });

        upload(forged)
                .andExpect(jsonPath("$.status").value("FAKE"))
                .andExpect(jsonPath("$.checks[1].result").value("PASSED"))
                .andExpect(jsonPath("$.checks[2].result").value("FAILED"));
    }

    @Test
    @DisplayName("the original PDF of a revoked certificate is REVOKED")
    void revokedOriginal() throws Exception {
        String admin = onboardInstitution("PDFV-FIVE", "registrar@pdfvfive.test");
        Certificate certificate = issuedCertificate(admin, "Revoked Student");
        byte[] pdf = downloadPdf(admin, certificate);
        postJson(CERTIFICATES + "/" + certificate.getId() + "/revoke",
                "{\"reason\": \"FRAUD\", \"note\": \"internal note\"}", admin)
                .andExpect(status().isAccepted());

        upload(pdf)
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.record.revocation.reason").value("FRAUD"))
                .andExpect(content().string(not(containsString("internal note"))));
    }

    @Test
    @DisplayName("PDF made before fingerprints were stored: compared with the stored file instead")
    void olderPdfWithoutFingerprint() throws Exception {
        String admin = onboardInstitution("PDFV-SIX", "registrar@pdfvsix.test");
        Certificate certificate = issuedCertificate(admin, "Asha Patil");
        byte[] pdf = downloadPdf(admin, certificate);
        jdbcTemplate.update("UPDATE certificates SET pdf_sha256 = NULL WHERE id = ?", certificate.getId());

        upload(pdf)
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.checks[4].result").value("PASSED"));
    }

    @Test
    @DisplayName("any other PDF is FAKE; a file that is not a PDF is a 400")
    void otherFiles() throws Exception {
        upload(plainPdf())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAKE"))
                .andExpect(jsonPath("$.checks", hasSize(1)))
                .andExpect(jsonPath("$.checks[0].result").value("FAILED"));

        upload("name,grade\nAsha,A".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // ---------- helpers ----------

    private ResultActions upload(byte[] content) throws Exception {
        return mockMvc.perform(multipart(VERIFY_PDF)
                .file(new MockMultipartFile("file", "certificate.pdf", "application/pdf", content)));   // no login
    }

    private byte[] downloadPdf(String admin, Certificate certificate) throws Exception {
        pdfService.generateMissing(10);
        return mockMvc.perform(get(CERTIFICATES + "/{id}/pdf", certificate.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    /** Re-saves the PDF the way a PDF editor would, applying a change. */
    private static byte[] edit(byte[] pdf, Consumer<PdfStamper> change) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfStamper stamper = new PdfStamper(reader, out);
        change.accept(stamper);
        stamper.close();
        reader.close();
        return out.toByteArray();
    }

    private static byte[] editInfo(byte[] pdf, Consumer<Map<String, String>> change) throws Exception {
        PdfReader original = new PdfReader(pdf);
        Map<String, String> info = new HashMap<>(original.getInfo());
        original.close();
        change.accept(info);
        return edit(pdf, stamper -> stamper.setInfoDictionary(info));
    }

    private static byte[] plainPdf() {
        Document document = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.open();
        document.add(new Paragraph("Certificate of Excellence - Asha Patil"));
        document.close();
        return out.toByteArray();
    }

    /** Creates a certificate and simulates the chain confirming its batch (blockchain is off in tests). */
    private Certificate issuedCertificate(String admin, String studentName) throws Exception {
        activateWallet(admin);
        String studentId = createStudent(admin, "2022CS001", studentName);
        String batchId = createBatch(admin);
        String certificateId = addCertificate(admin, batchId, studentId);
        mockMvc.perform(post(BATCHES + "/{id}/issue", batchId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isAccepted());

        CertificateBatch batch = batchRepository.findById(UUID.fromString(batchId)).orElseThrow();
        batch.markSubmitted("0x" + "e".repeat(64));
        batch.markAnchored(1L, Instant.now());
        batchRepository.save(batch);
        Certificate certificate = certificateRepository.findById(UUID.fromString(certificateId)).orElseThrow();
        certificate.markIssued();
        return certificateRepository.save(certificate);
    }

    private String createBatch(String admin) throws Exception {
        String body = postJson(BATCHES, "{\"title\": \"Convocation\"}", admin)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String addCertificate(String admin, String batchId, String studentId) throws Exception {
        String body = postJson(BATCHES + "/" + batchId + "/certificates", """
                {"studentId": "%s", "type": "DEGREE", "title": "Bachelor of Technology",
                 "cgpa": 8.50, "awardedOn": "2026-06-30"}
                """.formatted(studentId), admin)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void activateWallet(String adminToken) throws Exception {
        String profile = mockMvc.perform(get("/api/v1/institution/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andReturn().getResponse().getContentAsString();
        UUID institutionId = UUID.fromString(JsonPath.read(profile, "$.id"));

        InstitutionWallet wallet = walletRepository.findByInstitutionId(institutionId).orElseThrow();
        wallet.markActivated(null, Instant.now());
        walletRepository.save(wallet);
    }
}
