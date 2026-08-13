# AGENTS.md

## Cursor Cloud specific instructions

### Repository overview

Click is a privacy-first social connection app. The repo contains:

| Component | Location | Tech |
|---|---|---|
| KMP mobile app | `composeApp/` | Kotlin Multiplatform + Compose Multiplatform (Android/iOS) |
| Supabase Edge Functions | `supabase/functions/` | Deno/TypeScript (mobile-owned + mirrored shared) |
| DB migrations (mirror) | `supabase/migrations/` | Subset mirrored from **click-web** (source of truth) |
| Legacy SQL notes | `database/` | PostgreSQL SQL files / historical notes |

Backend HTTP APIs live in the sibling **`click-web`** Next.js app (`CLICK_WEB_BASE_URL`). The old Flask `server/` tree has been **removed** — do not recreate it.

See `README.md` for architecture details and `AI.md` for coding guidelines.

### Running services

**No local Flask/API server is required.** Point `CLICK_WEB_BASE_URL` in `composeApp/src/commonMain/kotlin/QRModels.kt` at deployed click-web (`https://joinclick.co`) or a local Next.js instance (`http://localhost:3000` for simulator).

**Android build:**
```
./gradlew :composeApp:assembleDebug
```
Requires `local.properties` with `sdk.dir=/opt/android-sdk` and `MAPS_API_KEY=<key>` (placeholder value works for builds). If `local.properties` is missing, Gradle falls back to checked-in `local.defaults.properties` so iOS framework embed and IDE sync still configure. The `google-services.json` is optional; the build gracefully skips the Google Services plugin when it is absent.

### Testing

- **Kotlin unit tests (Android JVM):** `./gradlew :composeApp:testDebugUnitTest`
- **Kotlin / Compose UI tests (iOS Simulator):** `./gradlew :composeApp:iosSimulatorArm64Test` (macOS + Xcode required)
- **All Kotlin tests:** `./gradlew :composeApp:allTests`
- **Maestro E2E (device/simulator + CLI):** `maestro test .maestro --include-tags smoke` after `assembleDebug` / iOS sim install. Auth flows: `maestro test .maestro/auth -e TEST_EMAIL=... -e TEST_PASSWORD=...`
- **Supabase drift (when `../click-web` is present):** `bash scripts/check-supabase-drift.sh`

CI: Android job runs on Linux (`testDebugUnitTest` + `assembleDebug`); iOS job runs on `macos-26` + Xcode 26 (`iosSimulatorArm64Test`).

### Non-obvious caveats

- The `google-secrets` Gradle plugin reads `MAPS_API_KEY` from `local.properties`, with `local.defaults.properties` as a checked-in fallback for CI/Xcode when the gitignored file is absent. A placeholder value is sufficient for compilation but Google Maps features won't work at runtime without a real key.
- iOS builds require Xcode (macOS only) and are not runnable in Cloud Agent VMs.
- The `click-web` Next.js companion app (LiveKit token endpoint, QR flows, chat gatekeeper) is a **separate repository**. Prefer checking it out as a sibling `../click-web`.
- **`click-web/supabase` is the source of truth** for shared migrations and `bind-proximity-connection`. Sync mirrors with `bash scripts/sync-supabase-from-click-web.sh`. Mobile-only functions (`send-push-notification`, `expire-*`, `verify-hub-proximity`) stay in this repo.
- Supabase Edge Functions require the Supabase CLI to deploy/serve locally; they are not needed for basic mobile build testing.
- `local.properties` is gitignored. Recreate it on each fresh checkout (or rely on `local.defaults.properties` for Gradle configure-only steps such as iOS framework embedding).
