package com.credchain.modules.student.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateStudentRequest(

        @Schema(example = "2022CS001")
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9/_-]{2,50}$",
                message = "must be 2-50 letters, digits, '/', '_' or '-'")
        String enrollmentNo,

        @Schema(example = "Chaitanya Pawar")
        @NotBlank @Size(max = 150)
        String fullName,

        @Schema(example = "chaitanya@example.com")
        @Email @Size(max = 255)
        String email,

        @Schema(example = "2004-05-12")
        @Past
        LocalDate dateOfBirth,


        @Schema(example = "B.Tech Computer Engineering")
        @NotBlank @Size(max = 150)
        String program,

        @Schema(example = "Computer Engineering")
        @Size(max = 150)
        String department,

        @Schema(example = "2022")
        @NotNull @Min(1950) @Max(2100)
        Integer admissionYear,

        @Schema(example = "2026")
        @Min(1950) @Max(2100)
        Integer graduationYear
) {
}