-- ============================================================================
-- Proximity Verification System Migration
-- Adds: qr_tokens table, connections proximity columns, atomic redemption RPC,
--        anomaly detection trigger
-- ============================================================================

-- 1. QR Tokens table — single-use, time-bounded tokens for QR codes
CREATE TABLE IF NOT EXISTS public.qr_tokens (
    token TEXT PRIMARY KEY,
    user_id UUID NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,  -- created_at + 90_000 (90 seconds in ms)
    redeemed BOOLEAN DEFAULT false,
    initiator_lat DOUBLE PRECISION,  -- GPS lat of the user who generated the QR
    initiator_lon DOUBLE PRECISION   -- GPS lon of the user who generated the QR
);

-- Back-fill existing tables that lack the columns
ALTER TABLE public.qr_tokens
    ADD COLUMN IF NOT EXISTS initiator_lat DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS initiator_lon DOUBLE PRECISION;

-- Index for cleanup cron and user lookups
CREATE INDEX IF NOT EXISTS idx_qr_tokens_user_id ON public.qr_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_qr_tokens_expires_at ON public.qr_tokens(expires_at);

-- Enable RLS on qr_tokens
ALTER TABLE public.qr_tokens ENABLE ROW LEVEL SECURITY;

-- Authenticated users can insert their own tokens
CREATE POLICY "Users can insert own tokens" ON public.qr_tokens
    FOR INSERT TO authenticated
    WITH CHECK (user_id = auth.uid());

-- Authenticated users can read any token (needed for redemption)
CREATE POLICY "Users can read tokens" ON public.qr_tokens
    FOR SELECT TO authenticated
    USING (true);

-- 2. Alter connections table with proximity verification columns
ALTER TABLE public.connections
    ADD COLUMN IF NOT EXISTS proximity_confidence INTEGER DEFAULT 100,
    ADD COLUMN IF NOT EXISTS proximity_signals JSONB DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS connection_method TEXT DEFAULT 'qr',
    ADD COLUMN IF NOT EXISTS flagged BOOLEAN DEFAULT false;

-- 3. Atomic token redemption RPC with proximity enforcement
-- Uses FOR UPDATE to prevent race conditions (two simultaneous scans).
-- When both the initiator and scanner have GPS, rejects if they are
-- more than 100 m apart (Haversine). The token is NOT consumed on
-- proximity rejection so the initiator can retry in person.
CREATE OR REPLACE FUNCTION public.redeem_qr_token(
    p_token TEXT,
    p_scanner_lat DOUBLE PRECISION DEFAULT NULL,
    p_scanner_lon DOUBLE PRECISION DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_record RECORD;
    v_now BIGINT;
    v_distance_m DOUBLE PRECISION;
    v_max_distance_m CONSTANT DOUBLE PRECISION := 100.0;  -- 100-meter threshold
BEGIN
    v_now := (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT;

    -- Lock the row for atomic update
    SELECT token, user_id, created_at, expires_at, redeemed,
           initiator_lat, initiator_lon
    INTO v_record
    FROM public.qr_tokens
    WHERE token = p_token
    FOR UPDATE;

    -- Token not found
    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'not_found');
    END IF;

    -- Already redeemed
    IF v_record.redeemed THEN
        RETURN jsonb_build_object('success', false, 'error', 'already_used');
    END IF;

    -- Expired
    IF v_now > v_record.expires_at THEN
        RETURN jsonb_build_object('success', false, 'error', 'expired');
    END IF;

    -- ── Proximity gate ──
    -- Only enforced when BOTH sides have valid GPS coordinates.
    -- (0,0) is treated as missing/sentinel.
    IF  v_record.initiator_lat IS NOT NULL
        AND v_record.initiator_lon IS NOT NULL
        AND p_scanner_lat IS NOT NULL
        AND p_scanner_lon IS NOT NULL
        AND NOT (v_record.initiator_lat = 0 AND v_record.initiator_lon = 0)
        AND NOT (p_scanner_lat = 0 AND p_scanner_lon = 0)
    THEN
        -- Haversine distance in meters
        v_distance_m := 6371000.0 * 2.0 * ASIN(SQRT(
            POWER(SIN(RADIANS(p_scanner_lat - v_record.initiator_lat) / 2.0), 2)
            + COS(RADIANS(v_record.initiator_lat))
              * COS(RADIANS(p_scanner_lat))
              * POWER(SIN(RADIANS(p_scanner_lon - v_record.initiator_lon) / 2.0), 2)
        ));

        IF v_distance_m > v_max_distance_m THEN
            -- Do NOT mark the token as redeemed — allow retry when in person
            RETURN jsonb_build_object(
                'success', false,
                'error', 'proximity_failed',
                'distance_meters', ROUND(v_distance_m)::INTEGER
            );
        END IF;
    END IF;

    -- Mark as redeemed
    UPDATE public.qr_tokens
    SET redeemed = true
    WHERE token = p_token;

    -- Return success with user_id and token age
    RETURN jsonb_build_object(
        'success', true,
        'user_id', v_record.user_id,
        'token_age_ms', v_now - v_record.created_at
    );
END;
$$;

-- 4. Anomaly detection trigger function
-- Runs on each connection INSERT to flag suspicious patterns
CREATE OR REPLACE FUNCTION public.check_connection_anomalies()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_user_id TEXT;
    v_velocity_count INTEGER;
    v_star_count INTEGER;
    v_now BIGINT;
    v_ten_minutes_ago BIGINT;
    v_one_hour_ago BIGINT;
    v_twenty_four_hours_ago BIGINT;
    v_should_flag BOOLEAN := false;
    v_flag_reasons TEXT[] := '{}';
BEGIN
    v_now := (EXTRACT(EPOCH FROM now()) * 1000)::BIGINT;
    v_ten_minutes_ago := v_now - (10 * 60 * 1000);
    v_one_hour_ago := v_now - (60 * 60 * 1000);
    v_twenty_four_hours_ago := v_now - (24 * 60 * 60 * 1000);

    -- Check each user in the connection
    FOREACH v_user_id IN ARRAY NEW.user_ids
    LOOP
        -- Velocity check: > 20 connections in 10 minutes
        SELECT COUNT(*) INTO v_velocity_count
        FROM public.connections
        WHERE user_ids @> ARRAY[v_user_id]
          AND created > v_ten_minutes_ago;

        IF v_velocity_count > 20 THEN
            v_should_flag := true;
            v_flag_reasons := array_append(v_flag_reasons,
                'velocity:' || v_user_id || ':' || v_velocity_count || '_in_10min');
        END IF;

        -- Star topology: > 100 connections in 24 hours
        SELECT COUNT(*) INTO v_star_count
        FROM public.connections
        WHERE user_ids @> ARRAY[v_user_id]
          AND created > v_twenty_four_hours_ago;

        IF v_star_count > 100 THEN
            v_should_flag := true;
            v_flag_reasons := array_append(v_flag_reasons,
                'star_topology:' || v_user_id || ':' || v_star_count || '_in_24hr');
        END IF;

        -- Geographic impossibility: connections 500km+ apart within 1 hour
        -- Uses simple lat/lon degree approximation (1 degree ≈ 111km)
        -- 500km ≈ 4.5 degrees
        PERFORM 1
        FROM public.connections c
        WHERE c.user_ids @> ARRAY[v_user_id]
          AND c.created > v_one_hour_ago
          AND c.id != NEW.id
          AND c.geo_location IS NOT NULL
          AND NEW.geo_location IS NOT NULL
          AND (
            ABS((c.geo_location->>'lat')::FLOAT - (NEW.geo_location->>'lat')::FLOAT) > 4.5
            OR ABS((c.geo_location->>'lon')::FLOAT - (NEW.geo_location->>'lon')::FLOAT) > 4.5
          )
        LIMIT 1;

        IF FOUND THEN
            v_should_flag := true;
            v_flag_reasons := array_append(v_flag_reasons,
                'geo_impossibility:' || v_user_id);
        END IF;
    END LOOP;

    -- Flag the connection if anomalies detected
    IF v_should_flag THEN
        NEW.flagged := true;
        NEW.proximity_signals := NEW.proximity_signals || jsonb_build_object(
            'anomaly_flags', v_flag_reasons
        );
    END IF;

    RETURN NEW;
END;
$$;

-- Create the trigger (drop first if exists to allow re-running)
DROP TRIGGER IF EXISTS trg_check_connection_anomalies ON public.connections;
CREATE TRIGGER trg_check_connection_anomalies
    BEFORE INSERT ON public.connections
    FOR EACH ROW
    EXECUTE FUNCTION public.check_connection_anomalies();
