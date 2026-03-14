package com.asdeveloperszone.musicplayer

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        val btnBack            = findViewById<ImageButton>(R.id.btnSettingsBack)
        val switchAutoPlay     = findViewById<Switch>(R.id.switchAutoPlay)
        val switchPauseUnplug  = findViewById<Switch>(R.id.switchPauseOnUnplug)
        val switchGapless      = findViewById<Switch>(R.id.switchGapless)
        val switchDarkTheme    = findViewById<Switch>(R.id.switchDarkTheme)
        val btnBattery         = findViewById<Button>(R.id.btnBatteryOptimization)
        val tvVersion          = findViewById<TextView>(R.id.tvVersion)

        // Load saved values
        switchAutoPlay.isChecked    = prefs.getBoolean("auto_play_headphone", false)
        switchPauseUnplug.isChecked = prefs.getBoolean("pause_on_unplug", true)
        switchGapless.isChecked     = prefs.getBoolean("gapless_playback", false)

        // Dark theme — read current mode
        val isDark = AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_NO
        switchDarkTheme.isChecked = isDark

        tvVersion.text = "Version 5.0"

        btnBack.setOnClickListener { finish() }

        switchAutoPlay.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("auto_play_headphone", checked).apply()
        }
        switchPauseUnplug.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("pause_on_unplug", checked).apply()
        }
        switchGapless.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("gapless_playback", checked).apply()
            Toast.makeText(this, "Restart app to apply", Toast.LENGTH_SHORT).show()
        }
        switchDarkTheme.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("dark_theme", checked).apply()
            // Apply immediately
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        btnBattery.setOnClickListener {
            BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this)
        }
    }
}
