package com.credchain.modules.share.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** app.share.* in application.yaml. */
@Validated
@ConfigurationProperties(prefix = "app.share")
public record ShareLinkProperties(

        /** e.g. http://localhost:5173/s -> a link opens http://localhost:5173/s/{token} */
        @NotBlank
        @Pattern(regexp = "^https?://\\S+[^/]$", message = "must be an http(s) URL without a trailing slash")
        String baseUrl,

        /** How many links a student may have switched on at the same time. */
        @Min(1) @Max(200) int maxActivePerStudent
) {

    public String urlFor(String token) {
        return baseUrl + "/" + token;
    }
}
