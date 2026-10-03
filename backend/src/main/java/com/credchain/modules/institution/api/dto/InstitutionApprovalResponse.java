package com.credchain.modules.institution.api.dto;

import java.util.UUID;

/** Returned once on approval. The temporary password is never shown again. */
public record InstitutionApprovalResponse(
        InstitutionResponse institution,
        AdminAccount adminAccount
) {

    public record AdminAccount(
            UUID userId,
            String email,
            String temporaryPassword,
            String note
    ) {
    }
}