package com.musicapp.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class SongAdapter(
    private var songs: List<Song>,
    private val onSongClick: (Int) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var currentPlayingIndex: Int = -1

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val albumArt: ImageView = itemView.findViewById(R.id.ivAlbumArt)
        val title: TextView = itemView.findViewById(R.id.tvSongTitle)
        val artist: TextView = itemView.findViewById(R.id.tvArtistName)
        val duration: TextView = itemView.findViewById(R.id.tvDuration)
        val playingIndicator: View = itemView.findViewById(R.id.viewPlayingIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.title.text = song.title
        holder.artist.text = song.artist
        holder.duration.text = song.getDurationFormatted()

        if (song.albumArtUri != null) {
            Glide.with(holder.itemView.context)
                .load(song.albumArtUri)
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .centerCrop()
                .into(holder.albumArt)
        } else {
            holder.albumArt.setImageResource(R.drawable.ic_music_note)
        }

        val isCurrentlyPlaying = position == currentPlayingIndex
        holder.playingIndicator.visibility = if (isCurrentlyPlaying) View.VISIBLE else View.INVISIBLE
        holder.title.alpha = if (isCurrentlyPlaying) 1f else 0.85f

        holder.itemView.setOnClickListener {
            val prevIndex = currentPlayingIndex
            currentPlayingIndex = position
            notifyItemChanged(prevIndex)
            notifyItemChanged(position)
            onSongClick(position)
        }
    }

    override fun getItemCount() = songs.size

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    fun setCurrentPlaying(index: Int) {
        val prev = currentPlayingIndex
        currentPlayingIndex = index
        notifyItemChanged(prev)
        notifyItemChanged(index)
    }
}
