package com.credchain.modules.certificate.api;

import com.credchain.modules.certificate.api.dto.ReconciliationReportResponse;
import com.credchain.modules.certificate.application.ChainReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Reconciliation", description = "Blockchain vs database check (SUPER_ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/v1/admin/reconciliation")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminReconciliationController {

    private final ChainReconciliationService reconciliationService;

    @Operation(summary = "Summary of the blockchain check and the certificates that don't match (newest first)")
    @GetMapping
    public ReconciliationReportResponse report(@RequestParam(defaultValue = "0") @Min(0) int page,
                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reconciliationService.report(page, size);
    }
}