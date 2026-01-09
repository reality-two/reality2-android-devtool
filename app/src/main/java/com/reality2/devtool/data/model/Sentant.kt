package com.reality2.devtool.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Represents a Sentant on a Reality2 node
 */
@Serializable
@Parcelize
data class Sentant(
    val id: String,
    val name: String,
    val description: String? = null,
    val events: List<SentantEvent> = emptyList(),
    val signals: List<String> = emptyList(),
    val state: String? = null
) : Parcelable

/**
 * Represents an event that can be sent to a Sentant
 */
@Serializable
@Parcelize
data class SentantEvent(
    val event: String
) : Parcelable
