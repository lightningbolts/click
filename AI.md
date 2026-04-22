# Click — AI & contributor context (Cursor / agents)

This file is **authoritative guidance** for future developers and AI assistants working on the Click monorepo (`click/` KMP app, `click-web/` Next.js companion, Supabase functions under `click/supabase/`). It encodes architectural decisions and pitfalls that are easy to get wrong from training data alone.

---

## 1. Kotlin Multiplatform architecture

- **Default location for logic:** `click/composeApp/src/commonMain/kotlin/...`.
- **Use `expect` / `actual` (or small platform-specific facades)** only when you must call **native APIs** or SDKs that are not available in common code—examples in this repo: **CallKit**, **PushKit**, **NFC**, secure **Keychain** / **EncryptedSharedPreferences**, **LiveKit Android** vs **LiveKit Swift**, location services, and crypto primitives (`PlatformCrypto`).
- **Do not** duplicate business rules across `androidMain` and `iosMain` if they can live in `commonMain` behind a narrow interface.

---

## 2. State management (Compose + ViewModels)

- **ViewModels must expose UI state exclusively via `StateFlow`** (`MutableStateFlow` internally, public `StateFlow` or `asStateFlow()`). Collect in Compose with `collectAsState()` / `collectAsStateWithLifecycle()` (or equivalent patterns).
- **UI must observe state reactively**—avoid one-off reads that miss updates.
- **Immutability:** When updating nested `data class` state, **replace with deep copies** (or persistent collections) so Jetpack Compose **does not miss recompositions** due to stable references and `remember` caching the old instance.
- **Legacy note:** Some older code paths may still use `mutableStateOf` on a ViewModel; **new code should not**—migrate toward `StateFlow` when touching those classes.

---

## 3. VoIP and notifications (iOS vs Android)

### iOS VoIP — never Firebase

- **Do not** route **iOS VoIP** pushes through Firebase. Incoming calls use **direct APNs** with **`apns-push-type: voip`** and topic **`<bundleId>.voip`**, implemented in the Supabase Edge Function **`send-push-notification`** (see `click/supabase/functions/send-push-notification/`).
- **Standard** iOS notifications may still use APNs through the same function with appropriate headers; **Android** uses **FCM** via service account JSON configured as a Supabase secret.

### VoIP race condition (PushKit ↔ Kotlin)

- **PushKit** can deliver or refresh the VoIP token **before** the Kotlin runtime and `TokenStorage` are ready.
- **Mitigation in this repo:** The native layer **caches the VoIP token in `UserDefaults`** (e.g. key `cached_voip_token`) **immediately** when `PKPushRegistry` updates credentials, **then** calls into Kotlin to persist when possible.
- **On app wake / standard token sync**, Swift **replays** the cached VoIP token into Kotlin so the shared data layer and backend stay consistent. **AI-generated code must preserve this ordering**—do not remove UserDefaults caching or defer token registration only to Kotlin.

### CallKit

- **Incoming VoIP** must report to **CallKit** quickly on the main queue; completion handlers from PushKit must be invoked according to Apple’s contract (see `ClickCallKitManager` / `ClickVoipPushManager` in `click/iosApp/`).

---

## 4. Database and Supabase Auth metadata

- Assume a **normalized PostgreSQL schema** with dedicated tables—patterns in `click/database/` include (among others) users-related data, **`user_interests`**, **connections**, **messages**, **chats**, **push_tokens** (with `token_type` for `standard` vs `voip`), notification preferences, and location/environment-related columns where used.
- **Do not** store **core application state** in `auth.users.raw_user_meta_data` as the primary source of truth. Use **first-class tables** and RPCs/views as the app does for profiles, interests, and connection graph data. Metadata may still be used for **display fallbacks** where the codebase already does—do not expand reliance on raw metadata for new features.

---

## 5. Configuration pointers (avoid hallucinated paths)

| Concern | Where to look |
|--------|----------------|
| Supabase URL / anon key | `click/composeApp/src/commonMain/kotlin/compose/project/click/click/data/SupabaseConfig.kt` |
| Web / QR / default API base | `click/composeApp/src/commonMain/kotlin/QRModels.kt` — `CLICK_WEB_BASE_URL` |
| LiveKit token HTTP client | `click/composeApp/src/commonMain/kotlin/compose/project/click/click/calls/CallApiClient.kt` |
| Push + APNs VoIP logic | `click/supabase/functions/send-push-notification/index.ts` |
| iOS VoIP + cache | `click/iosApp/iosApp/ClickVoipPushManager.swift`, `iOSApp.swift` |
| Operator checklist | `click/EXTERNAL_SETUP.md` |

---

## 6. General discipline

- **Match existing style** in the touched file: package names, coroutine usage, and repository patterns.
- **Prefer small, focused diffs**—do not refactor unrelated modules when fixing a bug.
- **Verify platform behavior** on both Android and iOS when changing shared call, push, or connection flows.

When in doubt, read the referenced files before proposing changes.
