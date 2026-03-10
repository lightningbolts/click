ALTER TABLE connections
  ADD COLUMN IF NOT EXISTS initiator_id TEXT,
  ADD COLUMN IF NOT EXISTS responder_id TEXT,
  ADD COLUMN IF NOT EXISTS memory_capsule JSONB,
  ADD COLUMN IF NOT EXISTS noise_level TEXT,
  ADD COLUMN IF NOT EXISTS height_category TEXT,
  ADD COLUMN IF NOT EXISTS weather_condition TEXT,
  ADD COLUMN IF NOT EXISTS context_tag_id TEXT;

CREATE INDEX IF NOT EXISTS idx_connections_memory_capsule
  ON connections USING GIN (memory_capsule);

CREATE INDEX IF NOT EXISTS idx_conn_noise
  ON connections USING BTREE (noise_level);

CREATE INDEX IF NOT EXISTS idx_conn_height
  ON connections USING BTREE (height_category);

CREATE INDEX IF NOT EXISTS idx_conn_weather
  ON connections USING BTREE (weather_condition);

CREATE INDEX IF NOT EXISTS idx_conn_context_tag
  ON connections USING BTREE (context_tag_id);