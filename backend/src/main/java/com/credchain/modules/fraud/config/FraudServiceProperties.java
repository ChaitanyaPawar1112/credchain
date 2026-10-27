package com.credchain.modules.fraud.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Where the Python fraud-detection service runs (app.fraud.* in application.yaml). */
@Validated
@ConfigurationProperties(prefix = "app.fraud")
public record FraudServiceProperties(

        /** false = the "check a photo or scan" feature answers "not available" without calling anything. */
        boolean enabled,

        /** e.g. http://localhost:8090 (the docker compose service "fraud"). */
        @NotBlank String baseUrl,

        /** Shared key sent as X-Api-Key; must match FRAUD_API_KEY of the service. Empty = none. */
        String apiKey,

        @NotNull Duration connectTimeout,

        /** OCR of a large photo can take several seconds. */
        @NotNull Duration readTimeout
) {
}
