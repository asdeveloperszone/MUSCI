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

class ArtistSongsActivity : AppCompatActivity(), ServiceConnection {
    private var svc: MusicService? = null
    private var bound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album_songs)

        val artistId   = intent.getLongExtra("artist_id", -1)
        val artistName = intent.getStringExtra("artist_name") ?: "Artist"

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvAlbumTitle).text = artistName

        val rv = findViewById<RecyclerView>(R.id.rvSongs)
        rv.layoutManager = LinearLayoutManager(this)

        val songs = loadArtistSongs(artistId)
        rv.adapter = SongAdapter(songs) { song, _ ->
            svc?.load(songs, songs.indexOfFirst { it.id == song.id })
        }

        bindService(Intent(this, MusicService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    private fun loadArtistSongs(artistId: Long): List<Song> {
        val list = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED
        )
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
            "${MediaStore.Audio.Media.ARTIST_ID} = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0",
            arrayOf(artistId.toString()),
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { c ->
            val idC  = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val aidC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val datC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id  = c.getLong(idC)
                val aid = c.getLong(aidC)
                list.add(Song(id,
                    c.getString(titC) ?: "Unknown",
                    c.getString(artC) ?: "Unknown",
                    c.getString(albC) ?: "Unknown",
                    c.getLong(durC),
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), aid),
                    c.getLong(datC)
                ))
            }
        }
        return list
    }

    override fun onServiceConnected(n: ComponentName?, s: IBinder?) {
        svc = (s as MusicService.MusicBinder).getService(); bound = true
    }
    override fun onServiceDisconnected(n: ComponentName?) { bound = false; svc = null }
    override fun onDestroy() {
        if (bound) { unbindService(this); bound = false }
        super.onDestroy()
    }
}
