package com.credchain.modules.verification.api;

import com.credchain.modules.verification.api.dto.VerificationActivityResponse;
import com.credchain.modules.verification.application.VerificationLogService;
import com.credchain.modules.verification.domain.VerificationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Verification activity", description = "Institution admin: who checked my institution's certificates")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/v1/institution/verifications")
@PreAuthorize("hasRole('INSTITUTION_ADMIN')")
@RequiredArgsConstructor
public class InstitutionVerificationController {

    private final VerificationLogService logService;

    @Operation(summary = "Public checks of my institution's certificates (newest first), with totals per result. "
            + "Optional filter: result (e.g. FAKE for edited copies of our certificates)")
    @GetMapping
    public VerificationActivityResponse list(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(required = false) VerificationStatus result,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return logService.forInstitution(UUID.fromString(jwt.getSubject()), result, page, size);
    }
}
