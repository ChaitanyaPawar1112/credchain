package com.credchain.modules.wallet.api;

import com.credchain.modules.wallet.api.dto.InstitutionWalletResponse;
import com.credchain.modules.wallet.application.InstitutionWalletQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Issuer wallets", description = "Custodial blockchain wallets that issue credentials on-chain")
@SecurityRequirement(name = "bearerAuth")   // Swagger: send the JWT from "Authorize" with these requests
@RestController
@RequiredArgsConstructor
public class InstitutionWalletController {

    private final InstitutionWalletQueryService walletQueryService;

    @Operation(summary = "Issuer wallet of any institution (SUPER_ADMIN)")
    @GetMapping("/api/v1/admin/institutions/{institutionId}/wallet")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public InstitutionWalletResponse institutionWallet(@PathVariable UUID institutionId) {
        return walletQueryService.forInstitution(institutionId);
    }


    @Operation(summary = "My institution's issuer wallet (INSTITUTION_ADMIN)")
    @GetMapping("/api/v1/institution/wallet")
    @PreAuthorize("hasRole('INSTITUTION_ADMIN')")
    public InstitutionWalletResponse myWallet(@AuthenticationPrincipal Jwt jwt) {
        return walletQueryService.forInstitutionAdmin(UUID.fromString(jwt.getSubject()));
    }
}