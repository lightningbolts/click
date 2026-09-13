# Premium stabilization plan closure — 2026-09-13

This document is the canonical closure record for the stabilization / premium-flow work merged through PR #95 and the CI correction in PR #97.

## Scope and terminology

The original `GPT6_STABILIZATION_AND_PREMIUM_FLOW.md` was supplied to the implementation agent as an attachment and was not committed to this repository. Therefore this closure record does not invent requirements that are no longer recoverable. It uses the repository-tracked phase reports and the superseding PR #95 repair review as the authoritative implementation evidence.

`IMPLEMENTED` means the required source behavior exists and has targeted automated/source evidence. `DEVICE PENDING` means the implementation exists but the complete physical/simulator user journey has not yet been executed under the plan's performance, motion, premium-flow, and accessibility gates. A `DEVICE PENDING` row is not permission for a future agent to rewrite the feature without a reproduced defect.

## Implementation closure

### Authentication, JWT/session lifecycle — IMPLEMENTED

- Offline-first returning-session bootstrap is retained.
- Supabase is the live-session authority; app token storage is a persisted mirror rather than an independent auth source.
- `EnsureFreshAccessToken` refreshes missing/near-expiry access tokens and coordinates refresh rather than allowing independent request-level refresh races.
- `SessionRefreshCoordinator` and the resume gates serialize/coordinate session refresh behavior.
- Authenticated Click Web requests use the fresh-token provider.
- Targeted unit coverage exists for fresh-token and session-refresh coordination behavior.

Do not add a second token-refresh system or cache access tokens in feature repositories.

### Credential / crypto session isolation — IMPLEMENTED

PR #95's repair record is authoritative for logout/credential-scoped state, send-key ownership, key/session integrity, and account-switch isolation. Future changes must preserve user-scoped ownership and teardown behavior; a pending device journey is validation debt, not evidence that this implementation is absent.

### Motion and reduced-motion behavior — IMPLEMENTED at source/component level

- Platform reduce-motion preference is exposed through `rememberReduceMotionEnabled()`.
- iOS reads UIKit accessibility reduce-motion/reduce-transparency settings.
- Android derives the preference from platform animation scale.
- Navigation/primary hosts receive the reduce-motion state.
- Motion tokens/components provide non-spatial or static fallbacks rather than running decorative spring/pulse behavior unconditionally.

The full device journey remains an evidence gate where the regression matrix says `PENDING`.

### Accessibility and interaction repairs — IMPLEMENTED where recorded by PR #95

The superseding PR #95 repair review records targeted repairs including minimum interactive target sizing, photo/full-screen semantics, event guest-list wiring, and reduced-motion behavior. These repairs should only be reopened when a concrete regression is reproduced or a tracked acceptance criterion is demonstrably unmet.

### Premium-flow / visual continuity — IMPLEMENTED where recorded; DEVICE PENDING for end-to-end evidence

The phase reports and PR #95 review deliberately separate source/component repairs from end-to-end premium-flow evidence. Every shipped feature group must retain that distinction. `PENDING` in the regression matrix means the complete device journey was not run under all required gates; it does not mean a future agent should perform another broad UI rewrite.

A feature may be marked fully closed only after its required device journey passes:

1. correctness / no functional regression;
2. performance and absence of visible jank under the plan's gate;
3. normal-motion transition behavior;
4. reduced-motion behavior;
5. premium-flow continuity (no blank flash, teleport, stale overlay, or visibly broken state handoff);
6. accessibility behavior required by the matrix.

## CI policy after the September regression

PR-time CI should answer whether a change is safe to review and merge, not serially rebuild every release permutation.

The iOS PR critical path therefore contains distinct coverage only:

1. Supabase drift/security checks;
2. Kotlin iOS Simulator tests;
3. Kotlin iOS device-target compilation;
4. one complete Debug iOS Simulator app build.

Release Simulator and Release device Xcode builds remain required release evidence, but run as independent jobs on `main`/manual validation rather than serially extending every PR. iOS Maestro smoke remains post-merge/manual release evidence because it performs another complete Debug Simulator build before executing the smoke flow.

Do not restore serial Release Simulator + Release device builds to the PR critical path unless a concrete class of defect is shown to escape the faster gate and the expected coverage gain is documented.

## Remaining work before declaring the entire premium plan empirically closed

There is no repository-backed basis to claim that all end-to-end premium/device gates have passed. The superseding PR #95 review explicitly records those gates as pending across the shipped feature groups. The remaining work is therefore validation unless a device run reproduces an actual defect.

For each pending matrix row:

- execute the prescribed iOS/Android journey;
- record the result against the existing regression matrix;
- fix only reproduced failures;
- add the smallest durable automated regression test that can catch the failure without duplicating expensive whole-app builds;
- leave the implementation untouched when the journey passes.

## Agent completion rule

An agent working on this stabilization plan must not report "fully implemented" merely because compilation passes, and must not report "implementation missing" merely because a device evidence cell is `PENDING`.

Completion must be reported in two independent dimensions:

- **source implementation:** implemented / reproduced defect / missing requirement;
- **validation evidence:** automated pass / build-only / device pass / device pending.

This prevents both false closure and repeated broad rewrites that consume agent budget without increasing coverage.
