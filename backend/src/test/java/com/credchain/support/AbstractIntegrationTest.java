package com.credchain.support;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
public abstract class AbstractIntegrationTest {

    protected static final String STRONG_PASSWORD = "Str0ng@Pass";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE refresh_tokens, users CASCADE");
    }

    // ---------- request helpers ----------

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

    /** Registers a STUDENT and returns their tokens (fails the test if registration fails). */
    protected Tokens registerStudent(String email) throws Exception {
        return Tokens.from(register(email, STRONG_PASSWORD, "STUDENT")
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    /** Access + refresh token pulled out of an AuthResponse JSON body. */
    public record Tokens(String accessToken, String refreshToken) {

        public static Tokens from(MvcResult result) throws Exception {
            String body = result.getResponse().getContentAsString();
            return new Tokens(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
        }
    }
}