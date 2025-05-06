package com.example.mediatoolkit

import java.util.*

data class Playlist(
    val id: String = UUID.randomUUID().toString(),  // Unik ID for playlisten
    var name: String,  // Navnet på playlisten
    var description: String = "",  // Beskrivelse af playlisten
    var mediaList: MutableList<SearchResult> = mutableListOf(),  // Liste af medieobjekter i playlisten
    val createdAt: Long = System.currentTimeMillis(),  // Oprettelsesdato
    var updatedAt: Long = System.currentTimeMillis()  // Seneste ændringsdato
) {
    // Funktion til at tilføje et medie til playlisten
    fun addMedia(media: SearchResult) {
        mediaList.add(media)
        updatedAt = System.currentTimeMillis()
    }

    // Funktion til at fjerne et medie fra playlisten
    fun removeMedia(media: SearchResult) {
        mediaList.remove(media)
        updatedAt = System.currentTimeMillis()
    }

    // Funktion til at opdatere playlistens navn
    fun updateName(newName: String) {
        name = newName
        updatedAt = System.currentTimeMillis()
    }

}
