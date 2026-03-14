package com.asdeveloperszone.musicplayer
import android.net.Uri
data class Song(
    val id: Long, val title: String, val artist: String, val album: String,
    val duration: Long, val uri: Uri, val albumArtUri: Uri?, val dateAdded: Long = 0L
) {
    fun getDurationFormatted(): String {
        val m = (duration/1000)/60; val s = (duration/1000)%60
        return String.format("%d:%02d", m, s)
    }
}
