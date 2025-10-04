package com.example.mediatoolkit

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class SearchResult(
    val title: String?,
    val id: String?,
    val durationMs: Int?,
    val description: String?,
    val startTime: String?,
    val origin: String?,
    val kalturaId: String?,
    val internalSeriesId: String?,
    val internalSeasonId: String?,
    val internalEpisodeId: String?,
) : Parcelable