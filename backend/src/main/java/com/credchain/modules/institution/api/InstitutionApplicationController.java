package com.credchain.modules.institution.api;

import com.credchain.modules.institution.api.dto.ApplicationStatusResponse;
import com.credchain.modules.institution.api.dto.InstitutionApplicationRequest;
import com.credchain.modules.institution.api.dto.InstitutionResponse;
import com.credchain.modules.institution.application.InstitutionApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/institutions/applications")
@RequiredArgsConstructor
@Tag(name = "Institution applications", description = "Public: apply to join CredChain and check status")
public class InstitutionApplicationController {

    private final InstitutionApplicationService applicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit an institution application (status starts as PENDING)")

    public InstitutionResponse apply(@Valid @RequestBody InstitutionApplicationRequest request) {
        return applicationService.apply(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Check the status of an application")
    public ApplicationStatusResponse status(@PathVariable UUID id) {
        return applicationService.getStatus(id);
    }
}