package com.fenrir.her.playback

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.scheduler.Requirements
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.music.innertube.YouTube
import com.music.innertube.models.IpVersion
import dagger.hilt.android.qualifiers.ApplicationContext
import com.fenrir.her.constants.AudioQuality
import com.fenrir.her.constants.DownloadOnWifiOnlyKey
import com.fenrir.her.constants.IpVersionKey
import com.fenrir.her.db.MusicDatabase
import com.fenrir.her.db.entities.FormatEntity
import com.fenrir.her.db.entities.SongEntity
import com.fenrir.her.di.DownloadCache
import com.fenrir.her.di.PlayerCache
import com.fenrir.her.ui.utils.resize
import com.fenrir.her.utils.YTPlayerUtils
import com.fenrir.her.utils.dataStore
import com.fenrir.her.utils.enumPreference
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.time.LocalDateTime
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import okhttp3.OkHttpClient

@Singleton
class DownloadUtil
@Inject
constructor(
  @ApplicationContext context: Context,
  val database: MusicDatabase,
  val databaseProvider: DatabaseProvider,
  @DownloadCache val downloadCache: SimpleCache,
  @PlayerCache val playerCache: SimpleCache,
) {
  private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
  private val downloadQuality by
    enumPreference(
      context,
      com.fenrir.her.constants.DownloadQualityKey,
      com.fenrir.her.constants.DownloadQuality.YOUTUBE
    )
  private val ipVersion by enumPreference(context, IpVersionKey, IpVersion.AUTO)
  private val songUrlCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

  private val dataSourceFactory =
    ResolvingDataSource.Factory(
      ChunkingDataSourceFactory(
        // Read already-streamed bytes from playerCache instead of re-downloading them:
        // a song played before being downloaded would otherwise be fetched twice.
        CacheDataSource.Factory()
          .setCache(playerCache)
          .setUpstreamDataSourceFactory(
            OkHttpDataSource.Factory(
              OkHttpClient.Builder()
                .dns(
                  object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                      val addresses = Dns.SYSTEM.lookup(hostname)
                      return when (this@DownloadUtil.ipVersion) {
                        IpVersion.IPV4 ->
                          addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                        IpVersion.IPV6 ->
                          addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                        IpVersion.AUTO -> addresses
                      }
                    }
                  }
                )
                .proxy(YouTube.proxy)
                .proxyAuthenticator { _, response ->
                  YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder().header("Proxy-Authorization", auth).build()
                  } ?: response.request
                }
                .build(),
            )
          )
          .setCacheWriteDataSinkFactory(null)
          .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
      )
    ) { dataSpec ->
      val mediaId = dataSpec.key ?: error("No media id")

      songUrlCache["${mediaId}_${downloadQuality.name}"]
        ?.takeIf { it.second > System.currentTimeMillis() }
        ?.let {
          return@Factory dataSpec.withUri(it.first.toUri())
        }

      val targetAudioQuality = when (downloadQuality) {
        com.fenrir.her.constants.DownloadQuality.LOSSLESS_FLAC -> com.fenrir.her.constants.AudioQuality.HI_RES_LOSSLESS
        com.fenrir.her.constants.DownloadQuality.HIGH_AAC -> com.fenrir.her.constants.AudioQuality.HIGH
        com.fenrir.her.constants.DownloadQuality.OPUS -> com.fenrir.her.constants.AudioQuality.OPUS
        com.fenrir.her.constants.DownloadQuality.MP3_320 -> com.fenrir.her.constants.AudioQuality.HIGH
        com.fenrir.her.constants.DownloadQuality.YOUTUBE -> com.fenrir.her.constants.AudioQuality.AUTO
      }

      val playbackData =
        runBlocking(Dispatchers.IO) {
            YTPlayerUtils.playerResponseForPlayback(
              videoId = mediaId,
              audioQuality = targetAudioQuality,
              connectivityManager = connectivityManager
            )
          }
          .getOrElse { e -> throw java.io.IOException("Failed to resolve stream: ${e.message}", e) }
      val format = playbackData.format

      database.query {
        upsert(
          FormatEntity(
            id = mediaId,
            itag = format.itag,
            mimeType = format.mimeType.split(";")[0],
            codecs =
              format.mimeType.split("codecs=").getOrNull(1)?.removeSurrounding("\"") ?: "opus",
            bitrate = format.bitrate,
            sampleRate = format.audioSampleRate,
            contentLength = format.contentLength ?: 0L,
            loudnessDb = playbackData.audioConfig?.loudnessDb,
            perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb,
            playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
          ),
        )

        val now = LocalDateTime.now()
        val existing = getSongByIdBlocking(mediaId)?.song

        val updatedSong =
          if (existing != null) {
            existing.copy(
              dateDownload = existing.dateDownload ?: now,
              thumbnailUrl =
                existing.thumbnailUrl
                  ?: playbackData.videoDetails
                    ?.thumbnail
                    ?.thumbnails
                    ?.lastOrNull()
                    ?.url
                    ?.resize(1200, 1200)
            )
          } else {
            SongEntity(
              id = mediaId,
              title = playbackData.videoDetails?.title ?: "Unknown",
              duration = playbackData.videoDetails?.lengthSeconds?.toIntOrNull() ?: 0,
              thumbnailUrl =
                playbackData.videoDetails
                  ?.thumbnail
                  ?.thumbnails
                  ?.lastOrNull()
                  ?.url
                  ?.resize(1200, 1200),
              dateDownload = now,
              isDownloaded = false
            )
          }

        upsert(updatedSong)

        updatedSong.thumbnailUrl?.let { url ->
          val request =
            ImageRequest.Builder(context)
              .data(url)
              .memoryCachePolicy(CachePolicy.ENABLED)
              .diskCachePolicy(CachePolicy.ENABLED)
              .build()
          SingletonImageLoader.get(context).enqueue(request)
        }
      }

      val streamUrl = playbackData.streamUrl

      val expiresAt = System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L) - 60_000L
      songUrlCache["${mediaId}_${downloadQuality.name}"] = streamUrl to expiresAt
      dataSpec.withUri(streamUrl.toUri())
    }

  val downloadNotificationHelper =
    DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

  @OptIn(DelicateCoroutinesApi::class)
  val downloadManager: DownloadManager =
    DownloadManager(
        context,
        databaseProvider,
        downloadCache,
        dataSourceFactory,
        Executors.newFixedThreadPool(3)
      )
      .apply {
        maxParallelDownloads = 3
        addListener(
          object : DownloadManager.Listener {
            override fun onDownloadChanged(
              downloadManager: DownloadManager,
              download: Download,
              finalException: Exception?,
            ) {
              downloads.update { map ->
                map.toMutableMap().apply { set(download.request.id, download) }
              }

              scope.launch {
                when (download.state) {
                  Download.STATE_COMPLETED -> {
                    database.updateDownloadedInfo(download.request.id, true, LocalDateTime.now())
                  }
                  Download.STATE_FAILED,
                  Download.STATE_STOPPED,
                  Download.STATE_REMOVING -> {
                    database.updateDownloadedInfo(download.request.id, false, null)
                  }
                  else -> {}
                }
              }
            }
          }
        )
      }

  init {
    val result = mutableMapOf<String, Download>()
    downloadManager.downloadIndex.getDownloads().use { cursor ->
      while (cursor.moveToNext()) {
        result[cursor.download.request.id] = cursor.download
      }
    }
    downloads.value = result

    val initialWifiOnly = runBlocking {
      context.dataStore.data.firstOrNull()?.get(DownloadOnWifiOnlyKey) ?: false
    }
    downloadManager.requirements =
      Requirements(if (initialWifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

    // Wi-Fi-only downloads: DownloadManager pauses queued downloads whenever the
    // active requirements aren't met, so flipping this pref mid-download stops it
    // on mobile data without losing progress.
    scope.launch {
      context.dataStore.data
        .map { it[DownloadOnWifiOnlyKey] ?: false }
        .distinctUntilChanged()
        .collectLatest { wifiOnly ->
          downloadManager.requirements =
            Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)
        }
    }
  }

  fun getDownload(songId: String): Flow<Download?> = downloads.map { it[songId] }

  fun release() {
    scope.cancel()
  }
}
