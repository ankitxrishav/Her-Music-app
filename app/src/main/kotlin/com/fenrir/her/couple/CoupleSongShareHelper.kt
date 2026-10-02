package com.fenrir.her.couple

import android.content.Context
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import com.music.innertube.models.SongItem
import com.fenrir.her.auth.CoupleAuthGuard
import com.fenrir.her.constants.AccountEmailKey
import com.fenrir.her.constants.ActiveCoupleProfileKey
import com.fenrir.her.constants.CoupleChatMessagesKey
import com.fenrir.her.constants.CoupleSharedSongsKey
import com.fenrir.her.models.MediaMetadata
import com.fenrir.her.utils.dataStore
import kotlinx.coroutines.flow.first

object CoupleSongShareHelper {
  suspend fun shareSong(
    context: Context,
    songId: String,
    title: String,
    artist: String,
    thumbnailUrl: String?,
    formatBadge: String? = "HI-RES",
    note: String? = null
  ) {
    val prefs = context.dataStore.data.first()
    val email = prefs[AccountEmailKey].orEmpty()
    val profile = prefs[ActiveCoupleProfileKey].orEmpty()
    val role = CoupleAuthGuard.resolveRole(email, profile)
    val sender = CoupleAuthGuard.getMyName(role)
    val receiver = CoupleAuthGuard.getPartnerName(role)

    val attachment = SharedSongAttachment(
      id = songId,
      title = title,
      artist = artist,
      thumbnailUrl = thumbnailUrl,
      formatBadge = formatBadge,
      addedBy = sender,
      note = note
    )

    val message = CoupleChatMessage(
      sender = sender,
      text = note ?: "Shared a song for you: $title",
      song = attachment
    )

    context.dataStore.edit { editPrefs ->
      val currentMsgs = CoupleSerialization.parseMessages(editPrefs[CoupleChatMessagesKey].orEmpty()).toMutableList()
      currentMsgs.add(message)
      editPrefs[CoupleChatMessagesKey] = CoupleSerialization.serializeMessages(currentMsgs)

      val currentSongs = CoupleSerialization.parseSharedSongs(editPrefs[CoupleSharedSongsKey].orEmpty()).toMutableList()
      currentSongs.removeAll { it.id == songId }
      currentSongs.add(0, attachment)
      editPrefs[CoupleSharedSongsKey] = CoupleSerialization.serializeSharedSongs(currentSongs)
    }

    Toast.makeText(context, "Sent \"$title\" to $receiver with love ❤️", Toast.LENGTH_SHORT).show()
  }

  suspend fun shareSong(context: Context, song: SongItem, note: String? = null) {
    shareSong(
      context = context,
      songId = song.id,
      title = song.title,
      artist = song.artists.joinToString { it.name },
      thumbnailUrl = song.thumbnail,
      note = note
    )
  }

  suspend fun shareMediaMetadata(context: Context, mediaMetadata: MediaMetadata, note: String? = null) {
    shareSong(
      context = context,
      songId = mediaMetadata.id,
      title = mediaMetadata.title,
      artist = mediaMetadata.artists.joinToString { it.name },
      thumbnailUrl = mediaMetadata.thumbnailUrl,
      note = note
    )
  }
}
