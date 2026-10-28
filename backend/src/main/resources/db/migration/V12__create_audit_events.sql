-- =====================================================
-- V12: Audit log
-- One row per important action: logins, college reviews, student imports,
-- batches issued, revocations. Written once and never changed: a trigger
-- refuses UPDATE and DELETE, so nobody can quietly edit history.
-- Ids are plain columns (no foreign keys) so entries outlive the rows they mention.
-- =====================================================

CREATE TABLE audit_events (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at     TIMESTAMPTZ  NOT NULL,
    action          VARCHAR(40)  NOT NULL,
    category        VARCHAR(20)  NOT NULL,
    actor_user_id   UUID,
    actor_email     VARCHAR(255),
    actor_role      VARCHAR(30),
    institution_id  UUID,
    target_type     VARCHAR(30),
    target_id       UUID,
    summary         VARCHAR(500) NOT NULL,
    ip_address      VARCHAR(45),
    CONSTRAINT chk_audit_events_category CHECK (category IN ('ACCOUNT', 'INSTITUTION', 'STUDENT', 'CERTIFICATE', 'SYSTEM'))
);

-- Super admin: newest first, optionally one category
CREATE INDEX idx_audit_events_occurred_at ON audit_events (occurred_at DESC);
CREATE INDEX idx_audit_events_category ON audit_events (category, occurred_at DESC);

-- College admin: its own college's entries, newest first
CREATE INDEX idx_audit_events_institution ON audit_events (institution_id, occurred_at DESC);

CREATE FUNCTION audit_events_read_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_read_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_read_only();
