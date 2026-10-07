package com.credchain.modules.verification.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Rate limiter (one-minute windows)")
class FixedWindowRateLimiterTest {

    /** A clock the test can move forward. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-07T10:00:00Z");

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    @DisplayName("allows the limit, blocks the next request until the minute is over, then allows again")
    void limitAndReset() {
        MovableClock clock = new MovableClock();
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(clock);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("lookup:1.2.3.4", 3).allowed()).isTrue();
        }
        clock.advanceSeconds(20);
        FixedWindowRateLimiter.Decision blocked = limiter.tryAcquire("lookup:1.2.3.4", 3);
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isEqualTo(40);

        clock.advanceSeconds(40);
        assertThat(limiter.tryAcquire("lookup:1.2.3.4", 3).allowed()).isTrue();
    }

    @Test
    @DisplayName("each client and each kind of request has its own count")
    void separateKeys() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(new MovableClock());
        assertThat(limiter.tryAcquire("upload:1.2.3.4", 1).allowed()).isTrue();
        assertThat(limiter.tryAcquire("upload:1.2.3.4", 1).allowed()).isFalse();

        assertThat(limiter.tryAcquire("upload:5.6.7.8", 1).allowed()).isTrue();
        assertThat(limiter.tryAcquire("lookup:1.2.3.4", 1).allowed()).isTrue();
    }
}
