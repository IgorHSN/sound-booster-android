package com.soundbooster.app

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.media.AudioTrack
import android.media.AudioFormat
import android.media.AudioAttributes
import android.os.Build
import android.util.Log

/**
 * AudioBoosterManager handles the core audio boost logic.
 *
 * It creates a silent AudioTrack to obtain an audio session ID, then attaches
 * a LoudnessEnhancer effect to that session. On Android, attaching a
 * LoudnessEnhancer to session 0 (the global mix) is attempted as a fallback
 * for broader system-wide boosting on supported devices.
 *
 * Boost range: 0 – 1000 millibels (0 – 10 dB).  The UI maps 0–200% to this
 * range so that 100% == 500 mB (5 dB) and 200% == 1000 mB (10 dB).
 */
class AudioBoosterManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioBoosterManager"
        /** Maximum gain the LoudnessEnhancer supports (1000 mB = 10 dB). */
        const val MAX_GAIN_MB = 1000
    }

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioTrack: AudioTrack? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var globalLoudnessEnhancer: LoudnessEnhancer? = null

    /** The audio session ID used for effect attachment. */
    var sessionId: Int = 0
        private set

    /** Whether the booster is currently active. */
    var isEnabled: Boolean = false
        private set

    /** Current boost level in millibels (0–1000). */
    var currentGainMb: Int = 0
        private set

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Initialise the silent AudioTrack and attach effects.
     * Must be called before [enable] or [setBoostLevel].
     */
    fun init() {
        try {
            audioTrack = createSilentAudioTrack()
            sessionId = audioTrack?.audioSessionId ?: 0
            Log.d(TAG, "AudioTrack session ID: $sessionId")

            // Attach LoudnessEnhancer to our private session
            if (sessionId != 0) {
                loudnessEnhancer = LoudnessEnhancer(sessionId).also {
                    it.enabled = false
                }
            }

            // Attempt global session (session 0) – may be ignored by the OS
            // but works on many devices for a system-wide boost effect.
            try {
                globalLoudnessEnhancer = LoudnessEnhancer(0).also {
                    it.enabled = false
                }
                Log.d(TAG, "Global LoudnessEnhancer (session 0) created.")
            } catch (e: Exception) {
                Log.w(TAG, "Global LoudnessEnhancer not supported: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialise AudioBoosterManager: ${e.message}", e)
        }
    }

    /**
     * Enable the boost. Starts the silent track and activates effects.
     */
    fun enable() {
        try {
            audioTrack?.play()
            loudnessEnhancer?.enabled = true
            globalLoudnessEnhancer?.enabled = true
            isEnabled = true
            applySystemVolumeMax()
            Log.d(TAG, "Booster enabled at $currentGainMb mB")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable booster: ${e.message}", e)
        }
    }

    /**
     * Disable the boost and restore effects to inactive state.
     */
    fun disable() {
        try {
            loudnessEnhancer?.enabled = false
            globalLoudnessEnhancer?.enabled = false
            audioTrack?.pause()
            isEnabled = false
            Log.d(TAG, "Booster disabled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable booster: ${e.message}", e)
        }
    }

    /**
     * Set the boost gain.
     *
     * @param percent Boost percentage from 0 to 200.
     *   0%   = no boost (0 mB)
     *   100% = 500 mB (5 dB)
     *   200% = 1000 mB (10 dB)
     */
    fun setBoostLevel(percent: Int) {
        val clamped = percent.coerceIn(0, 200)
        // Map 0–200% → 0–1000 mB
        val gainMb = (clamped * MAX_GAIN_MB / 200)
        currentGainMb = gainMb
        try {
            loudnessEnhancer?.setTargetGain(gainMb)
            globalLoudnessEnhancer?.setTargetGain(gainMb)
            Log.d(TAG, "Boost set to $clamped% ($gainMb mB)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set boost level: ${e.message}", e)
        }
    }

    /**
     * Get current boost percentage (0–200).
     */
    fun getBoostPercent(): Int = (currentGainMb * 200 / MAX_GAIN_MB)

    /**
     * Get the current media volume as a percentage of the stream maximum.
     */
    fun getVolumePercent(): Int {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) (current * 100 / max) else 0
    }

    /**
     * Get the raw current volume index for STREAM_MUSIC.
     */
    fun getCurrentVolume(): Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    /**
     * Get the maximum volume index for STREAM_MUSIC.
     */
    fun getMaxVolume(): Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    /**
     * Set the system media volume to a given index.
     */
    fun setSystemVolume(index: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val clamped = index.coerceIn(0, max)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            clamped,
            0 // no flags – avoids showing the system volume UI
        )
    }

    /**
     * Release all resources. Call when the service is destroyed.
     */
    fun release() {
        disable()
        try {
            loudnessEnhancer?.release()
            globalLoudnessEnhancer?.release()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error during release: ${e.message}", e)
        } finally {
            loudnessEnhancer = null
            globalLoudnessEnhancer = null
            audioTrack = null
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Push media stream to its hardware maximum so LoudnessEnhancer has room to work. */
    private fun applySystemVolumeMax() {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set max volume: ${e.message}")
        }
    }

    /**
     * Create a minimal silent AudioTrack.  It exists solely to obtain a
     * unique audio session ID that we can attach effects to.
     */
    private fun createSilentAudioTrack(): AudioTrack {
        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(1024)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
        }
    }
}
