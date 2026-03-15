package com.asdeveloperszone.musicplayer

import android.content.Context
import android.content.SharedPreferences

object PlayCountManager {

    private const val PREFS_NAME = "play_counts"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun increment(songId: Long) {
        val current = prefs.getInt(songId.toString(), 0)
        prefs.edit().putInt(songId.toString(), current + 1).apply()
    }

    fun getCount(songId: Long): Int {
        return prefs.getInt(songId.toString(), 0)
    }
}
