# Implementation Plan: Smart Cash Mode & Cash Wallet

## 1. Executive Summary
This plan adds **Smart Cash Mode & Cash Wallet** to SnapSpend while preserving the neo-brutalist 3D editorial design (colored cards, 2dp black outlines, offset shadows, spring timings, tactile press feedback, and Light/Dark themes), integer paise storage (`Long`), and existing AI scanning and math pipelines:

1. **Database & Migration (`v3 -> v4`)**:
   - Adds `paymentMode: String = "ONLINE"` (`"ONLINE"` or `"CASH"`) to each transaction record.
   - Implements a real Room migration (`MIGRATION_3_4`) executing `ALTER TABLE transactions ADD COLUMN paymentMode TEXT NOT NULL DEFAULT 'ONLINE'`, generates and commits the version 4 Room schema, and verifies `v3 -> v4` migration in the migration test suite without destructive migration.
   - Updates JSON backup export/import and CSV export/import to include `paymentMode` (defaulting to `"ONLINE"` when importing older backups that lack the field).
   - Updates the **Data Check** screen's **COUNTS** section and **LAST 10 SAVED ROWS** section (plus its copied plain-text report) to show active **Online** vs **Cash** counts via plain SQL and display `paymentMode` on each stored row.
2. **One-Line "Smart Cash Log" (AI Auto-Sort) & Online/Cash Toggles**:
   - Adds a prominent **LOG CASH PAYMENT** card inside the **+ ADD** Quick Scan & Add sheet.
   - Opens a chunky 3D **Smart Cash Log** sheet featuring:
     - A single text input with system speech recognizer mic button (no audio recording permission required).
     - Quick amount buttons (`+₹10`, `+₹20`, `+₹50`, `+₹100`, `+₹500`) that insert or add to the numeric amount in the note text.
     - A 3-way intent toggle for **Spent Cash (Expense)**, **Received Cash (Earning)**, and **ATM Withdrawal / Wallet Top-up (Transfer)**.
   - Tapping **SORT WITH AI** checks the one-time *"Before your first scan"* disclosure first, sends only the short text note and active categories to the Gemini extraction pipeline, splits multi-item notes (e.g., `"auto 60, samosa 30"`) into separate drafts pre-tagged with `paymentMode = "CASH"` (and default type from the selected toggle unless overridden by explicit note semantics), applies learned merchant rules, and opens the standard **AI Review Deck Overlay**.
   - Adds an **Online | Cash** segmented pill inside both the manual **Add/Edit Payment** dialog and each **AI Review Card** so any transaction or draft can be flipped between Online and Cash at any time.
   - Preserves `paymentMode` (`"ONLINE"` vs `"CASH"`) on **Quick Repeat** chips on Home.
3. **Dedicated Cash View & `[CASH]` Badges**:
   - Displays a chunky Sunny Yellow `[CASH]` pill badge (black border, black text) next to the category name on every cash transaction row across **Home Recent Activity**, **Transactions**, **Categories**, and **Trash**.
   - Adds a 3-way segmented filter pill (`All | Online | Cash`) at the top of both the **Transactions** screen and the **Spending (Charts)** screen:
     - When `Cash` is selected on **Transactions**, displays a Sunny Yellow 3D **Cash Wallet Summary Card** at the top showing **CASH SPENT THIS MONTH** and **CASH IN HAND**, along with a **+ Top Up / ATM Withdrawal** button that opens the full Add Payment editor pre-set to `Transfer` and `Cash`, and filters the list to `paymentMode == "CASH"`.
     - When `Cash` (or `Online`) is selected on **Spending (Charts)**, recalculates the area/line chart, pill bar chart, category donut, period comparisons, and Top 5 places locally for that payment mode.
4. **"Cash in Hand / Wallet" Tracker & ATM Double-Count Protection**:
   - Computes `cashInHandPaise` locally in integer paise from non-trashed `SUCCESS` cash transactions:
     `cashInHandPaise = Cash Transfers (ATM Withdrawals / Top-ups) + Cash Earnings − Cash Expenses`.
   - Automatically classifies bank statement PDF/screenshot/SMS drafts containing ATM withdrawal markers (`"ATM WDL"`, `"CASH WITHDRAWAL"`, `"ATW"`, `"ATM CASH"`) as `type = TRANSFER` and `paymentMode = "CASH"` so confirming an ATM withdrawal tops up the Cash Wallet without double-counting it as a monthly expense.

---

## 2. System Architecture & Data Flow

```text
+-----------------------------------------------------------------------------------+
|                    SMART CASH MODE & CASH WALLET ARCHITECTURE                     |
+-----------------------------------------------------------------------------------+

  [+ ADD Sheet] ---> "LOG CASH PAYMENT" Card
                           |
                           v
             +---------------------------------------------------+
             | Smart Cash Log 3D Sheet                           |
             | - Text / Voice note ("40 momos, 60 auto")         |
             | - Quick Amount Chips (+₹10 +₹20 +₹50 +₹100 +₹500) |
             |   (insert or add to number in note text)          |
             | - Mode Toggle: Spent | Received | ATM Top-up      |
             +---------------------------------------------------+
                           |
                           v  (Gated by "Before your first scan" notice)
             +---------------------------------------------------+
             | Gemini Extraction + Local Rules Engine            |
             | - Splits multi-item notes into separate drafts    |
             | - Tags drafts with paymentMode = "CASH"           |
             | - Applies LearnedMerchantRule category overrides  |
             | - Auto-detects ATM WDL / CASH WITHDRAWAL / ATW    |
             |   from statements/SMS -> TRANSFER + CASH          |
             +---------------------------------------------------+
                           |
                           v
             +---------------------------------------------------+
             | AI Review Deck Overlay & Manual Editor            |
             | - "Online | Cash" segmented pill on every card    |
             | - Nothing saved until Confirm                     |
             +---------------------------------------------------+
                           |
                           v
             +---------------------------------------------------+
             | Room SQLite Database (v3 -> v4 Migration)         |
             | - transactions.paymentMode ('ONLINE' | 'CASH')    |
             | - Preserved in JSON/CSV Backup & Quick Repeats    |
             +---------------------------------------------------+
                    /                  |                   \
                   v                   v                    v
     +-------------------+   +--------------------+   +--------------------+
     | Transactions Tab  |   | Spending Charts    |   | Data Check Screen  |
     | - All|Online|Cash |   | - All|Online|Cash  |   | - SQL Online &     |
     | - Cash Ledger 3D  |   | - Recalculates     |   |   Cash counts      |
     |   Summary Card    |   |   charts & top 5   |   | - paymentMode in   |
     | - + Top Up button |   |   for selected     |   |   Last 10 rows &   |
     | - [CASH] badges   |   |   payment mode     |   |   Copied Report    |
     +-------------------+   +--------------------+   +--------------------+
```

---

## 3. Detailed Feature Specifications

### 3.1 Database & Migration (`v3 -> v4`), Backups & Data Check
- **Schema Update**:
  - Add `val paymentMode: String = "ONLINE"` to `TransactionEntity` (normalized to `"ONLINE"` or `"CASH"`).
  - Add `MIGRATION_3_4` to the Room database executing:
    `ALTER TABLE transactions ADD COLUMN paymentMode TEXT NOT NULL DEFAULT 'ONLINE'`
  - Increment the Room database version from `3` to `4` with `exportSchema = true` so KSP generates the `4.json` schema file alongside `2.json` and `3.json`.
  - Add a migration test verifying `2 -> 3` and `3 -> 4` (and `2 -> 4`), confirming that rows inserted in version 3 survive migration to version 4 with all columns intact and `paymentMode = "ONLINE"`.
- **JSON & CSV Backup / Export / Import**:
  - Include `paymentMode` in JSON export and parse `optString("paymentMode", "ONLINE")` on JSON import so older backups without `paymentMode` default cleanly to `"ONLINE"`.
  - Include `paymentMode` in CSV export and support reading `paymentMode` on CSV import (defaulting to `"ONLINE"` when importing older 10-column CSV files).
- **Data Check Screen Updates**:
  - Add two plain SQL queries to `TransactionDao`:
    - `getDataCheckOnlineActiveCount()`: `SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NULL AND paymentMode = 'ONLINE'`
    - `getDataCheckCashActiveCount()`: `SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NULL AND paymentMode = 'CASH'`
  - Display **Online** and **Cash** active transaction counts in the **COUNTS** card, show `paymentMode` in each card of **LAST 10 SAVED ROWS**, and include both in the **COPY REPORT** plain-text output.

### 3.2 One-Line "Smart Cash Log" & Online/Cash Toggle
- **Quick Add Sheet Entry**:
  - Add a prominent Mint (`EditorialMint`) 3D card in the **+ ADD** sheet titled **LOG CASH PAYMENT** with subtitle *"Type or say '40 momos, 60 auto' — AI sorts merchant & category."*
- **Smart Cash Log 3D Modal / Sheet**:
  - **Single Text Input + Voice Mic Button**: Uses `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` (zero audio permission needed) or keyboard input for notes like `"40 momos near station"` or `"auto 60, samosa 30"`.
  - **Quick Amount Chips (`+₹10`, `+₹20`, `+₹50`, `+₹100`, `+₹500`)**:
    - Tapping a chip inspects the current note text: if the note already has a trailing or standalone number token (e.g., `"20"` or `"momos 20"`), it adds the chip value to that number (`"40"` or `"momos 40"`); otherwise it inserts the amount into the note text so the user can immediately type the item name.
  - **3-Way Cash Intent Toggle**:
    - `Spent Cash (Expense)` (`EXPENSE`)
    - `Received Cash (Earning)` (`EARNING`)
    - `ATM Withdrawal / Wallet Top-up (Transfer)` (`TRANSFER`)
  - **SORT WITH AI Button**:
    - Checks the one-time *"Before your first scan"* disclosure first if not yet seen.
    - Calls the natural-language Gemini extraction pipeline with the cash mode context so multi-item notes (e.g., `"auto 60, samosa 30"`) are split into separate `DraftTransaction` items pre-tagged with `paymentMode = "CASH"`, `paymentApp = "Cash"`, and the chosen default transaction type (while honoring explicit cues like `"got 500 cash from friend"` -> `EARNING` or `"ATM withdrawal 2000"` -> `TRANSFER`).
    - Applies local `MerchantMemoryEntity` category overrides and opens `AiReviewDeckOverlay`.
- **Online | Cash Segmented Pill in Manual Editor & AI Review Cards**:
  - In `TransactionEditorDialog` (Add/Edit Payment by Hand): add an **Online | Cash** segmented pill toggle. Selecting `Cash` sets `paymentMode = "CASH"` (and defaults `paymentApp` to `"Cash"` if blank or previously set to an online app).
  - In `AiReviewDeckOverlay`: add an **Online | Cash** segmented pill on each review draft card so the user can flip any scanned or note-generated draft between `"ONLINE"` and `"CASH"` before confirming.

### 3.3 Dedicated Cash View, `[CASH]` Badges & Filtering
- **`[CASH]` Pill Badge**:
  - Whenever `transaction.paymentMode == "CASH"`, render a compact chunky `[CASH]` badge (`SunnyYellow` `#FFD23F` background, `1.4.dp` `InkBlack` border, bold `InkBlack` text) next to the category label on transaction rows in:
    - **Home** (Recent Activity rows)
    - **Transactions** (Transaction Explorer / Cash Ledger rows)
    - **Categories** (Category detail transaction rows)
    - **Trash** (Trashed payment cards)
- **3-Way Segmented Filter (`All | Online | Cash`) on Transactions & Spending (Charts)**:
  - **Transactions Screen**:
    - Adds `All | Online | Cash` segmented toggle at the top of the screen.
    - When `Cash` is selected, displays the **Cash Wallet 3D Summary Card** (`SunnyYellow` `#FFD23F` surface, 2dp black border, 3D offset shadow) showing:
      - **CASH SPENT THIS MONTH: ₹X**
      - **CASH IN HAND: ₹Y**
      - **+ Top Up / ATM Withdrawal** button, which opens the full `TransactionEditorDialog` pre-filled with `type = TransactionType.TRANSFER`, `paymentMode = "CASH"`, `merchant = "ATM Withdrawal"`, `category = "Transfers"`, and `paymentApp = "Cash"`.
  - **Spending (Charts) Screen**:
    - Adds `All | Online | Cash` segmented toggle at the top of the Charts screen.
    - Recalculates the Pill Bar Chart, Area/Line Trend Chart, Category Donut & breakdown list, Period Comparisons, and Top 5 Spending Places using only transactions matching the selected mode (`All`, `Online`, or `Cash`).

### 3.4 "Cash in Hand / Wallet" Math & ATM Double-Count Protection
- **Pure Local Paise Math for Cash in Hand**:
  - Computed in `LocalInsightsAndRulesEngine` from all non-trashed `SUCCESS` transactions where `paymentMode == "CASH"`:
    - `cashTransfersInPaise` = sum of `amountPaise` where `type == TRANSFER && paymentMode == "CASH"`
    - `cashEarningsInPaise` = sum of `amountPaise` where `type == EARNING && paymentMode == "CASH"`
    - `cashExpensesOutPaise` = sum of `amountPaise` where `type == EXPENSE && paymentMode == "CASH"`
    - `cashInHandPaise = cashTransfersInPaise + cashEarningsInPaise - cashExpensesOutPaise`
  - Also computes `thisMonthCashSpentPaise` = sum of `amountPaise` for non-trashed `SUCCESS` transactions in the current local calendar month where `type == EXPENSE && paymentMode == "CASH"`.
- **Automatic ATM Withdrawal Classification (Double-Count Protection)**:
  - In `LocalInsightsAndRulesEngine.enrichAndResolveDraft` (and AI prompt instructions), if a scanned bank statement PDF, screenshot, or SMS contains ATM withdrawal keywords (`"ATM WDL"`, `"ATM-WDL"`, `"CASH WITHDRAWAL"`, `"ATM CASH"`, `"ATW/"`, `"ATW "`, `"SELF WITHDRAWAL"`), automatically set `type = TransactionType.TRANSFER`, `category = "Transfers"`, and `paymentMode = "CASH"`.
  - Confirming that draft tops up `cashInHandPaise` without inflating monthly expenses.

---

## 4. Verification & Testing Plan
- **Room Migration Test (`v3 -> v4`)**:
  - Add a test in `DatabaseMigrationTest.kt` using `MigrationTestHelper` (and SQLite verification against the committed `3.json` and `4.json` schemas) that creates a version 3 database, inserts real transaction rows, runs `MIGRATION_3_4` to version 4, and verifies all existing column values are unchanged and `paymentMode == "ONLINE"`.
- **Smart Cash Mode, Wallet Math & ATM Protection Unit Tests**:
  - Verify `cashInHandPaise` formula across ATM top-ups (`TRANSFER` + `CASH`), cash earnings (`EARNING` + `CASH`), cash expenses (`EXPENSE` + `CASH`), and verify trashed/pending/failed/online rows are ignored.
  - Verify ATM withdrawal drafts (`"ATM WDL"`, `"CASH WITHDRAWAL"`, `"ATW"`) auto-classify as `type = TRANSFER` and `paymentMode = "CASH"` and do not count toward monthly expenses.
  - Verify JSON and CSV export/import round-trip `paymentMode` and default legacy backups without `paymentMode` to `"ONLINE"`.
  - Verify Data Check SQL counts for Online and Cash active rows.
