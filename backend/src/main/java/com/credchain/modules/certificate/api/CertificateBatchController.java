package com.credchain.modules.certificate.api;

import com.credchain.common.api.PageResponse;
import com.credchain.modules.certificate.api.dto.AddCertificateRequest;
import com.credchain.modules.certificate.api.dto.BatchDetailResponse;
import com.credchain.modules.certificate.api.dto.BatchResponse;
import com.credchain.modules.certificate.api.dto.BulkAddCertificatesRequest;
import com.credchain.modules.certificate.api.dto.CertificateResponse;
import com.credchain.modules.certificate.api.dto.CreateBatchRequest;
import com.credchain.modules.certificate.application.CertificateIssuanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


import java.util.List;
import java.util.UUID;

@Tag(name = "Certificate issuance", description = "Institution admin: prepare certificate batches and issue them on-chain")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/v1/institution/certificate-batches")
@PreAuthorize("hasRole('INSTITUTION_ADMIN')")
@RequiredArgsConstructor
public class CertificateBatchController {

    private final CertificateIssuanceService issuanceService;

    @Operation(summary = "Create a draft batch")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BatchResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBatchRequest request) {
        return issuanceService.createBatch(userId(jwt), request);
    }

    @Operation(summary = "List my institution's batches (newest first)")
    @GetMapping
    public PageResponse<BatchResponse> list(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return issuanceService.listBatches(userId(jwt), page, size);
    }

    @Operation(summary = "Batch details with its certificates")
    @GetMapping("/{batchId}")
    public BatchDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID batchId) {

        return issuanceService.getBatch(userId(jwt), batchId);
    }

    @Operation(summary = "Delete a draft batch and its certificates")
    @DeleteMapping("/{batchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID batchId) {
        issuanceService.deleteDraftBatch(userId(jwt), batchId);
    }

    @Operation(summary = "Add one certificate to a draft batch")
    @PostMapping("/{batchId}/certificates")
    @ResponseStatus(HttpStatus.CREATED)
    public CertificateResponse addCertificate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID batchId,
                                              @Valid @RequestBody AddCertificateRequest request) {
        return issuanceService.addCertificate(userId(jwt), batchId, request);
    }

    @Operation(summary = "Add many certificates (same degree) to a draft batch, all or nothing")
    @PostMapping("/{batchId}/certificates/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CertificateResponse> addCertificates(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID batchId,
                                                     @Valid @RequestBody BulkAddCertificatesRequest request) {
        return issuanceService.addCertificates(userId(jwt), batchId, request);
    }

    @Operation(summary = "Remove a certificate from a draft batch")
    @DeleteMapping("/{batchId}/certificates/{certificateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCertificate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID batchId,
                                  @PathVariable UUID certificateId) {
        issuanceService.removeCertificate(userId(jwt), batchId, certificateId);

    }

    @Operation(summary = "Issue the batch: freeze it, build the Merkle tree and queue it for the blockchain")
    @PostMapping("/{batchId}/issue")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BatchResponse issue(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID batchId) {
        return issuanceService.issue(userId(jwt), batchId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}