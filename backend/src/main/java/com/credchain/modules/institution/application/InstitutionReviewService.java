package com.credchain.modules.institution.application;

import com.credchain.common.api.PageResponse;
import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.common.util.PasswordGenerator;
import com.credchain.modules.auth.application.RefreshTokenService;
import com.credchain.modules.institution.api.dto.InstitutionApprovalResponse;
import com.credchain.modules.institution.api.dto.InstitutionResponse;
import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.institution.domain.InstitutionStatus;
import com.credchain.modules.institution.domain.InstitutionStatusChangedEvent;
import com.credchain.modules.institution.infrastructure.InstitutionRepository;
import com.credchain.modules.user.domain.User;
import com.credchain.modules.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** SUPER_ADMIN actions on institutions. */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstitutionReviewService {

    private static final int TEMP_PASSWORD_LENGTH = 16;

    private final InstitutionRepository institutionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // ---------- Queries ----------

    @Transactional(readOnly = true)
    public PageResponse<InstitutionResponse> list(InstitutionStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Institution> result = (status == null)
                ? institutionRepository.findAll(pageRequest)
                : institutionRepository.findAllByStatus(status, pageRequest);
        return PageResponse.from(result.map(InstitutionResponse::from));
    }

    @Transactional(readOnly = true)
    public InstitutionResponse get(UUID id) {
        return InstitutionResponse.from(load(id));
    }

    // ---------- Commands ----------

    /** Approve + create the institution admin account (+ issuer wallet via event), atomically. */
    @Transactional
    public InstitutionApprovalResponse approve(UUID institutionId, UUID reviewerId) {
        Institution institution = load(institutionId);
        Instant now = clock.instant();

        institution.approve(reviewerId, now);   // throws 409 if not PENDING

        String adminEmail = institution.getContactPersonEmail();
        if (userRepository.existsByEmail(adminEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS,
                    "Cannot create institution admin: " + adminEmail + " is already used by another account");
        }

        String temporaryPassword = PasswordGenerator.generate(TEMP_PASSWORD_LENGTH);
        User admin = User.createInstitutionAdmin(
                adminEmail,
                passwordEncoder.encode(temporaryPassword),
                institution.getContactPersonName(),
                institution.getId());

        try {
            userRepository.saveAndFlush(admin);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        events.publishEvent(new InstitutionStatusChangedEvent(institution.getId(), InstitutionStatus.APPROVED));

        log.info("Institution {} approved by {}; admin account {} created",
                institution.getCode(), reviewerId, admin.getId());   // never log the password

        return new InstitutionApprovalResponse(
                InstitutionResponse.from(institution),
                new InstitutionApprovalResponse.AdminAccount(
                        admin.getId(),
                        admin.getEmail(),
                        temporaryPassword,
                        "Share this temporary password securely. It is shown only once and must be changed at first login."));
    }

    @Transactional
    public InstitutionResponse reject(UUID institutionId, UUID reviewerId, String reason) {
        Institution institution = load(institutionId);
        institution.reject(reviewerId, reason, clock.instant());   // throws 409 if not PENDING
        events.publishEvent(new InstitutionStatusChangedEvent(institution.getId(), InstitutionStatus.REJECTED));
        log.info("Institution {} rejected by {}", institution.getCode(), reviewerId);
        return InstitutionResponse.from(institution);
    }

    /** Suspend, immediately log out every admin of that institution, and revoke on-chain issuing (via event). */
    @Transactional
    public InstitutionResponse suspend(UUID institutionId, UUID reviewerId) {
        Institution institution = load(institutionId);
        institution.suspend(reviewerId, clock.instant());

        userRepository.findAllByInstitutionId(institutionId)
                .forEach(user -> refreshTokenService.revokeAll(user.getId()));

        events.publishEvent(new InstitutionStatusChangedEvent(institution.getId(), InstitutionStatus.SUSPENDED));

        log.warn("Institution {} suspended by {}", institution.getCode(), reviewerId);
        return InstitutionResponse.from(institution);
    }

    @Transactional
    public InstitutionResponse reinstate(UUID institutionId, UUID reviewerId) {
        Institution institution = load(institutionId);
        institution.reinstate(reviewerId, clock.instant());
        events.publishEvent(new InstitutionStatusChangedEvent(institution.getId(), InstitutionStatus.APPROVED));
        log.info("Institution {} reinstated by {}", institution.getCode(), reviewerId);
        return InstitutionResponse.from(institution);
    }

    private Institution load(UUID id) {
        return institutionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Institution not found"));
    }
}