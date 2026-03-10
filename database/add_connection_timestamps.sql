ALTER TABLE connections
  ADD COLUMN IF NOT EXISTS created_utc TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS time_of_day_utc TEXT;

UPDATE connections
SET
  created_utc = COALESCE(created_utc, TO_TIMESTAMP(created / 1000.0) AT TIME ZONE 'UTC'),
  time_of_day_utc = COALESCE(
    time_of_day_utc,
    TO_CHAR(TO_TIMESTAMP(created / 1000.0) AT TIME ZONE 'UTC', 'HH24:MI:SS "UTC"')
  )
WHERE created_utc IS NULL OR time_of_day_utc IS NULL;

COMMENT ON COLUMN connections.created_utc IS 'UTC timestamp for the connection creation time';
COMMENT ON COLUMN connections.time_of_day_utc IS 'UTC time-of-day label derived from the connection creation time';