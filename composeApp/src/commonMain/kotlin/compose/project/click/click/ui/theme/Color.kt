package compose.project.click.click.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Brand Colors (Violet Palette - matching web)
// ============================================================================
val PrimaryBlue = Color(0xFF8338EC)  // Main violet - web primary
val LightBlue = Color(0xFFA374F9)    // Lighter violet for gradients
val DeepBlue = Color(0xFF5F1DAD)     // Darker violet
val SoftBlue = Color(0xFFF3EBFF)     // Very light violet tint
val AccentBlue = Color(0xFF6A1BC9)   // Accent violet
val NeonPurple = Color(0xFFD0BCFF)   // Bright neon for glows/gradients

// ============================================================================
// Light Mode Colors (Zinc palette from Tailwind)
// ============================================================================
val BackgroundLight = Color(0xFFFAFAFA)    // Zinc-50
val SurfaceLight = Color(0xFFFFFFFF)       // White
val GlassLight = Color(0xFFF5F5F5)         // Subtle off-white
val OnSurfaceLight = Color(0xFF1A1A1A)     // Near-black text
val OnSurfaceVariant = Color(0xFF616161)   // Medium gray text
val TextSecondary = Color(0xFF757575)      // Lighter gray text

// ============================================================================
// Dark Mode Colors (Zinc-950 base from web)
// ============================================================================
val BackgroundDark = Color(0xFF09090B)     // Zinc-950 - Deep dark, matches web
val SurfaceDark = Color(0xFF18181B)        // Zinc-900 - Slightly lighter surface
val OnSurfaceDark = Color(0xFFFAFAFA)      // Zinc-50 - Light text on dark
val GlassDark = Color(0xFF18181B).copy(alpha = 0.8f)  // Translucent dark surface for nav/overlays

// ============================================================================
// Glass Effect Colors (Simulating .glass CSS class)
// bg-white/5, border-white/10, backdrop-blur (blur simulated via opacity)
// ============================================================================

/** Glass surface background - White at 5% opacity (matches bg-white/5) */
val GlassWhite = Color(0xFFFFFFFF).copy(alpha = 0.05f)

/** Glass border - White at 10% opacity (matches border-white/10) */
val GlassBorder = Color(0xFFFFFFFF).copy(alpha = 0.10f)

/** Glass border with primary tint - For accented glass cards */
val GlassBorderPrimary = PrimaryBlue.copy(alpha = 0.15f)

/** Slightly more visible glass for hover/active states */
val GlassWhiteHover = Color(0xFFFFFFFF).copy(alpha = 0.08f)

// ============================================================================
// Gradient Text Colors (for text-gradient effect)
// ============================================================================
val GradientTextStart = Color(0xFFFFFFFF)  // Pure white
val GradientTextEnd = Color(0xFFA1A1AA)    // Zinc-400
