-- =====================================================
-- V8: Fingerprint of each certificate PDF
-- Lets the public "upload PDF" check tell whether a file is
-- exactly the one CredChain issued, or was edited afterwards.
-- =====================================================

ALTER TABLE certificates
    ADD COLUMN pdf_sha256 VARCHAR(64);

COMMENT ON COLUMN certificates.pdf_sha256 IS 'SHA-256 of the stored PDF file (64 lowercase hex); null for PDFs made before V8';
