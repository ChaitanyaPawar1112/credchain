package com.credchain.modules.fraud.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** What the fraud-detection service found in one document (its JSON answer, field for field). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentAnalysis(
        int riskScore,
        RiskLevel riskLevel,
        String summary,
        String fileType,
        int pagesAnalysed,
        Qr qr,
        Text text,
        List<Signal> signals,
        /** Error Level Analysis heat map (base64 PNG) for JPEG photos, else null. */
        String elaImage,
        String serviceVersion
) {

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    public enum Severity { INFO, LOW, MEDIUM, HIGH }

    /** One finding; weight = risk points it adds (0-100 scale). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Signal(String code, Severity severity, int weight, String detail) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Qr(boolean found, List<String> texts, String certHash) {
    }

    /** available = false when OCR is switched off or not installed in the service. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Text(boolean available, String text, int wordCount) {
    }
}
