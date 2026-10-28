# Smart Cash Mode & Cash Wallet — Implementation Plan

## 1. Goal & Scope
Upgrade SnapSpend with **Smart Cash Mode & Cash Wallet**, ensuring zero double-counting of ATM withdrawals, sub-20ms on-device multi-item Quick Cash logging with instant save + Undo snackbar, persistent Cash Wallet balance tracking in Jetpack Preferences DataStore, and tactile Neo-Brutalist 3D UI controls across Home, Transactions, and the AI Review Deck.

---

## 2. Room Database Migration (`v3 -> v4`) & Models

### `SnapSpendEntities.kt` & `LocalInsightsAndRulesEngine.kt`
- **`TransactionEntity`**:
  - Keep `val paymentMode: String = "ONLINE"` (`"ONLINE"` or `"CASH"`).
  - Add `val isAtmWithdrawal: Boolean = false`.
- **`DraftTransaction`**:
  - Keep `val paymentMode: String = "ONLINE"`.
  - Add `val isAtmWithdrawal: Boolean = false`.

### `SnapSpendDatabase.kt`
- Update `MIGRATION_3_4` so migrating from version 3 to version 4 executes both non-destructive `ALTER TABLE` statements:
  ```sql
  ALTER TABLE transactions ADD COLUMN paymentMode TEXT NOT NULL DEFAULT 'ONLINE'
  ALTER TABLE transactions ADD COLUMN isAtmWithdrawal INTEGER NOT NULL DEFAULT 0
  ```
- Also add a safe column-check helper / `MIGRATION_4_5` compatibility path if an existing local v4 database was created before `isAtmWithdrawal` was added, while keeping `MIGRATION_3_4` exposed and tested directly.
- Update `SnapSpendRepository.kt` (`saveTransaction`, `commitConfirmedDrafts`, JSON backup import/export, and CSV import/export) to persist and restore `paymentMode` and `isAtmWithdrawal`.

---

## 3. Prevent ATM Double-Counting & Persistent Cash Wallet (`DataStore` + Rules Engine)

### `SnapSpendPreferences.kt`
- Add `KEY_CASH_WALLET_BALANCE_PAISE = longPreferencesKey("cash_wallet_balance_paise")` to `SnapSpendPreferences`.
- Expose `cashWalletBalancePaiseFlow: Flow<Long>` and `suspend fun setCashWalletBalancePaise(balancePaise: Long)` / `suspend fun adjustCashWalletBalancePaise(deltaPaise: Long)` using exact `Long` paise arithmetic.

### ATM Detection & Double-Counting Prevention (`LocalInsightsAndRulesEngine.kt`, `UpiRegexPreProcessor.kt`, `GeminiReceiptExtractor.kt`, `TransactionDao.kt`)
- Detect ATM withdrawals when scanning PDF bank statements, SMS/screenshots, or manual entries containing keywords (`"ATM"`, `"ATM WDL"`, `"CASH WDL"`, `"CASH WITHDRAWAL"`, `"ATW"`, or manual ATM toggle):
  - Set `isAtmWithdrawal = true` and `paymentMode = "CASH"`.
  - Exclude any transaction where `isAtmWithdrawal == true` (as well as `type == TransactionType.TRANSFER`) from monthly `"Spent"` totals, category pie slices, budget usage, and SQL Data Check `Money Out` queries so cash withdrawn from an ATM is never double-counted when spent later.
- **Automatic Cash Wallet Balance Updates**:
  - When an ATM withdrawal (`isAtmWithdrawal == true`) or cash top-up is confirmed/saved, automatically add the withdrawn `amountPaise` to `cashWalletBalancePaise` in DataStore.
  - When any transaction with `paymentMode == "CASH"` and `type == TransactionType.EXPENSE` (and `!isAtmWithdrawal`) is confirmed/saved, automatically deduct `amountPaise` from `cashWalletBalancePaise`.
  - When a `CASH` transaction is soft-deleted, restored, or undone, reverse the corresponding delta on `cashWalletBalancePaise` so the wallet stays in sync.
  - Allow users to manually set or top up their current `"Cash in Hand"` balance directly from the Home screen Cash Wallet pill modal.

---

## 4. Natural-Language Quick Cash Logging (`<20ms` 0-API Regex + Gemini 3.8-Flash Fallback)

### `QuickCashFastParser.kt` (New Dedicated Helper in `com.example.data.ai`)
- **0-API Local Fast Parser (`<20ms`)**:
  - Splits casual multi-item inputs on commas, semicolons, newlines, or `" and "` / `" & "` (e.g., `"20 chai, 50 auto, 140 maggi"` or `"chai 20, auto 50"`).
  - Matches simple `<Amount> <Item>` and `<Item> <Amount>` patterns (e.g., `^\s*(?:₹|Rs\.?\s*)?(\d+(?:\.\d{1,2})?)\s+([a-zA-Z][a-zA-Z0-9\s&'.\-]*)\s*$` and reverse).
  - Converts amounts into exact `Long` paise via `MoneyUtils.parseRupeesToPaise`.
  - Matches each item merchant against learned `MerchantMemoryEntity` rules first, then built-in Indian keyword heuristics (`chai`/`coffee`/`maggi`/`momos`/`samosa` -> `Food & Dining`, `auto`/`cab`/`bus`/`metro`/`petrol` -> `Transport & Fuel`, `milk`/`bread`/`veggies` -> `Groceries`, etc.).
- **Instant Save + Quick Undo Snackbar**:
  - When the user submits a Quick Cash string in the Quick Cash bottom sheet/modal:
    - If `QuickCashFastParser` matches all segments locally (`<20ms`, 0 network calls), immediately insert the `CASH` transactions into Room, deduct their total paise from `cashWalletBalancePaise`, dismiss the sheet, and trigger the 6-second Neo-Brutalist Undo bar (`"Logged 3 cash spends (₹210) · UNDO"`).
    - Tapping `UNDO` deletes the batch of inserted transaction IDs and restores the deducted paise to `cashWalletBalancePaise`.
- **Gemini 3.8-Flash Text-Only Fallback**:
  - If the input is conversational or complex (does not match the fast local pattern), run a lightweight text-only call to `gemini-3.8-flash` (`thinkingBudget = 0`, `temperature = 0.1f`) returning a JSON list of `DraftTransaction` items with `paymentMode = "CASH"`, and commit them to the Cash Wallet with the same Undo snackbar (or surface in the review deck if low confidence).

---

## 5. Neo-Brutalist 3D UI Updates

### Home Screen (`HomeScreen.kt` & `MainActivity.kt`)
- **Compact Neo-Brutalist `"Cash in Hand: ₹X"` Pill Card & `"Quick Cash"` Action**:
  - Position a tactile Neo-Brutalist 3D Cash Wallet card near the main balance header (`2dp` solid black border, `3dp` hard black offset shadow, Sunny Yellow `#FFD23F` / Mint `#9FE3C0` accents).
  - Displays `"CASH IN HAND: ₹X"` with tabular numerals and a `"⚡ QUICK CASH"` pill button.
  - Tapping the pill or button opens the **Quick Cash & Wallet Sheet** where the user can:
    1. Type or voice-dictate multi-item quick cash strings (`"20 chai, 50 auto, 140 maggi"`) with `+₹10`, `+₹20`, `+₹50`, `+₹100`, `+₹500` quick chips for instant logging.
    2. Adjust or top up their persistent `"Cash in Hand"` wallet balance directly in Rupees.

### Transaction Cards, AI Review Deck & Manual Editor (`EditorialComponents.kt`, `ReviewAndAddSheet.kt`)
- **Transaction Rows (`EditorialTransactionCard`)**:
  - Show a clean neo-brutalist badge on every transaction row: `"CASH"` (Sunny Yellow `#FFD23F` pill) vs `"UPI / ONLINE"` (Aqua `#8FE3F0` / White pill), plus an `"ATM WDL"` badge when `isAtmWithdrawal == true`.
- **AI Review Deck Overlay (`AiReviewDeckOverlay`) & Manual Editor (`TransactionEditorDialog`)**:
  - Provide a one-tap `"ONLINE"` vs `"CASH"` segmented toggle and an `"ATM Withdrawal (Add to Cash Wallet)"` toggle chip so any scanned or manual entry can be flipped with one tap.

---

## 6. Verification & Unit Testing
- Add unit tests in `HybridLocalScanningTest.kt` verifying:
  - `QuickCashFastParser` parses `"20 chai, 50 auto, 140 maggi"` in `<20ms` into 3 `CASH` transactions with exact `Long` paise (`2000L`, `5000L`, `14000L`) and expected categories.
  - ATM withdrawal detection (`"ATM WDL"`, `"CASH WDL"`, `"ATW"`) flags `isAtmWithdrawal = true` and excludes the withdrawal from monthly `"Spent"` totals while adding to Cash in Hand.
- Run `compile_applet` and `gradle :app:testDebugUnitTest` to verify `MIGRATION_3_4` and all tests compile and pass cleanly.
