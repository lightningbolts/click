-- E2EE Message Encryption Support
--
-- No schema changes are required for E2EE support.
--
-- Encrypted messages are stored in the existing `content` TEXT column
-- using the wire format:   e2e:<base64(iv || hmac || ciphertext)>
--
-- The "e2e:" prefix allows backward compatibility: unencrypted (legacy)
-- messages are returned as-is, while encrypted messages are decrypted
-- client-side before display.
--
-- Key derivation (identical on KMP and Web):
--   master  = SHA-256( SALT || sorted_user_id_1 || sorted_user_id_2 || connection_id )
--   enc_key = SHA-256( master || 0x01 )
--   mac_key = SHA-256( master || 0x02 )
--
-- The push notification trigger (notify_new_message_push) has been updated
-- to emit a generic "Tap to view message" body when the content starts
-- with "e2e:", preventing ciphertext leakage into push payloads.
--
-- Re-apply the updated trigger:

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

    -- E2EE guardrail: never leak ciphertext into push notifications
    IF LEFT(COALESCE(NEW.content, ''), 4) = 'e2e:' THEN
        preview_body := 'Tap to view message';
    ELSE
        preview_body := LEFT(COALESCE(NULLIF(NEW.content, ''), 'Open Click to view the latest message'), 120);
    END IF;

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

-- Ensure trigger is attached
DROP TRIGGER IF EXISTS trigger_new_message_push ON messages;
CREATE TRIGGER trigger_new_message_push
    AFTER INSERT ON messages
    FOR EACH ROW
    EXECUTE FUNCTION notify_new_message_push();
