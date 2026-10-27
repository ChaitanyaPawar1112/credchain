package com.credchain.support;

import com.credchain.modules.fraud.infrastructure.DocumentAnalysis;
import com.credchain.modules.fraud.infrastructure.FraudServiceClient;
import com.credchain.modules.fraud.infrastructure.FraudServiceException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Stands in for the Python fraud-detection service in API tests: answers whatever the test sets up. */
public class StubFraudServiceClient implements FraudServiceClient {

    private volatile DocumentAnalysis answer;
    private volatile FraudServiceException failure;
    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public DocumentAnalysis analyse(byte[] file, String fileName) {
        calls.incrementAndGet();
        if (failure != null) {
            throw failure;
        }
        if (answer == null) {
            throw FraudServiceException.unavailable("No stub answer set up", null);
        }
        return answer;
    }

    public void respondWith(DocumentAnalysis analysis) {
        this.answer = analysis;
        this.failure = null;
    }

    public void failWith(FraudServiceException exception) {
        this.failure = exception;
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        answer = null;
        failure = null;
        calls.set(0);
    }

    /** A typical answer: the QR code (when certHash is not null) and the OCR text, plus any extra signals. */
    public static DocumentAnalysis analysis(String certHash, String ocrText, DocumentAnalysis.Signal... extra) {
        List<DocumentAnalysis.Signal> signals = new java.util.ArrayList<>(List.of(extra));
        if (certHash != null) {
            signals.add(new DocumentAnalysis.Signal("QR_FOUND", DocumentAnalysis.Severity.INFO, 0, "QR found"));
        } else {
            signals.add(new DocumentAnalysis.Signal("NO_QR_CODE", DocumentAnalysis.Severity.MEDIUM, 20, "No QR code was found."));
        }
        int score = Math.min(100, signals.stream().mapToInt(DocumentAnalysis.Signal::weight).sum());
        DocumentAnalysis.RiskLevel level = score >= 55 ? DocumentAnalysis.RiskLevel.HIGH
                : score >= 25 ? DocumentAnalysis.RiskLevel.MEDIUM : DocumentAnalysis.RiskLevel.LOW;
        int words = ocrText == null ? 0 : ocrText.split("\\s+").length;
        return new DocumentAnalysis(score, level, "stub", "image/jpeg", 1,
                new DocumentAnalysis.Qr(certHash != null, certHash == null ? List.of() : List.of("http://localhost:5173/verify/" + certHash), certHash),
                new DocumentAnalysis.Text(ocrText != null, ocrText == null ? "" : ocrText, words),
                signals, "iVBORw0KGgo=", "1.0.0");
    }
}
