package com.musicapp.player

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MainActivity : AppCompatActivity(), ServiceConnection {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SongAdapter
    private lateinit var etSearch: EditText
    private lateinit var tvSongCount: TextView
    private lateinit var miniPlayer: View
    private lateinit var ivMiniArt: ImageView
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var btnMiniPlayPause: ImageButton
    private lateinit var btnMiniNext: ImageButton

    private var musicService: MusicService? = null
    private var isBound = false
    private var allSongs: List<Song> = emptyList()

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupViews()
        checkPermissions()
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.recyclerView)
        etSearch = findViewById(R.id.etSearch)
        tvSongCount = findViewById(R.id.tvSongCount)
        miniPlayer = findViewById(R.id.miniPlayer)
        ivMiniArt = findViewById(R.id.ivMiniArt)
        tvMiniTitle = findViewById(R.id.tvMiniTitle)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        btnMiniPlayPause = findViewById(R.id.btnMiniPlayPause)
        btnMiniNext = findViewById(R.id.btnMiniNext)

        adapter = SongAdapter(emptyList()) { index ->
            musicService?.setSongList(allSongs, index)
            adapter.setCurrentPlaying(index)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterSongs(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnMiniPlayPause.setOnClickListener { musicService?.togglePlayPause() }
        btnMiniNext.setOnClickListener { musicService?.playNext() }
        miniPlayer.setOnClickListener { startActivity(Intent(this, NowPlayingActivity::class.java)) }
    }

    private fun checkPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
            startMusicService()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
            startMusicService()
        } else {
            Toast.makeText(this, "Permission required to access music files", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadSongs() {
        val songs = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val cursor: Cursor? = contentResolver.query(
            uri, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val albumId = it.getLong(albumIdCol)
                val duration = it.getLong(durationCol)
                if (duration > 0) {
                    songs.add(Song(
                        id = id,
                        title = it.getString(titleCol) ?: "Unknown",
                        artist = it.getString(artistCol) ?: "Unknown Artist",
                        album = it.getString(albumCol) ?: "Unknown Album",
                        duration = duration,
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        albumArtUri = ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"), albumId)
                    ))
                }
            }
        }
        allSongs = songs
        adapter.updateSongs(songs)
        tvSongCount.text = "${songs.size} songs"
    }

    private fun filterSongs(query: String) {
        val filtered = if (query.isEmpty()) allSongs
        else allSongs.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true)
        }
        adapter.updateSongs(filtered)
    }

    private fun startMusicService() {
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        musicService = (service as MusicService.MusicBinder).getService()
        isBound = true
        musicService?.setOnSongChangeListener { song -> runOnUiThread { updateMiniPlayer(song) } }
        musicService?.setOnPlayStateChangeListener { playing ->
            runOnUiThread {
                btnMiniPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            }
        }
    }

    private fun updateMiniPlayer(song: Song) {
        miniPlayer.visibility = View.VISIBLE
        tvMiniTitle.text = song.title
        tvMiniArtist.text = song.artist
        Glide.with(this).load(song.albumArtUri)
            .placeholder(R.drawable.ic_music_note).into(ivMiniArt)
    }

    override fun onServiceDisconnected(name: ComponentName?) { isBound = false; musicService = null }

    override fun onDestroy() {
        if (isBound) { unbindService(this); isBound = false }
        super.onDestroy()
    }
}
