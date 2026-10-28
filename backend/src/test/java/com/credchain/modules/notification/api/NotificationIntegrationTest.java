package com.credchain.modules.notification.api;

import com.credchain.modules.certificate.domain.BatchAnchoredEvent;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.notification.email.EmailMessage;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import com.credchain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Notifications: the bell and emails")
class NotificationIntegrationTest extends AbstractIntegrationTest {

    private static final String NOTIFICATIONS = "/api/v1/notifications";
    private static final String BATCHES = "/api/v1/institution/certificate-batches";

    @Autowired
    private InstitutionWalletRepository walletRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateBatchRepository batchRepository;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("approval: welcome in the bell and an email without the password; rejection: email with the reason")
    void approvalAndRejection() throws Exception {
        String institutionId = applyInstitution("NT-ONE", "registrar@ntone.test");
        String tempPassword = approveInstitution(institutionId, superAdminToken());

        List<EmailMessage> approved = emails.sentTo("registrar@ntone.test");
        assertThat(approved).hasSize(1);
        assertThat(approved.getFirst().subject()).contains("NT-ONE College is approved");
        assertThat(approved.getFirst().text()).contains("http://localhost:5173/login").doesNotContain(tempPassword);

        String admin = adminToken("registrar@ntone.test", tempPassword);
        mockMvc.perform(get(NOTIFICATIONS).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.notifications.content[0].type").value("WELCOME"))
                .andExpect(jsonPath("$.notifications.content[0].read").value(false));

        String rejectedId = applyInstitution("NT-TWO", "registrar@nttwo.test");
        postJson("/api/v1/admin/institutions/" + rejectedId + "/reject",
                "{\"reason\": \"Registration number could not be verified\"}", superAdminToken())
                .andExpect(status().isOk());
        assertThat(emails.sentTo("registrar@nttwo.test")).singleElement()
                .satisfies(m -> assertThat(m.text()).contains("Registration number could not be verified"));
    }

    @Test
    @DisplayName("certificate issued, checked twice and revoked: the student and college are told, checks only once")
    void certificateLifecycle() throws Exception {
        String admin = onboardInstitution("NT-THREE", "registrar@ntthree.test");
        activateWallet(admin);
        String studentId = read(postJson("/api/v1/institution/students", """
                {"enrollmentNo": "2022CS001", "fullName": "Asha Patil", "email": "asha@student.test",
                 "program": "B.Tech Computer Engineering", "admissionYear": 2022}
                """, admin).andExpect(status().isCreated()).andReturn(), "$.id");
        String student = linkedStudentToken(admin, "NT-THREE", studentId, "2022CS001", "asha.account@student.test");
        emails.clear();

        Certificate certificate = anchoredCertificate(admin, studentId);

        mockMvc.perform(get(NOTIFICATIONS).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.notifications.content[0].type").value("CERTIFICATE_ISSUED"))
                .andExpect(jsonPath("$.notifications.content[0].link").value("/student/certificates"));
        mockMvc.perform(get(NOTIFICATIONS).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.notifications.content[0].type").value("BATCH_ANCHORED"));
        assertThat(emails.sentTo("asha@student.test")).singleElement()
                .satisfies(m -> assertThat(m.text()).contains(certificate.getCertificateNumber()));

        // Two genuine checks make one "checked" notification while it is unread
        mockMvc.perform(get("/api/v1/public/verify/{certHash}", certificate.getCertHash()))
                .andExpect(jsonPath("$.status").value("VALID"));
        mockMvc.perform(get("/api/v1/public/verify/{certHash}", certificate.getCertHash()))
                .andExpect(jsonPath("$.status").value("VALID"));
        mockMvc.perform(get(NOTIFICATIONS).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.notifications.content[0].type").value("CERTIFICATE_CHECKED"));

        postJson("/api/v1/institution/certificates/" + certificate.getId() + "/revoke",
                "{\"reason\": \"ISSUED_IN_ERROR\", \"note\": \"Internal: wrong CGPA typed\"}", admin)
                .andExpect(status().isAccepted());
        mockMvc.perform(get(NOTIFICATIONS).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(jsonPath("$.unreadCount").value(3))
                .andExpect(jsonPath("$.notifications.content[0].type").value("CERTIFICATE_REVOKED"))
                .andExpect(jsonPath("$.notifications.content[0].body").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Internal"))));
        assertThat(emails.sentTo("asha@student.test")).hasSize(2).last()
                .satisfies(m -> assertThat(m.text()).contains("issued in error").doesNotContain("Internal"));
    }

    @Test
    @DisplayName("read one, read all; another user's notification is 404; no token is 401")
    void markingRead() throws Exception {
        String admin = onboardInstitution("NT-FOUR", "registrar@ntfour.test");
        String other = onboardInstitution("NT-FIVE", "registrar@ntfive.test");
        String id = read(mockMvc.perform(get(NOTIFICATIONS).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andReturn(), "$.notifications.content[0].id");

        mockMvc.perform(post(NOTIFICATIONS + "/{id}/read", id).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(NOTIFICATIONS + "/{id}/read", id).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(NOTIFICATIONS + "/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.unreadCount").value(0));
        mockMvc.perform(get(NOTIFICATIONS + "/unread-count").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(post(NOTIFICATIONS + "/read-all").header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(NOTIFICATIONS).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.notifications.content[0].read").value(true));

        mockMvc.perform(get(NOTIFICATIONS)).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    /** First login with the temporary password -> new password -> token. */
    private String adminToken(String email, String tempPassword) throws Exception {
        Tokens first = Tokens.from(login(email, tempPassword).andExpect(status().isOk()).andReturn());
        postJson("/api/v1/auth/change-password", changePasswordBody(tempPassword, STRONG_PASSWORD), first.accessToken())
                .andExpect(status().isNoContent());
        return Tokens.from(login(email, STRONG_PASSWORD).andExpect(status().isOk()).andReturn()).accessToken();
    }

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

    /**
     * Issues one certificate and does what the anchoring worker does when the chain confirms it
     * (the blockchain is off in tests): marks it anchored and publishes BatchAnchoredEvent in one transaction.
     */
    private Certificate anchoredCertificate(String admin, String studentId) throws Exception {
        String batchId = read(postJson(BATCHES, "{\"title\": \"Convocation\"}", admin)
                .andExpect(status().isCreated()).andReturn(), "$.id");
        String certificateId = read(postJson(BATCHES + "/" + batchId + "/certificates", """
                {"studentId": "%s", "type": "DEGREE", "title": "Bachelor of Technology",
                 "cgpa": 8.00, "awardedOn": "2026-06-30"}
                """.formatted(studentId), admin).andExpect(status().isCreated()).andReturn(), "$.id");
        mockMvc.perform(post(BATCHES + "/{id}/issue", batchId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isAccepted());

        transactionTemplate.executeWithoutResult(tx -> {
            CertificateBatch batch = batchRepository.findById(UUID.fromString(batchId)).orElseThrow();
            batch.markSubmitted("0x" + "c".repeat(64));
            batch.markAnchored(1L, Instant.now());
            Certificate certificate = certificateRepository.findById(UUID.fromString(certificateId)).orElseThrow();
            certificate.markIssued();
            events.publishEvent(new BatchAnchoredEvent(batch.getId(), batch.getInstitutionId(), batch.getTitle(), 1));
        });
        return certificateRepository.findById(UUID.fromString(certificateId)).orElseThrow();
    }

    private void activateWallet(String adminToken) throws Exception {
        UUID institutionId = UUID.fromString(read(mockMvc.perform(get("/api/v1/institution/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))).andReturn(), "$.id"));
        InstitutionWallet wallet = walletRepository.findByInstitutionId(institutionId).orElseThrow();
        wallet.markActivated(null, Instant.now());
        walletRepository.save(wallet);
    }
}
