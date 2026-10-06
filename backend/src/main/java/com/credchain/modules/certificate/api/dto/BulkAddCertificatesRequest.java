package com.credchain.modules.certificate.api.dto;

import com.credchain.modules.certificate.domain.CertificateType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Same degree for many students (e.g. a convocation); grade and CGPA per student. */
public record BulkAddCertificatesRequest(
        @NotNull CertificateType type,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 150) String program,
        @NotNull @PastOrPresent LocalDate awardedOn,
        @NotEmpty @Size(max = 500) List<@Valid Item> items
) {
    public record Item(
            @NotNull UUID studentId,
            @Size(max = 100) String grade,
            @DecimalMin("0.00") @DecimalMax("10.00") @Digits(integer = 2, fraction = 2) BigDecimal cgpa
    ) {
    }

}