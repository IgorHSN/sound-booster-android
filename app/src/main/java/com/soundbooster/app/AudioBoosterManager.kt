package com.soundbooster.app

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import android.view.KeyEvent
import java.util.Timer
import java.util.TimerTask

class AudioBoosterManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioBoosterManager"
        private const val MAX_GAIN_MB = 6000
        private const val WATCHDOG_INTERVAL_MS = 3000L
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var watchdogTimer: Timer? = null

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
        // One-time restart so the active player reconnects to the effect chain.
        // Only here — never on slider changes or disable.
        dispatchRestartPlayback()
        startWatchdog()
        Log.d(TAG, "Boost enabled at $boostPercent% (${calcGainMb(boostPercent)} mB)")
    }

    fun disable() {
        isEnabled = false
        stopWatchdog()
        try { loudnessEnhancer?.enabled = false } catch (_: Exception) {}
        // No restart on disable — music keeps playing uninterrupted
        Log.d(TAG, "Boost disabled")
    }

    fun setBoostLevel(percent: Int) {
        boostPercent = percent.coerceIn(0, 100)
        if (isEnabled) applyGainOnly() // no restart, real-time gain change
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
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        loudnessEnhancer = null
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun createEnhancer() {
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        loudnessEnhancer = null
        try {
            loudnessEnhancer = LoudnessEnhancer(0)
            Log.d(TAG, "LoudnessEnhancer created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create LoudnessEnhancer: ${e.message}")
        }
    }

    // Full apply: used on enable() — sets gain then enables
    private fun applyGain() {
        applyGainOnly()
    }

    // Gain-only: used on slider changes — no side effects
    private fun applyGainOnly() {
        val le = loudnessEnhancer ?: return
        try {
            val gainMb = calcGainMb(boostPercent)
            le.setTargetGain(gainMb)
            le.enabled = boostPercent > 0
            Log.d(TAG, "Gain: $gainMb mB")
        } catch (e: Exception) {
            // Effect was released by the system — recreate and restart once
            Log.w(TAG, "Effect died, recreating: ${e.message}")
            createEnhancer()
            try {
                loudnessEnhancer?.setTargetGain(calcGainMb(boostPercent))
                loudnessEnhancer?.enabled = boostPercent > 0
                dispatchRestartPlayback() // need one restart for the new effect to connect
            } catch (e2: Exception) {
                Log.e(TAG, "Recreate failed: ${e2.message}")
            }
        }
    }

    // Watchdog: checks every 3s that the effect is still alive.
    // Recreates silently if it died between slider changes.
    private fun startWatchdog() {
        stopWatchdog()
        watchdogTimer = Timer("BoostWatchdog", true).also { t ->
            t.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (!isEnabled) return
                    try {
                        val le = loudnessEnhancer
                        if (le == null) {
                            Log.d(TAG, "Watchdog: enhancer null, recreating")
                            createEnhancer()
                            applyGainOnly()
                            dispatchRestartPlayback()
                            return
                        }
                        // Probe the effect — throws if released by system
                        le.setTargetGain(calcGainMb(boostPercent))
                        if (!le.enabled) le.enabled = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Watchdog: effect dead, recreating: ${e.message}")
                        createEnhancer()
                        try {
                            loudnessEnhancer?.setTargetGain(calcGainMb(boostPercent))
                            loudnessEnhancer?.enabled = true
                            dispatchRestartPlayback()
                        } catch (_: Exception) {}
                    }
                }
            }, WATCHDOG_INTERVAL_MS, WATCHDOG_INTERVAL_MS)
        }
    }

    private fun stopWatchdog() {
        watchdogTimer?.cancel()
        watchdogTimer = null
    }

    // Pause→Play forces the active player to drop and reopen its audio session,
    // connecting to the session-0 effect chain. Called only when strictly needed.
    private fun dispatchRestartPlayback() {
        try {
            listOf(
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY
            ).forEach { keyCode ->
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            Log.d(TAG, "Restart dispatched")
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
