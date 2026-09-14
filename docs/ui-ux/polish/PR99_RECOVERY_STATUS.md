# PR #99 recovery status

Legend: `IMPLEMENTED` means code changed and automated validation is still required. `DEVICE` means physical-device validation is required before completion.

| Area | Status | Notes |
| --- | --- | --- |
| Me tab avatar bounds | IMPLEMENTED / DEVICE | Retained verified PR #100 bounded circular native tab image fix. |
| Event hub denial contract | IMPLEMENTED | Mobile copy now matches RSVP/host server policy; standalone hub messaging remains separate. |
| Check-in failure semantics | IMPLEMENTED | Explicit auth/location/not-live/server messages. |
| Check-in server-authoritative state | TODO | Remove optimistic success and early-409 local success in `MapViewModelEngagement`. |
| Event hub stale cache | TODO | `AppMainShell.launchEventHubJoin` must not bypass current authorization using `lastHubChatArgs`. |
| Checkout vs event-chat eligibility | TODO | Checkout must not revoke RSVP-based event hub access. |
| RSVP cancellation hub invalidation | TODO | Invalidate cached event-hub authorization after cancellation. |
| Event chat loading | TODO / DEVICE | Decouple authorization/navigation from history/realtime loading and provide empty/error states. |
| Collapsed native headers | TODO / DEVICE | Rework timing/geometry without the rejected broad PR #100 header substitution. |
| Liquid Glass continuity | TODO / DEVICE | Stable native slots; no remount/teleport. |
| Nearby bottom sheet | TODO / DEVICE | Fix gesture ownership with coherent sheet state rather than threshold-only patch. |
| Chat performance/reactions | TODO / DEVICE | Profile first-entry, keyboard, list/reaction stability. |
| Global search latency | TODO / DEVICE | Present/focus immediately; decouple remote query. |
| Event/beacon visual hierarchy | TODO / DEVICE | Reduce card density and clarify RSVP/check-in/chat hierarchy. |
| Connect/Add Click | TODO / DEVICE | Reframe around one connection intent. |
| Me root | TODO / DEVICE | Do not reuse rejected PR #100 redesign. |
| Home/Updates | TODO / DEVICE | Stable async geometry and quieter hierarchy. |

Do not change TODO to complete based only on code inspection or CI.
