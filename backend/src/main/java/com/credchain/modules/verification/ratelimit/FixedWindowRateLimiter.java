package com.credchain.modules.verification.ratelimit;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts requests per key (e.g. "upload:203.0.113.7") in one-minute windows kept in memory.
 * Simple and enough for one server; with several servers each one counts on its own.
 */
public class FixedWindowRateLimiter {

    static final long WINDOW_MILLIS = 60_000;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(long start, int count) {
    }

    /** allowed = false means the caller should wait retryAfterSeconds. */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public FixedWindowRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public Decision tryAcquire(String key, int limitPerMinute) {
        long now = clock.millis();
        long windowStart = now - (now % WINDOW_MILLIS);
        Window window = windows.merge(key, new Window(windowStart, 1),
                (old, fresh) -> old.start() == windowStart ? new Window(old.start(), old.count() + 1) : fresh);
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> w.start() < windowStart);
        }
        if (window.count() <= limitPerMinute) {
            return new Decision(true, 0);
        }
        long retryAfter = Math.max(1, (windowStart + WINDOW_MILLIS - now + 999) / 1000);
        return new Decision(false, retryAfter);
    }

    int trackedKeys() {
        return windows.size();
    }
}
