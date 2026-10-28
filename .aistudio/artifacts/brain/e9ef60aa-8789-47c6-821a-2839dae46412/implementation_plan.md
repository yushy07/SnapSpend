# SnapSpend

A paise-accurate Indian UPI and bank statement expense tracker with deterministic on-device verification, native PDF text extraction, and benchmarked low-token AI parsing.

## Aesthetic Direction

- **Domain**: Personal Finance & Indian UPI / Bank Statement Tracking
- **Aesthetic Persona**: Neo-Brutalist 3D Ledger (existing look preserved unchanged)
- **Brand Inspirations**: Gumroad, Wise, Teenage Engineering
- **Color Palette**: Existing warm cream (`#F7EBE1`) and dark charcoal (`#1C1917`) surfaces with 2dp black borders and 3dp hard shadows; Mint Green (`#9FE3C0`) for the `VERIFIED` pill and completion banner; Coral (`#FF7A6B`) for `DATE NOT FOUND · TAP TO SET`, `CHECK AMOUNT`, `CHECK DATE`, `CARD BILL · CHECK`, and `SOME ROWS MAY BE MISSING` warning chips
- **Typography**: Existing geometric headings and tabular numerals; monospaced metrics in Data Check tables

##Key Features

- **Stage-by-Stage Local Scan Log (`SCAN TIMINGS`)**:
  - Records timings for every stage (read file, on-device text reading, regex, Gemini text call, vision fallback, total), resolution path (`FAST_PATH`, `TEXT_AI`, `VISION_AI`, `NOTE_AI`, `SKIPPED_KNOWN`), retry count, and token counts (`response.usageMetadata` prompt/output plus pre/post statement filter counts).
  - Persists the last 50 entries in DataStore without ever logging receipt text, merchant names, or amounts.
  - Renders a `SCAN TIMINGS` table and summary averages (time per scan, tokens per scan, percentage per path) in Data Check and includes them in `COPY REPORT`.
- **Interactive Scan Lab (`SCAN LAB`)**:
  - Inside Data Check, lets you pick up to 20 screenshots or PDFs from the phone and runs each through the real pipeline without saving to transactions or copying files.
  - Displays a row per file (time, path used, tokens, extracted merchant, amount, date, category) with `CORRECT` and `WRONG` buttons and live totals (percent correct, average time, average tokens) to benchmark before/after changes and compare the 12-key vs. compact short-key schemas.
- **Local Parser Fixes & Strict Fast Path**:
  - **Categories & Whole-Word Matching**: Fixes legacy names to `Transport & Fuel` and `Health`; enforces whole-word keyword matching (`ola`, `tea`, `gas`, `CRED` never match inside words); removes category-name substring matching for `Other`, `Other Income`, and `Rent`.
  - **150-Brand Indian Dictionary**: Consolidates ~150 Indian brands and UPI handle prefixes (`swiggy@`, `zomato@`, `irctc@`, `uber@`, `jio@`, etc.) into a single validated table mapped only to existing categories (validated at startup with a unit test; no new categories added).
  - **Payee & Amount Extraction**: Matches `Paid to`, `Sent to`, `To:`, `Merchant:` only at word start to end-of-line with trailing status/date/ID stripped; ignores lines with `balance`, `cashback`, `reward`, `offer`, `coupon`, `limit`, `scratch` and skips Fast Path if two different candidate amounts remain.
  - **Multi-Row & Direction Rules**: Counts only lines with a real money amount (`₹`/`Rs`/`INR` prefix or two decimals) so single GPay/PhonePe/Paytm screens are never treated as multi-row; requires explicit phrases (`received from`, `credited to your account`, `refund of`) for earnings.
  - **Indian Dates & Missing Date Chip**: Supports `28 Oct 2026`, `Oct 28, 2026`, `28 October 2026 at 9:42 am`, `28 Oct`, `28/10/26`, `28-10-2026` (`DD/MM/YYYY`), normalizes numeric dates locally (`04/10/2026` -> `4 Oct 2026`) using the phone's time zone, resolves missing years to the most recent non-future date, rejects future dates, and marks missing dates `dateFound = false` with a coral `DATE NOT FOUND · TAP TO SET` chip (today preselected).
  - **Strict Fast Path Rule**: Triggers Fast Path only when ALL are true: one clear amount, a merchant (matched in the brand dictionary or merchant memory), a valid date (or `"Today"`), status `SUCCESS`, and (`UTR found` OR `merchant is in merchant memory`).
- **Skip AI When Already Known (`SKIPPED_KNOWN`)**:
  - Before any network call, checks if an extracted UTR/reference number matches a non-trashed transaction or if the cleaned text SHA-256 hash matches the last 200 confirmed scan hashes (hashes only, excluded from backups/exports).
  - Immediately shows `ALREADY SAVED: ₹X · Merchant · date` with `OPEN IT`.
- **Per-File Concurrency, Native PDF Text Layer & Statement Filter**:
  - Processes each file separately (up to 3 at once), streams review cards as each finishes, shows per-file `RETRY` on failure without blocking others, and runs vision fallback on every failed image.
  - Reads the PDF's own text layer first via `PdfBox-Android` (reporting exact APK size impact), renders/OCRs only pages without text, handles password-protected PDFs with a clear message and password prompt, processes in 2-page / 40-row chunks (up to 3 at once) showing `Read 12 of 30 pages`, and reports exact skipped pages if >30 pages.
  - Filters multi-row statement text to retain date+amount lines and wrapped narration continuations while dropping headers/footers/IFSC/summaries, logs pre/post token counts, and flags `SOME ROWS MAY BE MISSING` if AI returns fewer rows than kept.
  - Defines a clean `BankStatementParser` interface with no guessed bank implementations; unmatched lines go to Gemini.
- **Smaller/Compact AI Call & Smart Retry**:
  - Uses one model constant (`temperature = 0.1`, `thinkingBudget = 0`), fixed instructions first and cleaned text last, no regex hints block, `EARNING` wording, `"Sent to People"` for individual names, `TRANSFER` for own-account/ATM, `"unknown"` for missing dates, and benchmarks compact short keys (`m, a, d, tm, t, p, s, r, c` + `readable`) against the 12-field schema (switching back automatically if Scan Lab accuracy drops).
  - Enforces 20s text / 30s vision timeouts (max 2 attempts), retries only network/timeout/429/503, fails immediately with a clear explanation on 400/403/404, queues without looping on 429 (`AI limit reached. Queued, will retry later`) or offline, and displays elapsed seconds with a `CANCEL` button.
- **Local Verification, Section Rules & Verified Batch Confirm**:
  - Verifies locally that `amountPaise` appears in on-device text (`CHECK AMOUNT` chip if not; shows both AI and regex amounts if they differ), drops `referenceNumber` if not in text, flags future or >5-year-old dates (`CHECK DATE`), and overrides AI category with merchant memory.
  - Applies deterministic whole-word section rules in priority order: ATM cash logic, own-account `TRANSFER`, and credit card bill payments marked with `CARD BILL · CHECK` (Transfer vs. Expense selector + Settings option `Card bill payments: Ask / Transfer / Expense`, default `Ask`).
  - Shows a green `VERIFIED` pill only when all local checks pass, sorts flagged cards first, displays `X verified · Y to check`, and adds `CONFIRM ALL VERIFIED (X)` with an `UNDO` bar.

## User Flows

- **Per-File Scanning & Batch Verification**: Select one or more images/PDFs -> up to 3 process concurrently with live elapsed timer, page progress (`Read 12 of 30 pages`), and `CANCEL` -> known UTRs/hashes immediately show `ALREADY SAVED: ₹X · Merchant · date` (`OPEN IT`) -> completed files stream into the review screen with flagged cards sorted first and counts `X verified · Y to check` -> tap `CONFIRM ALL VERIFIED (X)` to save verified cards with an `UNDO` bar while inspecting any flagged cards.
- **Scan Lab & Timing Diagnostics**: Open Settings -> Data Check -> inspect `SCAN TIMINGS` and path/token averages or tap `COPY REPORT` -> tap `SCAN LAB` and select up to 20 files -> grade each row `CORRECT` or `WRONG` to compare accuracy, latency, and token usage without saving to the ledger.

## Architecture & Technical Approach

- **On-Device Pipeline**: `LocalDocumentExtractor` (PdfBox text layer + ML Kit OCR fallback) -> `KnownScanDeduplicator` (UTR query + 200 SHA-256 ring buffer) -> `BankStatementParser` interface -> `UpiRegexPreProcessor` + `IndianBrandDictionary` -> `StatementLineFilter` -> `GeminiReceiptExtractor` (compact/12-key schema with round-trip mapper) -> `LocalDraftVerifier` & `DeterministicSectionRules`.
- **Verification & Telemetry**: Comprehensive JUnit/Robolectric tests covering all 10 local parser scenarios, brand dictionary startup validation, compact schema round-trip conversion, and automated before/after Scan Lab benchmark comparisons.
