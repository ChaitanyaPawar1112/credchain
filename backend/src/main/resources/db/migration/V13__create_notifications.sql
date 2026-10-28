-- =====================================================
-- V13: In-app notifications (the bell in the website)
-- One row per message to one user. Emails are sent separately and not stored.
-- =====================================================

CREATE TABLE notifications (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(40)   NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    body        VARCHAR(1000) NOT NULL,
    link        VARCHAR(300),
    created_at  TIMESTAMPTZ   NOT NULL,
    read_at     TIMESTAMPTZ
);

-- The bell: a user's notifications, newest first
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);

-- The unread count on the bell
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;
