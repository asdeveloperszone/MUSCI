package com.asdeveloperszone.musicplayer

import android.content.*
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class PlaybackControlsActivity : AppCompatActivity(), ServiceConnection {

    private var svc: MusicService? = null
    private var bound = false
    private lateinit var prefs: SharedPreferences

    private val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private val speedLabels = listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback_controls)
        prefs = getSharedPreferences("playback_settings", Context.MODE_PRIVATE)

        findViewById<ImageButton>(R.id.btnPbBack).setOnClickListener { finish() }

        setupSpeedControls()
        setupPitchControl()
        setupCrossfade()

        bindService(Intent(this, MusicService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    private fun setupSpeedControls() {
        val container = findViewById<LinearLayout>(R.id.speedContainer)
        val savedSpeed = prefs.getFloat("playback_speed", 1.0f)

        speeds.forEachIndexed { i, speed ->
            val btn = Button(this).apply {
                text = speedLabels[i]
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { setMargins(4, 0, 4, 0) }
                layoutParams = lp
                setBackgroundResource(
                    if (speed == savedSpeed) R.drawable.bg_tab_active else R.drawable.bg_tab_inactive)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
            }
            btn.setOnClickListener {
                // Reset all buttons
                for (j in 0 until container.childCount) {
                    (container.getChildAt(j) as? Button)?.setBackgroundResource(R.drawable.bg_tab_inactive)
                }
                btn.setBackgroundResource(R.drawable.bg_tab_active)
                setSpeed(speed)
            }
            container.addView(btn)
        }
    }

    private fun setupPitchControl() {
        val pitchBar = findViewById<SeekBar>(R.id.seekPitch)
        val tvPitch  = findViewById<TextView>(R.id.tvPitchValue)
        val savedPitch = prefs.getFloat("playback_pitch", 1.0f)

        // Pitch range: 0.5 to 2.0 mapped to 0-150
        pitchBar.max = 150
        pitchBar.progress = ((savedPitch - 0.5f) * 100).toInt()
        tvPitch.text = String.format("%.2fx", savedPitch)

        pitchBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) {
                val pitch = 0.5f + (p / 100f)
                prefs.edit().putFloat("playback_pitch", pitch).apply()
                tvPitch.text = String.format("%.2fx", pitch)
                setPitch(pitch)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun setupCrossfade() {
        val crossfadeBar = findViewById<SeekBar>(R.id.seekCrossfade)
        val tvCrossfade  = findViewById<TextView>(R.id.tvCrossfadeValue)
        val saved = prefs.getInt("crossfade_duration", 0)

        crossfadeBar.max = 10
        crossfadeBar.progress = saved
        tvCrossfade.text = if (saved == 0) "Off" else "${saved}s"

        crossfadeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) {
                prefs.edit().putInt("crossfade_duration", p).apply()
                tvCrossfade.text = if (p == 0) "Off" else "${p}s"
                svc?.setCrossfade(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun setSpeed(speed: Float) {
        prefs.edit().putFloat("playback_speed", speed).apply()
        svc?.setPlaybackSpeed(speed)
        Toast.makeText(this, "Speed: ${speed}x", Toast.LENGTH_SHORT).show()
    }

    private fun setPitch(pitch: Float) {
        svc?.setPlaybackPitch(pitch)
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        svc = (service as MusicService.MusicBinder).getService()
        bound = true
        // Apply saved settings
        val speed = prefs.getFloat("playback_speed", 1.0f)
        val pitch = prefs.getFloat("playback_pitch", 1.0f)
        if (speed != 1.0f) svc?.setPlaybackSpeed(speed)
        if (pitch != 1.0f) svc?.setPlaybackPitch(pitch)
    }

    override fun onServiceDisconnected(name: ComponentName?) { bound = false; svc = null }
    override fun onDestroy() {
        if (bound) { try { unbindService(this) } catch (e: Exception) { }; bound = false }
        super.onDestroy()
    }
}
