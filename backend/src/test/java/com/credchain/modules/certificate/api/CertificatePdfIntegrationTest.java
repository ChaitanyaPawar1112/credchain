package com.credchain.modules.certificate.api;

import com.credchain.modules.certificate.application.CertificatePdfService;
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
import org.openpdf.text.pdf.PdfReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Certificate PDF")
class CertificatePdfIntegrationTest extends AbstractIntegrationTest {

    private static final String BATCHES = "/api/v1/institution/certificate-batches";
    private static final String CERTIFICATES = "/api/v1/institution/certificates";

    @Autowired
    private CertificatePdfService pdfService;

    @Autowired
    private InstitutionWalletRepository walletRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateBatchRepository batchRepository;

    @Test
    @DisplayName("PDF is created after anchoring and can then be downloaded")
    void createdThenDownloaded() throws Exception {
        String admin = onboardInstitution("PDF-ONE", "registrar@pdfone.test");
        String certificateId = issuedCertificate(admin);

        mockMvc.perform(get(CERTIFICATES + "/{id}/pdf", certificateId).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_JSON))   // what a browser/Swagger sends
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERTIFICATE_PDF_NOT_READY"));

        assertThat(pdfService.generateMissing(10)).isEqualTo(1);
        assertThat(pdfService.generateMissing(10)).isZero();   // never made twice

        Certificate certificate = certificateRepository.findById(UUID.fromString(certificateId)).orElseThrow();
        byte[] pdf = mockMvc.perform(get(CERTIFICATES + "/{id}/pdf", certificateId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + certificate.getCertificateNumber() + ".pdf\""))
                .andReturn().getResponse().getContentAsByteArray();

        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getInfo())
                .containsEntry("CredChain-CertHash", certificate.getCertHash())
                .containsEntry("CredChain-Payload", certificate.getCanonicalPayload());
        reader.close();
    }

    @Test
    @DisplayName("draft certificates get no PDF")
    void draftsAreSkipped() throws Exception {
        String admin = onboardInstitution("PDF-TWO", "registrar@pdftwo.test");
        String student = createStudent(admin, "2022CS001", "Student One");
        addCertificate(admin, createBatch(admin), student);

        assertThat(pdfService.generateMissing(10)).isZero();
    }

    @Test
    @DisplayName("another institution cannot download my certificate (404)")
    void tenantIsolation() throws Exception {
        String adminA = onboardInstitution("PDF-AAA", "registrar@pdfa.test");
        String adminB = onboardInstitution("PDF-BBB", "registrar@pdfb.test");
        String certificateId = issuedCertificate(adminA);
        pdfService.generateMissing(10);

        mockMvc.perform(get(CERTIFICATES + "/{id}/pdf", certificateId).header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    /** Creates a certificate and simulates the chain confirming its batch (blockchain is off in tests). */
    private String issuedCertificate(String admin) throws Exception {
        activateWallet(admin);
        String student = createStudent(admin, "2022CS001", "Student One");
        String batchId = createBatch(admin);
        String certificateId = addCertificate(admin, batchId, student);
        mockMvc.perform(post(BATCHES + "/{id}/issue", batchId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isAccepted());

        CertificateBatch batch = batchRepository.findById(UUID.fromString(batchId)).orElseThrow();
        batch.markSubmitted("0x" + "b".repeat(64));
        batch.markAnchored(1L, Instant.now());
        batchRepository.save(batch);
        Certificate certificate = certificateRepository.findById(UUID.fromString(certificateId)).orElseThrow();
        certificate.markIssued();
        certificateRepository.save(certificate);
        return certificateId;
    }

    private String createBatch(String admin) throws Exception {
        String body = postJson(BATCHES, "{\"title\": \"Convocation\"}", admin)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String addCertificate(String admin, String batchId, String studentId) throws Exception {
        String body = postJson(BATCHES + "/" + batchId + "/certificates", """
                {"studentId": "%s", "type": "DEGREE", "title": "Bachelor of Technology",
                 "cgpa": 8.00, "awardedOn": "2026-06-30"}
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