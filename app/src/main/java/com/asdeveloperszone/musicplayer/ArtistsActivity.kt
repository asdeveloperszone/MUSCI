package com.asdeveloperszone.musicplayer

import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ArtistsActivity : AppCompatActivity() {

    data class Artist(val id: Long, val name: String, val songCount: Int, val albumCount: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_artists)

        findViewById<ImageButton>(R.id.btnArtistsBack).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvArtists)
        rv.layoutManager = LinearLayoutManager(this)

        val artists = loadArtists()
        rv.adapter = ArtistAdapter(artists) { artist ->
            val intent = Intent(this, ArtistSongsActivity::class.java)
            intent.putExtra("artist_id", artist.id)
            intent.putExtra("artist_name", artist.name)
            startActivity(intent)
        }
    }

    private fun loadArtists(): List<Artist> {
        val list = mutableListOf<Artist>()
        val projection = arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS,
            MediaStore.Audio.Artists.NUMBER_OF_ALBUMS
        )
        contentResolver.query(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Audio.Artists.ARTIST} ASC"
        )?.use { c ->
            val idCol    = c.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
            val nameCol  = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
            val tracksCol = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)
            val albumsCol = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS)
            while (c.moveToNext()) {
                list.add(Artist(
                    id         = c.getLong(idCol),
                    name       = c.getString(nameCol) ?: "Unknown",
                    songCount  = c.getInt(tracksCol),
                    albumCount = c.getInt(albumsCol)
                ))
            }
        }
        return list
    }

    inner class ArtistAdapter(
        private val artists: List<Artist>,
        private val onClick: (Artist) -> Unit
    ) : RecyclerView.Adapter<ArtistAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView  = v.findViewById(R.id.tvArtistName)
            val info: TextView  = v.findViewById(R.id.tvArtistInfo)
            val icon: ImageView = v.findViewById(R.id.ivArtistIcon)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_artist, p, false))

        override fun getItemCount() = artists.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val a = artists[pos]
            h.name.text = a.name
            h.info.text = "${a.songCount} songs • ${a.albumCount} albums"
            h.icon.setImageResource(R.drawable.ic_artist)
            h.itemView.setOnClickListener { onClick(a) }
        }
    }
}
