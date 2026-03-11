CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    message_push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    call_push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

ALTER TABLE notification_preferences ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can read their own notification preferences" ON notification_preferences;
CREATE POLICY "Users can read their own notification preferences"
ON notification_preferences
FOR SELECT
USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can insert their own notification preferences" ON notification_preferences;
CREATE POLICY "Users can insert their own notification preferences"
ON notification_preferences
FOR INSERT
WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can update their own notification preferences" ON notification_preferences;
CREATE POLICY "Users can update their own notification preferences"
ON notification_preferences
FOR UPDATE
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);