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
        private const val PERM_AUDIO = 100
        private const val PERM_NOTIF = 101
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

    override fun onResume() {
        super.onResume()
        // Refresh mini player when returning from NowPlaying
        musicService?.let { svc ->
            svc.getCurrentSong()?.let { updateMiniPlayer(it) }
            btnMiniPlayPause.setImageResource(
                if (svc.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            musicService?.onSongChangeListener = null
            musicService?.onPlayStateChangeListener = null
            try { unbindService(this) } catch (e: Exception) { }
            isBound = false
            musicService = null
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERM_NOTIF)
            }
        }
    }

    private fun setupViews() {
        recyclerView     = findViewById(R.id.recyclerView)
        etSearch         = findViewById(R.id.etSearch)
        tvSongCount      = findViewById(R.id.tvSongCount)
        miniPlayer       = findViewById(R.id.miniPlayer)
        ivMiniArt        = findViewById(R.id.ivMiniArt)
        tvMiniTitle      = findViewById(R.id.tvMiniTitle)
        tvMiniArtist     = findViewById(R.id.tvMiniArtist)
        btnMiniPlayPause = findViewById(R.id.btnMiniPlayPause)
        btnMiniNext      = findViewById(R.id.btnMiniNext)
        btnMiniClose     = findViewById(R.id.btnMiniClose)
        btnSort          = findViewById(R.id.btnSort)

        adapter = SongAdapter(emptyList()) { song, _ ->
            try {
                val idx = allSongs.indexOfFirst { it.id == song.id }
                if (idx >= 0) {
                    PlayCountManager.increment(song.id)
                    musicService?.setSongList(allSongs, idx)
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

        btnMiniPlayPause.setOnClickListener {
            musicService?.togglePlayPause()
            musicService?.let { svc ->
                btnMiniPlayPause.setImageResource(
                    if (svc.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            }
        }
        btnMiniNext.setOnClickListener  { musicService?.playNext() }
        btnMiniClose.setOnClickListener {
            musicService?.stopMusic()
            miniPlayer.visibility = View.GONE
        }
        miniPlayer.setOnClickListener {
            startActivity(Intent(this, NowPlayingActivity::class.java))
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (searchQuery.isNotEmpty()) {
            etSearch.text.clear()
            searchQuery = ""
            applyFilterAndSort()
        } else super.onBackPressed()
    }

    private fun showSortDialog() {
        val options = SortOption.values().map { it.label }.toTypedArray()
        AlertDialog.Builder(this, R.style.SortDialogTheme)
            .setTitle("Sort Songs")
            .setSingleChoiceItems(options, SortOption.values().indexOf(currentSort)) { dialog, which ->
                currentSort = SortOption.values()[which]
                applyFilterAndSort()
                dialog.dismiss()
            }.show()
    }

    private fun applyFilterAndSort() {
        var list = if (searchQuery.isEmpty()) allSongs
        else allSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
        list = when (currentSort) {
            SortOption.A_TO_Z        -> list.sortedBy { it.title.lowercase() }
            SortOption.Z_TO_A        -> list.sortedByDescending { it.title.lowercase() }
            SortOption.NEWEST_FIRST  -> list.sortedByDescending { it.dateAdded }
            SortOption.OLDEST_FIRST  -> list.sortedBy { it.dateAdded }
            SortOption.MOST_LISTENED -> list.sortedByDescending { PlayCountManager.getCount(it.id) }
        }
        adapter.updateSongs(list)
        tvSongCount.text = if (searchQuery.isEmpty()) "${allSongs.size} songs"
                           else "${list.size} / ${allSongs.size}"
    }

    private fun checkPermissions() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED)
            loadSongs()
        else
            ActivityCompat.requestPermissions(this, arrayOf(perm), PERM_AUDIO)
    }

    override fun onRequestPermissionsResult(rc: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(rc, perms, results)
        if (rc == PERM_AUDIO && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED)
            loadSongs()
    }

    private fun loadSongs() {
        Thread {
            val songs = mutableListOf<Song>()
            try {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DATE_ADDED
                )
                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
                    "${MediaStore.Audio.Media.TITLE} ASC"
                )?.use { c ->
                    val idCol  = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val aidCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val datCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    while (c.moveToNext()) {
                        try {
                            val id  = c.getLong(idCol)
                            val dur = c.getLong(durCol)
                            val aid = c.getLong(aidCol)
                            if (dur > 0) songs.add(Song(
                                id       = id,
                                title    = c.getString(titCol) ?: "Unknown",
                                artist   = c.getString(artCol) ?: "Unknown Artist",
                                album    = c.getString(albCol) ?: "Unknown Album",
                                duration = dur,
                                uri      = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                                albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), aid),
                                dateAdded = c.getLong(datCol)
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
        musicService = (service as MusicService.MusicBinder).getService()
        isBound = true

        musicService?.getCurrentSong()?.let { updateMiniPlayer(it) }
        btnMiniPlayPause.setImageResource(
            if (musicService?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play)

        musicService?.onSongChangeListener = { song ->
            runOnUiThread {
                updateMiniPlayer(song)
                adapter.setCurrentPlaying(song.id)
            }
        }
        musicService?.onPlayStateChangeListener = { playing ->
            runOnUiThread {
                btnMiniPlayPause.setImageResource(
                    if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            }
        }
    }

    private fun updateMiniPlayer(song: Song) {
        miniPlayer.visibility = View.VISIBLE
        tvMiniTitle.text = song.title
        tvMiniArtist.text = song.artist
        Glide.with(this).load(song.albumArtUri)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .into(ivMiniArt)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        isBound = false
        musicService = null
    }
}
