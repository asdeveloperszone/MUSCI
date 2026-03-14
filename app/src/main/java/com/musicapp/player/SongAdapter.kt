package com.musicapp.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class SongAdapter(
    private var songs: List<Song>,
    private val onSongClick: (Song, Int) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var currentPlayingId: Long = -1

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: CardView = itemView.findViewById(R.id.cardSong)
        val albumArt: ImageView = itemView.findViewById(R.id.ivAlbumArt)
        val title: TextView = itemView.findViewById(R.id.tvSongTitle)
        val artist: TextView = itemView.findViewById(R.id.tvArtistName)
        val album: TextView = itemView.findViewById(R.id.tvAlbumName)
        val duration: TextView = itemView.findViewById(R.id.tvDuration)
        val playingIndicator: View = itemView.findViewById(R.id.viewPlayingIndicator)
        val ivPlayingIcon: ImageView = itemView.findViewById(R.id.ivPlayingIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.title.text = song.title
        holder.artist.text = song.artist
        holder.album.text = song.album
        holder.duration.text = song.getDurationFormatted()

        Glide.with(holder.itemView.context)
            .load(song.albumArtUri)
            .placeholder(R.drawable.ic_music_note)
            .error(R.drawable.ic_music_note)
            .centerCrop()
            .into(holder.albumArt)

        val isActive = song.id == currentPlayingId
        holder.playingIndicator.visibility = if (isActive) View.VISIBLE else View.GONE
        holder.ivPlayingIcon.visibility = if (isActive) View.VISIBLE else View.GONE
        holder.card.cardElevation = if (isActive) 8f else 2f
        holder.card.setCardBackgroundColor(
            if (isActive)
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.card_active)
            else
                androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.card_bg)
        )

        holder.itemView.setOnClickListener {
            val prev = songs.indexOfFirst { it.id == currentPlayingId }
            currentPlayingId = song.id
            if (prev >= 0) notifyItemChanged(prev)
            notifyItemChanged(position)
            onSongClick(song, position)
        }
    }

    override fun getItemCount() = songs.size

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    fun setCurrentPlaying(songId: Long) {
        val prev = songs.indexOfFirst { it.id == currentPlayingId }
        currentPlayingId = songId
        if (prev >= 0) notifyItemChanged(prev)
        val next = songs.indexOfFirst { it.id == songId }
        if (next >= 0) notifyItemChanged(next)
    }
}
