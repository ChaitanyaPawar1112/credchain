package com.credchain.modules.institution.api;

import com.credchain.modules.institution.api.dto.InstitutionResponse;
import com.credchain.modules.institution.application.InstitutionAccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/institution")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTITUTION_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Institution - Profile", description = "The logged-in admin's own institution")
public class MyInstitutionController {

    private final InstitutionAccessGuard accessGuard;

    @GetMapping("/profile")
    @Operation(summary = "Get my institution's details")
    public InstitutionResponse profile(@AuthenticationPrincipal Jwt jwt) {
        return InstitutionResponse.from(accessGuard.requireActiveInstitution(UUID.fromString(jwt.getSubject())));
    }

}