package com.credchain.modules.verification.application;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.fraud.infrastructure.DocumentAnalysis;
import com.credchain.modules.fraud.infrastructure.DocumentAnalysis.RiskLevel;
import com.credchain.modules.fraud.infrastructure.DocumentAnalysis.Severity;
import com.credchain.modules.fraud.infrastructure.FraudServiceClient;
import com.credchain.modules.fraud.infrastructure.FraudServiceException;
import com.credchain.modules.verification.api.dto.DocumentCheckResponse;
import com.credchain.modules.verification.api.dto.DocumentCheckResponse.FieldCheck;
import com.credchain.modules.verification.api.dto.DocumentCheckResponse.Signal;
import com.credchain.modules.verification.api.dto.VerificationResponse;
import com.credchain.modules.verification.api.dto.VerificationResponse.CertificateDetails;
import com.credchain.modules.verification.domain.CheckResult;
import com.credchain.modules.verification.domain.VerificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Public check of a certificate photo, scan or any other document (no login).
 *   1. A CredChain PDF goes to the exact PDF check (cryptographic, gives VALID / FAKE).
 *   2. Anything else goes to the AI fraud-detection service: QR code, file history, printed text (OCR).
 *   3. The certificate the QR code points to is looked up (database + live blockchain, as for a QR scan).
 *   4. The printed name, numbers, programme and grade are compared with that official record.
 * The answer is a risk score with reasons, never "genuine": a photo can be of a well-made fake.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentCheckService {

    static final int MEDIUM_FROM = 25;
    static final int HIGH_FROM = 55;
    /** OCR that read fewer words than this saw too little to compare anything. */
    static final int MIN_WORDS_TO_COMPARE = 5;

    static final String FIELD_NAME = "Student name";
    static final String FIELD_ENROLLMENT = "Enrollment number";
    static final String FIELD_NUMBER = "Certificate number";
    static final String FIELD_PROGRAM = "Programme";
    static final String FIELD_RESULT = "Grade / CGPA";

    private final PdfVerificationService pdfVerificationService;
    private final PublicVerificationService verificationService;
    private final FraudServiceClient fraudService;
    private final Clock clock;

    public DocumentCheckResponse check(byte[] file, String fileName) {
        if (isCredChainPdf(file)) {
            return DocumentCheckResponse.exactPdf(pdfVerificationService.verify(file));
        }

        DocumentAnalysis analysis;
        try {
            analysis = fraudService.analyse(file, fileName);
        } catch (FraudServiceException e) {
            if (e.isFileRejected()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, e.getMessage());
            }
            throw new BusinessException(ErrorCode.FRAUD_SERVICE_UNAVAILABLE);
        }

        List<Signal> signals = new ArrayList<>();
        analysis.signals().forEach(s -> signals.add(new Signal(s.code(), s.severity(), s.weight(), s.detail())));

        String certHash = analysis.qr().certHash();
        VerificationResponse record = certHash == null ? null : verificationService.verifyByHash(certHash);
        if (record != null) {
            signals.addAll(recordSignals(record));
        }

        boolean textRead = analysis.text().available() && analysis.text().wordCount() >= MIN_WORDS_TO_COMPARE;
        List<FieldCheck> fieldChecks = record == null || record.certificate() == null
                ? List.of()
                : compare(record.certificate(), textRead ? analysis.text().text() : null, signals);

        signals.sort(Comparator.comparing((Signal s) -> s.severity().ordinal()).reversed()
                .thenComparing(Comparator.comparingInt(Signal::weight).reversed()));
        int score = Math.min(100, signals.stream().mapToInt(Signal::weight).sum());
        RiskLevel level = score >= HIGH_FROM ? RiskLevel.HIGH : score >= MEDIUM_FROM ? RiskLevel.MEDIUM : RiskLevel.LOW;

        return new DocumentCheckResponse(DocumentCheckResponse.Kind.DOCUMENT_ANALYSIS, null, score, level,
                summary(level, record, fieldChecks), analysis.fileType(), analysis.qr().found(), certHash, textRead,
                fieldChecks, List.copyOf(signals), record, analysis.elaImage(), clock.instant());
    }

    /** Only a readable PDF that carries CredChain's embedded data can be checked exactly. */
    static boolean isCredChainPdf(byte[] file) {
        try {
            return PdfVerificationService.embedded(PdfVerificationService.readInfo(file)).isPresent();
        } catch (BusinessException notAReadablePdf) {
            return false;
        }
    }

    // ---------- official record ----------

    static List<Signal> recordSignals(VerificationResponse record) {
        return switch (record.status()) {
            case NOT_FOUND -> List.of(new Signal("QR_UNKNOWN_CERTIFICATE", Severity.HIGH, 55,
                    "The QR code points to a certificate that was never issued on CredChain."));
            case REVOKED -> List.of(new Signal("CERTIFICATE_REVOKED", Severity.HIGH, 55,
                    "The certificate this QR code points to has been revoked by the college."));
            case EXPIRED -> List.of(new Signal("CERTIFICATE_EXPIRED", Severity.MEDIUM, 25,
                    "The certificate this QR code points to has expired."));
            case FAKE -> List.of(new Signal("QR_RECORD_FAKE", Severity.HIGH, 55,
                    "The QR code's certificate failed the official check."));
            case VALID -> List.of();
        };
    }

    /**
     * Compares the printed details with the official record. Every mismatch adds a signal.
     * With no readable text, every field is SKIPPED (nothing can be said either way).
     */
    static List<FieldCheck> compare(CertificateDetails official, String ocrText, List<Signal> signals) {
        PrintedTextMatcher page = ocrText == null ? null : new PrintedTextMatcher(ocrText);
        List<FieldCheck> checks = new ArrayList<>();

        field(checks, signals, page, FIELD_NAME, official.studentName(), false, 45,
                "The name printed on the document is not the name on the official record.");
        field(checks, signals, page, FIELD_ENROLLMENT, official.enrollmentNo(), true, 25,
                "The enrollment number printed on the document does not match the official record.");
        field(checks, signals, page, FIELD_NUMBER, official.certificateNumber(), true, 25,
                "The certificate number printed on the document does not match the official record.");
        String program = official.program() != null && !official.program().isBlank() ? official.program() : official.title();
        field(checks, signals, page, FIELD_PROGRAM, program, false, 20,
                "The programme printed on the document does not match the official record.");
        String result = official.cgpa() != null && !official.cgpa().isBlank() ? official.cgpa() : official.grade();
        field(checks, signals, page, FIELD_RESULT, result, true, 30,
                "The grade or CGPA printed on the document does not match the official record.");
        return checks;
    }

    private static void field(List<FieldCheck> checks, List<Signal> signals, PrintedTextMatcher page, String name,
                              String officialValue, boolean code, int weight, String mismatch) {
        if (officialValue == null || officialValue.isBlank()) {
            return;   // not on this certificate (e.g. no CGPA)
        }
        if (page == null) {
            checks.add(new FieldCheck(name, officialValue, CheckResult.SKIPPED));
            return;
        }
        boolean found = code ? page.containsCode(officialValue) : page.containsWords(officialValue);
        checks.add(new FieldCheck(name, officialValue, found ? CheckResult.PASSED : CheckResult.FAILED));
        if (!found) {
            String signalCode = "FIELD_MISMATCH_" + name.toUpperCase().replaceAll("[^A-Z]+", "_").replaceAll("_$", "");
            signals.add(new Signal(signalCode, weight >= 40 ? Severity.HIGH : Severity.MEDIUM, weight, mismatch));
        }
    }

    // ---------- summary ----------

    static String summary(RiskLevel level, VerificationResponse record, List<FieldCheck> fields) {
        if (level == RiskLevel.HIGH) {
            return "Strong signs that this document is forged or edited. Do not accept it.";
        }
        if (level == RiskLevel.MEDIUM) {
            return "Some warning signs. Do not accept it until you have checked it against the official record below.";
        }
        boolean allMatch = !fields.isEmpty() && fields.stream().allMatch(f -> f.result() == CheckResult.PASSED);
        if (record != null && record.status() == VerificationStatus.VALID && allMatch) {
            return "The QR code points to a valid certificate and the printed details match the official record. "
                    + "A photo or scan cannot prove the paper itself is genuine: for full proof, ask for the original PDF.";
        }
        return "No warning signs found, but this does not prove the document is genuine. "
                + "Ask for the original PDF or the QR link for a full check.";
    }
}
