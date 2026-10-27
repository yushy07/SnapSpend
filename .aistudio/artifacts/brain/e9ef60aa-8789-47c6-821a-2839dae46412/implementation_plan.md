# Implementation Plan: 8-Bit Retro Sound Effects & Codebase Polish

## 1. Overview & Objectives
Implement an authentic, lightweight 8-bit retro arcade audio system for **SnapSpend** and resolve potential warnings/errors across the codebase.
Based on your clarifying choices:
- **Sound Style**: Playful 8-bit retro arcade blips and coin sounds (chiptune square/pulse wave synthesis).
- **Scope**: Key financial actions (saving expenses/earnings, AI review confirmation, deleting/trashing, ATM top-ups) and primary action button taps.
- **Default State**: Enabled by default with a dedicated toggle card and test buttons in Settings.

---

## 2. Architecture & Sound Engine

### A. Synthesized Chiptune Audio (`RetroSoundManager`)
Rather than relying on external asset files that could fail to load or add binary bloat, we implement an on-device 8-bit chiptune sound generator using `SoundPool` with synthesized 16-bit PCM waveforms:
1. **Coin Cha-Ching (`COIN_CHA_CHING`)**: Classic 2-tone rising arpeggio (B5 ~987Hz to E6 ~1318Hz, duty-cycle square wave) played when logging a payment, confirming an AI draft, or topping up cash.
2. **Action Blip (`BUTTON_BLIP`)**: Snappy, rapid upward pitch blip (440Hz -> 880Hz, 35ms) played on primary button taps (e.g. "+ ADD", "SORT WITH AI", segmented pill switches).
3. **Power-Up Fanfare (`POWER_UP_SUCCESS`)**: 4-note ascending arcade melody (C5, E5, G5, C6) played on backup completion, import success, or AI batch scan completion.
4. **Warp Delete (`DELETE_WARP`)**: Fast downward frequency drop (700Hz -> 180Hz) played when moving transactions to trash or emptying trash.
5. **Alert Buzz (`ERROR_BUZZ`)**: Low dual-frequency rasp (160Hz + 110Hz square wave) played on validation error or duplicate alert.

### B. Settings & State Management
- Store `soundEffectsEnabled: Boolean` in DataStore preferences / ViewModel state, defaulting to `true`.
- Add a dedicated neo-brutalist 3D card in `SettingsAndBackupScreen.kt`:
  - Sound effect toggle switch (ON/OFF).
  - Quick test buttons: "TEST COIN", "TEST BLIP", "TEST WARP".
  - Status indicator badge (`ENABLED` / `MUTED`).

### C. Trigger Integration Points
- **HomeScreen**:
  - "+ ADD" / Quick Scan sheet opening -> `BUTTON_BLIP`
  - Quick repeat chip tap -> `BUTTON_BLIP`
  - Payment logged -> `COIN_CHA_CHING`
- **ReviewAndAddSheet / Smart Cash Log**:
  - Quick amount buttons (+₹10, +₹50, etc.) -> `BUTTON_BLIP`
  - "SORT WITH AI" action -> `BUTTON_BLIP`
  - "CONFIRM ALL" or individual transaction save -> `COIN_CHA_CHING`
- **TransactionsScreen & ChartsAndInsightsScreen**:
  - Segmented filter switch (All | Online | Cash) -> `BUTTON_BLIP`
  - ATM Withdrawal / Top-Up save -> `COIN_CHA_CHING`
- **TrashScreen & Delete Dialogs**:
  - Move to trash / Delete confirmed -> `DELETE_WARP`
- **SettingsAndBackupScreen**:
  - Cloud Backup or Export completed -> `POWER_UP_SUCCESS`

---

## 3. Codebase Diagnostics & Warning Fixes
1. **Audio Resource Lifecycle**: Ensure `SoundPool` is properly initialized with an `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION` profile and released cleanly without audio session leaks.
2. **Nullable & Safe Cast Checks**: Audit ViewModel and UI event callbacks for any potential null dereference warnings or unhandled edge cases in cash calculations.
3. **Compose Deprecations & Cleanliness**: Fix any dangling unused parameters, redundant imports, or suppressions.

---

## 4. Verification Plan
- **Audio Verification**: Test sound playback across all trigger events and verify instant muting when toggled off in Settings.
- **Unit & Migration Tests**: Run `:app:testDebugUnitTest` to ensure all existing Room v3->v4 migration tests and month-end calculations pass.
- **Build Verification**: Run `compile_applet` to confirm clean compilation with zero errors.
