package com.credchain.modules.institution.api;

import com.credchain.common.api.PageResponse;
import com.credchain.modules.institution.api.dto.InstitutionApprovalResponse;
import com.credchain.modules.institution.api.dto.InstitutionResponse;
import com.credchain.modules.institution.api.dto.RejectInstitutionRequest;
import com.credchain.modules.institution.application.InstitutionReviewService;
import com.credchain.modules.institution.domain.InstitutionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/institutions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")

@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Institutions", description = "Review institution applications (SUPER_ADMIN only)")
public class AdminInstitutionController {

    private final InstitutionReviewService reviewService;

    @GetMapping
    @Operation(summary = "List institutions, optionally filtered by status")
    public PageResponse<InstitutionResponse> list(
            @RequestParam(required = false) InstitutionStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reviewService.list(status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one institution's full details")
    public InstitutionResponse get(@PathVariable UUID id) {
        return reviewService.get(id);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a PENDING institution and create its admin account")
    public InstitutionApprovalResponse approve(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return reviewService.approve(id, reviewerId(jwt));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a PENDING institution with a reason")
    public InstitutionResponse reject(@PathVariable UUID id,
                                      @Valid @RequestBody RejectInstitutionRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {

        return reviewService.reject(id, reviewerId(jwt), request.reason());
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend an APPROVED institution (logs out its admins)")
    public InstitutionResponse suspend(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return reviewService.suspend(id, reviewerId(jwt));
    }

    @PostMapping("/{id}/reinstate")
    @Operation(summary = "Reinstate a SUSPENDED institution")
    public InstitutionResponse reinstate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return reviewService.reinstate(id, reviewerId(jwt));
    }

    private static UUID reviewerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}