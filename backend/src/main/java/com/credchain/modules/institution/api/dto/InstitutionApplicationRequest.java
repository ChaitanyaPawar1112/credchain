package com.credchain.modules.institution.api.dto;

import com.credchain.modules.institution.domain.InstitutionProfile;
import com.credchain.modules.institution.domain.InstitutionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InstitutionApplicationRequest(

        @Schema(example = "Sahyadri Institute of Technology")
        @NotBlank @Size(max = 200)
        String name,

        @Schema(example = "SIT-AUR", description = "Short unique code: 3-20 letters, digits or hyphens")
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{3,20}$", message = "must be 3-20 letters, digits or hyphens")
        String code,

        @Schema(example = "AISHE-C-45678", description = "Government registration / AISHE code")
        @NotBlank @Size(max = 50)
        String registrationNumber,

        @Schema(example = "COLLEGE")
        @NotNull
        InstitutionType type,

        @Schema(example = "office@sit.edu.in")
        @NotBlank @Email @Size(max = 255)
        String email,


        @Schema(example = "+912402345678")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "must be 10-15 digits, optional leading +")
        String phone,

        @Schema(example = "https://sit.edu.in")
        @Size(max = 255) @Pattern(regexp = "^https?://\\S+$", message = "must start with http:// or https://")
        String website,

        @Schema(example = "Plot 12, Jalna Road")
        @Size(max = 255)
        String addressLine,

        @Schema(example = "Chhatrapati Sambhajinagar")
        @NotBlank @Size(max = 100)
        String city,

        @Schema(example = "Maharashtra")
        @NotBlank @Size(max = 100)
        String state,

        @Schema(example = "IN", description = "ISO 3166 two-letter code; defaults to IN")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a 2-letter country code")
        String country,

        @Schema(example = "431001")
        @Pattern(regexp = "^[A-Za-z0-9 -]{3,12}$", message = "is not a valid postal code")
        String postalCode,

        @Schema(example = "Dr. Anil Deshmukh")
        @NotBlank @Size(max = 150)
        String contactPersonName,


        @Schema(example = "registrar@sit.edu.in", description = "Becomes the institution admin's login email")
        @NotBlank @Email @Size(max = 255)
        String contactPersonEmail,

        @Schema(example = "0x71C7656EC7ab88b098defB751B7401B5f6d8976F", description = "Optional; can be added later")
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "must be an Ethereum address: 0x followed by 40 hex characters")
        String walletAddress
) {

    public InstitutionProfile toProfile() {
        return new InstitutionProfile(name, code, registrationNumber, type, email, phone, website,
                addressLine, city, state, country, postalCode, contactPersonName, contactPersonEmail, walletAddress);
    }
}