export type PublicEventListItem = {
  beacon_id: string;
  title: string | null;
  description: string | null;
  image_url: string | null;
  host_name: string | null;
  event_start_at: string | null;
  event_end_at: string | null;
  location_name: string | null;
  latitude: number | null;
  longitude: number | null;
  rsvp_count: number;
  rsvp_enabled: boolean;
  timezone: string | null;
};

export type PublicEventDetail = PublicEventListItem & {
  expires_at: string | null;
  created_at: string | null;
  cover_theme_id: string | null;
};

export class ClickApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ClickApiError";
  }
}

export class ClickApi {
  constructor(
    private readonly baseUrl: string,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  private url(path: string): string {
    return `${this.baseUrl.replace(/\/$/, "")}${path}`;
  }

  async listPublicEvents(): Promise<PublicEventListItem[]> {
    const res = await this.fetchImpl(this.url("/api/beacons/public-events"), {
      headers: { Accept: "application/json" },
    });
    if (!res.ok) {
      throw new ClickApiError(`public-events failed: ${res.status}`, res.status);
    }
    const body = (await res.json()) as { events?: PublicEventListItem[] };
    return Array.isArray(body.events) ? body.events : [];
  }

  async getPublicEvent(beaconId: string): Promise<PublicEventDetail> {
    const res = await this.fetchImpl(
      this.url(`/api/beacons/${encodeURIComponent(beaconId)}/public`),
      { headers: { Accept: "application/json" } },
    );
    if (!res.ok) {
      throw new ClickApiError(`public event failed: ${res.status}`, res.status);
    }
    return (await res.json()) as PublicEventDetail;
  }

  async guestRsvp(
    beaconId: string,
    name: string,
    contact: string,
  ): Promise<void> {
    const res = await this.fetchImpl(
      this.url(`/api/beacons/${encodeURIComponent(beaconId)}/rsvp/guest`),
      {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ name, contact }),
      },
    );
    if (!res.ok) {
      let detail = `guest RSVP failed: ${res.status}`;
      try {
        const body = (await res.json()) as { error?: string };
        if (body.error) detail = body.error;
      } catch {
        /* ignore */
      }
      throw new ClickApiError(detail, res.status);
    }
  }

  eventShareUrl(beaconId: string): string {
    return `${this.baseUrl.replace(/\/$/, "")}/e/${beaconId}`;
  }
}
