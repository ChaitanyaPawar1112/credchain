package com.credchain.modules.certificate.api;

import com.credchain.common.api.PageResponse;
import com.credchain.modules.certificate.api.dto.CertificateResponse;
import com.credchain.modules.certificate.api.dto.RevokeCertificateRequest;
import com.credchain.modules.certificate.application.CertificatePdfService;
import com.credchain.modules.certificate.application.CertificateRevocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Certificates", description = "Institution admin: view issued certificates and revoke them on-chain")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/v1/institution/certificates")
@PreAuthorize("hasRole('INSTITUTION_ADMIN')")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateRevocationService revocationService;
    private final CertificatePdfService pdfService;

    @Operation(summary = "My institution's certificates (newest first)")
    @GetMapping
    public PageResponse<CertificateResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return revocationService.list(userId(jwt), page, size);
    }

    @Operation(summary = "One certificate")
    @GetMapping("/{certificateId}")
    public CertificateResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID certificateId) {
        return revocationService.get(userId(jwt), certificateId);
    }

    @Operation(summary = "Download the certificate PDF (409 until it has been created after anchoring)")
    @GetMapping("/{certificateId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID certificateId) {
        CertificatePdfService.PdfFile file = pdfService.downloadForInstitution(userId(jwt), certificateId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }

    @Operation(summary = "Request revocation of an ISSUED certificate (executed on-chain in the background)")
    @PostMapping("/{certificateId}/revoke")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CertificateResponse revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID certificateId,
                                      @Valid @RequestBody RevokeCertificateRequest request) {
        return revocationService.requestRevocation(userId(jwt), certificateId, request);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}