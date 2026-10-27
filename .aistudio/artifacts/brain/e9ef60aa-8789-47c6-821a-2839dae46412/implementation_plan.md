# Implementation Plan: Fix Connection Hiccup Root Cause in Gemini AI Extraction

## Root Cause Analysis
- **Problem**: When scanning custom photos, PDFs, or natural language notes, users encounter the "Connection hiccup" error card.
- **Root Cause**: `GeminiReceiptExtractor.kt` instantiates Firebase AI models using `modelName = "gemini-3.8-flash"`. The Firebase AI Logic / Vertex AI backend does not recognize `"gemini-3.8-flash"`, causing requests to fail immediately with an unhandled model/API error.
- **Solution**: Update `modelName` in `GeminiReceiptExtractor.kt` to a valid, standard Firebase AI model name (`"gemini-2.5-flash"`).

---

## Proposed Changes

### `app/src/main/java/com/example/data/ai/GeminiReceiptExtractor.kt`
- Replace `modelName = "gemini-3.8-flash"` with `modelName = "gemini-2.5-flash"` in all occurrences (both text extraction and multimodal image/PDF analysis).

---

## Verification Plan

### Automated Tests
- Run `gradle :app:testDebugUnitTest` to verify compilation and unit test suite success.

### Build Verification
- Execute `compile_applet` to ensure clean build output.
