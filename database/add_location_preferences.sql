-- Migration: Location privacy preferences on users table
-- Run in Supabase SQL Editor. Adds columns for granular location opt-in and Memory Map controls.

-- Add location preference columns (all default true for backward compatibility)
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS location_connection_snap_enabled BOOLEAN DEFAULT true,
  ADD COLUMN IF NOT EXISTS location_show_on_map_enabled BOOLEAN DEFAULT true,
  ADD COLUMN IF NOT EXISTS location_include_in_insights_enabled BOOLEAN DEFAULT true;

-- Mark whether a specific connection location may contribute to anonymized venue/campus insights.
-- This keeps "show on my Memory Map" independent from "include in business insights".
ALTER TABLE connections
  ADD COLUMN IF NOT EXISTS include_in_business_insights BOOLEAN DEFAULT true;

COMMENT ON COLUMN users.location_connection_snap_enabled IS 'Records GPS at moment of tap; when false, no location is captured.';
COMMENT ON COLUMN users.location_show_on_map_enabled IS 'Show connection locations on the user''s personal Memory Map only; never shared.';
COMMENT ON COLUMN users.location_include_in_insights_enabled IS 'Include anonymized location in B2B/campus/venue insights; no PII.';
COMMENT ON COLUMN connections.include_in_business_insights IS 'Whether this connection may contribute to anonymized business insights aggregations.';
