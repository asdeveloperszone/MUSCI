package com.asdeveloperszone.musicplayer

import android.content.ContentUris
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.*
import android.widget.*
import com.bumptech.glide.Glide

class AlbumsActivity : AppCompatActivity() {

    data class Album(val id: Long, val name: String, val artist: String,
                     val songCount: Int, val artUri: Uri?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_albums)

        findViewById<ImageButton>(R.id.btnAlbumsBack).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvAlbums)
        rv.layoutManager = GridLayoutManager(this, 2)

        val albums = loadAlbums()
        rv.adapter = AlbumAdapter(albums) { album ->
            val intent = Intent(this, AlbumSongsActivity::class.java)
            intent.putExtra("album_id", album.id)
            intent.putExtra("album_name", album.name)
            startActivity(intent)
        }
    }

    private fun loadAlbums(): List<Album> {
        val albums = mutableListOf<Album>()
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS
        )
        contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Audio.Albums.ALBUM} ASC"
        )?.use { c ->
            val idCol    = c.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val nameCol  = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artCol   = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val countCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                albums.add(Album(
                    id        = id,
                    name      = c.getString(nameCol) ?: "Unknown",
                    artist    = c.getString(artCol) ?: "Unknown",
                    songCount = c.getInt(countCol),
                    artUri    = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), id)
                ))
            }
        }
        return albums
    }

    inner class AlbumAdapter(
        private val albums: List<Album>,
        private val onClick: (Album) -> Unit
    ) : RecyclerView.Adapter<AlbumAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val art: ImageView  = v.findViewById(R.id.ivAlbumArt)
            val name: TextView  = v.findViewById(R.id.tvAlbumName)
            val count: TextView = v.findViewById(R.id.tvSongCount)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_album, p, false))

        override fun getItemCount() = albums.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val album = albums[pos]
            h.name.text  = album.name
            h.count.text = "${album.songCount} songs"
            Glide.with(h.itemView.context).load(album.artUri)
                .placeholder(R.drawable.ic_music_note).centerCrop().into(h.art)
            h.itemView.setOnClickListener { onClick(album) }
        }
    }
}
