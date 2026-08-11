# Engineering Evaluation — `click` (KMP mobile) & `click-web` (Next.js)

**Date:** 2026-08-10
**Scope:** `/home/zhihongcheng/code/click` and `/home/zhihongcheng/code/click-web`
**Lens:** software engineering best practice, with emphasis on how well the codebase supports an **agentic (AI-assisted) development workflow**.

---

## 0. Executive summary

Two repos, one product. `click` is a Kotlin Multiplatform Compose app (~550 Kotlin files, ~115k LOC) plus a vestigial Flask server; `click-web` is a Next.js 16 / React 19 app (~301 TS files, ~58k LOC) deployed to Cloudflare Workers via OpenNext, sharing the same Supabase project.

**What is genuinely strong:** the agent-facing documentation. Both repos carry `AGENTS.md` + `AI.md` + `.cursor/.cursorrules`, and `click` additionally has **25 per-package `README.md` files** inside `commonMain`. These encode non-obvious, hard-won constraints (iOS VoIP must never go through Firebase; the PushKit↔Kotlin token race and its `UserDefaults` mitigation; don't treat `auth.users.raw_user_meta_data` as source of truth). This is materially better than most codebases and is exactly the right investment for agentic work.

**What undermines it:** the *verification* half of the loop is largely missing. `click-web` has **zero CI**, **no ESLint**, and `npm run lint` is only `tsc --noEmit`. `click` has CI that compiles Android but **never runs the 82 test files**. Neither repo has a linter/formatter for its primary language. An agent (or human) can therefore produce confidently-wrong code with no automated signal.

**The most serious concrete defect found:** shipped mobile chat code still points at a hardcoded developer LAN IP over cleartext HTTP (`ApiConfig.USE_LOCAL_SERVER = true` → `http://10.19.165.221:5000`), with 22 call sites still targeting a Flask server that has been unmaintained since June and does not implement most of the endpoints being called.

**Overall grade: C+ / B-.** Impressive product surface and unusually good written context; weak engineering guardrails and one live production-correctness bug.

| Dimension | `click` | `click-web` |
|---|---|---|
| Agent-facing docs | **A-** | B+ |
| Architecture & layering | B | C+ |
| Test coverage & strategy | C | C |
| CI/CD & quality gates | D+ | **F** |
| Static analysis / lint | F | D |
| Security posture | C | B- |
| Repo hygiene | D+ | C+ |
| Cross-repo coherence | **D** (shared failure) | **D** (shared failure) |

---

## 1. What is done well

### 1.1 Agent context is a first-class artifact
`AI.md` in both repos is *authoritative constraint documentation*, not marketing. Representative examples from `click/AI.md`:

- "**Do not** route **iOS VoIP** pushes through Firebase… `apns-push-type: voip`, topic `<bundleId>.voip`."
- The PushKit race: "The native layer **caches the VoIP token in `UserDefaults`**… **AI-generated code must preserve this ordering**."
- A "Configuration pointers (avoid hallucinated paths)" table mapping concerns → exact files.

That last table is best-practice for agentic workflows: it directly attacks the dominant LLM failure mode (inventing plausible file paths). `click-web/AI.md` mirrors this with Server/Client Component rules, Edge Function CORS gotchas, and explicit guardrails ("Never replace multi-step auth UX with blind auto-redirects").

### 1.2 Per-package READMEs in `commonMain`
25 package-level READMEs (`auth/`, `calls/`, `crypto/`, `proximity/`, `sensors/`, `viewmodel/`, …) explain each package's purpose, layering, and architecture with diagrams. For an agent doing retrieval over a 550-file Kotlin tree, these are high-signal entry points and dramatically reduce blind grepping. This is a genuine differentiator — keep it, and enforce that new packages ship one.

### 1.3 Clear architectural doctrine
`commonMain`-first with `expect`/`actual` reserved for named native concerns (CallKit, PushKit, Keychain, LiveKit, crypto); ViewModels expose `StateFlow` only, with an explicit acknowledgement that `mutableStateOf` in ViewModels is legacy to be migrated. Stating the *migration direction* for legacy patterns is more useful to an agent than pretending the codebase is uniform.

### 1.4 Sensible tech choices
Supabase KMP + Ktor 3 + Compose Multiplatform on mobile; Next 16 / React 19 / Supabase SSR / OpenNext-on-Cloudflare on web. Dependencies are current, not stale. `click-web` pins `@types/react` via `overrides` — evidence someone actually debugged a real dependency conflict.

### 1.5 Server-side security fundamentals in `click-web`
`lib/server/connectionWriteAuth.ts:8-20` creates the Supabase admin client from the server-only `SUPABASE_SERVICE_ROLE_KEY` with an explicit "Never fall back to the anon key" comment. No `.env` files tracked; no service-role key leaked through `NEXT_PUBLIC_`; **0** `@ts-ignore`/`@ts-expect-error` across the TS codebase.

---

## 2. Critical findings

### 2.1 🔴 Shipped mobile code points at a developer's laptop over cleartext HTTP

`composeApp/src/commonMain/kotlin/compose/project/click/click/data/api/ApiConfig.kt:11-27`:

```kotlin
private const val USE_LOCAL_SERVER = true
private const val LOCAL_IP = "10.19.165.221"   // "Your Mac's local IP"
private const val LOCAL_PORT = 5000
private const val PRODUCTION_URL = "https://your-production-api.com"   // placeholder
```

`ApiClient.kt:376` similarly hardcodes `const val BASE_URL = "http://localhost:5000"`.

This is not dead code. `ChatApiClient` defaults `baseUrl = ApiConfig.BASE_URL` (`ChatApiClient.kt:32`) and is instantiated by `ChatViewModel.kt:192`, `HubChatViewModel.kt:174`, and `SupabaseChatRepository.kt:97` — the core chat path. **22 call sites still target this base URL** (15 in `ChatApiClient`, 7 in `ApiClient`), versus 69 already migrated to `CLICK_WEB_BASE_URL`. The migration to click-web is roughly ¾ done and stalled.

Compounding problems:
- The `PRODUCTION_URL` fallback is an unedited placeholder, so flipping `USE_LOCAL_SERVER` to `false` breaks rather than fixes.
- Cleartext `http://` — will be blocked by default on Android 9+ and ATS on iOS.
- **Most of the endpoints being called don't exist.** `server/app.py` defines 11 API routes; the client calls `/api/messages/{id}/reactions`, `/api/chats/{id}/typing`, `/api/chats/{id}/search`, `/api/messages/{id}/forward`, `/api/users/{id}` — none are implemented. These are permanent 404 paths.

**Action:** delete `ApiConfig.USE_LOCAL_SERVER` / `LOCAL_IP`, finish routing the remaining 22 call sites to `CLICK_WEB_BASE_URL` (or delete them if superseded by Supabase Realtime), and delete or archive `server/`.

### 2.2 🔴 The Flask server (`click/server/`) is abandoned but still documented as required

`git log` shows `server/` untouched since **2026-06-12**, while `database/` and the app churn weekly. Yet `AGENTS.md` still says "**Flask server** (required for backend API)". An agent reading `AGENTS.md` will spin up a service that nothing in production uses, and may "fix" mobile code to match its stale schema. Stale setup instructions are worse than none in an agentic workflow: they actively misdirect. Decide — delete it, or fix §2.1 and give it a deployment.

### 2.3 🔴 `click-web` has no CI at all

There is **no `.github/workflows/` directory**. `vercel.json` is an empty `{}`. No Husky, no pre-commit hooks (`.git/hooks` holds only `.sample` files). Nothing automatically runs `tsc --noEmit`, `jest`, or `next build` on any push or PR.

For a 58k-LOC TypeScript app with 85 API routes handling auth, payments (Stripe), and E2EE chat, this is the single highest-leverage gap. Every quality gate depends on a human remembering to run it locally — and an AI agent has no gate at all.

### 2.4 🟠 `click` CI compiles but never tests

`.github/workflows/android-ci.yml` runs only `./gradlew :composeApp:assembleDebug`. The repo contains **82 Kotlin test files** — `MessageCryptoTest`, `ChatViewModelStateTest`, `OfflineBootTest`, `OlderMessagesPaginationTest`, and more — and **not one of them runs in CI**. The tests that exist are decent; they're simply not wired to anything.

**Action (one line):** add `./gradlew :composeApp:testDebugUnitTest` to the workflow. This is the cheapest large win available in either repo.

### 2.5 🟠 Migrations and an Edge Function are forked across repos, and have already drifted

Both repos carry `supabase/migrations/` (`click-web`: 75 files, `click`: 24) with **24 exactly-overlapping filenames**, plus a `bind-proximity-connection` Edge Function in both. They are copied by hand, and drift is already measurable:

- `20260810010000_fix_rls_friction_and_auto_archive.sql` differs — `click`'s copy strips comments and is missing a `COMMENT ON FUNCTION` statement, with the header rewritten to "Mirror of click-web migration…".
- `bind-proximity-connection/index.ts` (click-web 1236 lines vs click 1226) has **real logic divergence**: click-web has an `isEncounterRateLimitError()` helper the mobile copy lacks; the mobile copy carries a stray `console.log("INCOMING_HANDSHAKE_PAYLOAD:", …)`; and the `is_new_connection` fallback differs — `meta != null ? meta.isNewConnection : false` (web) vs `meta?.isNewConnection ?? true` (mobile). **Opposite defaults for the same field in the same deployed function.**

Two copies of one function against one Supabase project means whichever repo deploys last wins, silently. This is precisely the class of bug agents create and then fail to detect, because a search in one repo looks complete.

**Action:** designate `click-web` the single source of truth for `supabase/`. Replace the mobile copy with a git submodule, a sync script with a CI drift check, or simply delete it and document where it lives.

---

## 3. Significant findings

### 3.1 No linter or formatter in either repo
- `click`: no ktlint, no detekt, no Spotless anywhere in Gradle config. **345 raw `println(` calls** across `composeApp/src` — no structured logging discipline, and these ship in release builds.
- `click-web`: **no ESLint config exists** and `eslint` is not a dependency. `npm run lint` is literally `tsc --noEmit`. There is therefore no `react-hooks/exhaustive-deps` check — notable given `DashboardView.tsx` alone has 22 `useEffect` hooks. 50 uses of `any` (`: any` / `as any`).

Agentic implication: a linter is the cheapest, fastest, most deterministic feedback an agent can act on autonomously. Without it, style and correctness drift compound silently across every AI-authored change.

### 3.2 God files in both codebases

| `click` | LOC | `click-web` | LOC |
|---|---|---|---|
| `viewmodel/ChatViewModel.kt` | **5155** | `components/DashboardView.tsx` | **3682** |
| `ui/screens/MapScreen.kt` | 3415 | `components/chat/ChatView.tsx` | 2778 |
| `ui/components/ProfileBottomSheet.kt` | 3293 | `components/UserProfileModal.tsx` | 2258 |
| `viewmodel/MapViewModel.kt` | 3167 | `app/api/connections/route.ts` | 1396 |
| `data/repository/SupabaseChatRepository.kt` | 2950 | `lib/server/proximity/bindProximityHandshake.ts` | 1351 |
| `App.kt` | 2556 | | |

`DashboardView.tsx` and `ChatView.tsx` are single default-exported client components (0 named exports) with 11/22 and 21/15 `useState`/`useEffect` hooks respectively — classic god components mixing fetching, view state, and rendering. `bindProximityHandshake.ts` is 1351 lines exporting **one** function.

This is the top agentic tax in the codebase. A 5155-line `ChatViewModel.kt` will not fit comfortably in a working context; agents will edit it partially, miss a second code path, and produce inconsistent state handling. It also blocks meaningful unit testing.

**Action:** treat >800 LOC as a refactor trigger. Split `ChatViewModel` by concern (send/receive, media, typing/presence, pagination, encryption); extract `DashboardView` sections into subcomponents plus custom hooks.

### 3.3 Test coverage is thin and inverted
- `click-web`: 37 test files vs 301 source files (~12%). Coverage is sensibly skewed to `lib/` (24 files: crypto, auth, proximity, rate limiting) and API contract tests (9) — good instincts. But **1 of 60 component files is tested**, and there is **no e2e layer at all** (no Playwright, no Cypress). `jest.config.ts` sets **no coverage thresholds**.
- `click`: 82 test files across `commonTest`/`androidUnitTest` — but they don't run in CI (§2.4), so coverage is unmeasured and unenforced.
- `click/server/`: no test suite whatsoever; `AGENTS.md` says "test manually with `curl`".

Tests are the primary mechanism by which an agent verifies its own work. Without a runnable, enforced suite, "done" is unverifiable — and the instruction to *"validate that changes don't break existing behavior"* becomes impossible to satisfy.

### 3.4 No input-validation schema layer in `click-web`
**Zero** imports of `zod` (or any schema library) across 85 API routes. Validation is ad-hoc (e.g. `app/api/waitlist/route.ts:9` does `if (!email || !email.includes('@'))`). There's also no shared error-envelope helper — `{ error: string }` is a convention, not enforced by code. Two routes (`app/api/qr/route.ts:35`, `app/api/waitlist/route.ts:16`) re-implement their own `createAdminClient()` instead of importing the shared one, so a future security hardening of the canonical helper would silently miss them.

Schema validation would be doubly valuable here: it defends the API *and* gives agents a machine-readable contract to code against, instead of inferring shapes from handler bodies.

### 3.5 Middleware rate limiting won't survive the deployment target
`middleware.ts` implements sliding-window rate limits in an in-memory `Map` (10/60s on `/api/connections` mutations; 60/60s read-heavy). On Cloudflare Workers each isolate has its own memory, so limits are per-isolate, not global — effectively unbounded under load. Needs Durable Objects, KV, or Upstash.

Separately, middleware explicitly **skips `supabase.auth.getUser()` for all `/api/*` paths** (lines 116-122, with a latency rationale), making every one of the 85 route handlers individually responsible for auth. That's a defensible performance call but a fragile one: one forgotten check is an open endpoint. It needs a lint rule, a shared `withAuth()` wrapper, or a contract test per route.

### 3.6 Documentation sprawl vs. durable reference
`click` tracks **69** markdown/text files. Roughly 25 are the excellent per-package READMEs. The rest is a mix of durable design docs (`docs/ui-ux/mobile/*` — 18 numbered files) and *point-in-time planning artifacts*: `docs/handoff/track-c-next-revamps.md`, `docs/handoff/functional-clarity-continuation.md`, `features.txt`, `quick_test_setup.md`, `PERFORMANCE.md`, `REGRESSION_CHECKLIST.md`, `database/PHASE4_LEGACY_NAME_REFERENCES.md`.

Handoff/continuation docs are ephemeral by nature and go stale within weeks, but an agent retrieving over the repo cannot tell them apart from authoritative ones. Recommend `docs/archive/` for anything time-bound, and a one-line "Status: durable | archived (as of YYYY-MM)" header on every doc.

### 3.7 Documentation inaccuracies that will actively mislead an agent
- `click/AGENTS.md`: Flask server described as "required" (§2.2) — false.
- `click-web/AGENTS.md`: lists `npm test:watch`, which fails ("Unknown command"); the real invocation is `npm run test:watch`.
- Neither `click-web/AGENTS.md`, `AI.md`, nor `README.md` mentions `npm run lint` at all — and doesn't disclose that it's type-checking only, not linting.
- `click-web/.cursor/.cursorrules` instructs the agent to use a `codebase-memory` MCP server for structural searches; that server is not present in this environment, so an agent following the rule literally will fail.

### 3.8 Repo hygiene
- `click` tracks **`output.zip`** and **`pepk.jar`** at root (`pepk.jar` is Google's Play encryption tool — should be downloaded, not vendored). `click-web` tracks **`decode-ico-0.4.1.tgz`**, an npm tarball checked into git.
- `click` tracks **24 files under `.idea/`**, including per-developer XML; `click-web` tracks `.idea/` (7 files) and `.vscode/settings.json`.
- `click` has personal identifiers in tracked Xcode files (`iosApp.xcodeproj/xcuserdata/timberlake2025.xcuserdatad/…`) — per-user Xcode state should never be committed.
- Positives: `click-web` has **0** TODO/FIXME/HACK markers and only 2 stray `console.log` calls (`components/SettingsView.tsx:337`, `lib/AuthContext.tsx:102`); `click` has only 2 TODO markers.

### 3.9 Commit hygiene
`click-web`'s recent history is 8 consecutive commits all prefixed `refactor:` with near-identical messages ("streamline environment variable access", "improve type handling and environment variable access"). `click`'s messages are prose-y and vague ("Refactor bottom sheet components for improved scrolling behavior and UI consistency" appears in near-duplicate form three times).

These read as machine-generated summaries of diffs rather than explanations of intent. Since `git log` is a primary retrieval surface for agents reconstructing *why* code looks the way it does, low-information commit messages compound over time. Adopt Conventional Commits with a mandatory "why" line.

---

## 4. Agentic-workflow scorecard

| Capability | State | Notes |
|---|---|---|
| Agent instruction files present | ✅ Strong | `AGENTS.md`, `AI.md`, `.cursorrules` in both repos |
| Anti-hallucination path maps | ✅ Strong | `AI.md` config-pointer table is best-in-class |
| Package-level orientation docs | ✅ Strong | 25 READMEs in `click/commonMain` |
| Documented guardrails ("do not…") | ✅ Good | VoIP/Firebase, raw_user_meta_data, auth UX |
| Accurate build/run commands | ⚠️ Partial | Flask "required" is false; `npm test:watch` broken |
| Fast local feedback loop | ❌ Weak | No linter in either repo; Gradle builds are slow |
| Automated verification (CI) | ❌ Weak | click-web: none. click: builds but skips 82 tests |
| Test suite an agent can trust | ⚠️ Partial | Exists but unenforced/unmeasured; no e2e |
| Context-sized modules | ❌ Weak | 5155-line ViewModel, 3682-line component |
| Machine-readable contracts | ❌ Weak | No zod; no shared API error/auth wrapper |
| Single source of truth | ❌ Weak | Forked migrations + Edge Function, already drifted |
| Durable vs ephemeral doc separation | ⚠️ Partial | Handoff/planning docs mixed into `docs/` |

**The pattern:** context *provision* is excellent; context *verification* is missing. Agentic workflows need both — good docs let an agent start correctly, but only fast deterministic feedback stops it from finishing incorrectly. Right now this codebase is optimized for the first half of the loop only.

---

## 5. Prioritized recommendations

### P0 — this week
1. **Fix the LAN-IP leak.** Delete `USE_LOCAL_SERVER`/`LOCAL_IP` from `ApiConfig.kt`; repoint or delete the 22 remaining `baseUrl` call sites in `ApiClient.kt`/`ChatApiClient.kt`. (§2.1)
2. **Decide the fate of `click/server/`.** Delete it, or deploy it and fix the client. Update `AGENTS.md` either way. (§2.2)
3. **Add CI to `click-web`:** a workflow running `npm ci && npm run lint && npm test && npm run build` on push and PR. (§2.3)
4. **Add `testDebugUnitTest` to `click`'s Android CI.** One line; unlocks 82 existing tests. (§2.4)
5. **De-duplicate `supabase/`.** Pick one source of truth, reconcile the `is_new_connection` default divergence, and add a CI drift check. (§2.5)

### P1 — this month
6. Add **ESLint** (`next/core-web-vitals` + `@typescript-eslint`) to `click-web`, and **ktlint or detekt** to `click`. Rename `lint` → `typecheck` and make `lint` mean lint.
7. Introduce **zod** for API request/response schemas plus a shared `withAuth()` route wrapper and a standard error envelope. (§3.4)
8. Move rate limiting off in-memory `Map` to Durable Objects / KV. (§3.5)
9. Fix the doc inaccuracies in §3.7; add the missing `lint` documentation.
10. Untrack `output.zip`, `pepk.jar`, `decode-ico-0.4.1.tgz`, `.idea/`, `.vscode/`, and `xcuserdata/`; add them to `.gitignore`.

### P2 — this quarter
11. Decompose the six god files (§3.2). Adopt an >800 LOC refactor trigger, enforced by a CI warning.
12. Set jest coverage thresholds (start at current level, ratchet up); add a Playwright smoke suite for signup → connect → chat.
13. Replace the 345 `println` calls with a leveled logger that is a no-op in release builds.
14. Move ephemeral handoff/planning docs to `docs/archive/`; add a status header to every doc. (§3.6)
15. Adopt Conventional Commits with a required rationale line. (§3.9)

---

## 6. Closing assessment

The product ambition here is real and the domain complexity is genuinely high — tri-factor proximity (BLE + 18.5 kHz ultrasonic + progressive GPS), E2EE group chat, CallKit/PushKit VoIP, LiveKit calls, cross-platform KMP. The team clearly understands the hard parts, and `AI.md` proves it by documenting exactly the traps that generic training data would lead an assistant straight into.

The gap is not knowledge — it's **enforcement**. Everything currently holding quality in place is a convention written in a markdown file: auth checks per route, `StateFlow` in ViewModels, migrations mirrored by hand, error shapes by habit, tests run by memory. Conventions degrade under velocity, and they degrade *fastest* under AI-assisted velocity, because agents generate plausible code far faster than humans can review it.

Converting conventions into automated gates — CI, linters, schema validation, a single source of truth for `supabase/` — is the highest-return work available. It's mostly a few days of setup, and it would move both repos from "well-documented but unverified" to genuinely agent-ready.
