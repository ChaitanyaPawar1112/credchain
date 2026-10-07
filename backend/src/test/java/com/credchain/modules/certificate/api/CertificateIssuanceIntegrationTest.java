package com.credchain.modules.certificate.api;

import com.credchain.modules.certificate.crypto.CertificateHasher;
import com.credchain.modules.certificate.crypto.MerkleTree;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.domain.CertificateStatus;
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
import org.web3j.utils.Numeric;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Certificate issuance API")

class CertificateIssuanceIntegrationTest extends AbstractIntegrationTest {

    private static final String BATCHES = "/api/v1/institution/certificate-batches";

    @Autowired
    private InstitutionWalletRepository walletRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateBatchRepository batchRepository;

    @Test
    @DisplayName("draft -> add certificates -> issue: every stored proof leads to the batch root")
    void issueFlow() throws Exception {
        String admin = onboardInstitution("CERT-ONE", "registrar@certone.test");
        activateWallet(admin);
        String s1 = createStudent(admin, "2022CS001", "Student One");
        String s2 = createStudent(admin, "2022CS002", "Student Two");
        String batchId = createBatch(admin, "Convocation 2026");

        postJson(BATCHES + "/" + batchId + "/certificates", certificateBody(s1, "8.75"), admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.certificateNumber").value(matchesPattern("^CERT-ONE-2026-\\d{6}$")))
                .andExpect(jsonPath("$.certHash").value(matchesPattern("^0x[0-9a-f]{64}$")))
                .andExpect(jsonPath("$.studentName").value("Student One"))
                .andExpect(jsonPath("$.salt").doesNotExist());
        postJson(BATCHES + "/" + batchId + "/certificates", certificateBody(s2, "9.10"), admin)
                .andExpect(status().isCreated());


        mockMvc.perform(post(BATCHES + "/{id}/issue", batchId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.certificateCount").value(2))
                .andExpect(jsonPath("$.merkleRoot").value(matchesPattern("^0x[0-9a-f]{64}$")));

        CertificateBatch batch = batchRepository.findById(UUID.fromString(batchId)).orElseThrow();
        byte[] root = Numeric.hexStringToByteArray(batch.getMerkleRoot());
        List<Certificate> certificates = certificateRepository.findAllByBatchIdOrderByCertificateNumber(batch.getId());

        assertThat(certificates).hasSize(2).allMatch(c -> c.getStatus() == CertificateStatus.PENDING);
        for (Certificate c : certificates) {
            List<String> proofHex = JsonPath.read(c.getMerkleProof(), "$");
            List<byte[]> proof = proofHex.stream().map(Numeric::hexStringToByteArray).toList();
            assertThat(MerkleTree.verify(proof, root, Numeric.hexStringToByteArray(c.getCertHash()))).isTrue();
            assertThat(CertificateHasher.hashOf(c.getCanonicalPayload())).isEqualTo(c.getCertHash());
        }
    }

    @Test
    @DisplayName("409 when the institution wallet is not active yet")
    void walletNotReady() throws Exception {
        String admin = onboardInstitution("CERT-WAIT", "registrar@certwait.test");   // wallet stays PENDING in tests
        String student = createStudent(admin, "2022CS001", "Student One");
        String batchId = createBatch(admin, "Batch");
        postJson(BATCHES + "/" + batchId + "/certificates", certificateBody(student, "8.00"), admin)
                .andExpect(status().isCreated());

        mockMvc.perform(post(BATCHES + "/{id}/issue", batchId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ISSUER_WALLET_NOT_READY"));
    }


    @Test
    @DisplayName("guards: duplicate student, other institution's student, empty batch, changes after issue")
    void guards() throws Exception {
        String adminA = onboardInstitution("CERT-AAA", "registrar@certa.test");
        String adminB = onboardInstitution("CERT-BBB", "registrar@certb.test");
        activateWallet(adminA);
        String studentA = createStudent(adminA, "2022CS001", "Student A");
        String studentB = createStudent(adminB, "2022CS001", "Student B");

        String empty = createBatch(adminA, "Empty");
        mockMvc.perform(post(BATCHES + "/{id}/issue", empty).header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
                .andExpect(status().isBadRequest());

        String batchId = createBatch(adminA, "Batch");
        postJson(BATCHES + "/" + batchId + "/certificates", certificateBody(studentA, "8.00"), adminA)
                .andExpect(status().isCreated());
        postJson(BATCHES + "/" + batchId + "/certificates", certificateBody(studentA, "8.00"), adminA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERTIFICATE_ALREADY_IN_BATCH"));
        postJson(BATCHES + "/" + batchId + "/certificates", certificateBody(studentB, "8.00"), adminA)
                .andExpect(status().isNotFound());

        mockMvc.perform(post(BATCHES + "/{id}/issue", batchId).header(HttpHeaders.AUTHORIZATION, bearer(adminA)))
                .andExpect(status().isAccepted());
        String studentA2 = createStudent(adminA, "2022CS002", "Student A2");
        postJson(BATCHES + "/" + batchId + "/certificates", certificateBody(studentA2, "8.00"), adminA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test

    @DisplayName("bulk add creates all certificates in one call")
    void bulkAdd() throws Exception {
        String admin = onboardInstitution("CERT-BULK", "registrar@certbulk.test");
        String s1 = createStudent(admin, "2022CS001", "Student One");
        String s2 = createStudent(admin, "2022CS002", "Student Two");
        String batchId = createBatch(admin, "Convocation");

        postJson(BATCHES + "/" + batchId + "/certificates/bulk", """
                {"type": "DEGREE", "title": "Bachelor of Technology", "program": "Computer Science",
                 "awardedOn": "2026-06-30",
                 "items": [{"studentId": "%s", "cgpa": 8.10}, {"studentId": "%s", "grade": "First Class"}]}
                """.formatted(s1, s2), admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get(BATCHES + "/{id}", batchId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch.certificateCount").value(2))
                .andExpect(jsonPath("$.certificates.length()").value(2));
    }

    @Test
    @DisplayName("another institution cannot see or change my batch (404)")
    void tenantIsolation() throws Exception {
        String adminA = onboardInstitution("CERT-ISO1", "registrar@iso1.test");
        String adminB = onboardInstitution("CERT-ISO2", "registrar@iso2.test");
        String batchId = createBatch(adminA, "Private batch");

        mockMvc.perform(get(BATCHES + "/{id}", batchId).header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(BATCHES + "/{id}", batchId).header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
                .andExpect(status().isNotFound());

    }

    @Test
    @DisplayName("draft certificates and batches can be deleted")
    void deleteDrafts() throws Exception {
        String admin = onboardInstitution("CERT-DEL", "registrar@certdel.test");
        String student = createStudent(admin, "2022CS001", "Student One");
        String batchId = createBatch(admin, "To delete");
        String certificateId = JsonPath.read(postJson(BATCHES + "/" + batchId + "/certificates",
                certificateBody(student, "8.00"), admin).andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete(BATCHES + "/{b}/certificates/{c}", batchId, certificateId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(BATCHES + "/{id}", batchId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(BATCHES + "/{id}", batchId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private String createBatch(String adminToken, String title) throws Exception {
        String body = postJson(BATCHES, "{\"title\": \"" + title + "\"}", adminToken)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String certificateBody(String studentId, String cgpa) {
        return """
                {"studentId": "%s", "type": "DEGREE", "title": "Bachelor of Technology",

                 "program": "Computer Science and Engineering", "grade": "First Class",
                 "cgpa": %s, "awardedOn": "2026-06-30"}
                """.formatted(studentId, cgpa);
    }

    /** In tests the blockchain is off, so we mark the wallet ACTIVE directly. */
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