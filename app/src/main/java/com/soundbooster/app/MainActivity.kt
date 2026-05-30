package com.soundbooster.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.soundbooster.app.databinding.ActivityMainBinding

/**
 * MainActivity provides the primary UI for controlling the audio boost.
 *
 * Layout overview:
 *  - tvCurrentVolume  : shows current system volume index / max
 *  - tvBoostPercent   : shows boost level as percentage
 *  - tvBoostDb        : shows approximate dB gain
 *  - sbBoost          : SeekBar 0–200 for boost percentage
 *  - btnToggle        : enable / disable the boost
 *  - tvStatus         : "Active" or "Inactive" indicator
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 100
        private const val UI_REFRESH_INTERVAL_MS = 500L
    }

    private lateinit var binding: ActivityMainBinding

    // Service connection
    private var boosterService: AudioBoosterService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioBoosterService.LocalBinder
            boosterService = binder.getService()
            isBound = true
            syncUiFromService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boosterService = null
            isBound = false
        }
    }

    // Periodic UI refresh handler
    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiRefreshRunnable = object : Runnable {
        override fun run() {
            if (isBound) refreshVolumeDisplay()
            uiHandler.postDelayed(this, UI_REFRESH_INTERVAL_MS)
        }
    }

    // -------------------------------------------------------------------------
    // Activity lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUi()
        requestRequiredPermissions()
        startAndBindService()
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(uiRefreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(uiRefreshRunnable)
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // UI setup
    // -------------------------------------------------------------------------

    private fun setupUi() {
        // SeekBar: 0–100 maps to 0–100% boost (0–1500 mB / 0–15 dB)
        binding.sbBoost.max = 100
        binding.sbBoost.progress = 50 // default 50%

        binding.sbBoost.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateBoostLabels(progress)
                if (fromUser && isBound) {
                    boosterService?.updateBoostLevel(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnToggle.setOnClickListener {
            toggleBoost()
        }

        updateBoostLabels(binding.sbBoost.progress)
        updateToggleButton(false)
    }

    private fun updateBoostLabels(percent: Int) {
        binding.tvBoostPercent.text = "$percent%"
        // 100% slider = 3000 mB = 30 dB
        val db = percent * 30.0f / 100f
        binding.tvBoostDb.text = String.format("+%.1f dB", db)
    }

    private fun refreshVolumeDisplay() {
        val service = boosterService ?: return
        val manager = service.boosterManager
        val current = manager.getCurrentVolume()
        val max = manager.getMaxVolume()
        binding.tvCurrentVolume.text = "Volume: $current / $max"
    }

    private fun updateToggleButton(active: Boolean) {
        if (active) {
            binding.btnToggle.text = "Stop Boost"
            binding.btnToggle.setBackgroundColor(
                ContextCompat.getColor(this, R.color.toggle_off)
            )
            binding.tvStatus.text = "Status: Active"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_active))
        } else {
            binding.btnToggle.text = "Start Boost"
            binding.btnToggle.setBackgroundColor(
                ContextCompat.getColor(this, R.color.toggle_on)
            )
            binding.tvStatus.text = "Status: Inactive"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_inactive))
        }
    }

    // -------------------------------------------------------------------------
    // Service interaction
    // -------------------------------------------------------------------------

    private fun startAndBindService() {
        val intent = Intent(this, AudioBoosterService::class.java)
        ContextCompat.startForegroundService(this, intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun toggleBoost() {
        val service = boosterService ?: run {
            Toast.makeText(this, "Service not ready. Please wait.", Toast.LENGTH_SHORT).show()
            return
        }

        val isActive = service.boosterManager.isEnabled
        if (isActive) {
            service.stopBoost()
            updateToggleButton(false)
        } else {
            val level = binding.sbBoost.progress
            service.startBoost(level)
            updateToggleButton(true)
        }
    }

    private fun syncUiFromService() {
        val service = boosterService ?: return
        val manager = service.boosterManager
        val isActive = manager.isEnabled
        val percent = manager.getBoostPercent()
        binding.sbBoost.progress = percent
        updateBoostLabels(percent)
        updateToggleButton(isActive)
        refreshVolumeDisplay()
    }

    // -------------------------------------------------------------------------
    // Permissions
    // -------------------------------------------------------------------------

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                    this,
                    "Notification permission denied. The booster will still work but no notification will be shown.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
