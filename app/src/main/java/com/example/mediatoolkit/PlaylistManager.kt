package com.example.mediatoolkit

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit

object PlaylistManager {

    private val playlists = mutableListOf<Playlist>()
    private var currentPlaylist: Playlist? = null
    private val gson = Gson()

    fun addPlaylist(playlist: Playlist) {
        playlists.add(playlist)
    }

    fun getAllPlaylists(): List<Playlist> = playlists

    fun getPlaylistById(id: String): Playlist? = playlists.find { it.id == id }

    fun setCurrentPlaylist(playlist: Playlist) {
        currentPlaylist = playlist
    }

    fun getCurrentPlaylistInstance(): Playlist {
        return currentPlaylist ?: playlists.firstOrNull() ?: Playlist(name = "Ny playliste")
    }

    fun deletePlaylist(playlist: Playlist) {
        playlists.remove(playlist)
        if (currentPlaylist == playlist) {
            currentPlaylist = playlists.firstOrNull()
        }
    }

    fun setAllPlaylists(newPlaylists: List<Playlist>) {
        playlists.clear()
        playlists.addAll(newPlaylists)
        currentPlaylist = playlists.firstOrNull()
    }

    fun savePlaylistsToPrefs(context: Context) {
        val sharedPref: SharedPreferences = context.getSharedPreferences("MyAppPreferences", Context.MODE_PRIVATE)
        val json = gson.toJson(playlists)
        Log.d("PlaylistManager", "Gemmer playlister: $json")
        sharedPref.edit() { putString("playlists_json", json) }
    }

    fun loadPlaylistsFromPrefs(context: Context) {
        val sharedPref: SharedPreferences = context.getSharedPreferences("MyAppPreferences", Context.MODE_PRIVATE)
        val json = sharedPref.getString("playlists_json", null)

        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<Playlist>>() {}.type
                val loadedPlaylists: List<Playlist> = gson.fromJson(json, type)
                setAllPlaylists(loadedPlaylists)
                Log.d("PlaylistManager", "Indlæste ${loadedPlaylists.size} playlister fra prefs")
            } catch (e: Exception) {
                Log.e("PlaylistManager", "Fejl ved indlæsning: ${e.message}")
            }
        } else {
            Log.d("PlaylistManager", "Ingen gemte playlister – tilføjer dummy’er")
            val default = Playlist(name = "Playliste", description = "")
            playlists.addAll(listOf(default))
            currentPlaylist = default
            savePlaylistsToPrefs(context)
        }
    }
}
