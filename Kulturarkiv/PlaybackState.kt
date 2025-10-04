package com.example.mediatoolkit

object PlaybackState {
    var currentEntryId: String? = null
    var currentSearchResult: SearchResult? = null
    var currentPlayedIds: Set<String> = emptySet()
    var showPlayed: Boolean = true
}