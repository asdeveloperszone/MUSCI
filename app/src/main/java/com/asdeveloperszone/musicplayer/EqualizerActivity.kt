package com.asdeveloperszone.musicplayer

import android.content.*
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.os.IBinder
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class EqualizerActivity : AppCompatActivity(), ServiceConnection {

    private var svc: MusicService? = null
    private var bound = false
    private lateinit var prefs: SharedPreferences

    private val bandBars = mutableListOf<SeekBar>()
    private val bandValueLabels = mutableListOf<TextView>()

    private val presets = linkedMapOf(
        "Flat"      to intArrayOf(0, 0, 0, 0, 0),
        "Rock"      to intArrayOf(400, 200, -100, 200, 400),
        "Pop"       to intArrayOf(-100, 200, 400, 200, -100),
        "Jazz"      to intArrayOf(300, 100, 0, 200, 300),
        "Classical" to intArrayOf(400, 300, -100, 200, 300),
        "Bass"      to intArrayOf(600, 400, 0, -100, -100),
        "Vocal"     to intArrayOf(-200, 0, 300, 300, 200)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equalizer)
        prefs = getSharedPreferences("eq_settings", Context.MODE_PRIVATE)
        findViewById<ImageButton>(R.id.btnEqBack).setOnClickListener { finish() }
        setupBassBoost()
        bindService(Intent(this, MusicService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    private fun setupPresetButtons(eq: Equalizer) {
        val container = findViewById<LinearLayout>(R.id.presetContainer)
        container.removeAllViews()
        presets.forEach { (name, _) ->
            Button(this).apply {
                text = name
                setBackgroundResource(R.drawable.bg_tab_inactive)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 11f
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(6, 0, 6, 0) }
                layoutParams = lp
                setPadding(20, 10, 20, 10)
                setOnClickListener { applyPreset(name, eq) }
                container.addView(this)
            }
        }
    }

    private fun setupBassBoost() {
        val bassBar   = findViewById<SeekBar>(R.id.seekBassBoost)
        val tvBassVal = findViewById<TextView>(R.id.tvBassValue)
        val saved     = prefs.getInt("bass_boost", 0)
        bassBar.max      = 1000
        bassBar.progress = saved
        tvBassVal.text   = "$saved"

        bassBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) {
                prefs.edit().putInt("bass_boost", p).apply()
                tvBassVal.text = "$p"
                // Apply to service's BassBoost
                svc?.setBassBoost(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun setupEqBands(eq: Equalizer) {
        val container = findViewById<LinearLayout>(R.id.eqBandsContainer)
        container.removeAllViews()
        bandBars.clear(); bandValueLabels.clear()

        val numBands = eq.numberOfBands.toInt()
        val minLevel = eq.bandLevelRange[0].toInt()
        val maxLevel = eq.bandLevelRange[1].toInt()
        val range    = maxLevel - minLevel

        for (i in 0 until numBands) {
            val freqHz = eq.getCenterFreq(i.toShort()) / 1000
            val freq   = if (freqHz >= 1000) "${freqHz/1000}kHz" else "${freqHz}Hz"
            val saved  = prefs.getInt("band_$i", 0)

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvVal = TextView(this).apply {
                text      = "${saved / 100}dB"
                textSize  = 9f
                setTextColor(0xFFFFFFFF.toInt())
                gravity   = android.view.Gravity.CENTER
            }

            val sb = SeekBar(this).apply {
                rotation  = -90f
                max       = range
                progress  = (saved - minLevel).coerceIn(0, range)
                layoutParams = LinearLayout.LayoutParams(180, 52)
                progressTintList = android.content.res.ColorStateList.valueOf(0xFFCC0000.toInt())
                thumbTintList    = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            }

            val tvFreq = TextView(this).apply {
                text      = freq
                textSize  = 9f
                setTextColor(0xAAFFFFFF.toInt())
                gravity   = android.view.Gravity.CENTER
            }

            val bandIndex = i
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, user: Boolean) {
                    val level = (p + minLevel).toShort()
                    // Apply via service so it persists
                    svc?.setEqBand(bandIndex, level)
                    prefs.edit().putInt("band_$bandIndex", level.toInt()).apply()
                    tvVal.text = "${level / 100}dB"
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })

            col.addView(tvVal); col.addView(sb); col.addView(tvFreq)
            container.addView(col)
            bandBars.add(sb); bandValueLabels.add(tvVal)
        }
    }

    private fun applyPreset(name: String, eq: Equalizer) {
        val gains    = presets[name] ?: return
        val numBands = eq.numberOfBands.toInt()
        val min      = eq.bandLevelRange[0].toInt()
        val max      = eq.bandLevelRange[1].toInt()
        val range    = max - min

        for (i in 0 until minOf(numBands, gains.size)) {
            val level = gains[i].toShort()
            svc?.setEqBand(i, level)
            prefs.edit().putInt("band_$i", level.toInt()).apply()
            if (i < bandBars.size) {
                bandBars[i].progress = (level - min).coerceIn(0, range)
                bandValueLabels[i].text = "${level / 100}dB"
            }
        }
        prefs.edit().putString("preset", name).apply()
        Toast.makeText(this, "Preset: $name applied", Toast.LENGTH_SHORT).show()
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        try {
            svc = (service as MusicService.MusicBinder).getService()
            bound = true
            svc?.initEqualizer()
            val eq = svc?.getEqualizer() ?: return
            setupPresetButtons(eq)
            setupEqBands(eq)
            val numBands = eq.numberOfBands.toInt()
            for (i in 0 until numBands) {
                val saved = prefs.getInt("band_$i", 0)
                if (saved != 0) {
                    svc?.setEqBand(i, saved.toShort())
                    val min = eq.bandLevelRange[0].toInt()
                    val max = eq.bandLevelRange[1].toInt()
                    if (i < bandBars.size) {
                        bandBars[i].progress = (saved - min).coerceIn(0, max - min)
                        bandValueLabels[i].text = "${saved / 100}dB"
                    }
                }
            }
            val bassVal = prefs.getInt("bass_boost", 0)
            svc?.setBassBoost(bassVal)
        } catch (e: Exception) {
            Toast.makeText(this, "EQ not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) { bound = false; svc = null }
    override fun onDestroy() {
        if (bound) { try { unbindService(this) } catch (e: Exception) { }; bound = false }
        super.onDestroy()
    }
}
