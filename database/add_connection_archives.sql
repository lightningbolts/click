-- Migration: connection_archives table
-- Stores user-level archive decisions so a user can hide connections
-- from their main list without deleting them for the other party.
-- Run after applying the base schema (chat_schema.sql).

CREATE TABLE IF NOT EXISTS connection_archives (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    connection_id uuid NOT NULL REFERENCES connections(id) ON DELETE CASCADE,
    archived_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, connection_id)
);

-- Index for fast lookup by user
CREATE INDEX IF NOT EXISTS idx_connection_archives_user_id
    ON connection_archives(user_id);

-- RLS: each user may only read/write their own archive rows
ALTER TABLE connection_archives ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can manage their own archives"
    ON connection_archives
    FOR ALL
    USING  (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);
