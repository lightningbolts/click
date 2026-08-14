---
name: Functional Clarity
colors:
  surface: '#f9f9f9'
  surface-dim: '#dadada'
  surface-bright: '#f9f9f9'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3f4'
  surface-container: '#eeeeee'
  surface-container-high: '#e8e8e8'
  surface-container-highest: '#e2e2e2'
  on-surface: '#1a1c1c'
  on-surface-variant: '#4a4455'
  inverse-surface: '#2f3131'
  inverse-on-surface: '#f0f1f1'
  outline: '#7b7487'
  outline-variant: '#ccc3d8'
  surface-tint: '#732ee4'
  primary: '#630ed4'
  on-primary: '#ffffff'
  primary-container: '#7c3aed'
  on-primary-container: '#ede0ff'
  inverse-primary: '#d2bbff'
  secondary: '#224CFF'
  on-secondary: '#ffffff'
  secondary-container: '#e2e2e2'
  on-secondary-container: '#646464'
  tertiary: '#4d4f51'
  on-tertiary: '#ffffff'
  tertiary-container: '#656769'
  on-tertiary-container: '#e5e6e8'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#eaddff'
  primary-fixed-dim: '#d2bbff'
  on-primary-fixed: '#25005a'
  on-primary-fixed-variant: '#5a00c6'
  secondary-fixed: '#e2e2e2'
  secondary-fixed-dim: '#c6c6c6'
  on-secondary-fixed: '#1b1b1b'
  on-secondary-fixed-variant: '#474747'
  tertiary-fixed: '#e1e2e4'
  tertiary-fixed-dim: '#c5c6c8'
  on-tertiary-fixed: '#191c1e'
  on-tertiary-fixed-variant: '#444749'
  background: '#f9f9f9'
  on-background: '#1a1c1c'
  surface-variant: '#e2e2e2'
typography:
  display-lg:
    fontFamily: Manrope
    fontSize: 48px
    fontWeight: '800'
    lineHeight: 52px
    letterSpacing: -0.04em
  headline-lg:
    fontFamily: Manrope
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Manrope
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  body-lg:
    fontFamily: Manrope
    fontSize: 18px
    fontWeight: '500'
    lineHeight: 28px
  body-md:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 24px
  label-bold:
    fontFamily: Manrope
    fontSize: 14px
    fontWeight: '700'
    lineHeight: 20px
  label-md:
    fontFamily: Manrope
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  2xl: 48px
  gutter: 12px
  margin-mobile: 16px
---

## Brand & Style

The design system is built on the principle of "De-AI-ified" UI—a direct response to the oversaturated trend of ethereal gradients, synthetic glows, and glassmorphism. It prioritizes human-centric legibility through high-contrast, flat architectural elements. 

The aesthetic is a blend of **Neo-Brutalism** and **Modern Minimalism**. It utilizes bold, solid blocks of color and heavy-weight typography to create a sense of permanence and reliability. The interface feels tactile and structured, using physical metaphors of stacked cards and distinct modular panels rather than digital "shimmer." The emotional response is one of clarity, efficiency, and confidence.

## Colors

The palette is strictly high-contrast to ensure maximum accessibility and visual punch. 

- **Primary Purple:** A vibrant, saturated violet used for calls to action, active states, and brand-critical identifiers.
- **Secondary Black:** Used for primary text and structural borders to anchor the design.
- **Neutral Base:** A crisp white background maintains a clean, "paper-like" feel.
- **Surface Tiers:** Off-whites and very light greys are used for background modules to differentiate content zones without relying on shadows.

**Strict Rule:** No gradients are permitted. All transitions between colors must be hard-edged and intentional.

## Typography

The typography system exclusively uses **Manrope** to maintain a modern, geometric sans-serif aesthetic. 

- **Weight as Hierarchy:** Since italics and decorative fonts are forbidden, hierarchy is established through dramatic shifts in font weight and size.
- **Oversized Headers:** Inspired by high-impact editorial layouts, headers should feel "uncomfortably large" to lead the user's eye.
- **Readability:** A minimum size of 14px is enforced across the entire system. "Tiny" secondary text is replaced by bold labels to ensure the UI remains "de-AI-ified" and accessible.
- **Formatting:** All text is set with standard or tight letter spacing; tracking out text is reserved only for uppercase labels.

## Layout & Spacing

The layout philosophy follows a **Modular Card-Based** model. Content is housed within distinct containers that stack vertically or horizontally with consistent margins.

- **Grid:** A 4-column mobile grid with 12px gutters and 16px side margins.
- **The "Stack" Effect:** Elements should feel like physical objects placed on top of one another. This is achieved through solid fill colors and 1px or 2px solid borders.
- **Density:** Spacing is generous within modules to promote focus, but compact between modules to create the "Rolodex" feel of densely packed information.
- **Safe Areas:** Adhere to a "safe zone" of 24px from the bottom of the screen for navigation elements to ensure comfort on modern edge-to-edge mobile devices.

## Elevation & Depth

This design system rejects ambient shadows and blurs. Depth is communicated through:

1.  **Tonal Stacking:** Using slightly darker or lighter solid background colors to indicate "lift." 
2.  **Hard Borders:** A 1px or 2px solid #000000 border around cards creates a distinct separation from the background, mimicking a cut-out paper effect.
3.  **Flat Overlays:** When a modal or menu appears, it should use a solid, high-opacity dimming layer (e.g., Black at 40%) rather than a blur. 
4.  **Offset Fills:** To indicate a "pressed" or "active" state, an element may shift 2px down and right, or reveal a secondary solid color block underneath it, simulating a mechanical button.

## Shapes

The shape language is "Calculated Softness." While the layout is brutalist and structured, the corners are moderately rounded to keep the app feeling like a modern mobile product rather than a desktop utility.

- **Standard Radius:** 0.5rem (8px) for primary buttons and small input fields.
- **Large Radius:** 1rem (16px) for main content cards and the "Connection Rolodex" modules.
- **Interactive Circles:** Icons and small action buttons (like "Search" or "Add") use 100% rounding (pill/circle) to distinguish them from informational containers.

## Components

### Buttons
- **Primary:** Solid Purple fill, White bold text, 8px corner radius. No shadow.
- **Secondary:** Solid White fill, 2px Black border, Black bold text.
- **Active State:** On press, the button background color should darken by 10% with no transition (instant feedback).

### Cards
- **Base Style:** Solid background (White or Light Grey), 2px solid Black border, 16px corner radius.
- **Stacked Variant:** For "Connections," cards should overlap slightly or be separated by a heavy 12px gutter to mimic a physical rolodex.

### Inputs & Search
- **Text Fields:** 2px solid Black border, White background, 8px radius. Placeholder text should be high-contrast (Dark Grey) to avoid the "faded" look.
- **Search Bar:** Pill-shaped (circular ends) with a prominent 2px border and a leading Purple icon.

### Chips & Tags
- **Style:** Small, high-contrast pills with solid fills (e.g., Light Purple with Dark Purple text). Used for categorization on the Map or Discovery screen.

### Navigation
- **Tab Bar:** Floating or anchored bar with solid background and 2px top border. Active state is indicated by a solid Purple circle behind the icon, never a glow or soft indicator.