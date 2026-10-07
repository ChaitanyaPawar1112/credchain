package com.credchain.modules.verification.api;

import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import com.credchain.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Verification log")
class VerificationLogIntegrationTest extends AbstractIntegrationTest {

    private static final String BATCHES = "/api/v1/institution/certificate-batches";
    private static final String VERIFY = "/api/v1/public/verify/{certHash}";
    private static final String MY_LOG = "/api/v1/institution/verifications";
    private static final String ADMIN_LOG = "/api/v1/admin/verifications";

    @Autowired
    private InstitutionWalletRepository walletRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateBatchRepository batchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanLog() {
        jdbcTemplate.execute("TRUNCATE TABLE verification_logs");   // checks of unknown hashes belong to no institution
    }

    @Test
    @DisplayName("institution admin sees checks of their own certificates only, with totals per result")
    void institutionSeesOwnChecks() throws Exception {
        String adminA = onboardInstitution("LOG-AAA", "registrar@loga.test");
        String adminB = onboardInstitution("LOG-BBB", "registrar@logb.test");
        Certificate certificate = issuedCertificate(adminA, "Asha Patil");

        mockMvc.perform(get(VERIFY, certificate.getCertHash()).header(HttpHeaders.USER_AGENT, "Mozilla/5.0 Test"))
                .andExpect(status().isOk());
        mockMvc.perform(get(VERIFY, certificate.getCertHash())).andExpect(status().isOk());
        mockMvc.perform(get(VERIFY, "0x" + "9".repeat(64))).andExpect(status().isOk());   // unknown: nobody's

        mockMvc.perform(get(MY_LOG).header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChecks").value(2))
                .andExpect(jsonPath("$.byResult.VALID").value(2))
                .andExpect(jsonPath("$.byResult.FAKE").value(0))
                .andExpect(jsonPath("$.checks.content[0].method").value("HASH"))
                .andExpect(jsonPath("$.checks.content[0].certificateNumber").value(certificate.getCertificateNumber()))
                .andExpect(jsonPath("$.checks.content[0].studentName").value("Asha Patil"))
                .andExpect(jsonPath("$.checks.content[1].userAgent").value("Mozilla/5.0 Test"));

        mockMvc.perform(get(MY_LOG).header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChecks").value(0))
                .andExpect(jsonPath("$.checks.content").isEmpty());
    }

    @Test
    @DisplayName("super admin sees every check and can list only the FAKE ones; others get 403")
    void superAdminSeesEverything() throws Exception {
        String admin = onboardInstitution("LOG-CCC", "registrar@logc.test");
        Certificate certificate = issuedCertificate(admin, "Rahul Shinde");

        mockMvc.perform(get(VERIFY, certificate.getCertHash())).andExpect(status().isOk());
        mockMvc.perform(get(VERIFY, "0x" + "9".repeat(64))).andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/public/verify/pdf")
                        .file(new MockMultipartFile("file", "fake.pdf", "application/pdf", plainPdf())))
                .andExpect(jsonPath("$.status").value("FAKE"));
        mockMvc.perform(get(VERIFY, "hello")).andExpect(status().isBadRequest());   // invalid input: not logged

        String superToken = superAdminToken();
        mockMvc.perform(get(ADMIN_LOG).header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChecks").value(3))
                .andExpect(jsonPath("$.byResult.VALID").value(1))
                .andExpect(jsonPath("$.byResult.NOT_FOUND").value(1))
                .andExpect(jsonPath("$.byResult.FAKE").value(1));

        mockMvc.perform(get(ADMIN_LOG).param("result", "FAKE").header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(jsonPath("$.checks.totalElements").value(1))
                .andExpect(jsonPath("$.checks.content[0].method").value("PDF"))
                .andExpect(jsonPath("$.checks.content[0].certHash").doesNotExist());

        mockMvc.perform(get(ADMIN_LOG).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private static byte[] plainPdf() {
        Document document = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, out);
        document.open();
        document.add(new Paragraph("Not a CredChain certificate"));
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
