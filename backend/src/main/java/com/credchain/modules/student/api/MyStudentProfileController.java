package com.credchain.modules.student.api;

import com.credchain.modules.student.api.dto.LinkStudentRequest;
import com.credchain.modules.student.api.dto.MyStudentProfileResponse;
import com.credchain.modules.student.application.StudentLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/student-profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Student - My Profile", description = "A student's own official record")
public class MyStudentProfileController {

    private final StudentLinkService linkService;

    @GetMapping
    @Operation(summary = "Get my official student record (after linking)")
    public MyStudentProfileResponse myProfile(@AuthenticationPrincipal Jwt jwt) {
        return linkService.myProfile(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/link")
    @Operation(summary = "Link my account to my institution record using the claim code")
    public MyStudentProfileResponse link(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody LinkStudentRequest request) {
        return linkService.link(UUID.fromString(jwt.getSubject()), request);
    }
}