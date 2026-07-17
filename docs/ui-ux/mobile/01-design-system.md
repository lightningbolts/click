# Design System Foundation

As-built specification for the Click mobile Compose design system. All values are sourced from `click/composeApp/src/commonMain/kotlin/compose/project/click/click/ui/` unless noted.

**Source index**

| Area | Path |
|------|------|
| Colors | `ui/theme/Color.kt` |
| Typography | `ui/theme/Typography.kt` |
| Platform theme + color scheme | `ui/theme/PlatformTheme.kt` |
| Screen chrome defaults | `ui/components/ScreenChrome.kt` |
| Glass sheet tokens | `ui/components/GlassSheetTokens.kt` |
| Glass cards | `ui/components/GlassCard.kt` |
| Adaptive cards/buttons | `ui/components/AdaptiveCard.kt` |
| Liquid glass pill | `ui/components/LiquidGlassPill.kt` |
| Sheet grabber | `ui/components/GlassSheetGrabber.kt` |
| Sheet gesture physics | `ui/components/GlassSheetGesturePhysics.kt` |
| Material modal sheet | `ui/components/GlassModalBottomSheet.kt` |
| Calf adaptive sheet | `ui/components/GlassAdaptiveBottomSheet.kt` |
| Unified popup | `ui/components/UnifiedPopup.kt` |
| Unified toast | `ui/components/UnifiedToast.kt` |
| Chat bubble tokens | `ui/chat/ChatBubbleTokens.kt` |
| Chat bubble paint | `ui/chat/ChatMessageBubble.kt` |

---

## 1. Color Palette

### 1.1 Layout / Container

- **Brand tokens** (`Color.kt`): `PrimaryBlue` `#8338EC`, `LightBlue` `#A374F9`, `DeepBlue` `#5F1DAD`, `SoftBlue` `#F3EBFF`, `AccentBlue` `#6A1BC9`, `NeonPurple` `#D0BCFF`.
- **Light mode**: `BackgroundLight` `#FAFAFA`, `SurfaceLight` `#FFFFFF`, `GlassLight` `#F5F5F5`, `OnSurfaceLight` `#1A1A1A`, `OnSurfaceVariant` `#616161`, `TextSecondary` `#757575`.
- **Dark mode**: `BackgroundDark` `#09090B`, `SurfaceDark` `#18181B`, `OnSurfaceDark` `#FAFAFA`, `GlassDark` `#18181B` at 80% alpha.
- **Glass primitives**: `GlassWhite` `#FFFFFF` at 5%, `GlassBorder` `#FFFFFF` at 10%, `GlassBorderPrimary` `PrimaryBlue` at 15%, `GlassWhiteHover` `#FFFFFF` at 8%.
- **Gradient text** (web parity): `GradientTextStart` `#FFFFFF`, `GradientTextEnd` `#A1A1AA`.
- **Material color scheme** (`PlatformTheme.kt` `clickColorScheme`):
  - Light: `primary` = PrimaryBlue, `secondary` = AccentBlue, `background` = BackgroundLight, `surface` = SurfaceLight, `onSurface` = OnSurfaceLight, `primaryContainer` = SoftBlue, `onPrimaryContainer` = DeepBlue, `surfaceVariant` = `#E0E0E0`.
  - Dark: `primary` = PrimaryBlue, `secondary` = AccentBlue, `background` = BackgroundDark, `surface` = SurfaceDark, `onSurface` = OnSurfaceDark, `primaryContainer` = DeepBlue, `onPrimaryContainer` = NeonPurple, `surfaceVariant` = `#2C2C2C`.
- **App root background** (`App.kt`): dark mode adds a radial gradient from `PrimaryBlue` at 15% alpha (top-left, radius 1000) over `BackgroundDark`; light mode uses flat `BackgroundLight`.

### 1.2 Interactive Elements

- Primary actions use `MaterialTheme.colorScheme.primary` (PrimaryBlue).
- Secondary/accent uses `AccentBlue` / `primaryContainer` / `onPrimaryContainer` per scheme.
- Glass-accented surfaces use white or primary at platform-specific alphas (see PlatformStyle).

### 1.3 States

| State | Light | Dark |
|-------|-------|------|
| **Default** | Background `#FAFAFA`, Surface `#FFFFFF`, OnSurface `#1A1A1A` | Background `#09090B`, Surface `#18181B`, OnSurface `#FAFAFA` |
| **Pressed/Highlighted** | `GlassWhiteHover` 8% on glass surfaces | Same glass hover token |
| **Active** | `primary` / `primaryContainer` for selected controls | Same mapping |
| **Focus** | Inherited from M3 components (e.g. `PrimaryBlue` cursor in search fields) | Same |
| **Disabled** | `AdaptiveButton`: `Color.Gray` content, gray container at 8–10% alpha | Same |
| **Loading** | Shimmer screens use theme background; spinners use `PrimaryBlue` / `LightBlue` | Same |
| **Empty** | `onSurfaceVariant` / `TextSecondary` for hint copy | Same |
| **Error** | `MaterialTheme.colorScheme.error` on destructive actions (calls, etc.) | Same |
| **Success** | `PrimaryBlue`→`LightBlue` gradients on sent bubbles, send buttons | Same |

### 1.4 Micro-copy

- Color tokens are not user-facing strings; copy inherits `MaterialTheme.typography` and `onSurface` / `onSurfaceVariant`.

### 1.5 Flow Sequence

1. `PlatformThemeProvider(isDarkMode)` wraps authenticated and unauthenticated trees.
2. `clickColorScheme(isDarkMode)` supplies M3 semantic colors.
3. `PlatformStyleProvider` resolves iOS vs Android glass/corner deltas.
4. Components read `MaterialTheme.colorScheme` and `LocalPlatformStyle.current`.

### 1.6 A11y & Responsive

- Text contrast: `OnSurfaceLight` on `SurfaceLight`; `OnSurfaceDark` on `SurfaceDark`.
- `onSurfaceVariant` (`#616161`) used for secondary labels; meets secondary-text intent on light surfaces.
- Dark `surfaceVariant` `#2C2C2C` used for received chat bubbles at 90% alpha.
- No hard-coded font sizes in `Color.kt`; all type scales via Typography.

---

## 2. Typography

### 2.1 Layout / Container

- **Font family**: Manrope only — weights Normal, Medium, SemiBold, Bold loaded from Compose resources (`manrope_regular`, `manrope_medium`, `manrope_semibold`, `manrope_bold`).
- **Scale**: Full Material 3 type scale — all 13 roles (`displayLarge` through `labelSmall`) inherit default M3 sizes/line heights from `Typography()` baseline; **only** `fontFamily` is overridden to Manrope (`Typography.kt`). No per-role size overrides in theme file.

### 2.2 Interactive Elements

- Buttons, labels, headers use standard M3 roles (`labelLarge`, `titleMedium`, `bodyMedium`, etc.).
- Chat bubbles apply local scale multipliers on top of M3 roles (see Chat Bubble Tokens).

### 2.3 States

| State | Behavior |
|-------|----------|
| **Default** | Manrope at M3 default metrics for each role |
| **Pressed/Highlighted** | No typography change |
| **Active** | `FontWeight.SemiBold` applied ad hoc (e.g. popup titles) |
| **Focus** | No type change |
| **Disabled** | Inherited M3 disabled content alpha on buttons |
| **Loading** | `bodyMedium` on loading captions |
| **Empty** | `bodyMedium` / `labelSmall` for hints |
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
| `cardCornerRadius` | 28 dp | 28 dp |
| `compactCardCornerRadius` | 8 dp | 8 dp |
| `buttonCornerRadius` | **12 dp** | **16 dp** |
| `cardBorderWidth` | **0.5 dp** | **1 dp** |
| `glassBackgroundAlpha` | **0.08** | **0.05** |
| `glassBorderAlpha` | **0.14** | **0.10** |
| `glassBorderPrimaryAlpha` | **0.20** | **0.15** |
| `useShadowElevation` | **false** | **true** |
| `useRipple` | **false** | **true** |

Detection: `getPlatform().name.contains("iOS")`.

### 3.2 Interactive Elements

- `GlassCard`, `GlassCardCompact`, `GlassSurface` read platform alphas and border width.
- `AdaptiveButton` branches on `style.isIOS` for fill vs bordered M3 button.
- `GlassCard` clickable uses `indication = null` (no ripple on either platform at card level).

### 3.3 States

| State | iOS | Android |
|-------|-----|---------|
| **Default** | Higher glass alpha (more visible frost) | Lower glass alpha |
| **Pressed/Highlighted** | No ripple on glass cards | No ripple on glass cards; M3 buttons may ripple when `useRipple` true |
| **Active** | Primary border at 20% | Primary border at 15% |
| **Focus** | Platform default | Platform default |
| **Disabled** | `AdaptiveButton` gray 8% fill | Gray 10% fill + border retained |
| **Loading** | N/A | N/A |
| **Empty** | N/A | N/A |
| **Error** | N/A | N/A |
| **Success** | N/A | N/A |

### 3.4 Micro-copy

- None.

### 3.5 Flow Sequence

`PlatformStyleProvider` → child composables read `LocalPlatformStyle.current`.

### 3.6 A11y & Responsive

- Identical corner radii across platforms; touch targets unchanged.
- iOS omits shadow elevation on glass surfaces (flat OLED aesthetic).

---

## 4. AppScreenDefaults & Screen Chrome

### 4.1 Layout / Container

`AppScreenDefaults` (`ScreenChrome.kt`):

| Token | Value |
|-------|-------|
| `HorizontalPadding` | 20 dp |
| `SectionSpacing` | 24 dp |
| `HeaderCollapseScrollThreshold` | 96 px |
| `FloatingHeaderLargeHeight` | 112 dp (initial fallback) |
| `FloatingHeaderCompactHeight` | 52 dp |
| `ExtraScrollBottomPadding` | 16 dp |
| `IosTabBarContentHeight` | 49 dp |
| `AndroidNavBarContentHeight` | 80 dp |
| `FabGapAboveTabBar` | 6 dp |

`AppScreenChromeState.bottomChromeHeight` — mutable, updated by `PlatformBottomBar` (measured tab bar top on iOS; nav height + inset on Android).

Helpers: `rememberTabBarOverlayHeight()`, `rememberBottomChromePadding()`, `rememberStatusBarTopPadding()`, `rememberFabAboveNavPadding()`, `rememberComposerBottomPadding()`, `chatComposerDock`, `chatThreadKeyboardDock`.

### 4.2 Interactive Elements

- Floating headers: `LiquidGlassPageHeader` in `AppScreenScaffold` (see shell doc).
- FABs sit `FabGapAboveTabBar` above measured tab bar top.
- Chat composers dock above tab bar; iOS uses `maxOf(tabStack, imeBottom)`.

### 4.3 States

| State | Behavior |
|-------|----------|
| **Default** | Bottom chrome = measured tab/nav height + optional 16 dp scroll padding |
| **Pressed/Highlighted** | N/A |
| **Active** | Header `collapseFraction` 0→1 over 96 px scroll |
| **Focus** | IME lifts chat via `graphicsLayer` (iOS native lift) or `offset` (Android) |
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

## 5. GlassSheetTokens

### 5.1 Layout / Container

`GlassSheetTokens` object:

| Token | Value |
|-------|-------|
| `OledBlack` | `#000000` |
| `GlassSurface` | White 5% |
| `GlassBorder` | White 12% |
| `GlassBorderPressed` | White 22% |
| `OnOled` | White 92% |
| `OnOledMuted` | White 62% |
| `SheetTopCorner` | 32 dp |
| `BentoExteriorCorner` | 28 dp |
| `BentoInteriorCorner` | 8 dp |
| `ScrimBaseAlpha` | 0.58 |

### 5.2 Interactive Elements

- Sheet shells: OLED black fill + glass border.
- Scrim: black at `ScrimBaseAlpha` (popup/sheet); modal sheet scrim animates 0.38–0.58 based on sheet offset.

### 5.3 States

| State | Token |
|-------|-------|
| **Default** | `GlassSurface` + `GlassBorder` |
| **Pressed/Highlighted** | `GlassBorderPressed` (22%) |
| **Active** | Expanded sheet at full `ScrimBaseAlpha` |
| **Focus** | N/A |
| **Disabled** | N/A |
| **Loading** | N/A |
| **Empty** | N/A |
| **Error** | N/A |
| **Success** | N/A |

### 5.4 Micro-copy

- Content uses `OnOled` / `OnOledMuted` on OLED shells.

### 5.5 Flow Sequence

Shared by `GlassModalBottomSheet`, `GlassAdaptiveBottomSheet`, `UnifiedPopup`, `UnifiedToast` (opaque variant).

### 5.6 A11y & Responsive

- `SheetTopCorner` 32 dp top radius on modal sheets.
- Bento exterior 28 dp matches `GlassCard` corner constant.

---

## 6. GlassCard & GlassCardCompact

### 6.1 Layout / Container

- **GlassCard** / **GlassSurface**: corner = `LocalPlatformStyle.cardCornerRadius` (28 dp); constant `GlassCornerRadius` = 28 dp, `GlassCardShape` = `RoundedCornerShape(28.dp)`.
- **GlassCardCompact**: `compactCardCornerRadius` = 8 dp.
- Default `contentPadding`: 16 dp (compact: 12 dp).
- Background: `Color.White.copy(alpha = glassBackgroundAlpha)`.
- Border: white at `glassBorderAlpha`, or `PrimaryBlue` at `glassBorderPrimaryAlpha` when `usePrimaryBorder = true`.
- **glassEffect** modifier: static `GlassWhite` + 1 dp border (`GlassBorder` or `GlassBorderPrimary`), clipped to 28 dp shape.

### 6.2 Interactive Elements

- Optional `onClick`: `clickable(indication = null)`.
- `GlassSurface` uses M3 `Surface` with `shadowElevation = 0.dp`.

### 6.3 States

| State | Visual |
|-------|--------|
| **Default** | Frosted white fill + hairline border |
| **Pressed/Highlighted** | No built-in pressed tint (indication null) |
| **Active** | `usePrimaryBorder` → primary-tinted border |
| **Focus** | N/A |
| **Disabled** | Non-clickable when `onClick` null |
| **Loading** | N/A |
| **Empty** | N/A |
| **Error** | N/A |
| **Success** | N/A |

### 6.4 Micro-copy

- Consumer-provided.

### 6.5 Flow Sequence

`LocalPlatformStyle` → shape/alpha/border → optional click.

### 6.6 A11y & Responsive

- 28 dp corners; compact 8 dp for dense rows.
- iOS 0.5 dp borders vs Android 1 dp.

---

## 7. AdaptiveCard & AdaptiveButton

### 7.1 Layout / Container

- **AdaptiveCard**: corner = `getAdaptiveCornerRadius()` (28 dp); padding = 16 dp.
- Surface alpha: iOS 0.85, Android 0.8 on `MaterialTheme.colorScheme.surface`.
- Border: `PrimaryBlue` at iOS 0.35 / Android 0.5 alpha; width = `cardBorderWidth`.
- **AdaptiveSurface**: bottom-rounded sheet shape, surface 80% alpha.
- **AdaptiveBackground**: transparent `Box`.

### 7.2 Interactive Elements

- **AdaptiveButton**:
  - iOS: filled `PrimaryBlue` 15% container, `PrimaryBlue` content, 0 elevation, corner `buttonCornerRadius` (12 dp).
  - Android: `primaryContainer` 50% + 1 dp `PrimaryBlue` 50% border, corner 16 dp.

### 7.3 States

| State | AdaptiveButton |
|-------|----------------|
| **Default** | See above |
| **Pressed/Highlighted** | M3 button pressed state (0 elevation iOS) |
| **Active** | N/A |
| **Focus** | M3 focus ring |
| **Disabled** | `disabledContainerColor` = Gray 8% (iOS) / 10% (Android); `disabledContentColor` = Gray |
| **Loading** | N/A |
| **Empty** | N/A |
| **Error** | N/A |
| **Success** | N/A |

### 7.4 Micro-copy

- Consumer-provided button labels.

### 7.5 Flow Sequence

Platform branch inside composable → `RoundedCornerShape(style.buttonCornerRadius)`.

### 7.6 A11y & Responsive

- Disabled gray is explicit (not theme onSurface disabled).

---

## 8. LiquidGlassPill

### 8.1 Layout / Container

- Default `cornerRadiusDp` = **24** (used by grabber wrapper at 22, toast overlay at 28).
- Inner padding: horizontal 14 dp, vertical 8 dp.
- Vertical gradient on `scheme.surface`: top alpha lerp 0.58→0.90, bottom 0.34→0.78 by `backgroundStrength` (0–1).
- Border: `onSurface` at alpha lerp 0.08→0.18.
- Optional backing layer: `scheme.background` at alpha lerp 0→0.42 when `backgroundStrength` > 0.
- Procedural noise: density default 0.04, up to 4000 dots, white 2–8% alpha, 0.5 px radius.
- Caller may stack `Modifier.blur` externally for true backdrop blur (API 31+).

### 8.2 Interactive Elements

- Non-interactive container; children may be buttons/text.

### 8.3 States

| State | `backgroundStrength` |
|-------|----------------------|
| **Default** | 0 — translucent map overlay |
| **Pressed/Highlighted** | N/A |
| **Active** | N/A |
| **Focus** | N/A |
| **Disabled** | N/A |
| **Loading** | N/A |
| **Empty** | N/A |
| **Error** | N/A |
| **Success** | 0.85 — `UnifiedToastOverlay` readable over content |

### 8.4 Micro-copy

- Used for map memory count pills, toast overlay body, grabber chrome.

### 8.5 Flow Sequence

Clip → gradient → border → noise canvas → padded content.

### 8.6 A11y & Responsive

- Noise fallback ensures glass read at small sizes on Android < API 31.

---

## 9. GlassSheetGrabber

### 9.1 Layout / Container

- Full width; padding top 10 dp, bottom 6 dp.
- Wraps `LiquidGlassPill(cornerRadiusDp = 22, noiseDensity = 0.035f)`.
- Inner pill bar: 40×4 dp, `RoundedCornerShape(50)`, white 42% alpha.

### 9.2 Interactive Elements

- Drag handle for `GlassModalBottomSheet` and `GlassAdaptiveBottomSheet`; gesture handled by sheet state.

### 9.3 States

| State | Behavior |
|-------|----------|
| **Default** | Visible grabber pill |
| **Pressed/Highlighted** | Sheet drag offset (parent) |
| **Active** | Sheet expanding |
| **Focus** | N/A |
| **Disabled** | Hidden when sheet non-draggable (parent) |
| **Loading** | N/A |
| **Empty** | N/A |
| **Error** | N/A |
| **Success** | N/A |

### 9.4 Micro-copy

- None (decorative).

### 9.5 Flow Sequence

Sheet `dragHandle = { GlassSheetGrabber() }`.

### 9.6 A11y & Responsive

- 40 dp wide × 4 dp tall affordance centered in pill.

---

## 10. GlassModalBottomSheet (Material ModalBottomSheet)

### 10.1 Layout / Container

- `containerColor` = `OledBlack`; `contentColor` = `OnOled`.
- Shape: top corners `SheetTopCorner` (32 dp).
- `tonalElevation` = 0.
- Column body full width on `OledBlack`.
- Default `sheetState` = `rememberGlassModalBottomSheetState()` (travel 420 dp).
- Scrim: `Color.Black` alpha = `0.42 + (ScrimBaseAlpha - 0.42) * expandAmount`, clamped 0.38–0.58.

### 10.2 Interactive Elements

- `GlassSheetGrabber` drag handle.
- `confirmValueChange` on hide: commit if offset > 50% travel OR velocity > 800 px/s (`GlassGestureCommitFraction`, `GlassGestureFlickVelocityPxPerSec`).

### 10.3 States

| State | Behavior |
|-------|----------|
| **Default** | Hidden / collapsed |
| **Pressed/Highlighted** | Dragging — scrim deepens with expand |
| **Active** | Expanded sheet |
| **Focus** | N/A |
| **Disabled** | N/A |
| **Loading** | N/A |
| **Empty** | Consumer empty state |
| **Error** | Consumer error state |
| **Success** | Dismiss on commit |

### 10.4 Micro-copy

- Consumer content on OLED shell.

### 10.5 Flow Sequence

Offset snapshot → scrim alpha → `ModalBottomSheet` → grabber + column content.

### 10.6 A11y & Responsive

- `BottomSheetDefaults.windowInsets` default for safe area.
- Flick-dismiss threshold 800 px/s (iOS-style).

---

## 11. Click bottom sheets + GlassAdaptiveBottomSheet

**Sources:** `ui/components/ClickBottomSheet.kt`, `ui/components/GlassAdaptiveBottomSheet.kt`, `ui/sheet/MapBeaconSheetRoot.kt`

Two sheet families coexist:

| Family | Entry points | Shell |
|--------|--------------|-------|
| **Click sheets** | `ClickPlatformSheet`, `ClickActionBottomSheet`, `ClickFormBottomSheet` | `MapBeaconSheetRoot` (iOS native medium detent; Android Calf half-height cap) |
| **Glass adaptive** | `GlassAdaptiveBottomSheet` | Calf `AdaptiveBottomSheet` OLED shell (search, etc.) |

### 11.1 Layout / Container — Click sheets

**`ClickSheetDefaults`:**

| Token | Value |
|-------|-------|
| `ContentHorizontalPadding` | 20 dp |
| `ContentBottomPadding` | 24 dp |
| `TitleBottomSpacing` | 12 dp |
| `ScrimAlpha` | 0.55 |

**Hierarchy:**

```
ClickActionBottomSheet / ClickFormBottomSheet
 └── ClickPlatformSheet
      └── MapBeaconSheetRoot (OLED black, scrim Black@55%, zero window insets)
           └── ClickSheetDialogChrome (grabber + semantic color remap)
                └── Column content
```

**`ClickSheetChrome`:** optional `titleLarge` SemiBold title in `OnOled`, then content; bottom padding 24 dp.

**Usage:** Short menus → `ClickActionBottomSheet` (message/connection/hub actions). Tall forms → `ClickFormBottomSheet` (profile, availability, connection context, verified click).

**`GlassAdaptiveBottomSheet`:** Calf adaptive with `OledBlack` / `OnOled`; default scrim `ScrimBaseAlpha` (0.58); positional threshold 56 dp; velocity 800 px/s; default `dragHandle` = `GlassSheetGrabber()`.

### 11.2 Interactive Elements

- Platform drag-to-dismiss and back dismiss via sheet root.
- Grabber via `ClickSheetDialogChrome` / `GlassSheetGrabber`.
- `UnifiedSearchSheet` uses `GlassAdaptiveBottomSheet` with `contentWindowInsets = WindowInsets(0)`.

### 11.3 States

| State | Behavior |
|-------|----------|
| **Default** | OLED sheet expanded / medium detent |
| **Pressed/Highlighted** | Drag offset; border may use `GlassBorderPressed` on chrome |
| **Active** | Sheet visible; underlay dimmed |
| **Focus** | Focusable fields inside form sheets |
| **Disabled** | N/A at shell |
| **Loading** | Caller shows spinner inside content |
| **Empty** | Caller empty copy |
| **Error** | Caller inline error |
| **Success** | Dismiss after action |

### 11.4 Micro-copy

- Titles supplied by callers (`ClickSheetChrome(title = …)`).
- Search host copy: see [11-search.md](11-search.md).

### 11.5 Flow Sequence

```
Caller opens Action/Form sheet
 → MapBeaconSheetRoot presents platform sheet
 → User fills / taps action OR drags/backs to dismiss
 → onDismissRequest
```

Glass adaptive: `rememberGlassAdaptiveSheetState` → `GlassAdaptiveBottomSheet` → consumer column.

### 11.6 A11y & Responsive

- iOS: native page sheet + theme re-injection into separate `ComposeUIViewController`.
- Android: Calf adaptive, half-height cap for Click sheets.
- No custom live-region on sheet chrome; rely on titled content.

---

## 12. UnifiedPopup

### 12.1 Layout / Container

**UnifiedPopupTokens**:

| Token | Value |
|-------|-------|
| `FadeInMillis` | 320 |
| `FadeOutMillis` | 220 |
| `ScaleInInitial` | 0.92 |
| `ScaleOutTarget` | 0.94 |
| `ContentClearDelayMillis` | 240 |
| `OverlayZIndex` | 80 |

**UnifiedPopupOverlay**: full-screen `Box` z-index 80; scrim `Black` at `scrimAlpha` default `ScrimBaseAlpha` (0.58); centered content.

**UnifiedPopupCard**: `BentoExteriorCorner` (28 dp), 1 dp `GlassBorder`, `OledBlack` fill; padding H 18 / V 16; default horizontal margin 22 dp (alert uses 28 dp).

**UnifiedPopupFormDialog**: max width 360 dp default (null = full width for pickers); surface padding 28 dp; inner padding 24 dp; `tonalElevation` 6 dp on OLED surface.

**UnifiedPopupMotion.Picker**: fade in 280 / out 320; scale 0.95→0.97; slide enter 6% / exit 5%.

### 12.2 Interactive Elements

- Scrim tap dismisses (animated).
- `PlatformBackHandler` when overlay visible.
- `LocalUnifiedPopupAnimatedDismiss` for in-card buttons (fade-out before `onDismissRequest`).
- `UnifiedPopupTextButton`: M3 `TextButton` with configurable `contentColor`.
- Alert: optional icon, title (`titleMedium` OnOled), text (`bodyMedium` OnOledMuted), action row end-aligned.
- Form: title semibold `OnOled`; Cancel (muted) + Confirm (OnOled).

### 12.3 States

| State | Behavior |
|-------|----------|
| **Default** | Hidden |
| **Pressed/Highlighted** | Button pressed states |
| **Active** | Overlay `targetState` true |
| **Focus** | `Popup` focusable on alert variant |
| **Disabled** | N/A |
| **Loading** | Consumer body |
| **Empty** | N/A |
| **Error** | Consumer copy |
| **Success** | Confirm → animated dismiss |

### 12.4 Micro-copy

- Default dismiss: `"Cancel"` (`UnifiedPopupFormDialog`).
- Titles/confirm labels: consumer-provided strings.

### 12.5 Flow Sequence

1. `visible` true → `transitionState.targetState = true`.
2. Scrim fade + content scale/fade (optional slide for Picker motion).
3. Dismiss request → animate out → `onDismissRequest` on idle.

### 12.6 A11y & Responsive

- `PopupProperties(focusable, dismissOnBackPress, dismissOnClickOutside)` on alert.
- Form dialog `contentMaxWidth` 360 dp on phones; null for full-width date/time wheels.

---

## 13. UnifiedToast

### 13.1 Layout / Container

**UnifiedToastTokens**:

| Token | Value |
|-------|-------|
| `EnterMillis` | 240 (iOS: +40 = **280**) |
| `ExitMillis` | 180 |
| `DefaultDurationMs` | **2400** |
| `CompactCornerDp` | 14 |
| `OverlayCornerDp` | 28 |
| `MaxWidthDp` | 300 |

**UnifiedToastHost** (compact pill):

- Alignment: `CenterEnd` default; `Center` when `opaque = true`.
- Background: `GlassSheetTokens.GlassSurface` OR opaque `OledBlack` + `GlassBorder`.
- Padding: H 14 / V 10; `bodyMedium`, `OnOled` text.

**UnifiedToastOverlay** (center nudge):

- Full-bleed `Box`; `LiquidGlassPill` corner 28, `backgroundStrength` 0.85.
- Column: `bodyLarge` onSurface message + `TextButton` dismiss.

### 13.2 Interactive Elements

- Compact: auto-dismiss via `UnifiedToastState.show(scope, text, durationMs)`.
- Overlay: `TextButton` calls `onDismiss`.
- `UnifiedToastState.dismiss()` cancels job and clears message.

### 13.3 States

| State | Behavior |
|-------|----------|
| **Default** | Hidden (`message == null`) |
| **Pressed/Highlighted** | TextButton pressed (overlay) |
| **Active** | `AnimatedVisibility` visible |
| **Focus** | N/A |
| **Disabled** | N/A |
| **Loading** | N/A |
| **Empty** | Hidden |
| **Error** | Consumer error string in pill |
| **Success** | Consumer success string; auto-hide 2400 ms |

### 13.4 Micro-copy

- Overlay default dismiss label: **"Got it"** (`UnifiedToastOverlay` `dismissLabel` parameter).

### 13.5 Flow Sequence

1. `show()` sets message, launches delay `DefaultDurationMs`.
2. Enter: slide vertical 1/3 + fade (compact); spring fade+scale (overlay).
3. Exit: reverse; overlay dismiss invokes `onDismiss`.

### 13.6 A11y & Responsive

- Max width 300 dp on compact toast.
- iOS enter 280 ms vs Android 240 ms.

---

## 14. ChatBubbleTokens & Message Chrome

### 14.1 Layout / Container

**Scale**: `REL = 0.8` — all bubble layout dp values = design dp × 0.8.

| Token | Scaled value (design × 0.8) |
|-------|----------------------------|
| `contentMaxWidth` | 450 dp → 360 dp |
| `cornerMain` | 27 → 21.6 dp |
| `cornerTailSmall` | 8 → 6.4 dp |
| `bubblePaddingHorizontal` | 15 → 12 dp |
| `bubblePaddingVertical` | 12 → 9.6 dp |
| `messageMaxWidthToParentFraction` | 0.75 |
| `peerAvatarSize` | 36 → 28.8 dp |
| Reaction chip corner 18 → 14.4 dp | etc. |

**Typography multipliers** (on M3 roles):

- Message/reply: `bodyMedium` / `bodySmall` / `labelSmall` × `1.3 × REL` (1.04×).
- Edited footnote: × `1.35 × REL` (1.08×).
- Audio time labels: × `1.5 × REL`.

**Sent bubble** (`ChatMessageBubble.kt`):

- Fill: `Brush.linearGradient(PrimaryBlue → LightBlue)`.
- Shape: `RoundedCornerShape(cornerMain)`.

**Received bubble**:

- Fill: `surfaceVariant` at **90%** alpha.
- Border: 1 dp `PrimaryBlue` at **18%** alpha.
- Same corner main radius.

### 14.2 Interactive Elements

- Long-press reactions, reply blocks, media attachments use same token padding.
- Send button gradient: `PrimaryBlue → LightBlue` when `canSend`; else flat `surfaceVariant`.

### 14.3 States

| State | Sent | Received |
|-------|------|----------|
| **Default** | Violet gradient | Gray variant 90% + primary border 18% |
| **Pressed/Highlighted** | Gesture handlers on bubble | Same |
| **Active** | Selected for reply | Same |
| **Focus** | Composer field: primary border 55–65% | N/A |
| **Disabled** | Send gradient → surfaceVariant | N/A |
| **Loading** | Photo placeholder surfaceVariant 35% | Same |
| **Empty** | N/A | N/A |
| **Error** | Failed send states in composer | N/A |
| **Success** | Sent gradient | N/A |

### 14.4 Micro-copy

- `"edited"` footnote via `chatBubbleEditedFootnoteStyle()`.
- Typing indicator dots bounce `6 × REL` dp peak.

### 14.5 Flow Sequence

Measure row width → cap at 75% → apply scaled padding → paint sent gradient or received variant.

### 14.6 A11y & Responsive

- Bubble text scales with M3 × REL multiplier.
- Peer avatar 28.8 dp at REL 0.8.

---

## 15. Cross-Component Token Map

```
PlatformThemeProvider
├── clickColorScheme (Color.kt + M3)
├── clickTypography (Manrope M3 scale)
└── PlatformStyleProvider (iOS/Android deltas)
    ├── GlassCard / GlassCardCompact (28 dp / 8 dp)
    ├── AdaptiveCard / AdaptiveButton
    ├── LiquidGlassPill (default 24 dp corner)
    └── ScreenChrome (AppScreenDefaults)

GlassSheetTokens (OLED sheets)
├── GlassModalBottomSheet (Material)
├── GlassAdaptiveBottomSheet (Calf)
├── UnifiedPopup* (z-index 80)
└── UnifiedToast* (compact + overlay)

ChatBubbleTokens (REL 0.8)
└── ChatMessageBubble sent/received paint
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

*Document reflects as-built code. No web or backend scope. No redesign proposals.*
