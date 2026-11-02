package compose.project.click.click.ui.theme // pragma: allowlist secret

import androidx.compose.ui.graphics.Color

// ============================================================================
// Brand Colors — "Quiet Presence" (click-web DESIGN.md, iOS DesignSystem/Colors.swift).
// One violet is the action color; everything else is neutral and separates by tone.
// ============================================================================

/** The action fill: primary buttons, selected chips, outgoing emphasis (web `--action`, iOS primary action). */
val PrimaryBlue = Color(0xFF7C3AED)

/** Brand mark only (logo, splash). Never a button fill (iOS `ClickColors.brand`). */
val BrandMarkPurple = Color(0xFF630ED4)
val LightBlue = Color(0xFF8655F4) // Action hover / pressed on dark (web dark `--action-hover`)
val DeepBlue = Color(0xFF5A00C6) // Accent text on light (links, active tab)
val AccentTextDark = Color(0xFFC3A6FF) // Accent text on dark
val SoftBlue = Color(0xFFEEE5FF) // Selection wash on light
val SelectionDark = Color(0xFF24133F) // Selection wash on dark
val AccentBlue = Color(0xFF732EE4) // Surface tint
val NeonPurple = Color(0xFFD2BBFF) // Inverse primary / dark accents
val SecondaryAccent = Color(0xFF224CFF) // Events / map / non-CTA emphasis
val SecondaryContainerLight = Color(0xFFE8EDFF)
val OnSecondaryContainerLight = Color(0xFF0D1F73)
val SecondaryContainerDark = Color(0xFF1A2A6E)
val OnSecondaryContainerDark = Color(0xFFD6E0FF)

// ============================================================================
// Light Mode — Functional Clarity surface tiers
// ============================================================================
val BackgroundLight = Color(0xFFF6F6F8) // page
val SurfaceLight = Color(0xFFFFFFFF) // cards, sheets
val SurfaceContainerLow = Color(0xFFF2F2F5) // raised (inputs, nested)
val SurfaceContainer = Color(0xFFEDEDF0)
val SurfaceContainerHigh = Color(0xFFE7E7EB)
val SurfaceContainerHighest = Color(0xFFE1E1E6)
val SurfaceDim = Color(0xFFD9D9DE)
val GlassLight = SurfaceContainerLow // legacy alias → opaque tier
val OnSurfaceLight = Color(0xFF111113)
val OnSurfaceVariant = Color(0xFF55555C) // secondary text
val TextSecondary = Color(0xFF55555C)

// ============================================================================
// Dark Mode — deeper gray (not pure black)
// ============================================================================
val BackgroundDark = Color(0xFF0B0B0D) // page
val SurfaceDark = Color(0xFF1C1C1E) // cards over the page
val OnSurfaceDark = Color(0xFFF5F5F7)
val OnSurfaceVariantDark = Color(0xFFAEAEB2) // secondary text
val GlassDark = SurfaceDark // legacy alias → opaque
val SurfaceContainerDark = Color(0xFF242426)
val SurfaceVariantDark = Color(0xFF2C2C2E) // raised (inputs, nested)

// ============================================================================
// Quiet structural borders (match click-web outline-variant). Keep hard black/white
// only for high-emphasis rings (focus / selected), not default chrome.
// ============================================================================
val BorderHard = Color(0xFF000000)
val BorderHardDark = Color(0xFFFFFFFF)
val OutlineMuted = Color(0xFF6C6C71) // tertiary text / meta on light
val OutlineMutedDark = Color(0xFF98989F) // tertiary text / meta on dark
val OutlineVariant = Color(0xFFD1D1D6)
val BorderQuiet = OutlineVariant
val BorderQuietDark = Color(0xFF3A3A3C)

// Status (web `--destructive`, `--online`, `--live`; iOS Colors.swift)
val DestructiveLight = Color(0xFFD70015)
val DestructiveDark = Color(0xFFFF453A)
val OnlineLight = Color(0xFF1FA855)
val OnlineDark = Color(0xFF30D158)

/** @deprecated Prefer BorderHard; kept for call-site compatibility */
val GlassWhite = SurfaceLight

/** @deprecated Prefer BorderHard; kept for call-site compatibility */
val GlassBorder = BorderHard.copy(alpha = 0.12f)

/** @deprecated Prefer BorderHard / primary border */
val GlassBorderPrimary = PrimaryBlue

/** @deprecated Prefer SurfaceContainerLow */
val GlassWhiteHover = SurfaceContainerLow

// ============================================================================
// Legacy gradient text aliases → solid on-surface (no gradients)
// ============================================================================
val GradientTextStart = OnSurfaceLight
val GradientTextEnd = OnSurfaceVariant
