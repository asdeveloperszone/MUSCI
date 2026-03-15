package com.asdeveloperszone.musicplayer

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PlaylistSongsActivity : AppCompatActivity(), ServiceConnection {

    private var svc: MusicService? = null
    private var bound = false
    private lateinit var adapter: SongAdapter
    private var songs: List<Song> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album_songs)
        PlaylistManager.init(this)

        val playlistId   = intent.getStringExtra("playlist_id") ?: return
        val playlistName = intent.getStringExtra("playlist_name") ?: "Playlist"

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvAlbumTitle).text = playlistName

        val rv = findViewById<RecyclerView>(R.id.rvSongs)
        rv.layoutManager = LinearLayoutManager(this)

        // Load all songs then filter by playlist
        loadAllSongs { allSongs ->
            songs = PlaylistManager.getSongs(playlistId, allSongs)
            adapter = SongAdapter(songs) { song, _ ->
                svc?.load(songs, songs.indexOfFirst { it.id == song.id })
            }
            rv.adapter = adapter
        }
        bindService(Intent(this, MusicService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    private fun loadAllSongs(callback: (List<Song>) -> Unit) {
        Thread {
            val list = mutableListOf<Song>()
            try {
                val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DATE_ADDED)
                contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { c ->
                    val idC = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val ttC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val arC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val alC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val duC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val aiC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val daC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    while (c.moveToNext()) {
                        val id = c.getLong(idC); val dur = c.getLong(duC); val aid = c.getLong(aiC)
                        if (dur > 0) list.add(Song(id, c.getString(ttC) ?: "Unknown",
                            c.getString(arC) ?: "Unknown", c.getString(alC) ?: "Unknown", dur,
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), aid),
                            c.getLong(daC)))
                    }
                }
            } catch (e: Exception) { }
            runOnUiThread { callback(list) }
        }.start()
    }

    override fun onServiceConnected(n: ComponentName?, s: IBinder?) {
        svc = (s as MusicService.MusicBinder).getService(); bound = true
    }
    override fun onServiceDisconnected(n: ComponentName?) { bound = false; svc = null }
    override fun onDestroy() {
        if (bound) { try { unbindService(this) } catch (e: Exception) { }; bound = false }
        super.onDestroy()
    }
}
