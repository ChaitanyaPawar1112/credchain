package com.credchain.modules.institution.api;

import com.credchain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Institution onboarding API")
class InstitutionOnboardingIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_EMAIL = "registrar@sit.test";

    @Test
    @DisplayName("201: public application starts PENDING with normalized code")
    void applyCreatesPending() throws Exception {
        postJson("/api/v1/institutions/applications", """
                {"name": "SIT", "code": "sit-aur", "registrationNumber": "AISHE-1", "type": "COLLEGE",
                 "email": "Office@SIT.test", "city": "Pune", "state": "Maharashtra",
                 "contactPersonName": "Registrar", "contactPersonEmail": "registrar@sit.test"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.code").value("SIT-AUR"))
                .andExpect(jsonPath("$.email").value("office@sit.test"))
                .andExpect(jsonPath("$.country").value("IN"));
    }


    @Test
    @DisplayName("409: duplicate institution code")
    void duplicateCode() throws Exception {
        applyInstitution("SIT-AUR", ADMIN_EMAIL);
        postJson("/api/v1/institutions/applications", """
                {"name": "Other", "code": "sit-aur", "registrationNumber": "OTHER-1", "type": "COLLEGE",
                 "email": "x@other.test", "city": "Pune", "state": "Maharashtra",
                 "contactPersonName": "X", "contactPersonEmail": "x@other.test"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTITUTION_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("200: public status check hides contact details")
    void publicStatusHidesDetails() throws Exception {
        String id = applyInstitution("SIT-AUR", ADMIN_EMAIL);
        mockMvc.perform(get("/api/v1/institutions/applications/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.contactPersonEmail").doesNotExist())
                .andExpect(jsonPath("$.registrationNumber").doesNotExist());
    }

    @Test
    @DisplayName("approval creates an admin who must change the temporary password before working")
    void approvalFlowForcesPasswordChange() throws Exception {
        String id = applyInstitution("SIT-AUR", ADMIN_EMAIL);
        String tempPassword = approveInstitution(id, superAdminToken());

        Tokens first = Tokens.from(login(ADMIN_EMAIL, tempPassword)
                .andExpect(status().isOk())

                .andExpect(jsonPath("$.user.role").value("INSTITUTION_ADMIN"))
                .andExpect(jsonPath("$.user.institutionId").value(id))
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn());

        // blocked until the temporary password is changed
        mockMvc.perform(get("/api/v1/institution/students").header(HttpHeaders.AUTHORIZATION, bearer(first.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        postJson("/api/v1/auth/change-password", changePasswordBody(tempPassword, STRONG_PASSWORD), first.accessToken())
                .andExpect(status().isNoContent());

        Tokens second = Tokens.from(login(ADMIN_EMAIL, STRONG_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andReturn());

        mockMvc.perform(get("/api/v1/institution/profile").header(HttpHeaders.AUTHORIZATION, bearer(second.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SIT-AUR"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("409: an institution cannot be approved twice")
    void doubleApprove() throws Exception {
        String superToken = superAdminToken();
        String id = applyInstitution("SIT-AUR", ADMIN_EMAIL);
        approveInstitution(id, superToken);

        mockMvc.perform(post("/api/v1/admin/institutions/{id}/approve", id)

                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("rejection stores the reason and the applicant can see it")
    void rejectFlow() throws Exception {
        String id = applyInstitution("FAS-XYZ", "admin@fas.test");

        postJson("/api/v1/admin/institutions/" + id + "/reject",
                """
                {"reason": "Registration number could not be verified"}
                """, superAdminToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/v1/institutions/applications/{id}", id))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Registration number could not be verified"));
    }

    @Test
    @DisplayName("suspension blocks the institution admin and logs out their sessions")
    void suspendBlocksAdmin() throws Exception {
        String superToken = superAdminToken();
        String id = applyInstitution("SIT-AUR", ADMIN_EMAIL);
        String tempPassword = approveInstitution(id, superToken);
        Tokens first = Tokens.from(login(ADMIN_EMAIL, tempPassword).andReturn());
        postJson("/api/v1/auth/change-password", changePasswordBody(tempPassword, STRONG_PASSWORD), first.accessToken())
                .andExpect(status().isNoContent());
        Tokens admin = Tokens.from(login(ADMIN_EMAIL, STRONG_PASSWORD).andExpect(status().isOk()).andReturn());


        mockMvc.perform(post("/api/v1/admin/institutions/{id}/suspend", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(get("/api/v1/institution/students").header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSTITUTION_NOT_APPROVED"));

        refresh(admin.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("403: only SUPER_ADMIN can review institutions")
    void studentCannotReview() throws Exception {
        Tokens student = registerStudent("student@test.dev");
        mockMvc.perform(get("/api/v1/admin/institutions").header(HttpHeaders.AUTHORIZATION, bearer(student.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}