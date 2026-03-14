package com.asdeveloperszone.musicplayer
import android.content.Context
import android.content.SharedPreferences
object FavoritesManager {
    private lateinit var prefs: SharedPreferences
    fun init(c: Context) { prefs = c.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE) }
    fun isFavorite(id: Long) = getIds().contains(id.toString())
    fun toggle(id: Long): Boolean {
        val ids = getIds().toMutableSet()
        return if (ids.contains(id.toString())) { ids.remove(id.toString()); prefs.edit().putStringSet("favs", ids).apply(); false }
        else { ids.add(id.toString()); prefs.edit().putStringSet("favs", ids).apply(); true }
    }
    fun getIds(): Set<String> = prefs.getStringSet("favs", emptySet()) ?: emptySet()
    fun getFavoriteSongs(all: List<Song>): List<Song> { val ids = getIds(); return all.filter { ids.contains(it.id.toString()) } }
}
