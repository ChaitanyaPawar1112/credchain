-- =====================================================
-- V3: Institutions (trust registry) and student records
-- =====================================================

-- ---------- INSTITUTIONS ----------
CREATE TABLE institutions (
                              id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                              name                  VARCHAR(200)  NOT NULL,
                              code                  VARCHAR(20)   NOT NULL,
                              registration_number   VARCHAR(50)   NOT NULL,
                              type                  VARCHAR(30)   NOT NULL,
                              email                 VARCHAR(255)  NOT NULL,
                              phone                 VARCHAR(20),
                              website               VARCHAR(255),
                              address_line          VARCHAR(255),
                              city                  VARCHAR(100)  NOT NULL,
                              state                 VARCHAR(100)  NOT NULL,
                              country               VARCHAR(2)    NOT NULL DEFAULT 'IN',
                              postal_code           VARCHAR(12),
                              contact_person_name   VARCHAR(150)  NOT NULL,
                              contact_person_email  VARCHAR(255)  NOT NULL,
                              wallet_address        VARCHAR(42),
                              status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
                              rejection_reason      VARCHAR(500),
                              reviewed_by           UUID,
                              reviewed_at           TIMESTAMPTZ,
                              created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
                              updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
                              version               BIGINT        NOT NULL DEFAULT 0,

                              CONSTRAINT uk_institutions_code         UNIQUE (code),
                              CONSTRAINT uk_institutions_registration UNIQUE (registration_number),
                              CONSTRAINT uk_institutions_wallet       UNIQUE (wallet_address),
                              CONSTRAINT ck_institutions_code         CHECK (code ~ '^[A-Z0-9-]{3,20}$'),
    CONSTRAINT ck_institutions_type         CHECK (type IN ('UNIVERSITY', 'COLLEGE', 'SCHOOL', 'BOARD', 'TRAINING_INSTITUTE')),
    CONSTRAINT ck_institutions_status       CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT ck_institutions_email_lower  CHECK (email = lower(email) AND contact_person_email = lower(contact_person_email)),
    CONSTRAINT ck_institutions_wallet       CHECK (wallet_address IS NULL OR wallet_address ~ '^0x[0-9a-f]{40}$'),
    CONSTRAINT ck_institutions_rejection    CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL),
    CONSTRAINT fk_institutions_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_institutions_status ON institutions (status);

COMMENT ON TABLE  institutions                IS 'Trust registry: only APPROVED institutions may issue credentials';
COMMENT ON COLUMN institutions.wallet_address IS 'Ethereum address (lowercase) authorised to issue on-chain';

-- ---------- USERS: link institution admins ----------
ALTER TABLE users
    ADD COLUMN institution_id       UUID,
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT fk_users_institution
        FOREIGN KEY (institution_id) REFERENCES institutions (id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_users_institution_admin
        CHECK (role <> 'INSTITUTION_ADMIN' OR institution_id IS NOT NULL);

CREATE INDEX idx_users_institution_id ON users (institution_id);

-- ---------- STUDENTS ----------
CREATE TABLE students (
                          id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                          institution_id         UUID          NOT NULL,
                          enrollment_no          VARCHAR(50)   NOT NULL,
                          full_name              VARCHAR(150)  NOT NULL,
                          email                  VARCHAR(255),
                          date_of_birth          DATE,
                          program                VARCHAR(150)  NOT NULL,
                          department             VARCHAR(150),
                          admission_year         SMALLINT      NOT NULL,
                          graduation_year        SMALLINT,
                          status                 VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
                          user_id                UUID,
                          claim_code_hash        VARCHAR(64),
                          claim_code_expires_at  TIMESTAMPTZ,
                          linked_at              TIMESTAMPTZ,
                          created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
                          updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
                          version                BIGINT        NOT NULL DEFAULT 0,

                          CONSTRAINT uk_students_enrollment   UNIQUE (institution_id, enrollment_no),
                          CONSTRAINT uk_students_user         UNIQUE (user_id),
                          CONSTRAINT ck_students_status       CHECK (status IN ('ACTIVE', 'GRADUATED', 'WITHDRAWN')),
                          CONSTRAINT ck_students_email_lower  CHECK (email IS NULL OR email = lower(email)),
                          CONSTRAINT ck_students_years        CHECK (admission_year BETWEEN 1950 AND 2100
                              AND (graduation_year IS NULL OR graduation_year >= admission_year)),
                          CONSTRAINT fk_students_institution
                              FOREIGN KEY (institution_id) REFERENCES institutions (id) ON DELETE RESTRICT,
                          CONSTRAINT fk_students_user
                              FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_students_institution_name ON students (institution_id, full_name);

COMMENT ON TABLE  students                 IS 'Official student records created by institutions';
COMMENT ON COLUMN students.claim_code_hash IS 'SHA-256 of one-time code a student uses to link their own account';