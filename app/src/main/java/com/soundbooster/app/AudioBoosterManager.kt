package com.soundbooster.app

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log

class AudioBoosterManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioBoosterManager"
        // INT_MAX_VALUE priority ensures our effects take precedence over others
        private const val EFFECT_PRIORITY = Int.MAX_VALUE
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var bassBoost: BassBoost? = null

    var isEnabled = false
        private set

    private var boostPercent = 0

    fun init() {
        releaseEffects()
        try {
            // Session 0 = global audio mix. High priority = we override other effects.
            equalizer = Equalizer(EFFECT_PRIORITY, 0)
            Log.d(TAG, "Equalizer created. Bands: ${equalizer?.numberOfBands}, range: ${equalizer?.bandLevelRange?.toList()}")
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer unavailable: ${e.message}")
        }
        try {
            loudnessEnhancer = LoudnessEnhancer(0)
            Log.d(TAG, "LoudnessEnhancer created.")
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer unavailable: ${e.message}")
        }
        try {
            bassBoost = BassBoost(EFFECT_PRIORITY, 0)
            Log.d(TAG, "BassBoost created. Strength supported: ${bassBoost?.strengthSupported}")
        } catch (e: Exception) {
            Log.w(TAG, "BassBoost unavailable: ${e.message}")
        }
    }

    fun enable() {
        isEnabled = true
        setSystemVolumeMax()
        applyEffects()
    }

    fun disable() {
        isEnabled = false
        equalizer?.enabled = false
        loudnessEnhancer?.enabled = false
        bassBoost?.enabled = false
    }

    fun setBoostLevel(percent: Int) {
        boostPercent = percent.coerceIn(0, 200)
        if (isEnabled) applyEffects()
    }

    fun getBoostPercent() = boostPercent

    fun getVolumePercent(): Int {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) (current * 100 / max) else 0
    }

    fun getCurrentVolume() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    fun getMaxVolume() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    fun release() {
        disable()
        releaseEffects()
    }

    private fun applyEffects() {
        applyEqualizer()
        applyLoudnessEnhancer()
        applyBassBoost()
    }

    private fun applyEqualizer() {
        val eq = equalizer ?: return
        try {
            if (boostPercent == 0) {
                eq.enabled = false
                return
            }
            val maxLevel = eq.bandLevelRange[1] // typically 1500 centibels = 15 dB
            // Scale: at 100% use ~60% of max; at 200% use 100% of max
            val targetLevel = (maxLevel * boostPercent / 200).toShort()
            for (i in 0 until eq.numberOfBands) {
                eq.setBandLevel(i.toShort(), targetLevel)
            }
            eq.enabled = true
            Log.d(TAG, "EQ set to $targetLevel centibels on ${eq.numberOfBands} bands")
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer apply failed: ${e.message}")
        }
    }

    private fun applyLoudnessEnhancer() {
        val le = loudnessEnhancer ?: return
        try {
            if (boostPercent == 0) {
                le.enabled = false
                return
            }
            // 0–200% maps to 0–1000 mB (0–10 dB)
            val gainMb = boostPercent * 1000 / 200
            le.setTargetGain(gainMb)
            le.enabled = true
            Log.d(TAG, "LoudnessEnhancer set to $gainMb mB")
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer apply failed: ${e.message}")
        }
    }

    private fun applyBassBoost() {
        val bb = bassBoost ?: return
        try {
            if (boostPercent == 0) {
                bb.enabled = false
                return
            }
            // BassBoost strength: 0–1000
            val strength = (boostPercent * 1000 / 200).toShort()
            bb.setStrength(strength)
            bb.enabled = true
            Log.d(TAG, "BassBoost set to strength $strength")
        } catch (e: Exception) {
            Log.w(TAG, "BassBoost apply failed: ${e.message}")
        }
    }

    private fun setSystemVolumeMax() {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set max volume: ${e.message}")
        }
    }

    private fun releaseEffects() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { loudnessEnhancer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        equalizer = null
        loudnessEnhancer = null
        bassBoost = null
    }
}
