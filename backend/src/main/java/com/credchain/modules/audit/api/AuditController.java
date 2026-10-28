package com.credchain.modules.audit.api;

import com.credchain.common.api.PageResponse;
import com.credchain.modules.audit.api.dto.AuditEventResponse;
import com.credchain.modules.audit.application.AuditService;
import com.credchain.modules.audit.domain.AuditCategory;
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

@Tag(name = "Audit log", description = "Who did what and when. Super admin: everything; college admin: their own college")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @Operation(summary = "Every audit entry, newest first. Optional filter: category")
    @GetMapping("/api/v1/admin/audit")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PageResponse<AuditEventResponse> platform(@RequestParam(required = false) AuditCategory category,
                                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return auditService.forPlatform(category, page, size);
    }

    @Operation(summary = "My college's audit entries, newest first. Optional filter: category")
    @GetMapping("/api/v1/institution/audit")
    @PreAuthorize("hasRole('INSTITUTION_ADMIN')")
    public PageResponse<AuditEventResponse> institution(@AuthenticationPrincipal Jwt jwt,
                                                        @RequestParam(required = false) AuditCategory category,
                                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return auditService.forInstitution(UUID.fromString(jwt.getSubject()), category, page, size);
    }
}
