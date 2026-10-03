package com.credchain.modules.auth.domain;

import com.credchain.modules.user.domain.Role;
import com.credchain.modules.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RefreshToken domain rules")
class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");
    private static final Instant EXPIRES = NOW.plusSeconds(3600);

    private static RefreshToken newToken(String userAgent) {
        User user = User.create("a@b.com", "{bcrypt}x", "A B", null, Role.STUDENT);
        return RefreshToken.create(user, "a".repeat(64), EXPIRES, "127.0.0.1", userAgent);
    }

    @Test
    @DisplayName("is active (not revoked, not expired) right after creation")
    void freshToken() {
        RefreshToken token = newToken("JUnit");
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.isExpired(NOW)).isFalse();
    }

    @Test

    @DisplayName("is expired exactly at expiresAt and after")
    void expiry() {
        RefreshToken token = newToken("JUnit");
        assertThat(token.isExpired(EXPIRES.minusSeconds(1))).isFalse();
        assertThat(token.isExpired(EXPIRES)).isTrue();
        assertThat(token.isExpired(EXPIRES.plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("revoke is idempotent: the first revocation time is kept")
    void revokeIdempotent() {
        RefreshToken token = newToken("JUnit");
        token.revoke(NOW);
        token.revoke(NOW.plusSeconds(100));
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("replaceWith revokes and links to the successor token")
    void rotation() {
        RefreshToken token = newToken("JUnit");
        UUID successor = UUID.randomUUID();
        token.replaceWith(successor, NOW);
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getReplacedById()).isEqualTo(successor);
    }

    @Test
    @DisplayName("truncates very long user-agent strings to fit the DB column")
    void truncatesUserAgent() {
        RefreshToken token = newToken("x".repeat(500));

        assertThat(token.getUserAgent()).hasSize(255);
    }
}