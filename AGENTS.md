# AGENTS.md

## Cursor Cloud specific instructions

### Repository overview

Click is a privacy-first social connection app. The repo contains:

| Component | Location | Tech |
|---|---|---|
| KMP mobile app | `composeApp/` | Kotlin Multiplatform + Compose Multiplatform (Android/iOS) |
| Flask API server | `server/` | Python 3, Flask 3.0 |
| Supabase Edge Functions | `supabase/functions/` | Deno/TypeScript |
| DB migrations | `database/` | PostgreSQL SQL files |

See `README.md` for architecture details and `AI.md` for coding guidelines.

### Running services

**Flask server** (required for backend API):
```
cd server && source venv/bin/activate && python app.py
```
Runs on port 5000. Requires `server/.env` with `SUPABASE_URL`, `SUPABASE_KEY`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`. Without real Supabase credentials the server starts but DB-dependent endpoints will error; the root `/` endpoint returns "Hello World!" regardless.

**Android build:**
```
./gradlew :composeApp:assembleDebug
```
Requires `local.properties` with `sdk.dir=/opt/android-sdk` and `MAPS_API_KEY=<key>` (placeholder value works for builds). The `google-services.json` is optional; the build gracefully skips the Google Services plugin when it is absent.

### Testing

- **Kotlin unit tests:** `./gradlew :composeApp:testDebugUnitTest`
- **All Kotlin tests:** `./gradlew :composeApp:allTests`
- **No Python test suite** exists for the Flask server; test manually with `curl`.

### Non-obvious caveats

- The `google-secrets` Gradle plugin reads `MAPS_API_KEY` from `local.properties`. A placeholder value is sufficient for compilation but Google Maps features won't work at runtime without a real key.
- iOS builds require Xcode (macOS only) and are not runnable in Cloud Agent VMs.
- The `click-web` Next.js companion app (LiveKit token endpoint, QR flows) is a **separate repository** and not present in this workspace.
- Supabase Edge Functions require the Supabase CLI to deploy/serve locally; they are not needed for basic server or mobile build testing.
- `server/.env` and `local.properties` are both gitignored. They must be recreated on each fresh checkout.
