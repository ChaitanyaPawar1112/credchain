package com.credchain.modules.fraud.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** The latest trust analysis of one college (replaced on every run). */
@Getter
@Entity
@Table(name = "institution_trust_scores")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstitutionTrustScore {

    static final int MAX_REASONS_LENGTH = 4000;

    @Id
    @Column(name = "institution_id", nullable = false, updatable = false)
    private UUID institutionId;

    @Column(name = "trust_score", nullable = false)
    private int trustScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 10)
    private TrustLevel level;

    /** One reason per line, most important first. */
    @Column(name = "reasons", nullable = false, length = MAX_REASONS_LENGTH)
    private String reasons;

    /** Unusual recent days found by the machine-learning model. */
    @Column(name = "anomaly_count", nullable = false)
    private int anomalyCount;

    @Column(name = "model_used", nullable = false)
    private boolean modelUsed;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    public static InstitutionTrustScore of(UUID institutionId) {
        InstitutionTrustScore score = new InstitutionTrustScore();
        score.institutionId = institutionId;
        return score;
    }

    public void update(int trustScore, TrustLevel level, List<String> reasons, int anomalyCount, boolean modelUsed,
                       Instant computedAt) {
        this.trustScore = Math.max(0, Math.min(100, trustScore));
        this.level = level;
        String joined = String.join("\n", reasons.stream().map(r -> r.replace('\n', ' ')).toList());
        this.reasons = joined.length() <= MAX_REASONS_LENGTH ? joined : joined.substring(0, MAX_REASONS_LENGTH);
        this.anomalyCount = anomalyCount;
        this.modelUsed = modelUsed;
        this.computedAt = computedAt;
    }

    public List<String> reasonList() {
        return reasons.isEmpty() ? List.of() : Arrays.asList(reasons.split("\n"));
    }
}
