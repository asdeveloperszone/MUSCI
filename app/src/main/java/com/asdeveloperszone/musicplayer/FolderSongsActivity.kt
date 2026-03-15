package com.asdeveloperszone.musicplayer

import android.os.Bundle
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

object FolderSongsCache { var songs: List<Song> = emptyList() }

class FolderSongsActivity : BasePlayerActivity() {

    private lateinit var adapter: SongAdapter
    private val songs get() = FolderSongsCache.songs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album_songs)

        val folderName = intent.getStringExtra("folder_name") ?: "Folder"
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvAlbumTitle).text = folderName
        initMiniPlayer()

        val rv = findViewById<RecyclerView>(R.id.rvSongs)
        rv.layoutManager = LinearLayoutManager(this)

        adapter = SongAdapter(songs) { song, _ ->
            val idx = songs.indexOfFirst { it.id == song.id }
            if (idx >= 0) {
                PlayCountManager.increment(song.id)
                RecentlyPlayedManager.add(song.id)
                svc?.load(songs, idx)
                adapter.setCurrentPlaying(song.id)
                updateMini(song); syncMiniPlayBtn(true)
            }
        }
        rv.adapter = adapter
        bindToService()
    }

    override fun onServiceReady() {
        svc?.currentSong()?.let { adapter.setCurrentPlaying(it.id) }
    }
}
