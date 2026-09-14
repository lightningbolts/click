# Premium Refinement and Interaction Stabilization Plan

Date: 2026-09-13

Status: implementation plan for the launch-readiness polish pass

Primary target: iOS physical-device experience, with Android behavior kept functionally equivalent and CI-safe

## 1. Objective

The current application has most of the required product surfaces, but it still presents as a collection of individually styled Compose screens rather than one coherent, premium social product. This pass is deliberately not a decorative redesign. The goal is to make interaction geometry, native chrome, hierarchy, loading behavior, and surface styling feel stable and intentional while fixing the remaining functional defects exposed after PR #98.

The reference quality bar is WhatsApp on current iOS: restrained surfaces, stable geometry, native navigation chrome, low visual noise, predictable sheet behavior, and controls that appear to persist and transform across navigation rather than blink between unrelated states. Click should retain its own purple identity; the reference is interaction quality and hierarchy, not visual cloning.

## 2. Non-negotiable product principles

1. **Geometry is stable.** Async data, reactions, keyboard changes, and status changes may replace pixels but should not unexpectedly move neighboring content.
2. **Native chrome is persistent.** On iOS, navigation and tab controls remain UIKit-owned. Root and pushed chrome share the same alignment grid so buttons can morph in place.
3. **Purple is semantic.** Brand purple indicates selected, primary, live, or actionable state. It is not a default border around every card.
4. **Containment is earned.** Use cards only when the information is genuinely grouped. Prefer spacing, typography, dividers, and imagery for ordinary feed/list hierarchy.
5. **Loading has a terminal state.** No control may remain in an indefinite `Preparing…` state. A bounded retry path must resolve to content or an actionable error.
6. **First interaction performance matters.** Cold/open-first-time chat, keyboard, sheet, and event detail transitions are part of the release gate, not only steady-state FPS.
7. **Accessibility and reduced motion remain first-class.** Do not trade off VoiceOver/TalkBack targets, contrast, Dynamic Type behavior, or reduced-motion behavior for visual polish.

## 3. Scope and implementation order

The work is intentionally ordered by shared primitives first. Screen-specific polish should consume the corrected primitives rather than work around them.

### Phase A — Functional blockers

#### A1. Event chat must be deterministic for RSVP members

Current behavior can leave an event detail showing `Preparing event chat…` indefinitely when a bookmark/proximity seed is missing `hub_id` and hydration fails or races. The server already creates event hubs and persists the event-to-hub relationship; the client should therefore treat a missing hub ID as a recoverable hydration error, not a normal long-running state.

Implementation:

- Align mobile and click-web event-hub authorization so an event creator or RSVP member can read/open the event hub. Active check-in remains valid context but is no longer the prerequisite for an RSVP member.
- Keep server-side authorization authoritative; do not grant access purely from optimistic mobile state.
- Continue sourcing the hub ID from the event detail payload first and engagement payload second.
- Add bounded client hydration retries for missing `hub_id`.
- After the retry budget is exhausted, replace the spinner with `Retry event chat`; never leave an infinite progress indicator.
- On successful RSVP, immediately refresh engagement/detail data so the event-chat CTA becomes actionable without closing/reopening the sheet.
- Update copy from `Check in to join event chat` to `RSVP to join event chat` where access is RSVP-gated.
- Add tests for host, RSVP-only, checked-in-only, unaffiliated, and missing-hub cases on both mobile and click-web.

Acceptance:

- Creator can open event chat immediately.
- An accepted RSVP member can open event chat without checking in.
- A non-RSVP viewer cannot open the event chat.
- A temporary detail-fetch failure produces a bounded retry and then an explicit retry action.
- Event detail never sits indefinitely on `Preparing event chat…`.

#### A2. Nearby lip must behave like the collapsed state of the sheet

The compact `Nearby` surface is currently a clickable card that opens a native sheet. It visually resembles a sheet lip but does not own an upward drag gesture.

Implementation:

- Preserve the map as the mounted background.
- Add a vertical drag recognizer to the lip.
- A deliberate upward drag crosses a small threshold and opens the existing `ClickPlatformSheet` at its medium detent.
- A tap continues to open the sheet.
- Native medium/large detents remain the source of truth after presentation.
- Avoid competing nested scroll handlers between the lip, map, native sheet, and result list.
- Keep bottom-safe-area/nav clearance derived from shared bottom chrome metrics.

Acceptance:

- Tap opens Nearby.
- Swipe up on the lip opens Nearby.
- Swipe/scroll inside the expanded sheet remains native and smooth.
- Dismiss returns to the same map camera state.
- Keyboard focus is cleared on dismissal/open-result exactly once.

## 4. Native header and Liquid Glass system

### B1. Compact header alignment

The compact root header currently attempts to place title and subtitle in the same horizontal stack. Long root titles such as the Home greeting therefore truncate awkwardly and compete with the search/action cluster.

Implementation:

- Compact root headers show a single-line compact title only.
- Hide the root subtitle once the header reaches compact mode; the subtitle belongs to the expanded identity state.
- Keep compact title vertically centered on the same chrome plane as leading/trailing controls.
- Preserve the 40pt control slot and minimum 44pt interaction target.
- Keep title compression below trailing controls; controls must never move to make room for text.
- Use short semantic compact titles where the expanded title is intentionally conversational. Home uses `Home` when compact while retaining the greeting in expanded state.
- Pushed identity headers keep name + presence/status as a vertical identity cluster where appropriate.
- Avoid animating bar height and page translation as independent competing transitions.

Acceptance:

- No compact root header shows a truncated greeting plus subtitle.
- Search/action buttons stay at stable X/Y coordinates during collapse.
- Chat identity header remains readable at Dynamic Type sizes supported by the app.
- No title material flashes during root/subscreen transitions.

### B2. Persistent chrome slots and in-place morphs

The implementation already keeps UIKit host layers alive; this pass formalizes the visual contract.

Slots:

- leading navigation/control slot
- optional identity/avatar slot
- title/identity text slot
- trailing action slot 1
- trailing action slot 2+

Rules:

- A control changing purpose should reuse the same UIButton whenever practical and swap symbol/menu/handler in place.
- Root and overlay geometry use the same leading/trailing insets and control sizes.
- Do not animate a Compose replacement over a native control.
- When a pushed screen covers a root screen, suppress/clip the underlay chrome without rematerializing it.
- Back/close swaps are symbol morph/crossfade events on the same glass control, not remove/add events.
- Trailing actions should update inside the existing trailing cluster.

Acceptance:

- Enter/exit of Settings subpages, chat, scanner/QR, and media overlays does not visibly teleport controls.
- Back/close/action controls occupy matching coordinates before and after navigation.
- Interactive swipe-back reveals the underlying chrome continuously with no stale title flash.

## 5. Bottom navigation and Me identity

Implementation:

- Rename the Settings tab to **Me** while retaining the existing `settings` route for compatibility.
- Use the signed-in user’s profile image as the Me tab icon when one is available; use a person-circle fallback otherwise.
- Keep the image circular and visually consistent with native tab-item icon bounds.
- Settings remains the underlying account/preferences implementation but the root surface is presented as the user’s identity/account home.
- Profile photo changes should refresh the tab item without recreating the entire tab bar.

Acceptance:

- Bottom navigation reads Home / Add Click / Clicks / Map / Me.
- Me shows the current profile photo after it is available.
- Photo update propagates without app restart.
- Tab bar remains native Liquid Glass on supported iOS versions.

## 6. Chat visual and interaction refinement

### C1. Composer

The current composer uses prominent purple focus borders plus bordered auxiliary circles, making the input look more like a form than a messenger.

Implementation:

- Move the composer to a quiet neutral field surface.
- Focus changes cursor/tint but does not produce a heavy purple outline.
- Attachment button uses a neutral circular surface without a structural purple/outline ring on iOS.
- Send button is neutral when unavailable and brand-filled only when send is actionable.
- Keep the composer height stable when the keyboard first opens.
- Retain the same composer primitive for direct and hub chats.

### C2. Bubbles and message attachments

Implementation:

- Slightly reduce bubble visual weight and padding.
- Keep outgoing brand color restrained enough that attachments/reactions remain legible.
- Continue reserving reaction geometry; extend the same no-reflow rule to reply previews, async media thumbnails, delivery state, and link/beacon attachments where feasible.
- Event/beacon attachments should read like message attachments, not full dashboard cards.
- Avoid purple borders around neutral incoming content.

### C3. Motion/performance

- First chat open must not wait for decorative animation setup.
- Keyboard and list animations must not run competing transforms.
- Reaction updates remain optimistic without row-height changes.
- Navigation transitions are interruptible and do not queue duplicate pushes.

Acceptance:

- First keyboard open has no visible layout jump.
- First reaction does not move neighboring messages.
- Composer looks neutral until an action is available.
- Hub and direct chat have one visual grammar.

## 7. Add Click / connect refinement

The connection surface should feel like a high-confidence action hub rather than a large branded promo tile followed by unrelated rows.

Implementation:

- Keep Tap to Connect primary, but reduce the saturated purple block treatment.
- Use a quiet elevated/tonal surface with the purple icon/action as the semantic focus.
- Shorten supporting copy and reduce the oversized empty area.
- Keep QR actions and community-hub actions as clean list rows with one divider system.
- Maintain large touch targets and preserve the existing BLE/audio connection path unchanged.
- The success state uses one clear success symbol, connection identity, and primary next action without extra border decoration.

Acceptance:

- Primary connection action remains immediately obvious.
- The page has one dominant accent rather than a full-screen purple hierarchy.
- QR/community options read as native secondary actions.

## 8. Event and map beacon refinement

### D1. Event detail information hierarchy

Target order:

1. hero/media + type/status
2. title and distance/status
3. essential schedule + location
4. attendee/host context
5. description/details
6. contextual actions

Implementation:

- Reduce nested outlined cards.
- Use one schedule group rather than two equally weighted bordered dashboard tiles where possible.
- Host and location become lighter rows unless they require standalone interaction.
- Keep share/save/more controls compact and aligned.
- People directory and guest tooling remain secondary to the attendee-facing event content.
- Keep destructive RSVP/cancel controls visually separated from primary event-chat/check-in actions.

### D2. Map markers

- Use smaller, quieter unselected event markers.
- Keep distinct marker families for event / person / hub / media.
- Selected marker gets the visual expansion/brand emphasis; unselected markers should not dominate the map labels.
- Cluster/count badges use restrained neutral/brand treatment instead of large neon circles where possible.
- Preserve map readability at regional zoom levels.

Acceptance:

- Event sheet is scannable without every datum looking like a card.
- The map remains the dominant layer; markers annotate it rather than obscure it.

## 9. Home refinement

Home should read as a social activity feed, not a dashboard of equal-weight bordered widgets.

Recommended hierarchy:

1. expanded greeting + search
2. current availability/intent
3. one most-relevant social prompt (featured event / reconnect / poll-pair)
4. recap/recent activity
5. saved/upcoming events
6. nearby discovery
7. lower-priority insights/stats

Implementation:

- Compact header title becomes `Home`; expanded state retains greeting.
- Reduce default card borders and use surface separation only where containment helps.
- Empty recap becomes a compact state rather than a large framed panel repeating the same instruction in title/body/button.
- Keep only one dominant CTA per section.
- De-emphasize connection analytics on the primary feed; statistics are secondary to people/actions.
- Use section spacing and typography before introducing another card.
- Avoid section insertion/layout shift as async data arrives; reserve known geometry or replace stable placeholders.

Acceptance:

- Above-the-fold content has one obvious primary action and no stack of equivalent purple rectangles.
- Recap/data hydration does not shift content after first paint.
- Feed remains useful with zero activity and with dense activity.

## 10. Me / settings refinement

Implementation:

- Root title becomes `Me`.
- Profile header is identity content rather than a settings card: photo, name/handle, compact edit action.
- Reduce the visual weight of the profile container and avoid framing the entire identity block.
- Preferences remain grouped list rows with subtle dividers.
- Subpages retain the persistent native header/back contract.
- Keep destructive actions (sign out, data deletion where applicable) separated from routine preferences.

Acceptance:

- Root reads as a profile/account tab, not a generic Settings utility page.
- Profile/photo/edit interaction remains fully functional.
- Settings subpage push/pop has no header flash or control teleport.

## 11. Shared surface/token cleanup

This pass should converge existing primitives rather than add one-off screen styling.

- Default neutral card border should be a hairline/subtle outline, not a strong purple contour.
- Primary border remains opt-in for selected/focused state.
- Default card radius, compact card radius, list divider indentation, control size, and spacing remain tokenized.
- Avoid new gradients unless the asset itself is media/artwork.
- Keep opaque content surfaces where text legibility requires them; reserve native Liquid Glass for native chrome and appropriate floating controls.
- Do not create fake Compose glass where UIKit owns the equivalent control on iOS.

## 12. Loading, error, and state choreography

Every async surface must explicitly define:

- initial cached state
- in-flight refresh state
- success replacement state
- recoverable failure state
- terminal/empty state

Rules:

- Prefer cached content + subtle refresh over blank spinner.
- Reserve final geometry when a section is known to exist.
- Spinner-only states must have a bounded duration or transition to retry/error.
- Network failure must not erase useful cached content.
- No section should appear several frames later and push the rest of the screen unless the content is truly optional/discovered.

## 13. Performance release gates

Physical-device checks should explicitly include:

- cold Home open and first scroll
- first Home → Clicks transition
- first chat open
- first keyboard open and close
- first reaction add/remove
- chat → list interactive back
- Settings/Me subpage open/back
- map pan/zoom while Nearby lip is visible
- swipe-up Nearby lip, medium → large, dismiss
- event detail cold open from map and saved event
- RSVP → event chat without closing the event sheet
- global search first keystroke/results

Failure criteria include visible frame hitch, duplicate navigation, header/button teleport, section layout shift, indefinite spinner, or stale authorization copy.

## 14. Automated test plan

### Mobile

- Event hub access policy unit tests: host, RSVP member, checked-in non-RSVP if supported, unaffiliated viewer, missing hub.
- Native header metrics tests: compact root single-line contract, compact subtitle suppression, stable control insets.
- Nearby lip gesture threshold pure-function tests if gesture math is extracted.
- Composer submit/state tests remain green.
- Existing reaction geometry tests remain green.
- Android `testDebugUnitTest` + `assembleDebug`.
- iOS simulator unit suite used by repository CI.

### click-web

- Event hub authorization unit tests updated to RSVP-based read access.
- Gatekeeper/API route contract tests continue to enforce server-side authorization.
- Event creation lifecycle still guarantees `hub_id` linkage.
- Lint/typecheck/test/build workflows remain green.

## 15. CI/CD and merge policy

- Create feature branches from current `main` in each affected repository.
- Keep the backend authorization change in click-web linked to the mobile PR because a mobile-only policy change would disagree with server enforcement.
- Do not merge visual/native-chrome changes without the repository-required physical-device screenshot/recording pass.
- A green compile is necessary but not sufficient for native chrome work.
- All pull-request GitHub Actions checks must pass. If a workflow is not configured to run on pull requests (for example deployment-only CD), record that explicitly rather than claiming it passed.
- Fix CI failures on the feature branch rather than disabling coverage or removing meaningful validation.

## 16. Definition of done

This pass is complete when:

- compact headers are aligned, concise, and stable;
- iOS native buttons retain/morph through consistent slots instead of visibly remounting;
- event chat is immediately available to authorized RSVP members and never hangs indefinitely;
- Nearby opens via tap and upward swipe and behaves as a real native sheet thereafter;
- bottom navigation uses `Me` and the user’s avatar;
- chat composer/bubbles/attachments have quieter messenger hierarchy;
- Home, Add Click, event detail, and Me use restrained surfaces instead of repeated purple outlines;
- no new loading/layout-shift regressions are introduced;
- mobile and backend automated checks are green;
- required physical-device visual evidence is captured before merge.
