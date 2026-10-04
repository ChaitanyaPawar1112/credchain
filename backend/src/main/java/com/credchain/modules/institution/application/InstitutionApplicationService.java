package com.credchain.modules.institution.application;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.institution.api.dto.ApplicationStatusResponse;
import com.credchain.modules.institution.api.dto.InstitutionApplicationRequest;
import com.credchain.modules.institution.api.dto.InstitutionResponse;
import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.institution.infrastructure.InstitutionRepository;
import com.credchain.modules.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstitutionApplicationService {

    private final InstitutionRepository institutionRepository;
    private final UserRepository userRepository;

    @Transactional
    public InstitutionResponse apply(InstitutionApplicationRequest request) {
        Institution institution = Institution.apply(request.toProfile());

        // Friendly checks first (clear error messages)...
        if (institutionRepository.existsByCode(institution.getCode())) {
            throw new BusinessException(ErrorCode.INSTITUTION_ALREADY_EXISTS,
                    "Institution code " + institution.getCode() + " is already registered");
        }
        if (institutionRepository.existsByRegistrationNumber(institution.getRegistrationNumber())) {
            throw new BusinessException(ErrorCode.INSTITUTION_ALREADY_EXISTS,
                    "Registration number is already registered");
        }
        if (institution.getWalletAddress() != null
                && institutionRepository.existsByWalletAddress(institution.getWalletAddress())) {
            throw new BusinessException(ErrorCode.INSTITUTION_ALREADY_EXISTS,
                    "Wallet address is already registered to another institution");
        }
        if (userRepository.existsByEmail(institution.getContactPersonEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS,
                    "Contact person email is already used by an existing account");
        }

        // ...and the database unique constraints as the final guard against races.
        try {
            institutionRepository.saveAndFlush(institution);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.INSTITUTION_ALREADY_EXISTS);
        }

        log.info("New institution application {} ({})", institution.getId(), institution.getCode());
        return InstitutionResponse.from(institution);
    }

    @Transactional(readOnly = true)
    public ApplicationStatusResponse getStatus(UUID applicationId) {
        return institutionRepository.findById(applicationId)
                .map(ApplicationStatusResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Application not found"));
    }
}