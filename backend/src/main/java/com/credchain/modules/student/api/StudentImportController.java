package com.credchain.modules.student.api;

import com.credchain.modules.student.api.dto.StudentImportResult;
import com.credchain.modules.student.application.StudentCsvImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/institution/students/import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INSTITUTION_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Institution - Students", description = "Manage your institution's student records")
public class StudentImportController {

    private final StudentCsvImportService importService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import students from a CSV file (use dryRun=true to preview without saving)")
    public StudentImportResult importCsv(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam("file") MultipartFile file,
                                         @RequestParam(defaultValue = "false") boolean dryRun) {
        return importService.importCsv(UUID.fromString(jwt.getSubject()), file, dryRun);
    }

    @GetMapping(value = "/template", produces = "text/csv")
    @Operation(summary = "Download an empty CSV template with the correct columns")
    public ResponseEntity<String> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"students-template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(importService.template());
    }
}