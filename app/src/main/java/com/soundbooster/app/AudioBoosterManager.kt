package com.soundbooster.app

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

class AudioBoosterManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioBoosterManager"
        private const val EFFECT_PRIORITY = Int.MAX_VALUE
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // Per-session effects: key = audioSessionId
    private val sessionEffects = mutableMapOf<Int, SessionEffects>()

    // Fallback session-0 effects for older Android
    private var globalEqualizer: Equalizer? = null
    private var globalLoudness: LoudnessEnhancer? = null
    private var globalBass: BassBoost? = null

    var isEnabled = false
        private set
    private var boostPercent = 0

    private val playbackCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                handlePlaybackConfigChange(configs)
            }
        }
    } else null

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun init() {
        // Register callback to detect active audio sessions from other apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playbackCallback != null) {
            audioManager.registerAudioPlaybackCallback(playbackCallback, null)
            // Attach to any already-playing sessions immediately
            syncActiveSessions()
        }

        // Always also try session 0 as a fallback
        initGlobalEffects()
    }

    fun enable() {
        isEnabled = true
        setSystemVolumeMax()
        applyToAll()
        Log.d(TAG, "Boost enabled at $boostPercent%")
    }

    fun disable() {
        isEnabled = false
        disableAll()
        Log.d(TAG, "Boost disabled")
    }

    fun setBoostLevel(percent: Int) {
        boostPercent = percent.coerceIn(0, 200)
        if (isEnabled) applyToAll()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playbackCallback != null) {
            audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        }
        disableAll()
        sessionEffects.values.forEach { it.release() }
        sessionEffects.clear()
        releaseGlobalEffects()
    }

    // -------------------------------------------------------------------------
    // Session tracking (API 26+)
    // -------------------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handlePlaybackConfigChange(configs: List<AudioPlaybackConfiguration>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            handlePlaybackConfigChangeApi31(configs)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun handlePlaybackConfigChangeApi31(configs: List<AudioPlaybackConfiguration>) {
        val activeIds = configs.map { it.audioSessionId }.toSet()

        // Remove effects for sessions that ended
        val toRemove = sessionEffects.keys.filter { it !in activeIds }
        toRemove.forEach { id ->
            sessionEffects.remove(id)?.release()
            Log.d(TAG, "Released effects for ended session $id")
        }

        // Add effects for new sessions
        activeIds.forEach { sessionId ->
            if (sessionId > 0 && sessionId !in sessionEffects) {
                val effects = SessionEffects.create(sessionId)
                if (effects != null) {
                    sessionEffects[sessionId] = effects
                    if (isEnabled) effects.apply(boostPercent)
                    Log.d(TAG, "Attached effects to new session $sessionId")
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun syncActiveSessions() {
        val configs = audioManager.activePlaybackConfigurations
        handlePlaybackConfigChange(configs)
    }

    // -------------------------------------------------------------------------
    // Apply / disable
    // -------------------------------------------------------------------------

    private fun applyToAll() {
        // Per-session effects (reliable on Android 8+)
        sessionEffects.values.forEach { it.apply(boostPercent) }

        // Global session 0 fallback
        applyGlobalEffects()

        Log.d(TAG, "Applied to ${sessionEffects.size} sessions + global. Boost: $boostPercent%")
    }

    private fun disableAll() {
        sessionEffects.values.forEach { it.disable() }
        globalEqualizer?.enabled = false
        globalLoudness?.enabled = false
        globalBass?.enabled = false
    }

    // -------------------------------------------------------------------------
    // Global (session 0) fallback
    // -------------------------------------------------------------------------

    private fun initGlobalEffects() {
        releaseGlobalEffects()
        try {
            globalEqualizer = Equalizer(EFFECT_PRIORITY, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Global Equalizer unavailable: ${e.message}")
        }
        try {
            globalLoudness = LoudnessEnhancer(0)
        } catch (e: Exception) {
            Log.w(TAG, "Global LoudnessEnhancer unavailable: ${e.message}")
        }
        try {
            globalBass = BassBoost(EFFECT_PRIORITY, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Global BassBoost unavailable: ${e.message}")
        }
    }

    private fun applyGlobalEffects() {
        globalEqualizer?.let { eq ->
            try {
                if (boostPercent == 0) { eq.enabled = false; return@let }
                val maxLevel = eq.bandLevelRange[1]
                val level = (maxLevel * boostPercent / 200).toShort()
                for (i in 0 until eq.numberOfBands) eq.setBandLevel(i.toShort(), level)
                eq.enabled = true
            } catch (e: Exception) { Log.w(TAG, "Global EQ error: ${e.message}") }
        }
        globalLoudness?.let { le ->
            try {
                if (boostPercent == 0) { le.enabled = false; return@let }
                le.setTargetGain(boostPercent * 1000 / 200)
                le.enabled = true
            } catch (e: Exception) { Log.w(TAG, "Global Loudness error: ${e.message}") }
        }
        globalBass?.let { bb ->
            try {
                if (boostPercent == 0) { bb.enabled = false; return@let }
                bb.setStrength((boostPercent * 1000 / 200).toShort())
                bb.enabled = true
            } catch (e: Exception) { Log.w(TAG, "Global Bass error: ${e.message}") }
        }
    }

    private fun releaseGlobalEffects() {
        try { globalEqualizer?.release() } catch (_: Exception) {}
        try { globalLoudness?.release() } catch (_: Exception) {}
        try { globalBass?.release() } catch (_: Exception) {}
        globalEqualizer = null
        globalLoudness = null
        globalBass = null
    }

    private fun setSystemVolumeMax() {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set max volume: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Per-session effect holder
    // -------------------------------------------------------------------------

    private class SessionEffects private constructor(
        private val sessionId: Int,
        private val equalizer: Equalizer?,
        private val loudness: LoudnessEnhancer?,
        private val bass: BassBoost?
    ) {
        companion object {
            fun create(sessionId: Int): SessionEffects? {
                return try {
                    val eq = try { Equalizer(EFFECT_PRIORITY, sessionId) } catch (e: Exception) {
                        Log.w(TAG, "EQ failed for session $sessionId: ${e.message}"); null
                    }
                    val le = try { LoudnessEnhancer(sessionId) } catch (e: Exception) {
                        Log.w(TAG, "LE failed for session $sessionId: ${e.message}"); null
                    }
                    val bb = try { BassBoost(EFFECT_PRIORITY, sessionId) } catch (e: Exception) {
                        Log.w(TAG, "BB failed for session $sessionId: ${e.message}"); null
                    }
                    if (eq == null && le == null && bb == null) null
                    else SessionEffects(sessionId, eq, le, bb)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not create effects for session $sessionId: ${e.message}")
                    null
                }
            }
        }

        fun apply(boostPercent: Int) {
            equalizer?.let { eq ->
                try {
                    if (boostPercent == 0) { eq.enabled = false; return@let }
                    val maxLevel = eq.bandLevelRange[1]
                    val level = (maxLevel * boostPercent / 200).toShort()
                    for (i in 0 until eq.numberOfBands) eq.setBandLevel(i.toShort(), level)
                    eq.enabled = true
                } catch (e: Exception) { Log.w(TAG, "Session $sessionId EQ error: ${e.message}") }
            }
            loudness?.let { le ->
                try {
                    if (boostPercent == 0) { le.enabled = false; return@let }
                    le.setTargetGain(boostPercent * 1000 / 200)
                    le.enabled = true
                } catch (e: Exception) { Log.w(TAG, "Session $sessionId LE error: ${e.message}") }
            }
            bass?.let { bb ->
                try {
                    if (boostPercent == 0) { bb.enabled = false; return@let }
                    bb.setStrength((boostPercent * 1000 / 200).toShort())
                    bb.enabled = true
                } catch (e: Exception) { Log.w(TAG, "Session $sessionId BB error: ${e.message}") }
            }
        }

        fun disable() {
            try { equalizer?.enabled = false } catch (_: Exception) {}
            try { loudness?.enabled = false } catch (_: Exception) {}
            try { bass?.enabled = false } catch (_: Exception) {}
        }

        fun release() {
            try { equalizer?.release() } catch (_: Exception) {}
            try { loudness?.release() } catch (_: Exception) {}
            try { bass?.release() } catch (_: Exception) {}
        }
    }
}
