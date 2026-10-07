package com.credchain.modules.certificate.api;

import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.domain.ChainCheckResult;
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

@DisplayName("Admin reconciliation report")
class AdminReconciliationIntegrationTest extends AbstractIntegrationTest {

    private static final String BATCHES = "/api/v1/institution/certificate-batches";
    private static final String REPORT = "/api/v1/admin/reconciliation";

    @Autowired
    private InstitutionWalletRepository walletRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateBatchRepository batchRepository;

    @Test
    @DisplayName("super admin sees totals and the certificates that don't match the blockchain")
    void reportShowsMismatches() throws Exception {
        String admin = onboardInstitution("REC-ONE", "registrar@recone.test");
        activateWallet(admin);
        String matching = issuedCertificate(admin, createStudent(admin, "2022CS001", "Asha Patil"));
        String broken = issuedCertificate(admin, createStudent(admin, "2022CS002", "Rahul Shinde"));
        issuedCertificate(admin, createStudent(admin, "2022CS003", "Not Checked Yet"));

        recordCheck(matching, ChainCheckResult.MATCH, "VALID on-chain");
        recordCheck(broken, ChainCheckResult.MISMATCH, "ISSUED in the database but REVOKED on-chain");

        mockMvc.perform(get(REPORT).header(HttpHeaders.AUTHORIZATION, bearer(superAdminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onChainCertificates").value(3))
                .andExpect(jsonPath("$.neverChecked").value(1))
                .andExpect(jsonPath("$.mismatches").value(1))
                .andExpect(jsonPath("$.mismatchList.content[0].certificateId").value(broken))
                .andExpect(jsonPath("$.mismatchList.content[0].studentName").value("Rahul Shinde"))
                .andExpect(jsonPath("$.mismatchList.content[0].databaseStatus").value("ISSUED"))
                .andExpect(jsonPath("$.mismatchList.content[0].note")
                        .value("ISSUED in the database but REVOKED on-chain"));
    }

    @Test
    @DisplayName("only SUPER_ADMIN can see the report (403 for an institution admin)")
    void onlySuperAdmin() throws Exception {
        String admin = onboardInstitution("REC-TWO", "registrar@rectwo.test");

        mockMvc.perform(get(REPORT).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private void recordCheck(String certificateId, ChainCheckResult result, String note) {
        Certificate certificate = certificateRepository.findById(UUID.fromString(certificateId)).orElseThrow();
        certificate.recordChainCheck(result, note, Instant.now());
        certificateRepository.save(certificate);
    }

    /** Creates a certificate and simulates the chain confirming its batch (blockchain is off in tests). */
    private String issuedCertificate(String admin, String studentId) throws Exception {
        String batchId = createBatch(admin);
        String certificateId = addCertificate(admin, batchId, studentId);
        mockMvc.perform(post(BATCHES + "/{id}/issue", batchId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isAccepted());

        CertificateBatch batch = batchRepository.findById(UUID.fromString(batchId)).orElseThrow();
        batch.markSubmitted("0x" + "d".repeat(64));
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