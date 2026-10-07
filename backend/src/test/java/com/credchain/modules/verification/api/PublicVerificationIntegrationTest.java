package com.credchain.modules.verification.api;

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
import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Public verification API (no login)")
class PublicVerificationIntegrationTest extends AbstractIntegrationTest {

    private static final String BATCHES = "/api/v1/institution/certificate-batches";
    private static final String CERTIFICATES = "/api/v1/institution/certificates";
    private static final String VERIFY = "/api/v1/public/verify/{certHash}";

    @Autowired
    private InstitutionWalletRepository walletRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateBatchRepository batchRepository;

    @Test
    @DisplayName("an issued certificate is VALID for anyone, with what is printed on it and no secrets")
    void validWithoutLogin() throws Exception {
        String admin = onboardInstitution("VER-ONE", "registrar@verone.test");
        Certificate certificate = issuedCertificate(admin, "Asha Patil");

        mockMvc.perform(get(VERIFY, certificate.getCertHash()))   // no Authorization header
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.blockchainChecked").value(false))   // blockchain is off in tests
                .andExpect(jsonPath("$.certificate.studentName").value("Asha Patil"))
                .andExpect(jsonPath("$.certificate.cgpa").value("8.50"))
                .andExpect(jsonPath("$.certificate.institutionName").value("VER-ONE College"))
                .andExpect(jsonPath("$.blockchain.txHash").value("0x" + "e".repeat(64)))
                .andExpect(jsonPath("$.blockchain.explorerTxUrl").value("https://sepolia.etherscan.io/tx/0x" + "e".repeat(64)))
                .andExpect(jsonPath("$.revocation").doesNotExist())
                .andExpect(content().string(not(containsString(certificate.getSalt()))));
    }

    @Test
    @DisplayName("hash typed in capitals and without 0x still works (QR scanners, copy-paste)")
    void lenientHashInput() throws Exception {
        String admin = onboardInstitution("VER-TWO", "registrar@vertwo.test");
        Certificate certificate = issuedCertificate(admin, "Rahul Shinde");
        String typed = certificate.getCertHash().substring(2).toUpperCase(Locale.ROOT);

        mockMvc.perform(get(VERIFY, typed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.certHash").value(certificate.getCertHash()))
                .andExpect(jsonPath("$.status").value("VALID"));
    }

    @Test
    @DisplayName("as soon as the institution asks to revoke, the public result is REVOKED")
    void revoked() throws Exception {
        String admin = onboardInstitution("VER-THREE", "registrar@verthree.test");
        Certificate certificate = issuedCertificate(admin, "Revoked Student");
        postJson(CERTIFICATES + "/" + certificate.getId() + "/revoke",
                "{\"reason\": \"FRAUD\", \"note\": \"internal note\"}", admin)
                .andExpect(status().isAccepted());

        mockMvc.perform(get(VERIFY, certificate.getCertHash()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.revocation.reason").value("FRAUD"))
                .andExpect(content().string(not(containsString("internal note"))));
    }

    @Test
    @DisplayName("unknown hash and drafts are NOT_FOUND (drafts are never revealed)")
    void notFound() throws Exception {
        mockMvc.perform(get(VERIFY, "0x" + "9".repeat(64)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.certificate").doesNotExist());

        String admin = onboardInstitution("VER-FOUR", "registrar@verfour.test");
        String studentId = createStudent(admin, "2022CS001", "Draft Student");
        String draftId = addCertificate(admin, createBatch(admin), studentId);
        String draftHash = certificateRepository.findById(UUID.fromString(draftId)).orElseThrow().getCertHash();

        mockMvc.perform(get(VERIFY, draftHash))
                .andExpect(jsonPath("$.status").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("something that is not a hash: 400 with a clear message")
    void badInput() throws Exception {
        mockMvc.perform(get(VERIFY, "hello"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // ---------- helpers ----------

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
