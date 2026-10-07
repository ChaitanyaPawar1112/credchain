package com.credchain.modules.storage;

import com.credchain.modules.storage.config.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StorageProperties")
class StoragePropertiesTest {

    @Test
    @DisplayName("trims .env values and never prints the keys")
    void trimsAndHidesSecrets() {
        var properties = new StorageProperties(true, " http://localhost:9000 ", "us-east-1",
                "credchain\n", " super-secret-password ", "credchain-certificates", true);

        assertThat(properties.endpoint()).isEqualTo("http://localhost:9000");
        assertThat(properties.secretKey()).isEqualTo("super-secret-password");
        assertThat(properties.hasStaticCredentials()).isTrue();
        assertThat(properties.toString()).doesNotContain("super-secret-password", "credchain\n")
                .contains("secretKey=<hidden>");
    }

    @Test
    @DisplayName("access and secret key must be set together")
    void credentialsComeInPairs() {
        assertThat(new StorageProperties(true, "", "us-east-1", "", "", "b-1", false).isCredentialsPairValid()).isTrue();
        assertThat(new StorageProperties(true, "", "us-east-1", "key", "", "b-1", false).isCredentialsPairValid()).isFalse();
    }
}