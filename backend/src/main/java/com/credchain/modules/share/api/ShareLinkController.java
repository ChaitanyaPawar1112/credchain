package com.credchain.modules.share.api;

import com.credchain.common.api.PageResponse;
import com.credchain.modules.share.api.dto.CreateShareLinkRequest;
import com.credchain.modules.share.api.dto.ShareLinkResponse;
import com.credchain.modules.share.application.ShareLinkService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Share links (student)", description = "Named links to one certificate that expire and can be turned off, with view counts")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequestMapping("/api/v1/me/share-links")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class ShareLinkController {

    private final ShareLinkService service;

    @Operation(summary = "Create a link to one of my issued certificates (1 to 365 days)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShareLinkResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateShareLinkRequest request) {
        return service.create(userId(jwt), request);
    }

    @Operation(summary = "My links, newest first, with status and view counts")
    @GetMapping
    public PageResponse<ShareLinkResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(userId(jwt), page, size);
    }

    @Operation(summary = "Turn a link off for good")
    @PostMapping("/{id}/revoke")
    public ShareLinkResponse revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.revoke(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
