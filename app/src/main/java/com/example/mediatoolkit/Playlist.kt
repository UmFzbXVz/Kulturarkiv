package com.example.mediatoolkit

import java.util.*

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var description: String = "",
    var mediaList: MutableList<SearchResult> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    fun addMedia(media: SearchResult) {
        mediaList.add(media)
        updatedAt = System.currentTimeMillis()
    }

    fun removeMedia(media: SearchResult) {
        mediaList.remove(media)
        updatedAt = System.currentTimeMillis()
    }

    fun updateName(newName: String) {
        name = newName
        updatedAt = System.currentTimeMillis()
    }
}
