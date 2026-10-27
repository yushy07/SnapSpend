package com.example.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import androidx.compose.runtime.compositionLocalOf
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 8-Bit Retro Arcade Sound Synthesizer & Manager for SnapSpend.
 * Synthesizes authentic chiptune audio and looping lo-fi background music directly in memory
 * with zero external assets or network calls, caching them locally for zero-latency playback.
 */
class RetroSoundManager(private val context: Context) {

    enum class BgmTrack {
        MAIN_LOFI,
        CAROUSEL_INTRO
    }

    private var soundPool: SoundPool? = null
    private var isSoundEnabled: Boolean = true
    private var isBgmEnabled: Boolean = true

    private var coinSoundId: Int = 0
    private var blipSoundId: Int = 0
    private var successSoundId: Int = 0
    private var deleteSoundId: Int = 0
    private var errorSoundId: Int = 0
    private var swooshSoundId: Int = 0

    private val loadedSoundIds = mutableSetOf<Int>()

    // Background Music MediaPlayer
    private var mediaPlayer: MediaPlayer? = null
    private var currentTrack: BgmTrack? = null
    private var isPausedByLifecycle: Boolean = false

    private val cacheDir = File(context.cacheDir, "retro_sounds").apply { mkdirs() }
    private val mainLofiFile = File(cacheDir, "bgm_lofi.wav")
    private val carouselIntroFile = File(cacheDir, "bgm_carousel.wav")

    init {
        initSoundPool()
    }

    private fun initSoundPool() {
        runCatching {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(audioAttributes)
                .build().apply {
                    setOnLoadCompleteListener { _, sampleId, status ->
                        if (status == 0) {
                            loadedSoundIds.add(sampleId)
                        }
                    }
                }

            loadOrSynthesizeSounds()
        }
    }

    private fun loadOrSynthesizeSounds() {
        val sp = soundPool ?: return

        runCatching {
            val coinFile = File(cacheDir, "retro_coin.wav")
            if (!coinFile.exists() || coinFile.length() == 0L) {
                FileOutputStream(coinFile).use { it.write(synthesizeCoinWav()) }
            }
            coinSoundId = sp.load(coinFile.absolutePath, 1)

            val blipFile = File(cacheDir, "retro_blip.wav")
            if (!blipFile.exists() || blipFile.length() == 0L) {
                FileOutputStream(blipFile).use { it.write(synthesizeBlipWav()) }
            }
            blipSoundId = sp.load(blipFile.absolutePath, 1)

            val successFile = File(cacheDir, "retro_success.wav")
            if (!successFile.exists() || successFile.length() == 0L) {
                FileOutputStream(successFile).use { it.write(synthesizeSuccessWav()) }
            }
            successSoundId = sp.load(successFile.absolutePath, 1)

            val deleteFile = File(cacheDir, "retro_delete.wav")
            if (!deleteFile.exists() || deleteFile.length() == 0L) {
                FileOutputStream(deleteFile).use { it.write(synthesizeDeleteWav()) }
            }
            deleteSoundId = sp.load(deleteFile.absolutePath, 1)

            val errorFile = File(cacheDir, "retro_error.wav")
            if (!errorFile.exists() || errorFile.length() == 0L) {
                FileOutputStream(errorFile).use { it.write(synthesizeErrorWav()) }
            }
            errorSoundId = sp.load(errorFile.absolutePath, 1)

            val swooshFile = File(cacheDir, "retro_swoosh.wav")
            if (!swooshFile.exists() || swooshFile.length() == 0L) {
                FileOutputStream(swooshFile).use { it.write(synthesizeSwooshWav()) }
            }
            swooshSoundId = sp.load(swooshFile.absolutePath, 1)
        }
    }

    // --- Sound Effects Controls ---

    fun setSoundEnabled(enabled: Boolean) {
        isSoundEnabled = enabled
    }

    fun isSoundEnabled(): Boolean = isSoundEnabled

    fun playCoin() {
        playSound(coinSoundId, 0.95f)
    }

    fun playButtonBlip() {
        playSound(blipSoundId, 0.65f)
    }

    fun playSuccess() {
        playSound(successSoundId, 0.90f)
    }

    fun playDelete() {
        playSound(deleteSoundId, 0.85f)
    }

    fun playError() {
        playSound(errorSoundId, 0.75f)
    }

    fun playSwoosh() {
        playSound(swooshSoundId, 0.80f)
    }

    private fun playSound(soundId: Int, volume: Float) {
        if (!isSoundEnabled || soundId == 0) return
        runCatching {
            soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
        }
    }

    // --- Background Music Controls ---

    fun setBgmEnabled(enabled: Boolean) {
        isBgmEnabled = enabled
        if (!enabled) {
            pauseBgmInternal()
        } else {
            if (currentTrack != null) {
                resumeBgmInternal()
            } else {
                startMainBgm()
            }
        }
    }

    fun isBgmEnabled(): Boolean = isBgmEnabled

    fun startMainBgm() {
        playTrack(BgmTrack.MAIN_LOFI)
    }

    fun startCarouselBgm() {
        playTrack(BgmTrack.CAROUSEL_INTRO)
    }

    private fun playTrack(track: BgmTrack) {
        if (currentTrack == track && mediaPlayer?.isPlaying == true) return
        currentTrack = track

        if (!isBgmEnabled) return

        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            val file = when (track) {
                BgmTrack.MAIN_LOFI -> {
                    if (!mainLofiFile.exists() || mainLofiFile.length() == 0L) {
                        FileOutputStream(mainLofiFile).use { it.write(synthesizeLoFiBgmWav()) }
                    }
                    mainLofiFile
                }
                BgmTrack.CAROUSEL_INTRO -> {
                    if (!carouselIntroFile.exists() || carouselIntroFile.length() == 0L) {
                        FileOutputStream(carouselIntroFile).use { it.write(synthesizeCarouselIntroWav()) }
                    }
                    carouselIntroFile
                }
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                setVolume(0.75f, 0.75f)
                prepare()
                start()
            }
        }
    }

    fun onPauseLifecycle() {
        isPausedByLifecycle = true
        pauseBgmInternal()
    }

    fun onResumeLifecycle() {
        if (isPausedByLifecycle) {
            isPausedByLifecycle = false
            if (isBgmEnabled && currentTrack != null) {
                resumeBgmInternal()
            }
        }
    }

    private fun pauseBgmInternal() {
        runCatching {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        }
    }

    private fun resumeBgmInternal() {
        if (!isBgmEnabled) return
        runCatching {
            if (mediaPlayer != null) {
                mediaPlayer?.start()
            } else if (currentTrack != null) {
                playTrack(currentTrack!!)
            } else {
                startMainBgm()
            }
        }
    }

    fun release() {
        runCatching {
            soundPool?.release()
            soundPool = null
        }
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    companion object {
        private const val SAMPLE_RATE = 22050

        /**
         * Synthesizes 16-bit PCM RIFF/WAV for 8-bit coin sound:
         * Step 1 (80ms): B5 (987.8 Hz)
         * Step 2 (240ms): E6 (1318.5 Hz) with exponential decay
         */
        fun synthesizeCoinWav(): ByteArray {
            val totalSamples = (SAMPLE_RATE * 0.32).toInt()
            val step1Samples = (SAMPLE_RATE * 0.08).toInt()
            val step2Samples = totalSamples - step1Samples

            val pcmData = ShortArray(totalSamples)
            var phase = 0.0

            for (i in 0 until step1Samples) {
                val freq = 987.8
                phase += freq / SAMPLE_RATE
                if (phase >= 1.0) phase -= 1.0
                val square = if (phase < 0.5) 0.65f else -0.65f
                val env = (i.toFloat() / (SAMPLE_RATE * 0.005f)).coerceAtMost(1f)
                pcmData[i] = (square * env * 32767).toInt().toShort()
            }

            phase = 0.0
            for (i in 0 until step2Samples) {
                val freq = 1318.5
                phase += freq / SAMPLE_RATE
                if (phase >= 1.0) phase -= 1.0
                val square = if (phase < 0.5) 0.7f else -0.7f
                val progress = i.toFloat() / step2Samples
                val env = exp(-3.8 * progress).toFloat()
                pcmData[step1Samples + i] = (square * env * 32767).toInt().toShort()
            }

            return createWavFile(pcmData, SAMPLE_RATE)
        }

        /**
         * Synthesizes rapid upward chirp for button taps (40ms):
         * 420 Hz -> 880 Hz upward slide
         */
        fun synthesizeBlipWav(): ByteArray {
            val totalSamples = (SAMPLE_RATE * 0.040).toInt()
            val pcmData = ShortArray(totalSamples)
            var phase = 0.0

            for (i in 0 until totalSamples) {
                val progress = i.toFloat() / totalSamples
                val freq = 420.0 + (880.0 - 420.0) * progress
                phase += freq / SAMPLE_RATE
                if (phase >= 1.0) phase -= 1.0
                val square = if (phase < 0.4) 0.6f else -0.6f
                val env = (1.0f - progress).coerceAtLeast(0f)
                pcmData[i] = (square * env * 32767).toInt().toShort()
            }

            return createWavFile(pcmData, SAMPLE_RATE)
        }

        /**
         * Synthesizes 4-note ascending fanfare (300ms total):
         * C5 (523Hz) -> E5 (659Hz) -> G5 (784Hz) -> C6 (1046Hz)
         */
        fun synthesizeSuccessWav(): ByteArray {
            val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
            val noteSamples = (SAMPLE_RATE * 0.075).toInt()
            val totalSamples = noteSamples * notes.size
            val pcmData = ShortArray(totalSamples)

            for (n in notes.indices) {
                val freq = notes[n]
                var phase = 0.0
                val offset = n * noteSamples
                for (i in 0 until noteSamples) {
                    phase += freq / SAMPLE_RATE
                    if (phase >= 1.0) phase -= 1.0
                    val square = if (phase < 0.5) 0.7f else -0.7f
                    val progress = i.toFloat() / noteSamples
                    val attack = (i.toFloat() / (SAMPLE_RATE * 0.005f)).coerceAtMost(1f)
                    val decay = exp(-2.5 * progress).toFloat()
                    val env = attack * decay
                    pcmData[offset + i] = (square * env * 32767).toInt().toShort()
                }
            }

            return createWavFile(pcmData, SAMPLE_RATE)
        }

        /**
         * Synthesizes downward warp / delete drop (110ms):
         * 740 Hz -> 140 Hz downward pitch sweep
         */
        fun synthesizeDeleteWav(): ByteArray {
            val totalSamples = (SAMPLE_RATE * 0.110).toInt()
            val pcmData = ShortArray(totalSamples)
            var phase = 0.0

            for (i in 0 until totalSamples) {
                val progress = i.toFloat() / totalSamples
                val freq = 740.0 * (1.0 - progress * 0.82)
                phase += freq / SAMPLE_RATE
                if (phase >= 1.0) phase -= 1.0
                val duty = 0.5f - 0.25f * progress
                val square = if (phase < duty) 0.7f else -0.7f
                val env = (1.0f - progress * 0.7f).coerceAtLeast(0f)
                pcmData[i] = (square * env * 32767).toInt().toShort()
            }

            return createWavFile(pcmData, SAMPLE_RATE)
        }

        /**
         * Synthesizes two-tone harsh error buzz (160ms):
         * 160Hz + 110Hz square waves
         */
        fun synthesizeErrorWav(): ByteArray {
            val totalSamples = (SAMPLE_RATE * 0.160).toInt()
            val pcmData = ShortArray(totalSamples)
            var phase1 = 0.0
            var phase2 = 0.0

            for (i in 0 until totalSamples) {
                phase1 += 160.0 / SAMPLE_RATE
                if (phase1 >= 1.0) phase1 -= 1.0
                phase2 += 110.0 / SAMPLE_RATE
                if (phase2 >= 1.0) phase2 -= 1.0

                val square1 = if (phase1 < 0.5) 0.45f else -0.45f
                val square2 = if (phase2 < 0.5) 0.35f else -0.35f
                val combined = square1 + square2

                val progress = i.toFloat() / totalSamples
                val mod = if (sin(2 * PI * 25 * progress) > 0) 1.0f else 0.4f
                val env = (1.0f - progress * 0.4f) * mod
                pcmData[i] = (combined * env * 32767).toInt().toShort()
            }

            return createWavFile(pcmData, SAMPLE_RATE)
        }

        /**
         * Synthesizes aerodynamic card swipe swoosh (100ms):
         * Bandpass frequency swept noise + subtle low pitch drop
         */
        fun synthesizeSwooshWav(): ByteArray {
            val durationSeconds = 0.100
            val totalSamples = (SAMPLE_RATE * durationSeconds).toInt()
            val pcmData = ShortArray(totalSamples)

            var seed = 123456789L
            fun nextRandom(): Double {
                seed = (seed * 1103515245L + 12345L) and 0x7FFFFFFF
                return (seed.toDouble() / 0x7FFFFFFF) * 2.0 - 1.0
            }

            var lowPass = 0.0
            var bandPass = 0.0
            var tonePhase = 0.0

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / totalSamples // 0.0 to 1.0
                // Center frequency sweeps up to 1300Hz, then drops to 350Hz
                val centerFreq = if (t < 0.35) {
                    400.0 + (1300.0 - 400.0) * (t / 0.35)
                } else {
                    1300.0 - (1300.0 - 320.0) * ((t - 0.35) / 0.65)
                }

                val f = 2.0 * sin(PI * centerFreq / SAMPLE_RATE)
                val q = 0.35 // Resonance factor

                val whiteNoise = nextRandom()
                lowPass += f * bandPass
                val highPass = whiteNoise - lowPass - q * bandPass
                bandPass += f * highPass

                // Parabolic amplitude bell envelope
                val env = (4.0 * t * (1.0 - t)).coerceIn(0.0, 1.0)

                // Gentle tone component for body
                val toneFreq = 500.0 - 240.0 * t
                tonePhase += toneFreq / SAMPLE_RATE
                if (tonePhase >= 1.0) tonePhase -= 1.0
                val bodyTone = sin(2 * PI * tonePhase) * 0.3

                val sample = ((bandPass * 0.7 + bodyTone) * env).coerceIn(-1.0, 1.0)
                pcmData[i] = (sample * 28000).toInt().toShort()
            }

            return createWavFile(pcmData, SAMPLE_RATE)
        }

        /**
         * Synthesizes seamless looping Lo-Fi Cozy Electronic Melody (4 bars at 88 BPM, ~5.45s).
         * Uses warm mellow triangle-like waveforms and walking bass for relaxed ambient financial browsing.
         */
        fun synthesizeLoFiBgmWav(): ByteArray {
            val bpm = 88.0
            val secondsPerBeat = 60.0 / bpm
            val beats = 8 // 2 bars in 4/4
            val totalSeconds = beats * secondsPerBeat
            val totalSamples = (SAMPLE_RATE * totalSeconds).toInt()
            val pcmData = ShortArray(totalSamples)

            // Chord roots & melody notes:
            // Bar 1 (Fmaj7 -> Em7): F3 (174.6Hz), A4 (440.0Hz), C5 (523.3Hz), E5 (659.3Hz)
            // Bar 2 (Dm7 -> Cmaj7): D3 (146.8Hz), F4 (349.2Hz), A4 (440.0Hz), C5 (523.3Hz)
            val melodyNotes = doubleArrayOf(
                659.25, 0.0, 523.25, 587.33,  // E5, rest, C5, D5
                440.00, 523.25, 392.00, 0.0   // A4, C5, G4, rest
            )

            val bassNotes = doubleArrayOf(
                174.61, 174.61, 164.81, 164.81, // F3, F3, E3, E3
                146.83, 146.83, 130.81, 130.81  // D3, D3, C3, C3
            )

            val samplesPerBeat = (SAMPLE_RATE * secondsPerBeat).toInt()

            var melPhase = 0.0
            var bassPhase = 0.0
            var chordPhase1 = 0.0
            var chordPhase2 = 0.0

            for (i in 0 until totalSamples) {
                val beatIndex = (i / samplesPerBeat).coerceIn(0, beats - 1)
                val beatOffset = i % samplesPerBeat
                val beatProgress = beatOffset.toDouble() / samplesPerBeat

                // 1. Melody (Soft warm triangle wave with gentle decay)
                val melFreq = melodyNotes[beatIndex]
                val melSample = if (melFreq > 0.0) {
                    melPhase += melFreq / SAMPLE_RATE
                    if (melPhase >= 1.0) melPhase -= 1.0
                    val tri = 2.0 * kotlin.math.abs(2.0 * (melPhase - kotlin.math.floor(melPhase + 0.5))) - 1.0
                    val env = exp(-2.2 * beatProgress)
                    tri * env * 0.38
                } else {
                    0.0
                }

                // 2. Bassline (Deep mellow sine wave)
                val bassFreq = bassNotes[beatIndex]
                bassPhase += bassFreq / SAMPLE_RATE
                if (bassPhase >= 1.0) bassPhase -= 1.0
                val bassEnv = exp(-1.2 * (beatOffset.toDouble() / (samplesPerBeat * 2)))
                val bassSample = sin(2 * PI * bassPhase) * 0.32 * bassEnv

                // 3. Gentle Lo-Fi Ambient Pad Chords (soft sine shimmer)
                val padFreq1 = if (beatIndex < 4) 261.63 else 220.00 // C4 or A3
                val padFreq2 = if (beatIndex < 4) 329.63 else 261.63 // E4 or C4
                chordPhase1 += padFreq1 / SAMPLE_RATE
                if (chordPhase1 >= 1.0) chordPhase1 -= 1.0
                chordPhase2 += padFreq2 / SAMPLE_RATE
                if (chordPhase2 >= 1.0) chordPhase2 -= 1.0
                val padSample = (sin(2 * PI * chordPhase1) + sin(2 * PI * chordPhase2)) * 0.12

                // Smooth edge crossfade at loop boundaries (first & last 50ms) to ensure seamless loop
                val loopFadeSamples = (SAMPLE_RATE * 0.05).toInt()
                val boundaryFactor = when {
                    i < loopFadeSamples -> i.toDouble() / loopFadeSamples
                    i > totalSamples - loopFadeSamples -> (totalSamples - i).toDouble() / loopFadeSamples
                    else -> 1.0
                }

                val mixed = (melSample + bassSample + padSample) * boundaryFactor
                pcmData[i] = (mixed.coerceIn(-1.0, 1.0) * 26000).toInt().toShort()
            }

            return createWavFile(pcmData, SAMPLE_RATE)
        }

        /**
         * Synthesizes upbeat, energetic chiptune intro tune for the Onboarding Launch Carousel.
         * (~3.8 seconds seamless loop at 126 BPM).
         */
        fun synthesizeCarouselIntroWav(): ByteArray {
            val bpm = 126.0
            val secondsPerBeat = 60.0 / bpm
            val beats = 8
            val totalSeconds = beats * secondsPerBeat
            val totalSamples = (SAMPLE_RATE * totalSeconds).toInt()
            val pcmData = ShortArray(totalSamples)

            // Bright upbeat arpeggio: C5 -> E5 -> G5 -> C6 -> A5 -> F5 -> D5 -> G5
            val arpNotes = doubleArrayOf(
                523.25, 659.25, 783.99, 1046.50,
                880.00, 698.46, 587.33, 783.99
            )
            val bassNotes = doubleArrayOf(
                130.81, 130.81, 196.00, 196.00,
                174.61, 174.61, 146.83, 196.00
            )

            val samplesPerBeat = (SAMPLE_RATE * secondsPerBeat).toInt()
            var arpPhase = 0.0
            var bassPhase = 0.0

            for (i in 0 until totalSamples) {
                val beatIndex = (i / samplesPerBeat).coerceIn(0, beats - 1)
                val beatOffset = i % samplesPerBeat
                val beatProgress = beatOffset.toDouble() / samplesPerBeat

                val arpFreq = arpNotes[beatIndex]
                arpPhase += arpFreq / SAMPLE_RATE
                if (arpPhase >= 1.0) arpPhase -= 1.0
                val square = if (arpPhase < 0.45) 0.55 else -0.55
                val arpEnv = exp(-3.0 * beatProgress)
                val arpSample = square * arpEnv * 0.42

                val bassFreq = bassNotes[beatIndex]
                bassPhase += bassFreq / SAMPLE_RATE
                if (bassPhase >= 1.0) bassPhase -= 1.0
                val bassSquare = if (bassPhase < 0.5) 0.45 else -0.45
                val bassEnv = exp(-2.0 * beatProgress)
                val bassSample = bassSquare * bassEnv * 0.28

                val loopFadeSamples = (SAMPLE_RATE * 0.04).toInt()
                val boundaryFactor = when {
                    i < loopFadeSamples -> i.toDouble() / loopFadeSamples
                    i > totalSamples - loopFadeSamples -> (totalSamples - i).toDouble() / loopFadeSamples
                    else -> 1.0
                }

                val mixed = (arpSample + bassSample) * boundaryFactor
                pcmData[i] = (mixed.coerceIn(-1.0, 1.0) * 26000).toInt().toShort()
            }

            return createWavFile(pcmData, SAMPLE_RATE)
        }

        private fun createWavFile(pcmData: ShortArray, sampleRate: Int): ByteArray {
            val byteCount = pcmData.size * 2
            val totalLength = 44 + byteCount
            val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
            buffer.putInt(totalLength - 8)
            buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

            // fmt subchunk
            buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
            buffer.putInt(16) // Subchunk1Size for PCM
            buffer.putShort(1.toShort()) // AudioFormat 1 = PCM
            buffer.putShort(1.toShort()) // NumChannels = 1 (Mono)
            buffer.putInt(sampleRate) // SampleRate
            buffer.putInt(sampleRate * 2) // ByteRate (SampleRate * NumChannels * BitsPerSample/8)
            buffer.putShort(2.toShort()) // BlockAlign (NumChannels * BitsPerSample/8)
            buffer.putShort(16.toShort()) // BitsPerSample = 16

            // data subchunk
            buffer.put("data".toByteArray(Charsets.US_ASCII))
            buffer.putInt(byteCount)

            for (sample in pcmData) {
                buffer.putShort(sample)
            }

            return buffer.array()
        }
    }
}

val LocalRetroSoundManager = compositionLocalOf<RetroSoundManager?> { null }
