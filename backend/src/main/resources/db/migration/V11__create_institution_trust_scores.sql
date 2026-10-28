-- =====================================================
-- V11: Institution trust scores (Phase 7, AI fraud detection)
-- The latest result per college of the daily trust analysis: an Isolation Forest looks for unusual
-- recent days (e.g. thousands of certificates issued at night), plus rules for revocations and
-- forged copies. One row per college, replaced on every run.
-- =====================================================

CREATE TABLE institution_trust_scores (
    institution_id UUID          PRIMARY KEY REFERENCES institutions (id) ON DELETE CASCADE,
    trust_score    INTEGER       NOT NULL,
    level          VARCHAR(10)   NOT NULL,
    reasons        VARCHAR(4000) NOT NULL,
    anomaly_count  INTEGER       NOT NULL,
    model_used     BOOLEAN       NOT NULL,
    computed_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT chk_institution_trust_score CHECK (trust_score BETWEEN 0 AND 100),
    CONSTRAINT chk_institution_trust_level CHECK (level IN ('TRUSTED', 'WATCH', 'AT_RISK')),
    CONSTRAINT chk_institution_trust_anomalies CHECK (anomaly_count >= 0)
);
