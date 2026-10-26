# Interactive 3D Onboarding Carousel with Floating Bills, App Logos & Particles

Transform the 5-slide first-launch onboarding carousel into a dynamic, playful, tactile 3D experience featuring interactive floating currency bills, minted 3D coins, ambient particles, authentic UPI app badges (Google Pay, PhonePe, Paytm, CRED), and touch/drag reactive physical tilt.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences were confirmed during the interactive planning interview:
> - **Behavior**: Interactive floating bills, coins, and sparkles that tilt and drift dynamically based on touch and page offset.
> - **Visual Elements**: A vibrant mix of popular Indian UPI app badges (Google Pay, PhonePe, Paytm, CRED), rupee bills (₹500, ₹200, ₹100), credit card chips, and 3D coins.
> - **Aesthetic**: Chunky tactile 3D editorial design system with deep extruded drop-shadows, 2dp black outlines, and layered floating cards that react to finger drags and motion.

---

## 1. Overview & Core Concept

### What It Does
Upgrades `OnboardingCarouselScreen.kt` from static boxes into an interactive, multi-layered 3D playground:
- **Floating 3D Money & App Ecosystem**: Each slide features layered floating elements—crisp rupee banknotes (₹500 teal/olive, ₹200 orange/gold, ₹100 lavender/blue), minted 3D gold coins (`₹`), sparkling confetti/star particles, and prominent badges for popular payment apps (GPay, PhonePe, Paytm, CRED).
- **Physical Tilt & Drift Interaction**: Users can interact with the cards and floating layers: dragging or touching causes elements to tilt in 3D perspective (`rotationX`, `rotationY`, `cameraDistance`, `translationZ`), while ambient physics give floating bills and coins an organic floating bob.
- **Micro-interactions & Particles**: Touching the screen spawns micro-bursts of golden coins and sparkles; swiping between slides produces depth-parallax where background particles drift slower than foreground currency and app badges.

### Target Audience & Persona
Every new or returning user opening SnapSpend for the first time or reviewing the guide from Settings. Instead of reading flat text, users are greeted with a high-energy, memorable showcase of the financial apps they use every day in India.

### Key Value
Creates an unforgettable first impression, instantly communicating that SnapSpend understands UPI payments, receipts, and budgets with zero friction and delightful tactile polish.

---

## 2. User Experience & Visual Design

### Slide-by-Slide 3D Visual Experience

```
┌─────────────────────────────────────────────────────────────┐
│  1 / 5                                            [ SKIP ]  │
│                                                             │
│  SNAP IT. SORT IT.                                          │
│  SnapSpend reads your payment screenshots and sorts every   │
│  rupee into the right category.                             │
│                                                             │
│       ┌──────────┐  ★ [GPay]        💵 [₹500 Bill]         │
│       │ 🤖 Masc. │      🪙 [₹ Coin]       💳 [CRED Chip]    │
│       │ Receipt  │                                          │
│       └────┬─────┘     [PhonePe] 🟣       ✨ [Sparkles]     │
│            ▼ (Tilts on finger touch / ambient drift)        │
│                                                             │
│         ●  ○  ○  ○  ○                 [  NEXT  ➔  ]         │
└─────────────────────────────────────────────────────────────┘
```

1. **Slide 1 — Indigo (`#5B5CF6`) · "SNAP IT. SORT IT."**:
   - **Hero Visual**: Mascot robot holding an animated receipt card in 3D perspective.
   - **Floating Objects**: A floating ₹500 rupee bill gently waving in 3D, a PhonePe purple pill badge, a Google Pay tri-color badge, floating gold `₹` coins, and shimmering ambient star particles.
   - **Interactive Layer**: Touching the screen makes the robot and coins tilt toward the touch point.

2. **Slide 2 — Sunny Yellow (`#FFD23F`) · "ADD IN SECONDS"**:
   - **Hero Visual**: Staggered deck of the 5 quick-add source cards (Camera, Screenshots, Voice Note, PDF, Manual).
   - **Floating Objects**: Floating camera lens badge, Paytm cyan badge, CRED dark metallic card badge with chip, a floating ₹200 banknote, and floating microphone icon.
   - **Interactive Layer**: Cards separate and fan out slightly on touch/drag, revealing depths.

3. **Slide 3 — Coral (`#FF6F59`) · "YOU STAY IN CONTROL"**:
   - **Hero Visual**: Chunky 3D review card with merchant avatar, amount (`₹1,249.00`), category pill, black `CONFIRM` button, and the `"EXAMPLE"` tag.
   - **Floating Objects**: Green checkmark stamp pill, duplicate prevention shield badge, floating ₹100 banknote, and a merchant badge (e.g. Swiggy / Zomato order pill).
   - **Interactive Layer**: Tapping the review card triggers a playful haptic and card bounce.

4. **Slide 4 — Aqua (`#8FE3F0`) · "SEE WHERE IT GOES"**:
   - **Hero Visual**: 3D bar chart & spending trend card with extruded shadow and peeking category cards.
   - **Floating Objects**: Category circular chips (Food 🍔, Groceries 🛒, Shopping 🛍️), percentage badges (`+14%`, `80% Limit`), and floating gold coins falling gently into a mini piggy bank.
   - **Interactive Layer**: Touching the chart triggers a gentle bounce on the highest bar.

5. **Slide 5 — Mint (`#9FE3C0`) · "YOUR MONEY, YOUR PHONE"**:
   - **Hero Visual**: 3D Smartphone safe with an extruded black brass padlock and green "ON-DEVICE ONLY" badge.
   - **Floating Objects**: Shield badge, Google Drive cloud badge with lock icon, encrypted key badge, and floating confetti particles.
   - **Interactive Layer**: Padlock has a slight spring rotation when the slide enters.

### Motion & Physics Specification
- **Ambient Floating Physics**: Sine-wave oscillation with staggered phase offsets (`sin(time + index * offset)`) so bills, coins, and app badges never move in unison.
- **Touch / Drag Tilt**: `PointerInputScope.detectDragGestures` and `detectTapGestures` calculating normalized touch coordinates `(dx, dy) in [-1f, 1f]`, mapped to `rotationX = -dy * 12f` and `rotationY = dx * 14f` with `cameraDistance = 16f * density`.
- **Reduced Motion Support**: When system reduced motion is enabled, 3D rotations, rapid oscillations, and particle emitters settle into clean, elegant static offsets without motion fatigue.

---

## 3. Key Product Decisions & Trade-Offs

| Decision | Selected Approach | Why | Alternative Rejected |
| :--- | :--- | :--- | :--- |
| **Asset Implementation** | Pure Jetpack Compose Vectors & Canvases | Eliminates external PNG/WebP assets, stays razor-sharp on all screen densities, matches app palette, and eliminates network/bundle bloat. | External raster images or Lottie animations (heavy, inflexible colors). |
| **App Badges** | Iconic vector interpretations of GPay, PhonePe, Paytm & CRED | Instantly recognizable to Indian smartphone users, establishing authentic context for payment tracking. | Generic abstract shapes that feel detached from real-world payment apps. |
| **Physics & 3D Layering** | Compose `graphicsLayer` + `animateFloatAsState` springs | High 60/120fps performance, hardware accelerated, battery efficient, zero GC overhead. | Complex 3D engine (Sceneform / Filament) which would balloon APK size. |
| **Existing Logic Safety** | Strict modification boundary to `OnboardingCarouselScreen.kt` | Guarantees zero regression on existing SQLite Room data, DataStore flags, backup logic, or permissions. | Altering ViewModel or navigation graph. |

---

## 4. Technical Architecture & Component Structure

### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                      OnboardingCarouselScreen                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ TopBar: Step Counter ("1 / 5") + Skip Pill                       │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ HorizontalPager (5 Pages)                                        │  │
│  │  ┌────────────────────────────────────────────────────────────┐  │  │
│  │  │ Interactive3DCarouselSlide                                 │  │  │
│  │  │  ├─ Title & Body Text (40sp Condensed Title)               │  │  │
│  │  │  ├─ Background Particle Canvas (Sparkles, Starbursts)      │  │  │
│  │  │  ├─ Floating Elements Layer (Bills, Coins, App Badges)     │  │  │
│  │  │  │   ├─ FloatingRupeeBill (₹500 / ₹200 / ₹100)             │  │  │
│  │  │  │   ├─ FloatingCoin3D (Gold extruded ₹ coin)              │  │  │
│  │  │  │   └─ FloatingAppBadge (GPay, PhonePe, Paytm, CRED)      │  │  │
│  │  │  └─ Center 3D Interactive Hero Card                       │  │  │
│  │  │      ├─ Slide 1: MascotRobotIllustration                   │  │  │
│  │  │      ├─ Slide 2: QuickAddDeckIllustration                  │  │  │
│  │  │      ├─ Slide 3: ReviewDraftCardIllustration (EXAMPLE tag) │  │  │
│  │  │      ├─ Slide 4: SpendingChartIllustration                 │  │  │
│  │  │      └─ Slide 5: OnDeviceVaultIllustration                 │  │  │
│  │  └────────────────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ BottomBar: Morphing Page Dots + 3D Yellow "NEXT" / "LET'S GO"    │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Component Details to Build in `OnboardingCarouselScreen.kt`:
1. **`FloatingRupeeBill`**:
   - Distinct banknote colors (₹500 Stone Teal, ₹200 Bright Orange, ₹100 Lavender).
   - Crisp details: denomination numeral, `₹` symbol, subtle guilloche pattern border, and 3D extruded drop shadow.
   - Independent floating motion (`infiniteRepeatable` with phase offset).
2. **`FloatingCoin3D`**:
   - Shiny golden coin with rim, inner circle, embossed `₹` glyph, and extruded shadow.
   - Continuous gentle 3D wobble.
3. **`FloatingAppBadge`**:
   - **Google Pay**: Pill card with multi-colored GPay ribbon geometry (Blue, Red, Yellow, Green).
   - **PhonePe**: Purple circular icon with stylized `पे` / PhonePe emblem.
   - **Paytm**: Sky-blue card with navy `"Paytm"` typography and payment checkmark.
   - **CRED**: Sleek black & gold card pill with metallic gold IC chip and CRED styling.
4. **`InteractiveTiltBox`**:
   - Custom `Modifier` wrapping hero cards that captures pointer movement and tilts the card in 3D space (`rotationX`, `rotationY`, `cameraDistance`).
5. **`AmbientParticleEmitter`**:
   - Lightweight Compose Canvas drawing floating golden sparkles, cross stars, and soft bokeh circles drifting behind the cards.

---

## 5. Verification Plan

- **Compilation**: Run `compile_applet` to ensure error-free Kotlin and Compose compilation.
- **Unit & Robolectric Tests**: Run `gradle :app:testDebugUnitTest` to guarantee all existing CUJs, database tests, and preferences tests pass without regressions.
- **Visual & Interaction Verification**:
  - Test page-to-page swipe transitions, background color cross-fades, and parallax depths.
  - Verify touch/drag tilt responsiveness on all 5 slides.
  - Verify dark and light theme contrast and colors.
  - Verify that system navigation and status bar insets remain clean.
  - Test re-opening the onboarding guide from the Settings `"How to use SnapSpend"` card.
