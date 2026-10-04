package com.credchain.modules.auth.api.dto;

import com.credchain.modules.user.domain.Role;
import com.credchain.modules.user.domain.User;

import java.util.UUID;

public record UserSummary(
        UUID id,
        String email,
        String fullName,
        Role role,
        UUID institutionId,          // set only for INSTITUTION_ADMIN
        boolean mustChangePassword   // frontend shows "set new password" screen when true
) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getInstitutionId(), user.isMustChangePassword());
    }
}