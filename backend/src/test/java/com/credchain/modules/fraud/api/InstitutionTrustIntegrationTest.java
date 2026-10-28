package com.credchain.modules.fraud.api;

import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
import com.credchain.modules.fraud.infrastructure.FraudServiceException;
import com.credchain.modules.fraud.infrastructure.InstitutionActivity;
import com.credchain.modules.fraud.infrastructure.InstitutionTrustAnalysis;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("AI trust score per college (super admin)")
class InstitutionTrustIntegrationTest extends AbstractIntegrationTest {

    private static final String TRUST = "/api/v1/admin/institution-trust";
    private static final String BATCHES = "/api/v1/institution/certificate-batches";
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    @Autowired
    private StubFraudServiceClient fraudService;

    @Autowired
    private InstitutionWalletRepository walletRepository;

    @Autowired
    private CertificateBatchRepository batchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        fraudService.reset();
        jdbcTemplate.update("DELETE FROM verification_logs");
    }

    @Test
    @DisplayName("today's issued certificates and forged copies are sent; the answer is saved and listed lowest first")
    void recomputeSendsActivityAndSavesScores() throws Exception {
        String admin = onboardInstitution("TRUST-ONE", "registrar@trustone.test");
        UUID institutionId = institutionId(admin);
        issueBatch(admin, 3);
        jdbcTemplate.update("""
                INSERT INTO verification_logs (checked_at, method, result, blockchain_checked, institution_id)
                VALUES (now(), 'PDF', 'FAKE', false, ?), (now(), 'HASH', 'VALID', true, ?),
                       (now(), 'DOCUMENT', 'FAKE', false, ?)
                """, institutionId, institutionId, institutionId);
        jdbcTemplate.update("UPDATE verification_logs SET risk_score = 80 WHERE method = 'DOCUMENT'");
        onboardInstitution("TRUST-TWO", "registrar@trusttwo.test");
        String superToken = superAdminToken();

        fraudService.respondToTrustWith(activity -> new InstitutionTrustAnalysis("IsolationForest", true,
                activity.days().size(), activity.institutions().stream()
                .map(i -> i.institutionId().equals(institutionId.toString())
                        ? new InstitutionTrustAnalysis.Trust(i.institutionId(), i.name(), 40, "WHATEVER",
                        List.of("3000 certificates issued between midnight and 6 a.m. on 26 Oct 2026, while it normally has none."),
                        List.of(new InstitutionTrustAnalysis.Anomaly(LocalDate.now(), 0.9, "night issuing")))
                        : new InstitutionTrustAnalysis.Trust(i.institutionId(), i.name(), 100, "TRUSTED",
                        List.of("No unusual activity found."), List.of()))
                .toList()));

        mockMvc.perform(post(TRUST + "/recompute").header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.computedAt").exists())
                .andExpect(jsonPath("$.institutions[0].code").value("TRUST-ONE"))
                .andExpect(jsonPath("$.institutions[0].trustScore").value(40))
                .andExpect(jsonPath("$.institutions[0].level").value("AT_RISK"))   // follows the score, not the service's label
                .andExpect(jsonPath("$.institutions[0].anomalyCount").value(1))
                .andExpect(jsonPath("$.institutions[0].modelUsed").value(true))
                .andExpect(jsonPath("$.institutions[0].reasons[0]").value(org.hamcrest.Matchers.startsWith("3000 certificates")))
                .andExpect(jsonPath("$.institutions[1].code").value("TRUST-TWO"))
                .andExpect(jsonPath("$.institutions[1].level").value("TRUSTED"));

        InstitutionActivity sent = fraudService.lastActivity();
        assertThat(sent.today()).isEqualTo(LocalDate.now(INDIA));
        assertThat(sent.institutions()).extracting(InstitutionActivity.Institution::name).hasSize(2);
        InstitutionActivity.Day today = sent.days().stream()
                .filter(d -> d.institutionId().equals(institutionId.toString()) && d.date().equals(sent.today()))
                .findFirst().orElseThrow();
        assertThat(today.issued()).isEqualTo(3);
        assertThat(today.checks()).isEqualTo(3);
        assertThat(today.fakeChecks()).isEqualTo(1);       // the PDF one; the photo counts as high-risk instead
        assertThat(today.highRiskChecks()).isEqualTo(1);
        assertThat(today.revoked()).isZero();

        // the list reads the saved result without calling the service again
        int calls = fraudService.calls();
        mockMvc.perform(get(TRUST).header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institutions[0].trustScore").value(40));
        assertThat(fraudService.calls()).isEqualTo(calls);
    }

    @Test
    @DisplayName("a college approved after the last run is listed as not analysed yet; pending applications are not listed")
    void notAnalysedYet() throws Exception {
        onboardInstitution("TRUST-NEW", "registrar@trustnew.test");
        applyInstitution("TRUST-PENDING", "registrar@trustpending.test");

        mockMvc.perform(get(TRUST).header(HttpHeaders.AUTHORIZATION, bearer(superAdminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.computedAt").doesNotExist())
                .andExpect(jsonPath("$.institutions.length()").value(1))
                .andExpect(jsonPath("$.institutions[0].code").value("TRUST-NEW"))
                .andExpect(jsonPath("$.institutions[0].trustScore").doesNotExist())
                .andExpect(jsonPath("$.institutions[0].reasons[0]").value("Not analysed yet."));
    }

    @Test
    @DisplayName("service down: 503, and the previous scores stay")
    void serviceDown() throws Exception {
        onboardInstitution("TRUST-DOWN", "registrar@trustdown.test");
        String superToken = superAdminToken();
        mockMvc.perform(post(TRUST + "/recompute").header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(status().isOk());

        fraudService.failWith(FraudServiceException.unavailable("down", null));
        mockMvc.perform(post(TRUST + "/recompute").header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FRAUD_SERVICE_UNAVAILABLE"));

        mockMvc.perform(get(TRUST).header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(jsonPath("$.institutions[0].trustScore").value(100));
    }

    @Test
    @DisplayName("only the super admin can see or run it")
    void superAdminOnly() throws Exception {
        String admin = onboardInstitution("TRUST-SEC", "registrar@trustsec.test");
        mockMvc.perform(get(TRUST).header(HttpHeaders.AUTHORIZATION, bearer(admin))).andExpect(status().isForbidden());
        mockMvc.perform(post(TRUST + "/recompute").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(TRUST)).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private UUID institutionId(String admin) throws Exception {
        String profile = mockMvc.perform(get("/api/v1/institution/profile").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(profile, "$.id"));
    }

    /** Issues a batch of n certificates and simulates the chain confirming it now (blockchain is off in tests). */
    private void issueBatch(String admin, int certificates) throws Exception {
        InstitutionWallet wallet = walletRepository.findByInstitutionId(institutionId(admin)).orElseThrow();
        wallet.markActivated(null, Instant.now());
        walletRepository.save(wallet);

        String batchId = JsonPath.read(postJson(BATCHES, "{\"title\": \"Convocation\"}", admin)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        for (int i = 1; i <= certificates; i++) {
            String studentId = createStudent(admin, "2022CS00" + i, "Student " + i);
            postJson(BATCHES + "/" + batchId + "/certificates", """
                    {"studentId": "%s", "type": "DEGREE", "title": "Bachelor of Technology", "awardedOn": "2026-06-30"}
                    """.formatted(studentId), admin).andExpect(status().isCreated());
        }
        mockMvc.perform(post(BATCHES + "/{id}/issue", batchId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isAccepted());
        CertificateBatch batch = batchRepository.findById(UUID.fromString(batchId)).orElseThrow();
        batch.markSubmitted("0x" + "e".repeat(64));
        batch.markAnchored(1L, Instant.now());
        batchRepository.save(batch);
    }
}
