# Co-Pilot Hybrid Statement & Receipt Pipeline Implementation Plan

## Overview
This plan implements a two-stage Co-Pilot architecture that combines on-device OCR tiling and deterministic SBI row parsing with mandatory Gemini 3.8-Flash AI enrichment and smart categorization. Local OCR locks in mathematically verified dates, amounts, and UTRs, while Gemini 3.8-Flash cleans up narrations, assigns exact categories, and fills any missing table rows or merged columns via multimodal vision.

---

## 1. User Decisions & Architectural Guidelines
- **AI Enrichment Policy:** Always run Gemini 3.8-Flash enrichment for all statement rows to achieve human-friendly merchant titles, high-fidelity categories, and complete validation.
- **Offline Behavior:** If the device is offline during statement scanning, block and queue the scan until internet connectivity is restored.
- **Filter Synchronization:** When historical statement rows are confirmed, switch active period filters on Home, Charts, and Transactions to **All Time** and display the range snackbar: `"Imported X transactions (Aug–Sep 2026) · Showing updated charts"`.
- **Financial Accuracy:** All monetary amounts remain strictly in `Long` whole paise (₹1 = 100 paise).

---

## 2. Core Implementation Phases

### Phase 1: Tall-Image OCR Tiling & Resolution Preservation (`LocalDocumentExtractor.kt`)
- Ensure ML Kit OCR receives high-resolution bitmaps up to 2048px wide for tall stitched screenshots (`height > 1.8 * width`).
- Vertically tile images into horizontal strips (~1800px tall with 120px overlap) to prevent cutting table lines.
- Deduplicate boundary text lines and spatially cluster items on the same horizontal row within a 15px Y-tolerance.

### Phase 2: Stage 1 — Structural Extraction & Balance Math (`SbiStatementRowParser.kt`)
- Lock in hard data fields from SBI table rows:
  - Row boundary anchoring on `^(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})`.
  - Direction detection: `DEP TFR` / `UPI/CR/` -> `TransactionType.EARNING`, `WDL TFR` / `UPI/DR/` -> `TransactionType.EXPENSE`, `ATM WDL` -> `TransactionType.TRANSFER` (`isAtmWithdrawal = true`, `paymentMode = "CASH"`).
  - Decimal amount disambiguation: first decimal is transaction amount (`amountPaise`), second decimal is running balance (`closingBalancePaise`).
  - Consecutive mathematical balance validation: $\text{PrevBalance} \pm \text{Amount} == \text{CurrentBalance}$.
  - UTR extraction from `UPI/(?:CR|DR)/(\d{12})/([^/]+)`.

### Phase 3: Stage 2 — Mandatory Gemini 3.8-Flash AI Enrichment & Co-Pilot Vision (`GeminiReceiptExtractor.kt`)
- Build `enrichStatementRowsWithGemini`:
  - Formats locally parsed drafts and raw text lines into a compact JSON payload sent to `gemini-3.8-flash` (`temperature = 0.1f`, `thinkingBudget = 0`).
  - Prompts Gemini to clean raw bank narrations into friendly names (e.g. `BINOD KU` -> `Binod Ku`, `ONE97 CO` -> `Paytm Cashback`, `SB GENERAL INSURANCE` -> `SBI General Insurance`, `Indu shop` -> `Indu Shop`).
  - Intersperse the user's category list so Gemini selects the exact best-fit category (`Food & Dining`, `Groceries`, `Bills & Utilities`, `Health`, `Shopping`, `Received from People`, `Sent to People`, etc.).
  - Scenario B (Multimodal Co-Pilot Vision): If math validation fails or extracted row count is significantly lower than estimated page rows, pass compressed image tiles to Gemini Vision to detect and extract missed rows.
  - Merge Policy: Retain locally verified amounts, dates, and UTRs, apply Gemini's enriched merchant names and categories, and respect any user overrides in `MerchantMemoryEntity`.
- Offline handling: When offline, queue statement scans in `ScanUiState.RateLimited` / background work queue and resume immediately upon reconnection.

### Phase 4: UI & Date Synchronization (`SnapSpendViewModel.kt`)
- In `confirmAllReviewDrafts()` and `confirmAllVerifiedReviewDrafts()`:
  - Detect date span of newly confirmed transactions (e.g., Aug–Sep 2026).
  - If any transactions fall outside the current month or form a multi-row statement batch, switch `_filterState.value` to `DateRangeFilter.ALL` and `_chartControl.value` to `DateRangeFilter.ALL`.
  - Present the snackbar: `"Imported X transactions (Aug–Sep 2026) · Showing updated charts"`.
  - Ensure Home Cashflow (Money In / Money Out), Spending Charts (Category breakdown & trends), and Transactions list update reactively.

### Phase 5: Verification & Automated Tests (`HybridLocalScanningTest.kt`)
- Add integration unit tests verifying:
  - Full Co-Pilot pipeline extraction of the 18-row SBI statement from `image_a7efab22.png`.
  - Accurate direction, Long paise conversion, friendly merchant naming, and category mapping.
  - Balance math validation across all 18 rows without column mix-ups.
  - Execution of `gradle :app:testDebugUnitTest` and `compile_applet`.
