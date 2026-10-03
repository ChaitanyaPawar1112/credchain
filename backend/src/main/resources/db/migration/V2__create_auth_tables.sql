-- =====================================================
-- V2: Authentication tables (users, refresh_tokens)
-- =====================================================

-- ---------- USERS ----------
CREATE TABLE users (
                       id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                       email                  VARCHAR(255)  NOT NULL,
                       password_hash          VARCHAR(255)  NOT NULL,
                       full_name              VARCHAR(150)  NOT NULL,
                       phone                  VARCHAR(20),
                       role                   VARCHAR(30)   NOT NULL,
                       status                 VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE',
                       email_verified         BOOLEAN       NOT NULL DEFAULT FALSE,
                       failed_login_attempts  INTEGER       NOT NULL DEFAULT 0,
                       locked_until           TIMESTAMPTZ,
                       last_login_at          TIMESTAMPTZ,
                       created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
                       updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
                       version                BIGINT        NOT NULL DEFAULT 0,

                       CONSTRAINT uk_users_email        UNIQUE (email),
                       CONSTRAINT ck_users_email_lower  CHECK (email = lower(email)),
                       CONSTRAINT ck_users_role         CHECK (role IN ('SUPER_ADMIN', 'INSTITUTION_ADMIN', 'STUDENT', 'VERIFIER')),
                       CONSTRAINT ck_users_status       CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED')),
                       CONSTRAINT ck_users_failed_login CHECK (failed_login_attempts >= 0)
);

CREATE INDEX idx_users_role   ON users (role);
CREATE INDEX idx_users_status ON users (status);

COMMENT ON TABLE  users               IS 'All system users; role decides permissions';
COMMENT ON COLUMN users.password_hash IS 'BCrypt hash, never the raw password';

-- ---------- REFRESH TOKENS ----------
CREATE TABLE refresh_tokens (
                                id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                user_id         UUID          NOT NULL,
                                token_hash      VARCHAR(64)   NOT NULL,
                                expires_at      TIMESTAMPTZ   NOT NULL,
                                revoked_at      TIMESTAMPTZ,
                                replaced_by_id  UUID,
                                created_by_ip   VARCHAR(45),
                                user_agent      VARCHAR(255),
                                created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

                                CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
                                CONSTRAINT fk_refresh_tokens_user
                                    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
                                CONSTRAINT fk_refresh_tokens_replaced_by
                                    FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id) ON DELETE SET NULL
);

CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hex of the refresh token; raw token is never stored';