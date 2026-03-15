package com.asdeveloperszone.musicplayer

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class AlbumSongsActivity : AppCompatActivity(), ServiceConnection {
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
                svc?.load(songs, idx)
                adapter.setCurrentPlaying(song.id)
                updateMini(song); btnMiniPlay.setImageResource(R.drawable.ic_pause)
            }
        }
        rv.adapter = adapter
        bindService(Intent(this, MusicService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    private fun initMiniPlayer() {
        miniPlayer   = findViewById(R.id.miniPlayer)
        ivMiniArt    = findViewById(R.id.ivMiniArt)
        tvMiniTitle  = findViewById(R.id.tvMiniTitle)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        btnMiniPlay  = findViewById(R.id.btnMiniPlayPause)
        btnMiniNext  = findViewById(R.id.btnMiniNext)
        btnMiniPrev  = findViewById(R.id.btnMiniPrev)
        btnMiniClose = findViewById(R.id.btnMiniClose)
        btnMiniPrev.setOnClickListener  { svc?.previous() }
        btnMiniPlay.setOnClickListener  {
            svc?.togglePlayPause()
            btnMiniPlay.setImageResource(if (svc?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play)
        }
        btnMiniNext.setOnClickListener  { svc?.next() }
        btnMiniClose.setOnClickListener { svc?.stop(); miniPlayer.visibility = View.GONE }
        miniPlayer.setOnClickListener   { startActivity(Intent(this, NowPlayingActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        svc?.currentSong()?.let { updateMini(it) }
        btnMiniPlay.setImageResource(if (svc?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun loadSongs(albumId: Long): List<Song> {
        val list = mutableListOf<Song>()
        val proj = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED)
        contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Audio.Media.ALBUM_ID} = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0",
            arrayOf(albumId.toString()), "${MediaStore.Audio.Media.TRACK} ASC")?.use { c ->
            val idC=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val ttC=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val arC=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val alC=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duC=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val aiC=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val daC=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id=c.getLong(idC); val aid=c.getLong(aiC)
                list.add(Song(id, c.getString(ttC)?:"Unknown", c.getString(arC)?:"Unknown",
                    c.getString(alC)?:"Unknown", c.getLong(duC),
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), aid),
                    c.getLong(daC)))
            }
        }
        return list
    }

    private fun updateMini(song: Song) {
        miniPlayer.visibility = View.VISIBLE
        tvMiniTitle.text = song.title; tvMiniArtist.text = song.artist
        Glide.with(this).load(song.albumArtUri)
            .placeholder(R.drawable.ic_music_note).into(ivMiniArt)
    }

    override fun onServiceConnected(n: ComponentName?, s: IBinder?) {
        try {
            svc = (s as MusicService.MusicBinder).getService(); bound = true
            svc?.currentSong()?.let { updateMini(it) }
            btnMiniPlay.setImageResource(if (svc?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play)
            svc?.onSongChange = { song -> runOnUiThread { updateMini(song); adapter.setCurrentPlaying(song.id) } }
            svc?.onPlayState  = { p -> runOnUiThread { btnMiniPlay.setImageResource(if (p) R.drawable.ic_pause else R.drawable.ic_play) } }
        } catch (e: Exception) { }
    }
    override fun onServiceDisconnected(n: ComponentName?) { bound = false; svc = null }
    override fun onDestroy() {
        if (bound) { svc?.onSongChange=null; svc?.onPlayState=null; try { unbindService(this) } catch (e: Exception) { }; bound=false }
        super.onDestroy()
    }
}
