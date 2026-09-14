# PR #99 acceptance recovery

This branch follows the launch-polish specification produced after physical-device testing of PR #99 and the rejected broad PR #100 recovery attempt.

## Baseline

- Start from PR #99/main.
- Retain only the physically verified PR #100 Me-tab avatar bounds fix.
- Treat every other PR #99 claim as unresolved until its observable acceptance criterion passes.

## Implementation order

1. Event RSVP/check-in/hub authorization and loading.
2. Native collapsed navigation and persistent Liquid Glass geometry.
3. Nearby bottom-sheet gesture ownership.
4. Chat first-load, keyboard, reaction, scroll and navigation stability.
5. Global search presentation latency.
6. Event/beacon hierarchy.
7. Chat visual hierarchy and composer.
8. Connect/Add Click.
9. Me root.
10. Home and Updates async/layout polish.

## Rules

Do not bundle speculative redesigns with functional fixes. Do not add fake latency. Do not optimistically show server-validated state as successful. Do not use cached event-hub authorization as proof that the current RSVP policy still allows access. Do not declare physical-device behavior fixed from CI alone.

## Physical-device gate

Before merge, verify cold/warm launch, repeated navigation, event RSVP -> event chat without check-in, RSVP cancellation, live and future event check-in, Nearby drag, compact header geometry, Liquid Glass continuity, chat keyboard/reactions/scroll, search opening, and Home/Updates slow-data layout on a physical iPhone.
