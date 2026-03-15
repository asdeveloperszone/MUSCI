package com.asdeveloperszone.musicplayer

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AlbumSongsActivity : BasePlayerActivity() {

    private lateinit var adapter: SongAdapter
    private var songs: List<Song> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album_songs)

        val albumId   = intent.getLongExtra("album_id", -1)
        val albumName = intent.getStringExtra("album_name") ?: "Album"

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvAlbumTitle).text = albumName
        initMiniPlayer()

        val rv = findViewById<RecyclerView>(R.id.rvSongs)
        rv.layoutManager = LinearLayoutManager(this)

        songs = loadSongs(albumId)
        adapter = SongAdapter(songs) { song, _ ->
            val idx = songs.indexOfFirst { it.id == song.id }
            if (idx >= 0) {
                PlayCountManager.increment(song.id)
                RecentlyPlayedManager.add(song.id)
                svc?.load(songs, idx)
                adapter.setCurrentPlaying(song.id)
                updateMini(song)
                syncMiniPlayBtn(true)
            }
        }
        rv.adapter = adapter
        bindToService()
    }

    override fun onServiceReady() {
        svc?.currentSong()?.let { adapter.setCurrentPlaying(it.id) }
    }

    private fun loadSongs(albumId: Long): List<Song> {
        val list = mutableListOf<Song>()
        try {
            val proj = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATE_ADDED)
            contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                "${MediaStore.Audio.Media.ALBUM_ID} = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0",
                arrayOf(albumId.toString()), "${MediaStore.Audio.Media.TRACK} ASC")?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTt = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iAr = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAl = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iDu = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iAi = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDa = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val id = c.getLong(iId); val aid = c.getLong(iAi)
                    list.add(Song(id, c.getString(iTt) ?: "Unknown",
                        c.getString(iAr) ?: "Unknown", c.getString(iAl) ?: "Unknown",
                        c.getLong(iDu),
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), aid),
                        c.getLong(iDa)))
                }
            }
        } catch (e: Exception) { }
        return list
    }
}
