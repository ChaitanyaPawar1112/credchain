package com.credchain.modules.fraud.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

/** The service's answer: a trust score per college, with reasons and unusual recent days. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InstitutionTrustAnalysis(
        String model,
        /** false when there was too little history for the machine-learning model (rules only). */
        boolean modelUsed,
        int rowsAnalysed,
        List<Trust> institutions
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Trust(String institutionId, String name, int trustScore, String level, List<String> reasons,
                        List<Anomaly> anomalies) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Anomaly(LocalDate date, double score, String detail) {
    }
}
