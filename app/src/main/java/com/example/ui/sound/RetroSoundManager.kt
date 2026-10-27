package com.example.ui.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.compositionLocalOf
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sin

/**
 * 8-Bit Retro Arcade Sound Synthesizer & Manager for SnapSpend.
 * Synthesizes authentic chiptune PCM audio in memory with zero external assets or network calls,
 * caching them locally for zero-latency SoundPool playback.
 */
class RetroSoundManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private var isEnabled: Boolean = true

    private var coinSoundId: Int = 0
    private var blipSoundId: Int = 0
    private var successSoundId: Int = 0
    private var deleteSoundId: Int = 0
    private var errorSoundId: Int = 0

    private val loadedSoundIds = mutableSetOf<Int>()

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
                .setMaxStreams(4)
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
        val cacheDir = File(context.cacheDir, "retro_sounds").apply { mkdirs() }

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
        }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun isSoundEnabled(): Boolean = isEnabled

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

    private fun playSound(soundId: Int, volume: Float) {
        if (!isEnabled || soundId == 0) return
        runCatching {
            soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
        }
    }

    fun release() {
        runCatching {
            soundPool?.release()
            soundPool = null
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
                // slight stutter modulation at 25Hz
                val mod = if (sin(2 * Math.PI * 25 * progress) > 0) 1.0f else 0.4f
                val env = (1.0f - progress * 0.4f) * mod
                pcmData[i] = (combined * env * 32767).toInt().toShort()
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
