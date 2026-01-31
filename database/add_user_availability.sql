-- Migration to add user_availability table
-- Run this in the Supabase SQL Editor

-- Create user_availability table if not exists
CREATE TABLE IF NOT EXISTS user_availability (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE UNIQUE,
    is_free_this_week BOOLEAN DEFAULT false,
    available_days TEXT[] DEFAULT '{}',
    preferred_activities TEXT[] DEFAULT '{}',
    custom_status TEXT,
    last_updated BIGINT DEFAULT 0
);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_user_availability_user_id ON user_availability(user_id);

-- Enable Row Level Security
ALTER TABLE user_availability ENABLE ROW LEVEL SECURITY;

-- Drop existing policies if they exist (to avoid duplicates)
DROP POLICY IF EXISTS "Users can view their own availability" ON user_availability;
DROP POLICY IF EXISTS "Users can insert their own availability" ON user_availability;
DROP POLICY IF EXISTS "Users can update their own availability" ON user_availability;
DROP POLICY IF EXISTS "Users can view availability of connections" ON user_availability;

-- Policy: Users can view their own availability
CREATE POLICY "Users can view their own availability"
    ON user_availability FOR SELECT
    USING (auth.uid() = user_id);

-- Policy: Users can insert their own availability
CREATE POLICY "Users can insert their own availability"
    ON user_availability FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- Policy: Users can update their own availability
CREATE POLICY "Users can update their own availability"
    ON user_availability FOR UPDATE
    USING (auth.uid() = user_id);

-- Policy: Users can also view availability of their connections (for mutual availability features)
CREATE POLICY "Users can view availability of connections"
    ON user_availability FOR SELECT
    USING (
        user_id IN (
            SELECT unnest(user_ids)::uuid FROM connections WHERE auth.uid()::text = ANY(user_ids)
        )
    );

-- Update users table RLS to allow updating own record
DROP POLICY IF EXISTS "Users can update their own profile" ON users;
CREATE POLICY "Users can update their own profile"
    ON users FOR UPDATE
    USING (auth.uid() = id);

-- Allow users to insert their own record (for upsert)
DROP POLICY IF EXISTS "Users can insert their own profile" ON users;
CREATE POLICY "Users can insert their own profile"
    ON users FOR INSERT
    WITH CHECK (auth.uid() = id);

-- Users can view their own profile
DROP POLICY IF EXISTS "Users can view their own profile" ON users;
CREATE POLICY "Users can view their own profile"
    ON users FOR SELECT
    USING (auth.uid() = id);

-- Grant permissions
GRANT ALL ON user_availability TO authenticated;
GRANT ALL ON users TO authenticated;

-- Enable realtime for user_availability
ALTER PUBLICATION supabase_realtime ADD TABLE user_availability;

-- Comments for clarity
COMMENT ON TABLE user_availability IS 'Stores user availability preferences for the Free Currently feature';
COMMENT ON COLUMN user_availability.is_free_this_week IS 'Whether the user is currently free to meet up';
COMMENT ON COLUMN user_availability.available_days IS 'List of days the user is available';
COMMENT ON COLUMN user_availability.preferred_activities IS 'List of activities the user prefers';
