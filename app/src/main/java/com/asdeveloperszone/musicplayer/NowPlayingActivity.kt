package com.asdeveloperszone.musicplayer

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class NowPlayingActivity : AppCompatActivity(), ServiceConnection {

    private lateinit var root:        ConstraintLayout
    private lateinit var ivArt:       ImageView
    private lateinit var ivBlurBg:    ImageView
    private lateinit var tvTitle:     TextView
    private lateinit var tvArtist:    TextView
    private lateinit var tvAlbum:     TextView
    private lateinit var btnPlay:     ImageButton
    private lateinit var btnNext:     ImageButton
    private lateinit var btnPrev:     ImageButton
    private lateinit var btnShuffle:  ImageButton
    private lateinit var btnRepeat:   ImageButton
    private lateinit var btnClose:    ImageButton
    private lateinit var btnBack:     ImageButton
    private lateinit var btnRewind:   ImageButton
    private lateinit var btnFwd:      ImageButton
    private lateinit var btnFav:      ImageButton
    private lateinit var btnEq:       ImageButton
    private lateinit var btnSpeed:    ImageButton
    private lateinit var btnQueue:    ImageButton
    private lateinit var btnInfo:     ImageButton
    private lateinit var btnBgToggle: ImageButton
    private lateinit var visualizer:  VisualizerView
    private lateinit var seekBar:     SeekBar
    private lateinit var tvPos:       TextView
    private lateinit var tvDur:       TextView

    private var svc: MusicService? = null
    private var bound     = false
    private var lastSongId= -1L
    private var useBlurBg = false
    private val handler   = Handler(Looper.getMainLooper())
    private var tickerOn  = false

    // Swipe detection
    private var swipeX0       = 0f
    private val SWIPE_MIN_PX  = 100f

    // ── Ticker ─────────────────────────────────────────────────────────────────
    private val ticker = object : Runnable {
        override fun run() {
            if (!tickerOn) return
            val s = svc
            if (s != null) {
                // Seekbar position
                val pos = s.position(); val dur = s.duration()
                if (dur > 0 && !seekBar.isPressed) {
                    seekBar.max      = dur
                    seekBar.progress = pos
                    tvPos.text       = fmtMs(pos)
                }
                // Play/pause icon
                val playing = s.isPlaying
                if (btnPlay.tag as? Boolean != playing) {
                    btnPlay.tag = playing
                    btnPlay.setImageResource(
                        if (playing) R.drawable.ic_pause else R.drawable.ic_play)
                    ivArt.animate()
                        .scaleX(if (playing) 1f else 0.82f)
                        .scaleY(if (playing) 1f else 0.82f)
                        .setDuration(220).start()
                }
                // Song changed
                val song = s.currentSong()
                if (song != null && song.id != lastSongId) {
                    lastSongId = song.id
                    loadSong(song)
                }
            }
            handler.postDelayed(this, 250)
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        // Show over lock screen — full screen like Samsung Music
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON   or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)

        useBlurBg = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getBoolean("use_blur_bg", false)

        initViews()
        setupSwipe()
    }

    override fun onStart() {
        super.onStart()
        tickerOn = true
        handler.post(ticker)
        bindService(Intent(this, MusicService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        tickerOn = false
        handler.removeCallbacksAndMessages(null)
        try { visualizer.release() } catch (e: Exception) { }
        if (bound) {
            svc?.onSongChange    = null
            svc?.onPlayState     = null
            svc?.onShuffleChange = null
            svc?.onRepeatChange  = null
            try { unbindService(this) } catch (e: Exception) { }
            bound = false; svc = null
        }
    }

    // ── Views ──────────────────────────────────────────────────────────────────

    private fun initViews() {
        root        = findViewById(R.id.rootLayout)
        ivArt       = findViewById(R.id.ivAlbumArt)
        ivBlurBg    = findViewById(R.id.ivBlurBg)
        tvTitle     = findViewById(R.id.tvTitle)
        tvArtist    = findViewById(R.id.tvArtist)
        tvAlbum     = findViewById(R.id.tvAlbum)
        btnPlay     = findViewById(R.id.btnPlayPause)
        btnNext     = findViewById(R.id.btnNext)
        btnPrev     = findViewById(R.id.btnPrevious)
        btnShuffle  = findViewById(R.id.btnShuffle)
        btnRepeat   = findViewById(R.id.btnRepeat)
        btnClose    = findViewById(R.id.btnClose)
        btnBack     = findViewById(R.id.btnBack)
        btnRewind   = findViewById(R.id.btnRewind)
        btnFwd      = findViewById(R.id.btnForward)
        btnFav      = findViewById(R.id.btnFavorite)
        btnEq       = findViewById(R.id.btnEqualizer)
        btnSpeed    = findViewById(R.id.btnSpeed)
        btnQueue    = findViewById(R.id.btnQueue)
        btnInfo     = findViewById(R.id.btnSongInfo)
        btnBgToggle = findViewById(R.id.btnBgToggle)
        visualizer  = findViewById(R.id.visualizer)
        seekBar     = findViewById(R.id.seekBar)
        tvPos       = findViewById(R.id.tvCurrentTime)
        tvDur       = findViewById(R.id.tvTotalTime)

        refreshBgToggleIcon()

        btnBack.setOnClickListener    { finish() }
        btnClose.setOnClickListener   { svc?.stop(); finish() }
        btnPlay.setOnClickListener    { svc?.togglePlayPause() }
        btnNext.setOnClickListener    { svc?.next() }
        btnPrev.setOnClickListener    { svc?.previous() }
        btnRewind.setOnClickListener  { svc?.rewind() }
        btnFwd.setOnClickListener     { svc?.forward() }
        btnShuffle.setOnClickListener { svc?.shuffle() }
        btnRepeat.setOnClickListener  { svc?.cycleRepeat() }
        btnEq.setOnClickListener      { startActivity(Intent(this, EqualizerActivity::class.java)) }
        btnSpeed.setOnClickListener   { startActivity(Intent(this, PlaybackControlsActivity::class.java)) }
        btnQueue.setOnClickListener   { startActivity(Intent(this, QueueActivity::class.java)) }
        btnInfo.setOnClickListener    { startActivity(Intent(this, SongInfoActivity::class.java)) }

        btnFav.setOnClickListener {
            val song = svc?.currentSong() ?: return@setOnClickListener
            val fav  = FavoritesManager.toggle(song.id)
            syncFavBtn(fav)
            Toast.makeText(this, if (fav) "❤ Added to Favorites" else "Removed from Favorites",
                Toast.LENGTH_SHORT).show()
        }

        btnBgToggle.setOnClickListener {
            useBlurBg = !useBlurBg
            getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("use_blur_bg", useBlurBg).apply()
            refreshBgToggleIcon()
            // Force re-render background
            lastSongId = -1L
            svc?.currentSong()?.let { loadSong(it) }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) {
                if (user) { svc?.seekTo(p); tvPos.text = fmtMs(p) }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    // ── Swipe to skip ──────────────────────────────────────────────────────────

    private fun setupSwipe() {
        ivArt.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { swipeX0 = event.x; false }
                MotionEvent.ACTION_UP   -> {
                    val dx = event.x - swipeX0
                    when {
                        dx < -SWIPE_MIN_PX -> { svc?.next();     animateSwipe(-1); true }
                        dx >  SWIPE_MIN_PX -> { svc?.previous(); animateSwipe(1);  true }
                        else               -> false
                    }
                }
                else -> false
            }
        }
        ivArt.setOnClickListener { svc?.togglePlayPause() }
    }

    private fun animateSwipe(dir: Int) {
        val w = ivArt.width.toFloat().coerceAtLeast(300f)
        ivArt.animate().translationX(dir * w).alpha(0f).setDuration(180)
            .withEndAction {
                ivArt.translationX = -dir * w
                ivArt.animate().translationX(0f).alpha(1f).setDuration(180).start()
            }.start()
    }

    // ── Service ────────────────────────────────────────────────────────────────

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        try {
            val s   = (service as MusicService.MusicBinder).getService()
            svc     = s
            bound   = true
            lastSongId = -1L

            s.onSongChange    = { song -> runOnUiThread { lastSongId = song.id; loadSong(song) } }
            s.onPlayState     = { p    -> runOnUiThread { syncPlayBtn(p) } }
            s.onShuffleChange = { sh   -> runOnUiThread { btnShuffle.alpha = if (sh) 1f else 0.4f } }
            s.onRepeatChange  = { r    -> runOnUiThread { syncRepeat(r) } }

            s.currentSong()?.let { loadSong(it); lastSongId = it.id }
            syncPlayBtn(s.isPlaying)
            btnShuffle.alpha = if (s.isShuffle) 1f else 0.4f
            syncRepeat(s.repeatMode)

            // Visualizer
            attachVisualizer()
        } catch (e: Exception) { }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        svc?.onSongChange    = null
        svc?.onPlayState     = null
        svc?.onShuffleChange = null
        svc?.onRepeatChange  = null
        bound = false; svc = null
    }

    // ── Visualizer ─────────────────────────────────────────────────────────────

    private fun attachVisualizer() {
        val sessionId = svc?.getAudioSessionId() ?: 0
        if (sessionId == 0) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            try { visualizer.attach(sessionId) } catch (e: Exception) { }
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), 200)
        }
    }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(rc, perms, results)
        if (rc == 200 && results.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
            attachVisualizer()
        }
    }

    // ── Song loading ───────────────────────────────────────────────────────────

    private fun loadSong(song: Song) {
        tvTitle.text = song.title
        tvArtist.text= song.artist
        tvAlbum.text = song.album
        tvDur.text   = song.getDurationFormatted()
        syncFavBtn(FavoritesManager.isFavorite(song.id))

        Glide.with(this).asBitmap()
            .load(song.albumArtUri)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(bmp: Bitmap, t: Transition<in Bitmap>?) {
                    if (isDestroyed || isFinishing) return
                    ivArt.setImageBitmap(bmp)
                    if (useBlurBg) applyBlur(bmp) else applyGradient(bmp)
                    MusicWidget.push(this@NowPlayingActivity,
                        song.title, song.artist, svc?.isPlaying == true)
                }
                override fun onLoadCleared(p: android.graphics.drawable.Drawable?) {
                    if (!isDestroyed && !isFinishing) ivArt.setImageDrawable(p)
                }
                override fun onLoadFailed(e: android.graphics.drawable.Drawable?) {
                    if (!isDestroyed && !isFinishing) { ivArt.setImageDrawable(e); defaultBg() }
                }
            })
    }

    // ── Backgrounds ────────────────────────────────────────────────────────────

    private fun applyBlur(bmp: Bitmap) {
        ivBlurBg.visibility = View.VISIBLE
        root.setBackgroundColor(0xDD000000.toInt())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ivBlurBg.setImageBitmap(bmp)
            ivBlurBg.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(
                    50f, 50f, android.graphics.Shader.TileMode.CLAMP))
        } else {
            // Pre-Android 12 soft blur via downscale+upscale
            try {
                val small = Bitmap.createScaledBitmap(bmp,
                    (bmp.width * 0.08f).toInt().coerceAtLeast(1),
                    (bmp.height* 0.08f).toInt().coerceAtLeast(1), true)
                ivBlurBg.setImageBitmap(
                    Bitmap.createScaledBitmap(small, bmp.width, bmp.height, true))
                small.recycle()
            } catch (e: Exception) { ivBlurBg.setImageBitmap(bmp) }
        }
        ivBlurBg.alpha = 0.55f
    }

    private fun applyGradient(bmp: Bitmap) {
        ivBlurBg.visibility = View.GONE
        try {
            Palette.from(bmp).generate { p ->
                if (isDestroyed || isFinishing) return@generate
                root.background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        p?.getDominantColor(0xFF1A0000.toInt()) ?: 0xFF1A0000.toInt(),
                        p?.getVibrantColor (0xFFCC0000.toInt()) ?: 0xFFCC0000.toInt(),
                        p?.getMutedColor   (0xFF0A0A0A.toInt()) ?: 0xFF0A0A0A.toInt()
                    ))
            }
        } catch (e: Exception) { defaultBg() }
    }

    private fun defaultBg() {
        ivBlurBg.visibility = View.GONE
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF1A0000.toInt(), 0xFF880000.toInt(), 0xFF0A0A0A.toInt()))
    }

    // ── Sync helpers ───────────────────────────────────────────────────────────

    private fun syncPlayBtn(playing: Boolean) {
        btnPlay.tag = playing
        btnPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        ivArt.animate()
            .scaleX(if (playing) 1f else 0.82f)
            .scaleY(if (playing) 1f else 0.82f)
            .setDuration(220).start()
    }

    private fun syncFavBtn(fav: Boolean) {
        btnFav.setImageResource(
            if (fav) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
        btnFav.setColorFilter(if (fav) 0xFFFF4444.toInt() else 0xFFAAAAAA.toInt())
    }

    private fun syncRepeat(mode: RepeatMode) {
        when (mode) {
            RepeatMode.OFF        -> { btnRepeat.alpha = 0.4f; btnRepeat.setImageResource(R.drawable.ic_repeat) }
            RepeatMode.REPEAT_ALL -> { btnRepeat.alpha = 1f;   btnRepeat.setImageResource(R.drawable.ic_repeat) }
            RepeatMode.REPEAT_ONE -> { btnRepeat.alpha = 1f;   btnRepeat.setImageResource(R.drawable.ic_repeat_one) }
        }
    }

    private fun refreshBgToggleIcon() {
        btnBgToggle.setImageResource(
            if (useBlurBg) R.drawable.ic_blur_on else R.drawable.ic_palette)
        btnBgToggle.alpha = if (useBlurBg) 1f else 0.65f
    }

    private fun fmtMs(ms: Int): String {
        val t = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(t / 60, t % 60)
    }
}
