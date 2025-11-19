-- Migration script to update Supabase database to match Kotlin models
-- This aligns the database with the Python schema.py structure

-- Update users table to match Python schema
ALTER TABLE users
    DROP COLUMN IF EXISTS share_key,
    DROP COLUMN IF EXISTS updated_at,
    ADD COLUMN IF NOT EXISTS last_polled BIGINT,
    ADD COLUMN IF NOT EXISTS paired_with TEXT[] DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS connection_today INTEGER DEFAULT -1,
    ADD COLUMN IF NOT EXISTS last_paired BIGINT;

-- Drop and recreate connections table with new structure
DROP TABLE IF EXISTS connections CASCADE;

CREATE TABLE connections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created BIGINT NOT NULL,
    expiry BIGINT NOT NULL,
    -- Geographic location stored as JSONB with lat/lon
    geo_location JSONB NOT NULL,
    -- Full location data from geocoding service
    full_location JSONB,
    -- Human-readable location name
    semantic_location TEXT,
    -- Array of user IDs (replacing user1_id, user2_id)
    user_ids TEXT[] NOT NULL,
    -- Embedded chat data
    chat JSONB DEFAULT '{"messages": []}'::jsonb,
    -- Array of booleans for should_continue
    should_continue BOOLEAN[] DEFAULT '{false, false}',
    has_begun BOOLEAN DEFAULT false,
    CONSTRAINT unique_user_pair UNIQUE(user_ids)
);

-- Create index for querying by user_ids
CREATE INDEX idx_connections_user_ids ON connections USING GIN(user_ids);

-- Update chats table to remove it (data now embedded in connections)
-- Keep it for backward compatibility with messages
DROP TABLE IF EXISTS messages CASCADE;
DROP TABLE IF EXISTS chats CASCADE;

CREATE TABLE chats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    connection_id UUID REFERENCES connections(id) ON DELETE CASCADE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

-- Recreate messages table with corrected field names
CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    chat_id UUID REFERENCES chats(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    time_created BIGINT NOT NULL,
    time_edited BIGINT,
    is_read BOOLEAN DEFAULT false
);

-- Create message_reactions table
CREATE TABLE IF NOT EXISTS message_reactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reaction_type TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    CONSTRAINT unique_user_message_reaction UNIQUE(message_id, user_id, reaction_type)
);

-- Create indexes for better performance
CREATE INDEX idx_messages_chat ON messages(chat_id);
CREATE INDEX idx_messages_time_created ON messages(time_created DESC);
CREATE INDEX idx_messages_unread ON messages(chat_id, is_read) WHERE is_read = false;
CREATE INDEX idx_message_reactions_message ON message_reactions(message_id);
CREATE INDEX idx_chats_connection ON chats(connection_id);

-- Update Row Level Security policies for new structure
ALTER TABLE connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE chats ENABLE ROW LEVEL SECURITY;
ALTER TABLE message_reactions ENABLE ROW LEVEL SECURITY;

-- Connections policies (check if user is in user_ids array)
DROP POLICY IF EXISTS "Users can view their own connections" ON connections;
CREATE POLICY "Users can view their own connections"
    ON connections FOR SELECT
    USING (auth.uid()::text = ANY(user_ids));

DROP POLICY IF EXISTS "Users can create connections" ON connections;
CREATE POLICY "Users can create connections"
    ON connections FOR INSERT
    WITH CHECK (auth.uid()::text = ANY(user_ids));

DROP POLICY IF EXISTS "Users can update their own connections" ON connections;
CREATE POLICY "Users can update their own connections"
    ON connections FOR UPDATE
    USING (auth.uid()::text = ANY(user_ids));

-- Messages policies
DROP POLICY IF EXISTS "Users can view messages in their chats" ON messages;
CREATE POLICY "Users can view messages in their chats"
    ON messages FOR SELECT
    USING (
        chat_id IN (
            SELECT c.id FROM chats c
            JOIN connections conn ON c.connection_id = conn.id
            WHERE auth.uid()::text = ANY(conn.user_ids)
        )
    );

DROP POLICY IF EXISTS "Users can insert messages in their chats" ON messages;
CREATE POLICY "Users can insert messages in their chats"
    ON messages FOR INSERT
    WITH CHECK (
        chat_id IN (
            SELECT c.id FROM chats c
            JOIN connections conn ON c.connection_id = conn.id
            WHERE auth.uid()::text = ANY(conn.user_ids)
        )
    );

DROP POLICY IF EXISTS "Users can update their own messages" ON messages;
CREATE POLICY "Users can update their own messages"
    ON messages FOR UPDATE
    USING (user_id = auth.uid());

-- Chats policies
DROP POLICY IF EXISTS "Users can view their own chats" ON chats;
CREATE POLICY "Users can view their own chats"
    ON chats FOR SELECT
    USING (
        connection_id IN (
            SELECT id FROM connections WHERE auth.uid()::text = ANY(user_ids)
        )
    );

DROP POLICY IF EXISTS "Users can create chats" ON chats;
CREATE POLICY "Users can create chats"
    ON chats FOR INSERT
    WITH CHECK (
        connection_id IN (
            SELECT id FROM connections WHERE auth.uid()::text = ANY(user_ids)
        )
    );

-- Message reactions policies
DROP POLICY IF EXISTS "Users can view reactions" ON message_reactions;
CREATE POLICY "Users can view reactions"
    ON message_reactions FOR SELECT
    USING (
        message_id IN (
            SELECT m.id FROM messages m
            JOIN chats c ON m.chat_id = c.id
            JOIN connections conn ON c.connection_id = conn.id
            WHERE auth.uid()::text = ANY(conn.user_ids)
        )
    );

DROP POLICY IF EXISTS "Users can add reactions" ON message_reactions;
CREATE POLICY "Users can add reactions"
    ON message_reactions FOR INSERT
    WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS "Users can remove their reactions" ON message_reactions;
CREATE POLICY "Users can remove their reactions"
    ON message_reactions FOR DELETE
    USING (user_id = auth.uid());

-- Function to create sample connection with proper structure
CREATE OR REPLACE FUNCTION create_sample_connection(
    p_user1_id UUID,
    p_user2_id UUID,
    p_lat DOUBLE PRECISION,
    p_lon DOUBLE PRECISION,
    p_location_name TEXT
) RETURNS UUID AS $$
DECLARE
    v_connection_id UUID;
BEGIN
    INSERT INTO connections (
        created,
        expiry,
        geo_location,
        semantic_location,
        user_ids,
        should_continue
    ) VALUES (
        EXTRACT(EPOCH FROM NOW())::bigint * 1000,
        EXTRACT(EPOCH FROM NOW() + INTERVAL '30 days')::bigint * 1000,
        jsonb_build_object('lat', p_lat, 'lon', p_lon),
        p_location_name,
        ARRAY[p_user1_id::text, p_user2_id::text],
        ARRAY[false, false]
    ) RETURNING id INTO v_connection_id;

    RETURN v_connection_id;
END;
$$ LANGUAGE plpgsql;

-- Comments for clarity
COMMENT ON COLUMN connections.geo_location IS 'Geographic coordinates stored as {"lat": 40.7580, "lon": -73.9855}';
COMMENT ON COLUMN connections.full_location IS 'Full location data from geocoding service';
COMMENT ON COLUMN connections.semantic_location IS 'Human-readable location name like "Central Park, NYC"';
COMMENT ON COLUMN connections.user_ids IS 'Array of user IDs in the connection (replaces user1_id, user2_id)';
COMMENT ON COLUMN connections.should_continue IS 'Array of booleans indicating if each user wants to continue';
COMMENT ON COLUMN messages.time_created IS 'Unix timestamp in milliseconds when message was created';
COMMENT ON COLUMN messages.time_edited IS 'Unix timestamp in milliseconds when message was last edited';

