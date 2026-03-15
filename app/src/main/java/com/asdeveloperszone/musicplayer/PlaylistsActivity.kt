package com.asdeveloperszone.musicplayer

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PlaylistsActivity : AppCompatActivity(), ServiceConnection {

    private var svc: MusicService? = null
    private var bound = false
    private lateinit var adapter: PlaylistAdapter
    private var allSongs: List<Song> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlists)
        PlaylistManager.init(this)

        findViewById<ImageButton>(R.id.btnPlaylistsBack).setOnClickListener { finish() }
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
            R.id.fabNewPlaylist).setOnClickListener { showCreateDialog() }

        val rv = findViewById<RecyclerView>(R.id.rvPlaylists)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = PlaylistAdapter(
            onOpen   = { pl -> openPlaylist(pl) },
            onRename = { pl -> showRenameDialog(pl) },
            onDelete = { pl -> showDeleteDialog(pl) }
        )
        rv.adapter = adapter
        refresh()
        bindService(Intent(this, MusicService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() { adapter.setPlaylists(PlaylistManager.getAll()) }

    private fun showCreateDialog() {
        val et = EditText(this).apply { hint = "Playlist name"; setPadding(48,32,48,32) }
        AlertDialog.Builder(this, R.style.SortDialogTheme)
            .setTitle("New Playlist")
            .setView(et)
            .setPositiveButton("Create") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) { PlaylistManager.create(name); refresh() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDialog(pl: Playlist) {
        val et = EditText(this).apply { setText(pl.name); setPadding(48,32,48,32) }
        AlertDialog.Builder(this, R.style.SortDialogTheme)
            .setTitle("Rename Playlist")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isNotEmpty()) { PlaylistManager.rename(pl.id, name); refresh() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteDialog(pl: Playlist) {
        AlertDialog.Builder(this, R.style.SortDialogTheme)
            .setTitle("Delete \"${pl.name}\"?")
            .setMessage("This will delete the playlist but not the songs.")
            .setPositiveButton("Delete") { _, _ -> PlaylistManager.delete(pl.id); refresh() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openPlaylist(pl: Playlist) {
        val intent = Intent(this, PlaylistSongsActivity::class.java).apply {
            putExtra("playlist_id", pl.id)
            putExtra("playlist_name", pl.name)
        }
        startActivity(intent)
    }

    inner class PlaylistAdapter(
        private val onOpen: (Playlist) -> Unit,
        private val onRename: (Playlist) -> Unit,
        private val onDelete: (Playlist) -> Unit
    ) : RecyclerView.Adapter<PlaylistAdapter.VH>() {
        private var playlists: List<Playlist> = emptyList()

        fun setPlaylists(list: List<Playlist>) { playlists = list; notifyDataSetChanged() }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView  = v.findViewById(R.id.tvPlaylistName)
            val count: TextView = v.findViewById(R.id.tvPlaylistCount)
            val btnMore: ImageButton = v.findViewById(R.id.btnPlaylistMore)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_playlist, p, false))

        override fun getItemCount() = playlists.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val pl = playlists[pos]
            h.name.text  = pl.name
            h.count.text = "${pl.songIds.size} songs"
            h.itemView.setOnClickListener { onOpen(pl) }
            h.btnMore.setOnClickListener {
                val popup = PopupMenu(h.itemView.context, h.btnMore)
                popup.menu.add(0, 0, 0, "Rename")
                popup.menu.add(0, 1, 1, "Delete")
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) { 0 -> onRename(pl); 1 -> onDelete(pl) }
                    true
                }
                popup.show()
            }
        }
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
