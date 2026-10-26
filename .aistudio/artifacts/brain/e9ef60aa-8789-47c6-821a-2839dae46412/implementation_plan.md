# SnapSpend Unified Motion & Transition System Plan

A cohesive, interruptible 60fps motion system across every screen, card, button, chart, sheet, and dialog in both Light and Dark themes—preserving all existing data, calculations, and the 3D swipeable category card deck while making the entire app feel tactile and alive.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Shared Motion Tokens & Accessibility First:**
> Every animation in the app will read from a single centralized motion specification and respect the Android system **"Remove animations"** (`Settings.Global.ANIMATOR_DURATION_SCALE == 0f`) setting via a `LocalReducedMotion` composition local. When reduced motion is active, spring and tween transitions resolve instantaneously without visual lag or looping effects.

- **Exact Shared Timings & Springs (`Motion` Tokens)**:
  - **Quick (Taps, Presses, Check-Mark Pop, Icon Swaps)**: `120ms` (`FastOutSlowInEasing`), or tactile press spring (`dampingRatio = 0.65f`, `stiffness = 650f`).
  - **Normal (Tab Slides, Card Staggered Entrances, Sheets, Dialogs, Theme Cross-Fade)**: `300ms` (`CubicBezierEasing(0.2f, 0f, 0f, 1f)`), with `200ms` fast exit/dismiss and `50ms` per-item entrance stagger (capped at the first 8 items).
  - **Charts & Money Counters**: `600ms` (`CubicBezierEasing(0.16f, 1f, 0.3f, 1f)` ease-out) for money count-ups, area chart line draw, donut sweep, and progress bar fills; `30ms` per-bar stagger on bar charts.
  - **Physical Object Spring (Shared with Category Card Deck)**: Medium-bouncy spring (`dampingRatio = Spring.DampingRatioMediumBouncy` / `0.58f`, `stiffness = Spring.StiffnessMediumLow` / `320f`) for sliding pills, tooltips, bottom sheets, and card physics.
- **60fps Performance & What We Skip / Optimize**:
  - **Skipped**: Animating intrinsic layout height/width per frame inside long scrolling `LazyColumn` rows, and running infinite bobbing loops when an empty-state illustration is off-screen.
  - **Why**: Layout re-measurement on every animation frame causes dropped frames on mid-range phones. Instead, all press-in depth shifts, card entrances, dialog scales, and chart reveals run on the RenderThread via `Modifier.graphicsLayer` and `Canvas` draw fractions.

---

## 1. Overview & Core Concept

- **What It Does**: Unifies all micro-interactions, screen transitions, number count-ups, chart builds, and AI scanning feedback under one consistent physical motion language.
- **Target Audience / Persona**: Everyday mobile users tracking expenses who want immediate, physical button feedback and smooth visual continuity when switching tabs, filtering charts, or scanning receipts.
- **Key Value**: Every interaction feels responsive and interruptible—buttons physically press into their black 3D shadows, numbers roll smoothly in tabular numerals, and charts draw progressively without ever blocking touch input.

---

## 2. User Experience & Visual Design

### 0. Centralized Motion Specification & Accessibility
- All durations, easing curves, stagger delays, and spring specs live in one place and adapt automatically when Android's animator duration scale is set to `0x`.

### 1. Screen Switching (Bottom Navigation)
- **Directional Tab Slide + Fade (`300ms`)**: Switching to a tab further right slides the incoming screen in from `+24.dp` with a fade-in while the outgoing screen fades out; switching to a tab further left slides in from `-24.dp`.
- **Sliding Aqua Tab Pill & Spring Label**: The selected Aqua (`#8FE3F0`) pill glides smoothly across the bottom bar to the newly selected tab, and its text label expands/collapses using the shared medium-bouncy spring, paired with a light haptic tick on tap.

### 2. First-Open Screen Entrance (Once per Tab Session)
- **Staggered 24.dp Upward Reveal**: The first time each screen opens, its visible top-level cards fade in and slide up from `24.dp` one after another (`50ms` delay per item, capped at the first `8` items). Scrolling up and down afterward never re-triggers the entrance animation.

### 3. Tactile 3D Press Feedback (Buttons, Chips, Cards, Pills)
- **Push-Into-Shadow Physics**: On press (`awaitFirstDown` / `collectIsPressedAsState`), the foreground card or pill translates diagonally toward its black offset shadow (`+depthX`, `+depthY`) while the shadow offset shrinks to `(0.dp, 0.dp)`, looking like a real chunky mechanical button being pressed flush. Releasing springs it back with medium bounce.
- **Haptic Tick**: Main buttons, bottom nav tabs, segmented toggles, and filter chips trigger a light haptic tick on tap.

### 4. Counting Money Totals (`600ms` Ease-Out, Tabular Numerals)
- **Smooth Paise Counter**: Hero balance (`What's left`), `Total spent`, `Total earned`, chart summary boxes, and category totals animate smoothly from their previous paise value to the new target paise value over `600ms` ease-out using `fontFeatureSettings = "tnum"`. When the animation settles, it displays the exact integer paise amount.

### 5. Progressive & Animated Charts
- **Area Chart (`Spending Over Time`)**: The stroke line draws progressively from left to right (`0% → 100%` width), followed by a smooth fade-in of the 3D Coral area fill underneath. Switching between `This Week` and `This Month` interpolates point heights smoothly.
- **Bar Charts (`Coral Comparison` & `In vs Out`)**: Bars grow upward from the baseline one after another (`30ms` stagger). The selected bar's black tooltip bubble pops in with a bouncy scale spring (`0.6f → 1.0f`).
- **Donut Chart (`By Category`)**: Slices sweep clockwise from `-90°` at the top one after another.
- **Progress Bars (`What's left` & Category Budgets)**: Fill smoothly from `0f` to the target fraction (and between old and new fractions when filters change).

### 6. Segmented Controls & Filter Dropdowns
- **Sliding Indicator Pill**: In `Overview / Trends / Categories & Bills`, `Expenses / Earnings`, and `System / Light / Dark`, a single Aqua (`#8FE3F0`) indicator pill slides horizontally from the previous option to the newly selected option using the shared spring, while label colors cross-fade over `200ms`.
- **Dropdown Anchor Scale + Fade**: Filter dropdown pills trigger a crisp scale + fade transition (`0.92f → 1.0f`) anchored to the control.

### 7. Bottom Sheets & Dialogs
- **Quick Scan & Add Sheet**: Slides up with a medium-bouncy spring while its 5 colored option cards stagger in one after another (`40ms` apart).
- **Dialogs**: Scale in from `90% → 100%` with a `250ms` fade-in, and reverse faster (`160ms`) on dismiss.

### 8. Lists & Confirm Button Pop
- **Transactions List**: Newly added payments slide in from the top and briefly flash a warm highlight border/tint (`600ms`); deleted items shrink and fade out while remaining rows glide into place via `animateItem()`.
- **Confirm Check-Mark Pop**: When confirming scanned drafts, the confirm button plays a quick check-mark scale pop (`1.0f → 1.22f → 1.0f` over `220ms`) before closing the review overlay.

### 9. AI Scanning States (Shimmer, Laser Line, Status Cycle, Drop-In & Error Shake)
- **Scanning State**: While Gemini reads a receipt or screenshot, display a shimmering placeholder card (soft diagonal light sweep) alongside a thin horizontal scanning line moving up and down over the receipt preview, cycling status text every `1.2s`: `"Reading amount…"` → `"Finding the date…"` → `"Picking a category…"`.
- **Success & Error**: On success, extracted review draft cards drop in one after another (`50ms` stagger). On error, the error card performs a single horizontal shake (`-10dp → +10dp → -6dp → +6dp → 0dp` over `360ms`).

### 10. Theme Toggle Rotation & 300ms App-Wide Color Cross-Fade
- **Sun/Moon Icon Swap**: Tapping the top-bar theme button rotates (`-90° → 0°`) and scales (`0.6f → 1.0f`) the Moon/Sun icon.
- **300ms Color Cross-Fade**: Background and theme surface tokens cross-fade smoothly over `300ms` via `animateColorAsState` instead of snapping.

### 11. Empty-State Floating Illustrations
- **Gentle 4.dp Loop**: The robot, wallet, and empty-state illustrations gently bob up and down by `±4.dp` over a `3000ms` ease-in-out loop via `rememberInfiniteTransition`—active only while the composable is in the composition (visible on screen) and disabled when reduced motion is enabled.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Unified Push-Into-Shadow Press Interaction on `Editorial3DLayerBox`**
  - *Chosen Approach*: Build the press-in translation and shadow collapse directly into the shared 3D container and pill components (`Editorial3DLayerBox`, `WhitePillChip`, `WhitePillDropdown`, `RoundArrowButton`, `QuickAddFloatingPillButton`).
  - *Why*: Every card, button, and chip across all 5 screens automatically gets the exact same chunky push-button feel and interruptible spring release without duplicating pointer logic.
- **Decision 2: Session-Tracked First-Open Entrance Stagger**
  - *Chosen Approach*: Track which tabs have played their initial entrance in memory so each tab animates its top 8 cards once when first visited, and stays instant on subsequent scrolls.
  - *Why*: Satisfies the requirement that cards only animate on first open (never lagging or re-animating during normal scrolling).

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────────┐
│                     Centralized Motion System (Motion)                     │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ LocalReducedMotion (reads Settings.Global.ANIMATOR_DURATION_SCALE)   │  │
│  │ Durations: Quick=120ms | Normal=300ms | FastExit=180ms | Chart=600ms │  │
│  │ Springs: PhysicalSpring (damping=0.58f, stiffness=320f)              │  │
│  │          PressSpring    (damping=0.65f, stiffness=650f)              │  │
│  └──────────────────┬───────────────────────────────────────────────────┘  │
└─────────────────────┼──────────────────────────────────────────────────────┘
                      │
      ┌───────────────┼───────────────────────────────┬──────────────────────┐
      ▼               ▼                               ▼                      ▼
┌───────────┐  ┌─────────────────────────┐  ┌───────────────────┐  ┌─────────────────┐
│ Navigation│  │ 3D Cards, Pills & Lists │  │ Charts & Counters │  │ AI Scan & Theme │
├───────────┤  ├─────────────────────────┤  ├───────────────────┤  ├─────────────────┤
│• 24dp Tab │  │• Push-into-shadow press │  │• 600ms Paise roll │  │• Shimmer + Scan │
│  slide +  │  │  + light haptic tick    │  │  (tnum numerals)  │  │  line + 3-step  │
│  fade     │  │• First-open 8-item      │  │• Area line draw   │  │  status cycle   │
│• Sliding  │  │  stagger (50ms, 24dp)   │  │  then fill fade   │  │• Draft drop-in  │
│  Aqua pill│  │• Sliding segmented pill │  │• 30ms bar stagger │  │  & error shake  │
│  + spring │  │• New-tx highlight flash │  │  + tooltip pop    │  │• Sun/Moon spin  │
│  label    │  │  & animateItem() glide  │  │• Sequential donut │  │  + 300ms theme  │
└───────────┘  └─────────────────────────┘  └───────────────────┘  └─────────────────┘
```

- **State & Interruption**: All transitions use `animateFloatAsState`, `animateDpAsState`, `animateIntOffsetAsState`, `animateColorAsState`, and `AnimatedContent` backed by springs or interruptible tweens—so any mid-animation tap, swipe, or scroll immediately retargets from the current velocity and position.
