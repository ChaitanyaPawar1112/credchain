package com.credchain.modules.certificate.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Public verification page (app.verification.* in application.yaml). The QR code on each PDF points here. */
@Validated
@ConfigurationProperties(prefix = "app.verification")
public record VerificationProperties(

        /** e.g. http://localhost:5173/verify -> QR opens http://localhost:5173/verify/0x{certHash} */
        @NotBlank
        @Pattern(regexp = "^https?://\\S+[^/]$", message = "must be an http(s) URL without a trailing slash")
        String baseUrl
) {

    public String urlFor(String certHash) {
        return baseUrl + "/" + certHash;
    }
}