package com.asdeveloperszone.musicplayer
import android.view.*
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
class SongAdapter(
    private var songs: List<Song>,
    private val onSongClick: (Song, Int) -> Unit
) : RecyclerView.Adapter<SongAdapter.VH>() {
    private var currentId: Long = -1L
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: CardView      = v.findViewById(R.id.cardSong)
        val art: ImageView      = v.findViewById(R.id.ivAlbumArt)
        val title: TextView     = v.findViewById(R.id.tvSongTitle)
        val artist: TextView    = v.findViewById(R.id.tvArtistName)
        val album: TextView     = v.findViewById(R.id.tvAlbumName)
        val duration: TextView  = v.findViewById(R.id.tvDuration)
        val indicator: View     = v.findViewById(R.id.viewPlayingIndicator)
        val playIcon: ImageView = v.findViewById(R.id.ivPlayingIcon)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_song, p, false))
    override fun getItemCount() = songs.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val s = songs[pos]; val active = s.id == currentId
        h.title.text = s.title; h.artist.text = s.artist
        h.album.text = s.album; h.duration.text = s.getDurationFormatted()
        Glide.with(h.itemView.context).load(s.albumArtUri)
            .placeholder(R.drawable.ic_music_note).error(R.drawable.ic_music_note)
            .centerCrop().into(h.art)
        h.indicator.visibility = if (active) View.VISIBLE else View.GONE
        h.playIcon.visibility  = if (active) View.VISIBLE else View.GONE
        h.card.cardElevation   = if (active) 6f else 2f
        h.card.setCardBackgroundColor(ContextCompat.getColor(h.itemView.context,
            if (active) R.color.card_active else R.color.card_bg))
        h.itemView.setOnClickListener {
            val prev = songs.indexOfFirst { it.id == currentId }
            currentId = s.id
            if (prev >= 0) notifyItemChanged(prev)
            notifyItemChanged(pos)
            onSongClick(s, pos)
        }
    }
    fun updateSongs(newSongs: List<Song>) { songs = newSongs; notifyDataSetChanged() }
    fun setCurrentPlaying(id: Long) {
        val prev = songs.indexOfFirst { it.id == currentId }
        currentId = id
        if (prev >= 0) notifyItemChanged(prev)
        val next = songs.indexOfFirst { it.id == id }
        if (next >= 0) notifyItemChanged(next)
    }
}
