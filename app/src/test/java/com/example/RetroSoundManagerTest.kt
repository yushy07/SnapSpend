package com.example

import com.example.ui.sound.RetroSoundManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit tests verifying that the 8-bit retro arcade synthesizer produces valid, non-corrupt
 * RIFF/WAVE byte buffers with correct sample rates, channels, and headers.
 */
class RetroSoundManagerTest {

    private fun assertValidWav(bytes: ByteArray, minDurationSeconds: Double) {
        assertTrue("WAV byte array must be at least 44 bytes header", bytes.size > 44)

        // RIFF header
        val riff = String(bytes, 0, 4, Charsets.US_ASCII)
        assertEquals("RIFF", riff)

        val wave = String(bytes, 8, 4, Charsets.US_ASCII)
        assertEquals("WAVE", wave)

        val fmt = String(bytes, 12, 4, Charsets.US_ASCII)
        assertEquals("fmt ", fmt)

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val chunkSize = buffer.getInt(4)
        assertEquals(bytes.size - 8, chunkSize)

        val subchunk1Size = buffer.getInt(16)
        assertEquals(16, subchunk1Size)

        val audioFormat = buffer.getShort(20).toInt()
        assertEquals(1, audioFormat) // PCM

        val numChannels = buffer.getShort(22).toInt()
        assertEquals(1, numChannels) // Mono

        val sampleRate = buffer.getInt(24)
        assertEquals(22050, sampleRate)

        val bitsPerSample = buffer.getShort(34).toInt()
        assertEquals(16, bitsPerSample)

        val dataTag = String(bytes, 36, 4, Charsets.US_ASCII)
        assertEquals("data", dataTag)

        val dataSize = buffer.getInt(40)
        assertEquals(bytes.size - 44, dataSize)

        val samplesCount = dataSize / 2
        val durationSeconds = samplesCount.toDouble() / sampleRate
        assertTrue(
            "Duration should be at least $minDurationSeconds s, was $durationSeconds s",
            durationSeconds >= minDurationSeconds
        )
    }

    @Test
    fun testSynthesizeCoinWav() {
        val bytes = RetroSoundManager.synthesizeCoinWav()
        assertNotNull(bytes)
        assertValidWav(bytes, 0.25)
    }

    @Test
    fun testSynthesizeBlipWav() {
        val bytes = RetroSoundManager.synthesizeBlipWav()
        assertNotNull(bytes)
        assertValidWav(bytes, 0.03)
    }

    @Test
    fun testSynthesizeSuccessWav() {
        val bytes = RetroSoundManager.synthesizeSuccessWav()
        assertNotNull(bytes)
        assertValidWav(bytes, 0.25)
    }

    @Test
    fun testSynthesizeDeleteWav() {
        val bytes = RetroSoundManager.synthesizeDeleteWav()
        assertNotNull(bytes)
        assertValidWav(bytes, 0.08)
    }

    @Test
    fun testSynthesizeErrorWav() {
        val bytes = RetroSoundManager.synthesizeErrorWav()
        assertNotNull(bytes)
        assertValidWav(bytes, 0.12)
    }

    @Test
    fun testSynthesizeSwooshWav() {
        val bytes = RetroSoundManager.synthesizeSwooshWav()
        assertNotNull(bytes)
        assertValidWav(bytes, 0.08)
    }

    @Test
    fun testSynthesizeLoFiBgmWav() {
        val bytes = RetroSoundManager.synthesizeLoFiBgmWav()
        assertNotNull(bytes)
        assertValidWav(bytes, 4.0)
    }

    @Test
    fun testSynthesizeCarouselIntroWav() {
        val bytes = RetroSoundManager.synthesizeCarouselIntroWav()
        assertNotNull(bytes)
        assertValidWav(bytes, 3.0)
    }
}
