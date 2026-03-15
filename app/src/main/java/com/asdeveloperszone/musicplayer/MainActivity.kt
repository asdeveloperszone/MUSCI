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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MainActivity : AppCompatActivity(), ServiceConnection {

    private lateinit var rvSongs: RecyclerView
    private lateinit var adapter: SongAdapter
    private lateinit var etSearch: EditText
    private lateinit var tvCount: TextView
    private lateinit var miniPlayer: View
    private lateinit var ivMiniArt: ImageView
    private lateinit var tvMiniTitle: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var btnMiniPrev: ImageButton
    private lateinit var btnMiniPlay: ImageButton
    private lateinit var btnMiniNext: ImageButton
    private lateinit var btnMiniClose: ImageButton
    private lateinit var btnSort: ImageButton
    private lateinit var btnSettings: ImageButton

    // Tabs
    private lateinit var tabSongs: TextView
    private lateinit var tabAlbums: TextView
    private lateinit var tabArtists: TextView
    private lateinit var tabFolders: TextView
    private lateinit var tabPlaylists: TextView
    private lateinit var tabFavorites: TextView
    private lateinit var tabRecent: TextView
    private lateinit var tabMostPlayed: TextView

    private var svc: MusicService? = null
    private var bound = false
    private var allSongs: List<Song> = emptyList()
    private var currentSort = SortOption.A_TO_Z
    private var searchQuery = ""
    private var currentTab = 0 // 0=Songs,3=Favorites,6=Recent,7=MostPlayed
    private lateinit var prefs: SharedPreferences
    private lateinit var settingsPrefs: SharedPreferences

    companion object {
        private const val REQ_AUDIO = 100
        private const val REQ_NOTIF = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        settingsPrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            if (settingsPrefs.getBoolean("dark_theme", true))
                AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("music_settings", Context.MODE_PRIVATE)
        currentSort = SortOption.valueOf(
            prefs.getString("sort_option", SortOption.A_TO_Z.name) ?: SortOption.A_TO_Z.name)

        PlayCountManager.init(this)
        FavoritesManager.init(this)
        RecentlyPlayedManager.init(this)
        PlaylistManager.init(this)
        initViews()
        checkAudioPermission()
        askNotifPermission()
        BatteryOptimizationHelper.requestIfNeeded(this)
    }

    override fun onStart() {
        super.onStart()
        val i = Intent(this, MusicService::class.java)
        startService(i); bindService(i, this, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        svc?.let { s ->
            // Re-attach callbacks in case NowPlayingActivity stole them
            s.onSongChange = { song -> runOnUiThread {
                updateMini(song); adapter.setCurrentPlaying(song.id) } }
            s.onPlayState  = { p -> runOnUiThread {
                btnMiniPlay.setImageResource(if (p) R.drawable.ic_pause else R.drawable.ic_play) } }
            // Refresh UI
            s.currentSong()?.let { song ->
                updateMini(song); adapter.setCurrentPlaying(song.id)
            } ?: run { miniPlayer.visibility = View.GONE }
            btnMiniPlay.setImageResource(if (s.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        }
        if (currentTab in listOf(3, 6, 7)) filter()
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            svc?.onSongChange = null; svc?.onPlayState = null
            try { unbindService(this) } catch (e: Exception) { }
            bound = false; svc = null
        }
    }

    private fun askNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
    }

    private fun initViews() {
        rvSongs      = findViewById(R.id.recyclerView)
        etSearch     = findViewById(R.id.etSearch)
        tvCount      = findViewById(R.id.tvSongCount)
        miniPlayer   = findViewById(R.id.miniPlayer)
        ivMiniArt    = findViewById(R.id.ivMiniArt)
        tvMiniTitle  = findViewById(R.id.tvMiniTitle)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        btnMiniPrev  = findViewById(R.id.btnMiniPrev)
        btnMiniPlay  = findViewById(R.id.btnMiniPlayPause)
        btnMiniNext  = findViewById(R.id.btnMiniNext)
        btnMiniClose = findViewById(R.id.btnMiniClose)
        btnSort      = findViewById(R.id.btnSort)
        btnSettings  = findViewById(R.id.btnSettings)
        tabSongs     = findViewById(R.id.tabSongs)
        tabAlbums    = findViewById(R.id.tabAlbums)
        tabArtists   = findViewById(R.id.tabArtists)
        tabFolders   = findViewById(R.id.tabFolders)
        tabPlaylists = findViewById(R.id.tabPlaylists)
        tabFavorites = findViewById(R.id.tabFavorites)
        tabRecent    = findViewById(R.id.tabRecent)
        tabMostPlayed= findViewById(R.id.tabMostPlayed)

        adapter = SongAdapter(emptyList()) { song, _ ->
            val songs = getTabSongs()
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

        rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true); itemAnimator = null
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { searchQuery = s?.toString() ?: ""; filter() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // Tab clicks — direct activities or in-list tabs
        tabSongs.setOnClickListener      { selectTab(0) }
        tabAlbums.setOnClickListener     { startActivity(Intent(this, AlbumsActivity::class.java)) }
        tabArtists.setOnClickListener    { startActivity(Intent(this, ArtistsActivity::class.java)) }
        tabFolders.setOnClickListener    { startActivity(Intent(this, FoldersActivity::class.java)) }
        tabPlaylists.setOnClickListener  { startActivity(Intent(this, PlaylistsActivity::class.java)) }
        tabFavorites.setOnClickListener  { selectTab(3) }
        tabRecent.setOnClickListener     { selectTab(6) }
        tabMostPlayed.setOnClickListener { selectTab(7) }

        btnSort.setOnClickListener     { showSort() }
        btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        btnMiniPrev.setOnClickListener  { svc?.previous() }
        btnMiniPlay.setOnClickListener {
            svc?.togglePlayPause()
            btnMiniPlay.setImageResource(
                if (svc?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play)
        }
        btnMiniNext.setOnClickListener  { svc?.next() }
        btnMiniClose.setOnClickListener { svc?.stop(); miniPlayer.visibility = View.GONE }
        miniPlayer.setOnClickListener   { startActivity(Intent(this, NowPlayingActivity::class.java)) }

        selectTab(0)
    }

    private fun selectTab(tab: Int) {
        currentTab = tab
        val allTabs = listOf(tabSongs, tabAlbums, tabArtists, tabFolders,
            tabPlaylists, tabFavorites, tabRecent, tabMostPlayed)
        allTabs.forEach { it.setBackgroundResource(R.drawable.bg_tab_inactive); it.setTextColor(0xAAFFFFFF.toInt()) }
        val active = when (tab) {
            3 -> tabFavorites; 6 -> tabRecent; 7 -> tabMostPlayed; else -> tabSongs
        }
        active.setBackgroundResource(R.drawable.bg_tab_active)
        active.setTextColor(0xFFFFFFFF.toInt())
        filter()
    }

    private fun getTabSongs(): List<Song> {
        // Must return the SAME sorted list currently displayed
        var base = when (currentTab) {
            3 -> FavoritesManager.getFavoriteSongs(allSongs)
            6 -> RecentlyPlayedManager.getRecentSongs(allSongs)
            7 -> return allSongs.sortedByDescending { PlayCountManager.getCount(it.id) }.take(50)
            else -> allSongs
        }
        if (searchQuery.isNotEmpty()) {
            base = base.filter {
                it.title.contains(searchQuery, true) ||
                it.artist.contains(searchQuery, true) ||
                it.album.contains(searchQuery, true)
            }
        }
        return when (currentSort) {
            SortOption.A_TO_Z        -> base.sortedBy { it.title.lowercase() }
            SortOption.Z_TO_A        -> base.sortedByDescending { it.title.lowercase() }
            SortOption.NEWEST_FIRST  -> base.sortedByDescending { it.dateAdded }
            SortOption.OLDEST_FIRST  -> base.sortedBy { it.dateAdded }
            SortOption.MOST_LISTENED -> base.sortedByDescending { PlayCountManager.getCount(it.id) }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (searchQuery.isNotEmpty()) { etSearch.text.clear(); searchQuery = ""; filter() }
        else super.onBackPressed()
    }

    private fun showSort() {
        val labels = SortOption.values().map { it.label }.toTypedArray()
        AlertDialog.Builder(this, R.style.SortDialogTheme)
            .setTitle("Sort Songs")
            .setSingleChoiceItems(labels, SortOption.values().indexOf(currentSort)) { d, i ->
                currentSort = SortOption.values()[i]
                prefs.edit().putString("sort_option", currentSort.name).apply()
                filter(); d.dismiss()
            }.show()
    }

    private fun filter() {
        var base = getTabSongs()
        if (searchQuery.isNotEmpty()) {
            base = base.filter {
                it.title.contains(searchQuery, true) ||
                it.artist.contains(searchQuery, true) ||
                it.album.contains(searchQuery, true)
            }
        }
        // Don't re-sort Recent and MostPlayed — they have their own order
        val sorted = if (currentTab == 6 || currentTab == 7) base
        else when (currentSort) {
            SortOption.A_TO_Z        -> base.sortedBy { it.title.lowercase() }
            SortOption.Z_TO_A        -> base.sortedByDescending { it.title.lowercase() }
            SortOption.NEWEST_FIRST  -> base.sortedByDescending { it.dateAdded }
            SortOption.OLDEST_FIRST  -> base.sortedBy { it.dateAdded }
            SortOption.MOST_LISTENED -> base.sortedByDescending { PlayCountManager.getCount(it.id) }
        }
        adapter.updateSongs(sorted)
        tvCount.text = when {
            searchQuery.isNotEmpty() -> "${sorted.size} / ${allSongs.size}"
            currentTab == 3 -> "❤ ${sorted.size} favorites"
            currentTab == 6 -> "🕐 ${sorted.size} recent"
            currentTab == 7 -> "🔥 Top ${sorted.size} played"
            else -> "${allSongs.size} songs"
        }
    }

    private fun checkAudioPermission() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED)
            loadSongs()
        else ActivityCompat.requestPermissions(this, arrayOf(perm), REQ_AUDIO)
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        if (rc == REQ_AUDIO && r.isNotEmpty() && r[0] == PackageManager.PERMISSION_GRANTED) loadSongs()
    }

    private fun loadSongs() {
        Thread {
            val list = mutableListOf<Song>()
            try {
                val cols = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DATE_ADDED)
                contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
                    "${MediaStore.Audio.Media.TITLE} ASC")?.use { c ->
                    val ci=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val ct=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val ca=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val cb=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val cd=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val cp=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val cdt=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    while(c.moveToNext()) {
                        try {
                            val id=c.getLong(ci); val dur=c.getLong(cd); val aid=c.getLong(cp)
                            if(dur>0) list.add(Song(id,
                                c.getString(ct)?:"Unknown", c.getString(ca)?:"Unknown Artist",
                                c.getString(cb)?:"Unknown Album", dur,
                                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,id),
                                ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"),aid),
                                c.getLong(cdt)))
                        } catch(e:Exception){}
                    }
                }
            } catch(e:Exception){ runOnUiThread{ Toast.makeText(this,"Error loading songs",Toast.LENGTH_SHORT).show() } }
            runOnUiThread { allSongs=list; filter(); svc?.currentSong()?.let{ updateMini(it) } }
        }.start()
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        try {
            val s = (service as MusicService.MusicBinder).getService()
            svc = s; bound = true
            // Clear first to avoid double-callbacks
            s.onSongChange = null; s.onPlayState = null
            s.currentSong()?.let { updateMini(it) }
            btnMiniPlay.setImageResource(if (s.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            s.onSongChange = { song -> runOnUiThread {
                updateMini(song); adapter.setCurrentPlaying(song.id) } }
            s.onPlayState  = { p -> runOnUiThread {
                btnMiniPlay.setImageResource(if (p) R.drawable.ic_pause else R.drawable.ic_play) } }
        } catch (e: Exception) { }
    }

    private fun updateMini(song: Song) {
        miniPlayer.visibility=View.VISIBLE
        tvMiniTitle.text=song.title; tvMiniArtist.text=song.artist
        Glide.with(this).load(song.albumArtUri)
            .placeholder(R.drawable.ic_music_note).error(R.drawable.ic_music_note).into(ivMiniArt)
    }

    override fun onServiceDisconnected(name: ComponentName?) { bound=false; svc=null }
}
