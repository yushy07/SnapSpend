# Implementation Plan: Interactive Sound Effects, Category Card Swipe Swooshes & Playful Background Music

Implement interactive audio feedback across SnapSpend:
1. **Interactive Launch Carousel**: Upbeat intro tune on launch, swoosh sound effects on page transitions, and tactile button pops on Next/Skip/Finish actions.
2. **Lo-Fi Cozy Electronic Background Music (BGM)**: Continual looping, relaxing electronic music synthesized directly on-device in memory, running at full arcade volume with lifecycle-aware pause/resume and full control in Settings.
3. **Categories Card Swipe Swooshes**: Responsive card swiping swoosh sound effects whenever category cards are dragged, tossed, or stepped in `CategoriesFolderScreen`.
4. **Settings & Preferences**: Dedicated BGM toggle ("PLAYFUL BGM", ON/OFF) alongside Sound FX in the 3D Neo-Brutalist audio settings card.

---

## User Preferences & Design Choices (Confirmed)
- **BGM Style**: Lo-fi cozy electronic melody (warm chords, relaxing bassline, gentle retro synth timbre, seamless loop).
- **Control**: Full arcade volume with controls located exclusively in Settings (no intrusive top-bar toggles).
- **Carousel Audio**: Upbeat energetic intro tune, accompanied by page turn swooshes and button pops.
- **Card Swiping**: Quick swoosh / whoosh sound effect on every category card swipe.

---

## Proposed Changes

### 1. Audio Engine Expansion (`com.example.ui.sound.RetroSoundManager`)
- **Swoosh Synthesis (`synthesizeSwooshWav`)**:
  - Synthesize a dynamic aerodynamic whoosh using bandpass-filtered noise and descending/ascending frequency resonance (80–120ms duration).
  - Add `playSwoosh()` for category card dragging and carousel page turns.
- **BGM Synthesizer & Looper**:
  - **`synthesizeLoFiBgmWav()`**: Generate a warm, seamless 8-bar lo-fi electronic melody:
    - Root chord progression (e.g., Cmaj7 $\rightarrow$ Am7 $\rightarrow$ Dm7 $\rightarrow$ G7).
    - Soft triangle/pulse lead waveform with smooth envelope (no harsh clicks).
    - Subtle walking sub-bass and warm rhythm pulses.
  - **`synthesizeCarouselIntroWav()`**: Generate an upbeat, energetic 4-bar intro hook for the launch onboarding screen.
- **Background Music Player Engine**:
  - Use Android's `MediaPlayer` in looping mode (`isLooping = true`) referencing cached synthesized audio files.
  - Provides methods: `startMainBgm()`, `startCarouselBgm()`, `pauseBgm()`, `resumeBgm()`, `setBgmEnabled(Boolean)`.
  - Ensures seamless audio looping, low battery usage, zero memory leaks, and graceful release on activity disposal.

### 2. Categories Screen Card Swiping Swooshes (`CategoriesFolderScreen.kt`)
- Wire `soundManager?.playSwoosh()` into `animateSingleStep()` and `onDragEnd` when card swipe commits.
- Ensure rapid dragging or card flips trigger the swoosh audio without stutter.

### 3. Launch Carousel Interactive Audio (`OnboardingCarouselScreen.kt`)
- In `OnboardingCarouselScreen`:
  - When the carousel is active, play the upbeat carousel intro tune.
  - When `pagerState.currentPage` changes, trigger `soundManager?.playSwoosh()`.
  - When buttons (Next, Skip, Finish) are clicked, trigger `soundManager?.playButtonBlip()`.
  - On exit/finish, transition smoothly to main lo-fi BGM.

### 4. Settings & State Management (`SettingsAndBackupScreen.kt`, `SnapSpendPreferences.kt`, `SnapSpendViewModel.kt`)
- **State & Preferences**:
  - Add `KEY_BGM_ENABLED` in `SnapSpendPreferences` (default: `true`).
  - Expose `bgmEnabled: StateFlow<Boolean>` in `SnapSpendViewModel` with `setBgmEnabled(Boolean)`.
- **Settings Card (`SettingsAndBackupScreen.kt`)**:
  - Update the 3D Neo-Brutalist audio card to include:
    - Segmented toggle for **SOUND EFFECTS** (ON / MUTED).
    - Segmented toggle for **BACKGROUND MUSIC** (ON / MUTED).
    - Preview buttons: `COIN`, `BLIP`, `SWOOSH`, `WARP`.

### 5. Lifecycle Management (`MainActivity.kt`)
- Hook into Android `LifecycleEventObserver`:
  - `ON_PAUSE` / `ON_STOP`: Pause background music so it doesn't play when the user leaves the app or turns off the screen.
  - `ON_RESUME`: Resume background music if enabled.
  - `ON_DESTROY`: Cleanly release media players and audio pools.

---

## Verification Plan

### Automated Tests
- Extend `RetroSoundManagerTest.kt`:
  - Verify `synthesizeSwooshWav()` produces valid RIFF/WAV headers and non-zero audio samples.
  - Verify `synthesizeLoFiBgmWav()` and `synthesizeCarouselIntroWav()` produce valid looping WAV audio.
- Run unit and Robolectric tests via `gradle :app:testDebugUnitTest`.

### Build Verification
- Execute `compile_applet` to confirm clean compilation and zero build errors.
