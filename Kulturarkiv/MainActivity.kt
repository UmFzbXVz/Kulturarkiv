package com.example.mediatoolkit

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
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
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams as ConstraintLayoutParams
import android.view.ViewGroup


class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SearchResultAdapter
    private lateinit var searchInput: EditText
    private lateinit var navigationView: NavigationView
    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private lateinit var playAllFab: FloatingActionButton
    private lateinit var viewModel: MainViewModel

    companion object {
        var searchTerm: String = "*"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ApiService.init(applicationContext)
        PlaylistManager.loadPlaylistsFromPrefs(this)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        val sortButton: ImageButton = findViewById(R.id.sortButton)
        sortButton.setOnClickListener { showSortMenu() }

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        navigationView = findViewById(R.id.item_playlist)

        val footerView =
            layoutInflater.inflate(R.layout.navigation_drawer_footer, navigationView, false)
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

        ViewCompat.setOnApplyWindowInsetsListener(
            drawerLayout.getChildAt(0)
        ) { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = systemInsets.top,
                bottom = systemInsets.bottom,
                left = systemInsets.left,
                right = systemInsets.right
            )
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.setOnApplyWindowInsetsListener(playAllFab) { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemInsets.bottom + 40.dpToPx()
            }
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.setOnApplyWindowInsetsListener(searchInput) { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ConstraintLayoutParams> {
                bottomMargin = systemInsets.bottom + 4.dpToPx()
                topMargin = 10.dpToPx()
            }
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.setOnApplyWindowInsetsListener(menuButton) { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ConstraintLayoutParams> {
                topMargin = systemInsets.top + 12.dpToPx()
            }
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.setOnApplyWindowInsetsListener(sortButton) { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ConstraintLayoutParams> {
                bottomMargin = systemInsets.bottom + 8.dpToPx()
                marginEnd = systemInsets.right + 16.dpToPx()
            }
            WindowInsetsCompat.CONSUMED
        }

        adapter = SearchResultAdapter(mutableListOf())
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && !viewModel.isLoading.value!! && viewModel.hasMoreData.value!!) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val pastVisibleItems = layoutManager.findFirstVisibleItemPosition()

                    if ((visibleItemCount + pastVisibleItems) >= totalItemCount - 2) {
                        viewModel.fetchData(searchTerm, append = true)
                    }
                }
            }
        })

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                searchInput.clearFocus()
                val imm =
                    getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
                true
            } else {
                false
            }
        }

        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val imm =
                    getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
            }
        }

        viewModel.searchResults.observe(this) { results ->
            adapter.appendData(results)
            searchInput.hint = "søg blandt ${formatNumber(viewModel.totalItems)} arkivalier"
        }

        viewModel.fetchData(searchTerm)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { handler.removeCallbacks(it) }

                searchRunnable = Runnable {
                    val input = s.toString()
                    searchTerm = input
                    adapter.clear()
                    viewModel.resetAndFetch(searchTerm)
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

    private fun showSortMenu() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sort_menu, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.sortOptions)
        val switchShowPlayed = dialogView.findViewById<Switch>(R.id.showPlayedSwitch)
        val switchMinDuration = dialogView.findViewById<Switch>(R.id.minDurationSwitch)
        switchMinDuration.isChecked = viewModel.minDurationMinutes > 0

        switchMinDuration.setOnCheckedChangeListener { _, isChecked ->
            viewModel.minDurationMinutes = if (isChecked) 15 else 0
            viewModel.resetAndFetch(searchTerm)
        }

        val sortMap = mapOf(
            R.id.sortRelevance to "score desc",
            R.id.sortNewest to "startTime desc",
            R.id.sortOldest to "startTime asc",
            R.id.sortAZ to "title_sort_da asc",
            R.id.sortZA to "title_sort_da desc"
        )

        sortMap.entries.find { it.value == viewModel.sortOption }?.let { radioGroup.check(it.key) }

        switchShowPlayed.isChecked = PlaybackState.showPlayed

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            sortMap[checkedId]?.let {
                viewModel.sortOption = it
                adapter.clear()
                viewModel.resetAndFetch(searchTerm)
            }
        }

        switchShowPlayed.setOnCheckedChangeListener { _, isChecked ->
            PlaybackState.showPlayed = isChecked
            adapter.clear()
            viewModel.resetAndFetch(searchTerm)
        }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton("Luk", null)
            .show()
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
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT
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
        viewModel.loadPlaybackState()
        updateNavigationDrawerPlaylists()

        if (PlaybackState.currentEntryId != null) {
            PlaybackState.currentSearchResult?.let { result ->
                adapter.updateActivePlayback(result)
            }
        }
    }

    private fun updateNavigationDrawerPlaylists() {
        val playlistContainer = findViewById<LinearLayout>(R.id.playlist_container)
        playlistContainer.removeAllViews()

        PlaylistManager.getAllPlaylists().forEachIndexed { index, playlist ->
            val menuItemView =
                layoutInflater.inflate(R.layout.drawer_menu_item, playlistContainer, false)
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