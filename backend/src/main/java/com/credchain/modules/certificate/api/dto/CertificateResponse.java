package com.credchain.modules.certificate.api.dto;

import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateStatus;
import com.credchain.modules.certificate.domain.CertificateType;
import com.credchain.modules.certificate.domain.RevocationReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CertificateResponse(
        UUID id,
        UUID batchId,
        UUID studentId,
        String certificateNumber,
        CertificateType type,
        String title,
        String program,
        String grade,
        BigDecimal cgpa,
        LocalDate awardedOn,
        String studentName,
        String enrollmentNo,
        String certHash,
        CertificateStatus status,
        RevocationReason revocationReason,
        Instant revokedAt
) {
    public static CertificateResponse from(Certificate c) {
        return new CertificateResponse(c.getId(), c.getBatchId(), c.getStudentId(), c.getCertificateNumber(),

                c.getType(), c.getTitle(), c.getProgram(), c.getGrade(), c.getCgpa(), c.getAwardedOn(),
                c.getStudentName(), c.getEnrollmentNo(), c.getCertHash(), c.getStatus(),
                c.getRevocationReason(), c.getRevokedAt());
    }
}