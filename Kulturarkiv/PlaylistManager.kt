package com.example.mediatoolkit

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object PlaylistManager {private val playlists = mutableListOf<Playlist>()
    private val gson = Gson()

    fun addPlaylist(playlist: Playlist) {
        playlists.add(playlist)
    }

    fun getAllPlaylists(): List<Playlist> = playlists

    fun getPlaylistById(id: String): Playlist? = playlists.find { it.id == id }

    fun deletePlaylist(playlist: Playlist) {
        playlists.remove(playlist)
    }

    fun setAllPlaylists(newPlaylists: List<Playlist>) {
        playlists.clear()
        playlists.addAll(newPlaylists)
    }

    fun savePlaylistsToPrefs(context: Context) {
        val sharedPref = context.getSharedPreferences("MyAppPreferences", Context.MODE_PRIVATE)
        val json = gson.toJson(playlists)
        sharedPref.edit { putString("playlists_json", json) }
    }

    fun loadPlaylistsFromPrefs(context: Context) {
        val sharedPref = context.getSharedPreferences("MyAppPreferences", Context.MODE_PRIVATE)
        val json = sharedPref.getString("playlists_json", null)

        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<Playlist>>() {}.type
                val loadedPlaylists: List<Playlist> = gson.fromJson(json, type)
                setAllPlaylists(loadedPlaylists)
            } catch (e: Exception) {
                Log.e("PlaylistManager", "Error loading: ${e.message}")
            }
        } else {
            val default = Playlist(name = "Playliste", description = "")
            playlists.add(default)
            savePlaylistsToPrefs(context)
        }
    }

}