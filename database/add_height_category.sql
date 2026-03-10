ALTER TABLE connections
  ADD COLUMN IF NOT EXISTS height_category TEXT;

CREATE INDEX IF NOT EXISTS idx_conn_height
  ON connections USING BTREE (height_category);