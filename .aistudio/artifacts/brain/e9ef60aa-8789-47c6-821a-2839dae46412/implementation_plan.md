# Dead Code Cleanup Plan

Safe, non-breaking cleanup of unused imports, orphaned legacy helper functions, and unused wrappers across the SnapSpend codebase, guaranteeing zero changes to user experience, UI rendering, data persistence, or business logic.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed with the user during the planning phase:
> - **Cleanup Scope**: Focus on unused imports, unused private/internal functions, and orphaned legacy helpers.
> - **Behavior Invariance**: Strictly avoid touching any active logic, Room database entities, DataStore flags, UI components, or financial calculations.
> - **Verification Standard**: Verification via full compilation (`compile_applet`) and complete Robolectric test execution (`gradle :app:testDebugUnitTest`).

---

## 1. Overview & Core Concept

### What It Does
Audits and prunes dead code that has accumulated during iterative feature development:
1. **Unused Imports**: Removes obsolete, unreferenced imports across UI screens, components, theme files, and data classes.
2. **Orphaned Camera Thumbnail Pipeline**: Removes legacy `processCapturedCameraBitmap` from `SnapSpendViewModel` and `extractFromCameraBitmap` from `GeminiReceiptExtractor` (which were rendered obsolete when camera capture transitioned to full-resolution `Uri` via Android `FileProvider`).
3. **Legacy Unreferenced Wrappers**: Removes uncalled legacy wrappers and helpers such as `EditorialLimePillBarChartCard`, `Modifier.verticalRotatedLabel()`, and `lighterCardTint`.

### Key Value
Keeps the codebase clean, lean, and maintainable, reducing build time and eliminating confusion without any functional or visual regressions.

---

## 2. Inventory of Dead Code to Clean

### A. Orphaned Functions & Legacy Wrappers
```
┌──────────────────────────────────────┬─────────────────────────────────────────────────────────┐
│ File                                 │ Target Orphaned Symbol / Rationale                      │
├──────────────────────────────────────┼─────────────────────────────────────────────────────────┤
│ SnapSpendViewModel.kt                │ fun processCapturedCameraBitmap(bitmap: Bitmap)        │
│                                      │ - Legacy thumbnail method; camera now uses URI pipeline │
│ GeminiReceiptExtractor.kt            │ suspend fun extractFromCameraBitmap(bitmap, categories) │
│                                      │ - Only called by the legacy method above                │
│ EditorialCharts.kt                   │ fun EditorialLimePillBarChartCard(...)                  │
│                                      │ - Unreferenced legacy compatibility wrapper             │
│ EditorialCharts.kt                   │ fun Modifier.verticalRotatedLabel()                     │
│                                      │ - Unreferenced custom layout modifier                   │
│ Color.kt                             │ fun lighterCardTint(cardColor: Color): Color            │
│                                      │ - Unreferenced color interpolation utility              │
└──────────────────────────────────────┴─────────────────────────────────────────────────────────┘
```

### B. Unused Imports by File
1. **`MainActivity.kt`**:
   - `androidx.compose.animation.animateContentSize`
   - `com.example.ui.theme.EditorialCoral`
   - `com.example.ui.theme.SecondaryGreyText`
2. **`GeminiReceiptExtractor.kt`**:
   - `com.google.firebase.ai.type.thinkingConfig`
3. **`LocalInsightsAndRulesEngine.kt`**:
   - `kotlin.math.roundToInt`
4. **`SnapSpendViewModel.kt`**:
   - `com.example.data.local.TransactionStatus`
5. **`EditorialCharts.kt`**:
   - `androidx.compose.animation.AnimatedContent`
   - `androidx.compose.animation.core.Spring`
   - `androidx.compose.animation.core.animateDpAsState`
   - `androidx.compose.animation.fadeIn`, `fadeOut`, `scaleIn`, `scaleOut`, `togetherWith`
   - `androidx.compose.ui.geometry.CornerRadius`
   - `com.example.ui.theme.CandyCardPalette`
   - `com.example.ui.theme.DarkCardSurface`, `DarkSubtleBorder`, `TrueBlackBg`
6. **`EditorialComponents.kt`**:
   - `androidx.compose.animation.core.Spring`
   - `androidx.compose.foundation.layout.fillMaxHeight`
   - `androidx.compose.material3.HorizontalDivider`
   - `com.example.ui.theme.DarkCardSurface`, `DarkSubtleBorder`
7. **`PermissionsDialogs.kt`**:
   - `androidx.compose.foundation.layout.width`
   - `androidx.compose.ui.graphics.vector.ImageVector`
8. **`TransactionsFilterBottomSheet.kt`**:
   - `com.example.ui.theme.InkBlack`
9. **`ChartsAndInsightsScreen.kt`**:
   - `com.example.ui.theme.DarkCardSurface`, `DarkSubtleBorder`, `SecondaryGreyText`
10. **`HomeScreen.kt`**:
    - `androidx.compose.animation.core.Spring`, `spring`
    - `com.example.ui.theme.DarkCardSurface`, `DarkSubtleBorder`, `SecondaryGreyText`
11. **`OnboardingCarouselScreen.kt`**:
    - `androidx.compose.foundation.layout.BoxWithConstraints`
    - `androidx.compose.material.icons.filled.AutoAwesome`, `Warning`
    - `androidx.compose.ui.geometry.CornerRadius`, `Size`
    - `androidx.compose.ui.graphics.Brush`
    - `androidx.compose.ui.platform.LocalDensity`
12. **`ReviewAndAddSheet.kt`**:
    - `com.example.ui.theme.CandyLavenderPurple`
    - `com.example.ui.theme.PastelCyan`
13. **`SettingsAndBackupScreen.kt`**:
    - `com.example.ui.theme.DarkCardSurface`, `DarkSubtleBorder`, `PeriwinkleIndigo`
14. **`TransactionsScreen.kt`**:
    - `com.example.ui.theme.DarkCardSurface`, `DarkSubtleBorder`, `EditorialAqua`, `SecondaryGreyText`
15. **`Motion.kt`**:
    - `androidx.compose.animation.core.AnimationSpec`
    - `androidx.compose.runtime.SideEffect`
    - `kotlinx.coroutines.launch`

---

## 3. Strict Safety & Non-Regression Rules

- **Preserve Compose Property Delegate Imports**: Retain `getValue` and `setValue` imports across all Compose files using `by remember` / `by state` syntax.
- **Preserve Room Type Converters & DAOs**: Retain all Room `@TypeConverter` methods in `SnapSpendDatabase.kt` and DAO query methods in `SnapSpendDao.kt`.
- **Preserve Tested Utilities**: Retain `MoneyUtils.formatPaiseCompact` which is actively verified in `SmoketestResilienceTest.kt`.
- **Zero Behavioral Impact**: No modification to app strings, layouts, UI tests, database schemas, or business calculations.

---

## 4. Verification Plan

1. **Compilation Check**:
   - Run `compile_applet` to confirm zero compilation errors or missing symbol warnings.
2. **Automated Robolectric Tests**:
   - Run `gradle :app:testDebugUnitTest` to guarantee all critical user journeys, database operations, export/import round-trips, and financial calculations continue passing at 100%.
