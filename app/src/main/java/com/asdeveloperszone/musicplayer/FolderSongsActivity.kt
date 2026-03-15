package com.asdeveloperszone.musicplayer

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

// Cache to pass song list between activities without serialization
object FolderSongsCache {
    var songs: List<Song> = emptyList()
}

class FolderSongsActivity : AppCompatActivity(), ServiceConnection {

    private var svc: MusicService? = null
    private var bound = false
    private lateinit var adapter: SongAdapter
    private lateinit var miniPlayer: View
    private lateinit var ivMiniArt: ImageView
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var btnMiniPlay: ImageButton
    private lateinit var btnMiniNext: ImageButton
    private lateinit var btnMiniPrev: ImageButton
    private lateinit var btnMiniClose: ImageButton

    private val songs get() = FolderSongsCache.songs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_songs)

        val folderName = intent.getStringExtra("folder_name") ?: "Folder"
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvAlbumTitle).text = folderName

        miniPlayer   = findViewById(R.id.miniPlayer)
        ivMiniArt    = findViewById(R.id.ivMiniArt)
        tvMiniTitle  = findViewById(R.id.tvMiniTitle)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        btnMiniPlay  = findViewById(R.id.btnMiniPlayPause)
        btnMiniNext  = findViewById(R.id.btnMiniNext)
        btnMiniPrev  = findViewById(R.id.btnMiniPrev)
        btnMiniClose = findViewById(R.id.btnMiniClose)

        val rv = findViewById<RecyclerView>(R.id.rvSongs)
        rv.layoutManager = LinearLayoutManager(this)

        adapter = SongAdapter(songs) { song, _ ->
            val idx = songs.indexOfFirst { it.id == song.id }
            if (idx >= 0) {
                PlayCountManager.increment(song.id)
                RecentlyPlayedManager.add(song.id)
                svc?.load(songs, idx)
                adapter.setCurrentPlaying(song.id)
                updateMini(song)
                btnMiniPlay.setImageResource(R.drawable.ic_pause)
            }
        }
        rv.adapter = adapter

        btnMiniPrev.setOnClickListener  { svc?.previous() }
        btnMiniPlay.setOnClickListener  {
            svc?.togglePlayPause()
            btnMiniPlay.setImageResource(
                if (svc?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play)
        }
        btnMiniNext.setOnClickListener  { svc?.next() }
        btnMiniClose.setOnClickListener { svc?.stop(); miniPlayer.visibility = View.GONE }
        miniPlayer.setOnClickListener   { startActivity(Intent(this, NowPlayingActivity::class.java)) }

        bindService(Intent(this, MusicService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        svc?.let { s ->
            s.currentSong()?.let { updateMini(it) }
            btnMiniPlay.setImageResource(
                if (s.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        try {
            svc = (service as MusicService.MusicBinder).getService()
            bound = true
            svc?.currentSong()?.let { updateMini(it) }
            btnMiniPlay.setImageResource(
                if (svc?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play)
            svc?.onSongChange  = { song -> runOnUiThread { updateMini(song); adapter.setCurrentPlaying(song.id) } }
            svc?.onPlayState   = { p    -> runOnUiThread { btnMiniPlay.setImageResource(if (p) R.drawable.ic_pause else R.drawable.ic_play) } }
        } catch (e: Exception) { }
    }

    private fun updateMini(song: Song) {
        miniPlayer.visibility = View.VISIBLE
        tvMiniTitle.text  = song.title
        tvMiniArtist.text = song.artist
        Glide.with(this).load(song.albumArtUri)
            .placeholder(R.drawable.ic_music_note).error(R.drawable.ic_music_note).into(ivMiniArt)
    }

    override fun onServiceDisconnected(n: ComponentName?) { bound = false; svc = null }
    override fun onDestroy() {
        if (bound) {
            svc?.onSongChange = null; svc?.onPlayState = null
            try { unbindService(this) } catch (e: Exception) { }
            bound = false
        }
        super.onDestroy()
    }
}
