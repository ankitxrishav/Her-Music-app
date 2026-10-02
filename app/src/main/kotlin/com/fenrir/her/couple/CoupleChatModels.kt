package com.fenrir.her.couple

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SharedSongAttachment(
  val id: String,
  val title: String,
  val artist: String,
  val thumbnailUrl: String? = null,
  val formatBadge: String? = "HI-RES",
  val addedBy: String = "",
  val note: String? = null,
  val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class CoupleChatMessage(
  val id: String = System.currentTimeMillis().toString() + "_" + (1000..9999).random(),
  val sender: String,
  val text: String,
  val timestamp: Long = System.currentTimeMillis(),
  val song: SharedSongAttachment? = null,
  val reaction: String? = null
)

@Serializable
data class CoupleNowPlaying(
  val user: String,
  val title: String,
  val artist: String,
  val videoId: String,
  val thumbnailUrl: String? = null,
  val timestamp: Long = System.currentTimeMillis()
)

object CoupleSerialization {
  val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  fun parseMessages(raw: String): List<CoupleChatMessage> {
    return try {
      if (raw.isBlank()) emptyList() else json.decodeFromString(raw)
    } catch (e: Exception) {
      emptyList()
    }
  }

  fun serializeMessages(messages: List<CoupleChatMessage>): String {
    return try {
      json.encodeToString(messages)
    } catch (e: Exception) {
      "[]"
    }
  }

  fun parseSharedSongs(raw: String): List<SharedSongAttachment> {
    return try {
      if (raw.isBlank()) emptyList() else json.decodeFromString(raw)
    } catch (e: Exception) {
      emptyList()
    }
  }

  fun serializeSharedSongs(songs: List<SharedSongAttachment>): String {
    return try {
      json.encodeToString(songs)
    } catch (e: Exception) {
      "[]"
    }
  }

  fun parseNowPlaying(raw: String): CoupleNowPlaying? {
    return try {
      if (raw.isBlank()) null else json.decodeFromString(raw)
    } catch (e: Exception) {
      null
    }
  }

  fun serializeNowPlaying(nowPlaying: CoupleNowPlaying): String {
    return try {
      json.encodeToString(nowPlaying)
    } catch (e: Exception) {
      "{}"
    }
  }
}
