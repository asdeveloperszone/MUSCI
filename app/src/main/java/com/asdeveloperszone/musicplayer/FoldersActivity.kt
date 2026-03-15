package com.asdeveloperszone.musicplayer

import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FoldersActivity : AppCompatActivity(), ServiceConnection {

    data class Folder(val path: String, val name: String, val songs: List<Song>)

    private var svc: MusicService? = null
    private var bound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folders)
        findViewById<ImageButton>(R.id.btnFoldersBack).setOnClickListener { finish() }
        val rv = findViewById<RecyclerView>(R.id.rvFolders)
        rv.layoutManager = LinearLayoutManager(this)
        val folders = loadFolders()
        rv.adapter = FolderAdapter(folders) { folder ->
            val intent = Intent(this, AlbumSongsActivity::class.java).apply {
                putExtra("folder_songs", folder.songs.map { it.id }.toLongArray())
                putExtra("album_name", folder.name)
            }
            startActivity(intent)
        }
        bindService(Intent(this, MusicService::class.java), this, Context.BIND_AUTO_CREATE)
    }

    private fun loadFolders(): List<Folder> {
        val songsByFolder = mutableMapOf<String, MutableList<Song>>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATA
        )
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null
        )?.use { c ->
            val idC  = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val aidC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val datC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val pthC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                try {
                    val id  = c.getLong(idC)
                    val dur = c.getLong(durC)
                    val aid = c.getLong(aidC)
                    val path = c.getString(pthC) ?: continue
                    val folder = path.substringBeforeLast("/")
                    if (dur > 0) {
                        songsByFolder.getOrPut(folder) { mutableListOf() }.add(
                            Song(id, c.getString(titC) ?: "Unknown",
                                c.getString(artC) ?: "Unknown", c.getString(albC) ?: "Unknown",
                                dur, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                                ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), aid),
                                c.getLong(datC))
                        )
                    }
                } catch (e: Exception) { }
            }
        }
        return songsByFolder.map { (path, songs) ->
            Folder(path, path.substringAfterLast("/"), songs.sortedBy { it.title })
        }.sortedBy { it.name }
    }

    inner class FolderAdapter(
        private val folders: List<Folder>,
        private val onClick: (Folder) -> Unit
    ) : RecyclerView.Adapter<FolderAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView  = v.findViewById(R.id.tvFolderName)
            val count: TextView = v.findViewById(R.id.tvFolderCount)
            val icon: ImageView = v.findViewById(R.id.ivFolderIcon)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_folder, p, false))
        override fun getItemCount() = folders.size
        override fun onBindViewHolder(h: VH, pos: Int) {
            val f = folders[pos]
            h.name.text  = f.name
            h.count.text = "${f.songs.size} songs"
            h.icon.setImageResource(R.drawable.ic_folder)
            h.itemView.setOnClickListener { onClick(f) }
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
