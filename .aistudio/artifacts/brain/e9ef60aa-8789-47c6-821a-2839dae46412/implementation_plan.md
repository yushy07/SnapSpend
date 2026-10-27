# Part B Implementation Plan (Revised): Undo & Trash, Quick Repeats, and Month-End Estimate

This revised plan incorporates all 7 review corrections while preserving the existing neo-brutalist 3D look, money in paise (`Long`), `Motion.kt` timings, press feedback, Light/Dark themes, and existing AI scanning logic.

---

## Summary of Revisions Applied

1. **Real Room Schema Export & Migration Test (`2.json` → `3.json`)**:
   - Enable `exportSchema = true` on `AppDatabase` and configure KSP `room.schemaLocation` to `$projectDir/schemas` (plus wire `$projectDir/schemas` into test assets in `app/build.gradle.kts`).
   - **Before** modifying `TransactionEntity`, run KSP compilation at `version = 2` so Room generates and saves the real v2 schema at `app/schemas/com.example.data.local.AppDatabase/2.json`.
   - Then add `deletedAtMillis: Long? = null` to `TransactionEntity`, bump `AppDatabase` to `version = 3`, and compile to generate `app/schemas/com.example.data.local.AppDatabase/3.json`.
   - Write `DatabaseMigrationTest.kt` using Room's `MigrationTestHelper` backed by `2.json` and `3.json`: open v2, insert real rows, run `MIGRATION_2_3`, and assert every v2 column value is preserved and `deletedAtMillis` is `NULL`.
2. **Category Rename Updates Trashed Rows Too**:
   - `SnapSpendDao.updateTransactionsCategoryName` stays **intentionally unfiltered** (`UPDATE transactions SET category = :newName WHERE category = :oldName`) so restoring a trashed payment later never resurrects a stale category name.
3. **Explicit Audit of All DAO Queries Touching `transactions`**:
   - Both `deleteTransactionById` (`DELETE FROM transactions WHERE id = :id`) and `clearAllTransactions` (`DELETE FROM transactions`) remain **intentionally unfiltered** so they remove trashed rows too.
   - Quick Repeat's UNDO action calls `deleteTransactionById(id)` to permanently remove the accidental quick-add row.
   - Every single `@Query` touching `transactions` in `SnapSpendDao.kt` is listed below with its exact filter status.
4. **No Separate Data Check Card**:
   - The Part A Data Check screen is not present in the repository yet, so we will **not** build a substitute Data Check card in Settings and will report that it was skipped.
5. **Order of Operations & Clock Injection in `MonthEndMath.kt`**:
   - Month-end pace multiplies before dividing in `Long` paise:
     `val projectedSpendPaise = (spentPaise * daysInMonth.toLong()) / dayOfMonth.toLong()`
   - `MonthEndMath.computeMonthEndEstimate(...)` accepts explicit date/time inputs (`anchorMillis: Long`, `timeZone: TimeZone`) rather than reading the system clock internally, enabling deterministic leap-year and boundary-day unit tests.
6. **Superseding UNDO Bar State**:
   - If a second action triggers the UNDO bar while one is already visible, the bar immediately switches to the newest action (with a fresh 6-second timer) and the older action stays in its committed state (e.g., a previously trashed payment remains in Trash).

---

## 1. Feature 1: Undo and Trash (Database Migration & Soft Delete)

### Database Schema, Real Room Migration (`2` → `3`), and Schema Files
- **Old Version**: `2`
- **New Version**: `3`
- **Schema File Locations**:
  - `app/schemas/com.example.data.local.AppDatabase/2.json` (generated from unmodified v2 entity before adding `deletedAtMillis`)
  - `app/schemas/com.example.data.local.AppDatabase/3.json` (generated after adding `deletedAtMillis` and bumping to v3)
- **Migration SQL (`MIGRATION_2_3`)**:
  ```sql
  ALTER TABLE transactions ADD COLUMN deletedAtMillis INTEGER DEFAULT NULL
  ```
- **Builder Configuration (`SnapSpendDatabase.kt`)**:
  - Register `.addMigrations(MIGRATION_2_3)` in `AppDatabase.getInstance(context)`.
  - Remove `.fallbackToDestructiveMigration(true)` completely.

### Full Audit of Every `@Query` Touching `transactions` in `SnapSpendDao.kt`

There are **15 existing `@Query` methods** touching `transactions` in `SnapSpendDao.kt` (`14` in `TransactionDao` + `1` in `SnapSpendDao`), plus **8 new `@Query` methods** for Trash and full JSON backup (total **23 `@Query` methods** touching `transactions`):

| # | DAO Method | Status | Reason |
|---|------------|--------|--------|
| 1 | `observeAllTransactions()` | **Filtered (`deletedAtMillis IS NULL`)** | Active transactions stream for Home, Charts, Categories, Transactions, Insights |
| 2 | `getAllTransactionsOnce()` | **Filtered (`deletedAtMillis IS NULL`)** | Active snapshot for AI duplicate check, repeating-payment detection, CSV export, merge import |
| 3 | `getTransactionById(id)` | **Filtered (`deletedAtMillis IS NULL`)** | Active lookup by ID |
| 4 | `findByDuplicateKey(duplicateKey)` | **Filtered (`deletedAtMillis IS NULL`)** | Ignores trashed rows so a mistakenly deleted payment can be re-added |
| 5 | `findByReferenceNumber(refNumber)` | **Filtered (`deletedAtMillis IS NULL`)** | Ignores trashed rows during UTR/reference duplicate check |
| 6 | `observeTransactionsByDateRange(startDate, endDate)` | **Filtered (`deletedAtMillis IS NULL`)** | Active date-range stream |
| 7 | `getTransactionsByDateRange(startDate, endDate)` | **Filtered (`deletedAtMillis IS NULL`)** | Active date-range snapshot |
| 8 | `observeFilteredTransactions(...)` | **Filtered (`deletedAtMillis IS NULL`)** | Multi-filter reactive search excluding trashed rows |
| 9 | `getFilteredTransactions(...)` | **Filtered (`deletedAtMillis IS NULL`)** | Multi-filter one-shot search excluding trashed rows |
| 10 | `updatePendingTransactionToSuccess(...)` | **Filtered (`deletedAtMillis IS NULL`)** | Only upgrades active pending payments |
| 11 | `updatePendingToSuccessByReferenceNumber(...)` | **Filtered (`deletedAtMillis IS NULL`)** | Only upgrades active pending payments |
| 12 | `updatePendingToSuccessByDuplicateKey(...)` | **Filtered (`deletedAtMillis IS NULL`)** | Only upgrades active pending payments |
| 13 | `deleteTransactionById(id)` | **Intentionally Unfiltered** | Permanently deletes row by ID (used for Trash "DELETE FOREVER" and Quick Repeat UNDO) |
| 14 | `clearAllTransactions()` | **Intentionally Unfiltered** | Wipes all rows including trashed rows on "Replace Everything" backup import |
| 15 | `updateTransactionsCategoryName(oldName, newName)` | **Intentionally Unfiltered** | Updates category name across both active and trashed rows so restoring later keeps the valid renamed category |
| 16 | *(New)* `getAllTransactionsIncludingTrashOnce()` | **Intentionally Unfiltered** | Used exclusively by `exportFullBackupJson()` so JSON backups preserve trashed rows and their `deletedAtMillis` |
| 17 | *(New)* `observeTrashedTransactions()` | **Trash-Only (`deletedAtMillis IS NOT NULL`)** | Reactive stream for the Trash screen ordered by `deletedAtMillis DESC, id DESC` |
| 18 | *(New)* `getTrashedTransactionsOnce()` | **Trash-Only (`deletedAtMillis IS NOT NULL`)** | One-shot list of trashed rows |
| 19 | *(New)* `observeTrashedCount()` | **Trash-Only (`deletedAtMillis IS NOT NULL`)** | Reactive count for the Settings "TRASH" pale pink card |
| 20 | *(New)* `softDeleteTransaction(id, deletedAtMillis)` | **Soft-Delete Update** | Sets `deletedAtMillis = :deletedAtMillis WHERE id = :id` |
| 21 | *(New)* `restoreTransaction(id)` | **Trash Restore Update** | Sets `deletedAtMillis = NULL WHERE id = :id` |
| 22 | *(New)* `emptyTrash()` | **Trash-Only Hard Delete** | `DELETE FROM transactions WHERE deletedAtMillis IS NOT NULL` |
| 23 | *(New)* `purgeTrashedOlderThan(cutoffMillis)` | **Trash-Only Startup Purge** | `DELETE FROM transactions WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis < :cutoffMillis` |

### Soft-Delete, 6-Second Aqua UNDO Bar, and Superseding Behavior
- Deleting a payment calls `repository.softDeleteTransaction(id, System.currentTimeMillis())`.
- The row animates out of the list via Compose `animateItem`, and an Editorial Aqua (`#8FE3F0`) bar (`"Payment deleted · UNDO"`) appears above the bottom navigation bar for **6 seconds**.
- Tapping **UNDO** on a soft-deleted payment calls `repository.restoreTransaction(id)` (`deletedAtMillis = null`).
- **Superseding Rule**: `SnapSpendViewModel` holds a single `activeUndoBanner: StateFlow<UndoBannerState?>` keyed by an incrementing `actionToken`. Triggering a new UNDO-capable action (deleting another payment or tapping a Quick Repeat chip) replaces `activeUndoBanner` with the newest action and restarts the 6-second coroutine timer (`LaunchedEffect(undoBanner?.actionToken)`). The previous action remains in its committed state.

### Settings "TRASH" Row, Trash Screen, Startup Purge, and Backups
- **Settings**: Add a `"TRASH"` row (Pale Pink `#FFD6E0` 3D card) showing the trashed payment count.
- **Trash Screen (`TrashScreen.kt`)**:
  - Lists trashed payments (merchant, formatted paise amount, deleted date) with `"RESTORE"` and `"DELETE FOREVER"` buttons on each item.
  - Header includes `"EMPTY TRASH"` button with a confirmation dialog.
- **30-Day Startup Purge**:
  - On app start (`SnapSpendViewModel.init`), run `repository.purgeExpiredTrash(nowMillis - 30L * 24 * 60 * 60 * 1000L)`.
- **JSON vs CSV Export/Import**:
  - `exportFullBackupJson()` uses `getAllTransactionsIncludingTrashOnce()` and serializes `deletedAtMillis` (or `JSONObject.NULL`).
  - `importFromBackupJson()` reads `deletedAtMillis` if present and non-null, defaulting to `null` for old backups without the field.
  - `exportTransactionsCsv()` uses `getAllTransactionsOnce()` (`deletedAtMillis IS NULL`), excluding trashed rows.

---

## 2. Feature 2: Quick Repeats

- **Calculation (No New Table)**:
  - Computed from active transactions (`deletedAtMillis == null`):
    - Successful expenses only (`status == SUCCESS && type == EXPENSE && amountPaise > 0L`).
    - Within the last 90 days (`timestamp >= nowMillis - 90L * 24 * 60 * 60 * 1000L`).
    - Grouped by normalized merchant + exact `amountPaise`.
    - Filtered to groups with `size >= 3`.
    - Sorted by **frequency descending (`size`), then most recent `timestamp` descending**.
    - Capped at **6 chips** (e.g., `"Chai ₹20"`).
    - Entire section is hidden when the list is empty.
- **Tap & Long-Press Behavior**:
  - **Tap**: Light haptic feedback, inserts a new payment immediately (`timestamp = System.currentTimeMillis()`, same merchant, category, and `amountPaise`, `source = TransactionSource.MANUAL`), disables that specific chip for **3 seconds**, and shows the 6-second Aqua UNDO bar (`"Added Chai ₹20 · UNDO"`). Tapping UNDO calls `deleteTransactionById(insertedId)` to remove the accidental quick-add completely.
  - **Long-Press**: Opens `TransactionEditorDialog` pre-filled with the chip's merchant, category, and amount (`id = 0L`) so the user can edit the amount before saving.

---

## 3. Feature 3: Month-End Estimate

- **Placement**: Separate 3D card directly below the `"MONTHLY CASHFLOW PACE"` card on `HomeScreen.kt`.
- **Pure Math Helper (`util/MonthEndMath.kt`)**:
  - Accepts `thisMonthTransactions: List<TransactionEntity>`, `totalMonthlyBudgetPaise: Long`, `nowMillis: Long`, and `timeZone: TimeZone = TimeZone.getDefault()`.
  - Filters `thisMonthTransactions` to successful non-trashed expenses (`status == SUCCESS && type == EXPENSE && deletedAtMillis == null && amountPaise > 0L`).
  - Uses `Calendar.getInstance(timeZone)` with `timeInMillis = nowMillis`:
    - `dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)` (`1..daysInMonth`)
    - `daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)` (`28`, `29`, `30`, or `31`)
    - `daysLeftIncludingToday = daysInMonth - dayOfMonth + 1`
  - **Line a) Month-End Pace**:
    - Shown only when `successfulExpenseCount >= 5 && dayOfMonth >= 5`:
      - `projectedMonthEndPaise = (spentSoFarPaise * daysInMonth.toLong()) / dayOfMonth.toLong()`
      - Text: `"At this pace you'll spend ₹X by month end"`
    - Otherwise:
      - Text: `"Add a few more payments to see an estimate"`
  - **Line b) Safe to Spend Per Day**:
    - If `totalMonthlyBudgetPaise <= 0L`:
      - Text: `"Set a budget to see this"` (tapping opens budget setup dialog).
    - Else if `totalMonthlyBudgetPaise - spentSoFarPaise < 0L`:
      - `overBudgetPaise = spentSoFarPaise - totalMonthlyBudgetPaise`
      - Text in Editorial Coral (`#FF6F59`): `"Over budget by ₹X"`
    - Else:
      - `safePerDayPaise = (totalMonthlyBudgetPaise - spentSoFarPaise) / daysLeftIncludingToday.toLong()`
      - Text: `"SAFE TO SPEND PER DAY: ₹Y"`
- **Info `(i)` Button**:
  - Opens a card/dialog explaining both formulas in plain words.
- **Unit Tests (`MonthEndEstimateTest.kt`)**:
  - Tests day 1, mid-month day, last day of month, leap February (29 days), no budget, over budget, and fewer than 5 payments (as well as `< 5` days passed).
