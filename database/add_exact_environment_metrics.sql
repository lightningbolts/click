ALTER TABLE connections
  ADD COLUMN IF NOT EXISTS exact_noise_level_db DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS exact_barometric_elevation_m DOUBLE PRECISION;

CREATE INDEX IF NOT EXISTS idx_conn_exact_noise
  ON connections USING BTREE (exact_noise_level_db);

CREATE INDEX IF NOT EXISTS idx_conn_exact_barometric
  ON connections USING BTREE (exact_barometric_elevation_m);