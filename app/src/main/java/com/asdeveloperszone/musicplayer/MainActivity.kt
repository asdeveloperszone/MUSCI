package com.asdeveloperszone.musicplayer

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
import androidx.appcompat.app.AlertDialog
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
    private lateinit var btnMiniClose: ImageButton
    private lateinit var btnSort: ImageButton

    private var musicService: MusicService? = null
    private var isBound = false
    private var allSongs: List<Song> = emptyList()
    private var currentSort = SortOption.A_TO_Z
    private var searchQuery = ""

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val NOTIFICATION_PERMISSION_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        PlayCountManager.init(this)
        setupViews()
        checkPermissions()
        requestNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            musicService?.onSongChangeListener = null
            musicService?.onPlayStateChangeListener = null
            unbindService(this)
            isBound = false
        }
    }

    override fun onResume() {
        super.onResume()
        musicService?.getCurrentSong()?.let { song ->
            updateMiniPlayer(song)
            btnMiniPlayPause.setImageResource(
                if (musicService?.isCurrentlyPlaying() == true) R.drawable.ic_pause else R.drawable.ic_play
            )
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE
                )
            }
        }
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
        btnMiniClose = findViewById(R.id.btnMiniClose)
        btnSort = findViewById(R.id.btnSort)

        adapter = SongAdapter(emptyList()) { song, _ ->
            try {
                val realIndex = allSongs.indexOfFirst { it.id == song.id }
                if (realIndex >= 0) {
                    PlayCountManager.increment(song.id)
                    musicService?.setSongList(allSongs, realIndex)
                    adapter.setCurrentPlaying(song.id)
                    updateMiniPlayer(song)
                    btnMiniPlayPause.setImageResource(R.drawable.ic_pause)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Could not play song", Toast.LENGTH_SHORT).show()
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = null

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                applyFilterAndSort()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnSort.setOnClickListener { showSortDialog() }
        btnMiniPlayPause.setOnClickListener { musicService?.togglePlayPause() }
        btnMiniNext.setOnClickListener { musicService?.playNext() }
        btnMiniClose.setOnClickListener {
            try { musicService?.stopMusic() } catch (e: Exception) { }
            miniPlayer.visibility = View.GONE
        }
        miniPlayer.setOnClickListener {
            try { startActivity(Intent(this, NowPlayingActivity::class.java)) }
            catch (e: Exception) { }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (searchQuery.isNotEmpty()) {
            etSearch.text.clear()
            searchQuery = ""
            applyFilterAndSort()
        } else {
            super.onBackPressed()
        }
    }

    private fun showSortDialog() {
        val options = SortOption.values().map { it.label }.toTypedArray()
        AlertDialog.Builder(this, R.style.SortDialogTheme)
            .setTitle("Sort Songs")
            .setSingleChoiceItems(options, SortOption.values().indexOf(currentSort)) { dialog, which ->
                currentSort = SortOption.values()[which]
                applyFilterAndSort()
                dialog.dismiss()
            }
            .show()
    }

    private fun applyFilterAndSort() {
        var list = if (searchQuery.isEmpty()) allSongs
        else allSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
        list = when (currentSort) {
            SortOption.A_TO_Z       -> list.sortedBy { it.title.lowercase() }
            SortOption.Z_TO_A       -> list.sortedByDescending { it.title.lowercase() }
            SortOption.NEWEST_FIRST -> list.sortedByDescending { it.dateAdded }
            SortOption.OLDEST_FIRST -> list.sortedBy { it.dateAdded }
            SortOption.MOST_LISTENED -> list.sortedByDescending { PlayCountManager.getCount(it.id) }
        }
        adapter.updateSongs(list)
        tvSongCount.text = if (searchQuery.isEmpty()) "${allSongs.size} songs"
                           else "${list.size} / ${allSongs.size}"
    }

    private fun checkPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        }
    }

    private fun loadSongs() {
        Thread {
            val songs = mutableListOf<Song>()
            try {
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DATE_ADDED
                )
                val cursor: Cursor? = contentResolver.query(
                    uri, projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null, "${MediaStore.Audio.Media.TITLE} ASC"
                )
                cursor?.use {
                    val idCol      = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol   = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol  = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol   = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durCol     = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val albumIdCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val dateCol    = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    while (it.moveToNext()) {
                        try {
                            val id = it.getLong(idCol)
                            val dur = it.getLong(durCol)
                            val albumId = it.getLong(albumIdCol)
                            if (dur > 0) songs.add(Song(
                                id = id,
                                title = it.getString(titleCol) ?: "Unknown",
                                artist = it.getString(artistCol) ?: "Unknown Artist",
                                album = it.getString(albumCol) ?: "Unknown Album",
                                duration = dur,
                                uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                                albumArtUri = ContentUris.withAppendedId(
                                    Uri.parse("content://media/external/audio/albumart"), albumId),
                                dateAdded = it.getLong(dateCol)
                            ))
                        } catch (e: Exception) { }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error loading songs", Toast.LENGTH_SHORT).show() }
            }
            runOnUiThread {
                allSongs = songs
                applyFilterAndSort()
                tvSongCount.text = "${songs.size} songs"
                musicService?.getCurrentSong()?.let { updateMiniPlayer(it) }
            }
        }.start()
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        try {
            musicService = (service as MusicService.MusicBinder).getService()
            isBound = true
            musicService?.getCurrentSong()?.let { song -> updateMiniPlayer(song) }
            musicService?.onSongChangeListener = { song ->
                runOnUiThread {
                    try {
                        updateMiniPlayer(song)
                        adapter.setCurrentPlaying(song.id)
                    } catch (e: Exception) { }
                }
            }
            musicService?.onPlayStateChangeListener = { playing ->
                runOnUiThread {
                    try {
                        btnMiniPlayPause.setImageResource(
                            if (playing) R.drawable.ic_pause else R.drawable.ic_play)
                    } catch (e: Exception) { }
                }
            }
        } catch (e: Exception) { }
    }

    private fun updateMiniPlayer(song: Song) {
        miniPlayer.visibility = View.VISIBLE
        tvMiniTitle.text = song.title
        tvMiniArtist.text = song.artist
        Glide.with(this)
            .load(song.albumArtUri)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .into(ivMiniArt)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        isBound = false
        musicService = null
    }
}
