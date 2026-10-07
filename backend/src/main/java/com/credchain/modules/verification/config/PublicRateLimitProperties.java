package com.credchain.modules.verification.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Limits for the public verification API, per client IP (app.public-rate-limit.* in application.yaml). */
@Validated
@ConfigurationProperties(prefix = "app.public-rate-limit")
public record PublicRateLimitProperties(

        boolean enabled,

        /** Checks by hash / QR link per minute. */
        @Min(1) int lookupsPerMinute,

        /** PDF uploads per minute (heavier: the PDF is parsed and hashed). */
        @Min(1) int uploadsPerMinute,

        /**
         * true only when the app runs behind our own reverse proxy (nginx, load balancer) that sets X-Forwarded-For.
         * Otherwise anyone could send a fake header to dodge the limit.
         */
        boolean trustForwardedFor
) {
}
