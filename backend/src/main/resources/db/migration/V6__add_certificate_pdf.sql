-- =====================================================
-- V6: Certificate PDFs
-- The PDF itself lives in object storage (RustFS / S3);
-- the database only remembers where it is.
-- =====================================================

ALTER TABLE certificates
    ADD COLUMN pdf_key          VARCHAR(300),
    ADD COLUMN pdf_generated_at TIMESTAMPTZ;

-- The PDF worker only looks at on-chain certificates that have no PDF yet
CREATE INDEX idx_certificates_pdf_missing ON certificates (created_at)
    WHERE pdf_key IS NULL AND status IN ('ISSUED', 'REVOCATION_PENDING', 'REVOKED');

COMMENT ON COLUMN certificates.pdf_key IS 'Object storage key of the generated PDF, e.g. certificates/{institutionId}/{certificateId}.pdf';