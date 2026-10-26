# SnapSpend Smoketest Bug Audit & Remediation Plan

A targeted, production-grade remediation plan addressing potential bugs, edge-case crashes, and data corruption risks identified during the smoketest audit across data persistence, receipt scanning, and UI state boundaries, backed by automated Robolectric test suites.

---

## 1. Audit Findings: Potential Bugs & Edge Cases

### A. Data Persistence & Backup Resilience
1. **RFC 4180 CSV Export/Import Column Corruption**:
   - **Bug**: In `DriveBackupAndExportManager.kt`, CSV export naively concatenates fields (`"$id,$epochMs,$title,$merchant,$category,$type,$amountPaise,$source,$notes,$verified"`) and import splits lines with `line.split(",")`.
   - **Failure Mode**: If a merchant name (e.g., `"Trader Joe's, LLC"`) or note (e.g., `"Groceries, fruit, & bread"`) contains commas, quotes, or newlines, import splits into 11+ parts and fails or silently misassigns columns, corrupting transaction amounts and types.
   - **Fix**: Implement RFC 4180 compliant CSV serialization and parsing: wrap fields containing commas, quotes, or newlines in standard escaped double-quotes (`""`), and parse quoted multi-token fields properly.
2. **Drive Backup / Export Empty State Handling**:
   - **Bug**: Exporting when 0 transactions exist creates an empty CSV or backup payload that might trigger an index out-of-bounds or empty file exception upon import.
   - **Fix**: Handle empty datasets gracefully with valid headers and return informative UI feedback rather than throwing exceptions.

### B. Receipt Scanning & Permissions
1. **Camera Quick Scan Hardware & Permission Boundary**:
   - **Bug**: `QuickAddSourceBottomSheet` features `"Quick Scan with Camera"`. If a user device lacks a rear camera or the camera permission is rejected, directly launching the camera intent can throw an unhandled `ActivityNotFoundException` or runtime permission fault.
   - **Fix**: Verify camera availability via package manager, wrap camera intent launcher in graceful exception handling, and provide an automatic fallback to the zero-permission Android Photo Picker (`ActivityResultContracts.PickVisualMedia`).
2. **Transient Media URI Lifetime**:
   - **Bug**: When an image is picked via Photo Picker or Camera, accessing `contentResolver.openInputStream(uri)` after a configuration change or process recreation can throw `SecurityException` or `FileNotFoundException`.
   - **Fix**: Read and buffer the image bytes immediately into application cache upon selection before passing to the Gemini extraction pipeline, accompanied by a user-facing retry prompt if reading fails.
3. **Queued Scans Offline Persistence**:
   - **Bug**: Tapping `"Queue for later"` during network dropouts must persist the pending scan to Room/SQLite so it survives app restarts.
   - **Fix**: Ensure pending queue items are stored in persistent storage with retry badges on the Home screen.

### C. UI Boundary Conditions & Zero-State Math
1. **Category Card Deck Zero/Single-Item Division Guard**:
   - **Bug**: In `CategoriesFolderScreen.kt`, index calculations use `(frontIndex + i) % categories.size`.
   - **Failure Mode**: If categories are cleared or custom filters produce an empty list, evaluating `% categories.size` throws `java.lang.ArithmeticException: divide by zero`.
   - **Fix**: Add explicit empty-state fallback guard before deck layout rendering, and disable swipe physics when `categories.size <= 1`.
2. **Chart Math Division by Zero (Empty State)**:
   - **Bug**: In `ChartsAndInsightsScreen.kt`, `avgPerDayPaise`, MoM percentage changes, and bar chart height normalization divide by total days or max values (`value / maxValue.toFloat()`).
   - **Failure Mode**: On a fresh install with 0 transactions, `maxValue == 0` causes `Float.NaN` or `Float.POSITIVE_INFINITY`, causing Canvas draw crashes or layout calculation errors.
   - **Fix**: Guard all chart scale normalizations with `if (maxValue <= 0L) 0f else (value.toFloat() / maxValue)`.
3. **Paise Compact Formatting Boundaries**:
   - **Bug**: Compact money formatting (`formatPaiseCompact`) with negative paise or very large numbers (crores) can produce awkward signs (e.g. `-₹1.2L` vs `₹-1.2L`).
   - **Fix**: Normalize sign positioning and guarantee exact paise accuracy in all mathematical helpers.

---

## 2. Remediation Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Smoketest Fixes Pipeline                        │
├────────────────────────────────┬───────────────────────────────────────┤
│ 1. Data Layer Hardening        │ • RFC 4180 CSV quoted parser/formatter│
│                                │ • Safe empty backup/restore guards    │
├────────────────────────────────┼───────────────────────────────────────┤
│ 2. Scanner & Media Resilience  │ • Immediate URI byte buffering in UI  │
│                                │ • Camera permission & fallback checks │
├────────────────────────────────┼───────────────────────────────────────┤
│ 3. UI Boundary & Math Guards   │ • Category deck zero/single item check│
│                                │ • Chart NaN/Infinity math guards      │
├────────────────────────────────┼───────────────────────────────────────┤
│ 4. Robolectric Test Suite      │ • Export/Import round-trip tests      │
│                                │ • MoneyUtils & Math unit tests        │
│                                │ • Deck boundary & zero-state tests    │
└────────────────────────────────┴───────────────────────────────────────┘
```

---

## 3. Implementation Steps

### Step 1: Data Persistence & CSV Integrity (`DriveBackupAndExportManager.kt`)
- Write an RFC 4180 CSV encoder that escapes quotes (`" -> ""`) and encloses fields in double quotes when containing commas or newlines.
- Write a resilient stateful CSV line parser that respects quoted substrings with embedded commas.
- Add safety checks to handle blank rows, corrupt headers, and zero-record files cleanly.

### Step 2: Scanner & Intent Resilience (`MainActivity.kt` & `ReviewAndAddSheet.kt`)
- Safeguard camera intent launch with package resolution and `runCatching` fallback to Photo Picker.
- Buffer selected media stream synchronously into memory/cache to prevent Android URI permission expiration.
- Provide smooth retry dialogs on transient Gemini 429/network errors.

### Step 3: UI & Chart Math Boundary Protections
- In `CategoriesFolderScreen.kt`: Guard against empty category lists with a friendly empty card and clamp swipe gestures when category count is 1.
- In `ChartsAndInsightsScreen.kt` & `EditorialCharts.kt`: Guard `maxValue == 0L`, MoM percentage division, and average daily pacing against `0` denominators.
- In `MoneyUtils.kt`: Ensure negative values, extreme amounts, and tabular formatting format cleanly with standard currency prefixes.

### Step 4: Automated Robolectric Test Suite
- Create `SmoketestResilienceTest.kt` in `app/src/test/java/com/example/snapspend/`:
  - **Test 1: CSV Round-Trip**: Export transactions with complex merchant names (`"ACME, Inc."`), quotes (`"The \"Best\" Coffee"`), and newlines, then re-import and assert 100% data fidelity.
  - **Test 2: Empty State & Zero Math**: Assert chart computations with 0 transactions yield 0 instead of NaN or crash.
  - **Test 3: MoneyUtils Boundaries**: Test extreme values (`0`, `-1`, `-50000`, `100000000`) for paise conversion and display.
  - **Test 4: Category Deck Index Wrapping**: Test deck index calculations for 0, 1, and 20 items.

### Step 5: Verification & Quality Assurance
- Run `gradle :app:testDebugUnitTest` via `run_command` to execute all existing and new Robolectric test cases.
- Run `compile_applet` to confirm zero compilation errors.
