package com.credchain.support;

import com.credchain.modules.user.domain.Role;
import com.credchain.modules.user.domain.User;
import com.credchain.modules.user.infrastructure.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for API tests: full app + real test DB, cleaned before every test.
 * All subclasses share ONE Spring context, so the app starts only once per test run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryObjectStorage.class)   // stands in for RustFS/S3 (app.storage.enabled=false in tests)
public abstract class AbstractIntegrationTest {

    protected static final String STRONG_PASSWORD = "Str0ng@Pass";
    protected static final String SUPER_ADMIN_EMAIL = "root@test.dev";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE refresh_tokens, students, users, institutions CASCADE");
    }

    // ---------- Phase 1 request helpers ----------

    protected ResultActions register(String email, String password, String role) throws Exception {
        String body = """
                {"email": "%s", "password": "%s", "fullName": "Test User", "role": "%s"}
                """.formatted(email, password, role);
        return postJson("/api/v1/auth/register", body);
    }

    protected ResultActions login(String email, String password) throws Exception {
        String body = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
        return postJson("/api/v1/auth/login", body);
    }

    protected ResultActions refresh(String refreshToken) throws Exception {
        return postJson("/api/v1/auth/refresh", """
                {"refreshToken": "%s"}
                """.formatted(refreshToken));
    }

    protected ResultActions logout(String refreshToken) throws Exception {
        return postJson("/api/v1/auth/logout", """
                {"refreshToken": "%s"}
                """.formatted(refreshToken));
    }

    protected ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions postJson(String url, String json, String accessToken) throws Exception {
        return mockMvc.perform(post(url)
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** Registers a STUDENT and returns their tokens (fails the test if registration fails). */
    protected Tokens registerStudent(String email) throws Exception {
        return Tokens.from(register(email, STRONG_PASSWORD, "STUDENT")
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    @SuppressWarnings("unchecked")
    protected static <T> T read(MvcResult result, String jsonPath) throws Exception {
        return (T) JsonPath.read(result.getResponse().getContentAsString(), jsonPath);
    }

    // ---------- Phase 2 helpers ----------

    /** Inserts a SUPER_ADMIN directly (like the bootstrap does) and returns an access token. */
    protected String superAdminToken() throws Exception {
        if (!userRepository.existsByEmail(SUPER_ADMIN_EMAIL)) {
            userRepository.save(User.create(SUPER_ADMIN_EMAIL, passwordEncoder.encode(STRONG_PASSWORD),
                    "Root Admin", null, Role.SUPER_ADMIN));
        }
        return Tokens.from(login(SUPER_ADMIN_EMAIL, STRONG_PASSWORD).andExpect(status().isOk()).andReturn())
                .accessToken();
    }

    /** Submits a public application and returns its id. */
    protected String applyInstitution(String code, String contactEmail) throws Exception {
        String body = """
                {
                  "name": "%s College",
                  "code": "%s",
                  "registrationNumber": "REG-%s",
                  "type": "COLLEGE",
                  "email": "office@%s.test",
                  "city": "Pune",
                  "state": "Maharashtra",
                  "contactPersonName": "Registrar %s",
                  "contactPersonEmail": "%s"
                }
                """.formatted(code, code, code, code.toLowerCase(), code, contactEmail);
        return read(postJson("/api/v1/institutions/applications", body)
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    /** Approves an application and returns the admin's temporary password. */
    protected String approveInstitution(String institutionId, String superToken) throws Exception {
        return read(mockMvc.perform(post("/api/v1/admin/institutions/{id}/approve", institutionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(superToken)))
                .andExpect(status().isOk())
                .andReturn(), "$.adminAccount.temporaryPassword");
    }

    protected static String changePasswordBody(String current, String next) {
        return """
                {"currentPassword": "%s", "newPassword": "%s"}
                """.formatted(current, next);
    }

    /**
     * Full onboarding: apply -> approve -> first login -> change temporary password -> login again.
     * Returns a ready-to-use INSTITUTION_ADMIN access token (password = STRONG_PASSWORD).
     */
    protected String onboardInstitution(String code, String adminEmail) throws Exception {
        String institutionId = applyInstitution(code, adminEmail);
        String tempPassword = approveInstitution(institutionId, superAdminToken());

        Tokens first = Tokens.from(login(adminEmail, tempPassword).andExpect(status().isOk()).andReturn());
        postJson("/api/v1/auth/change-password", changePasswordBody(tempPassword, STRONG_PASSWORD), first.accessToken())
                .andExpect(status().isNoContent());

        return Tokens.from(login(adminEmail, STRONG_PASSWORD).andExpect(status().isOk()).andReturn())
                .accessToken();
    }

    /** Adds a student through the API and returns the student's id. */
    protected String createStudent(String adminToken, String enrollmentNo, String fullName) throws Exception {
        String body = """
                {"enrollmentNo": "%s", "fullName": "%s", "program": "B.Tech Computer Engineering", "admissionYear": 2022}
                """.formatted(enrollmentNo, fullName);
        return read(postJson("/api/v1/institution/students", body, adminToken)
                .andExpect(status().isCreated())
                .andReturn(), "$.id");
    }

    /** Access + refresh token pulled out of an AuthResponse JSON body. */
    public record Tokens(String accessToken, String refreshToken) {

        public static Tokens from(MvcResult result) throws Exception {
            String body = result.getResponse().getContentAsString();
            return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
        }
    }
}