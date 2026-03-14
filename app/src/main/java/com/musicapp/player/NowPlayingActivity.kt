package com.musicapp.player

import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class NowPlayingActivity : AppCompatActivity(), ServiceConnection {

    private lateinit var ivAlbumArt: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtist: TextView
    private lateinit var tvAlbum: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnBack: ImageButton

    private var musicService: MusicService? = null
    private var isBound = false
    private val handler = Handler(Looper.getMainLooper())

    private val updateSeekBar = object : Runnable {
        override fun run() {
            musicService?.let {
                val position = it.getCurrentPosition()
                val duration = it.getDuration()
                if (duration > 0) {
                    seekBar.max = duration
                    seekBar.progress = position
                    tvCurrentTime.text = formatTime(position)
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)
        setupViews()
        bindService(Intent(this, MusicService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    private fun setupViews() {
        ivAlbumArt = findViewById(R.id.ivAlbumArt)
        tvTitle = findViewById(R.id.tvTitle)
        tvArtist = findViewById(R.id.tvArtist)
        tvAlbum = findViewById(R.id.tvAlbum)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrevious = findViewById(R.id.btnPrevious)
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }
        btnPlayPause.setOnClickListener { musicService?.togglePlayPause() }
        btnNext.setOnClickListener { musicService?.playNext() }
        btnPrevious.setOnClickListener { musicService?.playPrevious() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) musicService?.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        musicService = (service as MusicService.MusicBinder).getService()
        isBound = true
        musicService?.getCurrentSong()?.let { updateUI(it) }
        musicService?.setOnSongChangeListener { song -> runOnUiThread { updateUI(song) } }
        musicService?.setOnPlayStateChangeListener { playing ->
            runOnUiThread {
                btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            }
        }
        handler.post(updateSeekBar)
    }

    private fun updateUI(song: Song) {
        tvTitle.text = song.title
        tvArtist.text = song.artist
        tvAlbum.text = song.album
        tvTotalTime.text = song.getDurationFormatted()
        Glide.with(this).load(song.albumArtUri)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .into(ivAlbumArt)
        btnPlayPause.setImageResource(
            if (musicService?.isPlaying() == true) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun formatTime(ms: Int): String {
        val minutes = (ms / 1000) / 60
        val seconds = (ms / 1000) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onServiceDisconnected(name: ComponentName?) { isBound = false; musicService = null }

    override fun onDestroy() {
        handler.removeCallbacks(updateSeekBar)
        if (isBound) { unbindService(this); isBound = false }
        super.onDestroy()
    }
}
