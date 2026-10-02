package com.fenrir.her.ui.screens.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.fenrir.her.constants.DownloadFolderNameKey
import com.fenrir.her.constants.DownloadStructure
import com.fenrir.her.constants.DownloadQuality
import com.fenrir.her.constants.DownloadQualityKey
import com.fenrir.her.constants.DownloadStructureKey
import com.fenrir.her.constants.EmbedLyricsOnDownloadKey
import com.fenrir.her.db.MusicDatabase
import com.fenrir.her.db.entities.Song
import com.fenrir.her.utils.dataStore
import com.fenrir.her.utils.get
import androidx.datastore.preferences.core.edit
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadedTrack(
  val id: String,
  val title: String,
  val artist: String,
  val album: String,
  val artworkUrl: String?,
  val filePath: String,
  val fileSizeBytes: Long,
  val formatBadge: String,
  val durationMs: Long,
  val isLossless: Boolean,
  val hasLyrics: Boolean,
  val downloadedAtMillis: Long = System.currentTimeMillis(),
  val originalSong: Song? = null,
)

data class DownloadedArtist(
  val name: String,
  val tracks: List<DownloadedTrack>,
  val albumCount: Int,
  val artworkUrl: String?,
  val totalSizeBytes: Long,
)

data class DownloadedAlbum(
  val title: String,
  val artist: String,
  val tracks: List<DownloadedTrack>,
  val artworkUrl: String?,
  val totalSizeBytes: Long,
  val totalDurationMs: Long,
)

@HiltViewModel
class DownloadsViewModel
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val database: MusicDatabase,
) : ViewModel() {

  private val _scannedFiles = MutableStateFlow<List<DownloadedTrack>>(emptyList())

  val downloadFolder = MutableStateFlow("Her")
  val downloadStructure = MutableStateFlow(DownloadStructure.FLAT)
  val downloadLyrics = MutableStateFlow(true)
  val downloadQuality = MutableStateFlow(DownloadQuality.LOSSLESS_FLAC)

  init {
    viewModelScope.launch {
      val folder = context.dataStore[DownloadFolderNameKey] ?: "Her"
      val structStr = context.dataStore[DownloadStructureKey] ?: DownloadStructure.FLAT.name
      val lyrics = context.dataStore[EmbedLyricsOnDownloadKey] ?: true
      val qualityStr = context.dataStore[DownloadQualityKey] ?: DownloadQuality.LOSSLESS_FLAC.name

      downloadFolder.value = folder
      downloadStructure.value = runCatching { DownloadStructure.valueOf(structStr) }.getOrDefault(DownloadStructure.FLAT)
      downloadLyrics.value = lyrics
      downloadQuality.value = runCatching { DownloadQuality.valueOf(qualityStr) }.getOrDefault(DownloadQuality.LOSSLESS_FLAC)

      refreshLocalFiles()
    }
  }

  fun setDownloadFolder(name: String) {
    val clean = name.trim().ifBlank { "Her" }
    downloadFolder.value = clean
    viewModelScope.launch(Dispatchers.IO) {
      context.dataStore.edit { it[DownloadFolderNameKey] = clean }
      refreshLocalFiles()
    }
  }

  fun setDownloadStructure(structure: DownloadStructure) {
    downloadStructure.value = structure
    viewModelScope.launch(Dispatchers.IO) {
      context.dataStore.edit { it[DownloadStructureKey] = structure.name }
    }
  }

  fun setDownloadLyrics(enabled: Boolean) {
    downloadLyrics.value = enabled
    viewModelScope.launch(Dispatchers.IO) {
      context.dataStore.edit { it[EmbedLyricsOnDownloadKey] = enabled }
    }
  }

  fun setDownloadQuality(quality: DownloadQuality) {
    downloadQuality.value = quality
    viewModelScope.launch(Dispatchers.IO) {
      context.dataStore.edit { it[DownloadQualityKey] = quality.name }
    }
  }

  val downloadedTracks: StateFlow<List<DownloadedTrack>> =
    combine(
      database.downloadedSongsByCreateDateAsc(),
      _scannedFiles.asStateFlow(),
    ) { dbSongs, scanned ->
      val dbTracks = dbSongs.map { song ->
        val fmt = song.format
        val mime = fmt?.mimeType.orEmpty().lowercase()
        val isFlac = mime.contains("flac") || fmt?.codecs.orEmpty().contains("flac")
        val badge = when {
          isFlac -> "FLAC"
          mime.contains("opus") -> "OPUS"
          mime.contains("mp4") || mime.contains("m4a") -> "AAC"
          mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
          else -> "AUDIO"
        }
        DownloadedTrack(
          id = song.song.id,
          title = song.song.title,
          artist = song.artists.joinToString { it.name }.ifBlank { "Unknown Artist" },
          album = song.song.albumName.orEmpty(),
          artworkUrl = song.song.thumbnailUrl,
          filePath = "",
          fileSizeBytes = fmt?.contentLength ?: (song.song.duration * 32000L),
          formatBadge = badge,
          durationMs = song.song.duration * 1000L,
          isLossless = isFlac || (fmt?.bitrate ?: 0) >= 800000,
          hasLyrics = true,
          downloadedAtMillis = System.currentTimeMillis(),
          originalSong = song,
        )
      }

      val merged = (dbTracks + scanned).distinctBy { "${it.title.lowercase()}_${it.artist.lowercase()}" }
      merged
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val downloadedArtists: StateFlow<List<DownloadedArtist>> =
    downloadedTracks.combine(downloadedTracks) { tracks, _ ->
      tracks.groupBy { it.artist.trim().ifBlank { "Unknown Artist" } }
        .map { (artistName, artistTracks) ->
          val uniqueAlbums = artistTracks.map { it.album.trim() }
            .filter { it.isNotBlank() && !it.equals("Singles", ignoreCase = true) }
            .distinct()
          val artwork = artistTracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
          val totalSize = artistTracks.sumOf { it.fileSizeBytes }
          DownloadedArtist(
            name = artistName,
            tracks = artistTracks,
            albumCount = uniqueAlbums.size,
            artworkUrl = artwork,
            totalSizeBytes = totalSize,
          )
        }.sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val downloadedAlbums: StateFlow<List<DownloadedAlbum>> =
    downloadedTracks.combine(downloadedTracks) { tracks, _ ->
      tracks.filter { it.album.isNotBlank() && !it.album.equals("Singles", ignoreCase = true) }
        .groupBy { it.album.trim().lowercase() }
        .map { (_, albumTracks) ->
          val albumTitle = albumTracks.first().album.trim()
          val artistName = albumTracks.groupBy { it.artist }.maxByOrNull { it.value.size }?.key ?: albumTracks.first().artist
          val artwork = albumTracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
          DownloadedAlbum(
            title = albumTitle,
            artist = artistName,
            tracks = albumTracks,
            artworkUrl = artwork,
            totalSizeBytes = albumTracks.sumOf { it.fileSizeBytes },
            totalDurationMs = albumTracks.sumOf { it.durationMs },
          )
        }.sortedBy { it.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val totalBytes: StateFlow<Long> =
    downloadedTracks.combine(downloadedTracks) { tracks, _ ->
      tracks.sumOf { it.fileSizeBytes }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

  fun refreshLocalFiles() {
    viewModelScope.launch(Dispatchers.IO) {
      val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
      val folder = File(musicDir, downloadFolder.value)
      if (!folder.exists() || !folder.isDirectory) {
        _scannedFiles.value = emptyList()
        return@launch
      }

      val files = mutableListOf<DownloadedTrack>()
      folder.walkTopDown().filter { it.isFile }.forEach { file ->
        val ext = file.extension.lowercase()
        if (ext in listOf("flac", "m4a", "mp3", "opus", "ogg")) {
          val nameWithoutExt = file.nameWithoutExtension
          val parts = nameWithoutExt.split(" - ", limit = 2)
          val (artist, title) = if (parts.size == 2) parts[0] to parts[1] else "Unknown Artist" to nameWithoutExt
          val lrcFile = File(file.parentFile, "$nameWithoutExt.lrc")
          val badge = when (ext) {
            "flac" -> "FLAC"
            "opus", "ogg" -> "OPUS"
            "m4a" -> "AAC"
            "mp3" -> "MP3"
            else -> ext.uppercase()
          }
          files.add(
            DownloadedTrack(
              id = file.absolutePath,
              title = title,
              artist = artist,
              album = file.parentFile?.name?.takeIf { it != folder.name } ?: "",
              artworkUrl = null,
              filePath = file.absolutePath,
              fileSizeBytes = file.length(),
              formatBadge = badge,
              durationMs = 0L,
              isLossless = ext == "flac",
              hasLyrics = lrcFile.exists(),
              downloadedAtMillis = file.lastModified(),
            )
          )
        }
      }
      _scannedFiles.value = files
    }
  }

  fun deleteTrack(track: DownloadedTrack) {
    viewModelScope.launch(Dispatchers.IO) {
      if (track.filePath.isNotBlank()) {
        val f = File(track.filePath)
        if (f.exists()) f.delete()
        val lrc = File(f.parentFile, "${f.nameWithoutExtension}.lrc")
        if (lrc.exists()) lrc.delete()
      }
      if (track.originalSong != null) {
        database.updateDownloadedInfo(track.originalSong.song.id, false, null)
      }
      refreshLocalFiles()
    }
  }

  fun clearAllDownloads() {
    viewModelScope.launch(Dispatchers.IO) {
      val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
      val folder = File(musicDir, downloadFolder.value)
      if (folder.exists()) {
        folder.deleteRecursively()
        folder.mkdirs()
      }
      val songs = database.downloadedSongsByCreateDateAsc()
      // clear db records
      refreshLocalFiles()
    }
  }

  fun openInFileManager() {
    runCatching {
      val intent = Intent(Intent.ACTION_VIEW).apply {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val folder = File(musicDir, downloadFolder.value)
        setDataAndType(Uri.fromFile(folder), "resource/folder")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
    }
  }
}
