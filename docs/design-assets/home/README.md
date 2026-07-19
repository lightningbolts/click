# Home design asset — mock vs product

**Mock:** [`code.html`](code.html)  
**Product truth:** [`../../ui-ux/mobile/05-home.md`](../../ui-ux/mobile/05-home.md)

## Use this mock for

- Greeting-first hierarchy (no competing `"Home"` app-bar title)
- Prominent search entry between greeting and hero
- Single featured **event** hero rhythm (image/time badge/CTA)
- Generous section spacing and bordered Functional Clarity surfaces

## Do not ship from this mock

| Mock artifact | Product |
|---------------|---------|
| **"Featured Click"** | **Featured Event** — driven by `homeEventReminders` |
| Hardcoded Networking / Social / Workshop / Co-working | **Explore nearby** tiles from live `MapBeaconKind` / Hub counts only |
| Floating white pill bottom nav | Transparent platform overlay nav (Track A / #23) |
| Stock hero photo | Tonal placeholder (beacons lack cover images) |

Availability intents and Reconnect remain first-class on Home (above Explore), even though the HTML mock emphasizes discovery.
