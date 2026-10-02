package com.fenrir.her.playback

import com.fenrir.her.db.entities.SongEntity

interface ISyncUtils {
  fun likeSong(song: SongEntity)
}
