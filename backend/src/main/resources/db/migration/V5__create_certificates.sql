-- =====================================================
-- V5: Certificates and certificate batches
-- One batch = one Merkle root = one issueBatch transaction.
-- =====================================================

-- Human-readable certificate numbers (e.g. SIT-AUR-2026-000042)
CREATE SEQUENCE certificate_number_seq START WITH 1 INCREMENT BY 1;

-- Lets certificates reference (student, institution) together, so a certificate's
-- student is guaranteed to belong to the same institution.
ALTER TABLE students
    ADD CONSTRAINT uk_students_id_institution UNIQUE (id, institution_id);

-- ---------- CERTIFICATE BATCHES ----------
CREATE TABLE certificate_batches (
                                     id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                     institution_id      UUID          NOT NULL,
                                     title               VARCHAR(200)  NOT NULL,
                                     status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
                                     certificate_count   INTEGER       NOT NULL DEFAULT 0,
                                     expires_at          TIMESTAMPTZ,
                                     merkle_root         VARCHAR(66),
                                     issuer_address      VARCHAR(42),
                                     chain_id            BIGINT,
                                     tx_hash             VARCHAR(66),
                                     block_number        BIGINT,
                                     queued_by           UUID,
                                     queued_at           TIMESTAMPTZ,
                                     anchored_at         TIMESTAMPTZ,
                                     attempts            INTEGER       NOT NULL DEFAULT 0,
                                     next_attempt_at     TIMESTAMPTZ,
                                     last_error          VARCHAR(500),

                                     revoked_at          TIMESTAMPTZ,
                                     revocation_reason   VARCHAR(30),
                                     created_by          UUID          NOT NULL,
                                     created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                     updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                     version             BIGINT        NOT NULL DEFAULT 0,

                                     CONSTRAINT uk_certificate_batches_id_institution UNIQUE (id, institution_id),
                                     CONSTRAINT uk_certificate_batches_merkle_root    UNIQUE (merkle_root),
                                     CONSTRAINT ck_certificate_batches_status   CHECK (status IN ('DRAFT', 'QUEUED', 'SUBMITTED', 'ANCHORED', 'REVOKED')),
                                     CONSTRAINT ck_certificate_batches_count    CHECK (certificate_count >= 0),
                                     CONSTRAINT ck_certificate_batches_attempts CHECK (attempts >= 0),
                                     CONSTRAINT ck_certificate_batches_formats  CHECK (
                                         (merkle_root    IS NULL OR merkle_root    ~ '^0x[0-9a-f]{64}$') AND
                                         (tx_hash        IS NULL OR tx_hash        ~ '^0x[0-9a-f]{64}$') AND
                                         (issuer_address IS NULL OR issuer_address ~ '^0x[0-9a-f]{40}$')),
                                     CONSTRAINT ck_certificate_batches_frozen   CHECK (
                                         status = 'DRAFT' OR (merkle_root IS NOT NULL AND issuer_address IS NOT NULL
                                             AND chain_id IS NOT NULL AND certificate_count > 0)),
                                     CONSTRAINT ck_certificate_batches_tx       CHECK (status NOT IN ('SUBMITTED', 'ANCHORED', 'REVOKED') OR tx_hash IS NOT NULL),
                                     CONSTRAINT ck_certificate_batches_revoked  CHECK (
                                         status <> 'REVOKED' OR (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL)),
                                     CONSTRAINT ck_certificate_batches_reason   CHECK (
                                         revocation_reason IS NULL OR revocation_reason IN ('ISSUED_IN_ERROR', 'FRAUD', 'SUPERSEDED', 'OTHER')),
                                     CONSTRAINT fk_certificate_batches_institution
                                         FOREIGN KEY (institution_id) REFERENCES institutions (id) ON DELETE RESTRICT,
                                     CONSTRAINT fk_certificate_batches_created_by
                                         FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
                                     CONSTRAINT fk_certificate_batches_queued_by
                                         FOREIGN KEY (queued_by) REFERENCES users (id) ON DELETE SET NULL
);


CREATE INDEX idx_certificate_batches_institution ON certificate_batches (institution_id, created_at DESC);
-- The anchoring worker only looks at batches still waiting for the chain
CREATE INDEX idx_certificate_batches_pending ON certificate_batches (next_attempt_at)
    WHERE status IN ('QUEUED', 'SUBMITTED');

-- ---------- CERTIFICATES ----------
CREATE TABLE certificates (
                              id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                              institution_id      UUID          NOT NULL,
                              batch_id            UUID          NOT NULL,
                              student_id          UUID          NOT NULL,
                              certificate_number  VARCHAR(50)   NOT NULL,
                              type                VARCHAR(30)   NOT NULL,
                              title               VARCHAR(200)  NOT NULL,
                              program             VARCHAR(150),
                              grade               VARCHAR(100),
                              cgpa                NUMERIC(4, 2),
                              awarded_on          DATE          NOT NULL,
                              student_name        VARCHAR(150)  NOT NULL,
                              enrollment_no       VARCHAR(50)   NOT NULL,
                              salt                VARCHAR(66)   NOT NULL,
                              canonical_payload   TEXT          NOT NULL,
                              cert_hash           VARCHAR(66)   NOT NULL,
                              merkle_proof        TEXT,
                              status              VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
                              revocation_reason   VARCHAR(30),
                              revocation_note     VARCHAR(500),
                              revoked_at          TIMESTAMPTZ,
                              revoke_tx_hash      VARCHAR(66),
                              attempts            INTEGER       NOT NULL DEFAULT 0,
                              next_attempt_at     TIMESTAMPTZ,
                              last_error          VARCHAR(500),

                              created_by          UUID          NOT NULL,
                              created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
                              updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
                              version             BIGINT        NOT NULL DEFAULT 0,

                              CONSTRAINT uk_certificates_hash   UNIQUE (cert_hash),
                              CONSTRAINT uk_certificates_number UNIQUE (institution_id, certificate_number),
                              CONSTRAINT ck_certificates_type   CHECK (type IN ('DEGREE', 'DIPLOMA', 'CERTIFICATE', 'TRANSCRIPT', 'PROVISIONAL', 'OTHER')),
                              CONSTRAINT ck_certificates_status CHECK (status IN ('DRAFT', 'PENDING', 'ISSUED', 'REVOCATION_PENDING', 'REVOKED')),
                              CONSTRAINT ck_certificates_formats CHECK (
                                  cert_hash ~ '^0x[0-9a-f]{64}$' AND
                                  salt      ~ '^0x[0-9a-f]{64}$' AND
                                  (revoke_tx_hash IS NULL OR revoke_tx_hash ~ '^0x[0-9a-f]{64}$')),
    CONSTRAINT ck_certificates_cgpa     CHECK (cgpa IS NULL OR cgpa BETWEEN 0 AND 10),
    CONSTRAINT ck_certificates_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_certificates_proof    CHECK (status = 'DRAFT' OR merkle_proof IS NOT NULL),
    CONSTRAINT ck_certificates_revocation CHECK (
        (status NOT IN ('REVOCATION_PENDING', 'REVOKED') OR revocation_reason IS NOT NULL) AND
        (status <> 'REVOKED' OR revoked_at IS NOT NULL)),
    CONSTRAINT ck_certificates_reason CHECK (
        revocation_reason IS NULL OR revocation_reason IN ('ISSUED_IN_ERROR', 'FRAUD', 'SUPERSEDED', 'OTHER')),
    CONSTRAINT fk_certificates_batch_same_institution
        FOREIGN KEY (batch_id, institution_id) REFERENCES certificate_batches (id, institution_id) ON DELETE RESTRICT,
    CONSTRAINT fk_certificates_student_same_institution
        FOREIGN KEY (student_id, institution_id) REFERENCES students (id, institution_id) ON DELETE RESTRICT,
    CONSTRAINT fk_certificates_created_by
        FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_certificates_batch               ON certificates (batch_id);
CREATE INDEX idx_certificates_student             ON certificates (student_id);
CREATE INDEX idx_certificates_institution_status  ON certificates (institution_id, status);

-- The revocation worker only looks at certificates waiting for an on-chain revoke
CREATE INDEX idx_certificates_revocation_pending  ON certificates (next_attempt_at)
    WHERE status = 'REVOCATION_PENDING';

COMMENT ON TABLE  certificate_batches             IS 'Group of certificates anchored with one Merkle root (one issueBatch transaction)';
COMMENT ON TABLE  certificates                    IS 'Issued credentials; only their keccak256 hash goes on-chain';
COMMENT ON COLUMN certificates.salt               IS 'Random 256-bit secret mixed into the hash so the public chain reveals nothing guessable';
COMMENT ON COLUMN certificates.canonical_payload  IS 'Exact canonical JSON that was hashed (never changes)';
COMMENT ON COLUMN certificates.merkle_proof       IS 'JSON array of sibling hashes proving the certificate is in its batch root';