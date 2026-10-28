# SnapSpend Bug Fixes & UX Polish Plan

## 1. Overview
This plan resolves key state-synchronization and calculation bugs, fixes navigation and temporary file cleanup edge cases, and upgrades the filter and navigation UX across the **Home**, **Transactions**, **Charts & Insights**, and **Settings** screens.

---

## 2. Data, Calculation & Lifecycle Bug Fixes

### A. Unified Charts & Insights Filtering (`SnapSpendViewModel.kt` & `ChartsAndInsightsScreen.kt`)
- **Synchronized Category Dropdown Filtering**:
  - Move `selectedChartCategoryFilter` into `SnapSpendViewModel` (or pass it cleanly through state computation) so that selecting a category in the Charts pill dropdown filters **all** visual cards in unison:
    - Total Spent Header & Donut Breakdown (`displayedCategorySlices`)
    - Month-over-Month / Period Comparison Bars (`editorialPillBars`)
    - Daily / Monthly Trend Line (`lineChartPoints`)
    - Top 5 Spending Places (`top5Places`)
- **Strict Date-Range Filtering on Top 5 Places**:
  - Remove the `.ifEmpty { chartModeFilteredTxs }` fallback when computing `top5Places` in `SnapSpendViewModel.kt`. If a selected date range (e.g., *This Week* or *Last Month*) has zero transactions, `top5Places` will accurately reflect the empty range instead of leaking all-time merchants.

### B. ATM Withdrawal / Cash Top-Up Category Consistency (`MainActivity.kt`)
- Update the `+ TOP UP / ATM WITHDRAWAL` action on the Transactions Cash tab so the pre-populated `TransactionEntity` uses `category = "Transfers"` (matching `SnapSpendViewModel.topUpCashWalletBalancePaise`) instead of `"Other"`.

### C. Decouple Budget Notification Side-Effects from `uiState` Flow (`SnapSpendViewModel.kt`)
- Remove the `checkAndDispatchBudgetNotifications(budgetStatuses, budgets)` call from inside the `combine(...)` state-builder lambda so typing in search bars or switching chart filters does not repeatedly spawn background coroutines.
- Observe active transactions and budgets in a dedicated coroutine collector inside `init` that triggers notification checks only when transaction totals or budget limits actually change.

### D. Camera Temp File Cleanup & Replay Back Navigation (`MainActivity.kt`, `OnboardingCarouselScreen.kt`)
- **Camera Temp File Cleanup**: Ensure temporary camera capture files (`cameraTempFile`) are deleted in a `finally`/cleanup path even when Gemini returns `Unreadable` or `Error`, preventing orphaned files in `cacheDir`.
- **Onboarding Replay `BackHandler`**: Add `BackHandler` support when replaying *"How to Use SnapSpend"* from Settings so the system back gesture returns to Settings instead of closing the app.

---

## 3. UX & Interaction Polish

### A. Direct Popup Menus for Pill Dropdowns (`TransactionsScreen.kt`, `ChartsAndInsightsScreen.kt`, `MainActivity.kt`)
- Upgrade `CompactPillDropdown` so tapping a Date Range pill (*This Month*, *Last Month*, *This Week*, *This Year*, *All Time*, *Custom Range*) or Category pill (*All Categories* + categories) opens a styled Material 3 `DropdownMenu` anchored to the pill for direct 1-tap selection instead of cycling sequentially through options one by one.
- Highlight the currently active option with a checkmark and subtle coral/amber accent inside the menu.

### B. Keyboard Focus & Scroll-to-Top Ergonomics (`MainActivity.kt`)
- Automatically dismiss the software keyboard (`LocalFocusManager.current.clearFocus()`) when the user selects a filter chip, changes the date range/payment mode pill, or switches bottom navigation tabs.
- Maintain a `LazyListState` on the Transactions list that smoothly scrolls back to item `0` when the active category filter, payment mode filter, or date range changes.

### C. Empty Filtered State Quick-Reset Actions (`MainActivity.kt` & `ChartsAndInsightsScreen.kt`)
- **Transactions Empty Filter State**: When transactions exist in the database but the active combination of Search Query, Category, Date Range, or Payment Mode yields zero matches, display a clear **"Reset Filters"** button inside the empty state card to restore default filters in one tap.
- **Charts Empty Filter State**: When a selected Date Range or Category filter on the Charts screen has zero matching spend, show a contextual empty state with a **"Reset Chart Filters"** pill button to jump back to *This Month · All Categories*.

---

## 4. Verification Plan
- Run `compile_applet` to verify zero compilation or type errors across all updated screens and ViewModel flows.
- Verify unit/Robolectric tests continue to pass.
