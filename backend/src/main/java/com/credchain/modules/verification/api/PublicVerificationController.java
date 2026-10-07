package com.credchain.modules.verification.api;

import com.credchain.modules.verification.api.dto.VerificationResponse;
import com.credchain.modules.verification.application.PublicVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public verification", description = "Anyone can check a certificate (no login): VALID / REVOKED / EXPIRED / NOT_FOUND")
@RestController
@RequestMapping("/api/v1/public/verify")
@RequiredArgsConstructor
public class PublicVerificationController {

    private final PublicVerificationService verificationService;

    @Operation(summary = "Verify a certificate by its hash (the QR code on the PDF opens this). Asks the blockchain live.")
    @GetMapping("/{certHash}")
    public VerificationResponse verifyByHash(@PathVariable String certHash) {
        return verificationService.verifyByHash(certHash);
    }
}
