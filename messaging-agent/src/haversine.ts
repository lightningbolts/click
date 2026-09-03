const EARTH_RADIUS_KM = 6371;

function toRad(deg: number): number {
  return (deg * Math.PI) / 180;
}

/** Great-circle distance in kilometers. */
export function haversineKm(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number,
): number {
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
}

export type Locatable = {
  latitude: number | null;
  longitude: number | null;
};

export function filterWithinRadiusKm<T extends Locatable>(
  items: T[],
  lat: number,
  lon: number,
  radiusKm: number,
): Array<T & { distance_km: number }> {
  const out: Array<T & { distance_km: number }> = [];
  for (const item of items) {
    if (item.latitude == null || item.longitude == null) continue;
    if (!Number.isFinite(item.latitude) || !Number.isFinite(item.longitude)) {
      continue;
    }
    const distance_km = haversineKm(lat, lon, item.latitude, item.longitude);
    if (distance_km <= radiusKm) {
      out.push({ ...item, distance_km });
    }
  }
  return out;
}
