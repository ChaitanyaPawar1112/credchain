package com.credchain.modules.student.api;

import com.credchain.common.api.PageResponse;
import com.credchain.modules.student.api.dto.CreateStudentRequest;
import com.credchain.modules.student.api.dto.StudentResponse;
import com.credchain.modules.student.application.StudentService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/institution/students")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTITUTION_ADMIN')")

@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Institution - Students", description = "Manage your institution's student records")
public class InstitutionStudentController {

    private final StudentService studentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a student record")
    public StudentResponse create(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody CreateStudentRequest request) {
        return studentService.create(userId(jwt), request);
    }

    @GetMapping
    @Operation(summary = "List students; optional q searches name or enrollment number")
    public PageResponse<StudentResponse> list(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return studentService.list(userId(jwt), q, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one student record")
    public StudentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return studentService.get(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

}