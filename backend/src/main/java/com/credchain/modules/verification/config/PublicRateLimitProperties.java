package com.credchain.modules.verification.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limits per client IP for everything anyone can call without logging in: the public verification API and the
 * sign-in forms (app.public-rate-limit.* in application.yaml).
 */
@Validated
@ConfigurationProperties(prefix = "app.public-rate-limit")
public record PublicRateLimitProperties(

        boolean enabled,

        /** Checks by hash / QR link per minute. */
        @Min(1) int lookupsPerMinute,

        /** PDF uploads per minute (heavier: the PDF is parsed and hashed). */
        @Min(1) int uploadsPerMinute,

        /** Login, sign-up and college applications per minute (slows down password guessing and spam sign-ups). */
        @Min(1) int signInsPerMinute,

        /**
         * true only when the app runs behind our own reverse proxy (nginx, load balancer) that sets X-Forwarded-For.
         * Otherwise anyone could send a fake header to dodge the limit.
         */
        boolean trustForwardedFor
) {
}
