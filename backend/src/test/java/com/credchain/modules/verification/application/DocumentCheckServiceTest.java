package com.credchain.modules.verification.application;

import com.credchain.modules.certificate.domain.CertificateType;
import com.credchain.modules.fraud.infrastructure.DocumentAnalysis.RiskLevel;
import com.credchain.modules.verification.api.dto.DocumentCheckResponse.FieldCheck;
import com.credchain.modules.verification.api.dto.DocumentCheckResponse.Signal;
import com.credchain.modules.verification.api.dto.VerificationResponse;
import com.credchain.modules.verification.api.dto.VerificationResponse.CertificateDetails;
import com.credchain.modules.verification.domain.CheckResult;
import com.credchain.modules.verification.domain.VerificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Photo / scan check rules")
class DocumentCheckServiceTest {

    private static final CertificateDetails SNEHA = new CertificateDetails("PIT-PUNE-2026-000004", CertificateType.DEGREE,
            "Bachelor of Technology", "B.Tech Computer Engineering", null, "8.50", LocalDate.of(2026, 6, 30),
            "Sneha Joshi", "PIT2022CS002", "Pune Institute of Technology", "PIT-PUNE", null);

    private static final String MATCHING_TEXT = """
            Pune Institute of Technology. This is to certify that Sneha Joshi has been awarded
            B.Tech Computer Engineering. Enrollment PIT2022CS002. Certificate PIT-PUNE-2026-000004. CGPA 8.50
            """;

    @Test
    @DisplayName("matching printed details: every field PASSED and no signal")
    void allFieldsMatch() {
        List<Signal> signals = new ArrayList<>();
        List<FieldCheck> checks = DocumentCheckService.compare(SNEHA, MATCHING_TEXT, signals);
        assertThat(checks).extracting(FieldCheck::field).containsExactly(
                "Student name", "Enrollment number", "Certificate number", "Programme", "Grade / CGPA");
        assertThat(checks).allMatch(c -> c.result() == CheckResult.PASSED);
        assertThat(signals).isEmpty();
    }

    @Test
    @DisplayName("a changed name and CGPA are caught, with the name as a HIGH signal")
    void changedNameAndGrade() {
        String edited = MATCHING_TEXT.replace("Sneha Joshi", "Rahul Sharma").replace("8.50", "9.80");
        List<Signal> signals = new ArrayList<>();
        List<FieldCheck> checks = DocumentCheckService.compare(SNEHA, edited, signals);
        assertThat(checks).filteredOn(c -> c.result() == CheckResult.FAILED)
                .extracting(FieldCheck::field).containsExactly("Student name", "Grade / CGPA");
        assertThat(signals).extracting(Signal::code)
                .containsExactly("FIELD_MISMATCH_STUDENT_NAME", "FIELD_MISMATCH_GRADE_CGPA");
        assertThat(signals.get(0).weight()).isEqualTo(45);
    }

    @Test
    @DisplayName("without readable text every field is SKIPPED and nothing is blamed")
    void noText() {
        List<Signal> signals = new ArrayList<>();
        List<FieldCheck> checks = DocumentCheckService.compare(SNEHA, null, signals);
        assertThat(checks).allMatch(c -> c.result() == CheckResult.SKIPPED);
        assertThat(signals).isEmpty();
    }

    @Test
    @DisplayName("fields the certificate does not have are left out")
    void missingFields() {
        CertificateDetails noGrade = new CertificateDetails("N-1", CertificateType.DEGREE, "Bachelor of Science", null,
                null, null, null, "Asha Patil", "E1", "College", "C", null);
        List<FieldCheck> checks = DocumentCheckService.compare(noGrade, "Asha Patil Bachelor of Science N-1 E1", new ArrayList<>());
        assertThat(checks).extracting(FieldCheck::field)
                .containsExactly("Student name", "Enrollment number", "Certificate number", "Programme");
        assertThat(checks).filteredOn(c -> c.field().equals("Programme"))
                .extracting(FieldCheck::officialValue).containsExactly("Bachelor of Science");   // falls back to the title
    }

    @Test
    @DisplayName("the official record's status adds its own signal")
    void recordSignals() {
        assertThat(DocumentCheckService.recordSignals(record(VerificationStatus.VALID))).isEmpty();
        assertThat(DocumentCheckService.recordSignals(record(VerificationStatus.REVOKED)))
                .extracting(Signal::code, Signal::weight).containsExactly(org.assertj.core.groups.Tuple.tuple("CERTIFICATE_REVOKED", 55));
        assertThat(DocumentCheckService.recordSignals(record(VerificationStatus.NOT_FOUND)))
                .extracting(Signal::code).containsExactly("QR_UNKNOWN_CERTIFICATE");
        assertThat(DocumentCheckService.recordSignals(record(VerificationStatus.EXPIRED)))
                .extracting(Signal::weight).containsExactly(25);
    }

    @Test
    @DisplayName("LOW never says genuine; it says 'matches the official record' only for a VALID record with all fields matching")
    void summaries() {
        List<FieldCheck> passed = List.of(new FieldCheck("Student name", "Sneha Joshi", CheckResult.PASSED));
        List<FieldCheck> skipped = List.of(new FieldCheck("Student name", "Sneha Joshi", CheckResult.SKIPPED));
        assertThat(DocumentCheckService.summary(RiskLevel.LOW, record(VerificationStatus.VALID), passed))
                .contains("match the official record").contains("cannot prove");
        assertThat(DocumentCheckService.summary(RiskLevel.LOW, record(VerificationStatus.VALID), skipped))
                .contains("does not prove");
        assertThat(DocumentCheckService.summary(RiskLevel.LOW, null, List.of())).contains("does not prove");
        assertThat(DocumentCheckService.summary(RiskLevel.HIGH, record(VerificationStatus.VALID), passed)).contains("Do not accept");
    }

    @Test
    @DisplayName("only a readable PDF with CredChain data goes to the exact PDF check")
    void credChainPdfDetection() {
        assertThat(DocumentCheckService.isCredChainPdf("not a pdf".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(DocumentCheckService.isCredChainPdf(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isFalse();
        assertThat(DocumentCheckService.isCredChainPdf("%PDF-1.4 broken".getBytes(StandardCharsets.US_ASCII))).isFalse();
    }

    private static VerificationResponse record(VerificationStatus status) {
        return new VerificationResponse("0x" + "a".repeat(64), status, "msg", true, Instant.now(), SNEHA, null, null);
    }
}
