export type GeocodeResult = {
  latitude: number;
  longitude: number;
  displayName: string;
};

export function normalizeUsZip(raw: string): string | null {
  const trimmed = raw.trim();
  // Allow "94107" or "ZIP 94107 please" — take first 5-digit run.
  const match = trimmed.match(/\b(\d{5})(?:-\d{4})?\b/);
  return match?.[1] ?? null;
}

/**
 * Geocode a US ZIP via OpenStreetMap Nominatim (no API key).
 * Callers should cache results on the phone profile.
 */
export async function geocodeUsZip(
  zip: string,
  fetchImpl: typeof fetch = fetch,
): Promise<GeocodeResult | { error: string }> {
  const normalized = normalizeUsZip(zip);
  if (!normalized) {
    return { error: "Please send a 5-digit US ZIP code." };
  }

  const url = new URL("https://nominatim.openstreetmap.org/search");
  url.searchParams.set("postalcode", normalized);
  url.searchParams.set("country", "US");
  url.searchParams.set("format", "json");
  url.searchParams.set("limit", "1");

  const res = await fetchImpl(url.toString(), {
    headers: {
      Accept: "application/json",
      "User-Agent": "click-messaging-agent/0.1 (event discovery bot)",
    },
  });
  if (!res.ok) {
    return { error: `Geocoder failed (${res.status}). Try again shortly.` };
  }
  const data = (await res.json()) as Array<{
    lat?: string;
    lon?: string;
    display_name?: string;
  }>;
  const first = data[0];
  if (!first?.lat || !first?.lon) {
    return { error: `Couldn't find ZIP ${normalized}. Double-check and try again.` };
  }
  const latitude = Number(first.lat);
  const longitude = Number(first.lon);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return { error: `Couldn't find ZIP ${normalized}.` };
  }
  return {
    latitude,
    longitude,
    displayName: first.display_name ?? normalized,
  };
}
