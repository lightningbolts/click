# Engineering Evaluation — `click` (KMP mobile) & `click-web` (Next.js)

**Status:** durable
**Round 1:** 2026-08-10
**Round 2 (this revision):** 2026-08-11 — full re-audit after remediation
**Scope:** `click` @ `b6bde68d` · `click-web` @ `fce5fb9`
**Lens:** software engineering best practice, with emphasis on how well the codebase supports an **agentic (AI-assisted) development workflow**.

> **Method note (Round 2):** unlike Round 1, which was static inspection only, this round **executed** the quality gates. `click-web`'s typecheck, lint, and full test suite were run locally, and the Supabase drift script was run against the current sibling checkout. `click`'s Gradle tests could **not** be executed here (no Android SDK on this machine) and were assessed by configuration inspection only — see §5.

---

## 0. Executive summary

**The Round 1 findings were acted on, and acted on well.** Every P0 and nearly every P1 item is resolved. In 13 commits across the two repos, the team deleted the abandoned Flask server (−8,227 lines), removed the hardcoded LAN-IP API base, stood up CI for `click-web` where none existed, added ESLint + ktlint/Spotless, adopted Zod across every JSON mutation body, introduced a shared error envelope, moved rate limiting onto Cloudflare bindings, made `click-web/supabase` the enforced source of truth, and purged tracked binaries and IDE state.

More impressively, the fixes are **backed by enforcement rather than convention** — the pattern Round 1 identified as the core weakness. Two contract tests (`routeAuth.contract.test.ts`, `parseBody.contract.test.ts`) now fail the build if someone reintroduces a local `createAdminClient()` or a raw `request.json()`. That is exactly the right shape of fix for an agentic codebase: the rule is executable, not merely written down.

**Verified working (executed, not assumed):**

| Gate | Result |
|---|---|
| `npm run typecheck` | ✅ exit 0 |
| `npm run lint` | ✅ 0 errors, 196 warnings (budget 250) |
| `npm test --coverage` | ✅ 43 suites, **250 tests**, all pass |
| Coverage vs thresholds | ✅ 22.04% stmt / 19.5% br / 28.89% fn / 22.77% ln vs 20/18/25/20 floors |
| `scripts/check-supabase-drift.sh` | ✅ no drift across 25 shared files vs *updated* click-web |

**But three new problems were introduced by the remediation itself**, all of the same species: **a gate that appears green while testing nothing.**

1. 🔴 The Supabase drift check runs in CI but **never checks out `click-web`**, so it self-skips and exits 0. The gate is a permanent no-op in CI.
2. 🔴 `app/api/enrichment/event/route.ts` is **fail-open** — `verifyWebhookSecret()` returns `true` when `ENRICHMENT_WEBHOOK_SECRET` is unset, leaving a service-role-backed write endpoint fully unauthenticated if the env var is missing. It passes the auth contract test.
3. 🟠 `lib/server/withAuth.ts` (`requireUser`) is **dead code** — **0 of 85** routes use it, 0% coverage.

**Overall grade: B+** (was C+/B−). Guardrails went from largely absent to genuinely good. The remaining work is about making the new gates *actually bite*, and the untouched P2 backlog (six files still >2,000 LOC).

| Dimension | Round 1 | Round 2 | Δ |
|---|---|---|---|
| Agent-facing docs | A− / B+ | **A** | ↑ accurate + enforced |
| Architecture & layering | B / C+ | B / B− | ↑ Flask gone, API contracts |
| Test coverage & strategy | C / C | B− / C+ | ↑ CI runs them; still no e2e |
| CI/CD & quality gates | D+ / F | B / B | ↑↑ biggest movement |
| Static analysis / lint | F / D | B− / B | ↑↑ Spotless + ESLint |
| Security posture | C / B− | C+ / B− | ↑ mixed: LAN IP fixed, fail-open found |
| Repo hygiene | D+ / C+ | A− / A− | ↑↑ binaries + IDE state purged |
| Cross-repo coherence | D / D | B+ | ↑↑ SoT declared, drift verified clean |

---

## 1. What was fixed (verified)

### 1.1 ✅ P0 — LAN IP and the Flask server are gone
`ApiConfig.kt` is now 22 lines and contains no base URLs at all — it delegates to `CLICK_WEB_BASE_URL`, with an explicit guardrail comment: *"The legacy Flask server has been removed — do not reintroduce LAN/localhost API bases."* The entire `server/` tree (10 Python files + a tracked `server/.idea/`) was deleted in `4935119a`. `AGENTS.md` was corrected in the same sweep and now states "**No local Flask/API server is required**."

This closes the single most serious Round 1 defect: shipped chat code pointing at `http://10.19.165.221:5000`.

### 1.2 ✅ P0 — `click-web` now has CI
`.github/workflows/ci.yml` runs on every push and PR: `npm ci` → `typecheck` → `lint` → `test --coverage` → `next build` with dummy Supabase env vars. This is the correct gate ordering (cheapest signal first) and the build step proves the app compiles without real secrets.

### 1.3 ✅ P0 — `click` CI runs its tests
`android-ci.yml` gained `spotlessCheck` and `:composeApp:testDebugUnitTest` before `assembleDebug`. A new `ios-ci.yml` runs `:composeApp:iosSimulatorArm64Test` on `macos-26`/Xcode 26. The 82 test files that Round 1 flagged as never executing are now wired to CI.

### 1.4 ✅ P0 — Supabase drift is resolved at the content level
`scripts/check-supabase-drift.sh` + `sync-supabase-from-click-web.sh` were added, `supabase/README.md` documents the ownership split, and both `AGENTS.md` files declare `click-web/supabase` the source of truth. **I ran the drift check against the 5-commits-newer click-web: all 25 shared files are byte-identical, including `bind-proximity-connection/index.ts`.** The `is_new_connection` default divergence is genuinely resolved. (The CI *wiring* of this check is broken — see §2.1 — but the content is correct today.)

### 1.5 ✅ P1 — Linting exists in both repos
- `click-web`: `eslint.config.mjs` with `next/core-web-vitals` + TypeScript. `lint` is now real ESLint; `tsc --noEmit` was correctly renamed to `typecheck`. Legacy debt is set to `warn` with a `--max-warnings 250` budget — a **ratchet**, not a bulk suppression. Good judgement: it blocks *new* errors without demanding a 196-warning cleanup first.
- `click`: Spotless + ktlint with `ratchetFrom("origin/main")`, so only changed lines are enforced. Same pragmatic ratchet strategy.

### 1.6 ✅ P1 — API contracts are real now
Zod is a dependency; `lib/api/{parseBody,parseParams,errors}.ts` plus five schema modules under `lib/api/schemas/`. **Verified: 0 routes still call raw `request.json()`; 53 routes use `parseBody`.** The remaining 32 routes take no JSON body (cron, GET reads, webhooks), so **JSON mutation coverage is effectively complete**. `apiError()` gives a standard `{ error, code? }` envelope.

### 1.7 ✅ P1 — Rate limiting moved off in-memory Maps
`lib/server/rateLimit.ts` now prefers Cloudflare Workers **Rate Limiting bindings** (`CONNECTIONS_RATE_LIMITER`, `READ_HEAVY_RATE_LIMITER`, both declared in `wrangler.jsonc`), falling back to in-memory Maps only for `next dev`/CI. The fallback even bounds its own memory (evicts above 50k keys). This directly fixes the per-isolate limit escape identified in Round 1.

### 1.8 ✅ P1 — Hygiene
`output.zip`, `pepk.jar` (9.1 MB!), and `decode-ico-0.4.1.tgz` are deleted. **0** tracked files under `.idea/` or `xcuserdata/` in `click` (was 24 + personal Xcode state); `.vscode`/`.idea` untracked in `click-web`.

### 1.9 ✅ Documentation accuracy restored
Every Round 1 doc defect is fixed: the false "Flask required" claim is gone, `npm run test:watch` is correct, `lint` is documented in `click-web/AGENTS.md`, and both repos now document the supabase source-of-truth rule and the drift script.

---

## 2. New findings (introduced by, or surviving, the remediation)

### 2.1 🔴 The Supabase drift check is a no-op in CI

`android-ci.yml:51-53` runs `bash scripts/check-supabase-drift.sh` with `continue-on-error: false` — it looks like a hard gate. But **no step checks out `click-web`**, and the script's own logic is:

```bash
WEB_ROOT="${CLICK_WEB_ROOT:-$(cd "$ROOT/../click-web" 2>/dev/null && pwd || true)}"
if [[ -z "${WEB_ROOT}" || ! -d "${WEB_ROOT}/supabase" ]]; then
  ...
  if [[ "${REQUIRE_CLICK_WEB:-0}" == "1" ]]; then exit 1; fi
  echo "  Skipping drift check (REQUIRE_CLICK_WEB!=1)."
  exit 0
fi
```

`REQUIRE_CLICK_WEB` is never set in the workflow, so in CI the sibling is absent → **"Skipping drift check" → exit 0 → green check mark, zero verification.** Drift can silently reappear exactly as it did before, and the green tick will actively suppress suspicion.

The graceful-skip default is right for *local* runs; it is wrong for CI.

**Fix:** add a checkout step and require it:
```yaml
- name: Checkout click-web
  uses: actions/checkout@v4
  with: { repository: lightningbolts/click-web, path: click-web-sibling }
- name: Supabase drift check
  env:
    CLICK_WEB_ROOT: ${{ github.workspace }}/click-web-sibling
    REQUIRE_CLICK_WEB: "1"
  run: bash scripts/check-supabase-drift.sh
```
(If click-web is private, this needs a PAT — in which case invert the ownership and run the check from `click-web`'s CI, or have click-web's release job push the mirror.)

### 2.2 🔴 `enrichment/event` is fail-open, and the contract test can't see it

`app/api/enrichment/event/route.ts:21-26`:

```ts
function verifyWebhookSecret(request: NextRequest): boolean {
  const expected = process.env.ENRICHMENT_WEBHOOK_SECRET?.trim();
  if (!expected) return true;          // ← unset secret ⇒ everyone authorized
  const provided = request.headers.get('x-enrichment-secret')?.trim();
  return provided === expected;
}
```

If `ENRICHMENT_WEBHOOK_SECRET` is not configured in the deployed environment, this `POST` accepts **any unauthenticated caller** and then proceeds to `createAdminSupabaseClient()` — a **service-role** client that bypasses RLS — to write encounter enrichment data. A missing env var silently converts an authenticated webhook into an open write endpoint.

Security controls should **fail closed**. Given `click-web` also has a `health/env` route and builds with dummy env vars in CI, a missing-secret production deploy is a realistic scenario, not a hypothetical.

**Fix:** `if (!expected) return false;` and fail startup/health-check loudly if the secret is absent.

### 2.3 🟠 The auth contract test verifies string presence, not authorization

`__tests__/app/api/routeAuth.contract.test.ts` is a genuinely good idea — but its `AUTH_MARKERS` list contains **15 strings**, including `createAdminClient` and `createAdminSupabaseClient`. A route "passes" merely by *mentioning* a service-role client, which is the opposite of an authorization check. §2.2 is the proof: `enrichment/event` is fail-open and the test is satisfied because the file contains the substring `createAdminSupabaseClient`.

Four routes use an admin client with no user-session check: `waitlist`, `beacons/[id]/public`, `enrichment/event`, `qr`. Three are legitimately public; one is a bug the test was supposed to catch.

**Fix:** drop the two admin-client strings from `AUTH_MARKERS` (an admin client is *not* evidence of auth), and require those routes to carry an explicit `publicRoute` export or allowlist entry with a stated justification. That converts the test from "did you mention auth?" into "did you make a deliberate decision?"

### 2.4 🟠 `requireUser` / `withAuth.ts` is dead code

Created in the remediation, **used by 0 of 85 routes**, and coverage output confirms `withAuth.ts | 0 | 0 | 0 | 0 | 4-31`. Meanwhile 51 routes still call `getSupabaseFromRouteRequest` directly, and there are **506** ad-hoc `NextResponse.json({ error … })` sites versus 1 direct `apiError` call in routes (the envelope currently reaches routes only indirectly, via `parseBody` failures).

An unused abstraction is worse than none in an agentic workflow: an agent reading `lib/server/` sees two sanctioned patterns and no signal about which is current, so it will pick arbitrarily and widen the split. Either adopt `requireUser` across the 51 routes (mechanical, and the contract test already knows the symbol) or delete it.

### 2.5 🟠 Validation gaps remain outside JSON bodies

JSON bodies are fully covered, but:
- **18 routes read `searchParams`**, only **2** use `parseParams` — query strings are still ad hoc.
- **2 FormData upload routes** (`hub/media`, `user/avatar`) have no schema validation. File uploads are the higher-risk surface.
- Three parallel service-role factories still coexist (`createAdminClient`, `createAdminSupabaseClient`, `createSupabaseServiceRoleClient`) — the same forking problem as before, now one level up.
- `enrichment/event` re-validates `encounter_id`/`lat`/`lon` by hand *after* Zod already parsed them — harmless, but a sign the schema layer isn't fully trusted yet.

### 2.6 🟡 Coverage floors are thin, and components are excluded

Thresholds pass, but branch coverage clears its floor by only **1.5 points** (19.5% vs 18%) — the next lightly-tested module will break the build for reasons unrelated to the change. More importantly, `collectCoverageFrom` covers only `lib/**` and `app/api/**`, so **`components/` is excluded from measurement entirely** — and it remains **1 test file for 57 components**. Coverage is honest about what it measures; it just doesn't measure the largest, least-tested surface.

Still **no e2e layer** in either repo (no Playwright/Cypress). For a product whose core flow is a multi-device proximity handshake, at least one scripted signup → connect → chat path would catch what unit tests structurally cannot.

### 2.7 🟡 P2 backlog untouched (expected — it was scoped "this quarter")

| Metric | Round 1 | Round 2 |
|---|---|---|
| `ChatViewModel.kt` | 5,155 LOC | **5,155** |
| `MapScreen.kt` | 3,415 | **3,415** |
| `DashboardView.tsx` | 3,682 | **3,682** |
| `println(` in `composeApp` | 345 | **338** |
| `any` in click-web | 50 | **50** |
| Tracked `.md`/`.txt` in click | 69 | **71** |

Six files still exceed 2,000 LOC. This is now the **largest remaining agentic tax**: a 5,155-line ViewModel does not fit comfortably in a working context, so partial edits and missed code paths are likely. The new ktlint ratchet helps style but does nothing about size.

---

## 3. Agentic-workflow scorecard

| Capability | Round 1 | Round 2 | Evidence |
|---|---|---|---|
| Agent instruction files | ✅ Strong | ✅ Strong | `AGENTS.md`/`AI.md`/`.cursorrules` in both |
| Anti-hallucination path maps | ✅ Strong | ✅ Strong | `AI.md` config-pointer table |
| Package-level orientation docs | ✅ Strong | ✅ Strong | 25 READMEs under `commonMain` |
| Accurate build/run commands | ⚠️ Partial | ✅ **Fixed** | Flask claim gone; `test:watch` fixed; lint documented |
| Fast local feedback loop | ❌ Weak | ✅ **Good** | ESLint + Spotless; typecheck ~seconds |
| Automated verification (CI) | ❌ Weak | ✅ **Good** | click-web CI added; click runs tests + iOS job |
| Executable guardrails | ❌ None | ✅ **Strong** | 2 contract tests block regressions |
| Machine-readable contracts | ❌ Weak | ✅ Good | Zod on all JSON bodies; query/FormData pending |
| Single source of truth | ❌ Weak | ⚠️ **Declared, unenforced in CI** | §2.1 — drift gate self-skips |
| Gates that actually bite | — | ⚠️ **Mixed** | §2.1 no-op; §2.3 string-matching |
| Context-sized modules | ❌ Weak | ❌ **Unchanged** | 6 files >2,000 LOC |
| Test suite an agent can trust | ⚠️ Partial | ⚠️ Partial | 250 tests pass; components + e2e uncovered |

**The pattern has shifted.** Round 1's problem was *"conventions with no enforcement."* Round 2's is narrower and more subtle: *"enforcement that doesn't always execute."* A skipped drift check and a substring-matching auth test are arguably more dangerous than having neither, because a green check mark stops humans and agents from looking. Worth adopting as a team principle: **every new gate should be proven by watching it fail once.**

---

## 4. Prioritized recommendations

### P0 — this week
1. **Make the drift check run in CI** — check out `click-web` and set `REQUIRE_CLICK_WEB=1`. Verify by intentionally editing a mirrored file and confirming CI goes red. (§2.1)
2. **Fix the fail-open webhook** — `verifyWebhookSecret` must return `false` when the secret is unset. Audit for the same `if (!expected) return true` shape elsewhere. (§2.2)
3. **Harden the auth contract test** — remove `createAdminClient`/`createAdminSupabaseClient` from `AUTH_MARKERS`; force the 4 admin-without-session routes into an explicit, justified allowlist. (§2.3)

### P1 — this month
4. **Resolve `requireUser`:** adopt across the 51 authenticated routes, or delete it. Do not leave it at zero adoption. (§2.4)
5. Extend Zod to **query strings** (18 routes) and the **2 FormData upload routes**. (§2.5)
6. Consolidate the three service-role client factories into one. (§2.5)
7. Add `components/**` to `collectCoverageFrom` (accept the lower number), and give branch coverage more headroom before the floor bites. (§2.6)
8. Add a **single Playwright smoke test**: signup → connect → chat. (§2.6)

### P2 — this quarter (carried over)
9. Decompose the six >2,000-LOC files, starting with `ChatViewModel.kt` (5,155). Consider a CI **warning** at 800 LOC to stop new growth. (§2.7)
10. Replace 338 `println` calls with a leveled logger that no-ops in release builds.
11. Ratchet the ESLint budget down from 250 as warnings are cleared; ratchet coverage floors up.
12. Archive ephemeral `docs/handoff/*` planning artifacts (now 71 tracked markdown files).

---

## 5. Verification caveats

- `click-web` results are **executed**: `npm ci`, `npm run typecheck`, `npm run lint`, `npm test -- --ci --coverage` all run locally at `fce5fb9`, all passing.
- `scripts/check-supabase-drift.sh` was **executed** against the live sibling checkout — 25/25 files identical, exit 0.
- **`click`'s Gradle tests were NOT executed.** This machine has no Android SDK (`ANDROID_HOME` unset, no `/opt/android-sdk`), so `:composeApp:testDebugUnitTest` fails at configuration time with "SDK location not found." The Android CI workflow, Spotless config, and test wiring were reviewed statically and are correctly formed, but *"the 82 Kotlin tests pass"* remains **unverified** in this round. Someone with an SDK (or the CI run itself) should confirm the suite is green rather than merely wired.
- iOS build/test paths (`ios-ci.yml`, Xcode 26) cannot be validated on Linux.

---

## 6. Closing assessment

Round 1 argued that the codebase was *"well-documented but unverified"* — strong agent context, weak enforcement. One day later, most of that gap is closed, and closed thoughtfully: ratcheted linting instead of a disruptive mass reformat, contract tests instead of a wiki page, Cloudflare bindings instead of a `Map` with a TODO. The team converted advice into executable guarantees quickly and with good taste.

The new risk is subtler and worth naming clearly. **A gate that cannot fail is worse than a missing gate**, because it manufactures confidence. Two of the three new P0s are exactly that: a drift check that skips itself in CI, and an auth test satisfied by the mere mention of a service-role client — which is how a fail-open webhook slipped through the very test designed to catch it. The remedy is cheap and cultural: when you add a gate, break something on purpose and watch it go red before you trust it.

Do that, finish `requireUser`, and start breaking up the 5,000-line files, and both repos will be in genuinely good shape — well above the norm for a product at this stage, and unusually well-suited to AI-assisted development.
