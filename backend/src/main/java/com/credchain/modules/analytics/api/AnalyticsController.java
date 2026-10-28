package com.credchain.modules.analytics.api;

import com.credchain.modules.analytics.api.dto.AnalyticsResponse;
import com.credchain.modules.analytics.application.AnalyticsService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Analytics", description = "Dashboard numbers and charts. Super admin: the platform; college admin: their college")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService service;

    @Operation(summary = "Platform dashboard for the last N days (default 30)")
    @GetMapping("/api/v1/admin/analytics")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AnalyticsResponse platform(@RequestParam(defaultValue = "30") @Min(7) @Max(365) int days) {
        return service.forPlatform(days);
    }

    @Operation(summary = "My college's dashboard for the last N days (default 30)")
    @GetMapping("/api/v1/institution/analytics")
    @PreAuthorize("hasRole('INSTITUTION_ADMIN')")
    public AnalyticsResponse institution(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(defaultValue = "30") @Min(7) @Max(365) int days) {
        return service.forInstitution(UUID.fromString(jwt.getSubject()), days);
    }
}
