package com.asdeveloperszone.musicplayer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Playlist(
    val id: String,
    val name: String,
    val songIds: MutableList<Long> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis()
)

object PlaylistManager {
    private lateinit var prefs: SharedPreferences
    private const val KEY_PLAYLISTS = "playlists"

    fun init(context: Context) {
        prefs = context.getSharedPreferences("playlist_prefs", Context.MODE_PRIVATE)
    }

    fun getAll(): List<Playlist> {
        val str = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(str)
            (0 until arr.length()).map { parsePlaylist(arr.getJSONObject(it)) }
        } catch (e: Exception) { emptyList() }
    }

    fun create(name: String): Playlist {
        val pl = Playlist(id = System.currentTimeMillis().toString(), name = name)
        val all = getAll().toMutableList()
        all.add(pl)
        saveAll(all)
        return pl
    }

    fun rename(id: String, newName: String) {
        val all = getAll().map { if (it.id == id) it.copy(name = newName) else it }
        saveAll(all)
    }

    fun delete(id: String) {
        saveAll(getAll().filter { it.id != id })
    }

    fun addSong(playlistId: String, songId: Long) {
        val all = getAll().map { pl ->
            if (pl.id == playlistId && !pl.songIds.contains(songId)) {
                pl.songIds.add(songId); pl
            } else pl
        }
        saveAll(all)
    }

    fun removeSong(playlistId: String, songId: Long) {
        val all = getAll().map { pl ->
            if (pl.id == playlistId) { pl.songIds.remove(songId); pl }
            else pl
        }
        saveAll(all)
    }

    fun getSongs(playlistId: String, allSongs: List<Song>): List<Song> {
        val pl = getAll().find { it.id == playlistId } ?: return emptyList()
        val map = allSongs.associateBy { it.id }
        return pl.songIds.mapNotNull { map[it] }
    }

    private fun parsePlaylist(obj: JSONObject): Playlist {
        val ids = mutableListOf<Long>()
        val arr = obj.optJSONArray("songIds")
        if (arr != null) for (i in 0 until arr.length()) ids.add(arr.getLong(i))
        return Playlist(
            id = obj.getString("id"),
            name = obj.getString("name"),
            songIds = ids,
            createdAt = obj.optLong("createdAt", 0L)
        )
    }

    private fun saveAll(playlists: List<Playlist>) {
        val arr = JSONArray()
        playlists.forEach { pl ->
            val obj = JSONObject().apply {
                put("id", pl.id); put("name", pl.name)
                put("createdAt", pl.createdAt)
                put("songIds", JSONArray().apply { pl.songIds.forEach { put(it) } })
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }
}
