package com.credchain.modules.fraud.infrastructure;

import com.credchain.modules.fraud.config.FraudServiceProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("HTTP client of the fraud-detection service")
class HttpFraudServiceClientTest {

    private static final String URL = "http://fraud.test/v1/analyze/document";

    private static final String ANSWER = """
            {"riskScore": 65, "riskLevel": "HIGH", "summary": "Strong signs", "fileType": "application/pdf",
             "pagesAnalysed": 1,
             "qr": {"found": true, "texts": ["http://x/verify/0xabc"], "certHash": "0x%s"},
             "text": {"available": true, "text": "Sneha Joshi", "wordCount": 2},
             "signals": [{"code": "PDF_EDITED_AFTER_SAVE", "severity": "HIGH", "weight": 35, "detail": "Edited"}],
             "elaImage": null, "serviceVersion": "1.0.0", "somethingNew": 1}
            """.formatted("b".repeat(64));

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private HttpFraudServiceClient client(boolean enabled, String apiKey) {
        return new HttpFraudServiceClient(new FraudServiceProperties(enabled, "http://fraud.test", apiKey,
                Duration.ofSeconds(1), Duration.ofSeconds(5)), builder);
    }

    @Test
    @DisplayName("sends the file as multipart with the API key and reads the answer (unknown fields ignored)")
    void analyses() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Api-Key", "k3y"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

        DocumentAnalysis analysis = client(true, "k3y").analyse(new byte[]{1, 2, 3}, "photo.jpg");

        assertThat(analysis.riskScore()).isEqualTo(65);
        assertThat(analysis.riskLevel()).isEqualTo(DocumentAnalysis.RiskLevel.HIGH);
        assertThat(analysis.qr().certHash()).isEqualTo("0x" + "b".repeat(64));
        assertThat(analysis.text().text()).isEqualTo("Sneha Joshi");
        assertThat(analysis.signals()).extracting(DocumentAnalysis.Signal::code).containsExactly("PDF_EDITED_AFTER_SAVE");
        server.verify();
    }

    @Test
    @DisplayName("a refused file comes back as fileRejected with the service's own message")
    void fileRejected() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.APPLICATION_JSON).body("{\"detail\": \"Upload a PDF, JPEG, PNG or WebP file.\"}"));

        assertThatThrownBy(() -> client(true, "").analyse(new byte[]{1}, "a.zip"))
                .isInstanceOfSatisfying(FraudServiceException.class, e -> {
                    assertThat(e.isFileRejected()).isTrue();
                    assertThat(e.getMessage()).isEqualTo("Upload a PDF, JPEG, PNG or WebP file.");
                });
    }

    @Test
    @DisplayName("a server error or wrong key means unavailable, not the user's fault")
    void serverError() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(() -> client(true, "").analyse(new byte[]{1}, "a.jpg"))
                .isInstanceOfSatisfying(FraudServiceException.class, e -> assertThat(e.isFileRejected()).isFalse());

        server.reset();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client(true, "wrong").analyse(new byte[]{1}, "a.jpg"))
                .isInstanceOfSatisfying(FraudServiceException.class, e -> assertThat(e.isFileRejected()).isFalse());
    }

    @Test
    @DisplayName("institution analysis: sends the activity as JSON and reads the trust scores")
    void institutions() {
        server.expect(requestTo("http://fraud.test/v1/analyze/institutions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.today").value("2026-10-27"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.days[0].offHoursIssued").value(7))
                .andRespond(withSuccess("""
                        {"model": "IsolationForest", "modelUsed": true, "rowsAnalysed": 1,
                         "institutions": [{"institutionId": "c1", "name": "College", "trustScore": 75, "level": "WATCH",
                                           "reasons": ["7 certificates issued between midnight and 6 a.m."],
                                           "anomalies": [{"date": "2026-10-27", "score": 0.6, "detail": "night"}]}]}
                        """, MediaType.APPLICATION_JSON));

        InstitutionTrustAnalysis analysis = client(true, "").analyseInstitutions(new InstitutionActivity(
                java.time.LocalDate.of(2026, 10, 27),
                java.util.List.of(new InstitutionActivity.Institution("c1", "College")),
                java.util.List.of(new InstitutionActivity.Day("c1", java.time.LocalDate.of(2026, 10, 27), 7, 0, 0, 0, 0, 7))));

        assertThat(analysis.modelUsed()).isTrue();
        assertThat(analysis.institutions().get(0).trustScore()).isEqualTo(75);
        assertThat(analysis.institutions().get(0).anomalies().get(0).date()).isEqualTo(java.time.LocalDate.of(2026, 10, 27));
        server.verify();
    }

    @Test
    @DisplayName("switched off: nothing is called")
    void disabled() {
        assertThatThrownBy(() -> client(false, "").analyse(new byte[]{1}, "a.jpg"))
                .isInstanceOfSatisfying(FraudServiceException.class, e -> assertThat(e.isFileRejected()).isFalse());
        server.verify();   // no request was expected, none was made
    }

    @Test
    @DisplayName("nothing listening on the port means unavailable")
    void unreachable() {
        HttpFraudServiceClient real = new HttpFraudServiceClient(new FraudServiceProperties(true, "http://127.0.0.1:1", "",
                Duration.ofMillis(500), Duration.ofSeconds(1)));
        assertThatThrownBy(() -> real.analyse(new byte[]{1}, "a.jpg"))
                .isInstanceOfSatisfying(FraudServiceException.class, e -> assertThat(e.isFileRejected()).isFalse());
    }
}
