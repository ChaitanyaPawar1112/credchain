-- =====================================================
-- V4: Custodial blockchain wallets for institutions
-- One wallet per institution; it receives ISSUER_ROLE
-- on the CredentialRegistry contract when approved.
-- =====================================================

CREATE TABLE institution_wallets (
                                     id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                     institution_id         UUID          NOT NULL,
                                     address                VARCHAR(42)   NOT NULL,
                                     encrypted_private_key  VARCHAR(200)  NOT NULL,
                                     custody                VARCHAR(20)   NOT NULL DEFAULT 'CUSTODIAL',
                                     status                 VARCHAR(30)   NOT NULL,
                                     funding_tx_hash        VARCHAR(66),
                                     grant_tx_hash          VARCHAR(66),
                                     revoke_tx_hash         VARCHAR(66),
                                     activated_at           TIMESTAMPTZ,
                                     attempts               INTEGER       NOT NULL DEFAULT 0,
                                     next_attempt_at        TIMESTAMPTZ,
                                     last_error             VARCHAR(500),
                                     created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                     updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                     version                BIGINT        NOT NULL DEFAULT 0,

                                     CONSTRAINT uk_institution_wallets_institution UNIQUE (institution_id),
                                     CONSTRAINT uk_institution_wallets_address     UNIQUE (address),
                                     CONSTRAINT ck_institution_wallets_address     CHECK (address ~ '^0x[0-9a-f]{40}$'),
    CONSTRAINT ck_institution_wallets_key_format  CHECK (encrypted_private_key LIKE 'v1:%'),
    CONSTRAINT ck_institution_wallets_custody     CHECK (custody IN ('CUSTODIAL')),
    CONSTRAINT ck_institution_wallets_status      CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE',
                                                                    'PENDING_DEACTIVATION', 'INACTIVE')),
    CONSTRAINT ck_institution_wallets_tx_hashes   CHECK (
        (funding_tx_hash IS NULL OR funding_tx_hash ~ '^0x[0-9a-f]{64}$') AND
        (grant_tx_hash   IS NULL OR grant_tx_hash   ~ '^0x[0-9a-f]{64}$') AND
        (revoke_tx_hash  IS NULL OR revoke_tx_hash  ~ '^0x[0-9a-f]{64}$')),
    CONSTRAINT ck_institution_wallets_attempts    CHECK (attempts >= 0),
    CONSTRAINT fk_institution_wallets_institution
        FOREIGN KEY (institution_id) REFERENCES institutions (id) ON DELETE RESTRICT
);

-- The sync worker only looks at wallets that still need an on-chain action
CREATE INDEX idx_institution_wallets_pending
    ON institution_wallets (next_attempt_at)
    WHERE status IN ('PENDING_ACTIVATION', 'PENDING_DEACTIVATION');

COMMENT ON TABLE  institution_wallets                       IS 'Custodial issuer wallets: one per institution';
COMMENT ON COLUMN institution_wallets.encrypted_private_key IS 'AES-256-GCM (v1:base64), bound to the institution id; master key lives outside the DB';
COMMENT ON COLUMN institution_wallets.status                IS 'On-chain ISSUER_ROLE state, kept in sync by the background worker';