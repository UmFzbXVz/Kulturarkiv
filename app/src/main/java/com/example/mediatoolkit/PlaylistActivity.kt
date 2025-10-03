package com.example.mediatoolkit

import android.content.DialogInterface
import android.os.Bundle
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
    private lateinit var mediaAdapter: SearchResultAdapter
    private lateinit var recyclerViewMedier: RecyclerView
    private var mediaList: MutableList<SearchResult> = mutableListOf()
    private var currentlyPlayingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        val playlistId = intent.getStringExtra("playlistId")
        playlist = PlaylistManager.getPlaylistById(playlistId ?: "")

        editTextName = findViewById(R.id.editTextName)
        val buttonDelete = findViewById<ImageButton>(R.id.buttonDelete)
        recyclerViewMedier = findViewById(R.id.recyclerViewMedier)

        playlist?.let { pl ->
            editTextName.setText(pl.name)

            val playedIds = getSharedPreferences("player_prefs", MODE_PRIVATE).getStringSet("played_ids", emptySet()) ?: emptySet()
            mediaList = if (PlaybackState.showPlayed) {
                pl.mediaList.toMutableList()
            } else {
                pl.mediaList.filterNot { media ->
                    playedIds.contains(media.kalturaId)
                }.toMutableList()
            }

            mediaAdapter = SearchResultAdapter(mediaList)
            recyclerViewMedier.layoutManager = LinearLayoutManager(this)
            recyclerViewMedier.adapter = mediaAdapter

            val fabPlayAll = findViewById<FloatingActionButton>(R.id.fab_play_all)
            fabPlayAll.setOnClickListener {
                val intent = Intent(this, PlayerActivity::class.java)
                intent.putParcelableArrayListExtra("playlistItems", ArrayList(pl.mediaList))
                startActivity(intent)
            }
            fabPlayAll.isEnabled = mediaList.isNotEmpty()

            editTextName.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    saveAndHideKeyboard()
                }
            }

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

        val previousPlayedIds = PlaybackState.currentPlayedIds
        loadPlaybackState()
        val newPlayedIds = PlaybackState.currentPlayedIds

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
        val prefs = getSharedPreferences("player_prefs", MODE_PRIVATE)
        val playedIdsSet = prefs.getStringSet("played_ids", emptySet()) ?: emptySet()

        PlaybackState.currentPlayedIds = playedIdsSet
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
