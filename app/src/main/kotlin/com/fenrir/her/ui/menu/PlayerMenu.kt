package com.fenrir.her.ui.menu

import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.fenrir.her.LocalDatabase
import com.fenrir.her.LocalDownloadUtil
import com.fenrir.her.LocalListenTogetherManager
import com.fenrir.her.LocalPlayerConnection
import com.fenrir.her.R
import com.fenrir.her.constants.EnableExportAsMp3Key
import com.fenrir.her.constants.ExportDirectoryUriKey
import com.fenrir.her.constants.ExportedSongIdsKey
import com.fenrir.her.constants.ExportingSongIdsKey
import com.fenrir.her.constants.ListItemHeight
import com.fenrir.her.constants.ShowLyricsOnPlayerKey
import com.fenrir.her.listentogether.ConnectionState
import com.fenrir.her.listentogether.ListenTogetherEvent
import com.fenrir.her.models.MediaMetadata
import com.fenrir.her.playback.ExoDownloadService
import com.fenrir.her.ui.component.BottomSheetState
import com.fenrir.her.ui.component.ListDialog
import com.fenrir.her.ui.component.Material3MenuGroup
import com.fenrir.her.ui.component.Material3MenuItemData
import com.fenrir.her.ui.component.NewAction
import com.fenrir.her.ui.component.NewActionGrid
import com.fenrir.her.utils.rememberPreference
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PlayerMenu(
  mediaMetadata: MediaMetadata?,
  navController: NavController,
  playerBottomSheetState: BottomSheetState,
  isQueueTrigger: Boolean? = false,
  onShowDetailsDialog: () -> Unit,
  onDismiss: () -> Unit,
) {
  mediaMetadata ?: return
  val context = LocalContext.current
  val database = LocalDatabase.current
  val playerConnection = LocalPlayerConnection.current ?: return

  val audioManager = remember {
    context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
  }
  var systemVolume by remember {
    androidx.compose.runtime.mutableFloatStateOf(
      audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
    )
  }
  var maxSystemVolume by remember {
    androidx.compose.runtime.mutableFloatStateOf(
      audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
    )
  }

  androidx.compose.runtime.DisposableEffect(context) {
    val volumeChangeReceiver =
      object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: android.content.Intent) {
          if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
            val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
            if (streamType == android.media.AudioManager.STREAM_MUSIC) {
              systemVolume =
                audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
              maxSystemVolume =
                audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).toFloat()
            }
          }
        }
      }
    context.registerReceiver(
      volumeChangeReceiver,
      android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
    )
    onDispose { context.unregisterReceiver(volumeChangeReceiver) }
  }

  val castHandler =
    remember(playerConnection) {
      try {
        playerConnection.service.castConnectionHandler
      } catch (e: Exception) {
        null
      }
    }
  val isCasting by castHandler?.isCasting?.collectAsState() ?: remember { mutableStateOf(false) }
  val castVolume by
    castHandler?.castVolume?.collectAsState() ?: remember { mutableFloatStateOf(1f) }
  val castDeviceName by
    castHandler?.castDeviceName?.collectAsState() ?: remember { mutableStateOf<String?>(null) }

  val librarySong by database.song(mediaMetadata.id).collectAsState(initial = null)
  val coroutineScope = rememberCoroutineScope()

  val download by
    LocalDownloadUtil.current.getDownload(mediaMetadata.id).collectAsState(initial = null)

  val artists = remember(mediaMetadata.artists) { mediaMetadata.artists.filter { it.id != null } }

  var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }

  val (enableExportAsMp3) = rememberPreference(key = EnableExportAsMp3Key, defaultValue = true)
  val (exportDirectoryUri) = rememberPreference(key = ExportDirectoryUriKey, defaultValue = "")
  val (exportingSongIds) = rememberPreference(key = ExportingSongIdsKey, defaultValue = "")
  val (exportedSongIds) = rememberPreference(key = ExportedSongIdsKey, defaultValue = "")
  val (showLyricsOnPlayer, onShowLyricsOnPlayerChange) =
    rememberPreference(key = ShowLyricsOnPlayerKey, defaultValue = false)

  val (exportProgressString) =
    rememberPreference(key = com.fenrir.her.constants.ExportProgressKey, defaultValue = "")
  val exportProgress =
    remember(exportProgressString, mediaMetadata.id) {
      exportProgressString
        .split(',')
        .find { it.startsWith("${mediaMetadata.id}:") }
        ?.split(':')
        ?.getOrNull(1)
        ?.toIntOrNull() ?: 0
    }

  val isExporting =
    remember(exportingSongIds, mediaMetadata.id) {
      exportingSongIds.split(",").contains(mediaMetadata.id)
    }
  val isExported =
    remember(exportedSongIds, mediaMetadata.id) {
      exportedSongIds.split(",").contains(mediaMetadata.id)
    }

  var showReExportDialog by
    androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
  if (showReExportDialog) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { showReExportDialog = false },
      title = { androidx.compose.material3.Text("Re-export") },
      text = { androidx.compose.material3.Text("Wanna re-export it again?") },
      confirmButton = {
        androidx.compose.material3.Button(
          onClick = {
            showReExportDialog = false
            if (exportDirectoryUri.isBlank()) {
              android.widget.Toast.makeText(
                  context,
                  context.getString(R.string.export_directory_not_set),
                  android.widget.Toast.LENGTH_SHORT
                )
                .show()
              onDismiss()
            } else {
              onDismiss()
              com.fenrir.her.playback.AudioExportService.start(
                context = context,
                songId = mediaMetadata.id,
                songTitle = mediaMetadata.title,
                songArtist = artists.joinToString(", ") { it.name },
                songAlbum = mediaMetadata.album?.title ?: "",
                artworkUrl = mediaMetadata.thumbnailUrl ?: "",
                targetDirectoryUri = exportDirectoryUri
              )
            }
          }
        ) {
          androidx.compose.material3.Text("Yes")
        }
      },
      dismissButton = {
        androidx.compose.material3.OutlinedButton(onClick = { showReExportDialog = false }) {
          androidx.compose.material3.Text("No")
        }
      }
    )
  }

  var showListenTogetherDialog by rememberSaveable { mutableStateOf(false) }

  val listenTogetherManager = LocalListenTogetherManager.current
  val ringtoneViewModel = com.fenrir.her.LocalRingtoneViewModel.current
  val isListenTogetherGuest by
    listenTogetherManager?.guestPlaybackRestricted?.collectAsState(initial = false)
      ?: remember { mutableStateOf(false) }
  val pendingSuggestions by
    listenTogetherManager?.pendingSuggestions?.collectAsState(initial = emptyList())
      ?: remember { mutableStateOf(emptyList()) }

  AddToPlaylistDialog(
    isVisible = showChoosePlaylistDialog,
    onGetSong = { playlist ->
      database.transaction { insert(mediaMetadata) }
      coroutineScope.launch(Dispatchers.IO) {
        playlist.playlist.browseId?.let { YouTube.addToPlaylist(it, mediaMetadata.id) }
      }
      listOf(mediaMetadata.id)
    },
    onDismiss = { showChoosePlaylistDialog = false }
  )

  ListenTogetherDialog(
    visible = showListenTogetherDialog,
    mediaMetadata = mediaMetadata,
    onDismiss = { showListenTogetherDialog = false }
  )

  var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }

  if (showSelectArtistDialog) {
    ListDialog(
      onDismiss = { showSelectArtistDialog = false },
    ) {
      items(artists) { artist ->
        Box(
          contentAlignment = Alignment.CenterStart,
          modifier =
            Modifier.fillParentMaxWidth()
              .height(ListItemHeight)
              .clickable {
                navController.navigate("artist/${artist.id}")
                showSelectArtistDialog = false
                playerBottomSheetState.collapseSoft()
                onDismiss()
              }
              .padding(horizontal = 24.dp),
        ) {
          Text(
            text = artist.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }

  var showPitchTempoDialog by rememberSaveable { mutableStateOf(false) }

  if (showPitchTempoDialog) {
    TempoPitchDialog(
      onDismiss = { showPitchTempoDialog = false },
    )
  }

  if (isQueueTrigger != true) {
    if (isCasting && castDeviceName != null) {
      Column(
        modifier =
          Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 6.dp),
      ) {
        Row(
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
          Icon(
            painter = painterResource(R.drawable.cast),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = stringResource(R.string.casting_to, castDeviceName ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }
    }
  }

  val configuration = LocalConfiguration.current
  val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

  LazyColumn(
    contentPadding =
      PaddingValues(
        start = 0.dp,
        top = 0.dp,
        end = 0.dp,
        bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
      ),
  ) {
    item {
      val startingRadioText = stringResource(R.string.starting_radio)
      NewActionGrid(
        actions =
          listOfNotNull(
            if (!isListenTogetherGuest) {
              NewAction(
                icon = {
                  Icon(
                    painter = painterResource(R.drawable.radio),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                },
                text = stringResource(R.string.start_an_radio),
                onClick = {
                  Toast.makeText(context, startingRadioText, Toast.LENGTH_SHORT).show()
                  playerConnection.startRadioSeamlessly()
                  onDismiss()
                }
              )
            } else null,
            NewAction(
              icon = {
                Icon(
                  painter = painterResource(R.drawable.playlist_add),
                  contentDescription = null,
                  modifier = Modifier.size(32.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              },
              text = stringResource(R.string.add_to_an_playlist),
              onClick = { showChoosePlaylistDialog = true }
            ),
            NewAction(
              icon = {
                Icon(
                  painter = painterResource(R.drawable.share),
                  contentDescription = null,
                  modifier = Modifier.size(32.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              },
              text = stringResource(R.string.share),
              onClick = {
                val intent =
                  android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(
                      android.content.Intent.EXTRA_TEXT,
                      "https://share.echomusic.fun/watch?v=${mediaMetadata.id}"
                    )
                  }
                context.startActivity(android.content.Intent.createChooser(intent, null))
                onDismiss()
              }
            )
          ),
        columns = if (isListenTogetherGuest) 2 else 3,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
      )
    }

    item {
      Material3MenuGroup(
        items =
          buildList {
            if (artists.isNotEmpty()) {
              add(
                Material3MenuItemData(
                  title = { Text(text = stringResource(R.string.view_artist)) },
                  description = {
                    Text(
                      text = mediaMetadata.artists.joinToString { it.name },
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  },
                  icon = {
                    Icon(
                      painter = painterResource(R.drawable.artist),
                      contentDescription = null,
                      modifier = Modifier.size(24.dp)
                    )
                  },
                  onClick = {
                    if (mediaMetadata.artists.size == 1) {
                      navController.navigate("artist/${mediaMetadata.artists[0].id}")
                      playerBottomSheetState.collapseSoft()
                      onDismiss()
                    } else {
                      showSelectArtistDialog = true
                    }
                  }
                )
              )
            }
            if (mediaMetadata.album != null) {
              add(
                Material3MenuItemData(
                  title = { Text(text = stringResource(R.string.view_album)) },
                  description = {
                    Text(
                      text = mediaMetadata.album!!.title,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  },
                  icon = {
                    Icon(
                      painter = painterResource(R.drawable.album),
                      contentDescription = null,
                      modifier = Modifier.size(24.dp)
                    )
                  },
                  onClick = {
                    navController.navigate("album/${mediaMetadata.album!!.id}")
                    playerBottomSheetState.collapseSoft()
                    onDismiss()
                  }
                )
              )
            }

            val isInLibrary = librarySong?.song?.inLibrary != null
            add(
              Material3MenuItemData(
                title = {
                  Text(
                    text =
                      stringResource(
                        if (isInLibrary) R.string.remove_from_library else R.string.add_to_library
                      )
                  )
                },
                icon = {
                  Icon(
                    painter =
                      painterResource(
                        if (isInLibrary) R.drawable.library_add_check else R.drawable.library_add
                      ),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                  )
                },
                onClick = {
                  playerConnection.toggleLibrary()
                  onDismiss()
                }
              )
            )
            add(
              Material3MenuItemData(
                customComposable = { com.fenrir.her.ui.component.CastButton(asMenuItem = true) }
              )
            )

            add(
              Material3MenuItemData(
                title = { Text(text = "Ambient Mode") },
                icon = {
                  Icon(
                    painter = painterResource(R.drawable.fullscreen),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                  )
                },
                onClick = {
                  navController.navigate("ambient_mode")
                  playerBottomSheetState.collapseSoft()
                  onDismiss()
                }
              )
            )
            add(
              Material3MenuItemData(
                title = { Text(text = if (showLyricsOnPlayer) "Hide Lyrics" else "Show Lyrics") },
                icon = {
                  Icon(
                    painter = painterResource(R.drawable.lyrics),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                  )
                },
                onClick = {
                  onShowLyricsOnPlayerChange(!showLyricsOnPlayer)
                  onDismiss()
                }
              )
            )
          }
      )
    }

    item { Spacer(modifier = Modifier.height(12.dp)) }

    item {
      Material3MenuGroup(
        items =
          listOf(
            when (download?.state) {
              Download.STATE_COMPLETED -> {
                Material3MenuItemData(
                  title = { Text(text = stringResource(R.string.remove_download)) },
                  icon = {
                    Icon(
                      painter = painterResource(R.drawable.offline),
                      contentDescription = null,
                      modifier = Modifier.size(24.dp)
                    )
                  },
                  onClick = {
                    DownloadService.sendRemoveDownload(
                      context,
                      ExoDownloadService::class.java,
                      mediaMetadata.id,
                      false,
                    )
                  }
                )
              }
              Download.STATE_QUEUED,
              Download.STATE_DOWNLOADING -> {
                Material3MenuItemData(
                  title = { Text(text = stringResource(R.string.downloading)) },
                  icon = {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                  },
                  onClick = {
                    DownloadService.sendRemoveDownload(
                      context,
                      ExoDownloadService::class.java,
                      mediaMetadata.id,
                      false,
                    )
                  }
                )
              }
              else -> {
                Material3MenuItemData(
                  title = { Text(text = stringResource(R.string.action_download)) },
                  icon = {
                    Icon(
                      painter = painterResource(R.drawable.download),
                      contentDescription = null,
                      modifier = Modifier.size(24.dp)
                    )
                  },
                  onClick = {
                    database.transaction { insert(mediaMetadata) }
                    val downloadRequest =
                      DownloadRequest.Builder(mediaMetadata.id, mediaMetadata.id.toUri())
                        .setCustomCacheKey(mediaMetadata.id)
                        .setData(mediaMetadata.title.toByteArray())
                        .build()
                    DownloadService.sendAddDownload(
                      context,
                      ExoDownloadService::class.java,
                      downloadRequest,
                      false,
                    )
                  }
                )
              }
            }
          )
      )
    }

    if (enableExportAsMp3) {
      item { Spacer(modifier = Modifier.height(12.dp)) }
      item {
        Material3MenuGroup(
          items =
            listOf(
              when {
                isExported ->
                  Material3MenuItemData(
                    title = { Text(text = stringResource(R.string.action_exported)) },
                    icon = {
                      Icon(
                        painter = painterResource(R.drawable.folder_managed),
                        contentDescription = null
                      )
                    },
                    onClick = { showReExportDialog = true }
                  )
                isExporting ->
                  Material3MenuItemData(
                    title = {
                      Text(text = "${stringResource(R.string.exporting)} $exportProgress%")
                    },
                    icon = {
                      CircularProgressIndicator(
                        progress = { exportProgress / 100f },
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        trackColor =
                          androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                      )
                    },
                    onClick = {}
                  )
                else ->
                  Material3MenuItemData(
                    title = { Text(text = stringResource(R.string.action_export)) },
                    description = { Text(text = stringResource(R.string.export_desc)) },
                    icon = {
                      Icon(
                        painter = painterResource(R.drawable.file_export),
                        contentDescription = null,
                      )
                    },
                    onClick = {
                      val effectiveTarget = exportDirectoryUri.ifBlank {
                        val defaultMusic = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                        val herDir = java.io.File(defaultMusic, "Her")
                        if (!herDir.exists()) herDir.mkdirs()
                        android.net.Uri.fromFile(herDir).toString()
                      }
                      onDismiss()
                      com.fenrir.her.playback.AudioExportService.start(
                        context = context,
                        songId = mediaMetadata.id,
                        songTitle = mediaMetadata.title,
                        songArtist = artists.joinToString(", ") { it.name },
                        songAlbum = mediaMetadata.album?.title ?: "",
                        artworkUrl = mediaMetadata.thumbnailUrl ?: "",
                        targetDirectoryUri = effectiveTarget
                      )
                    }
                  )
              }
            )
        )
      }
    }

    item { Spacer(modifier = Modifier.height(12.dp)) }

    item {
      Material3MenuGroup(
        items =
          listOf(
            Material3MenuItemData(
              title = { Text(text = "Set as Ringtone") },
              icon = {
                Icon(
                  painter = painterResource(R.drawable.notification),
                  contentDescription = null,
                  modifier = Modifier.size(24.dp)
                )
              },
              onClick = {
                if (ringtoneViewModel.hasSettingsPermission(context)) {
                  ringtoneViewModel.showTrimmer(
                    mediaMetadata.id,
                    mediaMetadata.title,
                    mediaMetadata.artists.joinToString { it.name },
                    mediaMetadata.duration
                  )
                } else {
                  ringtoneViewModel.requestSettingsPermission(context)
                }
                onDismiss()
              }
            )
          )
      )
    }

    item { Spacer(modifier = Modifier.height(12.dp)) }

    item {
      Material3MenuGroup(
        items =
          buildList {
            add(
              Material3MenuItemData(
                title = { Text(text = stringResource(R.string.listen_together)) },
                icon = {
                  Box {
                    Icon(
                      painter = painterResource(R.drawable.group),
                      contentDescription = null,
                      modifier = Modifier.size(24.dp)
                    )
                    if (pendingSuggestions.isNotEmpty()) {
                      Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.offset(x = 8.dp, y = (-6).dp).align(Alignment.TopEnd)
                      ) {
                        Text(
                          text = pendingSuggestions.size.toString(),
                          color = MaterialTheme.colorScheme.onPrimary,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                          style = MaterialTheme.typography.labelSmall
                        )
                      }
                    }
                  }
                },
                onClick = { showListenTogetherDialog = true }
              )
            )
            if (isListenTogetherGuest) {
              add(
                Material3MenuItemData(
                  title = { Text(text = stringResource(R.string.resync)) },
                  icon = {
                    Icon(
                      painter = painterResource(R.drawable.replay),
                      contentDescription = null,
                      modifier = Modifier.size(24.dp)
                    )
                  },
                  onClick = {
                    listenTogetherManager?.requestSync()
                    onDismiss()
                  }
                )
              )
            }
          }
      )
    }

    item { Spacer(modifier = Modifier.height(12.dp)) }

    item {
      Material3MenuGroup(
        items =
          buildList {
            add(
              Material3MenuItemData(
                title = { Text(text = stringResource(R.string.details)) },
                description = { Text(text = stringResource(R.string.details_desc)) },
                icon = {
                  Icon(
                    painter = painterResource(R.drawable.info),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                  )
                },
                onClick = {
                  onShowDetailsDialog()
                  onDismiss()
                }
              )
            )

            if (isQueueTrigger != true) {
              add(
                Material3MenuItemData(
                  title = { Text(text = stringResource(R.string.equalizer)) },
                  description = { Text(text = stringResource(R.string.equalizer_desc)) },
                  icon = {
                    Icon(
                      painter = painterResource(R.drawable.equalizer),
                      contentDescription = null,
                      modifier = Modifier.size(24.dp)
                    )
                  },
                  onClick = {
                    navController.navigate("equalizer")
                    onDismiss()
                  }
                )
              )
              add(
                Material3MenuItemData(
                  title = { Text(text = stringResource(R.string.advanced)) },
                  description = { Text(text = stringResource(R.string.advanced_desc)) },
                  icon = {
                    Icon(
                      painter = painterResource(R.drawable.tune),
                      contentDescription = null,
                      modifier = Modifier.size(24.dp)
                    )
                  },
                  onClick = { showPitchTempoDialog = true }
                )
              )
            }
          }
      )
    }
  }
}

@Composable
fun TempoPitchDialog(onDismiss: () -> Unit) {
  val playerConnection = LocalPlayerConnection.current ?: return
  var tempo by remember { mutableFloatStateOf(playerConnection.player.playbackParameters.speed) }
  var transposeValue by remember {
    mutableIntStateOf(round(12 * log2(playerConnection.player.playbackParameters.pitch)).toInt())
  }
  val updatePlaybackParameters = {
    playerConnection.player.playbackParameters =
      PlaybackParameters(tempo, 2f.pow(transposeValue.toFloat() / 12))
  }
  val listenTogetherManager = com.fenrir.her.LocalListenTogetherManager.current
  val isInRoom = listenTogetherManager?.isInRoom ?: false

  AlertDialog(
    properties = DialogProperties(usePlatformDefaultWidth = false),
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.tempo_and_pitch)) },
    dismissButton = {
      TextButton(
        onClick = {
          tempo = 1f
          transposeValue = 0
          updatePlaybackParameters()
        },
      ) {
        Text(stringResource(R.string.reset))
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
      ) {
        Text(stringResource(android.R.string.ok))
      }
    },
    text = {
      Column {
        if (!isInRoom) {
          ValueAdjuster(
            icon = R.drawable.speed,
            currentValue = tempo,
            values = (0..35).map { round((0.25f + it * 0.05f) * 100) / 100 },
            onValueUpdate = {
              tempo = it
              updatePlaybackParameters()
            },
            valueText = { "x$it" },
            modifier = Modifier.padding(bottom = 12.dp),
          )
        }
        ValueAdjuster(
          icon = R.drawable.discover_tune,
          currentValue = transposeValue,
          values = (-12..12).toList(),
          onValueUpdate = {
            transposeValue = it
            updatePlaybackParameters()
          },
          valueText = { "${if (it > 0) "+" else ""}$it" },
        )
      }
    },
  )
}

@Composable
fun <T> ValueAdjuster(
  @DrawableRes icon: Int,
  currentValue: T,
  values: List<T>,
  onValueUpdate: (T) -> Unit,
  valueText: (T) -> String,
  modifier: Modifier = Modifier,
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(24.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier,
  ) {
    Icon(
      painter = painterResource(icon),
      contentDescription = null,
      modifier = Modifier.size(28.dp),
    )

    IconButton(
      enabled = currentValue != values.first(),
      onClick = { onValueUpdate(values[values.indexOf(currentValue) - 1]) },
    ) {
      Icon(
        painter = painterResource(R.drawable.remove),
        contentDescription = null,
      )
    }

    Text(
      text = valueText(currentValue),
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier.width(80.dp),
    )

    IconButton(
      enabled = currentValue != values.last(),
      onClick = { onValueUpdate(values[values.indexOf(currentValue) + 1]) },
    ) {
      Icon(
        painter = painterResource(R.drawable.add),
        contentDescription = null,
      )
    }
  }
}

@Composable
fun ListenTogetherDialog(visible: Boolean, mediaMetadata: MediaMetadata?, onDismiss: () -> Unit) {
  if (!visible) return

  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val playerConnection = LocalPlayerConnection.current

  val spaceId by rememberPreference(com.fenrir.her.constants.CoupleSpaceIdKey, defaultValue = "")
  val partnerName by rememberPreference(com.fenrir.her.constants.CouplePartnerNameKey, defaultValue = "")
  val myRolePref by rememberPreference(com.fenrir.her.constants.ActiveCoupleProfileKey, defaultValue = "")
  val myEmailPref by rememberPreference(com.fenrir.her.constants.AccountEmailKey, defaultValue = "")
  val customUrl by rememberPreference(com.fenrir.her.constants.CoupleFirebaseUrlKey, defaultValue = "")
  val myRole = remember(myEmailPref, myRolePref) { com.fenrir.her.auth.CoupleAuthGuard.resolveRole(myEmailPref, myRolePref) }

  val isLiveSyncing by com.fenrir.her.couple.CouplePlaybackSyncManager.isLiveSyncing.collectAsState()
  val partnerPlayback by com.fenrir.her.couple.CouplePlaybackSyncManager.partnerPlayback.collectAsState()
  val syncResponse by com.fenrir.her.couple.FirebaseCoupleSync.syncResponseFlow.collectAsState()

  var inviteSent by remember { mutableStateOf(false) }

  LaunchedEffect(syncResponse) {
    syncResponse?.let { resp ->
      if (inviteSent) {
        if (resp.accepted) {
          Toast.makeText(context, "${resp.responderName.ifBlank { "Partner" }} accepted! Listening together 💕", Toast.LENGTH_SHORT).show()
          inviteSent = false
          onDismiss()
        } else {
          Toast.makeText(context, "${resp.responderName.ifBlank { "Partner" }} declined the sync request.", Toast.LENGTH_SHORT).show()
          inviteSent = false
        }
      }
    }
  }

  LaunchedEffect(spaceId) {
    if (spaceId.isNotBlank()) {
      com.fenrir.her.couple.FirebaseCoupleSync.subscribeSpace(spaceId)
      com.fenrir.her.couple.CouplePlaybackSyncManager.fetchPartnerPreview(scope, spaceId, myRole, customUrl.ifBlank { null })
    }
  }

  val displayPartner = partnerName.ifBlank { if (myRole == com.fenrir.her.auth.CoupleAuthGuard.CoupleRole.HER) "Him" else "Her" }

  ListDialog(onDismiss = onDismiss) {
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(
          modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color(0xFFFF4081).copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            painter = painterResource(R.drawable.favorite),
            contentDescription = null,
            tint = Color(0xFFFF4081),
            modifier = Modifier.size(28.dp)
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "Listen Together 💕",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (spaceId.isBlank()) {
          Text(
            text = "Connect with your partner in Couple Space to listen synchronously together.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(20.dp))
          Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
          ) {
            Text("OK", fontWeight = FontWeight.Bold)
          }
        } else if (isLiveSyncing) {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF4CAF50).copy(alpha = 0.15f),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF4CAF50))
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Live Synced with $displayPartner",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFF2E7D32)
                )
                Text(
                  text = "Audio playback and seek are synchronizing in real-time.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          Button(
            onClick = {
              com.fenrir.her.couple.CouplePlaybackSyncManager.disconnectSync()
              Toast.makeText(context, "Disjoined from sync", Toast.LENGTH_SHORT).show()
              onDismiss()
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2185B)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Disjoin / Stop Sync 💕", fontWeight = FontWeight.Bold, color = Color.White)
          }
        } else {
          Text(
            text = "Direct sync with $displayPartner without rooms or codes. One tap to sync up!",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          val live = partnerPlayback
          if (live != null && live.songId.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                  contentAlignment = Alignment.Center
                ) {
                  if (!live.thumbnailUrl.isNullOrBlank()) {
                    coil3.compose.AsyncImage(
                      model = live.thumbnailUrl,
                      contentDescription = null,
                      modifier = Modifier.fillMaxSize(),
                      contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                  } else {
                    Icon(
                      painter = painterResource(R.drawable.music_note),
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "$displayPartner is listening to:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = live.title.ifBlank { "Track" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = live.artist.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
              onClick = {
                com.fenrir.her.couple.CouplePlaybackSyncManager.startLiveSyncFollow(
                  scope = scope,
                  spaceId = spaceId,
                  myRole = myRole,
                  playerConnection = playerConnection,
                  customUrl = customUrl.ifBlank { null }
                )
                Toast.makeText(context, "Synced to $displayPartner's playback 💕", Toast.LENGTH_SHORT).show()
                onDismiss()
              },
              shape = RoundedCornerShape(16.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(
                painter = painterResource(R.drawable.connect_people),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color.White
              )
              Spacer(Modifier.width(8.dp))
              Text("Tune into $displayPartner's Song ▶", fontWeight = FontWeight.Bold, color = Color.White)
            }
          }

          if (mediaMetadata != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
              onClick = {
                val req = com.fenrir.her.couple.CoupleSyncRequest(
                  requestId = System.currentTimeMillis().toString(),
                  requesterRole = myRole.name,
                  requesterName = if (myRole == com.fenrir.her.auth.CoupleAuthGuard.CoupleRole.HER) "Her" else "Him",
                  songId = mediaMetadata.id,
                  title = mediaMetadata.title,
                  artist = mediaMetadata.artists.joinToString { it.name },
                  thumbnailUrl = mediaMetadata.thumbnailUrl,
                  positionMs = playerConnection?.player?.currentPosition?.coerceAtLeast(0L) ?: 0L,
                  isPlaying = playerConnection?.player?.isPlaying ?: true,
                  timestamp = System.currentTimeMillis()
                )
                scope.launch {
                  com.fenrir.her.couple.FirebaseCoupleSync.sendSyncRequest(spaceId, req)
                  com.fenrir.her.couple.CouplePlaybackSyncManager.startBroadcaster(
                    scope = scope,
                    spaceId = spaceId,
                    role = myRole,
                    myName = req.requesterName,
                    playerConnection = playerConnection,
                    customUrl = customUrl.ifBlank { null }
                  )
                }
                inviteSent = true
                Toast.makeText(context, "Invite sent to $displayPartner 💕", Toast.LENGTH_SHORT).show()
              },
              shape = RoundedCornerShape(16.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimary
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = if (inviteSent) "Waiting for $displayPartner..." else "Invite $displayPartner to Sync 💕",
                fontWeight = FontWeight.Bold
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

