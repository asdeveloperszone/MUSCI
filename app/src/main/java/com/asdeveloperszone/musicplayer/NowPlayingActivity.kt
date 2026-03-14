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
    private val handler = Handler(Looper.getMainLooper())

    private val updateSeekBar = object : Runnable {
        override fun run() {
            try {
                musicService?.let {
                    val pos = it.getCurrentPosition()
                    val dur = it.getDuration()
                    if (dur > 0) {
                        seekBar.max = dur
                        seekBar.progress = pos
                        tvCurrentTime.text = formatTime(pos)
                    }
                }
            } catch (e: Exception) { }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)
        setupViews()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(updateSeekBar)
        if (isBound) {
            musicService?.onSongChangeListener = null
            musicService?.onPlayStateChangeListener = null
            musicService?.onShuffleChangeListener = null
            musicService?.onRepeatChangeListener = null
            unbindService(this)
            isBound = false
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

        btnBack.setOnClickListener { finish() }
        btnPlayPause.setOnClickListener { musicService?.togglePlayPause() }
        btnNext.setOnClickListener { musicService?.playNext() }
        btnPrevious.setOnClickListener { musicService?.playPrevious() }
        btnClose.setOnClickListener { musicService?.stopMusic(); finish() }
        btnShuffle.setOnClickListener { musicService?.toggleShuffle() }
        btnRepeat.setOnClickListener { musicService?.cycleRepeat() }
        btnRewind.setOnClickListener { musicService?.rewind() }
        btnForward.setOnClickListener { musicService?.forward() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) musicService?.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        musicService = (service as MusicService.MusicBinder).getService()
        isBound = true

        musicService?.getCurrentSong()?.let { updateUI(it) }
        updateShuffleBtn(musicService?.isShuffle ?: false)
        updateRepeatBtn(musicService?.repeatMode ?: RepeatMode.OFF)
        updatePlayBtn(musicService?.isCurrentlyPlaying() ?: false)

        musicService?.onSongChangeListener = { song -> runOnUiThread { updateUI(song) } }
        musicService?.onPlayStateChangeListener = { playing -> runOnUiThread { updatePlayBtn(playing) } }
        musicService?.onShuffleChangeListener = { shuffle -> runOnUiThread { updateShuffleBtn(shuffle) } }
        musicService?.onRepeatChangeListener = { mode -> runOnUiThread { updateRepeatBtn(mode) } }
        handler.post(updateSeekBar)
    }

    private fun updateUI(song: Song) {
        tvTitle.text = song.title
        tvArtist.text = song.artist
        tvAlbum.text = song.album
        tvTotalTime.text = song.getDurationFormatted()
        updatePlayBtn(musicService?.isCurrentlyPlaying() ?: false)

        Glide.with(this).asBitmap()
            .load(song.albumArtUri)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
                    ivAlbumArt.setImageBitmap(bitmap)
                    applyDynamicGradient(bitmap)
                }
                override fun onLoadCleared(p: android.graphics.drawable.Drawable?) { ivAlbumArt.setImageDrawable(p) }
                override fun onLoadFailed(e: android.graphics.drawable.Drawable?) {
                    ivAlbumArt.setImageDrawable(e); applyDefaultGradient()
                }
            })
    }

    private fun updatePlayBtn(playing: Boolean) {
        btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        ivAlbumArt.animate().scaleX(if (playing) 1f else 0.78f)
            .scaleY(if (playing) 1f else 0.78f).setDuration(300).start()
    }

    private fun applyDynamicGradient(bitmap: Bitmap) {
        try {
            Palette.from(bitmap).generate { palette ->
                val dominant = palette?.getDominantColor(0xFF1A0000.toInt()) ?: 0xFF1A0000.toInt()
                val vibrant  = palette?.getVibrantColor(0xFFCC0000.toInt()) ?: 0xFFCC0000.toInt()
                val muted    = palette?.getMutedColor(0xFF0F0F0F.toInt()) ?: 0xFF0F0F0F.toInt()
                rootLayout.background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(dominant, vibrant, muted))
            }
        } catch (e: Exception) { applyDefaultGradient() }
    }

    private fun applyDefaultGradient() {
        rootLayout.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF1A0000.toInt(), 0xFF880000.toInt(), 0xFF0F0F0F.toInt()))
    }

    private fun updateShuffleBtn(shuffle: Boolean) {
        btnShuffle.alpha = if (shuffle) 1f else 0.4f
    }

    private fun updateRepeatBtn(mode: RepeatMode) {
        when (mode) {
            RepeatMode.OFF        -> { btnRepeat.alpha = 0.4f; btnRepeat.setImageResource(R.drawable.ic_repeat) }
            RepeatMode.REPEAT_ALL -> { btnRepeat.alpha = 1f;   btnRepeat.setImageResource(R.drawable.ic_repeat) }
            RepeatMode.REPEAT_ONE -> { btnRepeat.alpha = 1f;   btnRepeat.setImageResource(R.drawable.ic_repeat_one) }
        }
    }

    private fun formatTime(ms: Int): String {
        val m = (ms / 1000) / 60; val s = (ms / 1000) % 60
        return String.format("%d:%02d", m, s)
    }

    override fun onServiceDisconnected(name: ComponentName?) { isBound = false; musicService = null }
}
