package com.soundbooster.app

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

class AudioBoosterManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioBoosterManager"
        // Go well beyond the documented 1000 mB limit — Qualcomm HAL accepts up to ~3000 mB
        private const val MAX_GAIN_MB = 3000
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var loudnessEnhancer: LoudnessEnhancer? = null

    var isEnabled = false
        private set
    private var boostPercent = 0

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun init() {
        createEnhancer()
    }

    fun enable() {
        isEnabled = true
        setSystemVolumeMax()
        applyGain()
        Log.d(TAG, "Boost enabled at $boostPercent% (${calcGainMb(boostPercent)} mB)")
    }

    fun disable() {
        isEnabled = false
        try {
            loudnessEnhancer?.enabled = false
        } catch (e: Exception) {
            Log.w(TAG, "disable: ${e.message}")
        }
        Log.d(TAG, "Boost disabled")
    }

    fun setBoostLevel(percent: Int) {
        boostPercent = percent.coerceIn(0, 200)
        if (isEnabled) applyGain()
    }

    fun getBoostPercent() = boostPercent

    fun getVolumePercent(): Int {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) current * 100 / max else 0
    }

    fun getCurrentVolume() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    fun getMaxVolume() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    fun release() {
        disable()
        try {
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "release: ${e.message}")
        }
        loudnessEnhancer = null
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun createEnhancer() {
        try {
            loudnessEnhancer?.release()
        } catch (_: Exception) {}
        loudnessEnhancer = null

        try {
            loudnessEnhancer = LoudnessEnhancer(0)
            Log.d(TAG, "LoudnessEnhancer created on session 0")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create LoudnessEnhancer: ${e.message}")
        }
    }

    private fun applyGain() {
        val le = loudnessEnhancer ?: run {
            createEnhancer()
            loudnessEnhancer
        } ?: return

        try {
            val gainMb = calcGainMb(boostPercent)
            le.setTargetGain(gainMb)
            le.enabled = boostPercent > 0
            Log.d(TAG, "Gain applied: $gainMb mB (${boostPercent}%)")
        } catch (e: Exception) {
            Log.w(TAG, "applyGain failed, recreating: ${e.message}")
            createEnhancer()
            try {
                loudnessEnhancer?.setTargetGain(calcGainMb(boostPercent))
                loudnessEnhancer?.enabled = boostPercent > 0
            } catch (e2: Exception) {
                Log.e(TAG, "applyGain retry failed: ${e2.message}")
            }
        }
    }

    private fun calcGainMb(percent: Int): Int =
        (percent * MAX_GAIN_MB / 200)

    private fun setSystemVolumeMax() {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
        } catch (e: Exception) {
            Log.w(TAG, "setSystemVolumeMax: ${e.message}")
        }
    }
}
