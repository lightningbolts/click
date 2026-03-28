-- Add message_type and metadata for system rows (e.g. VoIP call_log) and structured payloads.
-- Safe to run multiple times (IF NOT EXISTS).

ALTER TABLE public.messages
    ADD COLUMN IF NOT EXISTS message_type text DEFAULT 'text';

ALTER TABLE public.messages
    ADD COLUMN IF NOT EXISTS metadata jsonb DEFAULT '{}'::jsonb;

COMMENT ON COLUMN public.messages.message_type IS 'Message kind: text (default), call_log, etc.';
COMMENT ON COLUMN public.messages.metadata IS 'Structured payload (e.g. call_state, duration_seconds for call_log).';

-- Skip push notifications for call_log system rows (same idea as e2e:* skip in skip_e2e_duplicate_message_push.sql).
CREATE OR REPLACE FUNCTION notify_new_message_push()
RETURNS TRIGGER AS $$
DECLARE
    recipient_user_id UUID;
    sender_name TEXT;
    preview_body TEXT;
    supabase_url TEXT;
    service_role_key TEXT;
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

    IF LEFT(COALESCE(NEW.content, ''), 4) = 'e2e:' THEN
        RETURN NEW;
    END IF;

    IF COALESCE(NEW.message_type, 'text') = 'call_log' THEN
        RETURN NEW;
    END IF;

    supabase_url := current_setting('app.settings.supabase_url', true);
    service_role_key := current_setting('app.settings.service_role_key', true);

    IF supabase_url IS NULL OR supabase_url = '' OR service_role_key IS NULL OR service_role_key = '' THEN
        RAISE LOG 'Skipping push notification for message % because required database settings are missing', NEW.id;
        RETURN NEW;
    END IF;

    SELECT COALESCE(NULLIF(name, ''), 'Someone')
      INTO sender_name
      FROM users
     WHERE id = NEW.user_id
     LIMIT 1;

    preview_body := LEFT(COALESCE(NULLIF(NEW.content, ''), 'Open Click to view the latest message'), 120);

    BEGIN
        PERFORM net.http_post(
            url := supabase_url || '/functions/v1/send-push-notification',
            headers := jsonb_build_object(
                'Authorization', 'Bearer ' || service_role_key,
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
    EXCEPTION
        WHEN undefined_function THEN
            RAISE LOG 'Skipping push notification for message % because pg_net is unavailable', NEW.id;
        WHEN OTHERS THEN
            RAISE LOG 'Push notification dispatch failed for message %: %', NEW.id, SQLERRM;
    END;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Inserts into public.messages use PostgREST / app servers (no Postgres RPC in this repo).
-- Optional message_type and metadata are accepted by:
--   - KMP: direct Supabase insert (SupabaseChatRepository.sendMessage)
--   - Web: POST /api/chat/messages (Next.js route)
--   - Python: ChatOperations.create_message via POST /api/chats/<chat_id>/messages
-- The trigger above runs on every INSERT and skips push for call_log rows.
