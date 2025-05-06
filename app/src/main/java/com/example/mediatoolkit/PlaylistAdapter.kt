package com.example.mediatoolkit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class PlaylistAdapter(
    private var playlists: List<Playlist>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    interface OnItemClickListener {
        fun onPlaylistClicked(playlist: Playlist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.playlist_item, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.bind(playlist)
    }

    override fun getItemCount(): Int = playlists.size

    fun updatePlaylists(newPlaylists: List<Playlist>) {
        playlists = newPlaylists
        notifyDataSetChanged()
    }

    inner class PlaylistViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        fun bind(playlist: Playlist) {

            itemView.setOnClickListener {
                listener.onPlaylistClicked(playlist)
            }
        }
    }
}