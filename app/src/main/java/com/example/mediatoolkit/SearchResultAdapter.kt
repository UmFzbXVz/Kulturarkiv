package com.example.mediatoolkit

import android.content.Intent
import android.graphics.text.LineBreaker
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale
import android.widget.ImageView
import com.bumptech.glide.Glide
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.bumptech.glide.load.engine.DiskCacheStrategy

class SearchResultAdapter(
    private val searchResults: MutableList<SearchResult>
) : RecyclerView.Adapter<SearchResultAdapter.ItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
        return ItemViewHolder(view)
    }

    fun clear() {
        searchResults.clear()
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(searchResults[position])
    }

    fun appendData(newItems: List<SearchResult>) {
        val filteredItems = newItems.filterNot { isPlayed(it.kalturaId.toString()) }
        val start = searchResults.size
        searchResults.addAll(filteredItems)
        notifyItemRangeInserted(start, filteredItems.size)
    }

    override fun getItemCount(): Int = searchResults.size

    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val thumbnailImageView: ImageView = itemView.findViewById(R.id.thumbnailImageView)
        private val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
        private val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)
        private val playIcon: ImageView = itemView.findViewById(R.id.playIcon)
        private val originIcon: ImageView = itemView.findViewById(R.id.originIcon)

        init {
            descriptionTextView.ellipsize = TextUtils.TruncateAt.END
            descriptionTextView.isSingleLine = false
            descriptionTextView.breakStrategy = LineBreaker.BREAK_STRATEGY_SIMPLE
        }

        fun bind(result: SearchResult) {
            titleTextView.text = result.title
            dateTextView.text = result.startTime?.let { formatDate(it) } ?: "Invalid Date"
            descriptionTextView.text = preprocessDescription(result.description)
            durationTextView.text = formatDuration(result.durationMs)
            playIcon.visibility = View.GONE
            originIcon.clearColorFilter()

            val drawableResId = when (result.origin) {
                "ds.tv" -> R.drawable.tv
                "ds.radio" -> R.drawable.radio
                else -> return
            }
            originIcon.setImageResource(drawableResId)

            durationTextView.visibility = if (durationTextView.text.isNullOrEmpty()) View.GONE else View.VISIBLE

            val thumbUrl = "https://vod-cache.kaltura.nordu.net/p/397/sp/39700/thumbnail/entry_id/${result.kalturaId}/version/100002/height/640/width/640"

            Glide.with(itemView.context)
                .load(thumbUrl)
                .transform(FeatherTransformation())
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(thumbnailImageView.width, thumbnailImageView.height)
                .into(thumbnailImageView)

            itemView.setOnClickListener {
                val intent = Intent(itemView.context, PlayerActivity::class.java).apply {
                    putExtra("searchResult", result)
                }
                itemView.context.startActivity(intent)
            }

            itemView.setOnLongClickListener {
                showPopupMenu(itemView, result)
                true
            }

            descriptionTextView.post {
                if (descriptionTextView.height > 0 && descriptionTextView.lineHeight > 0) {
                    val totalHeight = itemView.height
                    val titleHeight = titleTextView.height
                    val otherElementsHeight = dateTextView.height + durationTextView.height
                    val paddingAndMargins = itemView.paddingTop + itemView.paddingBottom +
                            descriptionTextView.paddingTop + descriptionTextView.paddingBottom
                    val availableHeight = totalHeight - titleHeight - otherElementsHeight - paddingAndMargins

                    val maxLines = ((availableHeight * 0.9) / descriptionTextView.lineHeight).toInt().coerceAtLeast(1)
                    descriptionTextView.maxLines = maxLines
                    descriptionTextView.requestLayout()
                } else {
                    descriptionTextView.maxLines = 2
                }
            }

            if (result.id == PlaybackState.currentSearchResult?.id) {
                playIcon.visibility = View.VISIBLE
            }

            if (PlaybackState.currentPlayedIds.contains(result.kalturaId)) {
                originIcon.setColorFilter(
                    ContextCompat.getColor(itemView.context, R.color.OKgreen),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
        }

        private fun preprocessDescription(description: String?): String {
            if (description.isNullOrEmpty()) return ""
            val maxLength = 500
            var result = if (description.length > maxLength) {
                description.substring(0, maxLength) + "…"
            } else {
                description
            }
            result = result.replace(Regex("(\\S{30})"), "$1\u200B")
            return result
        }

        private fun showPopupMenu(view: View, result: SearchResult) {
            val context = view.context
            val popupMenu = PopupMenu(context, view).apply {
                menuInflater.inflate(R.menu.menu_main, menu)
            }

            when (context) {
                is MainActivity -> {
                    popupMenu.menu.findItem(R.id.menu_item_add_to_playlist)?.isVisible = true
                    popupMenu.menu.findItem(R.id.menu_item_remove_from_playlist)?.isVisible = false
                    popupMenu.menu.findItem(R.id.menu_item_download)?.isVisible = false  // Skjult i MainActivity
                }
                is PlaylistActivity -> {
                    popupMenu.menu.findItem(R.id.menu_item_add_to_playlist)?.isVisible = false
                    popupMenu.menu.findItem(R.id.menu_item_remove_from_playlist)?.isVisible = true
                    popupMenu.menu.findItem(R.id.menu_item_download)?.isVisible = true
                }
                else -> {
                    popupMenu.menu.findItem(R.id.menu_item_add_to_playlist)?.isVisible = false
                    popupMenu.menu.findItem(R.id.menu_item_remove_from_playlist)?.isVisible = false
                    popupMenu.menu.findItem(R.id.menu_item_download)?.isVisible = false
                }
            }

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_item_add_to_playlist -> {
                        addToPlaylist(result)
                        true
                    }
                    R.id.menu_item_remove_from_playlist -> {
                        removeFromPlaylist(result)
                        true
                    }
                    R.id.menu_item_download -> {
                        android.app.AlertDialog.Builder(context)
                            .setTitle("Bekræft download")
                            .setMessage("${DownloadManager.formatDate(result.startTime.toString())}\n${result.title}")
                            .setPositiveButton("Ja") { _, _ ->
                                DownloadManager.startDownload(context, result)
                                Toast.makeText(context, "Download påbegyndt", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Nej", null)
                            .show()
                        true
                    }
                    else -> false
                }
            }

            popupMenu.show()
        }

        private fun addToPlaylist(result: SearchResult) {
            val context = itemView.context
            val playlists = PlaylistManager.getAllPlaylists()
            val playlistNames = playlists.map { it.name }.toTypedArray()

            android.app.AlertDialog.Builder(context)
                .setItems(playlistNames) { _, which ->
                    val selected = playlists[which]
                    selected.addMedia(result)
                    PlaylistManager.savePlaylistsToPrefs(context)
                    Toast.makeText(context, "Føjet til '${selected.name}'", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Annuller", null)
                .show()
        }

        private fun removeFromPlaylist(result: SearchResult) {
            val context = itemView.context
            if (context is PlaylistActivity) {
                context.playlist?.let { playlist ->
                    playlist.removeMedia(result)
                    PlaylistManager.savePlaylistsToPrefs(context)
                    val position = searchResults.indexOf(result)
                    if (position != -1) {
                        searchResults.removeAt(position)
                        notifyItemRemoved(position)
                    }
                } ?: Toast.makeText(context, "Ingen aktiv playliste fundet", Toast.LENGTH_SHORT).show()
            }
        }

        private fun formatDate(dateString: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("d. MMMM yyyy", Locale("da", "DK"))
                inputFormat.parse(dateString)?.let { outputFormat.format(it) } ?: "Invalid Date"
            } catch (e: Exception) {
                "Invalid Date"
            }
        }

        private fun formatDuration(durationMs: Int?): String {
            if (durationMs == null || durationMs <= 0) return "0m"
            val totalSeconds = durationMs / 1000
            val days = totalSeconds / 86400
            val hours = (totalSeconds % 86400) / 3600
            val minutes = (totalSeconds % 3600) / 60
            return buildList {
                if (days > 0) add("${days}d")
                if (hours > 0) add("${hours}t")
                if (minutes > 0 || isEmpty()) add("${minutes}m")
            }.joinToString(" ")
        }
    }

    fun getAllItems(): List<SearchResult> = searchResults

    fun updateActivePlayback(newResult: SearchResult?) {
        val oldPosition = searchResults.indexOfFirst { it.id == PlaybackState.currentSearchResult?.id }
        val newPosition = searchResults.indexOfFirst { it.id == newResult?.id }
        if (oldPosition != -1) notifyItemChanged(oldPosition)
        if (newPosition != -1) notifyItemChanged(newPosition)
        PlaybackState.currentSearchResult = newResult
    }

    private fun isPlayed(kalturaId: String): Boolean {
        return PlaybackState.currentPlayedIds.contains(kalturaId)
    }
}
