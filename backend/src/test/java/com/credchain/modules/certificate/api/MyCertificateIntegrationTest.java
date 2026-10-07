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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Student: my certificates + institution certificate filters")
class MyCertificateIntegrationTest extends AbstractIntegrationTest {

    private static final String BATCHES = "/api/v1/institution/certificate-batches";
    private static final String CERTIFICATES = "/api/v1/institution/certificates";
    private static final String MY_CERTIFICATES = "/api/v1/me/certificates";

    @Autowired
    private CertificatePdfService pdfService;

    @Autowired
    private InstitutionWalletRepository walletRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateBatchRepository batchRepository;

    @Test
    @DisplayName("a linked student sees only on-chain certificates and downloads the PDF")
    void studentSeesOwnIssuedCertificates() throws Exception {
        String admin = onboardInstitution("MY-ONE", "registrar@myone.test");
        activateWallet(admin);
        String studentId = createStudent(admin, "2022CS001", "Student One");
        String student = linkedStudentToken(admin, "MY-ONE", studentId, "2022CS001", "one@student.test");

        String issuedId = issuedCertificate(admin, studentId);
        String draftId = addCertificate(admin, createBatch(admin), studentId);   // not on-chain yet

        mockMvc.perform(get(MY_CERTIFICATES).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(issuedId))
                .andExpect(jsonPath("$.content[0].institutionName").value("MY-ONE College"))
                .andExpect(jsonPath("$.content[0].status").value("ISSUED"))
                .andExpect(jsonPath("$.content[0].pdfAvailable").value(false));

        mockMvc.perform(get(MY_CERTIFICATES + "/{id}", draftId).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(MY_CERTIFICATES + "/{id}/pdf", issuedId).header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERTIFICATE_PDF_NOT_READY"));

        pdfService.generateMissing(10);

        mockMvc.perform(get(MY_CERTIFICATES + "/{id}", issuedId).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pdfAvailable").value(true))
                .andExpect(jsonPath("$.verificationUrl").value(startsWith("http")));
        mockMvc.perform(get(MY_CERTIFICATES + "/{id}/pdf", issuedId).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("a student cannot see another student's certificate (404)")
    void otherStudentGets404() throws Exception {
        String admin = onboardInstitution("MY-TWO", "registrar@mytwo.test");
        activateWallet(admin);
        String ownerId = createStudent(admin, "2022CS001", "Owner");
        String otherId = createStudent(admin, "2022CS002", "Other");
        String other = linkedStudentToken(admin, "MY-TWO", otherId, "2022CS002", "other@student.test");
        String certificateId = issuedCertificate(admin, ownerId);

        mockMvc.perform(get(MY_CERTIFICATES + "/{id}", certificateId).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(MY_CERTIFICATES + "/{id}/pdf", certificateId).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(MY_CERTIFICATES).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("a student without a linked record gets 404; an institution admin gets 403")
    void notLinkedOrWrongRole() throws Exception {
        String admin = onboardInstitution("MY-THREE", "registrar@mythree.test");
        Tokens student = registerStudent("lonely@student.test");

        mockMvc.perform(get(MY_CERTIFICATES).header(HttpHeaders.AUTHORIZATION, bearer(student.accessToken())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(MY_CERTIFICATES).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("institution list can be filtered by status and searched")
    void institutionFilters() throws Exception {
        String admin = onboardInstitution("MY-FOUR", "registrar@myfour.test");
        activateWallet(admin);
        String first = createStudent(admin, "2022CS001", "Asha Patil");
        String second = createStudent(admin, "2022CS002", "Rahul Shinde");
        issuedCertificate(admin, first);
        addCertificate(admin, createBatch(admin), second);   // DRAFT

        mockMvc.perform(get(CERTIFICATES).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(CERTIFICATES).param("status", "ISSUED").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].studentName").value("Asha Patil"));
        mockMvc.perform(get(CERTIFICATES).param("search", "rahul").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"));
        mockMvc.perform(get(CERTIFICATES).param("search", "2022cs00").param("status", "DRAFT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(CERTIFICATES).param("status", "NOT_A_STATUS").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    /** Claim code -> student registers -> links their account. Returns the student's access token. */
    private String linkedStudentToken(String admin, String institutionCode, String studentId,
                                      String enrollmentNo, String email) throws Exception {
        String code = read(mockMvc.perform(post("/api/v1/institution/students/{id}/claim-code", studentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn(), "$.claimCode");
        Tokens student = registerStudent(email);
        postJson("/api/v1/me/student-profile/link", """
                {"institutionCode": "%s", "enrollmentNo": "%s", "claimCode": "%s"}
                """.formatted(institutionCode, enrollmentNo, code), student.accessToken())
                .andExpect(status().isOk());
        return student.accessToken();
    }

    /** Creates a certificate and simulates the chain confirming its batch (blockchain is off in tests). */
    private String issuedCertificate(String admin, String studentId) throws Exception {
        String batchId = createBatch(admin);
        String certificateId = addCertificate(admin, batchId, studentId);
        mockMvc.perform(post(BATCHES + "/{id}/issue", batchId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isAccepted());

        CertificateBatch batch = batchRepository.findById(UUID.fromString(batchId)).orElseThrow();
        batch.markSubmitted("0x" + "c".repeat(64));
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