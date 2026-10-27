# Adaptive Icon-Only 3D Editorial Bottom Navigation Bar

This update transforms the bottom navigation bar into a sleek, icon-only 3D editorial bar that dynamically adapts to Light Mode and Dark Mode, featuring black line icons on warm cream in Light Mode and `#FFDAB9` (Peach Puff) icons on dark obsidian in Dark Mode.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following design decisions have been confirmed from your preferences:

- **Confirmed Decision 1 — Icon-Only Layout with Floating Accent Pill**: All text labels are removed from the navigation bar. The active tab is highlighted exclusively by a 3D floating accent pill that slides smoothly behind the active icon.
- **Confirmed Decision 2 — Dual-State Icon Differentiation**: Inactive tabs render with crisp outlined line icons, while the active tab switches to a bold filled icon with a subtle spring scale bounce (`1.15x`).
- **Confirmed Decision 3 — Adaptive Light & Dark Theme Surfaces & Icon Tints**:
  - **Light Mode**: Editorial warm cream surface (`#F3EEE3`) with crisp black borders (`#0B0B0B`), solid black 3D shadow extrusion, and black (`#0B0B0B`) icons.
  - **Dark Mode**: Deep obsidian surface (`#14141E`) with subtle slate borders (`#2E2E40`), deep ink 3D extrusion (`#09090E`), and `#FFDAB9` (warm peach) unselected icons with high-contrast ink black (`#0B0B0B`) on the bright sliding active pill (or `#FFDAB9` tint option where appropriate for maximum legibility).

---

## 1. Overview & Core Concept

- **What It Does**: Replaces the text-and-icon bottom navigation bar with a balanced, symmetric icon-only navigation dock that automatically switches its surface palette, border treatment, 3D shadow extrusion, and icon coloration based on the active theme (Light vs. Dark).
- **Target Audience / Persona**: Users who want a clean, uncluttered, modern navigation bar that feels cohesive with both the warm paper-like editorial Light Mode and the high-contrast obsidian Dark Mode.
- **Key Value**: Eliminates label truncation and horizontal crowding while making every tab equally proportioned and visually grounded in the current theme.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Theme Switching**: When toggling between Light Mode and Dark Mode (via the top bar toggle or system theme), the bottom navigation bar smoothly animates its container surface, border color, and icon tints in real time.
  2. **Tab Selection**: Tapping any of the 5 icon slots triggers a light haptic tap, compresses the icon with physical press spring physics, slides the 3D accent pill behind the newly selected icon, and morphs the icon from outlined to filled with a spring scale bounce.
  3. **Alert Indicators**: Unselected tabs continue to display non-intrusive status dots (Coral warning dot on Categories when budgets are at risk; Sunny Yellow dot on Transactions when pending items exist).

- **Visual Identity & Theme**:
  - **Light Mode Posture**:
    - *Bar Surface*: Warm Editorial Cream (`#F3EEE3`)
    - *Bar Border*: Crisp `1.5dp` Ink Black (`#0B0B0B`)
    - *3D Extrusion Shadow*: Solid `3.5dp` Ink Black (`#0B0B0B`)
    - *Inactive Icons*: Outlined line icons in pure Ink Black (`#0B0B0B`)
    - *Active Pill & Icon*: 3D extruded pill in the tab's signature accent color (`#FFD23F`, `#FF6F59`, `#8FE3F0`, `#9FE3C0`, `#C9A0FF`) with a `1.5dp` black border and a filled Ink Black (`#0B0B0B`) icon.
  - **Dark Mode Posture**:
    - *Bar Surface*: Deep Obsidian (`#14141E`)
    - *Bar Border*: Subtle Slate (`1.5dp` `#2E2E40`)
    - *3D Extrusion Shadow*: Deep Ink (`3.5dp` `#09090E`)
    - *Inactive Icons*: Outlined line icons in warm Peach Puff (`#FFDAB9`)
    - *Active Pill & Icon*: 3D extruded pill in a deep tonal obsidian/charcoal capsule bordered by `#FFDAB9` (or vibrant accent pill) so the filled `#FFDAB9` icon shines with crisp contrast in Dark Mode.

- **Interactive Feedback & Motion**:
  - Physical spring sliding (`stiffness = 380f`, `dampingRatio = 0.78f`) for the floating pill X-offset.
  - Smooth color interpolation (`animateColorAsState`) when switching tabs or toggling Light/Dark themes.
  - Minimum `48dp x 48dp` interactive touch targets across all 5 equally distributed icon slots.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Equal-Width Icon Slots vs. Variable-Width Expandable Tabs**
  - *Chosen Approach*: Equal-width icon slots with a fixed-proportion squircle floating pill (`52dp x 44dp`) centered behind the active icon.
  - *Why*: With text labels removed, equal-width slots guarantee mathematical symmetry and predictable pill travel distance across all screen widths.
  - *Alternatives Considered*: Content-measured variable widths, which are only necessary when expanding text labels change a tab's width.

- **Decision 2: Dark Mode Active Icon & Pill Contrast with `#FFDAB9`**
  - *Chosen Approach*: In Dark Mode, all inactive outlined icons render in `#FFDAB9`, and the active filled icon also renders in `#FFDAB9` atop a rich dark plum-obsidian 3D floating pill (`#262338`) accented with a `#FFDAB9` border glow (while Light Mode uses the vibrant pastel 3D accent pill with a black border and black filled icon).
  - *Why*: Ensures that in Dark Mode, both active and inactive icons honor your exact `#FFDAB9` shade requirement while maintaining a distinct, high-contrast floating 3D pill behind the active icon.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌──────────────────────────────────────────────────────────────────────┐
│                        SnapSpendRootScreen                           │
│   Reads: isDark (SnapSpendThemeTokens), currentTab, badge counts     │
└──────────────────────────────────┬───────────────────────────────────┘
                                   │ Passes isDark, currentTab,
                                   │ pendingCount, hasBudgetWarning
                                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│                      EditorialBottomNavBar                           │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │               Editorial3DLayerBox (Outer Dock)                 │  │
│  │  Light: #F3EEE3 surface, #0B0B0B border & 3.5dp 3D extrusion   │  │
│  │  Dark:  #14141E surface, #2E2E40 border & 3.5dp #09090E shadow │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │       Sliding 3D Floating Pill (Animated X Offset)       │  │  │
│  │  │  Light: Tab Accent Fill + 1.5dp #0B0B0B Border           │  │  │
│  │  │  Dark:  Tonal Pill (#262338) + 1.5dp #FFDAB9 Border      │  │  │
│  │  └──────────────────────────────────────────────────────────┘  │  │
│  │  ┌──────────┬──────────┬──────────┬──────────┬──────────────┐  │  │
│  │  │   HOME   │  CHARTS  │CATEGORIES│   TXNS   │   SETTINGS   │  │  │
│  │  │ (Icon)   │  (Icon)  │ (Icon+!) │ (Icon+•) │    (Icon)    │  │  │
│  │  └──────────┴──────────┴──────────┴──────────┴──────────────┘  │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

- **Interactive Component & State Mapping**:
  - `isDark` state from `SnapSpendThemeTokens.colors.isDark` drives animated surface, border, pill, and icon colors (`#0B0B0B` in Light Mode vs. `#FFDAB9` in Dark Mode).
  - Each tab slot measures its center/position via `onGloballyPositioned` so the `52dp x 44dp` floating 3D pill centers accurately behind the active icon.
  - All existing accessibility semantics (`Role.Tab`, `contentDescription = tab.label`) and automated UI test tags (`nav_tab_home`, `nav_tab_charts`, `nav_tab_categories`, `nav_tab_transactions`, `nav_tab_settings`) remain intact.
