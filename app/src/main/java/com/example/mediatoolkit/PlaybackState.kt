package com.example.mediatoolkit

import com.google.android.exoplayer2.ExoPlayer

object PlaybackState {
    var currentEntryId: String? = null
    var currentSearchResult: SearchResult? = null
    var currentPlayedIds: Set<String> = emptySet()
    var showPlayed: Boolean = true
    var currentPlaylist: MutableList<SearchResult> = mutableListOf()
    var currentTotalItems: Int = 0
    lateinit var currentPlayer: ExoPlayer
}
