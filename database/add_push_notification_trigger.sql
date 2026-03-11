-- Migration: send push notifications for newly inserted messages via Supabase Edge Function.
-- Requires pg_net extension to be enabled in Supabase Dashboard.

CREATE OR REPLACE FUNCTION notify_new_message_push()
RETURNS TRIGGER AS $$
DECLARE
    recipient_user_id UUID;
    sender_name TEXT;
    preview_body TEXT;
    recipient_pref_enabled BOOLEAN;
BEGIN
    SELECT u.id::uuid
      INTO recipient_user_id
      FROM chats ch
      JOIN connections c ON c.id = ch.connection_id
      JOIN LATERAL unnest(c.user_ids) AS u(id) ON TRUE
     WHERE ch.id = NEW.chat_id
             AND u.id <> NEW.user_id::text
     LIMIT 1;

    IF recipient_user_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT COALESCE(np.message_push_enabled, TRUE)
      INTO recipient_pref_enabled
      FROM notification_preferences np
     WHERE np.user_id = recipient_user_id;

    IF recipient_pref_enabled = FALSE THEN
        RETURN NEW;
    END IF;

    SELECT COALESCE(NULLIF(name, ''), 'Someone')
      INTO sender_name
      FROM users
     WHERE id = NEW.user_id
     LIMIT 1;

    preview_body := LEFT(COALESCE(NULLIF(NEW.content, ''), 'Open Click to view the latest message'), 120);

    PERFORM net.http_post(
        url := current_setting('app.settings.supabase_url') || '/functions/v1/send-push-notification',
        headers := jsonb_build_object(
            'Authorization', 'Bearer ' || current_setting('app.settings.service_role_key'),
            'Content-Type', 'application/json'
        ),
        body := jsonb_build_object(
            'recipient_user_id', recipient_user_id,
            'title', 'New message from ' || sender_name,
            'body', preview_body,
            'data', jsonb_build_object(
                'type', 'chat_message',
                'connection_id', (SELECT connection_id FROM chats WHERE id = NEW.chat_id),
                'chat_id', NEW.chat_id,
                'message_id', NEW.id,
                'sender_user_id', NEW.user_id
            )
        )
    );

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS trigger_notify_new_message_push ON messages;

CREATE TRIGGER trigger_notify_new_message_push
    AFTER INSERT ON messages
    FOR EACH ROW
    EXECUTE FUNCTION notify_new_message_push();