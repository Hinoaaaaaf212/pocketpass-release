package com.pocketpass.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.compositionLocalOf
import com.pocketpass.app.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

val LocalSoundManager = compositionLocalOf<SoundManager> {
    error("No SoundManager provided")
}

class SoundManager(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val userPreferences = UserPreferences(context)
    private val appContext = context.applicationContext

    @Volatile private var sfxEnabled: Boolean = true
    @Volatile private var sfxVolume: Float = 0.5f

    private val soundIds = mutableMapOf<String, Int>()
    @Volatile private var buffersReady = false

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(audioAttributes)
        .build()

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val FADE_SAMPLES = 220 // ~5ms fade at 44100Hz
    }

    init {
        // Load preferences asynchronously
        scope.launch {
            sfxEnabled = userPreferences.sfxEnabledFlow.first()
            sfxVolume = userPreferences.sfxVolumeFlow.first()
        }
        scope.launch {
            userPreferences.sfxEnabledFlow.collect { sfxEnabled = it }
        }
        scope.launch {
            userPreferences.sfxVolumeFlow.collect { sfxVolume = it }
        }

        // Generate PCM buffers, convert to WAV, load into SoundPool
        // WAV files are cached between launches — only regenerated if missing
        scope.launch {
            val soundDefs = mapOf(
                "tap" to { generateTone(880f, 50) },
                "navigate" to { generateSweep(1046f, 1318f, 80) },
                "back" to { generateSweep(659f, 523f, 80) },
                "success" to { generateArpeggio(floatArrayOf(523f, 659f, 784f, 1046f), 300) },
                "error" to { generateSquareWave(330f, 150) },
                "toggleOn" to { generateSweep(660f, 880f, 60) },
                "toggleOff" to { generateSweep(880f, 660f, 60) },
                "delete" to { generateSweep(440f, 330f, 100) },
                "encounter" to { generateArpeggio(floatArrayOf(784f, 988f, 1318f, 1568f), 400) },
                "select" to { generateTone(1046f, 40) },
                "messageReceived" to { generateArpeggio(floatArrayOf(880f, 1046f, 1318f), 200) },
                "notification" to { generateSweep(784f, 1046f, 120) }
            )

            for ((name, generator) in soundDefs) {
                val file = File(appContext.cacheDir, "sfx_$name.wav")
                if (!file.exists()) {
                    val wavBytes = pcmToWavBytes(generator())
                    FileOutputStream(file).use { it.write(wavBytes) }
                }
                val id = soundPool.load(file.absolutePath, 1)
                soundIds[name] = id
            }

            soundPool.setOnLoadCompleteListener { _, _, _ ->
                if (soundIds.size == soundDefs.size) {
                    buffersReady = true
                }
            }
        }
    }

    // --- Public playback methods ---

    fun playTap() { play("tap") }
    fun playNavigate() { play("navigate") }
    fun playBack() { play("back") }
    fun playSuccess() { play("success") }
    fun playError() { play("error") }
    fun playToggleOn() { play("toggleOn") }
    fun playToggleOff() { play("toggleOff") }
    fun playDelete() { play("delete") }
    fun playEncounter() { play("encounter") }
    fun playSelect() { play("select") }
    fun playMessageReceived() { play("messageReceived") }
    fun playNotification() { play("notification") }

    fun release() {
        soundPool.release()
    }

    // --- Audio generation ---

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

            val posInNote = i % samplesPerNote
            val noteEnvelope = applyEnvelope(posInNote, samplesPerNote)
            val globalEnvelope = applyEnvelope(i, totalSamples)

            buffer[i] = (sample * Short.MAX_VALUE * noteEnvelope * globalEnvelope).toInt().toShort()
        }
        return buffer
    }

    private fun generateSquareWave(frequency: Float, durationMs: Int): ShortArray {
        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toFloat() / SAMPLE_RATE
            val sineVal = sin(2.0 * PI * frequency * t)
            val sample = if (sineVal >= 0) 0.6f else -0.6f
            buffer[i] = (sample * Short.MAX_VALUE * applyEnvelope(i, numSamples)).toInt().toShort()
        }
        return buffer
    }

    private fun applyEnvelope(sampleIndex: Int, totalSamples: Int): Float {
        val fadeLen = min(FADE_SAMPLES, totalSamples / 4)
        return when {
            sampleIndex < fadeLen -> sampleIndex.toFloat() / fadeLen
            sampleIndex > totalSamples - fadeLen -> (totalSamples - sampleIndex).toFloat() / fadeLen
            else -> 1f
        }
    }

    // --- Playback ---

    private fun play(soundName: String) {
        if (!sfxEnabled || sfxVolume <= 0f || !buffersReady) return
        val id = soundIds[soundName] ?: return
        val vol = sfxVolume * 0.25f
        soundPool.play(id, vol, vol, 1, 0, 1.0f)
    }

    // --- WAV conversion ---

    private fun pcmToWavBytes(pcmData: ShortArray): ByteArray {
        val pcmBytes = pcmData.size * 2
        val totalSize = 44 + pcmBytes
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(totalSize - 8)
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // fmt sub-chunk
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16)           // Sub-chunk size
        buffer.putShort(1)          // PCM format
        buffer.putShort(1)          // Mono
        buffer.putInt(SAMPLE_RATE)  // Sample rate
        buffer.putInt(SAMPLE_RATE * 2) // Byte rate (sampleRate * channels * bitsPerSample/8)
        buffer.putShort(2)          // Block align (channels * bitsPerSample/8)
        buffer.putShort(16)         // Bits per sample

        // data sub-chunk
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(pcmBytes)

        for (sample in pcmData) {
            buffer.putShort(sample)
        }

        return buffer.array()
    }
}
