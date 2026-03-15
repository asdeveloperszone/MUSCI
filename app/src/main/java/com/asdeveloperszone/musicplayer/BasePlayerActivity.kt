package com.asdeveloperszone.musicplayer

import android.content.*
import android.os.IBinder
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

abstract class BasePlayerActivity : AppCompatActivity(), ServiceConnection {

    protected var svc: MusicService? = null
    protected var bound = false

    private var miniPlayer: View? = null
    private var ivMiniArt: ImageView? = null
    private var tvMiniTitle: TextView? = null
    private var tvMiniArtist: TextView? = null
    private var btnMiniPlay: ImageButton? = null
    private var btnMiniPrev: ImageButton? = null
    private var btnMiniNext: ImageButton? = null
    private var btnMiniClose: ImageButton? = null

    protected fun initMiniPlayer() {
        miniPlayer   = findViewById(R.id.miniPlayer)     ?: return
        ivMiniArt    = findViewById(R.id.ivMiniArt)
        tvMiniTitle  = findViewById(R.id.tvMiniTitle)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        btnMiniPlay  = findViewById(R.id.btnMiniPlayPause)
        btnMiniPrev  = findViewById(R.id.btnMiniPrev)
        btnMiniNext  = findViewById(R.id.btnMiniNext)
        btnMiniClose = findViewById(R.id.btnMiniClose)

        btnMiniPrev?.setOnClickListener  { svc?.previous() }
        btnMiniPlay?.setOnClickListener  {
            svc?.togglePlayPause()
            syncMiniPlayBtn(svc?.isPlaying == true)
        }
        btnMiniNext?.setOnClickListener  { svc?.next() }
        btnMiniClose?.setOnClickListener {
            svc?.stop()
            miniPlayer?.visibility = View.GONE
        }
        miniPlayer?.setOnClickListener {
            startActivity(Intent(this, NowPlayingActivity::class.java))
        }
    }

    protected fun bindToService() {
        val i = Intent(this, MusicService::class.java)
        startService(i)
        bindService(i, this, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        refreshMiniPlayer()
    }

    protected fun refreshMiniPlayer() {
        svc?.let { s ->
            s.currentSong()?.let { updateMini(it) }
            syncMiniPlayBtn(s.isPlaying)
        }
    }

    protected fun updateMini(song: Song) {
        val mp = miniPlayer ?: return
        mp.visibility = View.VISIBLE
        tvMiniTitle?.text  = song.title
        tvMiniArtist?.text = song.artist
        ivMiniArt?.let { iv ->
            Glide.with(this).load(song.albumArtUri)
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .into(iv)
        }
    }

    protected fun syncMiniPlayBtn(playing: Boolean) {
        btnMiniPlay?.setImageResource(
            if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    protected fun attachServiceCallbacks() {
        svc?.onSongChange = { song ->
            runOnUiThread { updateMini(song) }
        }
        svc?.onPlayState = { playing ->
            runOnUiThread { syncMiniPlayBtn(playing) }
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        try {
            svc = (service as MusicService.MusicBinder).getService()
            bound = true
            attachServiceCallbacks()
            refreshMiniPlayer()
            onServiceReady()
        } catch (e: Exception) { }
    }

    // Override in subclass if needed
    open fun onServiceReady() {}

    override fun onServiceDisconnected(name: ComponentName?) {
        svc?.onSongChange = null
        svc?.onPlayState  = null
        bound = false
        svc = null
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            svc?.onSongChange = null
            svc?.onPlayState  = null
            try { unbindService(this) } catch (e: Exception) { }
            bound = false
            svc = null
        }
    }
}
