package com.credchain.modules.certificate.api.dto;

import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateStatus;
import com.credchain.modules.certificate.domain.CertificateType;
import com.credchain.modules.certificate.domain.RevocationReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** What a student sees about one of their own certificates. */
public record MyCertificateResponse(
        UUID id,
        String certificateNumber,
        String institutionName,
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
        Instant revokedAt,
        boolean pdfAvailable,
        String verificationUrl
) {
    public static MyCertificateResponse of(Certificate c, String institutionName, String verificationUrl) {
        return new MyCertificateResponse(c.getId(), c.getCertificateNumber(), institutionName,
                c.getType(), c.getTitle(), c.getProgram(), c.getGrade(), c.getCgpa(), c.getAwardedOn(),
                c.getStudentName(), c.getEnrollmentNo(), c.getCertHash(), c.getStatus(),
                c.getRevocationReason(), c.getRevokedAt(), c.hasPdf(), verificationUrl);
    }
}