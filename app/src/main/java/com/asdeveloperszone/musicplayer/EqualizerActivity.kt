package com.asdeveloperszone.musicplayer

import android.content.*
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.os.IBinder
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class EqualizerActivity : AppCompatActivity(), ServiceConnection {

    private var svc: MusicService? = null
    private var bound = false
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private lateinit var prefs: SharedPreferences

    // Band seekbars
    private val bandBars = mutableListOf<SeekBar>()
    private val bandLabels = mutableListOf<TextView>()

    // Presets
    private val presets = mapOf(
        "Flat"      to intArrayOf(0, 0, 0, 0, 0),
        "Rock"      to intArrayOf(4, 2, -1, 2, 4),
        "Pop"       to intArrayOf(-1, 2, 4, 2, -1),
        "Jazz"      to intArrayOf(3, 1, 0, 2, 3),
        "Classical" to intArrayOf(4, 3, -1, 2, 3),
        "Bass"      to intArrayOf(6, 4, 0, -1, -1),
        "Vocal"     to intArrayOf(-2, 0, 3, 3, 2)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_equalizer)
        prefs = getSharedPreferences("eq_settings", Context.MODE_PRIVATE)

        findViewById<ImageButton>(R.id.btnEqBack).setOnClickListener { finish() }

        setupPresetButtons()
        setupBassBoost()
        bindService(Intent(this, MusicService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    private fun setupPresetButtons() {
        val container = findViewById<LinearLayout>(R.id.presetContainer)
        presets.forEach { (name, _) ->
            val btn = Button(this).apply {
                text = name
                setBackgroundResource(R.drawable.bg_tab_inactive)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(8, 0, 8, 0) }
                layoutParams = lp
                setPadding(24, 12, 24, 12)
            }
            btn.setOnClickListener { applyPreset(name) }
            container.addView(btn)
        }
    }

    private fun setupBassBoost() {
        val bassBar = findViewById<SeekBar>(R.id.seekBassBoost)
        val bassValue = prefs.getInt("bass_boost", 0)
        bassBar.progress = bassValue

        bassBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) {
                prefs.edit().putInt("bass_boost", p).apply()
                bass?.setStrength(p.toShort())
                findViewById<TextView>(R.id.tvBassValue).text = "$p"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        findViewById<TextView>(R.id.tvBassValue).text = "$bassValue"
    }

    private fun setupEqBands(equalizer: Equalizer) {
        val container = findViewById<LinearLayout>(R.id.eqBandsContainer)
        container.removeAllViews()
        bandBars.clear(); bandLabels.clear()

        val numBands = equalizer.numberOfBands.toInt()
        val minLevel = equalizer.bandLevelRange[0]
        val maxLevel = equalizer.bandLevelRange[1]
        val range    = maxLevel - minLevel

        for (i in 0 until numBands) {
            val freqHz = equalizer.getCenterFreq(i.toShort()) / 1000
            val label  = if (freqHz >= 1000) "${freqHz/1000}kHz" else "${freqHz}Hz"
            val saved  = prefs.getInt("band_$i", 0)

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvVal = TextView(this).apply {
                text = "${saved/100}dB"
                textSize = 10f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = android.view.Gravity.CENTER
            }

            val sb = SeekBar(this).apply {
                rotation = -90f
                layoutParams = LinearLayout.LayoutParams(200, 48)
                max = range
                progress = (saved - minLevel)
                progressTintList = android.content.res.ColorStateList.valueOf(0xFFCC0000.toInt())
                thumbTintList    = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            }

            val tvFreq = TextView(this).apply {
                text = label
                textSize = 10f
                setTextColor(0xAAFFFFFF.toInt())
                gravity = android.view.Gravity.CENTER
            }

            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, user: Boolean) {
                    val level = (p + minLevel).toShort()
                    equalizer.setBandLevel(i.toShort(), level)
                    prefs.edit().putInt("band_$i", level.toInt()).apply()
                    tvVal.text = "${level/100}dB"
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })

            col.addView(tvVal)
            col.addView(sb)
            col.addView(tvFreq)
            container.addView(col)
            bandBars.add(sb)
            bandLabels.add(tvFreq)
        }
    }

    private fun applyPreset(name: String) {
        val eq = eq ?: return
        val gains = presets[name] ?: return
        val numBands = eq.numberOfBands.toInt()
        val min = eq.bandLevelRange[0]

        for (i in 0 until minOf(numBands, gains.size)) {
            val level = (gains[i] * 100).toShort()
            eq.setBandLevel(i.toShort(), level)
            prefs.edit().putInt("band_$i", level.toInt()).apply()
            if (i < bandBars.size) {
                bandBars[i].progress = level - min
                bandLabels[i]
            }
        }
        prefs.edit().putString("preset", name).apply()
        Toast.makeText(this, "Preset: $name", Toast.LENGTH_SHORT).show()
        // Refresh band views
        eq.let { setupEqBands(it) }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        svc = (service as MusicService.MusicBinder).getService()
        bound = true

        val sessionId = svc?.getAudioSessionId() ?: return
        if (sessionId == 0) return

        try {
            eq = Equalizer(0, sessionId).apply { enabled = true }
            bass = BassBoost(0, sessionId).apply {
                enabled = true
                setStrength(prefs.getInt("bass_boost", 0).toShort())
            }
            setupEqBands(eq!!)

            // Restore saved band levels
            val numBands = eq!!.numberOfBands.toInt()
            for (i in 0 until numBands) {
                val saved = prefs.getInt("band_$i", 0)
                if (saved != 0) eq!!.setBandLevel(i.toShort(), saved.toShort())
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Equalizer not supported on this device", Toast.LENGTH_LONG).show()
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) { bound = false; svc = null }

    override fun onDestroy() {
        if (bound) { try { unbindService(this) } catch (e: Exception) { }; bound = false }
        super.onDestroy()
    }
}
