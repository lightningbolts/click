# Native UI bridges (`ui/components/native/`)

Expect/actual components that render **authentic iOS liquid glass** (UIKit `UIVisualEffectView` + SF Symbols) while keeping **Material 3** semantics on Android.

## Components

| Composable | Use when |
|------------|----------|
| `NativeNavButton` / `NativeBackButton` | Icon buttons, FAB-sized circles (send, groups, map controls) |
| `NativeContextMenuBox` | Custom anchor + menu items |
| `NativeContextMenuIconButton` | Overflow / call / attach menus on icon buttons |
| `NativeContextMenuChip` | Filter pills (map layers) — Compose chip visual + native menu overlay |
| `NativeTextInputRow` | Chat composer text field |
| `NativeCallPreviewHost` | Incoming/outgoing call preview (iOS `UIAlertController`; Android `CallPreviewOverlay`) |

## Scope

**Native on iOS:** nav buttons, context menus, composer controls, call preview alerts, map overlay buttons.

**Stays Compose:** header pill shells (`LiquidGlassPageHeader`), message bubbles, sheets, in-call video layout (`ActiveCallOverlay` shell).

## iOS notes

- `IosLiquidGlassChrome` shares the `isLiquidGlass` gate with `BottomBar.ios.kt` (iOS 26+ `UIGlassEffect`, else chrome blur).
- **Liquid glass chrome:** use `createLiquidGlassChromeView(cornerRadius, interactive = true)` so `UIGlassEffect.setInteractive(true)` is set on iOS 26+. Put interactive controls (`UIButton`, `UITextView`, etc.) inside `contentView` via `fillContentViewWith` — never add them as siblings of the `UIVisualEffectView`.
- **UIKitView placement:** `nativeChromeUIKitInteropProperties()` sets `placedAsOverlay = false` on iOS 26+ (embed in Compose tree, respect z-order) and `placedAsOverlay = true` pre-26 (blur chrome samples behind Compose). Always call `applyTransparentChromeHost()` (`backgroundColor = clear`, `opaque = false`).
- **Glass buttons (iOS 26+):** single `UIButton` with `glassButtonConfiguration()` / `prominentGlassButtonConfiguration()`, clear `baseBackgroundColor` and `background.backgroundColor`, circular clip via `layer.cornerRadius = bounds.width/2`.
- `NativeContextMenuChip` — iOS uses native glass pill `UIButton`; Android uses Compose `LiquidGlassPill` + menu overlay.
- Icons map through `NavIconSfSymbols.kt` — add new icons there + `NavIconSfSymbolsTest`.
- Haptics: `PlatformHapticsPolicy` on tap; no Compose `Icon` or `LiquidGlassPill` inside iOS actuals.

## Anti-patterns

- No `if (isIOS)` in screens — use expect/actual only.
- No `LiquidGlassPill` inside iOS bridged controls.
- No Compose icon overlay on top of `UIKitView` buttons.
