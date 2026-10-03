package com.credchain.modules.auth.application;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.common.security.SecurityProperties;
import com.credchain.common.util.HashUtils;
import com.credchain.modules.auth.domain.RefreshToken;
import com.credchain.modules.auth.infrastructure.RefreshTokenRepository;
import com.credchain.modules.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Issues, rotates and revokes refresh tokens. Only SHA-256 hashes are stored. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32; // 256 bits of randomness
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final SecurityProperties properties;
    private final Clock clock;


    /** Saved entity + the raw token (raw value exists only in memory, never in DB). */
    private record Created(RefreshToken entity, IssuedToken token) {
    }

    @Transactional
    public IssuedToken issue(User user, ClientInfo client) {
        return createAndSave(user, client, clock.instant()).token();
    }

    /**
     * Validates the presented token and swaps it for a new one.
     * noRollbackFor: when token reuse is detected we revoke all sessions AND throw;
     * the revocation must still be saved, so the exception must not roll it back.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public RotationResult rotate(String rawToken, ClientInfo client) {
        Instant now = clock.instant();

        RefreshToken current = repository.findByTokenHashWithUser(HashUtils.sha256Hex(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (current.isRevoked()) {
            UUID userId = current.getUser().getId();
            log.warn("Refresh token reuse detected for user {} - revoking all sessions", userId);
            repository.revokeAllActiveForUser(userId, now);
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (current.isExpired(now)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }


        User user = current.getUser();
        Created next = createAndSave(user, client, now);
        current.replaceWith(next.entity().getId(), now);

        return new RotationResult(user, next.token());
    }

    /** Logout from this device. Unknown or already-revoked tokens are ignored (idempotent). */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHashWithUser(HashUtils.sha256Hex(rawToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    /** Logout from all devices. */
    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllActiveForUser(userId, clock.instant());
    }

    // ---------- helpers ----------

    private Created createAndSave(User user, ClientInfo client, Instant now) {
        String raw = generateRawToken();
        Instant expiresAt = now.plus(properties.jwt().refreshTokenTtl());
        RefreshToken saved = repository.save(RefreshToken.create(
                user, HashUtils.sha256Hex(raw), expiresAt, client.ipAddress(), client.userAgent()));
        return new Created(saved, new IssuedToken(raw, expiresAt));
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];

        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}