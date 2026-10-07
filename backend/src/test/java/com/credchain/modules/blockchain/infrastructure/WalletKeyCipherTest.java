package com.credchain.modules.blockchain.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WalletKeyCipher (AES-256-GCM)")
class WalletKeyCipherTest {

    private static final String KEY = "3aDSMzeQgUkgELHe+e7iCke/z5O/c/7OxNJwq+RkZYA=";
    private static final String CONTEXT = "institution:7d1f0c9e-0000-4000-8000-000000000001";

    private final WalletKeyCipher cipher = new WalletKeyCipher(KEY);

    @Test
    @DisplayName("encrypt then decrypt returns the original bytes, in v1 format")
    void roundTrip() {
        byte[] privateKey = randomBytes(32);

        String token = cipher.encrypt(privateKey, CONTEXT);

        assertThat(token).startsWith("v1:");
        assertThat(cipher.decrypt(token, CONTEXT)).isEqualTo(privateKey);
    }

    @Test
    @DisplayName("same input encrypts differently every time (random IV)")

    void randomIv() {
        byte[] privateKey = randomBytes(32);
        assertThat(cipher.encrypt(privateKey, CONTEXT)).isNotEqualTo(cipher.encrypt(privateKey, CONTEXT));
    }

    @Test
    @DisplayName("a key copied to another record (different context) cannot be decrypted")
    void wrongContext() {
        String token = cipher.encrypt(randomBytes(32), CONTEXT);
        assertThatThrownBy(() -> cipher.decrypt(token, "institution:someone-else"))
                .isInstanceOf(WalletKeyCipher.WalletKeyDecryptionException.class);
    }

    @Test
    @DisplayName("a single modified byte is detected")
    void tampering() {
        String token = cipher.encrypt(randomBytes(32), CONTEXT);
        byte[] payload = Base64.getDecoder().decode(token.substring(3));
        payload[payload.length - 1] ^= 0x01;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(payload);

        assertThatThrownBy(() -> cipher.decrypt(tampered, CONTEXT))
                .isInstanceOf(WalletKeyCipher.WalletKeyDecryptionException.class);
    }

    @Test
    @DisplayName("a different master key cannot decrypt")
    void wrongMasterKey() {
        String token = cipher.encrypt(randomBytes(32), CONTEXT);
        WalletKeyCipher other = new WalletKeyCipher(Base64.getEncoder().encodeToString(randomBytes(32)));

        assertThatThrownBy(() -> other.decrypt(token, CONTEXT))

                .isInstanceOf(WalletKeyCipher.WalletKeyDecryptionException.class);
    }

    @Test
    @DisplayName("invalid master keys are rejected")
    void invalidKeys() {
        assertThat(WalletKeyCipher.isValidKey(KEY)).isTrue();
        assertThat(WalletKeyCipher.isValidKey(null)).isFalse();
        assertThat(WalletKeyCipher.isValidKey("")).isFalse();
        assertThat(WalletKeyCipher.isValidKey("not-base64!!")).isFalse();
        assertThat(WalletKeyCipher.isValidKey(Base64.getEncoder().encodeToString(randomBytes(16)))).isFalse();
    }

    @Test
    @DisplayName("garbage tokens are rejected without leaking details")
    void garbageTokens() {
        assertThatThrownBy(() -> cipher.decrypt("plain-text-key", CONTEXT))
                .isInstanceOf(WalletKeyCipher.WalletKeyDecryptionException.class);
        assertThatThrownBy(() -> cipher.decrypt("v1:AAAA", CONTEXT))
                .isInstanceOf(WalletKeyCipher.WalletKeyDecryptionException.class);
    }

    private static byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}