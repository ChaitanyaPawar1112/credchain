package com.credchain.modules.student.api;

import com.credchain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Student management API")
class StudentManagementIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("tenant isolation: one institution can't see another's students (404, not 403)")
    void tenantIsolation() throws Exception {
        String adminA = onboardInstitution("SIT-AUR", "registrar@sit.test");
        String adminB = onboardInstitution("MIT-PUNE", "registrar@mit.test");
        String studentOfA = createStudent(adminA, "2022CS001", "Chaitanya Pawar");

        mockMvc.perform(get("/api/v1/institution/students/{id}", studentOfA)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/institution/students").header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // B can reuse the same enrollment number: uniqueness is per institution
        createStudent(adminB, "2022CS001", "Different Person");
    }

    @Test
    @DisplayName("409: duplicate enrollment number in the same institution (case-insensitive)")
    void duplicateEnrollment() throws Exception {
        String admin = onboardInstitution("SIT-AUR", "registrar@sit.test");
        createStudent(admin, "2022CS001", "Chaitanya Pawar");

        postJson("/api/v1/institution/students", """
                {"enrollmentNo": "2022cs001", "fullName": "Someone", "program": "B.Tech", "admissionYear": 2022}
                """, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_ALREADY_EXISTS"));
    }

    @Nested
    @DisplayName("CSV import")
    class CsvImport {

        private ResultActions upload(String adminToken, byte[] content, String filename, boolean dryRun) throws Exception {
            MockMultipartFile file = new MockMultipartFile("file", filename, "text/csv", content);
            return mockMvc.perform(multipart("/api/v1/institution/students/import")
                    .file(file)
                    .param("dryRun", String.valueOf(dryRun))
                    .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)));
        }

        private byte[] sampleCsv() throws Exception {
            try (var in = new ClassPathResource("csv/students-sample.csv").getInputStream()) {
                return in.readAllBytes();
            }
        }

        @Test
        @DisplayName("dry run reports problems, real run saves valid rows, re-import saves nothing")
        void sampleFile() throws Exception {
            String admin = onboardInstitution("SIT-AUR", "registrar@sit.test");
            createStudent(admin, "2022CS001", "Chaitanya Pawar");   // so row 5 is "already exists"
            byte[] csv = sampleCsv();

            upload(admin, csv, "students.csv", true)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRows").value(9))
                    .andExpect(jsonPath("$.valid").value(3))
                    .andExpect(jsonPath("$.imported").value(0))
                    .andExpect(jsonPath("$.failed").value(6))
                    .andExpect(jsonPath("$.errors[0].row").value(5));

            upload(admin, csv, "students.csv", false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imported").value(3));

            upload(admin, csv, "students.csv", false)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.imported").value(0))
                    .andExpect(jsonPath("$.failed").value(9));

            mockMvc.perform(get("/api/v1/institution/students").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(jsonPath("$.totalElements").value(4));
        }

        @Test
        @DisplayName("400: missing required columns")
        void missingColumns() throws Exception {
            String admin = onboardInstitution("SIT-AUR", "registrar@sit.test");
            upload(admin, "name,program\nA,B\n".getBytes(StandardCharsets.UTF_8), "students.csv", false)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_FILE"));
        }

        @Test
        @DisplayName("400: non-CSV file")
        void wrongExtension() throws Exception {
            String admin = onboardInstitution("SIT-AUR", "registrar@sit.test");
            upload(admin, "hello".getBytes(StandardCharsets.UTF_8), "students.txt", false)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_FILE"));
        }
    }

    @Nested
    @DisplayName("claim-code account linking")
    class Linking {

        private static String linkBody(String institutionCode, String enrollmentNo, String claimCode) {
            return """
                    {"institutionCode": "%s", "enrollmentNo": "%s", "claimCode": "%s"}
                    """.formatted(institutionCode, enrollmentNo, claimCode);
        }

        private String issueCode(String adminToken, String studentId) throws Exception {
            return read(mockMvc.perform(post("/api/v1/institution/students/{id}/claim-code", studentId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andReturn(), "$.claimCode");
        }

        @Test
        @DisplayName("full flow: wrong code 400, right code links, re-link 409, imposter 400")
        void linkFlow() throws Exception {
            String admin = onboardInstitution("SIT-AUR", "registrar@sit.test");
            String studentId = createStudent(admin, "2022CS001", "Chaitanya Pawar");
            String code = issueCode(admin, studentId);
            Tokens student = registerStudent("chaitanya@test.dev");

            mockMvc.perform(get("/api/v1/me/student-profile").header(HttpHeaders.AUTHORIZATION, bearer(student.accessToken())))
                    .andExpect(status().isNotFound());

            postJson("/api/v1/me/student-profile/link", linkBody("SIT-AUR", "2022CS001", "AAAA-BBBB"), student.accessToken())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CLAIM_CODE"));

            postJson("/api/v1/me/student-profile/link", linkBody("XYZ-123", "2022CS001", code), student.accessToken())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CLAIM_CODE"));

            String typedCasually = code.toLowerCase().replace("-", "");
            postJson("/api/v1/me/student-profile/link", linkBody("sit-aur", "2022cs001", typedCasually), student.accessToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.record.accountLinked").value(true))
                    .andExpect(jsonPath("$.institutionCode").value("SIT-AUR"));

            mockMvc.perform(get("/api/v1/me/student-profile").header(HttpHeaders.AUTHORIZATION, bearer(student.accessToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.record.enrollmentNo").value("2022CS001"));

            postJson("/api/v1/me/student-profile/link", linkBody("SIT-AUR", "2022CS001", code), student.accessToken())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("STUDENT_ALREADY_LINKED"));

            Tokens imposter = registerStudent("imposter@test.dev");
            postJson("/api/v1/me/student-profile/link", linkBody("SIT-AUR", "2022CS001", code), imposter.accessToken())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CLAIM_CODE"));
        }

        @Test
        @DisplayName("404: an institution can't issue a code for another institution's student")
        void cannotIssueForOtherInstitution() throws Exception {
            String adminA = onboardInstitution("SIT-AUR", "registrar@sit.test");
            String adminB = onboardInstitution("MIT-PUNE", "registrar@mit.test");
            String studentOfA = createStudent(adminA, "2022CS001", "Chaitanya Pawar");

            mockMvc.perform(post("/api/v1/institution/students/{id}/claim-code", studentOfA)
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminB)))
                    .andExpect(status().isNotFound());
        }
    }
}