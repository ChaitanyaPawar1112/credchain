package com.credchain.modules.fraud.api;

import com.credchain.modules.fraud.api.dto.InstitutionTrustResponse;
import com.credchain.modules.fraud.application.InstitutionTrustService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin - Institution trust", description = "AI trust score per college: unusual activity (Isolation Forest), revocations and forged copies (SUPER_ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/institution-trust")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminInstitutionTrustController {

    private final InstitutionTrustService trustService;

    @Operation(summary = "Latest trust score of every college, lowest first, with the reasons")
    @GetMapping
    public InstitutionTrustResponse list() {
        return trustService.list();
    }

    @Operation(summary = "Run the trust analysis now (it also runs every night). 503 if the fraud-detection service is down")
    @PostMapping("/recompute")
    public InstitutionTrustResponse recompute(@AuthenticationPrincipal Jwt jwt) {
        return trustService.recompute(UUID.fromString(jwt.getSubject()));
    }
}
