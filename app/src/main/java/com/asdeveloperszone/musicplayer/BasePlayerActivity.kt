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

    private var miniPlayer: View?      = null
    private var ivMiniArt: ImageView?  = null
    private var tvMiniTitle: TextView? = null
    private var tvMiniArtist: TextView?= null
    private var btnMiniPlay: ImageButton? = null
    private var btnMiniPrev: ImageButton? = null
    private var btnMiniNext: ImageButton? = null
    private var btnMiniClose: ImageButton?= null

    // ── Mini Player ────────────────────────────────────────────────────────────

    protected fun initMiniPlayer() {
        miniPlayer    = findViewById(R.id.miniPlayer)     ?: return
        ivMiniArt     = findViewById(R.id.ivMiniArt)
        tvMiniTitle   = findViewById(R.id.tvMiniTitle)
        tvMiniArtist  = findViewById(R.id.tvMiniArtist)
        btnMiniPlay   = findViewById(R.id.btnMiniPlayPause)
        btnMiniPrev   = findViewById(R.id.btnMiniPrev)
        btnMiniNext   = findViewById(R.id.btnMiniNext)
        btnMiniClose  = findViewById(R.id.btnMiniClose)

        btnMiniPrev?.setOnClickListener  { svc?.previous() }
        btnMiniNext?.setOnClickListener  { svc?.next() }
        btnMiniClose?.setOnClickListener { svc?.stop(); miniPlayer?.visibility = View.GONE }
        miniPlayer?.setOnClickListener   {
            startActivity(Intent(this, NowPlayingActivity::class.java))
        }
        btnMiniPlay?.setOnClickListener  {
            svc?.togglePlayPause()
            syncMiniPlayBtn(svc?.isPlaying == true)
        }
    }

    protected fun updateMini(song: Song) {
        miniPlayer?.visibility = View.VISIBLE
        tvMiniTitle?.text  = song.title
        tvMiniArtist?.text = song.artist
        ivMiniArt?.let { iv ->
            try {
                Glide.with(this).load(song.albumArtUri)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .into(iv)
            } catch (e: Exception) { }
        }
    }

    protected fun syncMiniPlayBtn(playing: Boolean) {
        btnMiniPlay?.setImageResource(
            if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    protected fun hideMiniIfNoSong() {
        if (svc?.currentSong() == null) miniPlayer?.visibility = View.GONE
    }

    // ── Service binding ────────────────────────────────────────────────────────

    protected fun bindToService() {
        val i = Intent(this, MusicService::class.java)
        startService(i)
        bindService(i, this, Context.BIND_AUTO_CREATE)
    }

    private fun attachCallbacks() {
        svc?.let { s ->
            s.onSongChange = { song -> runOnUiThread { updateMini(song) } }
            s.onPlayState  = { p    -> runOnUiThread { syncMiniPlayBtn(p) } }
        }
    }

    private fun detachCallbacks() {
        svc?.onSongChange = null
        svc?.onPlayState  = null
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        // Always refresh when returning to this screen
        svc?.let { s ->
            s.currentSong()?.let { updateMini(it) } ?: hideMiniIfNoSong()
            syncMiniPlayBtn(s.isPlaying)
        }
        // Re-attach callbacks in case another activity stole them
        attachCallbacks()
    }

    override fun onStop() {
        super.onStop()
        detachCallbacks()
        if (bound) {
            try { unbindService(this) } catch (e: Exception) { }
            bound = false
            svc   = null
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        try {
            svc   = (service as MusicService.MusicBinder).getService()
            bound = true
            attachCallbacks()
            svc?.currentSong()?.let { updateMini(it) } ?: hideMiniIfNoSong()
            syncMiniPlayBtn(svc?.isPlaying == true)
            onServiceReady()
        } catch (e: Exception) { }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        detachCallbacks()
        bound = false
        svc   = null
    }

    /** Override in subclass to do extra work after service connects */
    open fun onServiceReady() {}
}
