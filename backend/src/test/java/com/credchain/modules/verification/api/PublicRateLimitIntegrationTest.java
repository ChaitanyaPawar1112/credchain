package com.credchain.modules.verification.api;

import com.credchain.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Public verification rate limit")
@TestPropertySource(properties = {
        "app.public-rate-limit.lookups-per-minute=3",
        "app.public-rate-limit.uploads-per-minute=2"
})
class PublicRateLimitIntegrationTest extends AbstractIntegrationTest {

    private static final String VERIFY = "/api/v1/public/verify/{certHash}";
    private static final String HASH = "0x" + "9".repeat(64);

    @Test
    @DisplayName("over the limit: 429 RATE_LIMITED with Retry-After; another client is not affected")
    void lookupsLimitedPerClient() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(VERIFY, HASH).with(from("198.51.100.1"))).andExpect(status().isOk());
        }
        mockMvc.perform(get(VERIFY, HASH).with(from("198.51.100.1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));

        mockMvc.perform(get(VERIFY, HASH).with(from("198.51.100.2"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a fake X-Forwarded-For header does not get around the limit")
    void forwardedForIgnored() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get(VERIFY, HASH).with(from("198.51.100.3"))
                    .header("X-Forwarded-For", "203.0.113." + i)).andExpect(status().isOk());
        }
        mockMvc.perform(get(VERIFY, HASH).with(from("198.51.100.3")).header("X-Forwarded-For", "203.0.113.99"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("PDF uploads have their own, smaller limit")
    void uploadsLimited() throws Exception {
        MockMultipartFile notPdf = new MockMultipartFile("file", "x.txt", "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(multipart("/api/v1/public/verify/pdf").file(notPdf).with(from("198.51.100.4")))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(multipart("/api/v1/public/verify/pdf").file(notPdf).with(from("198.51.100.4")))
                .andExpect(status().isTooManyRequests());

        mockMvc.perform(get(VERIFY, HASH).with(from("198.51.100.4"))).andExpect(status().isOk());   // lookups still fine
    }

    @Test
    @DisplayName("logged-in APIs are not limited by this filter")
    void otherEndpointsNotLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/institution/verifications").with(from("198.51.100.5")))
                    .andExpect(status().isUnauthorized());   // normal 401, never 429
        }
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
