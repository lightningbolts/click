// Supabase Edge Function: verify-hub-proximity
// POST JSON body: { hub_id: string, user_lat: number, user_long: number }
// Uses Haversine distance vs public.hub_venues; 403 if outside radius_meters (default 50).

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.83.0";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ??
  Deno.env.get("SUPABASE_SERVICE_KEY")!;

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

function haversineMeters(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number,
): number {
  const R = 6371000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

function jsonResponse(
  body: unknown,
  status: number,
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse({ error: "Method not allowed" }, 405);
  }

  try {
    let payload: {
      hub_id?: string;
      user_lat?: number;
      user_long?: number;
    };
    try {
      payload = await req.json();
    } catch {
      return jsonResponse({ error: "Invalid JSON body" }, 400);
    }

    const hubId =
      typeof payload.hub_id === "string" ? payload.hub_id.trim() : "";
    const userLat = payload.user_lat;
    const userLong = payload.user_long;

    if (!hubId) {
      return jsonResponse({ error: "hub_id is required" }, 400);
    }
    if (
      typeof userLat !== "number" ||
      typeof userLong !== "number" ||
      Number.isNaN(userLat) ||
      Number.isNaN(userLong)
    ) {
      return jsonResponse(
        { error: "user_lat and user_long must be numbers" },
        400,
      );
    }
    if (userLat < -90 || userLat > 90 || userLong < -180 || userLong > 180) {
      return jsonResponse({ error: "Coordinates out of range" }, 400);
    }

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY, {
      auth: { autoRefreshToken: false, persistSession: false },
    });

    const { data: venue, error } = await supabase
      .from("hub_venues")
      .select("id, name, geofence_lat, geofence_long, radius_meters")
      .eq("id", hubId)
      .maybeSingle();

    if (error) {
      console.error("hub_venues query error:", error.message);
      return jsonResponse({ error: "Lookup failed" }, 500);
    }
    if (!venue) {
      return jsonResponse({ error: "Unknown hub" }, 404);
    }

    const radius =
      typeof venue.radius_meters === "number" && venue.radius_meters > 0
        ? venue.radius_meters
        : 50;

    const distanceM = haversineMeters(
      userLat,
      userLong,
      venue.geofence_lat,
      venue.geofence_long,
    );

    if (distanceM > radius) {
      return jsonResponse(
        {
          error: "Outside hub geofence",
          distance_meters: Math.round(distanceM * 100) / 100,
          radius_meters: radius,
        },
        403,
      );
    }

    const channel = `hub:${venue.id}`;
    return jsonResponse({
      success: true,
      hub_id: venue.id,
      name: venue.name,
      channel,
      distance_meters: Math.round(distanceM * 100) / 100,
      radius_meters: radius,
    }, 200);
  } catch (e) {
    console.error("verify-hub-proximity fatal:", e);
    return jsonResponse({ error: String(e) }, 500);
  }
});
