package com.credchain.modules.certificate.api;

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

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Certificate revocation API")
class CertificateRevocationIntegrationTest extends AbstractIntegrationTest {

    private static final String BATCHES = "/api/v1/institution/certificate-batches";
    private static final String CERTIFICATES = "/api/v1/institution/certificates";
    private static final String REVOKE_BODY = "{\"reason\": \"ISSUED_IN_ERROR\", \"note\": \"Wrong CGPA\"}";

    @Autowired
    private InstitutionWalletRepository walletRepository;


    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateBatchRepository batchRepository;

    @Test
    @DisplayName("an ISSUED certificate can be revoked once; the request waits for the chain")
    void revokeIssued() throws Exception {
        String admin = onboardInstitution("REV-ONE", "registrar@revone.test");
        String certificateId = issuedCertificate(admin);

        postJson(CERTIFICATES + "/" + certificateId + "/revoke", REVOKE_BODY, admin)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REVOCATION_PENDING"))
                .andExpect(jsonPath("$.revocationReason").value("ISSUED_IN_ERROR"));

        postJson(CERTIFICATES + "/" + certificateId + "/revoke", REVOKE_BODY, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        mockMvc.perform(get(CERTIFICATES + "/{id}", certificateId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOCATION_PENDING"));
    }

    @Test
    @DisplayName("a draft cannot be revoked (409) and a reason is required (400)")
    void guards() throws Exception {
        String admin = onboardInstitution("REV-TWO", "registrar@revtwo.test");
        String student = createStudent(admin, "2022CS001", "Student One");

        String batchId = createBatch(admin);
        String draftId = addCertificate(admin, batchId, student);

        postJson(CERTIFICATES + "/" + draftId + "/revoke", REVOKE_BODY, admin)
                .andExpect(status().isConflict());
        postJson(CERTIFICATES + "/" + draftId + "/revoke", "{\"note\": \"no reason\"}", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("another institution cannot see or revoke my certificate (404)")
    void tenantIsolation() throws Exception {
        String adminA = onboardInstitution("REV-AAA", "registrar@reva.test");
        String adminB = onboardInstitution("REV-BBB", "registrar@revb.test");
        String certificateId = issuedCertificate(adminA);

        mockMvc.perform(get(CERTIFICATES + "/{id}", certificateId).header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
                .andExpect(status().isNotFound());
        postJson(CERTIFICATES + "/" + certificateId + "/revoke", REVOKE_BODY, adminB)
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