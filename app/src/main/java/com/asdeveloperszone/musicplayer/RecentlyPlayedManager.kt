package com.asdeveloperszone.musicplayer

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

object RecentlyPlayedManager {
    private lateinit var prefs: SharedPreferences
    private const val KEY = "recently_played"
    private const val MAX = 20

    fun init(context: Context) {
        prefs = context.getSharedPreferences("recent_prefs", Context.MODE_PRIVATE)
    }

    fun add(songId: Long) {
        val ids = getIds().toMutableList()
        ids.remove(songId) // remove if already exists
        ids.add(0, songId) // add to front
        if (ids.size > MAX) ids.removeAt(ids.size - 1)
        val arr = JSONArray().apply { ids.forEach { put(it) } }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun getIds(): List<Long> {
        val str = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(str)
            (0 until arr.length()).map { arr.getLong(it) }
        } catch (e: Exception) { emptyList() }
    }

    fun getRecentSongs(allSongs: List<Song>): List<Song> {
        val ids = getIds()
        val map = allSongs.associateBy { it.id }
        return ids.mapNotNull { map[it] }
    }
}
