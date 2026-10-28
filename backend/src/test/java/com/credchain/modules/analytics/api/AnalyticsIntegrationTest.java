package com.credchain.modules.analytics.api;

import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import com.credchain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Analytics dashboard")
class AnalyticsIntegrationTest extends AbstractIntegrationTest {

    private static final String PLATFORM = "/api/v1/admin/analytics";
    private static final String COLLEGE = "/api/v1/institution/analytics";
    private static final String BATCHES = "/api/v1/institution/certificate-batches";

    @Autowired
    private InstitutionWalletRepository walletRepository;

    @Autowired
    private CertificateBatchRepository batchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String busyAdmin;
    private UUID busyId;

    /**
     * BUSY issued 3 certificates today (1 later revoked) and had 4 checks today plus 1 check 40 days ago;
     * QUIET issued 1; one more college is still waiting for review.
     */
    @BeforeEach
    void someActivity() throws Exception {
        jdbcTemplate.update("DELETE FROM verification_logs");
        busyAdmin = onboardInstitution("BUSY", "registrar@busy.test");
        busyId = institutionId(busyAdmin);
        issueBatch(busyAdmin, "B", 3);
        String quietAdmin = onboardInstitution("QUIET", "registrar@quiet.test");
        issueBatch(quietAdmin, "Q", 1);
        applyInstitution("WAITING", "registrar@waiting.test");

        String revoked = jdbcTemplate.queryForObject(
                "SELECT id FROM certificates WHERE institution_id = ? ORDER BY certificate_number LIMIT 1", String.class, busyId);
        postJson("/api/v1/institution/certificates/" + revoked + "/revoke", "{\"reason\": \"ISSUED_IN_ERROR\"}", busyAdmin)
                .andExpect(status().isAccepted());

        jdbcTemplate.update("""
                INSERT INTO verification_logs (checked_at, method, result, blockchain_checked, institution_id) VALUES
                    (now(), 'HASH', 'VALID', true, ?), (now(), 'HASH', 'VALID', true, ?),
                    (now(), 'PDF', 'FAKE', false, ?), (now(), 'DOCUMENT', 'REVOKED', true, ?),
                    (now() - interval '40 days', 'HASH', 'VALID', true, ?),
                    (now(), 'HASH', 'NOT_FOUND', false, NULL)
                """, busyId, busyId, busyId, busyId, busyId);
    }

    @Test
    @DisplayName("super admin: platform totals, a full day-by-day series, breakdowns and top colleges")
    void platform() throws Exception {
        String today = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
        mockMvc.perform(get(PLATFORM).header(HttpHeaders.AUTHORIZATION, bearer(superAdminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to").value(today))
                .andExpect(jsonPath("$.daily.length()").value(30))
                .andExpect(jsonPath("$.daily[29].date").value(today))
                .andExpect(jsonPath("$.daily[29].issued").value(4))
                .andExpect(jsonPath("$.daily[29].revoked").value(1))
                .andExpect(jsonPath("$.daily[29].checks").value(5))
                .andExpect(jsonPath("$.daily[29].valid").value(2))
                .andExpect(jsonPath("$.daily[29].failed").value(2))
                .andExpect(jsonPath("$.totals.colleges").value(2))
                .andExpect(jsonPath("$.totals.pendingApplications").value(1))
                .andExpect(jsonPath("$.totals.students").value(4))
                .andExpect(jsonPath("$.totals.studentsWithAccount").value(0))
                .andExpect(jsonPath("$.totals.certificatesIssued").value(3))
                .andExpect(jsonPath("$.totals.certificatesRevoked").value(1))
                .andExpect(jsonPath("$.totals.checksInPeriod").value(5))        // the 40-day-old check is outside
                .andExpect(jsonPath("$.totals.fakeChecksInPeriod").value(1))
                .andExpect(jsonPath("$.checksByResult.VALID").value(2))
                .andExpect(jsonPath("$.checksByResult.EXPIRED").value(0))
                .andExpect(jsonPath("$.checksByMethod.DOCUMENT").value(1))
                .andExpect(jsonPath("$.topColleges.length()").value(2))
                .andExpect(jsonPath("$.topColleges[0].code").value("BUSY"))
                .andExpect(jsonPath("$.topColleges[0].issued").value(3))
                .andExpect(jsonPath("$.topColleges[0].checks").value(4))
                .andExpect(jsonPath("$.topColleges[0].fakeChecks").value(1))
                .andExpect(jsonPath("$.topColleges[1].code").value("QUIET"));

        mockMvc.perform(get(PLATFORM).param("days", "90").header(HttpHeaders.AUTHORIZATION, bearer(superAdminToken())))
                .andExpect(jsonPath("$.daily.length()").value(90))
                .andExpect(jsonPath("$.totals.checksInPeriod").value(6));
    }

    @Test
    @DisplayName("college admin: only their own college, no platform-only numbers")
    void college() throws Exception {
        mockMvc.perform(get(COLLEGE).param("days", "7").header(HttpHeaders.AUTHORIZATION, bearer(busyAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily.length()").value(7))
                .andExpect(jsonPath("$.daily[6].issued").value(3))
                .andExpect(jsonPath("$.daily[6].checks").value(4))
                .andExpect(jsonPath("$.totals.students").value(3))
                .andExpect(jsonPath("$.totals.certificatesIssued").value(2))
                .andExpect(jsonPath("$.totals.colleges").doesNotExist())
                .andExpect(jsonPath("$.topColleges").doesNotExist());
    }

    @Test
    @DisplayName("roles are enforced and the period must be 7 to 365 days")
    void access() throws Exception {
        mockMvc.perform(get(PLATFORM).header(HttpHeaders.AUTHORIZATION, bearer(busyAdmin))).andExpect(status().isForbidden());
        mockMvc.perform(get(COLLEGE).header(HttpHeaders.AUTHORIZATION, bearer(superAdminToken()))).andExpect(status().isForbidden());
        mockMvc.perform(get(PLATFORM)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PLATFORM).param("days", "3").header(HttpHeaders.AUTHORIZATION, bearer(superAdminToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.days").exists());
    }

    // ---------- helpers ----------

    private UUID institutionId(String admin) throws Exception {
        return UUID.fromString(read(mockMvc.perform(get("/api/v1/institution/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))).andReturn(), "$.id"));
    }

    /** Issues a batch of n certificates and simulates the chain confirming it now (blockchain is off in tests). */
    private void issueBatch(String admin, String prefix, int certificates) throws Exception {
        InstitutionWallet wallet = walletRepository.findByInstitutionId(institutionId(admin)).orElseThrow();
        wallet.markActivated(null, Instant.now());
        walletRepository.save(wallet);

        String batchId = read(postJson(BATCHES, "{\"title\": \"Convocation\"}", admin)
                .andExpect(status().isCreated()).andReturn(), "$.id");
        for (int i = 1; i <= certificates; i++) {
            String studentId = createStudent(admin, prefix + "2022CS00" + i, "Student " + i);
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
        jdbcTemplate.update("UPDATE certificates SET status = 'ISSUED' WHERE batch_id = ?", UUID.fromString(batchId));
    }
}
