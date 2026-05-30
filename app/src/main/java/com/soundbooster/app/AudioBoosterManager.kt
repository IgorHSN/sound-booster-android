package com.soundbooster.app

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import java.util.Timer
import java.util.TimerTask

class AudioBoosterManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioBoosterManager"
        // Reapply effects every 2s — the OS can silently reset them on some devices
        private const val REAPPLY_INTERVAL_MS = 2000L
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var bass: BassBoost? = null

    private var reapplyTimer: Timer? = null

    var isEnabled = false
        private set
    private var boostPercent = 0

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun init() {
        createEffects()
    }

    fun enable() {
        isEnabled = true
        setSystemVolumeMax()
        applyEffects()
        startReapplyTimer()
        Log.d(TAG, "Boost enabled at $boostPercent%")
    }

    fun disable() {
        isEnabled = false
        stopReapplyTimer()
        disableEffects()
        Log.d(TAG, "Boost disabled")
    }

    fun setBoostLevel(percent: Int) {
        boostPercent = percent.coerceIn(0, 200)
        if (isEnabled) applyEffects()
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
        releaseEffects()
    }

    // -------------------------------------------------------------------------
    // Effects
    // -------------------------------------------------------------------------

    private fun createEffects() {
        releaseEffects()
        // Priority 0, session 0 = global audio mix
        try {
            equalizer = Equalizer(0, 0)
            Log.d(TAG, "Equalizer OK — bands=${equalizer?.numberOfBands}, range=${equalizer?.bandLevelRange?.toList()}")
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer unavailable: ${e.message}")
        }
        try {
            loudness = LoudnessEnhancer(0)
            Log.d(TAG, "LoudnessEnhancer OK")
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer unavailable: ${e.message}")
        }
        try {
            bass = BassBoost(0, 0)
            Log.d(TAG, "BassBoost OK — strengthSupported=${bass?.strengthSupported}")
        } catch (e: Exception) {
            Log.w(TAG, "BassBoost unavailable: ${e.message}")
        }
    }

    private fun applyEffects() {
        applyEqualizer()
        applyLoudness()
        applyBass()
    }

    private fun applyEqualizer() {
        val eq = equalizer ?: return
        try {
            if (boostPercent == 0) { eq.enabled = false; return }
            val maxLevel = eq.bandLevelRange[1] // typically 1500 centibels = 15 dB
            val level = (maxLevel * boostPercent / 200).toShort()
            for (i in 0 until eq.numberOfBands) {
                eq.setBandLevel(i.toShort(), level)
            }
            eq.enabled = true
            Log.d(TAG, "EQ applied: ${level} cB on ${eq.numberOfBands} bands")
        } catch (e: Exception) {
            Log.w(TAG, "EQ apply failed: ${e.message}")
            recreateAndApply()
        }
    }

    private fun applyLoudness() {
        val le = loudness ?: return
        try {
            if (boostPercent == 0) { le.enabled = false; return }
            le.setTargetGain(boostPercent * 1000 / 200) // 0–1000 mB
            le.enabled = true
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer apply failed: ${e.message}")
        }
    }

    private fun applyBass() {
        val bb = bass ?: return
        try {
            if (boostPercent == 0) { bb.enabled = false; return }
            bb.setStrength((boostPercent * 1000 / 200).toShort()) // 0–1000
            bb.enabled = true
        } catch (e: Exception) {
            Log.w(TAG, "BassBoost apply failed: ${e.message}")
        }
    }

    private fun disableEffects() {
        try { equalizer?.enabled = false } catch (_: Exception) {}
        try { loudness?.enabled = false } catch (_: Exception) {}
        try { bass?.enabled = false } catch (_: Exception) {}
    }

    private fun releaseEffects() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { loudness?.release() } catch (_: Exception) {}
        try { bass?.release() } catch (_: Exception) {}
        equalizer = null
        loudness = null
        bass = null
    }

    // If an effect throws (OS released it), recreate everything and reapply
    private fun recreateAndApply() {
        Log.d(TAG, "Recreating effects after error")
        createEffects()
        if (isEnabled) applyEffects()
    }

    // -------------------------------------------------------------------------
    // Reapply timer — keeps effects alive on devices that reset session 0
    // -------------------------------------------------------------------------

    private fun startReapplyTimer() {
        stopReapplyTimer()
        reapplyTimer = Timer("BoostReapply", true).also { t ->
            t.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (isEnabled) {
                        applyEffects()
                        Log.d(TAG, "Effects reapplied by timer")
                    }
                }
            }, REAPPLY_INTERVAL_MS, REAPPLY_INTERVAL_MS)
        }
    }

    private fun stopReapplyTimer() {
        reapplyTimer?.cancel()
        reapplyTimer = null
    }

    private fun setSystemVolumeMax() {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set max volume: ${e.message}")
        }
    }
}
