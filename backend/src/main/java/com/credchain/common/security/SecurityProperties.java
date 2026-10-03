package com.credchain.common.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Type-safe binding of the "app.security.*" settings in application.yaml.
 * @Validated makes the app FAIL AT STARTUP if a setting is missing or invalid,
 * instead of failing later at runtime.
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Login login,
        @Valid @NotNull Cors cors
) {

    public record Jwt(
            @NotBlank String issuer,
            @NotBlank @Size(min = 44, message = "JWT secret must be at least 256 bits (44 base64 chars)")
            String secret,

            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl
    ) {}

    public record Login(
            @Min(1) int maxFailedAttempts,
            @NotNull Duration lockDuration
    ) {}

    /**
     * Each origin must be a real URL. This also catches an unset
     * ${CORS_ALLOWED_ORIGINS} placeholder, which Spring would otherwise keep as literal text.
     */
    public record Cors(
            @NotEmpty
            List<@NotBlank @Pattern(
                    regexp = "^https?://[A-Za-z0-9.-]+(:\\d{1,5})?$",
                    message = "must be a URL like https://credchain.app or http://localhost:5173 (set CORS_ALLOWED_ORIGINS)")
                    String> allowedOrigins
    ) {}
}