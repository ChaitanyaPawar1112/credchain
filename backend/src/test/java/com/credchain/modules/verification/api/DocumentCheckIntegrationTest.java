package com.credchain.modules.verification.api;

import com.credchain.modules.certificate.application.CertificatePdfService;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.fraud.infrastructure.DocumentAnalysis;
import com.credchain.modules.fraud.infrastructure.FraudServiceException;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import com.credchain.support.AbstractIntegrationTest;
import com.credchain.support.StubFraudServiceClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Public check of a certificate photo or scan (AI fraud detection, no login)")
class DocumentCheckIntegrationTest extends AbstractIntegrationTest {

    private static final String BATCHES = "/api/v1/institution/certificate-batches";
    private static final String CERTIFICATES = "/api/v1/institution/certificates";
    private static final String VERIFY_DOCUMENT = "/api/v1/public/verify/document";
    private static final byte[] PHOTO = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    @Autowired
    private StubFraudServiceClient fraudService;

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

    @BeforeEach
    void resetStub() {
        fraudService.reset();
        jdbcTemplate.update("DELETE FROM verification_logs");
    }

    @Test
    @DisplayName("a photo whose QR and printed details match a valid certificate is LOW risk, every field PASSED")
    void matchingPhoto() throws Exception {
        String admin = onboardInstitution("DOC-ONE", "registrar@docone.test");
        Certificate certificate = issuedCertificate(admin, "Sneha Joshi");
        fraudService.respondWith(StubFraudServiceClient.analysis(certificate.getCertHash(), printedText(certificate, "Sneha Joshi")));

        upload(PHOTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("DOCUMENT_ANALYSIS"))
                .andExpect(jsonPath("$.riskScore").value(0))
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("match the official record")))
                .andExpect(jsonPath("$.qrFound").value(true))
                .andExpect(jsonPath("$.textRead").value(true))
                .andExpect(jsonPath("$.record.status").value("VALID"))
                .andExpect(jsonPath("$.record.certificate.studentName").value("Sneha Joshi"))
                .andExpect(jsonPath("$.fieldChecks[*].result").value(not(hasItem("FAILED"))))
                .andExpect(jsonPath("$.fieldChecks[0].field").value("Student name"))
                .andExpect(jsonPath("$.elaImage").value("iVBORw0KGgo="))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("salt"))));

        Map<String, Object> log = onlyLog();
        assertThat(log).containsEntry("method", "DOCUMENT").containsEntry("result", "VALID");
        assertThat(((Number) log.get("risk_score")).intValue()).isZero();
        assertThat(log.get("certificate_id")).isEqualTo(certificate.getId());
    }

    @Test
    @DisplayName("the real QR pasted on a document with another name is HIGH risk and logged as FAKE")
    void changedName() throws Exception {
        String admin = onboardInstitution("DOC-TWO", "registrar@doctwo.test");
        Certificate certificate = issuedCertificate(admin, "Sneha Joshi");
        fraudService.respondWith(StubFraudServiceClient.analysis(certificate.getCertHash(),
                printedText(certificate, "Rahul Sharma").replace("8.50", "9.90")));

        upload(PHOTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.riskScore").value(75))
                .andExpect(jsonPath("$.signals[0].code").value("FIELD_MISMATCH_STUDENT_NAME"))
                .andExpect(jsonPath("$.fieldChecks[0].result").value("FAILED"))
                .andExpect(jsonPath("$.fieldChecks[0].officialValue").value("Sneha Joshi"))
                .andExpect(jsonPath("$.record.status").value("VALID"));   // the QR's certificate itself is fine

        assertThat(onlyLog()).containsEntry("method", "DOCUMENT").containsEntry("result", "FAKE");
    }

    @Test
    @DisplayName("a QR code to a certificate CredChain never issued is HIGH risk")
    void unknownCertificate() throws Exception {
        fraudService.respondWith(StubFraudServiceClient.analysis("0x" + "c".repeat(64), "Some College certifies Rahul Sharma"));

        upload(PHOTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.signals[0].code").value("QR_UNKNOWN_CERTIFICATE"))
                .andExpect(jsonPath("$.record.status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.fieldChecks").isEmpty());

        Map<String, Object> log = onlyLog();
        assertThat(log).containsEntry("result", "FAKE");
        assertThat(log.get("certificate_id")).isNull();
    }

    @Test
    @DisplayName("a revoked certificate's QR is HIGH risk even when the details match")
    void revokedCertificate() throws Exception {
        String admin = onboardInstitution("DOC-THREE", "registrar@docthree.test");
        Certificate certificate = issuedCertificate(admin, "Aditya Shinde");
        postJson(CERTIFICATES + "/" + certificate.getId() + "/revoke", "{\"reason\": \"FRAUD\"}", admin)
                .andExpect(status().is2xxSuccessful());
        fraudService.respondWith(StubFraudServiceClient.analysis(certificate.getCertHash(), printedText(certificate, "Aditya Shinde")));

        upload(PHOTO)
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.record.status").value("REVOKED"))
                .andExpect(jsonPath("$.signals[0].code").value("CERTIFICATE_REVOKED"));
    }

    @Test
    @DisplayName("no QR and no readable text: the service's own signals, nothing compared")
    void noQr() throws Exception {
        fraudService.respondWith(StubFraudServiceClient.analysis(null, null,
                new DocumentAnalysis.Signal("EDITING_SOFTWARE", DocumentAnalysis.Severity.HIGH, 30, "Saved by Photoshop")));

        upload(PHOTO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskScore").value(50))
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.signals[0].code").value("EDITING_SOFTWARE"))
                .andExpect(jsonPath("$.qrFound").value(false))
                .andExpect(jsonPath("$.textRead").value(false))
                .andExpect(jsonPath("$.record").doesNotExist());

        assertThat(onlyLog()).containsEntry("result", "NOT_FOUND");
    }

    @Test
    @DisplayName("a CredChain PDF gets the exact PDF check, without calling the AI service")
    void credChainPdfGoesToExactCheck() throws Exception {
        String admin = onboardInstitution("DOC-FOUR", "registrar@docfour.test");
        Certificate certificate = issuedCertificate(admin, "Asha Patil");
        pdfService.generateMissing(10);
        byte[] pdf = mockMvc.perform(get(CERTIFICATES + "/{id}/pdf", certificate.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        upload(pdf)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("EXACT_PDF"))
                .andExpect(jsonPath("$.pdfCheck.status").value("VALID"))
                .andExpect(jsonPath("$.riskScore").doesNotExist());

        assertThat(fraudService.calls()).isZero();
        assertThat(onlyLog()).containsEntry("method", "PDF").containsEntry("result", "VALID");
    }

    @Test
    @DisplayName("service down: 503 FRAUD_SERVICE_UNAVAILABLE and nothing logged")
    void serviceDown() throws Exception {
        fraudService.failWith(FraudServiceException.unavailable("down", null));
        upload(PHOTO)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FRAUD_SERVICE_UNAVAILABLE"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM verification_logs", Long.class)).isZero();
    }

    @Test
    @DisplayName("a file the service refuses: 400 with its message; an empty file: 400")
    void badFiles() throws Exception {
        fraudService.failWith(FraudServiceException.fileRejected("Upload a PDF, JPEG, PNG or WebP file."));
        upload("PK zip".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Upload a PDF, JPEG, PNG or WebP file."));

        upload(new byte[0]).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("photos up to 5 MB are accepted, but the PDF check keeps its 2 MB limit")
    void sizeLimits() throws Exception {
        fraudService.respondWith(StubFraudServiceClient.analysis(null, null));
        byte[] threeMb = new byte[3 * 1024 * 1024];
        threeMb[0] = (byte) 0xFF;
        upload(threeMb).andExpect(status().isOk());

        mockMvc.perform(multipart("/api/v1/public/verify/pdf")
                        .file(new MockMultipartFile("file", "big.pdf", "application/pdf", threeMb)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("2 MB")));
    }

    @Test
    @DisplayName("the super admin's activity list shows photo checks with their risk score")
    void activityShowsRisk() throws Exception {
        fraudService.respondWith(StubFraudServiceClient.analysis(null, null));
        upload(PHOTO).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/verifications").header(HttpHeaders.AUTHORIZATION, bearer(superAdminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checks.content[0].method").value("DOCUMENT"))
                .andExpect(jsonPath("$.checks.content[0].riskScore").value(20));
    }

    // ---------- helpers ----------

    private ResultActions upload(byte[] content) throws Exception {
        return mockMvc.perform(multipart(VERIFY_DOCUMENT)
                .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", content)));   // no login
    }

    private Map<String, Object> onlyLog() {
        return jdbcTemplate.queryForMap("SELECT * FROM verification_logs");
    }

    /** What OCR would read from a photo of this certificate, printed with the given name. */
    private static String printedText(Certificate certificate, String name) {
        return """
                Institute certificate. This is to certify that %s has been awarded Bachelor of Technology
                B.Tech Computer Engineering. Enrollment No %s. Certificate No %s. CGPA 8.50. Scan to verify
                """.formatted(name, certificate.getEnrollmentNo(), certificate.getCertificateNumber());
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
