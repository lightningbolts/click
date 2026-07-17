# Visual & Technical Principles for Click

### Visual Language (Neo-Brutalist Tech)
- Color Palette: Primary (`#630ed4`), Background (`#f9f9f9`), High-contrast borders (`2px solid #000000`).
- Typography: `Manrope` font with bold, high-contrast headline hierarchies.
- Elevation & Borders: Sharp 2px black borders (`border-2 border-black`), rounded corners (`16px`), high-contrast card backgrounds.
- Touch Targets: Minimum 44x44px for touch targets, active press feedback (`translate-y-px`).

### Component Refactoring Rules (Do NOT Direct Copy HTML)
- Extract inline CSS / CDN Tailwind script configurations into native design tokens / Tailwind variables.
- Convert monolithic HTML structures into clean, modular components (e.g., `<Card />`, `<BottomNav />`, `<SearchInput />`).
- Ignore static/broken layout choices in raw HTML mockups if they violate accessibility or dynamic content flows.