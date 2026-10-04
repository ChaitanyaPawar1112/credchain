package com.credchain.modules.institution.domain;

/** Details an institution submits when applying. */
public record InstitutionProfile(
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
        String walletAddress
) {
}