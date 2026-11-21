package com.example.system

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Random
import kotlin.math.sin

enum class WhiteNoiseType(val id: String, val label: String, val desc: String) {
    RAIN("rain", "细雨落窗", "柔和粉红噪声 + 雨滴滤波"),
    WIND("wind", "山林夜风", "慢速潮汐包络风声"),
    CAMPFIRE("campfire", "篝火噼啪", "暖棕底噪 + 轻脆木柴声"),
    BROWN("brown", "图书馆棕噪", "低频屏蔽宿舍人声干扰"),
    OCEAN("ocean", "海浪潮汐", "周期舒缓潮汐律动")
}

data class WhiteNoiseState(
    val isPlaying: Boolean = false,
    val activeType: WhiteNoiseType = WhiteNoiseType.RAIN,
    val volume: Float = 0.45f
)

/**
 * Ultra-low-RAM, zero-asset real-time DSP White Noise synthesizer using Android AudioTrack (API 21+).
 * Consumes ~0 KB storage and minimal CPU on MT6580 hardware.
 */
class WhiteNoiseEngine {
    private val _state = MutableStateFlow(WhiteNoiseState())
    val state: StateFlow<WhiteNoiseState> = _state.asStateFlow()

    @Volatile
    private var running = false

    @Volatile
    private var currentType = WhiteNoiseType.RAIN

    @Volatile
    private var currentVolume = 0.45f

    private var workerThread: Thread? = null

    fun toggle(type: WhiteNoiseType = currentType) {
        if (running && currentType == type) {
            stop()
        } else {
            start(type)
        }
    }

    fun start(type: WhiteNoiseType = currentType) {
        currentType = type
        _state.value = _state.value.copy(isPlaying = true, activeType = type)
        if (running) return

        running = true
        workerThread = Thread {
            runDspLoop()
        }.apply {
            name = "PenOS-WhiteNoiseDSP"
            priority = Thread.MIN_PRIORITY + 1
            start()
        }
    }

    fun stop() {
        running = false
        workerThread?.interrupt()
        workerThread = null
        _state.value = _state.value.copy(isPlaying = false)
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0.05f, 1f)
        currentVolume = clamped
        _state.value = _state.value.copy(volume = clamped)
    }

    @Suppress("DEPRECATION")
    private fun runDspLoop() {
        val sampleRate = 16000
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        var track: AudioTrack? = null
        try {
            track = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2,
                AudioTrack.MODE_STREAM
            )
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                running = false
                _state.value = _state.value.copy(isPlaying = false)
                return
            }
            track.play()

            val buffer = ShortArray(1024)
            val rng = Random()
            var b0 = 0.0
            var b1 = 0.0
            var b2 = 0.0
            var brown = 0.0
            var phase = 0.0

            while (running && !Thread.currentThread().isInterrupted) {
                val mode = currentType
                val gain = currentVolume * 0.55
                for (i in buffer.indices) {
                    val white = rng.nextDouble() * 2.0 - 1.0
                    phase += 1.0 / sampleRate

                    val sample = when (mode) {
                        WhiteNoiseType.RAIN -> {
                            b0 = 0.997 * b0 + white * 0.029
                            b1 = 0.985 * b1 + white * 0.032
                            b2 = 0.950 * b2 + white * 0.048
                            val pink = (b0 + b1 + b2 + white * 0.08) * 1.4
                            val droplet = if (rng.nextInt(1400) == 0) (rng.nextDouble() - 0.5) * 0.65 else 0.0
                            pink + droplet
                        }
                        WhiteNoiseType.WIND -> {
                            brown = (brown + 0.025 * white) / 1.025
                            val swell = 0.45 + 0.55 * (0.5 + 0.5 * sin(phase * 0.55))
                            brown * 4.2 * swell
                        }
                        WhiteNoiseType.CAMPFIRE -> {
                            brown = (brown + 0.03 * white) / 1.03
                            val crackle = if (rng.nextInt(650) == 0) (rng.nextDouble() * 2.0 - 1.0) * 0.85 else 0.0
                            brown * 3.2 + crackle
                        }
                        WhiteNoiseType.BROWN -> {
                            brown = (brown + 0.02 * white) / 1.02
                            brown * 4.5
                        }
                        WhiteNoiseType.OCEAN -> {
                            b0 = 0.992 * b0 + white * 0.045
                            val wave = 0.25 + 0.75 * (0.5 + 0.5 * sin(phase * 0.78))
                            b0 * 2.8 * wave
                        }
                    }

                    val scaled = (sample * gain * 32767.0).coerceIn(-32000.0, 32000.0)
                    buffer[i] = scaled.toInt().toShort()
                }
                track.write(buffer, 0, buffer.size)
            }
        } catch (_: Throwable) {
            // Safe exit on audio focus or emulator without PCM sink
        } finally {
            try {
                track?.stop()
                track?.release()
            } catch (_: Exception) {
            }
            running = false
            _state.value = _state.value.copy(isPlaying = false)
        }
    }
}
