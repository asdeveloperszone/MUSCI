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

    private lateinit var rootLayout: ConstraintLayout
    private lateinit var ivAlbumArt: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvAlbum: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnRewind: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView

    private var musicService: MusicService? = null
    private var isBound = false
    private var lastSongId: Long = -1
    private val handler = Handler(Looper.getMainLooper())

    // Polls every 300ms — updates seekbar AND syncs all UI state
    private val pollRunnable = object : Runnable {
        override fun run() {
            try {
                val svc = musicService
                if (svc != null) {
                    // Seekbar
                    val pos = svc.getCurrentPosition()
                    val dur = svc.getDuration()
                    if (dur > 0) {
                        seekBar.max = dur
                        seekBar.progress = pos
                        tvCurrentTime.text = formatTime(pos)
                    }
                    // Play/pause button — always sync from source of truth
                    val playing = svc.isPlaying
                    val icon = if (playing) R.drawable.ic_pause else R.drawable.ic_play
                    if (btnPlayPause.tag != playing) {
                        btnPlayPause.setImageResource(icon)
                        btnPlayPause.tag = playing
                        val scale = if (playing) 1f else 0.78f
                        ivAlbumArt.animate().scaleX(scale).scaleY(scale).setDuration(250).start()
                    }
                    // Song changed — update all info
                    val song = svc.getCurrentSong()
                    if (song != null && song.id != lastSongId) {
                        lastSongId = song.id
                        updateSongInfo(song)
                    }
                }
            } catch (e: Exception) { }
            handler.postDelayed(this, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)
        setupViews()
    }

    override fun onStart() {
        super.onStart()
        // Bind to already-running service
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, this, Context.BIND_AUTO_CREATE)
        // Start polling
        handler.post(pollRunnable)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(pollRunnable)
        if (isBound) {
            // Clear callbacks to avoid memory leaks
            musicService?.onSongChangeListener = null
            musicService?.onPlayStateChangeListener = null
            musicService?.onShuffleChangeListener = null
            musicService?.onRepeatChangeListener = null
            try { unbindService(this) } catch (e: Exception) { }
            isBound = false
            musicService = null
        }
    }

    private fun setupViews() {
        rootLayout    = findViewById(R.id.rootLayout)
        ivAlbumArt    = findViewById(R.id.ivAlbumArt)
        tvTitle       = findViewById(R.id.tvTitle)
        tvArtist      = findViewById(R.id.tvArtist)
        tvAlbum       = findViewById(R.id.tvAlbum)
        btnPlayPause  = findViewById(R.id.btnPlayPause)
        btnNext       = findViewById(R.id.btnNext)
        btnPrevious   = findViewById(R.id.btnPrevious)
        btnShuffle    = findViewById(R.id.btnShuffle)
        btnRepeat     = findViewById(R.id.btnRepeat)
        btnClose      = findViewById(R.id.btnClose)
        btnBack       = findViewById(R.id.btnBack)
        btnRewind     = findViewById(R.id.btnRewind)
        btnForward    = findViewById(R.id.btnForward)
        seekBar       = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime   = findViewById(R.id.tvTotalTime)

        btnBack.setOnClickListener      { finish() }
        btnPlayPause.setOnClickListener { musicService?.togglePlayPause() }
        btnNext.setOnClickListener      { musicService?.playNext() }
        btnPrevious.setOnClickListener  { musicService?.playPrevious() }
        btnRewind.setOnClickListener    { musicService?.rewind() }
        btnForward.setOnClickListener   { musicService?.forward() }
        btnShuffle.setOnClickListener   { musicService?.toggleShuffle() }
        btnRepeat.setOnClickListener    { musicService?.cycleRepeat() }
        btnClose.setOnClickListener     { musicService?.stopMusic(); finish() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) musicService?.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val svc = (service as MusicService.MusicBinder).getService()
        musicService = svc
        isBound = true
        lastSongId = -1 // Reset so UI refreshes

        // Set callbacks for immediate response
        svc.onSongChangeListener = { song ->
            runOnUiThread {
                lastSongId = song.id
                updateSongInfo(song)
            }
        }
        svc.onPlayStateChangeListener = { playing ->
            runOnUiThread {
                btnPlayPause.tag = playing
                btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
                val scale = if (playing) 1f else 0.78f
                ivAlbumArt.animate().scaleX(scale).scaleY(scale).setDuration(250).start()
            }
        }
        svc.onShuffleChangeListener = { shuffle ->
            runOnUiThread { btnShuffle.alpha = if (shuffle) 1f else 0.4f }
        }
        svc.onRepeatChangeListener = { mode ->
            runOnUiThread { updateRepeatBtn(mode) }
        }

        // Load initial state
        svc.getCurrentSong()?.let { song ->
            lastSongId = song.id
            updateSongInfo(song)
        }
        btnShuffle.alpha = if (svc.isShuffle) 1f else 0.4f
        updateRepeatBtn(svc.repeatMode)
    }

    private fun updateSongInfo(song: Song) {
        tvTitle.text = song.title
        tvArtist.text = song.artist
        tvAlbum.text = song.album
        tvTotalTime.text = song.getDurationFormatted()

        Glide.with(this).asBitmap()
            .load(song.albumArtUri)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(bitmap: Bitmap, t: Transition<in Bitmap>?) {
                    ivAlbumArt.setImageBitmap(bitmap)
                    applyGradient(bitmap)
                }
                override fun onLoadCleared(p: android.graphics.drawable.Drawable?) {
                    ivAlbumArt.setImageDrawable(p)
                }
                override fun onLoadFailed(e: android.graphics.drawable.Drawable?) {
                    ivAlbumArt.setImageDrawable(e)
                    applyDefaultGradient()
                }
            })
    }

    private fun applyGradient(bitmap: Bitmap) {
        try {
            Palette.from(bitmap).generate { p ->
                val top    = p?.getDominantColor(0xFF1A0000.toInt()) ?: 0xFF1A0000.toInt()
                val mid    = p?.getVibrantColor(0xFFCC0000.toInt())  ?: 0xFFCC0000.toInt()
                val bottom = p?.getMutedColor(0xFF0F0F0F.toInt())    ?: 0xFF0F0F0F.toInt()
                rootLayout.background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, mid, bottom))
            }
        } catch (e: Exception) { applyDefaultGradient() }
    }

    private fun applyDefaultGradient() {
        rootLayout.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF1A0000.toInt(), 0xFF880000.toInt(), 0xFF0F0F0F.toInt()))
    }

    private fun updateRepeatBtn(mode: RepeatMode) {
        when (mode) {
            RepeatMode.OFF        -> { btnRepeat.alpha = 0.4f; btnRepeat.setImageResource(R.drawable.ic_repeat) }
            RepeatMode.REPEAT_ALL -> { btnRepeat.alpha = 1f;   btnRepeat.setImageResource(R.drawable.ic_repeat) }
            RepeatMode.REPEAT_ONE -> { btnRepeat.alpha = 1f;   btnRepeat.setImageResource(R.drawable.ic_repeat_one) }
        }
    }

    private fun formatTime(ms: Int): String {
        val m = (ms / 1000) / 60
        val s = (ms / 1000) % 60
        return String.format("%d:%02d", m, s)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        isBound = false
        musicService = null
    }
}
