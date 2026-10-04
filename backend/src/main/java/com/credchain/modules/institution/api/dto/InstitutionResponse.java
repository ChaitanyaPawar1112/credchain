package com.credchain.modules.institution.api.dto;

import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.institution.domain.InstitutionStatus;
import com.credchain.modules.institution.domain.InstitutionType;

import java.time.Instant;
import java.util.UUID;

public record InstitutionResponse(
        UUID id,
        String name,
        String code,
        String registrationNumber,
        InstitutionType type,
        String email,
        String phone,
        String website,
        String addressLine,
        String city,
        String state,
        String country,
        String postalCode,
        String contactPersonName,
        String contactPersonEmail,
        String walletAddress,
        InstitutionStatus status,
        String rejectionReason,
        Instant reviewedAt,
        Instant createdAt
) {

    public static InstitutionResponse from(Institution i) {
        return new InstitutionResponse(
                i.getId(), i.getName(), i.getCode(), i.getRegistrationNumber(), i.getType(),
                i.getEmail(), i.getPhone(), i.getWebsite(), i.getAddressLine(), i.getCity(), i.getState(),
                i.getCountry(), i.getPostalCode(), i.getContactPersonName(), i.getContactPersonEmail(),
                i.getWalletAddress(), i.getStatus(), i.getRejectionReason(), i.getReviewedAt(), i.getCreatedAt());
    }
}