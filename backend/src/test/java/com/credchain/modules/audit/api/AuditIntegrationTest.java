package com.credchain.modules.audit.api;

import com.credchain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Audit log")
class AuditIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_AUDIT = "/api/v1/admin/audit";
    private static final String COLLEGE_AUDIT = "/api/v1/institution/audit";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("records the onboarding of a college, its first logins and a student added, newest first")
    void recordsOnboarding() throws Exception {
        String admin = onboardInstitution("AUD-ONE", "registrar@audone.test");
        createStudent(admin, "2022CS001", "Asha Patil");

        List<Map<String, Object>> entries = read(fetch(ADMIN_AUDIT, superAdminToken()), "$.content");
        List<Object> actions = entries.stream().map(e -> e.get("action")).toList();

        assertThat(actions).containsSubsequence(
                "STUDENT_ADDED", "LOGIN_SUCCEEDED", "PASSWORD_CHANGED", "LOGIN_SUCCEEDED",
                "INSTITUTION_APPROVED", "INSTITUTION_APPLIED");

        Map<String, Object> approved = entry(entries, "INSTITUTION_APPROVED");
        assertThat(approved.get("actorEmail")).isEqualTo(SUPER_ADMIN_EMAIL);
        assertThat(approved.get("actorRole")).isEqualTo("SUPER_ADMIN");
        assertThat(approved.get("category")).isEqualTo("INSTITUTION");
        assertThat((String) approved.get("summary"))
                .isEqualTo("Approved AUD-ONE College (AUD-ONE) and created the admin account registrar@audone.test");
        assertThat(approved.get("ipAddress")).isNotNull();

        Map<String, Object> student = entry(entries, "STUDENT_ADDED");
        assertThat(student.get("actorEmail")).isEqualTo("registrar@audone.test");
        assertThat(student.get("summary")).isEqualTo("Added student Asha Patil (2022CS001)");
    }

    @Test
    @DisplayName("a wrong password and an unknown email are both recorded as failed logins")
    void recordsFailedLogins() throws Exception {
        registerStudent("asha@audit.test");
        login("asha@audit.test", "Wrong@Pass1").andExpect(status().isUnauthorized());
        login("nobody@audit.test", "Wrong@Pass1").andExpect(status().isUnauthorized());

        List<Map<String, Object>> entries = read(
                fetch(ADMIN_AUDIT + "?category=ACCOUNT", superAdminToken()), "$.content");
        List<Map<String, Object>> failed = entries.stream().filter(e -> "LOGIN_FAILED".equals(e.get("action"))).toList();

        assertThat(failed).extracting(e -> e.get("actorEmail"))
                .containsExactlyInAnyOrder("asha@audit.test", "nobody@audit.test");
        assertThat(failed).extracting(e -> e.get("summary"))
                .containsExactlyInAnyOrder("Failed login: wrong password", "Failed login: no account with this email");
        assertThat(entries).allMatch(e -> "ACCOUNT".equals(e.get("category")));
    }

    @Test
    @DisplayName("a college admin sees only their own college's entries; students cannot read the log")
    void collegeSeesOnlyItsOwnEntries() throws Exception {
        String first = onboardInstitution("AUD-ONE", "registrar@audone.test");
        String second = onboardInstitution("AUD-TWO", "registrar@audtwo.test");
        createStudent(first, "2022CS001", "Asha Patil");
        createStudent(second, "2022CS002", "Ravi Kumar");

        List<String> summaries = read(fetch(COLLEGE_AUDIT + "?category=STUDENT", first), "$.content[*].summary");
        assertThat(summaries).containsExactly("Added student Asha Patil (2022CS001)");

        List<String> emails = read(fetch(COLLEGE_AUDIT, first), "$.content[*].actorEmail");
        assertThat(emails).doesNotContain("registrar@audtwo.test");

        String student = registerStudent("ravi@audit.test").accessToken();
        mockMvc.perform(get(COLLEGE_AUDIT).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_AUDIT).header(HttpHeaders.AUTHORIZATION, bearer(first)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("entries cannot be changed or deleted in the database")
    void entriesAreAppendOnly() throws Exception {
        registerStudent("asha@audit.test");

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE audit_events SET summary = 'nothing happened'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_events"))
                .hasMessageContaining("append-only");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class)).isEqualTo(1);
    }

    private MvcResult fetch(String url, String token) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static Map<String, Object> entry(List<Map<String, Object>> entries, String action) {
        return entries.stream().filter(e -> action.equals(e.get("action"))).findFirst().orElseThrow();
    }
}
