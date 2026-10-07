package com.credchain.modules.storage.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * S3-compatible object storage for certificate PDFs (app.storage.* in application.yaml).
 * Locally this is RustFS in Docker; in production it can be AWS S3 with no code changes.
 * The access/secret keys come from infra/.env and are never logged.
 */
@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(

        /** Master switch. false = no S3 client (tests, or running without Docker). */
        boolean enabled,

        /** S3 API URL, e.g. http://localhost:9000. Empty = the real AWS S3 endpoint for the region. */
        String endpoint,

        @NotBlank String region,

        /** Empty = use the default AWS credentials chain (IAM role in production). Secret. */
        String accessKey,

        /** Secret. */
        String secretKey,

        /** Bucket names: 3-63 lowercase letters, digits, dots and hyphens. */
        @NotBlank
        @Pattern(regexp = "^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", message = "must be a valid S3 bucket name")
        String bucket,

        /** true for RustFS/MinIO (http://host:9000/bucket/key), false for AWS (bucket.s3.amazonaws.com). */
        boolean pathStyleAccess
) {

    /** Trim accidental spaces/newlines from values read out of .env files. */
    public StorageProperties {
        endpoint = strip(endpoint);
        accessKey = strip(accessKey);
        secretKey = strip(secretKey);
        bucket = strip(bucket);
    }

    @AssertTrue(message = "app.storage.endpoint (S3_ENDPOINT) must be an http(s) URL or empty")
    public boolean isEndpointValid() {
        return endpoint == null || endpoint.isEmpty() || endpoint.matches("^https?://\\S+$");
    }

    @AssertTrue(message = "S3_ACCESS_KEY and S3_SECRET_KEY must both be set, or both be empty")
    public boolean isCredentialsPairValid() {
        return isBlank(accessKey) == isBlank(secretKey);
    }

    public boolean hasStaticCredentials() {
        return !isBlank(accessKey);
    }

    /** Never print secrets, even by accident in a log or debugger. */
    @Override
    public String toString() {
        return "StorageProperties[enabled=" + enabled
                + ", endpoint=" + endpoint
                + ", region=" + region
                + ", accessKey=" + (isBlank(accessKey) ? "<empty>" : "<hidden>")
                + ", secretKey=" + (isBlank(secretKey) ? "<empty>" : "<hidden>")
                + ", bucket=" + bucket
                + ", pathStyleAccess=" + pathStyleAccess + "]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}