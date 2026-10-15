package com.fenrir.her.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.datastore.preferences.core.edit
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.music.innertube.YouTube
import com.fenrir.her.constants.AudioQuality
import com.fenrir.her.constants.DownloadQuality
import com.fenrir.her.constants.DownloadQualityKey
import com.fenrir.her.constants.DownloadStructure
import com.fenrir.her.constants.DownloadStructureKey
import com.fenrir.her.constants.EmbedLyricsOnDownloadKey
import com.fenrir.her.constants.ExportProgressKey
import com.fenrir.her.constants.ExportedSongIdsKey
import com.fenrir.her.constants.ExportingSongIdsKey
import com.fenrir.her.utils.YTPlayerUtils
import com.fenrir.her.utils.dataStore
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

class AudioExportService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val httpClient = OkHttpClient()
  private val activeExports = java.util.concurrent.atomic.AtomicInteger(0)
  private lateinit var notificationManager: NotificationManager

  override fun onCreate() {
    super.onCreate()
    notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    createNotificationChannel()
    serviceScope.launch {
      dataStore.edit { preferences ->
        preferences.remove(ExportingSongIdsKey)
        preferences.remove(ExportProgressKey)
      }
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Audio Export",
        NotificationManager.IMPORTANCE_LOW
      )
      notificationManager.createNotificationChannel(channel)
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val songId = intent?.getStringExtra(EXTRA_SONG_ID) ?: return START_NOT_STICKY
    val songTitle = intent.getStringExtra(EXTRA_SONG_TITLE).orEmpty()
    val songArtist = intent.getStringExtra(EXTRA_SONG_ARTIST).orEmpty()
    val songAlbum = intent.getStringExtra(EXTRA_SONG_ALBUM).orEmpty()
    val artworkUrl = intent.getStringExtra(EXTRA_ARTWORK_URL).orEmpty()
    val targetDirectoryUri = intent.getStringExtra(EXTRA_TARGET_DIRECTORY_URI).orEmpty()

    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Exporting audio")
      .setContentText(songTitle.ifBlank { "Export in progress..." })
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setOngoing(true)
      .setProgress(100, 0, true)
      .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }

    activeExports.incrementAndGet()
    serviceScope.launch {
      exportSong(
        songId = songId,
        songTitle = songTitle,
        songArtist = songArtist,
        songAlbum = songAlbum,
        artworkUrl = artworkUrl,
        targetDirectoryUri = targetDirectoryUri,
      )
    }
    return START_NOT_STICKY
  }

  private suspend fun exportSong(
    songId: String,
    songTitle: String,
    songArtist: String,
    songAlbum: String,
    artworkUrl: String,
    targetDirectoryUri: String,
  ) {
    val safeTitle = sanitizeTitle(songTitle.ifBlank { songId })
    addExportingSongId(songId)

    val prefs = dataStore.data.first()
    val quality = prefs[DownloadQualityKey]?.let { runCatching { DownloadQuality.valueOf(it) }.getOrNull() } ?: DownloadQuality.YOUTUBE
    val structure = prefs[DownloadStructureKey]?.let { runCatching { DownloadStructure.valueOf(it) }.getOrNull() } ?: DownloadStructure.FLAT

    val tempSourceFile = File.createTempFile("export_source_", ".tmp", cacheDir)
    val tempArtworkFile = File.createTempFile("export_cover_", ".jpg", cacheDir)

    try {
      if (cacheDir.freeSpace < 20 * 1024 * 1024L) {
        throw java.io.IOException("Insufficient disk storage")
      }

      val connectivityManager =
        getSystemService<ConnectivityManager>() ?: error("No connectivity manager")
      val playbackData =
        YTPlayerUtils.playerResponseForPlayback(
            videoId = songId,
            audioQuality = AudioQuality.OPUS,
            connectivityManager = connectivityManager,
          )
          .getOrThrow()

      val isWebm = playbackData.format.mimeType.contains("webm") || playbackData.format.mimeType.contains("opus")
      val (extension, mimeType) = when (quality) {
        DownloadQuality.MP3_320 -> "mp3" to "audio/mpeg"
        DownloadQuality.HIGH_AAC -> "m4a" to "audio/mp4"
        DownloadQuality.OPUS -> "opus" to "audio/opus"
        DownloadQuality.LOSSLESS_FLAC -> "flac" to "audio/flac"
        DownloadQuality.YOUTUBE -> if (isWebm) "opus" to "audio/opus" else "m4a" to "audio/mp4"
      }

      val tempOutputFile = File.createTempFile("export_result_", ".$extension", cacheDir)

      try {
        val year = fetchSongYear(songId)
        downloadStream(playbackData, tempSourceFile) { percent ->
          updateExportProgress(songId, percent)
        }
        val artworkDownloaded = downloadArtwork(artworkUrl, tempArtworkFile)
        convertToTargetFormat(
          sourceFile = tempSourceFile,
          outputFile = tempOutputFile,
          songTitle = songTitle,
          songArtist = songArtist,
          songAlbum = songAlbum,
          year = year,
          artworkFile = if (artworkDownloaded) tempArtworkFile else null,
          quality = quality,
          isWebm = isWebm,
        )
        var lyricsText: String? = null
        if (dataStore.data.first()[EmbedLyricsOnDownloadKey] ?: true) {
          runCatching {
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
              applicationContext,
              com.fenrir.her.di.LyricsHelperEntryPoint::class.java
            )
            val db = entryPoint.database()
            val cached = db.lyrics(songId).first()
            if (cached != null && cached.lyrics.isNotBlank() && cached.lyrics != com.fenrir.her.db.entities.LyricsEntity.LYRICS_NOT_FOUND) {
              lyricsText = cached.lyrics
            } else {
              val lyricsHelper = entryPoint.lyricsHelper()
              val meta = com.fenrir.her.models.MediaMetadata(
                id = songId,
                title = songTitle,
                artists = listOf(com.fenrir.her.models.MediaMetadata.Artist(id = null, name = songArtist)),
                duration = 0,
                thumbnailUrl = artworkUrl
              )
              val fetched = lyricsHelper.getLyrics(meta)
              if (!fetched.lyrics.isNullOrBlank() && fetched.lyrics != com.fenrir.her.db.entities.LyricsEntity.LYRICS_NOT_FOUND) {
                lyricsText = fetched.lyrics
                db.upsert(com.fenrir.her.db.entities.LyricsEntity(id = songId, lyrics = fetched.lyrics!!, provider = fetched.providerName ?: "Unknown"))
              }
            }
          }
        }
        writeOutputFile(
          safeTitle = safeTitle,
          targetDirectoryUri = targetDirectoryUri,
          sourceFile = tempOutputFile,
          extension = extension,
          mimeType = mimeType,
          songArtist = songArtist,
          songAlbum = songAlbum,
          structure = structure,
          lyricsText = lyricsText,
        )
        addExportedSongId(songId)
      } finally {
        tempOutputFile.delete()
      }
    } catch (e: Exception) {
      Timber.e(e, "Export failed for songId=$songId")
      val failNotification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Export failed")
        .setContentText("$songTitle: ${e.message ?: "Failed"}")
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setAutoCancel(true)
        .build()
      notificationManager.notify(songId.hashCode(), failNotification)
    } finally {
      tempSourceFile.delete()
      tempArtworkFile.delete()
      clearExportProgress(songId)
      removeExportingSongId(songId)
      if (activeExports.decrementAndGet() <= 0) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
      }
    }
  }

  private suspend fun fetchSongYear(songId: String): Int? =
    YouTube.getMediaInfo(songId).getOrNull()?.uploadDate?.let {
      Regex("(19|20)\\d{2}").find(it)?.value?.toIntOrNull()
    }

  private fun probeContentLength(url: String, headers: Map<String, String>?): Long? {
    android.net.Uri.parse(url).getQueryParameter("clen")?.toLongOrNull()?.let { if (it > 0) return it }
    val reqBuilder = Request.Builder().url(url).header("Range", "bytes=0-0")
    headers?.forEach { (k, v) -> reqBuilder.header(k, v) }
    return runCatching {
      httpClient.newCall(reqBuilder.build()).execute().use { r ->
        if (!r.isSuccessful && r.code != 206) return null
        r.header("Content-Range")?.substringAfter("/")?.toLongOrNull()
          ?: r.header("Content-Length")?.toLongOrNull()
      }
    }.getOrNull()
  }

  private suspend fun downloadStream(
    playbackData: com.fenrir.her.utils.YTPlayerUtils.PlaybackData,
    destFile: File,
    onProgress: suspend (Int) -> Unit = {},
  ) = withContext(Dispatchers.IO) {
    val streamUrl = playbackData.streamUrl
    val probedLength = playbackData.format.contentLength?.takeIf { it > 0 }
      ?: probeContentLength(streamUrl, playbackData.headers)

    suspend fun singleStreamDownload() {
      val reqBuilder = Request.Builder().url(streamUrl)
      playbackData.headers?.forEach { (k, v) -> reqBuilder.header(k, v) }
      httpClient.newCall(reqBuilder.build()).execute().use { response ->
        if (!response.isSuccessful) error("Download stream failed: HTTP ${response.code}")
        val body = response.body ?: error("No response body")
        destFile.outputStream().use { out ->
          body.byteStream().copyTo(out)
        }
      }
      onProgress(100)
    }

    if (probedLength == null || probedLength <= 0) {
      singleStreamDownload()
      return@withContext
    }

    try {
      val totalLength = probedLength
      destFile.createNewFile()
      val raf = RandomAccessFile(destFile, "rw")
      raf.setLength(totalLength)
      raf.close()

      val partSize = totalLength / PARALLEL_PARTS
      val written = java.util.concurrent.atomic.AtomicLong(0L)

      val parts = (0 until PARALLEL_PARTS).map { partIndex ->
        async {
          val start = partIndex * partSize
          val end = if (partIndex == PARALLEL_PARTS - 1) totalLength - 1 else start + partSize - 1
          val rangedUrl = "$streamUrl&range=$start-$end"
          val requestBuilder = Request.Builder().url(rangedUrl)
            .header("Range", "bytes=$start-$end")
          playbackData.headers?.forEach { (k, v) -> requestBuilder.header(k, v) }
          val request = requestBuilder.build()
          httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
              error("Part $partIndex failed: HTTP ${response.code}")
            }
            val body = response.body ?: error("No body for part $partIndex")
            val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
            var offset = start
            RandomAccessFile(destFile, "rw").use { out ->
              body.byteStream().use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                  out.seek(offset)
                  out.write(buffer, 0, read)
                  offset += read
                  val totalWritten = written.addAndGet(read.toLong())
                  val percent = ((totalWritten * 100) / totalLength).toInt().coerceIn(0, 99)
                  onProgress(percent)
                }
              }
            }
          }
        }
      }
      parts.awaitAll()
      onProgress(100)
    } catch (_: Exception) {
      singleStreamDownload()
    }
  }

  private fun downloadArtwork(artworkUrl: String, destFile: File): Boolean {
    if (artworkUrl.isBlank()) return false
    return runCatching {
        httpClient.newCall(Request.Builder().url(artworkUrl).build()).execute().use { response ->
          if (!response.isSuccessful) return@use
          response.body?.byteStream()?.use { input ->
            destFile.outputStream().use { output ->
              input.copyTo(output)
              output.flush()
            }
          }
        }
      }
      .isSuccess && destFile.length() > 0L
  }

  private fun convertToTargetFormat(
    sourceFile: File,
    outputFile: File,
    songTitle: String,
    songArtist: String,
    songAlbum: String,
    year: Int?,
    artworkFile: File?,
    quality: DownloadQuality,
    isWebm: Boolean,
  ) {
    val command =
      buildFfmpegCommand(
        inputPath = sourceFile.absolutePath,
        outputPath = outputFile.absolutePath,
        title = songTitle,
        artist = songArtist,
        album = songAlbum,
        year = year,
        coverPath = artworkFile?.absolutePath,
        quality = quality,
        isWebm = isWebm,
      )
    val session = FFmpegKit.execute(command)
    val returnCode = session.returnCode
    if (returnCode == null || !ReturnCode.isSuccess(returnCode)) {
      error("FFmpeg failed: ${session.output}")
    }
    if (!outputFile.exists() || outputFile.length() <= 0L) {
      error("Exported file is empty")
    }
  }

  private fun writeOutputFile(
    safeTitle: String,
    targetDirectoryUri: String,
    sourceFile: File,
    extension: String,
    mimeType: String,
    songArtist: String,
    songAlbum: String,
    structure: DownloadStructure,
    lyricsText: String? = null,
  ) {
    val effectiveDirUri = if (targetDirectoryUri.isBlank()) {
      val defaultMusic = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
      val herDir = File(defaultMusic, "Her")
      if (!herDir.exists()) herDir.mkdirs()
      Uri.fromFile(herDir).toString()
    } else {
      targetDirectoryUri
    }
    val uri = Uri.parse(effectiveDirUri)
    val artistName = sanitizeTitle(songArtist.ifBlank { "Unknown Artist" })
    val albumName = sanitizeTitle(songAlbum.ifBlank { "Unknown Album" })
    val rawBaseName = if (artistName != "Unknown Artist" && songArtist.isNotBlank()) "$artistName - $safeTitle" else safeTitle
    val baseName = rawBaseName.take(120).trimEnd('.', ' ')

    if (uri.scheme == "file") {
      val baseFolder = File(uri.path ?: error("Invalid export directory"))
      val folder = if (structure == DownloadStructure.ARTIST_ALBUM) {
        File(baseFolder, "$artistName/$albumName")
      } else {
        baseFolder
      }
      if (!folder.exists() && !folder.mkdirs()) error("Unable to create export directory")
      var targetFile = File(folder, "$baseName.$extension")
      var index = 2
      while (targetFile.exists()) {
        targetFile = File(folder, "$baseName ($index).$extension")
        index++
      }
      val finalBaseName = targetFile.nameWithoutExtension
      sourceFile.copyTo(targetFile, overwrite = false)
      if (!lyricsText.isNullOrBlank()) {
        runCatching { File(folder, "$finalBaseName.lrc").writeText(lyricsText) }
      }
      android.media.MediaScannerConnection.scanFile(
        this,
        arrayOf(targetFile.absolutePath),
        arrayOf(mimeType),
        null
      )
    } else {
      val rootDir =
        DocumentFile.fromTreeUri(this, uri) ?: error("Export directory unavailable")
      val targetDir = if (structure == DownloadStructure.ARTIST_ALBUM) {
        val artistDir = rootDir.findFile(artistName) ?: rootDir.createDirectory(artistName) ?: rootDir
        artistDir.findFile(albumName) ?: artistDir.createDirectory(albumName) ?: artistDir
      } else {
        rootDir
      }
      var fileName = "$baseName.$extension"
      var index = 2
      while (targetDir.findFile(fileName) != null) {
        fileName = "$baseName ($index).$extension"
        index++
      }
      val outputFile =
        targetDir.createFile(mimeType, fileName)
          ?: error("Unable to create output file")
      sourceFile.inputStream().use { input ->
        contentResolver.openOutputStream(outputFile.uri, "wt")!!.use { input.copyTo(it) }
      }
      if (!lyricsText.isNullOrBlank()) {
        val lrcName = fileName.substringBeforeLast(".") + ".lrc"
        runCatching {
          val lrcFile = targetDir.createFile("text/plain", lrcName)
          if (lrcFile != null) {
            contentResolver.openOutputStream(lrcFile.uri, "wt")?.use {
              it.write(lyricsText.toByteArray(Charsets.UTF_8))
            }
          }
        }
      }
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }

  private suspend fun addExportedSongId(songId: String) {
    dataStore.edit { preferences ->
      val current =
        preferences[ExportedSongIdsKey]
          .orEmpty()
          .split(',')
          .map { it.trim() }
          .filter { it.isNotBlank() }
      val updated = listOf(songId) + current.filterNot { it == songId }
      preferences[ExportedSongIdsKey] = updated.take(1000).joinToString(",")
    }
  }

  private suspend fun addExportingSongId(songId: String) {
    dataStore.edit { preferences ->
      val current =
        preferences[ExportingSongIdsKey]
          .orEmpty()
          .split(',')
          .map { it.trim() }
          .filter { it.isNotBlank() }
      val updated = listOf(songId) + current.filterNot { it == songId }
      preferences[ExportingSongIdsKey] = updated.take(1000).joinToString(",")
    }
  }

  private suspend fun removeExportingSongId(songId: String) {
    dataStore.edit { preferences ->
      val current =
        preferences[ExportingSongIdsKey]
          .orEmpty()
          .split(',')
          .map { it.trim() }
          .filter { it.isNotBlank() }
      preferences[ExportingSongIdsKey] = current.filterNot { it == songId }.joinToString(",")
    }
  }

  private suspend fun updateExportProgress(songId: String, percent: Int) {
    dataStore.edit { preferences ->
      val current =
        preferences[ExportProgressKey]
          .orEmpty()
          .split(',')
          .filter { it.isNotBlank() }
          .associate {
            val parts = it.split(':')
            parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
          }
          .toMutableMap()
      current[songId] = percent
      preferences[ExportProgressKey] = current.map { "${it.key}:${it.value}" }.joinToString(",")
    }
  }

  private suspend fun clearExportProgress(songId: String) {
    dataStore.edit { preferences ->
      val current =
        preferences[ExportProgressKey]
          .orEmpty()
          .split(',')
          .filter { it.isNotBlank() }
          .associate {
            val parts = it.split(':')
            parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
          }
          .toMutableMap()
      current.remove(songId)
      preferences[ExportProgressKey] = current.map { "${it.key}:${it.value}" }.joinToString(",")
    }
  }

  companion object {
    private const val PARALLEL_PARTS = 4
    private const val DOWNLOAD_BUFFER_SIZE = 128 * 1024
    private const val CHANNEL_ID = "audio_export_channel"
    private const val NOTIFICATION_ID = 2001
    private const val EXTRA_SONG_ID = "extra_song_id"
    private const val EXTRA_SONG_TITLE = "extra_song_title"
    private const val EXTRA_SONG_ARTIST = "extra_song_artist"
    private const val EXTRA_SONG_ALBUM = "extra_song_album"
    private const val EXTRA_ARTWORK_URL = "extra_artwork_url"
    private const val EXTRA_TARGET_DIRECTORY_URI = "extra_target_directory_uri"

    fun start(
      context: Context,
      songId: String,
      songTitle: String,
      songArtist: String,
      songAlbum: String,
      artworkUrl: String,
      targetDirectoryUri: String,
    ) {
      val intent =
        Intent(context, AudioExportService::class.java).apply {
          putExtra(EXTRA_SONG_ID, songId)
          putExtra(EXTRA_SONG_TITLE, songTitle)
          putExtra(EXTRA_SONG_ARTIST, songArtist)
          putExtra(EXTRA_SONG_ALBUM, songAlbum)
          putExtra(EXTRA_ARTWORK_URL, artworkUrl)
          putExtra(EXTRA_TARGET_DIRECTORY_URI, targetDirectoryUri)
        }
      ContextCompat.startForegroundService(context, intent)
    }

    private fun sanitizeTitle(title: String): String {
      val invalid = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|', '\n', '\r', '\t')
      val clean = title.map { if (it in invalid) '_' else it }.joinToString("").trim().trim('.', ' ')
      return clean.ifBlank { "song_${System.currentTimeMillis()}" }
    }
    private fun buildFfmpegCommand(
      inputPath: String,
      outputPath: String,
      title: String,
      artist: String,
      album: String,
      year: Int?,
      coverPath: String?,
      quality: DownloadQuality,
      isWebm: Boolean,
    ): String {
      val escapedInput = inputPath.ffmpegEscape()
      val escapedOutput = outputPath.ffmpegEscape()
      val titleMeta = title.ffmpegEscape()
      val artistMeta = artist.ffmpegEscape()
      val albumMeta = album.ffmpegEscape()
      val yearMeta = year?.toString()?.ffmpegEscape()
      val dateFlags =
        if (yearMeta != null) " -metadata date='$yearMeta' -metadata year='$yearMeta'" else ""

      return when (quality) {
        DownloadQuality.MP3_320 -> {
          val codecFlags = "-c:a libmp3lame -b:a 320k -id3v2_version 3"
          if (coverPath != null) {
            val escapedCover = coverPath.ffmpegEscape()
            "-y -i '$escapedInput' -i '$escapedCover' -map 0:a -map 1:v -c:v mjpeg -disposition:v attached_pic $codecFlags -metadata title='$titleMeta' -metadata artist='$artistMeta' -metadata album='$albumMeta'$dateFlags -metadata:s:v title='Album cover' '$escapedOutput'"
          } else {
            "-y -i '$escapedInput' $codecFlags -metadata title='$titleMeta' -metadata artist='$artistMeta' -metadata album='$albumMeta'$dateFlags '$escapedOutput'"
          }
        }
        DownloadQuality.LOSSLESS_FLAC -> {
          val flacCodec = "-c:a flac -compression_level 8"
          if (coverPath != null) {
            val escapedCover = coverPath.ffmpegEscape()
            "-y -i '$escapedInput' -i '$escapedCover' -map 0:a -map 1:v -c:v mjpeg -disposition:v attached_pic $flacCodec -metadata title='$titleMeta' -metadata artist='$artistMeta' -metadata album='$albumMeta'$dateFlags '$escapedOutput'"
          } else {
            "-y -i '$escapedInput' $flacCodec -metadata title='$titleMeta' -metadata artist='$artistMeta' -metadata album='$albumMeta'$dateFlags '$escapedOutput'"
          }
        }
        DownloadQuality.HIGH_AAC -> {
          if (!isWebm) {
            "-y -i '$escapedInput' -c:a copy -metadata title='$titleMeta' -metadata artist='$artistMeta' -metadata album='$albumMeta'$dateFlags '$escapedOutput'"
          } else {
            "-y -i '$escapedInput' -c:a aac -b:a 256k -metadata title='$titleMeta' -metadata artist='$artistMeta' -metadata album='$albumMeta'$dateFlags '$escapedOutput'"
          }
        }
        else -> {
          "-y -i '$escapedInput' -c:a copy -metadata title='$titleMeta' -metadata artist='$artistMeta' -metadata album='$albumMeta'$dateFlags '$escapedOutput'"
        }
      }
    }

    private fun String.ffmpegEscape(): String = replace("'", "'\\''")
  }
}
