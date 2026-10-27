package com.credchain.modules.fraud.api.dto;

import com.credchain.modules.fraud.domain.TrustLevel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Trust score of every college, lowest first (the ones to look at). */
public record InstitutionTrustResponse(
        /** When the analysis last ran; null if it never ran. */
        Instant computedAt,
        List<Entry> institutions
) {

    public record Entry(
            UUID institutionId,
            String name,
            String code,
            /** 0-100; null when the college has not been analysed yet (e.g. approved after the last run). */
            Integer trustScore,
            TrustLevel level,
            List<String> reasons,
            int anomalyCount,
            /** false = too little history on the platform for the machine-learning model; rules only. */
            boolean modelUsed,
            Instant computedAt
    ) {
    }
}
