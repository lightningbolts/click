# Event state-machine invariants

The next event implementation commit must satisfy these invariants together:

1. New check-in attempts do not set `checkedIn=true` before server success.
2. HTTP 409/not-live is a failure for check-in state; it must not create `localEarlyCheckIn`.
3. Checkout changes physical-presence state only. It does not revoke event-chat eligibility while RSVP remains accepted.
4. RSVP cancellation invalidates cached event-hub authorization for non-host users.
5. Opening an event hub always performs current server authorization; `lastHubChatArgs` may cache presentation metadata but may not bypass authorization.
6. Hub message-history/realtime loading does not block navigation indefinitely.

These invariants should be covered by pure tests where possible and physical-device checks where navigation/network behavior is involved.
