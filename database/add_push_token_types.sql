ALTER TABLE push_tokens
ADD COLUMN IF NOT EXISTS token_type TEXT NOT NULL DEFAULT 'standard'
CHECK (token_type IN ('standard', 'voip'));

CREATE INDEX IF NOT EXISTS idx_push_tokens_token_type ON push_tokens(token_type);