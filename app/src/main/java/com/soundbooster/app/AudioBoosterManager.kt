package com.soundbooster.app

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import android.view.KeyEvent

class AudioBoosterManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioBoosterManager"
        // 1500 mB = 15 dB: audible boost without DRC compression artifacts
        private const val MAX_GAIN_MB = 1500
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var loudnessEnhancer: LoudnessEnhancer? = null

    var isEnabled = false
        private set
    private var boostPercent = 0

    fun init() {
        createEnhancer()
    }

    fun enable() {
        isEnabled = true
        setSystemVolumeMax()
        applyGain()
        // Force the active media player to restart its audio session so it
        // picks up the LoudnessEnhancer that is now active on session 0.
        // Without this, the effect exists but the player ignores it because
        // its audio session was already open before the effect was created.
        dispatchRestartPlayback()
        Log.d(TAG, "Boost enabled at $boostPercent% (${calcGainMb(boostPercent)} mB)")
    }

    fun disable() {
        isEnabled = false
        try { loudnessEnhancer?.enabled = false } catch (e: Exception) {
            Log.w(TAG, "disable: ${e.message}")
        }
        dispatchRestartPlayback()
        Log.d(TAG, "Boost disabled")
    }

    fun setBoostLevel(percent: Int) {
        boostPercent = percent.coerceIn(0, 100)
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
        try { loudnessEnhancer?.release() } catch (e: Exception) {
            Log.w(TAG, "release: ${e.message}")
        }
        loudnessEnhancer = null
    }

    private fun createEnhancer() {
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        loudnessEnhancer = null
        try {
            loudnessEnhancer = LoudnessEnhancer(0)
            Log.d(TAG, "LoudnessEnhancer created on session 0")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create LoudnessEnhancer: ${e.message}")
        }
    }

    private fun applyGain() {
        val le = loudnessEnhancer ?: run { createEnhancer(); loudnessEnhancer } ?: return
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
                Log.e(TAG, "applyGain retry: ${e2.message}")
            }
        }
    }

    // Pause → Play → Pause → Play (twice) forces the active media player to
    // drop and reopen its audio session, connecting to the session-0 effect chain.
    private fun dispatchRestartPlayback() {
        try {
            val keys = listOf(
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY
            )
            for (keyCode in keys) {
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            Log.d(TAG, "Restart playback dispatched")
        } catch (e: Exception) {
            Log.w(TAG, "dispatchRestartPlayback: ${e.message}")
        }
    }

    private fun calcGainMb(percent: Int) = percent * MAX_GAIN_MB / 100

    private fun setSystemVolumeMax() {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
        } catch (e: Exception) {
            Log.w(TAG, "setSystemVolumeMax: ${e.message}")
        }
    }
}
