package com.asdeveloperszone.musicplayer

import android.content.*
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class NowPlayingActivity : AppCompatActivity(), ServiceConnection {

    private lateinit var root: ConstraintLayout
    private lateinit var ivArt: ImageView
    private lateinit var ivBlurBg: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvAlbum: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnRewind: ImageButton
    private lateinit var btnFwd: ImageButton
    private lateinit var btnFavorite: ImageButton
    private lateinit var btnEq: ImageButton
    private lateinit var btnSpeed: ImageButton
    private lateinit var btnQueue: ImageButton
    private lateinit var btnSongInfo: ImageButton
    private lateinit var btnBgToggle: ImageButton
    private lateinit var visualizer: VisualizerView
    private lateinit var seekBar: SeekBar
    private lateinit var tvPos: TextView
    private lateinit var tvDur: TextView

    private var svc: MusicService? = null
    private var bound = false
    private var lastSongId = -1L
    private val handler = Handler(Looper.getMainLooper())
    private var useBlurBg = false // false = dynamic color, true = blur

    private val ticker = object : Runnable {
        override fun run() {
            svc?.let { s ->
                val pos = s.position(); val dur = s.duration()
                if (dur > 0) {
                    seekBar.max = dur
                    if (!seekBar.isPressed) seekBar.progress = pos
                    tvPos.text = fmt(pos)
                }
                val playing = s.isPlaying
                if (btnPlay.tag as? Boolean != playing) {
                    btnPlay.tag = playing
                    btnPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
                    ivArt.animate().scaleX(if (playing) 1f else 0.8f)
                        .scaleY(if (playing) 1f else 0.8f).setDuration(200).start()
                }
                val song = s.currentSong()
                if (song != null && song.id != lastSongId) {
                    lastSongId = song.id; loadSong(song)
                }
            }
            handler.postDelayed(this, 250)
        }
    }

    // Swipe gesture
    private var swipeStartX = 0f
    private val SWIPE_THRESHOLD = 120f

    override fun onCreate(savedInstanceState: Bundle?) {
        // Show full screen on lock screen like Samsung Music
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)

        // Load saved bg preference
        useBlurBg = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getBoolean("use_blur_bg", false)

        initViews()
        setupSwipe()
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, MusicService::class.java), this, Context.BIND_AUTO_CREATE)
        handler.post(ticker)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(ticker)
        visualizer.release()
        if (bound) {
            svc?.apply { onSongChange = null; onPlayState = null; onShuffleChange = null; onRepeatChange = null }
            try { unbindService(this) } catch (e: Exception) { }
            bound = false; svc = null
        }
    }

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
        btnFavorite = findViewById(R.id.btnFavorite)
        btnEq       = findViewById(R.id.btnEqualizer)
        btnSpeed    = findViewById(R.id.btnSpeed)
        btnQueue    = findViewById(R.id.btnQueue)
        btnSongInfo = findViewById(R.id.btnSongInfo)
        btnBgToggle = findViewById(R.id.btnBgToggle)
        visualizer  = findViewById(R.id.visualizer)
        seekBar     = findViewById(R.id.seekBar)
        tvPos       = findViewById(R.id.tvCurrentTime)
        tvDur       = findViewById(R.id.tvTotalTime)

        syncBgToggleIcon()

        btnBack.setOnClickListener    { finish() }
        btnPlay.setOnClickListener    { svc?.togglePlayPause() }
        btnNext.setOnClickListener    { svc?.next() }
        btnPrev.setOnClickListener    { svc?.previous() }
        btnRewind.setOnClickListener  { svc?.rewind() }
        btnFwd.setOnClickListener     { svc?.forward() }
        btnShuffle.setOnClickListener { svc?.shuffle() }
        btnRepeat.setOnClickListener  { svc?.cycleRepeat() }
        btnClose.setOnClickListener   { svc?.stop(); finish() }
        btnEq.setOnClickListener      { startActivity(Intent(this, EqualizerActivity::class.java)) }
        btnSpeed.setOnClickListener   { startActivity(Intent(this, PlaybackControlsActivity::class.java)) }
        btnQueue.setOnClickListener    { startActivity(Intent(this, QueueActivity::class.java)) }
        btnSongInfo.setOnClickListener { startActivity(Intent(this, SongInfoActivity::class.java)) }

        btnBgToggle.setOnClickListener {
            useBlurBg = !useBlurBg
            getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("use_blur_bg", useBlurBg).apply()
            syncBgToggleIcon()
            // Re-apply background
            svc?.currentSong()?.let { loadSong(it) }
        }

        btnFavorite.setOnClickListener {
            val song = svc?.currentSong() ?: return@setOnClickListener
            val fav = FavoritesManager.toggle(song.id)
            syncFavBtn(fav)
            Toast.makeText(this, if (fav) "❤ Added to Favorites" else "Removed", Toast.LENGTH_SHORT).show()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) { if (user) svc?.seekTo(p) }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun setupSwipe() {
        ivArt.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { swipeStartX = event.x; true }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - swipeStartX
                    when {
                        dx < -SWIPE_THRESHOLD -> { svc?.next(); animateSwipe(-1); true }
                        dx >  SWIPE_THRESHOLD -> { svc?.previous(); animateSwipe(1); true }
                        else -> false
                    }
                }
                else -> false
            }
        }
        ivArt.setOnClickListener { svc?.togglePlayPause() }
    }

    private fun animateSwipe(dir: Int) {
        ivArt.animate().translationX(dir * 300f).alpha(0f).setDuration(200).withEndAction {
            ivArt.translationX = (-dir * 300f)
            ivArt.animate().translationX(0f).alpha(1f).setDuration(200).start()
        }.start()
    }

    private fun syncBgToggleIcon() {
        btnBgToggle.setImageResource(
            if (useBlurBg) R.drawable.ic_blur_on else R.drawable.ic_palette)
        btnBgToggle.alpha = if (useBlurBg) 1f else 0.7f
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        try {
            val s = (service as MusicService.MusicBinder).getService()
            svc = s; bound = true; lastSongId = -1L

            s.onSongChange    = { song -> runOnUiThread { lastSongId = song.id; loadSong(song) } }
            s.onPlayState     = { p    -> runOnUiThread { syncPlayBtn(p) } }
            s.onShuffleChange = { sh   -> runOnUiThread { btnShuffle.alpha = if (sh) 1f else 0.4f } }
            s.onRepeatChange  = { r    -> runOnUiThread { syncRepeat(r) } }

            s.currentSong()?.let { loadSong(it); lastSongId = it.id }
            btnShuffle.alpha = if (s.isShuffle) 1f else 0.4f
            syncRepeat(s.repeatMode); syncPlayBtn(s.isPlaying)

            // Attach visualizer safely
            val sessionId = s.getAudioSessionId()
            if (sessionId != 0) try { visualizer.attach(sessionId) } catch (e: Exception) { }
        } catch (e: Exception) { }
    }

    private fun loadSong(song: Song) {
        tvTitle.text = song.title; tvArtist.text = song.artist
        tvAlbum.text = song.album; tvDur.text = song.getDurationFormatted()
        syncFavBtn(FavoritesManager.isFavorite(song.id))

        Glide.with(this).asBitmap().load(song.albumArtUri)
            .placeholder(R.drawable.ic_music_note).error(R.drawable.ic_music_note)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(bmp: Bitmap, t: Transition<in Bitmap>?) {
                    ivArt.setImageBitmap(bmp)
                    if (useBlurBg) applyBlurBg(bmp) else applyGradient(bmp)
                    // Update widget
                    MusicWidget.push(this@NowPlayingActivity,
                        song.title, song.artist, svc?.isPlaying == true)
                }
                override fun onLoadCleared(p: android.graphics.drawable.Drawable?) { ivArt.setImageDrawable(p) }
                override fun onLoadFailed(e: android.graphics.drawable.Drawable?) {
                    ivArt.setImageDrawable(e); defaultGradient()
                }
            })
    }

    private fun applyBlurBg(bmp: Bitmap) {
        ivBlurBg.visibility = View.VISIBLE
        root.background = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ native blur
            ivBlurBg.setImageBitmap(bmp)
            ivBlurBg.setRenderEffect(
                RenderEffect.createBlurEffect(40f, 40f, Shader.TileMode.CLAMP))
        } else {
            // Fallback: scale down for blur effect
            val small = Bitmap.createScaledBitmap(bmp,
                (bmp.width * 0.1f).toInt().coerceAtLeast(1),
                (bmp.height * 0.1f).toInt().coerceAtLeast(1), true)
            val blurred = Bitmap.createScaledBitmap(small, bmp.width, bmp.height, true)
            ivBlurBg.setImageBitmap(blurred)
            small.recycle()
        }
        // Dark overlay so text is readable
        ivBlurBg.alpha = 0.6f
        root.setBackgroundColor(0xCC000000.toInt())
    }

    private fun applyGradient(bmp: Bitmap) {
        ivBlurBg.visibility = View.GONE
        try {
            Palette.from(bmp).generate { p ->
                root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        p?.getDominantColor(0xFF1A0000.toInt()) ?: 0xFF1A0000.toInt(),
                        p?.getVibrantColor(0xFFCC0000.toInt())  ?: 0xFFCC0000.toInt(),
                        p?.getMutedColor(0xFF0F0F0F.toInt())    ?: 0xFF0F0F0F.toInt()
                    ))
            }
        } catch (e: Exception) { defaultGradient() }
    }

    private fun defaultGradient() {
        ivBlurBg.visibility = View.GONE
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF1A0000.toInt(), 0xFF880000.toInt(), 0xFF0F0F0F.toInt()))
    }

    private fun syncPlayBtn(playing: Boolean) {
        btnPlay.tag = playing
        btnPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        ivArt.animate().scaleX(if (playing) 1f else 0.8f)
            .scaleY(if (playing) 1f else 0.8f).setDuration(200).start()
    }

    private fun syncFavBtn(fav: Boolean) {
        btnFavorite.setImageResource(if (fav) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
        btnFavorite.setColorFilter(if (fav) 0xFFFF4444.toInt() else 0xFFAAAAAA.toInt())
    }

    private fun syncRepeat(mode: RepeatMode) {
        when (mode) {
            RepeatMode.OFF        -> { btnRepeat.alpha = 0.4f; btnRepeat.setImageResource(R.drawable.ic_repeat) }
            RepeatMode.REPEAT_ALL -> { btnRepeat.alpha = 1f;   btnRepeat.setImageResource(R.drawable.ic_repeat) }
            RepeatMode.REPEAT_ONE -> { btnRepeat.alpha = 1f;   btnRepeat.setImageResource(R.drawable.ic_repeat_one) }
        }
    }

    private fun fmt(ms: Int) = String.format("%d:%02d", (ms/1000)/60, (ms/1000)%60)
    override fun onServiceDisconnected(name: ComponentName?) { bound = false; svc = null }
}
