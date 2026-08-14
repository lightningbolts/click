# Design System Foundation — Functional Clarity (Target State)

**Visual system:** Neo-brutalist Functional Clarity — opaque surfaces, 1dp quiet outline-variant borders, primary `#630ed4`, secondary `#224CFF`, no glass/blur/gradients.  
**Design tokens:** [../../design-assets/functional_clarity/DESIGN.md](../../design-assets/functional_clarity/DESIGN.md)  
**Compose source index:** `click/composeApp/src/commonMain/kotlin/compose/project/click/click/ui/` — existing `Glass*` API names may remain for churn; they **render as brutalist cards** in the target theme.

**Source index**

| Area | Path | Target rendering |
|------|------|------------------|
| Colors | `ui/theme/Color.kt` | Functional Clarity palette (see §1) |
| Typography | `ui/theme/Typography.kt` | Manrope M3 scale |
| Platform theme + color scheme | `ui/theme/PlatformTheme.kt` | `clickColorScheme` remapped to FC tokens |
| Screen chrome defaults | `ui/components/ScreenChrome.kt` | Solid headers, no blur |
| Glass sheet tokens | `ui/components/GlassSheetTokens.kt` | **→** opaque sheet surfaces + 2dp `#000` border |
| Glass cards | `ui/components/GlassCard.kt` | **→** bordered card (16dp radius, solid fill) |
| Adaptive cards/buttons | `ui/components/AdaptiveCard.kt` | **→** bordered surfaces, solid primary buttons |
| Liquid glass pill | `ui/components/LiquidGlassPill.kt` | **→** solid pill/chip (no gradient, no noise) |
| Sheet grabber | `ui/components/GlassSheetGrabber.kt` | **→** 40×4dp `#000` bar on solid sheet header |
| Sheet gesture physics | `ui/components/GlassSheetGesturePhysics.kt` | Unchanged behavior |
| Material modal sheet | `ui/components/GlassModalBottomSheet.kt` | **→** opaque sheet + hard border |
| Calf adaptive sheet | `ui/components/GlassAdaptiveBottomSheet.kt` | **→** opaque sheet + hard border |
| Unified popup | `ui/components/UnifiedPopup.kt` | **→** bordered dialog card |
| Unified toast | `ui/components/UnifiedToast.kt` | **→** bordered compact pill |
| Chat bubble tokens | `ui/chat/ChatBubbleTokens.kt` | Solid sent/received fills |
| Chat bubble paint | `ui/chat/ChatMessageBubble.kt` | No gradient bubbles |

---

## 1. Color Palette

### 1.1 Layout / Container

- **Brand tokens** (Functional Clarity): `primary` `#630ed4`, `on-primary` `#ffffff`, `primary-container` `#7c3aed`, `on-primary-container` `#ede0ff`, `surface-tint` `#732ee4`.
- **Light mode**: `background` `#f9f9f9`, `surface` `#ffffff`, `surface-container-low` `#f3f3f4`, `surface-container` `#eeeeee`, `on-surface` `#1a1c1c`, `on-surface-variant` `#4a4455`, `outline` `#7b7487`.
- **Dark mode (inverse)**: `background` `#101212`, `surface` `#1a1c1c`, `surface-container` `#242626`, `surface-variant` `#2a2c2c`, `on-surface` `#f0f1f1`, `on-surface-variant` `#d6d9d9` — **opaque product surfaces + hard borders**; tab bar chrome is **transparent** so content under icons matches content above.
- **Structural border**: **1dp** quiet edge — `#CCC3D8` (`BorderQuiet`) in light mode, `#4A3D5C` (`BorderQuietDark`) in dark mode — on cards, sheets, and inputs. Use `clickBorderColor()` / `clickBorderWidth()` / `clickBorderStroke()`. Keep **2dp** only for selected / focus / primary rings. Tab bar does **not** draw a filled top border band (see known issue `#23`).
- **Secondary accent**: `#224CFF` (`SecondaryAccent`) for events / map / non-CTA emphasis. Material `secondary` maps to this token.
- **Accent ratio:** Purple stays dominant. Shared `ClickAccent` / `accentColor(AccentRole)` plus `generateCardVisual` hash buckets are **5/8 purple, 3/8 blue** (~62.5 / 37.5), inside the product 60/40–65/35 band. Do not hardcode per-screen purple/blue mixes.
- **Generated card / beacon visuals:** `generateCardVisual(id)` (common) may paint 2–3 stop gradients + a subtle pattern **only** on pile Polaroids, beacon pins, and beacon detail identity banners. Always draw `contentScrim` behind text. Chrome, buttons, and list cards stay solid Functional Clarity (no gradients).
- **No glass primitives**: no `GlassWhite` alpha fills, no `GlassBorder` hairlines, no backdrop blur on product chrome.
- **No gradient text**: headings use solid `on-surface` or `primary`; no `GradientTextStart`/`End` usage.
- **Material color scheme** (`PlatformTheme.kt` `clickColorScheme`):
  - Light: `primary` = `#630ed4`, `background` = `#f9f9f9`, `surface` = `#ffffff`, `onSurface` = `#1a1c1c`, `primaryContainer` = `#ede0ff`, `onPrimaryContainer` = `#5a00c6`.
  - Dark: `primary` = `#630ed4`, `background` = `#101212`, `surface` = `#1a1c1c`, `onSurface` = `#f0f1f1`, `onSurfaceVariant` = `#d6d9d9`, `surfaceVariant` = `#2a2c2c`.
- **App root background** (`App.kt`): flat `background` color only — **no radial gradient** overlay in dark mode.
- **Map basemap**: light app theme → default color map tiles; dark app theme → dark / zinc style; ghost mode → grayscale (Android) or muted emphasis (iOS).

### 1.2 Interactive Elements

- Primary actions: solid `primary` `#630ed4` fill, `on-primary` `#ffffff` bold label, **8dp** corner radius, **2dp** `#000` border optional on secondary variants.
- Secondary actions: solid `#ffffff` fill, **2dp** `#000` border, `on-surface` bold text.
- Accent chips/tags: solid `primary-container` or `surface-container` fills with `on-primary-container` / `on-surface` text — no glow.

### 1.3 States

| State | Light | Dark |
|-------|-------|------|
| **Default** | Background `#f9f9f9`, Surface `#ffffff`, OnSurface `#1a1c1c` | Background `#101212`, Surface `#1a1c1c`, OnSurface `#f0f1f1` |
| **Pressed/Highlighted** | **2dp translate** down-right + **instant 10% darken** on fill (no animation curve) | Same mechanical press |
| **Active** | `primary` fill or `primary-container` for selected tabs/chips | Same |
| **Focus** | **2dp** `#630ed4` focus ring on inputs (no soft glow) | Same |
| **Disabled** | `on-surface` at 38% on `surface-container` fill; border retained at 50% | Same |
| **Loading** | Flat `background`; spinners use `primary` `#630ed4` | Same |
| **Empty** | `on-surface-variant` `#4a4455` for hint copy | Same |
| **Error** | `error` `#ba1a1a` on destructive actions | Same |
| **Success** | Solid `primary` sent bubbles / confirm buttons — **no gradient** | Same |

### 1.4 Micro-copy

- Color tokens are not user-facing strings; copy inherits `MaterialTheme.typography` and `onSurface` / `onSurfaceVariant`.

### 1.5 Flow Sequence

1. `PlatformThemeProvider(isDarkMode)` wraps authenticated and unauthenticated trees.
2. `clickColorScheme(isDarkMode)` supplies M3 semantic colors mapped to Functional Clarity.
3. `PlatformStyleProvider` resolves iOS vs Android press/ripple deltas (see §3).
4. Components read `MaterialTheme.colorScheme` and `LocalPlatformStyle.current`.

### 1.6 A11y & Responsive

- Text contrast: `on-surface` `#1a1c1c` on `surface` `#ffffff`; dark inverse meets WCAG on opaque fills.
- `on-surface-variant` `#4a4455` for secondary labels; minimum **14px** label size enforced.
- No hard-coded font sizes in `Color.kt`; all type scales via Typography.
- Borders aid low-vision edge detection — do not remove for "cleaner" look.

---

## 2. Typography

### 2.1 Layout / Container

- **Font family**: Manrope only — weights Normal (500), SemiBold (600), Bold (700), ExtraBold (800) from Compose resources.
- **Scale** (Functional Clarity editorial hierarchy):

| Role | Size | Weight | Line height |
|------|------|--------|-------------|
| `display-lg` | 48px | 800 | 52px |
| `headline-lg` | 32px | 700 | 40px |
| `headline-md` | 24px | 700 | 32px |
| `body-lg` | 18px | 500 | 28px |
| `body-md` | 16px | 500 | 24px |
| `label-bold` | 14px | 700 | 20px |
| `label-md` | 14px | 600 | 20px |

- M3 role mapping: map `titleLarge` → `headline-md`, `bodyMedium` → `body-md`, `labelLarge` → `label-bold`, etc.
- **No italics**; hierarchy via weight and size only.

### 2.2 Interactive Elements

- Buttons use `label-bold` (14px / 700).
- Section headers use `headline-md` or `label-bold` uppercase for category labels.
- Chat bubbles apply local scale multipliers on top of M3 roles (see Chat Bubble Tokens).

### 2.3 States

| State | Behavior |
|-------|----------|
| **Default** | Manrope at FC metrics for each role |
| **Pressed/Highlighted** | No typography change |
| **Active** | `FontWeight.Bold` on selected tab labels |
| **Focus** | No type change |
| **Disabled** | M3 disabled content alpha (38%) |
| **Loading** | `body-md` on loading captions |
| **Empty** | `body-md` / `label-md` for hints |
| **Error** | Standard body styles |
| **Success** | Standard body styles |

### 2.4 Micro-copy

- Typography carries no fixed strings.

### 2.5 Flow Sequence

`clickTypography()` → `MaterialTheme(typography = …)` inside `PlatformThemeProvider`.

### 2.6 A11y & Responsive

- Manrope scales with system font size (M3 default).
- `AppScreenScaffold` floating header measures expanded body height under accessibility scaling before locking scroll inset.

---

## 3. PlatformStyle (iOS vs Android)

### 3.1 Layout / Container

`PlatformStyle` (`PlatformTheme.kt`) exposed via `LocalPlatformStyle`:

| Property | iOS | Android |
|----------|-----|---------|
| `cardCornerRadius` | **16 dp** | **16 dp** |
| `compactCardCornerRadius` | **8 dp** | **8 dp** |
| `buttonCornerRadius` | **8 dp** | **8 dp** |
| `cardBorderWidth` | **1 dp** | **1 dp** |
| `borderColor` | quiet outline-variant | quiet outline-variant |
| `useShadowElevation` | **false** | **false** |
| `useRipple` | **false** | **true** |
| `pressTranslateDp` | **2 dp** | **2 dp** |

Detection: `getPlatform().name.contains("iOS")`.

### 3.2 Interactive Elements

- Shared primitives (reuse these; do not invent one-off chrome): `ClickButton` / `ClickButtonVariant`, `ClickOutlinedTextField` / `ClickSearchField` / `ClickFieldTokens`, `ClickDropdownMenu` / `ClickMenuItem`, `AdaptiveCard` / `GlassCard`.
- `GlassCard`, `GlassCardCompact`, `GlassSurface` → solid fill + **1dp** quiet border (API names unchanged).
- `ClickButton` / `AdaptiveButton` → solid `primary` fill; secondary variant uses 1dp quiet border; press = 2dp translate + instant darken.
- Card-level `clickable`: `indication = null` on iOS; Android may show ripple on M3 buttons when `useRipple` true.

### 3.3 States

| State | iOS | Android |
|-------|-----|---------|
| **Default** | Opaque surface + hard border | Opaque surface + hard border |
| **Pressed/Highlighted** | 2dp translate + instant darken | 2dp translate + instant darken; ripple on buttons only |
| **Active** | `primary` border or fill | Same |
| **Focus** | 2dp `primary` ring | Same |
| **Disabled** | Gray `surface-container` fill, 38% content | Same + optional ripple suppressed |
| **Loading** | N/A | N/A |
| **Empty** | N/A | N/A |
| **Error** | N/A | N/A |
| **Success** | N/A | N/A |

### 3.4 Micro-copy

- None.

### 3.5 Flow Sequence

`PlatformStyleProvider` → child composables read `LocalPlatformStyle.current`.

### 3.6 A11y & Responsive

- Identical corner radii and border widths across platforms.
- iOS omits ripple; Android retains Material ripple on standard buttons only.

---

## 4. AppScreenDefaults & Screen Chrome

### 4.1 Layout / Container

`AppScreenDefaults` (`ScreenChrome.kt`):

| Token | Value |
|-------|-------|
| `HorizontalPadding` | 16 dp (`margin-mobile`) |
| `SectionSpacing` | 24 dp |
| `HeaderCollapseScrollThreshold` | 96 px |
| `FloatingHeaderLargeHeight` | 112 dp (initial fallback) |
| `FloatingHeaderCompactHeight` | 52 dp |
| `ExtraScrollBottomPadding` | 16 dp |
| `IosTabBarContentHeight` | 49 dp |
| `AndroidNavBarContentHeight` | 80 dp |
| `FabGapAboveTabBar` | 6 dp |

`AppScreenChromeState.bottomChromeHeight` — mutable, updated by `PlatformBottomBar`.

Helpers: `rememberTabBarOverlayHeight()`, `rememberBottomChromePadding()`, `rememberStatusBarTopPadding()`, `rememberFabAboveNavPadding()`, `rememberComposerBottomPadding()`, `chatComposerDock`, `chatThreadKeyboardDock`.

### 4.2 Interactive Elements

- Floating headers: `LiquidGlassPageHeader` API → **solid header bar** with **2dp** bottom `#000` border (no blur).
- FABs sit `FabGapAboveTabBar` above measured tab bar top.
- Chat composers dock above tab bar; iOS uses `maxOf(tabStack, imeBottom)`.

### 4.3 States

| State | Behavior |
|-------|----------|
| **Default** | Bottom chrome = measured tab/nav height + optional 16 dp scroll padding |
| **Pressed/Highlighted** | N/A |
| **Active** | Header `collapseFraction` 0→1 over 96 px scroll |
| **Focus** | IME lifts chat via `graphicsLayer` (iOS) or `offset` (Android) |
| **Disabled** | N/A |
| **Loading** | N/A |
| **Empty** | N/A |
| **Error** | N/A |
| **Success** | N/A |

**Header hide hysteresis**: 32 px slack before re-showing hidden floating header.

### 4.4 Micro-copy

- None at chrome layer.

### 4.5 Flow Sequence

1. `PlatformBottomBar` measures and calls `AppScreenChromeState.updateBottomChromeHeight`.
2. Tab-root screens use `bottomChromePadding()` or scaffold content padding.
3. Collapsing header screens use `rememberFloatingHeaderTopPadding`.

### 4.6 A11y & Responsive

- iOS status bar: `WindowInsets.statusBars` only for floating headers.
- Android: `max(statusBars, safeDrawing top)` with 24 dp fallback on first frame zero.
- `rememberIosTabBarStackHeight()` = navigation bar inset + 49 dp minimum.

---

## 5. GlassSheetTokens (→ Opaque Sheet Tokens)

### 5.1 Layout / Container

`GlassSheetTokens` object — **target mapping**:

| Token | As-built (legacy) | Target (Functional Clarity) |
|-------|-------------------|----------------------------|
| `OledBlack` | `#000000` glass shell | `surface` `#ffffff` (light) / `#2f3131` (dark) |
| `GlassSurface` | White 5% | `surface-container-low` solid |
| `GlassBorder` | White 12% | `#000000` **2dp** |
| `GlassBorderPressed` | White 22% | `#000000` **2dp** + 2dp translate |
| `OnOled` | White 92% | `on-surface` |
| `OnOledMuted` | White 62% | `on-surface-variant` |
| `SheetTopCorner` | 32 dp | **16 dp** |
| `BentoExteriorCorner` | 28 dp | **16 dp** |
| `BentoInteriorCorner` | 8 dp | **8 dp** |
| `ScrimBaseAlpha` | 0.58 | **0.40** solid black dim (no blur) |

### 5.2 Interactive Elements

- Sheet shells: opaque fill + **2dp** `#000` top/side border.
- Scrim: flat black at 40% — **no backdrop blur**.

### 5.3 States

| State | Token |
|-------|-------|
| **Default** | Solid `surface` + `border` 2dp `#000` |
| **Pressed/Highlighted** | 2dp translate + instant darken on sheet chrome rows |
| **Active** | Expanded sheet at full scrim |
| **Focus** | 2dp `primary` ring on fields |
| **Disabled** | N/A |
| **Loading** | N/A |
| **Empty** | N/A |
| **Error** | N/A |
| **Success** | N/A |

### 5.4 Micro-copy

- Content uses `on-surface` / `on-surface-variant` on opaque shells.

### 5.5 Flow Sequence

Shared by `GlassModalBottomSheet`, `GlassAdaptiveBottomSheet`, `UnifiedPopup`, `UnifiedToast`.

### 5.6 A11y & Responsive

- `SheetTopCorner` 16 dp top radius on modal sheets.
- Bento exterior 16 dp matches `GlassCard` corner constant.

---

## 6. GlassCard & GlassCardCompact (→ Bordered Card)

### 6.1 Layout / Container

- **GlassCard** / **GlassSurface**: corner = **16 dp**; constant `GlassCardShape` = `RoundedCornerShape(16.dp)`.
- **GlassCardCompact**: **8 dp** radius.
- Default `contentPadding`: 16 dp (compact: 12 dp).
- Background: solid `surface` `#ffffff` (light) or `surface-container` (dark).
- Border: **2 dp** solid `#000000`; `usePrimaryBorder = true` → **2 dp** `#630ed4` border.
- **No** `glassEffect` alpha modifier — removed in target theme.

### 6.2 Interactive Elements

- Optional `onClick`: `clickable` with press = **2dp translate** + instant 10% darken.
- `GlassSurface` uses M3 `Surface` with `shadowElevation = 0.dp`.

### 6.3 States

| State | Visual |
|-------|--------|
| **Default** | Solid fill + 2dp `#000` border |
| **Pressed/Highlighted** | 2dp translate down-right + 10% darken |
| **Active** | `usePrimaryBorder` → `#630ed4` border |
| **Focus** | 2dp `primary` ring |
| **Disabled** | Non-clickable; 38% content alpha |
| **Loading** | N/A |
| **Empty** | N/A |
| **Error** | N/A |
| **Success** | N/A |

### 6.4 Micro-copy

- Consumer-provided.

### 6.5 Flow Sequence

`LocalPlatformStyle` → shape/border → optional click with mechanical press.

### 6.6 A11y & Responsive

- 16 dp corners on standard cards; 8 dp for dense rows.
- 2 dp borders on both platforms.

---

## 7. AdaptiveCard & AdaptiveButton

### 7.1 Layout / Container

- **AdaptiveCard**: corner = **16 dp**; padding = 16 dp.
- Surface: solid `surface` — **no alpha**.
- Border: **2 dp** `#000000`.
- **AdaptiveSurface**: bottom-rounded sheet shape, solid `surface`.
- **AdaptiveBackground**: flat `background` `#f9f9f9` (no transparent layer).

### 7.2 Interactive Elements

- **AdaptiveButton**:
  - Primary: solid `#630ed4` fill, `#ffffff` bold text, **8 dp** corner, **0 elevation**.
  - Secondary: solid `#ffffff` fill, **2 dp** `#000` border, `#1a1c1c` text.
  - Press: **2dp translate** + instant 10% darken.

### 7.3 States

| State | AdaptiveButton |
|-------|----------------|
| **Default** | Solid primary or bordered secondary |
| **Pressed/Highlighted** | 2dp translate + instant darken |
| **Active** | N/A |
| **Focus** | 2dp `primary` ring |
| **Disabled** | `surface-container` fill, 38% content |
| **Loading** | N/A |
| **Empty** | N/A |
| **Error** | N/A |
| **Success** | N/A |

### 7.4 Micro-copy

- Consumer-provided button labels.

### 7.5 Flow Sequence

Platform branch inside composable → `RoundedCornerShape(8.dp)` for buttons, `16.dp` for cards.

### 7.6 A11y & Responsive

- Disabled state uses explicit gray — not theme onSurface disabled alone.

---

## 8. LiquidGlassPill (→ Solid Pill)

### 8.1 Layout / Container

- Default `cornerRadiusDp` = **24** (grabber wrapper 22, toast overlay 28).
- Inner padding: horizontal 14 dp, vertical 8 dp.
- Background: solid `surface` or `surface-container` — **no vertical gradient**.
- Border: **2 dp** `#000000`.
- **No** procedural noise layer.
- **No** external `Modifier.blur`.

### 8.2 Interactive Elements

- Non-interactive container; children may be buttons/text.

### 8.3 States

| State | Visual |
|-------|--------|
| **Default** | Solid pill on map overlay |
| **Pressed/Highlighted** | Child button press semantics |
| **Active** | N/A |
| **Focus** | N/A |
| **Disabled** | N/A |
| **Loading** | N/A |
| **Empty** | N/A |
| **Error** | N/A |
| **Success** | `UnifiedToastOverlay` — solid `surface` + border |

### 8.4 Micro-copy

- Map memory count pills, toast overlay body, grabber chrome.

### 8.5 Flow Sequence

Clip → solid fill → border → padded content.

### 8.6 A11y & Responsive

- High-contrast border ensures pill read at small sizes without blur fallback.

---

## 9. GlassSheetGrabber

### 9.1 Layout / Container

- Full width; padding top 10 dp, bottom 6 dp.
- Inner bar: 40×4 dp, `RoundedCornerShape(50)`, solid `#000000` at 40% (light) / `#f0f1f1` at 60% (dark).
- **No** `LiquidGlassPill` wrapper — plain centered bar on solid sheet header.

### 9.2 Interactive Elements

- Drag handle for modal/adaptive sheets; gesture handled by sheet state.

### 9.3 States

| State | Behavior |
|-------|----------|
| **Default** | Visible grabber bar |
| **Pressed/Highlighted** | Sheet drag offset (parent) |
| **Active** | Sheet expanding |
| **Focus** | N/A |
| **Disabled** | Hidden when sheet non-draggable |
| **Loading** | N/A |
| **Empty** | N/A |
| **Error** | N/A |
| **Success** | N/A |

### 9.4 Micro-copy

- None (decorative).

### 9.5 Flow Sequence

Sheet `dragHandle = { GlassSheetGrabber() }`.

### 9.6 A11y & Responsive

- 40 dp wide × 4 dp tall affordance centered.

---

## 10. GlassModalBottomSheet (Material ModalBottomSheet)

### 10.1 Layout / Container

- `containerColor` = opaque `surface`; `contentColor` = `on-surface`.
- Shape: top corners **16 dp**.
- `tonalElevation` = 0; **no shadow**.
- Border: **2 dp** `#000` on top and sides.
- Default `sheetState` = `rememberGlassModalBottomSheetState()` (travel 420 dp).
- Scrim: `Color.Black` at **0.40** flat — no blur, no animated scrim gradient.

### 10.2 Interactive Elements

- `GlassSheetGrabber` drag handle.
- `confirmValueChange` on hide: commit if offset > 50% travel OR velocity > 800 px/s.

### 10.3 States

| State | Behavior |
|-------|----------|
| **Default** | Hidden / collapsed |
| **Pressed/Highlighted** | Dragging — scrim static at 40% |
| **Active** | Expanded sheet |
| **Focus** | Field focus rings inside sheet |
| **Disabled** | N/A |
| **Loading** | N/A |
| **Empty** | Consumer empty state |
| **Error** | Consumer error state |
| **Success** | Dismiss on commit |

### 10.4 Micro-copy

- Consumer content on opaque shell.

### 10.5 Flow Sequence

`ModalBottomSheet` → grabber + column content; scrim is flat dim.

### 10.6 A11y & Responsive

- `BottomSheetDefaults.windowInsets` default for safe area.
- Flick-dismiss threshold 800 px/s.

---

## 11. Click bottom sheets + GlassAdaptiveBottomSheet

**Sources:** `ui/components/ClickBottomSheet.kt`, `ui/components/GlassAdaptiveBottomSheet.kt`, `ui/sheet/MapBeaconSheetRoot.kt`

| Family | Entry points | Target shell |
|--------|--------------|--------------|
| **Click sheets** | `ClickPlatformSheet`, `ClickActionBottomSheet`, `ClickFormBottomSheet` | Opaque `surface` + 2dp border |
| **Glass adaptive** | `GlassAdaptiveBottomSheet` | Opaque `surface` + 2dp border |

### 11.1 Layout / Container — Click sheets

**`ClickSheetDefaults`:**

| Token | Value |
|-------|-------|
| `ContentHorizontalPadding` | 20 dp |
| `ContentBottomPadding` | 24 dp |
| `TitleBottomSpacing` | 12 dp |
| `ScrimAlpha` | **0.40** (flat, no blur) |

**Hierarchy:**

```
ClickActionBottomSheet / ClickFormBottomSheet
 └── ClickPlatformSheet
      └── MapBeaconSheetRoot (opaque surface, scrim Black@40%, zero window insets)
           └── ClickSheetDialogChrome (grabber + semantic color remap)
                └── Column content
```

**`GlassAdaptiveBottomSheet`:** Calf adaptive with opaque `surface` / `on-surface`; scrim 40%; positional threshold 56 dp; velocity 800 px/s.

### 11.2 Interactive Elements

- Platform drag-to-dismiss and back dismiss via sheet root.
- Grabber via `ClickSheetDialogChrome` / `GlassSheetGrabber`.

### 11.3 States

| State | Behavior |
|-------|----------|
| **Default** | Opaque sheet expanded / medium detent |
| **Pressed/Highlighted** | 2dp translate on row press |
| **Active** | Sheet visible; flat dim underlay |
| **Focus** | 2dp primary ring on fields |
| **Disabled** | N/A at shell |
| **Loading** | Caller spinner inside content |
| **Empty** | Caller empty copy |
| **Error** | Caller inline error |
| **Success** | Dismiss after action |

### 11.4 Micro-copy

- Titles supplied by callers (`ClickSheetChrome(title = …)`).

### 11.5 Flow Sequence

Caller opens sheet → platform presents opaque shell → dismiss on action or drag. Nested
sheets (directory, share, create-click) stack on the active page sheet; sibling top-level
sheets replace. Grabber clearance padding is applied *after* the sheet background so the
system grabber never reveals a black host strip.

### 11.6 A11y & Responsive

- iOS: UIKit `UISheetPresentationController` with native presentation material over the
  active app-themed page surface; scroll-hosted bodies measure unbounded height so the
  UIScrollView can reach footers (RSVP, Join Event Route, Cancel). Nested sheets stack;
  profile LazyColumn sheets opt out of the scroll host. Create-click / share-beacon use the
  same Drop-beacon scroll-host + `sheetBodyScroll` contract.
- Android: Calf adaptive, half-height cap for Click sheets.

---

## 12. UnifiedPopup

### 12.1 Layout / Container

**UnifiedPopupCard**: `BentoExteriorCorner` (**16 dp**), **2 dp** `#000` border, solid `surface` fill; padding H 18 / V 16; margin 22 dp.

**UnifiedPopupOverlay**: full-screen scrim **40%** black — no blur.

Motion tokens unchanged (`FadeInMillis` 320, `ScaleInInitial` 0.92, etc.).

### 12.2 Interactive Elements

- Scrim tap dismisses (animated).
- `PlatformBackHandler` when overlay visible.
- Alert: title (`titleMedium` on-surface), text (`body-md` on-surface-variant).

### 12.3 States

| State | Behavior |
|-------|----------|
| **Default** | Hidden |
| **Pressed/Highlighted** | Button mechanical press |
| **Active** | Overlay visible |
| **Focus** | `Popup` focusable on alert |
| **Disabled** | N/A |
| **Loading** | Consumer body |
| **Empty** | N/A |
| **Error** | Consumer copy |
| **Success** | Confirm → animated dismiss |

### 12.4 Micro-copy

- Default dismiss: `"Cancel"` (`UnifiedPopupFormDialog`).

### 12.5 Flow Sequence

Animate in → interact → animate out → `onDismissRequest`.

### 12.6 A11y & Responsive

- `PopupProperties(focusable, dismissOnBackPress, dismissOnClickOutside)` on alert.
- Form dialog `contentMaxWidth` 360 dp on phones.

---

## 13. UnifiedToast

### 13.1 Layout / Container

**UnifiedToastHost** (compact pill):

- Shape: `RoundedCornerShape(14.dp)` with **2 dp** `#000` border.
- Background: solid `surface` or `inverse-surface` (dark toast on light app).
- **No** `GlassSurface` alpha variant.

**UnifiedToastOverlay**: solid bordered pill (`cornerRadiusDp` 28), **no** `LiquidGlassPill` gradient.

| Token | Value |
|-------|-------|
| `DefaultDurationMs` | **2400** |
| `MaxWidthDp` | 300 |

### 13.2 Interactive Elements

- Compact: auto-dismiss via `UnifiedToastState.show`.
- Overlay: `TextButton` dismiss — default **"Got it"**.

### 13.3 States

| State | Behavior |
|-------|----------|
| **Default** | Hidden |
| **Pressed/Highlighted** | TextButton mechanical press |
| **Active** | `AnimatedVisibility` visible |
| **Focus** | N/A |
| **Disabled** | N/A |
| **Loading** | N/A |
| **Empty** | Hidden |
| **Error** | Error string in bordered pill |
| **Success** | Success string; auto-hide 2400 ms |

### 13.4 Micro-copy

- Overlay default dismiss: **"Got it"**.

### 13.5 Flow Sequence

`show()` → enter animation → delay → exit.

### 13.6 A11y & Responsive

- Max width 300 dp on compact toast.

---

## 14. ChatBubbleTokens & Message Chrome

### 14.1 Layout / Container

**Scale**: `REL = 0.8` — layout dp values = design dp × 0.8 (unchanged).

**Sent bubble** (`ChatMessageBubble.kt`):

- Fill: solid `primary` `#630ed4` — **no linear gradient**.
- Text: `on-primary` `#ffffff`.
- Shape: `RoundedCornerShape(cornerMain)`.

**Received bubble**:

- Fill: solid `surface-variant` `#e2e2e2` (light) / `#3a3c3c` (dark).
- Border: **2 dp** `#000000` at 100%.
- Same corner main radius.

### 14.2 Interactive Elements

- Send button: solid `primary` when `canSend`; else flat `surface-variant`.
- Long-press reactions, reply blocks unchanged behaviorally.

### 14.3 States

| State | Sent | Received |
|-------|------|----------|
| **Default** | Solid `#630ed4` | Solid `surface-variant` + 2dp border |
| **Pressed/Highlighted** | 2dp translate on bubble actions | Same |
| **Active** | Selected for reply | Same |
| **Focus** | Composer: 2dp `primary` border | N/A |
| **Disabled** | Send → `surface-variant` | N/A |
| **Loading** | Photo placeholder `surface-container` | Same |
| **Empty** | N/A | N/A |
| **Error** | Failed send in composer | N/A |
| **Success** | Solid primary sent bubble | N/A |

### 14.4 Micro-copy

- `"edited"` footnote via `chatBubbleEditedFootnoteStyle()`.

### 14.5 Flow Sequence

Measure row width → cap at 75% → paint solid fills.

### 14.6 A11y & Responsive

- Bubble text scales with M3 × REL multiplier.

---

## 15. Cross-Component Token Map

```
PlatformThemeProvider
├── clickColorScheme (Functional Clarity palette)
├── clickTypography (Manrope FC scale)
└── PlatformStyleProvider (2dp borders, press translate)
    ├── GlassCard → bordered card (16dp / 8dp compact)
    ├── AdaptiveCard / AdaptiveButton → solid fills
    ├── LiquidGlassPill → solid pill
    └── ScreenChrome (AppScreenDefaults)

GlassSheetTokens → opaque sheets + 40% scrim
├── GlassModalBottomSheet
├── GlassAdaptiveBottomSheet
├── UnifiedPopup* (z-index 80)
└── UnifiedToast* (bordered compact + overlay)

ChatBubbleTokens (REL 0.8)
└── ChatMessageBubble solid sent/received paint
```

---

## 16. Z-Index Reference (Design Overlays)

| Layer | z-index | Component |
|-------|---------|-----------|
| Offline banner | 10 | `OfflineStatusBanner` |
| Global tether toast | 70 | `GlobalTetherOverlay` |
| Unified popup | 80 | `UnifiedPopupOverlay` |
| Click Drops camera | 10_500 | `DisposableCameraView` |
| Call preview + active | 11_000 | `CallPreviewOverlay` / `ActiveCallOverlay` |

---

*Target-state Functional Clarity specification. Compose `Glass*` API names retained where churn is costly; visual output is neo-brutalist opaque surfaces. No web or backend scope.*
