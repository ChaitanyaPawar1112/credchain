package com.credchain.modules.institution.api.dto;

import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.institution.domain.InstitutionStatus;

import java.time.Instant;
import java.util.UUID;

/** Public view of an application: status only, no contact details. */
public record ApplicationStatusResponse(
        UUID id,
        String name,
        String code,
        InstitutionStatus status,
        String rejectionReason,
        Instant submittedAt,
        Instant reviewedAt
) {

    public static ApplicationStatusResponse from(Institution i) {
        return new ApplicationStatusResponse(i.getId(), i.getName(), i.getCode(), i.getStatus(),
                i.getRejectionReason(), i.getCreatedAt(), i.getReviewedAt());
    }
}