package com.credchain.modules.user.api;

import com.credchain.modules.user.domain.Role;
import com.credchain.modules.user.domain.User;
import com.credchain.modules.user.infrastructure.UserRepository;
import com.credchain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Admin Users API (role-based access)")
class AdminUserControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/admin/users";
    private static final String ADMIN_EMAIL = "admin@test.dev";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Admins can't self-register, so the test inserts one directly, like the bootstrap does. */
    private Tokens createAdminAndLogin() throws Exception {
        userRepository.save(User.create(
                ADMIN_EMAIL, passwordEncoder.encode(STRONG_PASSWORD), "Test Admin", null, Role.SUPER_ADMIN));
        return Tokens.from(login(ADMIN_EMAIL, STRONG_PASSWORD).andExpect(status().isOk()).andReturn());
    }

    @Test
    @DisplayName("200: SUPER_ADMIN sees all users")
    void adminCanList() throws Exception {
        Tokens admin = createAdminAndLogin();
        registerStudent("student@test.dev");

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("403: STUDENT is forbidden")
    void studentForbidden() throws Exception {
        Tokens student = registerStudent("student@test.dev");

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, bearer(student.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("401: anonymous request")
    void anonymousUnauthorized() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("400: page size above 100 is rejected")
    void rejectsHugePageSize() throws Exception {
        Tokens admin = createAdminAndLogin();

        mockMvc.perform(get(URL).param("size", "500")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.accessToken())))
                .andExpect(status().isBadRequest());
    }
}