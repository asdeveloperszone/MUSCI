package com.asdeveloperszone.musicplayer

import android.content.*
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
    private lateinit var seekBar: SeekBar
    private lateinit var tvPos: TextView
    private lateinit var tvDur: TextView

    private var svc: MusicService? = null
    private var bound = false
    private var lastSongId = -1L
    private val handler = Handler(Looper.getMainLooper())

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)
        initViews()
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, MusicService::class.java), this, Context.BIND_AUTO_CREATE)
        handler.post(ticker)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(ticker)
        if (bound) {
            svc?.apply { onSongChange = null; onPlayState = null; onShuffleChange = null; onRepeatChange = null }
            try { unbindService(this) } catch (e: Exception) { }
            bound = false; svc = null
        }
    }

    private fun initViews() {
        root        = findViewById(R.id.rootLayout)
        ivArt       = findViewById(R.id.ivAlbumArt)
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
        seekBar     = findViewById(R.id.seekBar)
        tvPos       = findViewById(R.id.tvCurrentTime)
        tvDur       = findViewById(R.id.tvTotalTime)

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

        btnFavorite.setOnClickListener {
            val song = svc?.currentSong() ?: return@setOnClickListener
            val isFav = FavoritesManager.toggle(song.id)
            syncFavoriteBtn(isFav)
            Toast.makeText(this,
                if (isFav) "❤ Added to Favorites" else "Removed from Favorites",
                Toast.LENGTH_SHORT).show()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) { if (user) svc?.seekTo(p) }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        svc = (service as MusicService.MusicBinder).getService()
        bound = true; lastSongId = -1L

        svc!!.onSongChange    = { song -> runOnUiThread { lastSongId = song.id; loadSong(song) } }
        svc!!.onPlayState     = { p    -> runOnUiThread { syncPlayBtn(p) } }
        svc!!.onShuffleChange = { s    -> runOnUiThread { btnShuffle.alpha = if (s) 1f else 0.4f } }
        svc!!.onRepeatChange  = { r    -> runOnUiThread { syncRepeat(r) } }

        svc!!.currentSong()?.let { loadSong(it); lastSongId = it.id }
        btnShuffle.alpha = if (svc!!.isShuffle) 1f else 0.4f
        syncRepeat(svc!!.repeatMode)
        syncPlayBtn(svc!!.isPlaying)
    }

    private fun loadSong(song: Song) {
        tvTitle.text = song.title; tvArtist.text = song.artist
        tvAlbum.text = song.album; tvDur.text = song.getDurationFormatted()
        syncFavoriteBtn(FavoritesManager.isFavorite(song.id))

        Glide.with(this).asBitmap().load(song.albumArtUri)
            .placeholder(R.drawable.ic_music_note).error(R.drawable.ic_music_note)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(bmp: Bitmap, t: Transition<in Bitmap>?) {
                    ivArt.setImageBitmap(bmp); applyGradient(bmp)
                }
                override fun onLoadCleared(p: android.graphics.drawable.Drawable?) { ivArt.setImageDrawable(p) }
                override fun onLoadFailed(e: android.graphics.drawable.Drawable?) { ivArt.setImageDrawable(e); defaultGradient() }
            })
    }

    private fun syncPlayBtn(playing: Boolean) {
        btnPlay.tag = playing
        btnPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        ivArt.animate().scaleX(if (playing) 1f else 0.8f)
            .scaleY(if (playing) 1f else 0.8f).setDuration(200).start()
    }

    private fun syncFavoriteBtn(isFav: Boolean) {
        btnFavorite.setImageResource(if (isFav) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
        btnFavorite.setColorFilter(if (isFav) 0xFFFF4444.toInt() else 0xFFAAAAAA.toInt())
    }

    private fun syncRepeat(mode: RepeatMode) {
        when (mode) {
            RepeatMode.OFF        -> { btnRepeat.alpha = 0.4f; btnRepeat.setImageResource(R.drawable.ic_repeat) }
            RepeatMode.REPEAT_ALL -> { btnRepeat.alpha = 1f;   btnRepeat.setImageResource(R.drawable.ic_repeat) }
            RepeatMode.REPEAT_ONE -> { btnRepeat.alpha = 1f;   btnRepeat.setImageResource(R.drawable.ic_repeat_one) }
        }
    }

    private fun applyGradient(bmp: Bitmap) {
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
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF1A0000.toInt(), 0xFF880000.toInt(), 0xFF0F0F0F.toInt()))
    }

    private fun fmt(ms: Int) = String.format("%d:%02d", (ms/1000)/60, (ms/1000)%60)
    override fun onServiceDisconnected(name: ComponentName?) { bound = false; svc = null }
}
