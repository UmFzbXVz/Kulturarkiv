package com.example.mediatoolkit

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainViewModel(application: Application) : AndroidViewModel(application) {private val _searchResults = MutableLiveData<List<SearchResult>>()
    val searchResults: LiveData<List<SearchResult>> = _searchResults

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _hasMoreData = MutableLiveData(true)
    val hasMoreData: LiveData<Boolean> = _hasMoreData

    var sortOption: String = "score desc"
    var minDurationMinutes: Int = 0
    private var startIndex = 0
    private var playedIds: Set<String> = emptySet()
    var totalItems: Int = 0

    fun loadPlaybackState() {
        val prefs = getApplication<Application>().getSharedPreferences("player_prefs", android.content.Context.MODE_PRIVATE)
        playedIds = prefs.getStringSet("played_ids", emptySet()) ?: emptySet()
        PlaybackState.currentPlayedIds = playedIds
    }

    fun resetAndFetch(term: String) {
        startIndex = 0
        _hasMoreData.value = true
        _searchResults.value = emptyList()
        fetchData(term)
    }

    fun fetchData(term: String, append: Boolean = false) {
        if (_isLoading.value == true || _hasMoreData.value == false) return

        _isLoading.value = true

        viewModelScope.launch {
            val normalized = normalizeSearchTerm(term)
            val encoded = URLEncoder.encode(normalized, StandardCharsets.UTF_8.toString())
            val url = "https://www.kb.dk/ds-api/bff/v1/proxy/search/?q=$encoded&facet=false&start=$startIndex&sort=${URLEncoder.encode(sortOption, StandardCharsets.UTF_8.toString())}&rows=10"

            ApiService.fetchData(url) { response ->
                _isLoading.postValue(false)

                if (response != null) {
                    val jsonObject = JSONObject(response)
                    totalItems = jsonObject.getJSONObject("response").getInt("numFound")
                    val items = jsonObject.getJSONObject("response").getJSONArray("docs")
                    val searchResults = mutableListOf<SearchResult>()

                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val title = item.optJSONArray("title")?.optString(0)
                        val id = item.optString("id")
                        val durationMs = item.optInt("duration_ms")
                        val description = item.optString("description")
                        val startTime = item.optString("startTime")
                        val origin = item.optString("origin")
                        val kalturaId = item.optString("kaltura_id")
                        val internalSeriesId = item.optString("internal_series_id")
                        val internalSeasonId = item.optString("internal_season_id")
                        val internalEpisodeId = item.optString("internal_episode_id")

                        val searchResult = SearchResult(
                            title, id, durationMs, description, startTime, origin, kalturaId, internalSeriesId, internalSeasonId, internalEpisodeId
                        )
                        searchResults.add(searchResult)
                    }

                    val filteredResults = searchResults
                        .filter { result -> PlaybackState.showPlayed || !playedIds.contains(result.kalturaId) }
                        .filter { result -> result.durationMs!! >= minDurationMinutes * 60_000 }

                    _searchResults.postValue(filteredResults)

                    if (searchResults.isNotEmpty()) {
                        startIndex += searchResults.size
                    } else {
                        _hasMoreData.postValue(false)
                    }
                } else {
                    Log.e("API Call", "No data received")
                }
            }
        }
    }

    private fun normalizeSearchTerm(term: String): String {
        return term.trim().lowercase().replace(Regex("\\s+"), " ")
    }}