package com.asdeveloperszone.musicplayer
import android.content.Context
import android.content.SharedPreferences
object PlayCountManager {
    private lateinit var prefs: SharedPreferences
    fun init(c: Context) { prefs = c.getSharedPreferences("play_counts", Context.MODE_PRIVATE) }
    fun increment(id: Long) { prefs.edit().putInt(id.toString(), getCount(id)+1).apply() }
    fun getCount(id: Long): Int = prefs.getInt(id.toString(), 0)
}
