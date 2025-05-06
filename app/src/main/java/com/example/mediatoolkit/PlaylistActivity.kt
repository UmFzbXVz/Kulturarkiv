package com.example.mediatoolkit

import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class PlaylistActivity : AppCompatActivity() {

    internal var playlist: Playlist? = null
    private lateinit var editTextName: EditText
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var mediaAdapter: SearchResultAdapter
    private lateinit var recyclerViewMedier: RecyclerView
    private var mediaList: MutableList<SearchResult> = mutableListOf()
    private var currentlyPlayingId: String? = null




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        // Initialisér SharedPreferences
        sharedPreferences = getSharedPreferences("player_prefs", MODE_PRIVATE)

        // Indlæs playlister
        PlaylistManager.loadPlaylistsFromPrefs(this)

        val playlistId = intent.getStringExtra("playlistId")
        playlist = PlaylistManager.getPlaylistById(playlistId ?: "")

        editTextName = findViewById(R.id.editTextName)
        val buttonDelete = findViewById<ImageButton>(R.id.buttonDelete)
        recyclerViewMedier = findViewById(R.id.recyclerViewMedier)

        playlist?.let { pl ->
            editTextName.setText(pl.name)

            // Hent afspillede ID'er fra SharedPreferences
            val playedIds = sharedPreferences.getStringSet("played_ids", emptySet()) ?: emptySet()
            val filteredMediaList = if (PlaybackState.showPlayed) {
                pl.mediaList.toMutableList() // Show all media
            } else {
                pl.mediaList.filterNot { media ->
                    playedIds.contains(media.kalturaId)
                }.toMutableList() // Filter out played media
            }

            mediaList = if (PlaybackState.showPlayed) {
                pl.mediaList.toMutableList()
            } else {
                pl.mediaList.filterNot { media ->
                    playedIds.contains(media.kalturaId)
                }.toMutableList()
            }

            mediaAdapter = SearchResultAdapter(mediaList) { }
            recyclerViewMedier.layoutManager = LinearLayoutManager(this)
            recyclerViewMedier.adapter = mediaAdapter

            // Sætter playlisten i PlaybackState
            // Dette afgør om vi sender FULD playliste eller FILTRERET playliste
            PlaybackState.currentPlaylist = mediaList


            // FAB til playlisteafpilning
            val fabPlayAll = findViewById<FloatingActionButton>(R.id.fab_play_all)
            fabPlayAll.setOnClickListener {
                playlist?.mediaList?.forEach { media ->
                    Log.d("PlaylistActivity", "Media (log(mediaList)): ${media.title}, ID: ${media.id}")
                }

                val intent = Intent(this, PlayerActivity::class.java)
                intent.putParcelableArrayListExtra("playlistItems", ArrayList(playlist?.mediaList ?: emptyList()))
                startActivity(intent)
            }

            fabPlayAll.isEnabled = mediaList.isNotEmpty()


            // Focus change listener
            editTextName.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    saveAndHideKeyboard()
                }
            }

            // Keyboard action listener
            editTextName.setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                ) {
                    saveAndHideKeyboard()
                    true
                } else {
                    false
                }
            }

            buttonDelete.setOnClickListener {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Bekræft sletning")
                builder.setMessage("Er du sikker på, at du vil slette denne playliste?")
                builder.setPositiveButton("Ja") { dialog: DialogInterface, _: Int ->
                    PlaylistManager.deletePlaylist(pl)
                    PlaylistManager.savePlaylistsToPrefs(this)
                    Toast.makeText(this, "Playliste slettet", Toast.LENGTH_SHORT).show()
                    finish()
                }
                builder.setNegativeButton("Nej") { dialog: DialogInterface, _: Int ->
                    dialog.dismiss()
                }
                builder.show()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val previousPlayedIds = PlaybackState.currentPlayedIds // Gem før load
        loadPlaybackState()
        val newPlayedIds = PlaybackState.currentPlayedIds

        // Sammenlign for ændringer
        val changedItems = mediaList.mapIndexedNotNull { index, item ->
            val wasPlayed = previousPlayedIds.contains(item.kalturaId)
            val isPlayed = newPlayedIds.contains(item.kalturaId)
            if (wasPlayed != isPlayed || item.id == currentlyPlayingId || item.id == PlaybackState.currentSearchResult?.id) {
                index
            } else {
                null
            }
        }

        currentlyPlayingId = PlaybackState.currentSearchResult?.id

        changedItems.forEach { mediaAdapter.notifyItemChanged(it) }
    }


    private fun saveAndHideKeyboard() {
        val name = editTextName.text.toString().trim()
        if (name.isNotEmpty()) {
            playlist?.updateName(name)
            PlaylistManager.savePlaylistsToPrefs(this)
        }

        editTextName.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editTextName.windowToken, 0)
    }


    private fun loadPlaybackState() {
        // Hent playedIds som Set fra SharedPreferences
        val prefs = getSharedPreferences("player_prefs", MODE_PRIVATE)
        val playedIdsSet = prefs.getStringSet("played_ids", emptySet()) ?: emptySet()

        PlaybackState.currentPlayedIds = playedIdsSet


        Log.d("", "playedIds: $playedIdsSet")
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (currentFocus != null && ev.action == MotionEvent.ACTION_DOWN) {
            val outRect = Rect()
            currentFocus?.getGlobalVisibleRect(outRect)
            if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                currentFocus?.clearFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

}