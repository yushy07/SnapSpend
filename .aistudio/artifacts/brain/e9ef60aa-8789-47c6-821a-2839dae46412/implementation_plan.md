# Part A Implementation Plan: Share to SnapSpend & Data Check Screen

## 1. Executive Summary
This plan adds two features to SnapSpend while preserving the existing neo-brutalist editorial look (colored 3D cards, crisp black outlines, offset shadows, spring timings, tactile press feedback, and Light/Dark themes), integer paise storage, and existing AI scanning, calculation, and database logic:

1. **Share to SnapSpend**: Lets users share single or multiple images (`image/*`), PDFs (`application/pdf`), and plain text (`text/plain`, such as pasted bank SMS alerts) directly into SnapSpend from any app—whether SnapSpend is cold-launched or already open (`singleTop` / `onNewIntent`). Shared items are validated and read immediately into temporary cache memory, gated behind the onboarding tutorial and the one-time *"Before your first scan"* disclosure, routed through the exact same Gemini scan pipeline, and presented in the review overlay on top of whatever screen is open. Temporary cache files are deleted automatically as soon as the scan completes, fails, is cancelled, or is superseded by a new incoming share via `onNewIntent`.
2. **Data Check Screen**: Adds a pale lavender **DATA CHECK** card in Settings that opens a read-only diagnostic screen powered by its own independent plain SQL `SUM` and `COUNT` queries (never reusing Home, Charts, or Insights calculation functions). It displays transaction counts (including active vs. trashed and mutually exclusive status/transfer breakdowns), a 12-month table of Money In, Money Out, and payment counts to the exact paise (`₹1,234.50`), the last 10 saved rows as stored in SQLite, a live **MATCH / MISMATCH** comparison against Home's monthly spend using identical millisecond month bounds in the local time zone, and a **COPY REPORT** button that copies the full plain-text report to the clipboard.

---

## 2. System Architecture & Data Flow

```text
+-----------------------------------------------------------------------------------+
|                        1. SHARE TO SNAPSPEND PIPELINE                             |
+-----------------------------------------------------------------------------------+
  Android Share Sheet (ACTION_SEND / ACTION_SEND_MULTIPLE)
  [image/* | application/pdf | text/plain]
         |
         v  (Cold start via onCreate OR warm start via singleTop + onNewIntent)
  +-------------------------------------------------------------------------+
  | Immediate Share Ingestor (Untrusted Input Guard + Re-Share Cleanup)     |
  | - On new share arrival (onNewIntent): immediately delete any remaining  |
  |   temporary cache files from the previous unfinished/abandoned share    |
  | - If file streams exist: process streams & ignore companion caption     |
  | - Enforce max 10 items (notify user if extra items were skipped)        |
  | - Validate MIME type (image/*, application/pdf, text/plain)             |
  | - Enforce max 20 MB per file (metadata check + hard stream byte cap)    |
  | - Copy stream immediately into ephemeral share_cache/ (or read text)    |
  | - Catch SecurityException / IOException / malformed URIs gracefully     |
  +-------------------------------------------------------------------------+
         |
         v  (Held in memory as PendingSharePayload)
  +-------------------------------------------------------------------------+
  | Onboarding & First-Scan Notice Gate                                     |
  | 1. Has user finished Onboarding Carousel?                               |
  |    -> No: Wait until onboarding finishes, keeping payload in memory     |
  | 2. Has user confirmed "Before your first scan" notice?                  |
  |    -> No: Show notice first; if dismissed, delete temp cache files      |
  +-------------------------------------------------------------------------+
         |
         v  (Confirmed)
  +-------------------------------------------------------------------------+
  | Existing Scan Pipeline (No Second Scanner)                              |
  | - Images / PDFs -> existing URI extraction pipeline                     |
  | - Plain text    -> existing natural-language note extraction pipeline   |
  | - Cleanup hook deletes ephemeral share_cache/ files on Finish / Fail /  |
  |   Cancel / Dismiss / Superseded by new onNewIntent share                |
  +-------------------------------------------------------------------------+
         |
         v
  +-------------------------------------------------------------------------+
  | Top-Level AI Review Deck Overlay                                        |
  | - Rendered above all tabs and sub-screens (Home, Trash, Data Check)     |
  | - Nothing is saved to the database until the user taps Confirm          |
  +-------------------------------------------------------------------------+


+-----------------------------------------------------------------------------------+
|                     2. DATA CHECK INDEPENDENT SQL PIPELINE                        |
+-----------------------------------------------------------------------------------+
  SQLite Database (`transactions` table)
         |
         +---> Plain SQL Queries (Independent of Home/Charts/Insights Kotlin logic)
                 |
                 +--> (a) COUNTS:
                 |        - Total Active (`deletedAtMillis IS NULL`)
                 |        - Success (`status = 'SUCCESS' AND type != 'TRANSFER'`)
                 |        - Pending (`status = 'PENDING' AND type != 'TRANSFER'`)
                 |        - Failed  (`status = 'FAILED'  AND type != 'TRANSFER'`)
                 |        - Transfer (`type = 'TRANSFER'`)
                 |        - Trashed (`deletedAtMillis IS NOT NULL`)
                 |
                 +--> (b) MONTH BY MONTH (Last 12 Months):
                 |        - Local-timezone calendar bounds:
                 |          1st of month at 00:00:00.000 to last day at 23:59:59.999
                 |        - Per-month SQL SUM(EARNING), SUM(EXPENSE), COUNT(*)
                 |        - Strictly `status = 'SUCCESS' AND type != 'TRANSFER'`
                 |        - Formatted to exact paise (`₹1,234.50`)
                 |
                 +--> (c) LAST 10 SAVED ROWS:
                 |        - `WHERE deletedAtMillis IS NULL ORDER BY id DESC LIMIT 10`
                 |        - Shows raw id, date/time, amountPaise, type, status,
                 |          merchant, category, source, referenceNumber, duplicateKey
                 |
                 +--> (d) COMPARE WITH HOME:
                          - SQL `SUM(amountPaise)` for current month (`EXPENSE`, `SUCCESS`)
                            using exact same [00:00:00.000, 23:59:59.999] local-timezone
                            month bounds as Home
                          - Compared with Home's `thisMonthSpentPaise`
                          - Mint `MATCH` pill if equal; Coral `MISMATCH` pill with both
                            numbers if different
```

---

## 3. Feature 1: Share to SnapSpend

### 3.1 Share Menu Registration & SingleTop Lifecycle
- Register `ACTION_SEND` and `ACTION_SEND_MULTIPLE` intent filters on the main activity for:
  - `image/*`
  - `application/pdf`
  - `text/plain`
- Keep the existing `MAIN` / `LAUNCHER` intent filter untouched and preserve `launchMode="singleTop"` so sharing while SnapSpend is already running delivers the intent to `onNewIntent` without creating a second activity instance.

### 3.2 Immediate Ingestion, Limits, Untrusted Input Safety & Re-Share Cleanup
Because temporary URI read permissions from `ACTION_SEND` / `ACTION_SEND_MULTIPLE` expire when the activity finishes handling the intent—or before the user finishes onboarding or the first-scan disclosure—shared streams are ingested immediately upon receipt:
- **Files Take Precedence Over Companion Captions**:
  - When an intent provides one or more stream URIs (`EXTRA_STREAM` or non-text `ClipData` URIs), SnapSpend processes the attached files and ignores any companion `EXTRA_TEXT` caption.
  - When no file streams are attached and the share is `text/plain` (either via `EXTRA_TEXT`, `ClipData` text items, or a `text/plain` stream URI), SnapSpend reads the plain text content (up to 10 items if multiple text items are shared).
- **Item Count Cap (Max 10 Items)**:
  - If more than 10 items are shared at once, only the first 10 items are processed and a friendly message informs the user how many extra items were skipped.
- **File Type Validation**:
  - Inspect each URI's MIME type via `ContentResolver.getType(uri)` (with fallback to file extension / header inspection when MIME is generic `*/*`).
  - Accept only `image/*`, `application/pdf`, and `text/plain`. Reject any other file type with a friendly message.
- **File Size Cap (Max 20 MB per File)**:
  - Query `OpenableColumns.SIZE` first and also enforce a strict `20 * 1024 * 1024` byte limit while copying the input stream so files that report an unknown size cannot exceed 20 MB.
  - Any file over 20 MB is skipped immediately (its partial temp file deleted) with a friendly message telling the user which file was skipped for exceeding 20 MB.
- **Ephemeral Cache Lifecycle & Re-Share (`onNewIntent`) Cleanup**:
  - Valid shared image/PDF streams are copied into a dedicated temporary cache folder inside the app's cache directory. They are **never** copied to permanent storage.
  - All URI queries and stream reads are wrapped in `try / catch (SecurityException | IOException | Exception)` blocks so revoked permissions, corrupt streams, or untrusted data never crash the app.
  - **Re-sharing while already in SnapSpend (`onNewIntent` cleanup)**: The share manager tracks the active/pending batch of temporary cache files. If the user shares a screenshot, does not finish the review (or leaves the app mid-notice/mid-review), and returns to another app (such as GPay) to share a different screenshot, the incoming `onNewIntent` handler immediately cancels any superseded share state and deletes all temporary cache files from the previous share before ingesting the new share, ensuring temporary files never pile up until the next app restart.
  - Temporary cache files are deleted deterministically whenever:
    1. A new share intent arrives via `onNewIntent` while a previous share's temp files or review state are still active,
    2. The scan finishes and produces review drafts (since drafts are held in memory),
    3. The scan fails and is dismissed or cancelled (if the user retries or queues a failed shared scan, the temp file is retained only until that retry/queued scan finishes, is dismissed, or is replaced by a new share),
    4. The user dismisses the *"Before your first scan"* disclosure dialog without proceeding, and
    5. Any stale share cache files exist on app startup.

### 3.3 Onboarding & First-Scan Notice Gating
- **Pending Share State in Memory**:
  - Ingested share payloads (`PendingSharePayload`: either validated temp cache `Uri`s or shared plain-text string(s), plus any warning message about skipped oversized/extra items) are held in memory.
  - If `hasSeenOnboarding == false`, the user completes or skips the 5-slide onboarding carousel first. As soon as onboarding finishes, SnapSpend automatically resumes processing the held share payload.
  - Next, if `hasSeenAiScanNotice == false`, SnapSpend shows the existing one-time *"Before your first scan"* dialog. Confirming the dialog marks the notice as seen and starts the scan; dismissing it cancels the share and deletes any temporary cache files.
- **Same Scan Pipeline & Top-Level Review Sheet**:
  - Shared images/PDFs call the existing `processSelectedUris` flow; shared plain text calls the existing natural-language note extraction flow (combining multiple text snippets if shared together).
  - The existing review overlay is rendered at the root overlay layer above all tabs and sub-screens (including Trash and Data Check), and nothing is written to the database until the user taps **Confirm**.

---

## 4. Feature 2: Data Check Screen

### 4.1 Settings Entry Card
- In **Settings**, right beside the **TRASH** card, add a pale lavender (`PaleLavenderTint`) 3D card row titled **DATA CHECK** with crisp black borders, offset 3D shadow, a subtitle (*"Verify counts, monthly sums & raw database rows"*), and a round arrow button.

### 4.2 Independent Plain SQL Queries & Same-Millisecond Month Boundary Alignment
To ensure Data Check can catch bugs in the app's Kotlin calculation helpers, Data Check uses its own dedicated `@Query` methods executing raw SQLite `COUNT` and `SUM` aggregations:
1. **Counts Queries**:
   - `getDataCheckTotalActiveCount()`: `SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NULL`
   - `getDataCheckSuccessCount()`: `SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NULL AND status = 'SUCCESS' AND type != 'TRANSFER'`
   - `getDataCheckPendingCount()`: `SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NULL AND status = 'PENDING' AND type != 'TRANSFER'`
   - `getDataCheckFailedCount()`: `SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NULL AND status = 'FAILED' AND type != 'TRANSFER'`
   - `getDataCheckTransferCount()`: `SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NULL AND type = 'TRANSFER'`
   - `getDataCheckTrashedCount()`: `SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NOT NULL`
2. **Same-Millisecond Calendar Month Bounds (`[monthStartMillis, monthEndMillis]`)**:
   - Data Check computes `[monthStartMillis, monthEndMillis]` in the phone's local time zone using the exact same calendar boundary definition as the Home screen:
     - **Start of month (`monthStartMillis`)**: 1st day of the month at `00:00:00.000` (`HOUR_OF_DAY = 0`, `MINUTE = 0`, `SECOND = 0`, `MILLISECOND = 0`).
     - **End of month (`monthEndMillis`)**: Last day of the month (`getActualMaximum(DAY_OF_MONTH)`) at `23:59:59.999` (`HOUR_OF_DAY = 23`, `MINUTE = 59`, `SECOND = 59`, `MILLISECOND = 999`).
   - Because SQLite `BETWEEN :startMillis AND :endMillis` is inclusive on both endpoints (`timestamp >= :startMillis AND timestamp <= :endMillis`), just like Home's `timestamp in thisMonthStart..thisMonthEnd`, a payment recorded at midnight `00:00:00.000` on the 1st or `23:59:59.999` on the last day of the month is guaranteed to be included identically by both Data Check and Home, preventing any false `MISMATCH`.
3. **Month-by-Month SQL Queries (Last 12 Months)**:
   - For each of the last 12 calendar months (`[monthStartMillis, monthEndMillis]`):
     - Money In (paise): `SELECT COALESCE(SUM(amountPaise), 0) FROM transactions WHERE deletedAtMillis IS NULL AND status = 'SUCCESS' AND type = 'EARNING' AND timestamp BETWEEN :startMillis AND :endMillis`
     - Money Out (paise): `SELECT COALESCE(SUM(amountPaise), 0) FROM transactions WHERE deletedAtMillis IS NULL AND status = 'SUCCESS' AND type = 'EXPENSE' AND timestamp BETWEEN :startMillis AND :endMillis`
     - Payment Count: `SELECT COUNT(*) FROM transactions WHERE deletedAtMillis IS NULL AND status = 'SUCCESS' AND type != 'TRANSFER' AND timestamp BETWEEN :startMillis AND :endMillis`
4. **Last 10 Saved Rows Query**:
   - `getDataCheckLast10SavedRows()`: `SELECT * FROM transactions WHERE deletedAtMillis IS NULL ORDER BY id DESC LIMIT 10`
5. **This Month's Spend SQL Query (for Compare with Home)**:
   - Uses the exact SQL `COALESCE(SUM(amountPaise), 0)` query for the current month (`[thisMonthStartMillis, thisMonthEndMillis]` at `00:00:00.000` .. `23:59:59.999` local time) where `deletedAtMillis IS NULL AND status = 'SUCCESS' AND type = 'EXPENSE'` and compares the resulting paise value against Home's `thisMonthSpentPaise`.

### 4.3 Read-Only Data Check Screen Layout & Exact Paise Formatting
- **Exact Paise Formatter (`formatExactPaise`)**:
  - Formats any paise `Long` value with exact two-digit paise decimals using Indian number grouping (e.g., `123450L` -> `₹1,234.50`, `2000L` -> `₹20.00`, `0L` -> `₹0.00`).
- **Four Cards on Data Check Screen**:
  1. **COMPARE WITH HOME**:
     - Shows *"Data Check SQL (This Month)"* vs *"Home Screen (This Month)"* both formatted to exact paise.
     - Displays a bright mint (`EditorialMint`) **MATCH** pill badge when the two paise totals are identical, or a coral (`EditorialCoral`) **MISMATCH** pill badge displaying both numbers clearly when they differ.
  2. **COUNTS**:
     - Displays Total Active Transactions, the four mutually exclusive active categories (**Success**, **Pending**, **Failed**, **Transfer**), and the separate **Trashed** counter.
  3. **MONTH BY MONTH (LAST 12 MONTHS)**:
     - Lists the last 12 months (newest first) with exact paise **Money In**, exact paise **Money Out**, and **Payments Count** (excluding Failed, Pending, and Transfer).
  4. **LAST 10 SAVED ROWS**:
     - Shows each of the last 10 saved active transactions in a clean monospace/structured card with exact stored fields: `id`, formatted date & time, `amountPaise` (raw integer paise), `type`, `status`, `merchant`, `category`, `source`, `referenceNumber` (or `null`), and `duplicateKey`.
- **COPY REPORT Button**:
  - A tactile 3D button in the header/top card that copies the complete plain-text diagnostic report (all 4 sections) to `ClipboardManager` and triggers a confirmation toast (*"Data check report copied to clipboard!"*).
  - Strictly read-only: no button on the Data Check screen mutates any database state.

---

## 5. Verification & Unit Testing Plan
- **Data Check SQL & Boundary Unit Tests**:
  - Add Robolectric + Room unit tests verifying:
    - Mutually exclusive `Success`, `Pending`, `Failed`, `Transfer`, `Total Active`, and `Trashed` SQL counts.
    - Month-by-Month SQL queries accurately sum `EARNING` (Money In), `EXPENSE` (Money Out), and count payments while excluding `FAILED`, `PENDING`, `TRANSFER`, and soft-deleted (`deletedAtMillis IS NOT NULL`) rows.
    - Exact millisecond boundary tests: a payment at `00:00:00.000` on the 1st of the month and a payment at `23:59:59.999` on the last day of the month are included by both Data Check's SQL query and Home's monthly spend calculation (`MATCH`), whereas `monthStartMillis - 1` and `monthEndMillis + 1` are excluded by both.
    - Last 10 Saved Rows query returns at most 10 active rows ordered by `id DESC` with all stored fields intact.
- **Share Input Validation & Re-Share Cleanup Unit Tests**:
  - Test the 10-item cap, 20 MB file size cap, MIME type validation, precedence of attached files over companion text, and deterministic cleanup of the first share's temporary cache files when a second share arrives before the first share is completed.
- **Build & Test Execution**:
  - Run `gradle :app:testDebugUnitTest` and `compile_applet` to ensure all tests and compilation pass with zero errors.
