package compose.project.click.click.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Brand Colors — Functional Clarity (neo-brutalist)
// ============================================================================
val PrimaryBlue = Color(0xFF630ED4) // Primary purple
val LightBlue = Color(0xFF7C3AED) // Primary container
val DeepBlue = Color(0xFF5A00C6) // On-primary-fixed-variant
val SoftBlue = Color(0xFFEDE0FF) // On-primary-container / light purple fill
val AccentBlue = Color(0xFF732EE4) // Surface tint
val NeonPurple = Color(0xFFD2BBFF) // Inverse primary / dark accents

// ============================================================================
// Light Mode — Functional Clarity surface tiers
// ============================================================================
val BackgroundLight = Color(0xFFF9F9F9)
val SurfaceLight = Color(0xFFFFFFFF) // surface-container-lowest
val SurfaceContainerLow = Color(0xFFF3F3F4)
val SurfaceContainer = Color(0xFFEEEEEE)
val SurfaceContainerHigh = Color(0xFFE8E8E8)
val SurfaceContainerHighest = Color(0xFFE2E2E2)
val SurfaceDim = Color(0xFFDADADA)
val GlassLight = SurfaceContainerLow // legacy alias → opaque tier
val OnSurfaceLight = Color(0xFF1A1C1C)
val OnSurfaceVariant = Color(0xFF4A4455)
val TextSecondary = Color(0xFF4A4455)

// ============================================================================
// Dark Mode — deeper gray (not pure black)
// ============================================================================
val BackgroundDark = Color(0xFF101212)
val SurfaceDark = Color(0xFF1A1C1C) // raised surface over background
val OnSurfaceDark = Color(0xFFF0F1F1)
val GlassDark = SurfaceDark // legacy alias → opaque
val SurfaceContainerDark = Color(0xFF242626)
val SurfaceVariantDark = Color(0xFF2A2C2C)

// ============================================================================
// Hard borders & structural tokens (replace glass vocabulary)
// ============================================================================
val BorderHard = Color(0xFF000000)
val BorderHardDark = Color(0xFFFFFFFF)
val OutlineMuted = Color(0xFF7B7487)
val OutlineVariant = Color(0xFFCCC3D8)

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
