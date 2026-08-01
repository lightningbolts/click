# Click — Android Focus Matrix

**Purpose:** Concentrate regression and bug verification on Android-only or Android-skewed failures.  
**Use with:** [03-known-issues-audit.md](03-known-issues-audit.md) · [01-full-checklist.md](01-full-checklist.md) · [02-smoke-10min.md](02-smoke-10min.md)

**Environment:** Physical Android device recommended (API 33+ for notification permission). Emulator is insufficient for BLE/LiveKit quality.

---

## 1. Permissions matrix

| Permission | Used by | Deny behavior | Grant / retry |
|------------|---------|---------------|---------------|
| Location | Map, handshake, hubs | No pins / handshake degrade | Settings → re-enter Map / Tap |
| Bluetooth / Nearby | Tri-Factor BLE | Handshake fails or GPS-only | Re-run Tap |
| Microphone | Calls, voice notes, ultrasonic | `[KNOWN-6]` call ends; `[KNOWN-7]` record may crash | Must retry call after grant (today broken) |
| Camera | Video call, QR, roll | Video call / scan fail | Retry flow |
| Notifications (`POST_NOTIFICATIONS`) | Incoming call UI, FCM | Silent miss of incoming call | Grant → retest incoming |
| Photo library | Chat media | Picker fail toast | Retry |

- [ ] Fresh install: walk each permission deny → rationale / settings → feature recovers
- [ ] `[KNOWN-6]` Mic/camera: first call requests permission **and** completes after grant (currently ends immediately)

---

## 2. Tri-Factor / BLE handshake

| Check | Pass criteria | Known |
|-------|---------------|-------|
| Advertise + scan | Second phone hears token within listen window | |
| GATT token read | 4-digit token matches peer | |
| Ultrasonic | Chirp does not leave mic stuck for voice notes | `[KNOWN-7]` |
| GPS fallback | Match within ~15m when BLE weak | `[KNOWN-1]` coalesce |
| Timeline after reconnect | Peer profile “Our timeline” updates without clearing app cache | |
| 1:1 outcome | DM connection, not group-only | `[KNOWN-3]` |
| 3+ outcome | All members on group | `[KNOWN-1]` |
| Re-tap | No duplicate Active rows | `[KNOWN-2]` |
| Offline queue | WorkManager flush creates connection online | §19 |

**Key code:**  
`androidMain/.../proximity/AndroidProximityManager.kt` ·  
`ConnectionViewModel` ·  
`click-web/lib/server/proximity/bindProximityHandshake.ts`

- [ ] Two Pixels/Samsungs tap → 1:1
- [ ] Three devices tap → one group with three members
- [ ] Immediately after handshake, open chat and record voice (mic release)

---

## 3. Voice & video calls (LiveKit)

| Check | Pass criteria | Known |
|-------|---------------|-------|
| Outgoing voice | Preview → connected → mute/end | `[KNOWN-6]` |
| Outgoing video | Camera preview + remote | `[KNOWN-6]` |
| Incoming | Notification / full-screen → accept | `[KNOWN-11]` |
| No activity | Background start does not hard-crash | activity-null end |
| Group call | ≤8 works; &gt;8 shows error (not silent) | |

**Key code:** `CallManager.android.kt` (`CALL_PERMISSION_REQUEST_CODE = 4013`), `CallSessionManager.kt`, `PlatformIncomingCallUi.android.kt`

- [ ] Permissions pre-granted → voice call connects
- [ ] Permissions cleared in system settings → call requests → **after grant, call works**
- [ ] Incoming from second device while app backgrounded

---

## 4. Voice messages

| Check | Pass criteria | Known |
|-------|---------------|-------|
| Record | Dialog start/stop without crash | `[KNOWN-7]` |
| Send | Encrypted upload; bubble appears | |
| Play | Play/pause; progress | `[KNOWN-7]` |
| Contention | After Tap-to-Connect ultrasonic | `[KNOWN-7]` |
| WebM inbound | Play or graceful fallback (no crash) | format gap |

**Key code:** `ChatMediaPickers.android.kt` (`MediaRecorder.start`), `ChatAudioPlayer.android.kt`, `SecureChatAudioFiles.android.kt`

- [ ] Record 3s → send → play
- [ ] Double-tap re-record
- [ ] Record while / just after proximity listen

---

## 5. Map styling & events

| Check | Pass criteria | Known |
|-------|---------------|-------|
| Default map | Matches design (dark zinc vs full color — confirm intent) | `[KNOWN-5]` |
| Ghost on | Grayscale style + dim overlay | `[KNOWN-5]` |
| Ghost off | Restores non-ghost style | |
| Event list | Event on map listed in discovery feed | `[KNOWN-4]` |
| Hazard pin | Icon size comparable to other beacons | `[KNOWN-9]` |

**Key code:** `MapView.android.kt` (`DARK_MAP_STYLE`, `GRAYSCALE_MAP_STYLE`), `MapUtils.kt` (`standaloneKinds`), `MapDiscoveryLayout.kt`

---

## 6. Group create

| Check | Pass criteria | Known |
|-------|---------------|-------|
| FAB → picker → create | Group chat opens | `[KNOWN-8]` |
| Ineligible members | Disabled / toast, no crash | |
| Logcat on fail | Capture stack if process dies | |

---

## 7. Push / background

| Check | Pass criteria | Known |
|-------|---------------|-------|
| FCM message | Tap opens correct chat | |
| FCM call | Incoming UI | `[KNOWN-6]` `[KNOWN-11]` |
| Kill + message | Notification still routes after auth | |
| Offline proximity | Flush on reconnect | |

---

## 8. Quick Android smoke (5 min)

Copy results into the [smoke log](02-smoke-10min.md).

1. Cold start  
2. Tab bar ×5  
3. Tap handshake (2 devices if available)  
4. Send text + voice note  
5. Outgoing voice call (permission edge case)  
6. Map + one event/hazard  
7. Group FAB create  
8. Ghost toggle  

---

## Logcat filters (debug)

```text
CallManager|LiveKit|MediaRecorder|Proximity|bindProximity|VerifiedClique|ChatAudio
```

Attach filtered logs when filing Android crashes for `#7` / `#8` / `#6`.
