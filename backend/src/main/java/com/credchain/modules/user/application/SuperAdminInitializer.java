package com.credchain.modules.user.application;

import com.credchain.modules.user.domain.Role;
import com.credchain.modules.user.domain.User;
import com.credchain.modules.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first SUPER_ADMIN on startup if none exists.
 * Idempotent: safe to run on every startup; it only ever creates one admin.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminInitializer implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final String DEFAULT_FULL_NAME = "System Administrator";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        BootstrapProperties.SuperAdmin config = properties.superAdmin();

        if (config == null || !config.isConfigured()) {
            log.info("Super admin bootstrap not configured - skipping");
            return;
        }
        if (userRepository.existsByRole(Role.SUPER_ADMIN)) {
            log.info("Super admin already exists - skipping bootstrap");
            return;
        }

        // Fail fast: refuse to start with a weak admin password
        if (config.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "SUPER_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        String email = User.normalizeEmail(config.email());
        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException(
                    "Cannot create super admin: " + email + " is already used by another account");
        }

        String fullName = (config.fullName() == null || config.fullName().isBlank())
                ? DEFAULT_FULL_NAME
                : config.fullName();

        User admin = User.create(email, passwordEncoder.encode(config.password()), fullName, null, Role.SUPER_ADMIN);
        admin.markEmailVerified();
        userRepository.save(admin);

        log.info("Super admin account created: {}", email);

    }
}