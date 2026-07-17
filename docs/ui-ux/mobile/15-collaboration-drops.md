# 15 — Collaboration Drops (Click Drops / Disposable Roll)

**Scope:** `DisposableCameraChrome`, `DisposableRollCapturedPreview`, `DisposableCameraView` (Android/iOS), filter pipeline, locked roll in chat, profile/chat entry points, send pipeline.  
**Source:** `ui/camera/DisposableCameraShared.kt`, `ui/camera/DisposableRollCapturedPreview.kt`, `ui/camera/DisposableRollFilters.kt`, `ui/camera/DisposableCameraView.kt`, `ui/chat/ChatPhotoBubble.kt`, `ui/chat/ConnectionChatMessageComposer.kt`, `ui/components/ProfileBottomSheet.kt`, `App.kt`, `viewmodel/ChatViewModel.kt`, `collaboration/ClickDropReveal.kt`  
**Out of scope:** Web, backend collaboration APIs, redesign proposals.

**Visual system:** Functional Clarity (neo-brutalist) — opaque surfaces, 2px `#000` borders, primary `#630ed4`, no glass/blur/gradients. Design-asset mock: invented from design system.

---

## ASCII hierarchy

```
Entry (Profile / Chat attach / Map)
  → ensureCollaborationSessionReady
    → fail: Scaffold snackbar
    → ok: DisposableCameraView (zIndex 10500)
        ├── DisposableCameraChrome
        │   ├── Top: Close/Retake | Title pill "Click Drops"
        │   ├── Preview slot OR DisposableRollCapturedPreview
        │   └── Bottom: Filter slider | Status chip | Shutter+Flip OR Send
        └── Native camera preview (Android CameraX / iOS AVCapture)
  → onPhotoConfirmed → sendDisposableRollPhoto
    → Chat bubble (locked 24h) → reveal after TTL
```

---

## 1. Layout

### DisposableCameraChrome (full-screen)

| Layer | Position | Style |
|-------|----------|-------|
| Black background | `fillMaxSize` | `Color.Black` |
| Preview OR captured preview | Full bleed | `previewContent()` when no capture; else `DisposableRollCapturedPreview` |
| Top gradient scrim | `TopCenter` | 190dp tall; black 68% → 12% → transparent |
| Bottom gradient scrim | `BottomCenter` | 320dp tall; transparent → 24% → 78% black |
| Close / Retake button | `TopStart` | `statusBarsPadding`, `padding(start=16, top=12)` |
| Title pill | `TopCenter` | `statusBarsPadding`, `padding(top=18)` — text `"Click Drops"` |
| Bottom column | `BottomCenter` | `navigationBarsPadding`, `padding(bottom=36+extraBottomPadding)` |
| White flash overlay | `fillMaxSize` | Animated alpha on shutter (230ms fade from 0.82α) |

**Title pill style:** `RoundedCornerShape(999.dp)`, background `Color.Black.copy(alpha = 0.32f)`, border 1dp white @ 18% alpha, `labelLarge`, white.

### Bottom column (top → bottom)

1. **Filter slider** — only when `capturedImage != null`
2. **Status chip** — always visible
3. **14dp spacer**
4. **Capture controls** — shutter+flip OR send button

### Capture controls

**Pre-capture** (`capturedImage == null`):

| Element | Size |
|---------|------|
| Left spacer | 52dp |
| Shutter button | 96dp outer, 78dp ring (5dp white border @ 96%), 58dp inner fill |
| Flip button | 52dp |

Row: horizontal padding 36dp, `SpaceBetween`.

**Post-capture:**

| Element | Size |
|---------|------|
| Send button | 96dp centered only — no flip |

**Shutter glow:** Radial glow using `PrimaryBlue` / `LightBlue`. Scale 1.0 enabled / 0.92 disabled (spring). `glowAlpha`: 0.55 when shutter enabled OR has capture; else 0.22.

**Send button:** 96dp tap target, 78dp gradient circle (`PrimaryBlue` → `LightBlue`), 4dp white border, `Icons.AutoMirrored.Filled.Send`.

### Filter slider (`DisposableRollFilterSlider`)

| Property | Value |
|----------|-------|
| Label | Filter name at `selectedFilterIndex` — `labelMedium`, white @ 92% |
| Track | Full width, 52dp tall, pill shape, black @ 28%, 1dp white border @ 14% |
| Dots | 10 circles, `SpaceBetween`, horizontal padding 18dp |
| Selected dot | 14dp, `PrimaryBlue` |
| Unselected dot | 10dp, white @ 42% |
| Visibility | Only after capture |

### Status chip

| Property | Value |
|----------|-------|
| Shape | Pill (`RoundedCornerShape(999)`) |
| Background | Black @ 26% |
| Border | 1dp white @ 16% |
| Typography | `labelMedium`, white @ 88% |
| Padding | horizontal 14dp, vertical 7dp |

### DisposableRollCapturedPreview

| Property | Value |
|----------|-------|
| Scale | `ContentScale.Crop`, `fillMaxSize` |
| Mirror | `graphicsLayer { scaleX = -1f }` when `mirrorHorizontally` (front camera) |
| Preview max dimension | 1280px |
| Send JPEG quality | 88 |

### Locked roll in chat bubble

| Property | Value |
|----------|-------|
| Blur | `Modifier.blur(25.dp)` on image |
| Overlay | Scrim @ 28% alpha, centered countdown |
| Countdown (hours > 0) | `"Reveals in {hours}h {mins}m"` |
| Countdown (minutes only) | `"Reveals in {mins}m"` |
| Countdown fallback | `"Locked"` |
| Tap to expand | Disabled when locked |
| Photo a11y | `"Photo"` |

### Profile entry card

| Property | Value |
|----------|-------|
| Label | `"Click Drops"` |
| Icon | `Icons.Filled.PhotoCamera` |
| Style | `usePrimaryBorder = true`, min height 52dp |
| Visibility | `onOpenDisposableRoll != null && !connectionId.isNullOrBlank()` |

### Chat attach menu row

| Property | Value |
|----------|-------|
| Anchor icon a11y | `"Attach"` |
| Menu row label | `"Click Drops"` |
| Menu row icon | `Icons.Filled.PhotoCamera` |

### App overlay mount

| Property | Value |
|----------|-------|
| z-index | `10_500f` |
| Enter | Fade 120ms + scale 0.08 spring |
| Exit | Fade 140ms + scale out 220ms |
| Bottom bar | Hidden while `showConnectionDisposableRoll || disposableRollOpening` |

---

## 2. Interactive

| Action | Behavior |
|--------|----------|
| **Close** (no capture) | `onDismiss` → exits camera overlay |
| **Retake** (has capture) | Clears `capturedImage`, resets filter index to 0, returns to live preview |
| **Shutter** | `PlatformHapticsPolicy.lightImpact()` + white flash animation |
| **Flip camera** | Pre-capture only; sets `isFlippingCamera` until rebind/reconfigure |
| **Filter slider drag** | 28dp threshold per step; haptic `lightImpact` on change |
| **Filter dot tap** | Select index + haptic |
| **Send** | `PlatformHapticsPolicy.successNotification()` → `applyDisposableRollFilterToJpeg` → `sendDisposableRollPhoto` → dismiss overlay |
| **Pinch zoom** | Android `detectTransformGestures`; iOS `UIPinchGestureRecognizer` (max 5×) |
| **Default camera** | Front (`useFrontCamera = true`) |
| **Locked roll tap** | No expand; long-press still allowed via parent bubble |
| **Profile card tap** | Dismiss sheet → `open(connectionId)` |
| **Chat attach row tap** | `heavyImpact` + `successNotification`, collapse menu, hide keyboard, `onOpenDisposableRoll()` |

### Filter index → name mapping

| Index | Name |
|-------|------|
| 0 | `"Natural"` |
| 1 | `"Warm"` |
| 2 | `"Cool"` |
| 3 | `"Vintage"` |
| 4 | `"Dramatic"` |
| 5 | `"Fade"` |
| 6 | `"Noir"` |
| 7 | `"Vibrant"` |
| 8 | `"Golden"` |
| 9 | `"Moody"` |

`COUNT = 10`. Index 0 (Natural) returns original bytes unchanged on send.

---

## 3. States

### Status chip (priority-ordered — first match wins)

| Condition | String |
|-----------|--------|
| `capturedImage != null` | `"Ready for the roll"` |
| `isCapturingPhoto` | `"Capturing..."` |
| `isFlippingCamera` | `"Flipping..."` |
| `isShutterEnabled` | `"Snap once"` |
| else | `"Preparing..."` |

### When each status appears

| Status | Android trigger | iOS trigger |
|--------|-----------------|-------------|
| `"Preparing..."` | No permission / `imageCapture == null` / capturing / sending | `!setupComplete` / capturing / sending |
| `"Snap once"` | Camera bound, not capturing | `setupComplete && !isCapturing` |
| `"Capturing..."` | `takePicture` in flight | `AVCapturePhoto` in flight |
| `"Flipping..."` | `isFlippingCamera = true` until rebind | `useFrontCamera` change → `setupComplete = false` until session reconfigures |
| `"Ready for the roll"` | `capturedImage != null` | same |

**Shutter enabled:** `capturedImage == null && camera ready && !isCapturing && !isSending`.

### Session open

| State | UI |
|-------|-----|
| Opening | `disposableRollOpening = true`; bottom bar hidden; no camera yet |
| Ready | `showConnectionDisposableRoll && rollConnectionId != null && activeRollSession != null` |
| Open fail | Scaffold snackbar (see errors) |

### Locked roll

| State | UI |
|-------|-----|
| Locked | `metadata.disposable_roll == true` AND `now < collaboration_ttl` |
| Missing/unparseable TTL | Treated as **locked** |
| Reveal TTL | 24 hours after send (`computeClickDropRevealTtlIso()`) |
| Unlocked | Full image; tap expands to `ChatExpandedPhotoPreview` |
| Profile Media tab | Blur + countdown; click disabled when locked |

### Camera permission / setup fallbacks

| Platform | Title | Message |
|----------|-------|---------|
| Android denied | `"Camera permission needed"` | `"Disposable Roll uses the camera only for this shared drop."` |
| Android setup fail | `"Camera unavailable"` | Error message or `"The camera could not be prepared. Close and try again."` |
| iOS checking | `"Requesting camera access"` | `"Disposable Roll needs the camera to capture a private drop."` |
| iOS denied | `"Camera access required"` | `"Enable camera access in Settings to use Disposable Roll."` + `"Open Settings"` |

### Send-time errors

| String | Trigger |
|--------|---------|
| `"Failed to send — unable to start chat"` | No API chat ID |
| `"Failed to upload Click Drop photo"` | Upload failure |
| `"Failed to send Click Drop photo"` | `sendMessage` null |
| `"Failed to send Click Drop — {error}"` | Exception catch |

### Session open error

| String | Trigger |
|--------|---------|
| Server/error message (max 160 chars) | `ensureCollaborationSessionReady` failure with message |
| `"Couldn't open Click Drops"` | Fallback when error message empty |

---

## 4. Micro-copy

### Camera chrome

| Key | String |
|-----|--------|
| Title pill | `"Click Drops"` |
| Status ready | `"Ready for the roll"` |
| Status capturing | `"Capturing..."` |
| Status flipping | `"Flipping..."` |
| Status snap | `"Snap once"` |
| Status preparing | `"Preparing..."` |
| Close (no capture) a11y | `"Close camera"` |
| Retake (has capture) a11y | `"Retake photo"` |
| Flip a11y | `"Flip camera"` |
| Send a11y | `"Send to Click Drops"` |

### Filters

| Index | Name |
|-------|------|
| 0 | `"Natural"` |
| 1 | `"Warm"` |
| 2 | `"Cool"` |
| 3 | `"Vintage"` |
| 4 | `"Dramatic"` |
| 5 | `"Fade"` |
| 6 | `"Noir"` |
| 7 | `"Vibrant"` |
| 8 | `"Golden"` |
| 9 | `"Moody"` |

### Entry points

| Key | String |
|-----|--------|
| Profile card | `"Click Drops"` |
| Chat attach anchor | `"Attach"` |
| Chat attach row | `"Click Drops"` |

### Locked roll

| Key | String |
|-----|--------|
| Countdown hours | `"Reveals in {hours}h {mins}m"` |
| Countdown minutes | `"Reveals in {mins}m"` |
| Fallback | `"Locked"` |
| Photo a11y | `"Photo"` |

### Errors

| Key | String |
|-----|--------|
| Session open fallback | `"Couldn't open Click Drops"` |
| Android camera permission | `"Camera permission needed"` |
| Android camera permission body | `"Disposable Roll uses the camera only for this shared drop."` |
| Android camera unavailable | `"Camera unavailable"` |
| Android camera unavailable body | `"The camera could not be prepared. Close and try again."` |
| iOS requesting | `"Requesting camera access"` |
| iOS requesting body | `"Disposable Roll needs the camera to capture a private drop."` |
| iOS denied | `"Camera access required"` |
| iOS denied body | `"Enable camera access in Settings to use Disposable Roll."` |
| iOS open settings | `"Open Settings"` |
| Send no chat | `"Failed to send — unable to start chat"` |
| Send upload fail | `"Failed to upload Click Drop photo"` |
| Send fail | `"Failed to send Click Drop photo"` |
| Send exception | `"Failed to send Click Drop — {error}"` |

---

## 5. Flow

### Open from Profile

```
Profile sheet → "Click Drops" card tap
  → Dismiss profile sheet
  → disposableRollOpening = true
  → ensureCollaborationSessionReady(connectionId)
    → fail: snackbar "Couldn't open Click Drops" (or server msg)
    → ok: showConnectionDisposableRoll = true, camera overlay z=10500
```

**Wiring:** Map pin profile, Connections user profile (`TabbedUserProfileSheet`), Group profile (`TabbedGroupProfileSheet` uses `resolvedChatId`).

### Open from chat attach menu

```
Chat composer → "+" Attach
  → Menu row "Click Drops"
  → heavyImpact + successNotification
  → Collapse menu, hide keyboard
  → Group: onOpenDisposableRollForChat(chatId)
  → 1:1: onOpenDisposableRoll(connectionId)
  → Same session-ready → camera flow
```

### Capture → send

```
Live preview + "Snap once"
  → Shutter tap → "Capturing..." → flash animation
  → "Ready for the roll" + filter slider (default "Natural")
  → Optional: adjust filter via slider/dots
  → "Send to Click Drops" → filter applied to JPEG
  → sendDisposableRollPhoto (E2EE upload)
  → Overlay dismiss
  → Chat bubble: blurred + countdown (24h lock)
  → After TTL: image revealed, tap expands
```

### Retake / dismiss

```
Post-capture: top-left "Retake photo"
  → Clear bytes, filter index → 0, return to live preview

Pre-capture: top-left "Close camera"
  → Exit overlay entirely
```

### Locked roll in chat

```
Message metadata: disposable_roll=true, collaboration_ttl=ISO instant
  → While now < ttl:
    → ChatPhotoBubble: blur 25dp + countdown overlay
    → ChatExpandedPhotoPreview: blocked
    → Profile Media tab: thumb blurred, tap disabled
  → After ttl:
    → Full image visible
    → Tap → fullscreen preview
```

### Map secondary entry

```
MapScreen → profile → "Click Drops"
  → Same openConnectionDisposableRoll flow
```

---

## 6. A11y

| Element | `contentDescription` |
|---------|---------------------|
| Close (no capture) | `"Close camera"` |
| Retake (has capture) | `"Retake photo"` |
| Flip camera | `"Flip camera"` |
| Send | `"Send to Click Drops"` |
| Shutter inner icon | `null` |
| Captured preview image | `null` |
| Title `"Click Drops"` | Visible text only |
| Filter name | Visible text only |
| Status chip | Visible text only |
| Chat attach anchor | `"Attach"` |
| Attach menu icons | `null` (label text carries meaning) |
| Profile card icon | `null` |

**Platform notes:**

- iOS camera preview: `isNativeAccessibilityEnabled = false` on `UIKitView`.
- Fallback screens use `"Close camera"` on close button.

**Gaps:**

- Shutter has no spoken label — relies on status chip `"Snap once"` context.
- Filter slider has no accessibility actions or value announcements.
- Status chip text not exposed as live region.
- Locked roll countdown not announced on tick.

---

## Related documents

- [02-shell-navigation.md](02-shell-navigation.md) — z-index stack (camera @ 10500), snackbar host
- [08-chat.md](08-chat.md) — chat composer, photo bubbles, attach menu
- [07-connections-inbox.md](07-connections-inbox.md) — profile sheets from connections
