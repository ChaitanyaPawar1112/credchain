-- =====================================================
-- V14: Share links (Phase 8.4)
-- A student shares one certificate through a named link that expires and can be turned off.
-- Anyone with the link sees the live verification result; every view is counted.
-- =====================================================

CREATE TABLE share_links (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    token           VARCHAR(43)   NOT NULL,                 -- 32 random bytes, base64url: the secret part of the URL
    certificate_id  UUID          NOT NULL REFERENCES certificates (id) ON DELETE CASCADE,
    student_id      UUID          NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    name            VARCHAR(100)  NOT NULL,                 -- the student's own label, e.g. "Infosys HR"
    created_at      TIMESTAMPTZ   NOT NULL,
    expires_at      TIMESTAMPTZ   NOT NULL,
    revoked_at      TIMESTAMPTZ,
    view_count      INTEGER       NOT NULL DEFAULT 0,
    last_viewed_at  TIMESTAMPTZ,
    CONSTRAINT uq_share_links_token UNIQUE (token),
    CONSTRAINT ck_share_links_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_share_links_views CHECK (view_count >= 0)
);

-- A student's links, newest first
CREATE INDEX idx_share_links_student ON share_links (student_id, created_at DESC);
