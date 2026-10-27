-- =====================================================
-- V10: Photo / scan checks (Phase 7, AI fraud detection)
-- A public check can now be a DOCUMENT check: a photo, scan or other file analysed by the
-- fraud-detection service. risk_score (0-100) is that check's risk; empty for HASH and PDF checks.
-- =====================================================

ALTER TABLE verification_logs DROP CONSTRAINT chk_verification_logs_method;
ALTER TABLE verification_logs
    ADD CONSTRAINT chk_verification_logs_method CHECK (method IN ('HASH', 'PDF', 'DOCUMENT'));

ALTER TABLE verification_logs
    ADD COLUMN risk_score INTEGER,
    ADD CONSTRAINT chk_verification_logs_risk_score CHECK (risk_score BETWEEN 0 AND 100);
