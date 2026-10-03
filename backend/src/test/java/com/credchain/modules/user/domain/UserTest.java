package com.credchain.modules.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User domain rules")
class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-03T10:00:00Z");
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);

    private static User newStudent() {
        return User.create("  Chaitanya@Example.COM ", "{bcrypt}hash", "  Chaitanya Pawar  ", null, Role.STUDENT);
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("normalizes email to lowercase and trims it")
        void normalizesEmail() {
            assertThat(newStudent().getEmail()).isEqualTo("chaitanya@example.com");
        }


        @Test
        @DisplayName("trims the full name")
        void trimsFullName() {
            assertThat(newStudent().getFullName()).isEqualTo("Chaitanya Pawar");
        }

        @Test
        @DisplayName("starts ACTIVE, unverified, with zero failed attempts")
        void defaults() {
            User user = newStudent();
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.isActive()).isTrue();
            assertThat(user.isEmailVerified()).isFalse();
            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.isLocked(NOW)).isFalse();
        }
    }

    @Nested
    @DisplayName("login attempts & lockout")
    class Lockout {

        @Test
        @DisplayName("is NOT locked before reaching max failed attempts")
        void notLockedBelowLimit() {
            User user = newStudent();
            for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
                user.recordFailedLogin(NOW, MAX_ATTEMPTS, LOCK);
            }
            assertThat(user.isLocked(NOW)).isFalse();
            assertThat(user.getFailedLoginAttempts()).isEqualTo(MAX_ATTEMPTS - 1);
        }


        @Test
        @DisplayName("locks for the configured duration on the max-th failure")
        void locksAtLimit() {
            User user = newStudent();
            for (int i = 0; i < MAX_ATTEMPTS; i++) {
                user.recordFailedLogin(NOW, MAX_ATTEMPTS, LOCK);
            }
            assertThat(user.isLocked(NOW)).isTrue();
            assertThat(user.getLockedUntil()).isEqualTo(NOW.plus(LOCK));
            assertThat(user.getFailedLoginAttempts()).isZero(); // counter resets for the next window
        }

        @Test
        @DisplayName("unlocks automatically once the lock period has passed")
        void unlocksAfterDuration() {
            User user = newStudent();
            for (int i = 0; i < MAX_ATTEMPTS; i++) {
                user.recordFailedLogin(NOW, MAX_ATTEMPTS, LOCK);
            }
            assertThat(user.isLocked(NOW.plus(LOCK).minusSeconds(1))).isTrue();
            assertThat(user.isLocked(NOW.plus(LOCK))).isFalse();
        }

        @Test
        @DisplayName("successful login resets counter, clears lock and records time")
        void successfulLoginResets() {
            User user = newStudent();
            user.recordFailedLogin(NOW, MAX_ATTEMPTS, LOCK);
            user.recordFailedLogin(NOW, MAX_ATTEMPTS, LOCK);

            Instant later = NOW.plusSeconds(60);

            user.recordSuccessfulLogin(later);

            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.getLockedUntil()).isNull();
            assertThat(user.getLastLoginAt()).isEqualTo(later);
        }
    }

    @Test
    @DisplayName("changePassword replaces the stored hash")
    void changePassword() {
        User user = newStudent();
        user.changePassword("{bcrypt}newhash");
        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}newhash");
    }

    @Test
    @DisplayName("normalizeEmail handles null safely")
    void normalizeEmailNull() {
        assertThat(User.normalizeEmail(null)).isNull();
    }
}