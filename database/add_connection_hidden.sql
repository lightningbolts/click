-- Migration: connection_hidden (per-user soft hide / "Remove Connection")
-- Rows here hide a connection from this user's Active and Archived lists without
-- mutating connections.status or deleting the connection row.

CREATE TABLE IF NOT EXISTS connection_hidden (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    connection_id   uuid NOT NULL REFERENCES connections(id) ON DELETE CASCADE,
    hidden_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, connection_id)
);

CREATE INDEX IF NOT EXISTS idx_connection_hidden_user_id
    ON connection_hidden(user_id);

ALTER TABLE connection_hidden ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can manage their own hidden connections"
    ON connection_hidden
    FOR ALL
    USING  (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);
