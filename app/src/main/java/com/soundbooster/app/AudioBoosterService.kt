package com.soundbooster.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * AudioBoosterService runs as a foreground service to keep the audio boost
 * active even when the app is not in the foreground.
 *
 * Communication with the UI is done through the [LocalBinder] which exposes
 * the [AudioBoosterManager] directly.
 */
class AudioBoosterService : Service() {

    companion object {
        private const val TAG = "AudioBoosterService"
        private const val NOTIFICATION_CHANNEL_ID = "sound_booster_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START_BOOST = "com.soundbooster.app.ACTION_START_BOOST"
        const val ACTION_STOP_BOOST  = "com.soundbooster.app.ACTION_STOP_BOOST"
        const val EXTRA_BOOST_LEVEL  = "boost_level"
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioBoosterService = this@AudioBoosterService
        fun getManager(): AudioBoosterManager = boosterManager
    }

    private val binder = LocalBinder()
    lateinit var boosterManager: AudioBoosterManager
        private set

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        boosterManager = AudioBoosterManager(applicationContext)
        boosterManager.init()
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BOOST -> {
                val level = intent.getIntExtra(EXTRA_BOOST_LEVEL, 100)
                boosterManager.setBoostLevel(level)
                boosterManager.enable()
                startForeground(NOTIFICATION_ID, buildNotification(level, true))
                Log.d(TAG, "Boost started at $level%")
            }
            ACTION_STOP_BOOST -> {
                boosterManager.disable()
                updateNotification(boosterManager.getBoostPercent(), false)
                Log.d(TAG, "Boost stopped")
            }
            else -> {
                // Service bound without an explicit action – just show notification
                startForeground(NOTIFICATION_ID, buildNotification(0, false))
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        boosterManager.release()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    // -------------------------------------------------------------------------
    // Public helpers called by bound clients (MainActivity)
    // -------------------------------------------------------------------------

    fun startBoost(level: Int) {
        boosterManager.setBoostLevel(level)
        boosterManager.enable()
        updateNotification(level, true)
    }

    fun stopBoost() {
        boosterManager.disable()
        updateNotification(boosterManager.getBoostPercent(), false)
    }

    fun updateBoostLevel(level: Int) {
        boosterManager.setBoostLevel(level)
        if (boosterManager.isEnabled) {
            updateNotification(level, true)
        }
    }

    // -------------------------------------------------------------------------
    // Notification helpers
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Sound Booster",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Sound Booster is active"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(boostPercent: Int, active: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPi = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (active) "Boosting at $boostPercent%" else "Inactive – tap to open"

        // Toggle action
        val toggleIntent = Intent(this, AudioBoosterService::class.java).apply {
            action = if (active) ACTION_STOP_BOOST else ACTION_START_BOOST
            putExtra(EXTRA_BOOST_LEVEL, boostPercent)
        }
        val togglePi = PendingIntent.getService(
            this, 1, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(if (active) "Sound Booster Active" else "Sound Booster")
            .setContentText(statusText)
            .setContentIntent(openAppPi)
            .addAction(
                if (active) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (active) "Stop" else "Start",
                togglePi
            )
            .setOngoing(active)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(boostPercent: Int, active: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(boostPercent, active))
    }
}
