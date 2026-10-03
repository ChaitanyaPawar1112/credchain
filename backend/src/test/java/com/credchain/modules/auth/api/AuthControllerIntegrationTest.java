package com.credchain.modules.auth.api;

import com.credchain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Auth API")
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "student@test.dev";
    private static final String NEW_PASSWORD = "N3w@Str0ngPass";

    @Nested
    @DisplayName("POST /register")
    class Register {

        @Test
        @DisplayName("201: creates a student and returns tokens")
        void createsStudent() throws Exception {
            register(EMAIL, STRONG_PASSWORD, "STUDENT")
                    .andExpect(status().isCreated())

                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.user.email").value(EMAIL))
                    .andExpect(jsonPath("$.user.role").value("STUDENT"));
        }

        @Test
        @DisplayName("409: duplicate email, even with different capitalization")
        void rejectsDuplicateEmail() throws Exception {
            registerStudent(EMAIL);
            register("STUDENT@Test.DEV", STRONG_PASSWORD, "VERIFIER")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        }

        @Test
        @DisplayName("400: returns an error for each invalid field")
        void validationErrors() throws Exception {
            register("not-an-email", "weak", "STUDENT")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors.email").exists())
                    .andExpect(jsonPath("$.errors.password").exists());
        }

        @ParameterizedTest(name = "400: cannot self-register as {0}")
        @ValueSource(strings = {"SUPER_ADMIN", "INSTITUTION_ADMIN"})
        void blocksPrivilegedRoles(String role) throws Exception {
            register(EMAIL, STRONG_PASSWORD, role)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        }
    }

    @Nested
    @DisplayName("POST /login")
    class Login {

        @Test
        @DisplayName("200: correct credentials")
        void succeeds() throws Exception {
            registerStudent(EMAIL);
            login(EMAIL, STRONG_PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.email").value(EMAIL));
        }

        @Test
        @DisplayName("401: same error for wrong password and unknown email")
        void doesNotRevealWhichEmailsExist() throws Exception {
            registerStudent(EMAIL);
            login(EMAIL, "Wr0ng@Pass")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
            login("nobody@test.dev", STRONG_PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("423: account locks after max failed attempts (3 in tests)")
        void locksAccount() throws Exception {
            registerStudent(EMAIL);

            for (int i = 0; i < 3; i++) {
                login(EMAIL, "Wr0ng@Pass").andExpect(status().isUnauthorized());
            }
            login(EMAIL, STRONG_PASSWORD)
                    .andExpect(status().isLocked())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        }
    }

    @Nested
    @DisplayName("GET /me")
    class CurrentUser {

        @Test
        @DisplayName("200: valid access token")
        void withValidToken() throws Exception {
            Tokens tokens = registerStudent(EMAIL);
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(EMAIL));
        }

        @Test
        @DisplayName("401 UNAUTHORIZED: no token")
        void withoutToken() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("401 INVALID_TOKEN: malformed token")

        void withBadToken() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer("not.a.jwt")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }
    }

    @Nested
    @DisplayName("POST /refresh and /logout")
    class RefreshAndLogout {

        @Test
        @DisplayName("200: refresh returns a NEW refresh token (rotation)")
        void rotates() throws Exception {
            Tokens first = registerStudent(EMAIL);
            Tokens second = Tokens.from(refresh(first.refreshToken()).andExpect(status().isOk()).andReturn());
            assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        }

        @Test
        @DisplayName("401: reusing an old token revokes ALL sessions (theft detection)")
        void reuseRevokesEverything() throws Exception {
            Tokens first = registerStudent(EMAIL);
            Tokens second = Tokens.from(refresh(first.refreshToken()).andExpect(status().isOk()).andReturn());

            refresh(first.refreshToken())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
            refresh(second.refreshToken())
                    .andExpect(status().isUnauthorized());
        }


        @Test
        @DisplayName("204: logout kills the refresh token")
        void logoutRevokes() throws Exception {
            Tokens tokens = registerStudent(EMAIL);
            logout(tokens.refreshToken()).andExpect(status().isNoContent());
            refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /change-password")
    class ChangePassword {

        private static String body(String current, String next) {
            return """
                    {"currentPassword": "%s", "newPassword": "%s"}
                    """.formatted(current, next);
        }

        @Test
        @DisplayName("204: changes password and logs out all sessions")
        void changesPassword() throws Exception {
            Tokens tokens = registerStudent(EMAIL);

            mockMvc.perform(post("/api/v1/auth/change-password")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(STRONG_PASSWORD, NEW_PASSWORD)))
                    .andExpect(status().isNoContent());

            refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());
            login(EMAIL, STRONG_PASSWORD).andExpect(status().isUnauthorized());

            login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
        }

        @Test
        @DisplayName("400: wrong current password")
        void wrongCurrentPassword() throws Exception {
            Tokens tokens = registerStudent(EMAIL);

            mockMvc.perform(post("/api/v1/auth/change-password")
                            .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("Wr0ng@Pass", NEW_PASSWORD)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));
        }
    }
}