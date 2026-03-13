-- Migration: Location privacy preferences on users table
-- Run in Supabase SQL Editor. Adds columns for granular location opt-in and Memory Map controls.

-- Add location preference columns (all default true for backward compatibility)
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS location_connection_snap_enabled BOOLEAN DEFAULT false,
  ADD COLUMN IF NOT EXISTS location_show_on_map_enabled BOOLEAN DEFAULT false,
  ADD COLUMN IF NOT EXISTS location_include_in_insights_enabled BOOLEAN DEFAULT false;

ALTER TABLE users
  ALTER COLUMN location_connection_snap_enabled SET DEFAULT false,
  ALTER COLUMN location_show_on_map_enabled SET DEFAULT false,
  ALTER COLUMN location_include_in_insights_enabled SET DEFAULT false;

-- Mark whether a specific connection location may contribute to anonymized venue/campus insights.
-- This keeps "show on my Memory Map" independent from "include in business insights".
ALTER TABLE connections
  ADD COLUMN IF NOT EXISTS include_in_business_insights BOOLEAN DEFAULT false;

ALTER TABLE connections
  ALTER COLUMN include_in_business_insights SET DEFAULT false;

UPDATE users
SET
  location_connection_snap_enabled = COALESCE(location_connection_snap_enabled, false),
  location_show_on_map_enabled = COALESCE(location_show_on_map_enabled, false),
  location_include_in_insights_enabled = COALESCE(location_include_in_insights_enabled, false)
WHERE
  location_connection_snap_enabled IS NULL
  OR location_show_on_map_enabled IS NULL
  OR location_include_in_insights_enabled IS NULL;

UPDATE connections
SET include_in_business_insights = COALESCE(include_in_business_insights, false)
WHERE include_in_business_insights IS NULL;

COMMENT ON COLUMN users.location_connection_snap_enabled IS 'Records GPS at moment of tap; disabled by default until the user opts in.';
COMMENT ON COLUMN users.location_show_on_map_enabled IS 'Show connection locations on the user''s personal Memory Map only; disabled by default until the user opts in.';
COMMENT ON COLUMN users.location_include_in_insights_enabled IS 'Include anonymized location in B2B/campus/venue insights; disabled by default until the user opts in.';
COMMENT ON COLUMN connections.include_in_business_insights IS 'Whether this connection may contribute to anonymized business insights aggregations; false by default.';
