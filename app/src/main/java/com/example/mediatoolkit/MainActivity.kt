package com.example.mediatoolkit

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.constraintlayout.widget.ConstraintLayout

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SearchResultAdapter
    private lateinit var searchInput: EditText
    private var isLoading = false
    private var hasMoreData = true
    private var startIndex = 0
    private lateinit var navigationView: NavigationView
    private var sortOption: String = "score desc"
    private var playedIds: Set<String> = emptySet()
    private var currentlyPlayingId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var initialFetch: Boolean = true
    private lateinit var playAllFab: FloatingActionButton
    private var minDurationMinutes: Int = 0

    companion object { var searchTerm: String = "*" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Cookies og cache!
        ApiService.init(applicationContext)

        // Playlister
        PlaylistManager.loadPlaylistsFromPrefs(this)

        // sortButton
        val sortButton: ImageButton = findViewById(R.id.sortButton)
        sortButton.setOnClickListener { showSortMenu(it) }

        // Side-menu
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        navigationView = findViewById(R.id.item_playlist)

        // "+"-knappen til playlistappendering
        val footerView = layoutInflater.inflate(R.layout.navigation_drawer_footer, navigationView, false)
        navigationView.addView(footerView)
        val addPlaylistButton = footerView.findViewById<ImageButton>(R.id.addPlaylistButton)
        addPlaylistButton.setOnClickListener {
            showCreatePlaylistDialog()
        }

        val menuButton: ImageButton = findViewById(R.id.menuButton)
        menuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        searchInput = findViewById(R.id.searchField)
        playAllFab = findViewById(R.id.fab_play_all)

        // Kun anvend insets-justeringer på Android 15 (API 35)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.setDecorFitsSystemWindows(true)

            // Håndter insets for ConstraintLayout (barn af DrawerLayout)
            val constraintLayout = drawerLayout.getChildAt(0) as ConstraintLayout
            ViewCompat.setOnApplyWindowInsetsListener(constraintLayout) { view, insets ->
                val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(
                    top = systemInsets.top,
                    bottom = systemInsets.bottom,
                    left = systemInsets.left,
                    right = systemInsets.right
                )
                WindowInsetsCompat.CONSUMED
            }

            // Juster FloatingActionButton
            ViewCompat.setOnApplyWindowInsetsListener(playAllFab) { view, insets ->
                val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = systemInsets.bottom + 40.dpToPx()
                }
                WindowInsetsCompat.CONSUMED
            }

            // Juster søgefelt (flyt fra bunden for at undgå navigationsbjælke)
            ViewCompat.setOnApplyWindowInsetsListener(searchInput) { view, insets ->
                val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    bottomMargin = systemInsets.bottom + 4.dpToPx()
                    topMargin = 10.dpToPx()
                    // Fjern bottom constraint for at undgå konflikt
                    clearBottomConstraint()
                }
                WindowInsetsCompat.CONSUMED
            }

            // Juster menuButton (flyt fra toppen for at undgå statusbjælke)
            ViewCompat.setOnApplyWindowInsetsListener(menuButton) { view, insets ->
                val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    topMargin = systemInsets.top + 12.dpToPx()
                    // Fjern bottom constraint for at undgå konflikt
                    clearBottomConstraint()
                }
                WindowInsetsCompat.CONSUMED
            }

            // Juster sortButton (flyt fra toppen for at undgå statusbjælke)
            ViewCompat.setOnApplyWindowInsetsListener(sortButton) { view, insets ->
                val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    topMargin = systemInsets.top + 12.dpToPx()
                    // Fjern bottom constraint for at undgå konflikt
                    clearBottomConstraint()
                }
                WindowInsetsCompat.CONSUMED
            }
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val pastVisibleItems = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoading && hasMoreData) {
                        if ((visibleItemCount + pastVisibleItems) >= totalItemCount - 2) {
                            fetchAndDisplayData(searchTerm, append = true)
                        }
                    }
                }
            }
        })

        // Defokusér søgefelt og luk tastaturet ved "Search"/"Done"
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                searchInput.clearFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
                true
            } else {
                false
            }
        }

        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
            }
        }

        // Initielt fetch
        fetchAndDisplayData(searchTerm)

        // Debounced søgning ved input
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { handler.removeCallbacks(it) }

                searchRunnable = Runnable {
                    val input = s.toString()
                    searchTerm = input
                    startIndex = 0
                    hasMoreData = true

                    adapter = SearchResultAdapter(mutableListOf()) { result ->
                        val intent = Intent(this@MainActivity, PlayerActivity::class.java)
                        intent.putExtra("searchResult", result)
                        startActivity(intent)
                    }

                    recyclerView.adapter = adapter

                    fetchAndDisplayData(searchTerm)
                }

                handler.postDelayed(searchRunnable!!, 1000)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        playAllFab.setOnClickListener {
            val currentList = adapter.getAllItems()
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putParcelableArrayListExtra("playlistItems", ArrayList(currentList))
            startActivity(intent)
        }
        updateNavigationDrawerPlaylists()
    }

    private fun ConstraintLayout.LayoutParams.clearBottomConstraint() {
        bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        bottomToTop = ConstraintLayout.LayoutParams.UNSET
    }

    private fun checkIfMoreDataShouldBeLoaded() {
        recyclerView.post {
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val lastVisible = layoutManager.findLastVisibleItemPosition()
            val total = adapter.itemCount

            if (lastVisible >= total - 1 && hasMoreData && !isLoading) {
                fetchAndDisplayData(searchTerm, append = true)
            }
        }
    }

    private fun refreshSearch() {
        startIndex = 0
        hasMoreData = true

        adapter = SearchResultAdapter(mutableListOf()) { result ->
            val intent = Intent(this@MainActivity, PlayerActivity::class.java)
            intent.putExtra("searchResult", result)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
        adapter.clear()

        checkIfMoreDataShouldBeLoaded()

        fetchAndDisplayData(searchTerm)
    }

    private fun showSortMenu(anchor: View) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sort_menu, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.sortOptions)
        val switchShowPlayed = dialogView.findViewById<Switch>(R.id.showPlayedSwitch)
        val switchMinDuration = dialogView.findViewById<Switch>(R.id.minDurationSwitch)
        switchMinDuration.isChecked = minDurationMinutes > 0

        switchMinDuration.setOnCheckedChangeListener { _, isChecked ->
            minDurationMinutes = if (isChecked) 15 else 0
            refreshSearch()
        }

        val sortMap = mapOf(
            R.id.sortRelevance to "score desc",
            R.id.sortNewest to "startTime desc",
            R.id.sortOldest to "startTime asc",
            R.id.sortAZ to "title_sort_da asc",
            R.id.sortZA to "title_sort_da desc"
        )

        sortMap.entries.find { it.value == sortOption }?.let { radioGroup.check(it.key) }

        switchShowPlayed.isChecked = PlaybackState.showPlayed

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            sortMap[checkedId]?.let {
                sortOption = it
                refreshSearch()
            }
        }

        switchShowPlayed.setOnCheckedChangeListener { _, isChecked ->
            PlaybackState.showPlayed = isChecked
            refreshSearch()
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton("Luk", null)
            .show()
    }

    private fun fetchAndDisplayData(term: String, append: Boolean = false) {
        if (isLoading || !hasMoreData) return

        isLoading = true

        val normalized = normalizeSearchTerm(term)
        val encoded = URLEncoder.encode(normalized, StandardCharsets.UTF_8.toString())
        val url = "https://www.kb.dk/ds-api/bff/v1/proxy/search/?q=$encoded&facet=false&start=$startIndex&sort=${URLEncoder.encode(sortOption, StandardCharsets.UTF_8.toString())}&rows=10"

        ApiService.fetchData(url) { response ->
            isLoading = false

            if (response != null) {
                val searchResults = parseJson(response)

                runOnUiThread {
                    val filteredResults = searchResults
                        .filter { result -> PlaybackState.showPlayed || !playedIds.contains(result.kalturaId) }
                        .filter { result -> result.durationMs!! >= minDurationMinutes * 60_000 }

                    if (append) {
                        adapter.appendData(filteredResults)
                    } else {
                        adapter = SearchResultAdapter(filteredResults.toMutableList()) { selectedItem ->
                            val intent = Intent(this@MainActivity, PlayerActivity::class.java)
                            intent.putExtra("searchResult", selectedItem)
                            startActivity(intent)
                        }
                        recyclerView.adapter = adapter
                    }

                    if (searchResults.isNotEmpty()) {
                        startIndex += searchResults.size
                    } else {
                        hasMoreData = false
                    }

                    if (initialFetch) {
                        initialFetch = false
                        searchInput.hint = "søg blandt ${formatNumber(PlaybackState.currentTotalItems)} arkivalier"
                    }

                    checkIfMoreDataShouldBeLoaded()
                }
            } else {
                Log.e("API Call", "No data received")
            }
        }
    }

    private fun normalizeSearchTerm(term: String): String {
        return term.trim().lowercase().replace(Regex("\\s+"), " ")
    }

    private fun parseJson(response: String): List<SearchResult> {
        val searchResults = mutableListOf<SearchResult>()
        val jsonObject = JSONObject(response)
        val items = jsonObject.getJSONObject("response").getJSONArray("docs")

        PlaybackState.currentTotalItems = jsonObject.getJSONObject("response").getInt("numFound")

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)

            val title = item.getJSONArray("title").getString(0)
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

        return searchResults
    }

    private fun formatNumber(numFound: Int): String {
        return when {
            numFound >= 1_000_000 -> String.format("%.1fM", numFound / 1_000_000.0)
            numFound >= 1_000 -> String.format("%.1fk", numFound / 1_000.0)
            else -> numFound.toString()
        }
    }

    private fun showCreatePlaylistDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Ny playliste")

        val input = EditText(this)
        input.hint = "Navn på playliste"
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        builder.setPositiveButton("Opret") { _, _ ->
            val playlistName = input.text.toString().trim()
            if (playlistName.isNotEmpty()) {
                val newPlaylist = Playlist(name = playlistName)
                PlaylistManager.addPlaylist(newPlaylist)
                PlaylistManager.savePlaylistsToPrefs(this)
                updateNavigationDrawerPlaylists()
            }
        }

        builder.setNegativeButton("Annuller") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    override fun onResume() {
        super.onResume()
        loadPlaybackState()
        updateNavigationDrawerPlaylists()

        if (PlaybackState.currentEntryId != null) {
            Log.d("", "MainActivity.onResume -> currentEntryId == " + PlaybackState.currentEntryId)
            currentlyPlayingId = PlaybackState.currentEntryId
            PlaybackState.currentSearchResult?.let { result ->
                adapter.updateActivePlayback(result)
                adapter.notifyDataSetChanged()
            }
        } else {
            Log.d("", "MainActivity.onResume -> currentEntryId == null")
        }
    }

    private fun loadPlaybackState() {
        val prefs = getSharedPreferences("player_prefs", MODE_PRIVATE)
        val playedIdsSet = prefs.getStringSet("played_ids", emptySet()) ?: emptySet()

        playedIds = playedIdsSet
        PlaybackState.currentPlayedIds = playedIdsSet

        Log.d("", "MainActivity.loadPlaybackState-> playedIds: $playedIdsSet")
    }

    private fun updateNavigationDrawerPlaylists() {
        val playlistContainer = findViewById<LinearLayout>(R.id.playlist_container)
        playlistContainer.removeAllViews()

        PlaylistManager.getAllPlaylists().forEachIndexed { index, playlist ->
            val menuItemView = layoutInflater.inflate(R.layout.drawer_menu_item, playlistContainer, false)
            val menuItemText = menuItemView.findViewById<TextView>(R.id.menuItemText)
            menuItemText.text = playlist.name

            menuItemView.setOnClickListener {
                val intent = Intent(this, PlaylistActivity::class.java)
                intent.putExtra("playlistId", playlist.id)
                startActivity(intent)
            }

            val layoutParams = menuItemView.layoutParams as LinearLayout.LayoutParams
            layoutParams.topMargin = if (index == 0) 30.dpToPx() else 2.dpToPx()
            menuItemView.layoutParams = layoutParams

            playlistContainer.addView(menuItemView)
        }
    }

    private fun Int.dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchRunnable?.let { handler.removeCallbacks(it) }
    }
}
