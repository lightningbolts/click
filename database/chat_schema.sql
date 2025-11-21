-- Click Chat Database Schema
-- This script creates the necessary tables and policies for the chat functionality

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users table (if not already exists)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    email TEXT UNIQUE NOT NULL,
    image TEXT,
    share_key BIGINT DEFAULT 0,
    connections TEXT[] DEFAULT '{}',
    created_at BIGINT NOT NULL,
    updated_at BIGINT
);

-- Connections table
CREATE TABLE IF NOT EXISTS connections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user1_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user2_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chat_id UUID,
    location TEXT,
    created BIGINT NOT NULL,
    expiry BIGINT,
    should_continue BOOLEAN DEFAULT false,
    CONSTRAINT unique_connection UNIQUE(user1_id, user2_id)
);

-- Chats table
CREATE TABLE IF NOT EXISTS chats (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    connection_id UUID NOT NULL REFERENCES connections(id) ON DELETE CASCADE,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

-- Messages table
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT,
    is_read BOOLEAN DEFAULT false
);

-- Add foreign key to connections table for chat_id
ALTER TABLE connections
    ADD CONSTRAINT fk_chat
    FOREIGN KEY (chat_id)
    REFERENCES chats(id)
    ON DELETE SET NULL;

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_connections_user1 ON connections(user1_id);
CREATE INDEX IF NOT EXISTS idx_connections_user2 ON connections(user2_id);
CREATE INDEX IF NOT EXISTS idx_messages_chat ON messages(chat_id);
CREATE INDEX IF NOT EXISTS idx_messages_created ON messages(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_unread ON messages(chat_id, is_read) WHERE is_read = false;
CREATE INDEX IF NOT EXISTS idx_chats_connection ON chats(connection_id);

-- Enable Row Level Security
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE chats ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;

-- Users policies
CREATE POLICY "Users can view their own profile"
    ON users FOR SELECT
    USING (auth.uid() = id);

CREATE POLICY "Users can view profiles of their connections"
    ON users FOR SELECT
    USING (
        id IN (
            SELECT user1_id FROM connections WHERE user2_id = auth.uid()
            UNION
            SELECT user2_id FROM connections WHERE user1_id = auth.uid()
        )
    );

CREATE POLICY "Users can update their own profile"
    ON users FOR UPDATE
    USING (auth.uid() = id);

-- Connections policies
CREATE POLICY "Users can view their own connections"
    ON connections FOR SELECT
    USING (user1_id = auth.uid() OR user2_id = auth.uid());

CREATE POLICY "Users can create connections"
    ON connections FOR INSERT
    WITH CHECK (user1_id = auth.uid() OR user2_id = auth.uid());

CREATE POLICY "Users can update their own connections"
    ON connections FOR UPDATE
    USING (user1_id = auth.uid() OR user2_id = auth.uid());

-- Chats policies
CREATE POLICY "Users can view chats for their connections"
    ON chats FOR SELECT
    USING (
        connection_id IN (
            SELECT id FROM connections
            WHERE user1_id = auth.uid() OR user2_id = auth.uid()
        )
    );

CREATE POLICY "Users can create chats for their connections"
    ON chats FOR INSERT
    WITH CHECK (
        connection_id IN (
            SELECT id FROM connections
            WHERE user1_id = auth.uid() OR user2_id = auth.uid()
        )
    );

CREATE POLICY "Users can update chats for their connections"
    ON chats FOR UPDATE
    USING (
        connection_id IN (
            SELECT id FROM connections
            WHERE user1_id = auth.uid() OR user2_id = auth.uid()
        )
    );

-- Messages policies
CREATE POLICY "Users can view messages in their chats"
    ON messages FOR SELECT
    USING (
        chat_id IN (
            SELECT c.id FROM chats c
            JOIN connections conn ON c.connection_id = conn.id
            WHERE conn.user1_id = auth.uid() OR conn.user2_id = auth.uid()
        )
    );

CREATE POLICY "Users can create messages in their chats"
    ON messages FOR INSERT
    WITH CHECK (
        user_id = auth.uid() AND
        chat_id IN (
            SELECT c.id FROM chats c
            JOIN connections conn ON c.connection_id = conn.id
            WHERE conn.user1_id = auth.uid() OR conn.user2_id = auth.uid()
        )
    );

CREATE POLICY "Users can update their own messages"
    ON messages FOR UPDATE
    USING (user_id = auth.uid());

CREATE POLICY "Users can mark messages as read in their chats"
    ON messages FOR UPDATE
    USING (
        chat_id IN (
            SELECT c.id FROM chats c
            JOIN connections conn ON c.connection_id = conn.id
            WHERE conn.user1_id = auth.uid() OR conn.user2_id = auth.uid()
        )
    );

-- Function to automatically create a chat when a connection is created
CREATE OR REPLACE FUNCTION create_chat_for_connection()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO chats (connection_id, created_at, updated_at)
    VALUES (NEW.id, NEW.created, NEW.created)
    RETURNING id INTO NEW.chat_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to automatically create chat
CREATE TRIGGER trigger_create_chat_for_connection
    BEFORE INSERT ON connections
    FOR EACH ROW
    WHEN (NEW.chat_id IS NULL)
    EXECUTE FUNCTION create_chat_for_connection();

-- Enable Realtime for messages table
ALTER PUBLICATION supabase_realtime ADD TABLE messages;
ALTER PUBLICATION supabase_realtime ADD TABLE chats;

-- Add message status column (if not exists)
ALTER TABLE messages ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'sent';
CREATE INDEX IF NOT EXISTS idx_messages_status ON messages(status);

-- Message reactions table
CREATE TABLE IF NOT EXISTS message_reactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reaction_type VARCHAR(50) NOT NULL,
    created_at BIGINT NOT NULL,
    CONSTRAINT unique_reaction UNIQUE(message_id, user_id, reaction_type)
);
CREATE INDEX IF NOT EXISTS idx_reactions_message ON message_reactions(message_id);
CREATE INDEX IF NOT EXISTS idx_reactions_user ON message_reactions(user_id);

-- Typing events table (ephemeral typing indicators)
CREATE TABLE IF NOT EXISTS typing_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    chat_id UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    updated_at BIGINT NOT NULL,
    CONSTRAINT unique_typing UNIQUE(chat_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_typing_chat ON typing_events(chat_id);
CREATE INDEX IF NOT EXISTS idx_typing_updated ON typing_events(updated_at);

-- RLS Policies for reactions
ALTER TABLE message_reactions ENABLE ROW LEVEL SECURITY;
CREATE POLICY IF NOT EXISTS "Users can view reactions in their chats" ON message_reactions FOR SELECT USING (
    message_id IN (
        SELECT m.id FROM messages m
        JOIN chats c ON m.chat_id = c.id
        JOIN connections conn ON c.connection_id = conn.id
        WHERE conn.user1_id = auth.uid() OR conn.user2_id = auth.uid()
    )
);
CREATE POLICY IF NOT EXISTS "Users can add reactions to their chat messages" ON message_reactions FOR INSERT WITH CHECK (
    user_id = auth.uid() AND message_id IN (
        SELECT m.id FROM messages m
        JOIN chats c ON m.chat_id = c.id
        JOIN connections conn ON c.connection_id = conn.id
        WHERE conn.user1_id = auth.uid() OR conn.user2_id = auth.uid()
    )
);
CREATE POLICY IF NOT EXISTS "Users can remove their own reactions" ON message_reactions FOR DELETE USING (user_id = auth.uid());

-- RLS Policies for typing events
ALTER TABLE typing_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY IF NOT EXISTS "Users can view typing events in their chats" ON typing_events FOR SELECT USING (
    chat_id IN (
        SELECT c.id FROM chats c
        JOIN connections conn ON c.connection_id = conn.id
        WHERE conn.user1_id = auth.uid() OR conn.user2_id = auth.uid()
    )
);
CREATE POLICY IF NOT EXISTS "Users can create typing events in their chats" ON typing_events FOR INSERT WITH CHECK (
    user_id = auth.uid() AND chat_id IN (
        SELECT c.id FROM chats c
        JOIN connections conn ON c.connection_id = conn.id
        WHERE conn.user1_id = auth.uid() OR conn.user2_id = auth.uid()
    )
);
CREATE POLICY IF NOT EXISTS "Users can update typing events in their chats" ON typing_events FOR UPDATE USING (
    user_id = auth.uid() AND chat_id IN (
        SELECT c.id FROM chats c
        JOIN connections conn ON c.connection_id = conn.id
        WHERE conn.user1_id = auth.uid() OR conn.user2_id = auth.uid()
    )
);

-- Enable realtime for new tables
ALTER PUBLICATION supabase_realtime ADD TABLE message_reactions;
ALTER PUBLICATION supabase_realtime ADD TABLE typing_events;

-- Grant permissions
GRANT ALL ON users TO authenticated;
GRANT ALL ON connections TO authenticated;
GRANT ALL ON chats TO authenticated;
GRANT ALL ON messages TO authenticated;
GRANT ALL ON message_reactions TO authenticated;
GRANT ALL ON typing_events TO authenticated;
