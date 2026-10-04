package com.credchain.modules.student.api;

import com.credchain.modules.student.api.dto.ClaimCodeResponse;
import com.credchain.modules.student.application.StudentLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/institution/students")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTITUTION_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Institution - Students", description = "Manage your institution's student records")
public class StudentClaimCodeController {

    private final StudentLinkService linkService;

    @PostMapping("/{id}/claim-code")
    @Operation(summary = "Generate a one-time claim code so the student can link their own account")
    public ClaimCodeResponse issueClaimCode(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return linkService.issueClaimCode(UUID.fromString(jwt.getSubject()), id);
    }
}