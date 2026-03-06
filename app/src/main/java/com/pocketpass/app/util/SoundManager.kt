package com.pocketpass.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.compositionLocalOf
import com.pocketpass.app.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

val LocalSoundManager = compositionLocalOf<SoundManager> {
    error("No SoundManager provided")
}

class SoundManager(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val userPreferences = UserPreferences(context)

    private var sfxEnabled: Boolean = true
    private var sfxVolume: Float = 0.5f

    // Pre-generated PCM buffers
    private val tapBuffer: ShortArray
    private val navigateBuffer: ShortArray
    private val backBuffer: ShortArray
    private val successBuffer: ShortArray
    private val errorBuffer: ShortArray
    private val toggleOnBuffer: ShortArray
    private val toggleOffBuffer: ShortArray
    private val deleteBuffer: ShortArray
    private val encounterBuffer: ShortArray
    private val selectBuffer: ShortArray

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val FADE_SAMPLES = 220 // ~5ms fade at 44100Hz
    }

    init {
        // Load initial preferences
        scope.launch {
            sfxEnabled = userPreferences.sfxEnabledFlow.first()
            sfxVolume = userPreferences.sfxVolumeFlow.first()
        }

        // Observe preference changes
        scope.launch {
            userPreferences.sfxEnabledFlow.collect { sfxEnabled = it }
        }
        scope.launch {
            userPreferences.sfxVolumeFlow.collect { sfxVolume = it }
        }

        // Pre-generate all sound buffers
        tapBuffer = generateTone(880f, 50)
        navigateBuffer = generateSweep(1046f, 1318f, 80)
        backBuffer = generateSweep(659f, 523f, 80)
        successBuffer = generateArpeggio(floatArrayOf(523f, 659f, 784f, 1046f), 300)
        errorBuffer = generateSquareWave(330f, 150)
        toggleOnBuffer = generateSweep(660f, 880f, 60)
        toggleOffBuffer = generateSweep(880f, 660f, 60)
        deleteBuffer = generateSweep(440f, 330f, 100)
        encounterBuffer = generateArpeggio(floatArrayOf(784f, 988f, 1318f, 1568f), 400)
        selectBuffer = generateTone(1046f, 40)
    }

    // --- Public playback methods ---

    fun playTap() = play(tapBuffer)
    fun playNavigate() = play(navigateBuffer)
    fun playBack() = play(backBuffer)
    fun playSuccess() = play(successBuffer)
    fun playError() = play(errorBuffer)
    fun playToggleOn() = play(toggleOnBuffer)
    fun playToggleOff() = play(toggleOffBuffer)
    fun playDelete() = play(deleteBuffer)
    fun playEncounter() = play(encounterBuffer)
    fun playSelect() = play(selectBuffer)

    // --- Audio generation ---

    /**
     * Generate a single sine tone at a fixed frequency.
     */
    private fun generateTone(frequency: Float, durationMs: Int): ShortArray {
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val sample = sin(2.0 * PI * frequency * t).toFloat()
            buffer[i] = (sample * Short.MAX_VALUE * applyEnvelope(i, numSamples)).toInt().toShort()
        }
        return buffer
    }

    /**
     * Generate a frequency sweep (glide) from startFreq to endFreq.
     */
    private fun generateSweep(startFreq: Float, endFreq: Float, durationMs: Int): ShortArray {
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        var phase = 0.0
        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val freq = startFreq + (endFreq - startFreq) * progress
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sample = sin(phase).toFloat()
            buffer[i] = (sample * Short.MAX_VALUE * applyEnvelope(i, numSamples)).toInt().toShort()
        }
        return buffer
    }

    /**
     * Generate an arpeggio — quick sequence of notes.
     */
    private fun generateArpeggio(frequencies: FloatArray, totalDurationMs: Int): ShortArray {
        val totalSamples = (SAMPLE_RATE * totalDurationMs) / 1000
        val samplesPerNote = totalSamples / frequencies.size
        val buffer = ShortArray(totalSamples)
        var phase = 0.0

        for (i in 0 until totalSamples) {
            val noteIndex = min(i / samplesPerNote, frequencies.size - 1)
            val freq = frequencies[noteIndex]
            phase += 2.0 * PI * freq / SAMPLE_RATE
            val sample = sin(phase).toFloat()

            // Per-note envelope for clean transitions
            val posInNote = i % samplesPerNote
            val noteEnvelope = applyEnvelope(posInNote, samplesPerNote)
            // Global envelope for overall fade
            val globalEnvelope = applyEnvelope(i, totalSamples)

            buffer[i] = (sample * Short.MAX_VALUE * noteEnvelope * globalEnvelope).toInt().toShort()
        }
        return buffer
    }

    /**
     * Generate a square wave tone (buzzy/retro sound for errors).
     */
    private fun generateSquareWave(frequency: Float, durationMs: Int): ShortArray {
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val sineVal = sin(2.0 * PI * frequency * t)
            // Square wave: just the sign of the sine
            val sample = if (sineVal >= 0) 0.6f else -0.6f
            buffer[i] = (sample * Short.MAX_VALUE * applyEnvelope(i, numSamples)).toInt().toShort()
        }
        return buffer
    }

    /**
     * Fade-in/out envelope to prevent audio clicks.
     */
    private fun applyEnvelope(sampleIndex: Int, totalSamples: Int): Float {
        val fadeLen = min(FADE_SAMPLES, totalSamples / 4)
        return when {
            sampleIndex < fadeLen -> sampleIndex.toFloat() / fadeLen
            sampleIndex > totalSamples - fadeLen -> (totalSamples - sampleIndex).toFloat() / fadeLen
            else -> 1f
        }
    }

    // --- Playback ---

    private fun play(buffer: ShortArray) {
        if (!sfxEnabled || sfxVolume <= 0f) return

        scope.launch {
            try {
                val bufferSizeBytes = buffer.size * 2 // 16-bit = 2 bytes per sample
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSizeBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.setVolume(sfxVolume)
                track.play()

                // Wait for playback to finish, then release
                val durationMs = (buffer.size * 1000L) / SAMPLE_RATE
                kotlinx.coroutines.delay(durationMs + 50)
                track.stop()
                track.release()
            } catch (e: Exception) {
                // Silently ignore audio errors - don't crash the app for a sound effect
            }
        }
    }
}
