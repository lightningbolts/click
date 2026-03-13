-- Migration: Location privacy preferences on users table
-- Run in Supabase SQL Editor. Adds columns for granular location opt-in and Memory Map controls.

-- Add location preference columns (all default true for opt-out behavior)
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS location_connection_snap_enabled BOOLEAN DEFAULT true,
  ADD COLUMN IF NOT EXISTS location_show_on_map_enabled BOOLEAN DEFAULT true,
  ADD COLUMN IF NOT EXISTS location_include_in_insights_enabled BOOLEAN DEFAULT true;

ALTER TABLE users
  ALTER COLUMN location_connection_snap_enabled SET DEFAULT true,
  ALTER COLUMN location_show_on_map_enabled SET DEFAULT true,
  ALTER COLUMN location_include_in_insights_enabled SET DEFAULT true;

-- Mark whether a specific connection location may contribute to anonymized venue/campus insights.
-- This keeps "show on my Memory Map" independent from "include in business insights".
ALTER TABLE connections
  ADD COLUMN IF NOT EXISTS include_in_business_insights BOOLEAN DEFAULT true;

ALTER TABLE connections
  ALTER COLUMN include_in_business_insights SET DEFAULT true;

UPDATE users
SET
  location_connection_snap_enabled = COALESCE(location_connection_snap_enabled, true),
  location_show_on_map_enabled = COALESCE(location_show_on_map_enabled, true),
  location_include_in_insights_enabled = COALESCE(location_include_in_insights_enabled, true)
WHERE
  location_connection_snap_enabled IS NULL
  OR location_show_on_map_enabled IS NULL
  OR location_include_in_insights_enabled IS NULL;

UPDATE connections
SET include_in_business_insights = COALESCE(include_in_business_insights, true)
WHERE include_in_business_insights IS NULL;

COMMENT ON COLUMN users.location_connection_snap_enabled IS 'Records GPS at moment of tap; enabled by default until the user opts out.';
COMMENT ON COLUMN users.location_show_on_map_enabled IS 'Show connection locations on the user''s personal Memory Map only; enabled by default until the user opts out.';
COMMENT ON COLUMN users.location_include_in_insights_enabled IS 'Include anonymized location in B2B/campus/venue insights; enabled by default until the user opts out.';
COMMENT ON COLUMN connections.include_in_business_insights IS 'Whether this connection may contribute to anonymized business insights aggregations; true by default unless the user opted out.';
