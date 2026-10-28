package com.credchain.modules.verification.api.dto;

import com.credchain.modules.fraud.infrastructure.DocumentAnalysis.RiskLevel;
import com.credchain.modules.fraud.infrastructure.DocumentAnalysis.Severity;
import com.credchain.modules.verification.domain.CheckResult;

import java.time.Instant;
import java.util.List;

/**
 * Result of checking a certificate photo, scan or other document.
 *
 * kind = EXACT_PDF: the upload was a CredChain PDF, so the exact cryptographic check ran (see pdfCheck);
 * the other fields are empty.
 * kind = DOCUMENT_ANALYSIS: the AI forensic checks ran. riskScore / riskLevel say how suspicious the document is,
 * record is the official certificate its QR code points to (if any), and fieldChecks compare the printed text
 * with that record. A photo can never be proven genuine: only a VALID record plus matching fields and a LOW risk
 * means "consistent with the official record".
 */
public record DocumentCheckResponse(
        Kind kind,
        PdfVerificationResponse pdfCheck,
        Integer riskScore,
        RiskLevel riskLevel,
        String summary,
        String fileType,
        boolean qrFound,
        String certHash,
        boolean textRead,
        List<FieldCheck> fieldChecks,
        List<Signal> signals,
        VerificationResponse record,
        /** Error Level Analysis heat map (base64 PNG) for JPEG photos, else null. */
        String elaImage,
        Instant checkedAt
) {

    public enum Kind { EXACT_PDF, DOCUMENT_ANALYSIS }

    /** One printed detail compared with the official record. */
    public record FieldCheck(String field, String officialValue, CheckResult result) {
    }

    /** One finding, most serious first. weight = risk points it added. */
    public record Signal(String code, Severity severity, int weight, String detail) {
    }

    public static DocumentCheckResponse exactPdf(PdfVerificationResponse pdfCheck) {
        return new DocumentCheckResponse(Kind.EXACT_PDF, pdfCheck, null, null, null, "application/pdf", false,
                null, false, List.of(), List.of(), null, null, pdfCheck.checkedAt());
    }
}
