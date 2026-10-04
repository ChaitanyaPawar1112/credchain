package com.credchain.modules.institution.application;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.institution.infrastructure.InstitutionRepository;
import com.credchain.modules.user.domain.Role;
import com.credchain.modules.user.domain.User;
import com.credchain.modules.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single checkpoint for every institution-admin action.
 * Resolves the caller's institution from the DATABASE (never from request input),
 * which is what guarantees tenant isolation.
 */
@Component
@RequiredArgsConstructor
public class InstitutionAccessGuard {

    private final UserRepository userRepository;
    private final InstitutionRepository institutionRepository;

    @Transactional(readOnly = true)
    public Institution requireActiveInstitution(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));


        if (user.getRole() != Role.INSTITUTION_ADMIN || user.getInstitutionId() == null) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (user.isMustChangePassword()) {
            throw new BusinessException(ErrorCode.PASSWORD_CHANGE_REQUIRED);
        }

        Institution institution = institutionRepository.findById(user.getInstitutionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
        if (!institution.isApproved()) {
            throw new BusinessException(ErrorCode.INSTITUTION_NOT_APPROVED,
                    "Institution is " + institution.getStatus() + "; contact the CredChain administrator");
        }
        return institution;
    }
}