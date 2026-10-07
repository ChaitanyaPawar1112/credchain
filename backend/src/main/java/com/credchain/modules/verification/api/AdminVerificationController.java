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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Verification log", description = "Every public certificate check on the platform (SUPER_ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/v1/admin/verifications")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminVerificationController {

    private final VerificationLogService logService;

    @Operation(summary = "All public checks (newest first), with totals per result. "
            + "Optional filter: result (e.g. FAKE to see forged certificates being used)")
    @GetMapping
    public VerificationActivityResponse list(@RequestParam(required = false) VerificationStatus result,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return logService.forPlatform(result, page, size);
    }
}
