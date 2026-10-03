package com.credchain.modules.auth.application;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.common.security.SecurityProperties;
import com.credchain.modules.auth.api.dto.AuthResponse;
import com.credchain.modules.auth.api.dto.ChangePasswordRequest;
import com.credchain.modules.auth.api.dto.LoginRequest;
import com.credchain.modules.auth.api.dto.RegisterRequest;
import com.credchain.modules.auth.api.dto.UserSummary;
import com.credchain.modules.user.domain.Role;
import com.credchain.modules.user.domain.User;
import com.credchain.modules.user.infrastructure.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class AuthService {

    private static final Set<Role> SELF_REGISTER_ROLES = EnumSet.of(Role.STUDENT, Role.VERIFIER);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final SecurityProperties properties;
    private final Clock clock;

    /** Used to spend the same time on "email not found" as on "wrong password". */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AccessTokenService accessTokenService,
                       RefreshTokenService refreshTokenService,
                       SecurityProperties properties,
                       Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    // ---------- Register ----------

    @Transactional
    public AuthResponse register(RegisterRequest request, ClientInfo client) {
        if (!SELF_REGISTER_ROLES.contains(request.role())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Self-registration is only allowed for STUDENT or VERIFIER");
        }

        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.create(
                email,
                passwordEncoder.encode(request.password()),
                request.fullName(),
                blankToNull(request.phone()),
                request.role());

        try {
            userRepository.saveAndFlush(user);   // flush now so a duplicate is caught HERE
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        log.info("New {} registered: {}", user.getRole(), user.getId());
        return issueTokens(user, client);
    }

    // ---------- Login ----------

    /** noRollbackFor: the failed-attempt counter must be saved even though we throw. */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResponse login(LoginRequest request, ClientInfo client) {
        String email = User.normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash); // equal timing
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        Instant now = clock.instant();
        if (user.isLocked(now)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.recordFailedLogin(now,
                    properties.login().maxFailedAttempts(),
                    properties.login().lockDuration());
            log.debug("Failed login for user {}", user.getId());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        user.recordSuccessfulLogin(now);
        return issueTokens(user, client);
    }

    // ---------- Refresh ----------

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResponse refresh(String refreshToken, ClientInfo client) {
        RotationResult rotation = refreshTokenService.rotate(refreshToken, client);
        User user = rotation.user();

        if (!user.isActive()) {
            refreshTokenService.revokeAll(user.getId());
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        }

        IssuedToken access = accessTokenService.issue(user);
        return AuthResponse.of(user, access, rotation.refreshToken());
    }

    // ---------- Logout ----------

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    public void logoutAll(UUID userId) {
        refreshTokenService.revokeAll(userId);
    }

    // ---------- Change password ----------

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "New password must be different from the current password");
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAll(user.getId());   // log out every device
        log.info("Password changed for user {} - all sessions revoked", userId);
    }

    // ---------- Current user ----------

    @Transactional(readOnly = true)
    public UserSummary me(UUID userId) {
        return userRepository.findById(userId)
                .map(UserSummary::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
    }

    // ---------- helpers ----------

    private AuthResponse issueTokens(User user, ClientInfo client) {
        IssuedToken access = accessTokenService.issue(user);
        IssuedToken refresh = refreshTokenService.issue(user, client);
        return AuthResponse.of(user, access, refresh);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}