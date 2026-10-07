-- =====================================================
-- V7: Reconciliation (blockchain vs database)
-- A background job regularly re-reads each on-chain certificate
-- from the smart contract and records whether it still matches.
-- =====================================================

ALTER TABLE certificates
    ADD COLUMN chain_checked_at   TIMESTAMPTZ,
    ADD COLUMN chain_check_result VARCHAR(20),
    ADD COLUMN chain_check_note   VARCHAR(500),
    ADD CONSTRAINT chk_certificates_chain_check_result
        CHECK (chain_check_result IS NULL OR chain_check_result IN ('MATCH', 'MISMATCH'));

-- The job picks never-checked certificates first, then the ones checked longest ago
CREATE INDEX idx_certificates_chain_check ON certificates (chain_checked_at NULLS FIRST)
    WHERE status IN ('ISSUED', 'REVOCATION_PENDING', 'REVOKED');

-- The admin report lists mismatches
CREATE INDEX idx_certificates_chain_mismatch ON certificates (chain_checked_at)
    WHERE chain_check_result = 'MISMATCH';