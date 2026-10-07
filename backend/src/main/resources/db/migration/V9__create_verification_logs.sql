-- =====================================================
-- V9: Verification log
-- One row per public check (by hash / QR link or by PDF upload).
-- Lets an institution see how often its certificates are checked,
-- and the platform admin spot forged certificates being used.
-- No IP address or other personal data of the verifier is stored.
-- =====================================================

CREATE TABLE verification_logs (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    checked_at         TIMESTAMPTZ  NOT NULL,
    method             VARCHAR(10)  NOT NULL,
    result             VARCHAR(20)  NOT NULL,
    blockchain_checked BOOLEAN      NOT NULL,
    cert_hash          VARCHAR(66),
    certificate_id     UUID         REFERENCES certificates (id) ON DELETE SET NULL,
    institution_id     UUID         REFERENCES institutions (id) ON DELETE SET NULL,
    user_agent         VARCHAR(300),
    CONSTRAINT chk_verification_logs_method CHECK (method IN ('HASH', 'PDF')),
    CONSTRAINT chk_verification_logs_result CHECK (result IN ('VALID', 'REVOKED', 'EXPIRED', 'NOT_FOUND', 'FAKE'))
);

-- Institution admins list checks of their own certificates, newest first
CREATE INDEX idx_verification_logs_institution ON verification_logs (institution_id, checked_at DESC);

-- Platform admin lists all checks, newest first
CREATE INDEX idx_verification_logs_checked_at ON verification_logs (checked_at DESC);
