package com.credchain.modules.certificate.api;

import com.credchain.common.api.PageResponse;
import com.credchain.modules.certificate.api.dto.MyCertificateResponse;
import com.credchain.modules.certificate.application.CertificatePdfService;
import com.credchain.modules.certificate.application.MyCertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Student - My Certificates", description = "A student's own certificates (only those on the blockchain)")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/v1/me/certificates")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class MyCertificateController {

    private final MyCertificateService myCertificateService;

    @Operation(summary = "My certificates (newest award first)")
    @GetMapping
    public PageResponse<MyCertificateResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestParam(defaultValue = "0") @Min(0) int page,
                                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return myCertificateService.list(userId(jwt), page, size);
    }

    @Operation(summary = "One of my certificates")
    @GetMapping("/{certificateId}")
    public MyCertificateResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID certificateId) {
        return myCertificateService.get(userId(jwt), certificateId);
    }

    @Operation(summary = "Download my certificate PDF (409 until it has been created after anchoring)")
    @GetMapping("/{certificateId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID certificateId) {
        CertificatePdfService.PdfFile file = myCertificateService.downloadPdf(userId(jwt), certificateId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}