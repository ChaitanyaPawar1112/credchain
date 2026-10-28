package com.credchain.modules.share.api;

import com.credchain.modules.certificate.application.CertificatePdfService;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import com.credchain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Share links: named, expiring, revocable, counted")
class ShareLinkIntegrationTest extends AbstractIntegrationTest {

    private static final String LINKS = "/api/v1/me/share-links";
    private static final String PUBLIC = "/api/v1/public/share/";
    private static final String BATCHES = "/api/v1/institution/certificate-batches";

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

    private String admin;
    private String student;
    private String certificateId;

    @BeforeEach
    void studentWithCertificate() throws Exception {
        admin = onboardInstitution("SH-ONE", "registrar@shone.test");
        activateWallet(admin);
        String studentId = createStudent(admin, "2022CS001", "Asha Patil");
        student = linkedStudentToken(admin, "SH-ONE", studentId, "2022CS001", "asha@student.test");
        certificateId = issuedCertificate(admin, studentId);
    }

    @Test
    @DisplayName("create, open twice (counted, live VALID result, label never shown), download the PDF")
    void createOpenAndCount() throws Exception {
        String url = read(createLink(certificateId, "Infosys HR", 30)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.viewCount").value(0))
                .andExpect(jsonPath("$.url").value(startsWith("http://localhost:5173/s/")))
                .andReturn(), "$.url");
        String token = url.substring(url.lastIndexOf('/') + 1);
        assertThat(token).hasSize(43);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get(PUBLIC + token))   // no login
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verification.status").value("VALID"))
                    .andExpect(jsonPath("$.verification.certificate.studentName").value("Asha Patil"))
                    .andExpect(jsonPath("$.pdfAvailable").value(false))
                    .andExpect(content().string(not(containsString("Infosys"))));
        }
        mockMvc.perform(get(LINKS).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(jsonPath("$.content[0].name").value("Infosys HR"))
                .andExpect(jsonPath("$.content[0].certificateTitle").value("Bachelor of Technology"))
                .andExpect(jsonPath("$.content[0].viewCount").value(2))
                .andExpect(jsonPath("$.content[0].lastViewedAt").exists());
        // each view is also a check in the college's verification log
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM verification_logs WHERE certificate_id = ?::uuid",
                Integer.class, certificateId)).isEqualTo(2);

        mockMvc.perform(get(PUBLIC + token + "/pdf").accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
        pdfService.generateMissing(10);
        mockMvc.perform(get(PUBLIC + token + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("turned off, expired and unknown links all answer the same 404")
    void revokedExpiredUnknown() throws Exception {
        String revokedId = read(createLink(certificateId, "Old employer", 30).andReturn(), "$.id");
        String revokedToken = token(revokedId);
        mockMvc.perform(post(LINKS + "/{id}/revoke", revokedId).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        String expiredId = read(createLink(certificateId, "Short", 1).andReturn(), "$.id");
        jdbcTemplate.update("UPDATE share_links SET created_at = now() - interval '3 days', "
                + "expires_at = now() - interval '2 days' WHERE id = ?::uuid", expiredId);

        for (String token : new String[] {revokedToken, token(expiredId), "x".repeat(43), "short"}) {
            mockMvc.perform(get(PUBLIC + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SHARE_LINK_UNAVAILABLE"));
        }
        mockMvc.perform(get(PUBLIC + revokedToken + "/pdf")).andExpect(status().isNotFound());
        mockMvc.perform(get(LINKS).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(jsonPath("$.content[0].status").value("REVOKED"))
                .andExpect(jsonPath("$.content[1].status").value("EXPIRED"));   // made 3 days ago, so listed last
    }

    @Test
    @DisplayName("only my own issued certificates; only my own links; students only; 1 to 365 days; at most 20 active")
    void rules() throws Exception {
        String otherStudentId = createStudent(admin, "2022CS002", "Rahul Shinde");
        String other = linkedStudentToken(admin, "SH-ONE", otherStudentId, "2022CS002", "rahul@student.test");
        createLink(certificateId, "Not mine", 30, other).andExpect(status().isNotFound());

        String myLink = read(createLink(certificateId, "Mine", 30).andReturn(), "$.id");
        mockMvc.perform(post(LINKS + "/{id}/revoke", myLink).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());

        createLink(certificateId, "Too long", 366).andExpect(status().isBadRequest());
        createLink(certificateId, " ", 30).andExpect(status().isBadRequest());
        createLink(certificateId, "College", 30, admin).andExpect(status().isForbidden());

        for (int i = 2; i <= 20; i++) {
            createLink(certificateId, "Link " + i, 30).andExpect(status().isCreated());
        }
        createLink(certificateId, "One more", 30)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHARE_LINK_LIMIT_REACHED"));

        postJson("/api/v1/institution/certificates/" + certificateId + "/revoke", "{\"reason\": \"ISSUED_IN_ERROR\"}", admin)
                .andExpect(status().isAccepted());
        mockMvc.perform(post(LINKS + "/{id}/revoke", myLink).header(HttpHeaders.AUTHORIZATION, bearer(student)));
        createLink(certificateId, "After revocation", 30)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.ResultActions createLink(String certificate, String name, int days)
            throws Exception {
        return createLink(certificate, name, days, student);
    }

    private org.springframework.test.web.servlet.ResultActions createLink(String certificate, String name, int days,
                                                                          String token) throws Exception {
        return postJson(LINKS, """
                {"certificateId": "%s", "name": "%s", "expiresInDays": %d}
                """.formatted(certificate, name, days), token);
    }

    private String token(String linkId) {
        return jdbcTemplate.queryForObject("SELECT token FROM share_links WHERE id = ?::uuid", String.class, linkId);
    }

    private String linkedStudentToken(String admin, String institutionCode, String studentId,
                                      String enrollmentNo, String email) throws Exception {
        String code = read(mockMvc.perform(post("/api/v1/institution/students/{id}/claim-code", studentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn(), "$.claimCode");
        Tokens tokens = registerStudent(email);
        postJson("/api/v1/me/student-profile/link", """
                {"institutionCode": "%s", "enrollmentNo": "%s", "claimCode": "%s"}
                """.formatted(institutionCode, enrollmentNo, code), tokens.accessToken())
                .andExpect(status().isOk());
        return tokens.accessToken();
    }

    /** Creates a certificate and simulates the chain confirming its batch (blockchain is off in tests). */
    private String issuedCertificate(String admin, String studentId) throws Exception {
        String batchId = read(postJson(BATCHES, "{\"title\": \"Convocation\"}", admin)
                .andExpect(status().isCreated()).andReturn(), "$.id");
        String id = read(postJson(BATCHES + "/" + batchId + "/certificates", """
                {"studentId": "%s", "type": "DEGREE", "title": "Bachelor of Technology",
                 "cgpa": 8.00, "awardedOn": "2026-06-30"}
                """.formatted(studentId), admin).andExpect(status().isCreated()).andReturn(), "$.id");
        mockMvc.perform(post(BATCHES + "/{id}/issue", batchId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isAccepted());

        CertificateBatch batch = batchRepository.findById(UUID.fromString(batchId)).orElseThrow();
        batch.markSubmitted("0x" + "c".repeat(64));
        batch.markAnchored(1L, Instant.now());
        batchRepository.save(batch);
        Certificate certificate = certificateRepository.findById(UUID.fromString(id)).orElseThrow();
        certificate.markIssued();
        certificateRepository.save(certificate);
        return id;
    }

    private void activateWallet(String adminToken) throws Exception {
        UUID institutionId = UUID.fromString(read(mockMvc.perform(get("/api/v1/institution/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))).andReturn(), "$.id"));
        InstitutionWallet wallet = walletRepository.findByInstitutionId(institutionId).orElseThrow();
        wallet.markActivated(null, Instant.now());
        walletRepository.save(wallet);
    }
}
