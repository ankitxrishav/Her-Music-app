package com.fenrir.her.ui.screens.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.fenrir.her.LocalPlayerAwareWindowInsets
import com.fenrir.her.LocalPlayerConnection
import com.fenrir.her.R
import com.fenrir.her.constants.DownloadStructure
import com.fenrir.her.constants.DownloadQuality
import com.fenrir.her.extensions.toMediaItem
import com.fenrir.her.playback.queues.ListQueue
import java.io.File

enum class DownloadTab(val label: String) {
  SONGS("Songs"),
  ARTISTS("Artists"),
  ALBUMS("Albums"),
}

enum class SongSortOption(val label: String) {
  RECENT("Recently Added"),
  OLDEST("Oldest First"),
  TITLE_AZ("Title (A-Z)"),
  ARTIST_AZ("Artist (A-Z)"),
  SIZE_DESC("File Size"),
}

enum class ArtistSortOption(val label: String) {
  NAME_AZ("Name (A-Z)"),
  MOST_SONGS("Most Songs"),
  STORAGE_SIZE("Storage Size"),
}

enum class AlbumSortOption(val label: String) {
  TITLE_AZ("Title (A-Z)"),
  ARTIST_AZ("Artist (A-Z)"),
  MOST_TRACKS("Most Tracks"),
  RECENT("Recently Added"),
}

sealed interface DownloadSubView {
  data object Root : DownloadSubView
  data class ArtistDetail(val artistName: String) : DownloadSubView
  data class AlbumDetail(val albumTitle: String, val artistName: String) : DownloadSubView
}

private fun formatBytes(bytes: Long): String {
  if (bytes <= 0) return "0 MB"
  val mb = bytes.toDouble() / (1024 * 1024)
  return if (mb >= 1000) "%.1f GB".format(mb / 1024) else "%.1f MB".format(mb)
}

private fun formatDuration(ms: Long): String {
  if (ms <= 0L) return "0:00"
  val totalSeconds = ms / 1000
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return "%d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
  navController: NavController,
  viewModel: DownloadsViewModel = hiltViewModel(),
) {
  val tracks by viewModel.downloadedTracks.collectAsState()
  val artists by viewModel.downloadedArtists.collectAsState()
  val albums by viewModel.downloadedAlbums.collectAsState()
  val totalBytes by viewModel.totalBytes.collectAsState()
  val downloadFolder by viewModel.downloadFolder.collectAsState()
  val downloadStructure by viewModel.downloadStructure.collectAsState()
  val downloadLyrics by viewModel.downloadLyrics.collectAsState()
  val downloadQuality by viewModel.downloadQuality.collectAsState()

  val playerConnection = LocalPlayerConnection.current

  var selectedTab by remember { mutableStateOf(DownloadTab.SONGS) }
  var subViewStack by remember { mutableStateOf<List<DownloadSubView>>(listOf(DownloadSubView.Root)) }
  val currentSubView = subViewStack.last()

  var searchQuery by remember { mutableStateOf("") }
  var songSort by remember { mutableStateOf(SongSortOption.RECENT) }
  var artistSort by remember { mutableStateOf(ArtistSortOption.NAME_AZ) }
  var albumSort by remember { mutableStateOf(AlbumSortOption.TITLE_AZ) }
  var filterLosslessOnly by remember { mutableStateOf(false) }
  var filterLyricsOnly by remember { mutableStateOf(false) }

  var showOptionsMenu by remember { mutableStateOf(false) }
  var showFolderDialog by remember { mutableStateOf(false) }
  var showStructureDialog by remember { mutableStateOf(false) }
  var showQualityDialog by remember { mutableStateOf(false) }
  var showClearAllConfirm by remember { mutableStateOf(false) }
  var trackToDelete by remember { mutableStateOf<DownloadedTrack?>(null) }

  fun navigateTo(subView: DownloadSubView) {
    subViewStack = subViewStack + subView
  }

  fun popSubView(): Boolean {
    return if (subViewStack.size > 1) {
      subViewStack = subViewStack.dropLast(1)
      true
    } else {
      false
    }
  }

  BackHandler(enabled = subViewStack.size > 1) {
    popSubView()
  }

  fun playDownloadedTracks(items: List<DownloadedTrack>, startIndex: Int = 0, shuffled: Boolean = false) {
    if (items.isEmpty() || playerConnection == null) return
    val mediaItems = items.map { track ->
      track.originalSong?.toMediaItem() ?: androidx.media3.common.MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(if (track.filePath.startsWith("/")) android.net.Uri.fromFile(File(track.filePath)) else android.net.Uri.parse(track.filePath))
        .setMediaMetadata(
          androidx.media3.common.MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artworkUrl?.let { android.net.Uri.parse(it) })
            .setIsPlayable(true)
            .build()
        )
        .build()
    }
    val finalItems = if (shuffled) mediaItems.shuffled() else mediaItems
    playerConnection.playQueue(
      ListQueue(
        title = "Downloads",
        items = finalItems,
        startIndex = if (shuffled) 0 else startIndex,
      )
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          when (currentSubView) {
            is DownloadSubView.Root -> {
              Text(
                text = "Music & Downloads",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
              )
            }
            is DownloadSubView.ArtistDetail -> {
              Text(
                text = currentSubView.artistName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            is DownloadSubView.AlbumDetail -> {
              Text(
                text = currentSubView.albumTitle,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        },
        navigationIcon = {
          if (subViewStack.size > 1) {
            IconButton(onClick = { popSubView() }) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          }
        },
        actions = {
          Box {
            IconButton(onClick = { showOptionsMenu = true }) {
              Icon(Icons.Filled.MoreVert, contentDescription = "Options")
            }
            DropdownMenu(
              expanded = showOptionsMenu,
              onDismissRequest = { showOptionsMenu = false },
            ) {
              DropdownMenuItem(
                text = {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Text("Download Lyrics (.lrc)")
                    Switch(
                      checked = downloadLyrics,
                      onCheckedChange = { viewModel.setDownloadLyrics(it) },
                    )
                  }
                },
                leadingIcon = { Icon(Icons.Filled.FormatQuote, contentDescription = null) },
                onClick = { viewModel.setDownloadLyrics(!downloadLyrics) },
              )
              DropdownMenuItem(
                text = {
                  Column {
                    Text("Download Format")
                    Text(
                      downloadQuality.name.replace("_", " "),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                },
                leadingIcon = { Icon(Icons.Filled.HighQuality, contentDescription = null) },
                onClick = {
                  showOptionsMenu = false
                  showQualityDialog = true
                },
              )
              DropdownMenuItem(
                text = {
                  Column {
                    Text("Download Folder")
                    Text(
                      "Music/$downloadFolder",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                },
                leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                onClick = {
                  showOptionsMenu = false
                  showFolderDialog = true
                },
              )
              DropdownMenuItem(
                text = {
                  Column {
                    Text("Folder Organization")
                    Text(
                      if (downloadStructure == DownloadStructure.FLAT) "Flat (Music/$downloadFolder/)" else "Artist / Album",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                onClick = {
                  showOptionsMenu = false
                  showStructureDialog = true
                },
              )
              DropdownMenuItem(
                text = { Text("Open in File Manager") },
                leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                onClick = {
                  showOptionsMenu = false
                  viewModel.openInFileManager()
                },
              )
              if (tracks.isNotEmpty()) {
                DropdownMenuItem(
                  text = { Text("Clear All Downloads", color = MaterialTheme.colorScheme.error) },
                  leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                  onClick = {
                    showOptionsMenu = false
                    showClearAllConfirm = true
                  },
                )
              }
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
          titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      when (currentSubView) {
        is DownloadSubView.Root -> {
          DownloadTabBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            songCount = tracks.size,
            artistCount = artists.size,
            albumCount = albums.size,
          )

          DownloadFilterControls(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            selectedTab = selectedTab,
            songSort = songSort,
            onSongSortChange = { songSort = it },
            artistSort = artistSort,
            onArtistSortChange = { artistSort = it },
            albumSort = albumSort,
            onAlbumSortChange = { albumSort = it },
            filterLossless = filterLosslessOnly,
            onToggleLossless = { filterLosslessOnly = !filterLosslessOnly },
            filterLyrics = filterLyricsOnly,
            onToggleLyrics = { filterLyricsOnly = !filterLyricsOnly },
          )

          when (selectedTab) {
            DownloadTab.SONGS -> {
              val filteredTracks = remember(tracks, searchQuery, songSort, filterLosslessOnly, filterLyricsOnly) {
                tracks
                  .filter {
                    if (searchQuery.isBlank()) true
                    else it.title.contains(searchQuery, ignoreCase = true) ||
                      it.artist.contains(searchQuery, ignoreCase = true) ||
                      it.album.contains(searchQuery, ignoreCase = true)
                  }
                  .filter { if (filterLosslessOnly) it.isLossless else true }
                  .filter { if (filterLyricsOnly) it.hasLyrics else true }
                  .let { list ->
                    when (songSort) {
                      SongSortOption.RECENT -> list.sortedByDescending { it.downloadedAtMillis }
                      SongSortOption.OLDEST -> list.sortedBy { it.downloadedAtMillis }
                      SongSortOption.TITLE_AZ -> list.sortedBy { it.title.lowercase() }
                      SongSortOption.ARTIST_AZ -> list.sortedBy { it.artist.lowercase() }
                      SongSortOption.SIZE_DESC -> list.sortedByDescending { it.fileSizeBytes }
                    }
                  }
              }

              DownloadedSongsList(
                tracks = filteredTracks,
                totalSizeText = formatBytes(totalBytes),
                downloadFolder = downloadFolder,
                downloadStructure = downloadStructure,
                onPlayTrack = { track ->
                  val index = filteredTracks.indexOf(track).coerceAtLeast(0)
                  playDownloadedTracks(filteredTracks, startIndex = index)
                },
                onPlayAll = { shuffled ->
                  playDownloadedTracks(filteredTracks, shuffled = shuffled)
                },
                onDeleteTrack = { trackToDelete = it },
                onSelectArtist = { navigateTo(DownloadSubView.ArtistDetail(it)) },
                onSelectAlbum = { alb, art -> navigateTo(DownloadSubView.AlbumDetail(alb, art)) },
              )
            }
            DownloadTab.ARTISTS -> {
              val filteredArtists = remember(artists, searchQuery, artistSort) {
                artists
                  .filter {
                    if (searchQuery.isBlank()) true
                    else it.name.contains(searchQuery, ignoreCase = true)
                  }
                  .let { list ->
                    when (artistSort) {
                      ArtistSortOption.NAME_AZ -> list.sortedBy { it.name.lowercase() }
                      ArtistSortOption.MOST_SONGS -> list.sortedByDescending { it.tracks.size }
                      ArtistSortOption.STORAGE_SIZE -> list.sortedByDescending { it.totalSizeBytes }
                    }
                  }
              }

              DownloadedArtistsList(
                artists = filteredArtists,
                onSelectArtist = { navigateTo(DownloadSubView.ArtistDetail(it.name)) },
                onPlayArtist = { artist -> playDownloadedTracks(artist.tracks) },
              )
            }
            DownloadTab.ALBUMS -> {
              val filteredAlbums = remember(albums, searchQuery, albumSort) {
                albums
                  .filter {
                    if (searchQuery.isBlank()) true
                    else it.title.contains(searchQuery, ignoreCase = true) ||
                      it.artist.contains(searchQuery, ignoreCase = true)
                  }
                  .let { list ->
                    when (albumSort) {
                      AlbumSortOption.TITLE_AZ -> list.sortedBy { it.title.lowercase() }
                      AlbumSortOption.ARTIST_AZ -> list.sortedBy { it.artist.lowercase() }
                      AlbumSortOption.MOST_TRACKS -> list.sortedByDescending { it.tracks.size }
                      AlbumSortOption.RECENT -> list.sortedByDescending { it.tracks.maxOfOrNull { t -> t.downloadedAtMillis } ?: 0L }
                    }
                  }
              }

              DownloadedAlbumsList(
                albums = filteredAlbums,
                onSelectAlbum = { navigateTo(DownloadSubView.AlbumDetail(it.title, it.artist)) },
                onPlayAlbum = { album -> playDownloadedTracks(album.tracks) },
              )
            }
          }
        }
        is DownloadSubView.ArtistDetail -> {
          val artistTracks = remember(tracks, currentSubView.artistName) {
            tracks.filter {
              it.artist.equals(currentSubView.artistName, ignoreCase = true) ||
                it.artist.contains(currentSubView.artistName, ignoreCase = true)
            }.sortedByDescending { it.downloadedAtMillis }
          }
          val artistAlbums = remember(albums, currentSubView.artistName) {
            albums.filter {
              it.artist.equals(currentSubView.artistName, ignoreCase = true) ||
                it.artist.contains(currentSubView.artistName, ignoreCase = true)
            }
          }

          ArtistDetailView(
            artistName = currentSubView.artistName,
            tracks = artistTracks,
            albums = artistAlbums,
            onPlayTrack = { track ->
              val index = artistTracks.indexOf(track).coerceAtLeast(0)
              playDownloadedTracks(artistTracks, startIndex = index)
            },
            onPlayAll = { shuffled -> playDownloadedTracks(artistTracks, shuffled = shuffled) },
            onSelectAlbum = { alb -> navigateTo(DownloadSubView.AlbumDetail(alb, currentSubView.artistName)) },
            onDeleteTrack = { trackToDelete = it },
          )
        }
        is DownloadSubView.AlbumDetail -> {
          val albumTracks = remember(tracks, currentSubView.albumTitle, currentSubView.artistName) {
            tracks.filter {
              it.album.trim().equals(currentSubView.albumTitle.trim(), ignoreCase = true) &&
                (currentSubView.artistName.isBlank() ||
                  it.artist.equals(currentSubView.artistName, ignoreCase = true) ||
                  it.artist.contains(currentSubView.artistName, ignoreCase = true))
            }
          }

          AlbumDetailView(
            albumTitle = currentSubView.albumTitle,
            artistName = currentSubView.artistName,
            tracks = albumTracks,
            onPlayTrack = { track ->
              val index = albumTracks.indexOf(track).coerceAtLeast(0)
              playDownloadedTracks(albumTracks, startIndex = index)
            },
            onPlayAll = { shuffled -> playDownloadedTracks(albumTracks, shuffled = shuffled) },
            onDeleteTrack = { trackToDelete = it },
          )
        }
      }
    }
  }

  trackToDelete?.let { track ->
    AlertDialog(
      onDismissRequest = { trackToDelete = null },
      title = { Text("Delete \"${track.title}\"?") },
      text = { Text("This will remove the downloaded audio file and associated lyrics from storage.") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteTrack(track)
            trackToDelete = null
          },
        ) {
          Text("Delete", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { trackToDelete = null }) { Text("Cancel") }
      },
    )
  }

  if (showClearAllConfirm) {
    AlertDialog(
      onDismissRequest = { showClearAllConfirm = false },
      title = { Text("Clear All Downloads?") },
      text = { Text("Are you sure you want to delete all downloaded songs and lyrics in Music/$downloadFolder?") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.clearAllDownloads()
            showClearAllConfirm = false
          },
        ) {
          Text("Clear All", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancel") }
      },
    )
  }

  if (showFolderDialog) {
    var folderInput by remember(downloadFolder) { mutableStateOf(downloadFolder) }
    AlertDialog(
      onDismissRequest = { showFolderDialog = false },
      title = { Text("Download Folder") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Saved directly in Music/$folderInput on device storage.", style = MaterialTheme.typography.bodySmall)
          OutlinedTextField(
            value = folderInput,
            onValueChange = { folderInput = it },
            label = { Text("Folder Name") },
            prefix = { Text("Music/") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.setDownloadFolder(folderInput)
            showFolderDialog = false
          },
        ) { Text("Save") }
      },
      dismissButton = {
        TextButton(onClick = { showFolderDialog = false }) { Text("Cancel") }
      },
    )
  }

  if (showStructureDialog) {
    AlertDialog(
      onDismissRequest = { showStructureDialog = false },
      title = { Text("Folder Organization") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          DownloadStructure.entries.forEach { struct ->
            val selected = struct == downloadStructure
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { viewModel.setDownloadStructure(struct) }
                .padding(vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(selected = selected, onClick = { viewModel.setDownloadStructure(struct) })
              Spacer(Modifier.width(8.dp))
              Column {
                Text(
                  if (struct == DownloadStructure.FLAT) "Flat Directory" else "Organized by Artist / Album",
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                  if (struct == DownloadStructure.FLAT) "Music/$downloadFolder/Artist - Title.ext" else "Music/$downloadFolder/Artist/Album/Track.ext",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showStructureDialog = false }) { Text("Done") }
      },
    )
  }

  if (showQualityDialog) {
    AlertDialog(
      onDismissRequest = { showQualityDialog = false },
      title = { Text("Download Quality") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          DownloadQuality.entries.forEach { quality ->
            val selected = quality == downloadQuality
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { viewModel.setDownloadQuality(quality) }
                .padding(vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(selected = selected, onClick = { viewModel.setDownloadQuality(quality) })
              Spacer(Modifier.width(8.dp))
              Column {
                Text(
                  quality.name.replace("_", " "),
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showQualityDialog = false }) { Text("Done") }
      },
    )
  }
}

@Composable
private fun DownloadTabBar(
  selectedTab: DownloadTab,
  onTabSelected: (DownloadTab) -> Unit,
  songCount: Int,
  artistCount: Int,
  albumCount: Int,
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 6.dp),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      DownloadTab.entries.forEach { tab ->
        val isSelected = selectedTab == tab
        val count = when (tab) {
          DownloadTab.SONGS -> songCount
          DownloadTab.ARTISTS -> artistCount
          DownloadTab.ALBUMS -> albumCount
        }
        val bgColor by animateColorAsState(
          targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
          label = "tabBg",
        )
        val textColor by animateColorAsState(
          targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
          label = "tabText",
        )

        Surface(
          onClick = { onTabSelected(tab) },
          shape = RoundedCornerShape(16.dp),
          color = bgColor,
          modifier = Modifier
            .weight(1f)
            .height(38.dp),
        ) {
          Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
              text = "${tab.label} ($count)",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = textColor,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DownloadFilterControls(
  searchQuery: String,
  onSearchQueryChange: (String) -> Unit,
  selectedTab: DownloadTab,
  songSort: SongSortOption,
  onSongSortChange: (SongSortOption) -> Unit,
  artistSort: ArtistSortOption,
  onArtistSortChange: (ArtistSortOption) -> Unit,
  albumSort: AlbumSortOption,
  onAlbumSortChange: (AlbumSortOption) -> Unit,
  filterLossless: Boolean,
  onToggleLossless: () -> Unit,
  filterLyrics: Boolean,
  onToggleLyrics: () -> Unit,
) {
  var sortMenuExpanded by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 4.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = {
          Text(
            when (selectedTab) {
              DownloadTab.SONGS -> "Search songs, artists, albums..."
              DownloadTab.ARTISTS -> "Search downloaded artists..."
              DownloadTab.ALBUMS -> "Search downloaded albums..."
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
          )
        },
        leadingIcon = {
          Icon(Icons.Filled.Search, contentDescription = "Search", modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchQueryChange("") }) {
              Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          focusedBorderColor = MaterialTheme.colorScheme.primary,
          unfocusedBorderColor = Color.Transparent,
        ),
        modifier = Modifier
          .weight(1f)
          .height(52.dp),
      )

      Box {
        FilledTonalButton(
          onClick = { sortMenuExpanded = true },
          shape = RoundedCornerShape(16.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          modifier = Modifier.height(52.dp),
        ) {
          Icon(
            Icons.AutoMirrored.Filled.Sort,
            contentDescription = "Sort",
            modifier = Modifier.size(18.dp),
          )
        }

        DropdownMenu(
          expanded = sortMenuExpanded,
          onDismissRequest = { sortMenuExpanded = false },
        ) {
          when (selectedTab) {
            DownloadTab.SONGS -> {
              SongSortOption.entries.forEach { option ->
                DropdownMenuItem(
                  text = {
                    Text(
                      text = option.label,
                      fontWeight = if (songSort == option) FontWeight.Bold else FontWeight.Normal,
                      color = if (songSort == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                  },
                  onClick = {
                    onSongSortChange(option)
                    sortMenuExpanded = false
                  },
                )
              }
            }
            DownloadTab.ARTISTS -> {
              ArtistSortOption.entries.forEach { option ->
                DropdownMenuItem(
                  text = {
                    Text(
                      text = option.label,
                      fontWeight = if (artistSort == option) FontWeight.Bold else FontWeight.Normal,
                      color = if (artistSort == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                  },
                  onClick = {
                    onArtistSortChange(option)
                    sortMenuExpanded = false
                  },
                )
              }
            }
            DownloadTab.ALBUMS -> {
              AlbumSortOption.entries.forEach { option ->
                DropdownMenuItem(
                  text = {
                    Text(
                      text = option.label,
                      fontWeight = if (albumSort == option) FontWeight.Bold else FontWeight.Normal,
                      color = if (albumSort == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                  },
                  onClick = {
                    onAlbumSortChange(option)
                    sortMenuExpanded = false
                  },
                )
              }
            }
          }
        }
      }
    }

    if (selectedTab == DownloadTab.SONGS) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = !filterLossless && !filterLyrics,
          onClick = {
            if (filterLossless) onToggleLossless()
            if (filterLyrics) onToggleLyrics()
          },
          label = { Text("All") },
          shape = RoundedCornerShape(12.dp),
        )
        FilterChip(
          selected = filterLossless,
          onClick = onToggleLossless,
          label = { Text("Lossless / FLAC") },
          shape = RoundedCornerShape(12.dp),
        )
        FilterChip(
          selected = filterLyrics,
          onClick = onToggleLyrics,
          label = { Text("With Lyrics (LRC)") },
          shape = RoundedCornerShape(12.dp),
        )
      }
    }
  }
}

@Composable
private fun DownloadedSongsList(
  tracks: List<DownloadedTrack>,
  totalSizeText: String,
  downloadFolder: String,
  downloadStructure: DownloadStructure,
  onPlayTrack: (DownloadedTrack) -> Unit,
  onPlayAll: (Boolean) -> Unit,
  onDeleteTrack: (DownloadedTrack) -> Unit,
  onSelectArtist: (String) -> Unit,
  onSelectAlbum: (String, String) -> Unit,
) {
  LazyColumn(
    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(Modifier.weight(1f)) {
            Text(
              "$totalSizeText used",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
              "Saved in Music/$downloadFolder (${tracks.size} tracks)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
              onClick = { onPlayAll(false) },
              shape = RoundedCornerShape(14.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              modifier = Modifier.height(36.dp),
            ) {
              Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Play All", style = MaterialTheme.typography.labelSmall)
            }
            FilledTonalButton(
              onClick = { onPlayAll(true) },
              shape = RoundedCornerShape(14.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
              modifier = Modifier.height(36.dp),
            ) {
              Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
            }
          }
        }
      }
    }

    if (tracks.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              Icons.Filled.MusicNote,
              contentDescription = null,
              modifier = Modifier.size(48.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
              "No downloaded music yet",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              "Downloaded FLAC, AAC, Opus, MP3 tracks appear here",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
          }
        }
      }
    } else {
      items(tracks, key = { it.id }) { track ->
        DownloadedTrackRow(
          track = track,
          onClick = { onPlayTrack(track) },
          onDelete = { onDeleteTrack(track) },
          onSelectArtist = { onSelectArtist(track.artist) },
          onSelectAlbum = { onSelectAlbum(track.album, track.artist) },
        )
      }
    }
  }
}

@Composable
private fun DownloadedTrackRow(
  track: DownloadedTrack,
  onClick: () -> Unit,
  onDelete: () -> Unit,
  onSelectArtist: () -> Unit,
  onSelectAlbum: () -> Unit,
) {
  var showMenu by remember { mutableStateOf(false) }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
      ) {
        if (!track.artworkUrl.isNullOrBlank()) {
          AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
          )
        }
      }

      Spacer(Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = track.title,
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = track.artist + if (track.album.isNotBlank()) " \u2022 ${track.album}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (track.isLossless) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
          ) {
            Text(
              text = track.formatBadge,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = if (track.isLossless) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
          if (track.hasLyrics) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
              Text(
                text = "LRC",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
              )
            }
          }
        }
        Text(
          text = "${formatBytes(track.fileSizeBytes)}" + if (track.durationMs > 0) " \u2022 ${formatDuration(track.durationMs)}" else "",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
      }

      Box {
        IconButton(onClick = { showMenu = true }) {
          Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
          DropdownMenuItem(
            text = { Text("Play") },
            leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
            onClick = {
              showMenu = false
              onClick()
            },
          )
          if (track.artist.isNotBlank()) {
            DropdownMenuItem(
              text = { Text("View Artist") },
              leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
              onClick = {
                showMenu = false
                onSelectArtist()
              },
            )
          }
          if (track.album.isNotBlank()) {
            DropdownMenuItem(
              text = { Text("View Album") },
              leadingIcon = { Icon(Icons.Filled.Album, contentDescription = null) },
              onClick = {
                showMenu = false
                onSelectAlbum()
              },
            )
          }
          DropdownMenuItem(
            text = { Text("Delete Download", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = {
              showMenu = false
              onDelete()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun DownloadedArtistsList(
  artists: List<DownloadedArtist>,
  onSelectArtist: (DownloadedArtist) -> Unit,
  onPlayArtist: (DownloadedArtist) -> Unit,
) {
  LazyColumn(
    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    if (artists.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            "No downloaded artists found",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      items(artists, key = { it.name }) { artist ->
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectArtist(artist) },
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
              contentAlignment = Alignment.Center,
            ) {
              if (!artist.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                  model = artist.artworkUrl,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize(),
                )
              } else {
                Icon(
                  Icons.Filled.Person,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp),
                )
              }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${artist.tracks.size} songs \u2022 ${artist.albumCount} albums \u2022 ${formatBytes(artist.totalSizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            IconButton(onClick = { onPlayArtist(artist) }) {
              Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DownloadedAlbumsList(
  albums: List<DownloadedAlbum>,
  onSelectAlbum: (DownloadedAlbum) -> Unit,
  onPlayAlbum: (DownloadedAlbum) -> Unit,
) {
  LazyColumn(
    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    if (albums.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            "No downloaded albums found",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      items(albums, key = { "${it.title}_${it.artist}" }) { album ->
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectAlbum(album) },
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
              contentAlignment = Alignment.Center,
            ) {
              if (!album.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                  model = album.artworkUrl,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize(),
                )
              } else {
                Icon(
                  Icons.Filled.Album,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp),
                )
              }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = album.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${album.artist} \u2022 ${album.tracks.size} tracks \u2022 ${formatBytes(album.totalSizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            IconButton(onClick = { onPlayAlbum(album) }) {
              Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ArtistDetailView(
  artistName: String,
  tracks: List<DownloadedTrack>,
  albums: List<DownloadedAlbum>,
  onPlayTrack: (DownloadedTrack) -> Unit,
  onPlayAll: (Boolean) -> Unit,
  onSelectAlbum: (String) -> Unit,
  onDeleteTrack: (DownloadedTrack) -> Unit,
) {
  LazyColumn(
    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        FilledTonalButton(
          onClick = { onPlayAll(false) },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Play All (${tracks.size})")
        }
        FilledTonalButton(
          onClick = { onPlayAll(true) },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Shuffle")
        }
      }
    }

    if (albums.isNotEmpty()) {
      item {
        Text(
          "Albums (${albums.size})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
      }
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          albums.forEach { album ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              modifier = Modifier
                .width(130.dp)
                .clickable { onSelectAlbum(album.title) }
                .padding(8.dp),
            ) {
              Column {
                Box(
                  modifier = Modifier
                    .size(114.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                  contentAlignment = Alignment.Center,
                ) {
                  if (!album.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                      model = album.artworkUrl,
                      contentDescription = null,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize(),
                    )
                  } else {
                    Icon(Icons.Filled.Album, contentDescription = null)
                  }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                  album.title,
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  "${album.tracks.size} tracks",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }

    item {
      Text(
        "Songs (${tracks.size})",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
      )
    }

    items(tracks, key = { it.id }) { track ->
      DownloadedTrackRow(
        track = track,
        onClick = { onPlayTrack(track) },
        onDelete = { onDeleteTrack(track) },
        onSelectArtist = {},
        onSelectAlbum = { onSelectAlbum(track.album) },
      )
    }
  }
}

@Composable
private fun AlbumDetailView(
  albumTitle: String,
  artistName: String,
  tracks: List<DownloadedTrack>,
  onPlayTrack: (DownloadedTrack) -> Unit,
  onPlayAll: (Boolean) -> Unit,
  onDeleteTrack: (DownloadedTrack) -> Unit,
) {
  val artwork = tracks.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
  val totalBytes = tracks.sumOf { it.fileSizeBytes }

  LazyColumn(
    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier
            .size(90.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
          contentAlignment = Alignment.Center,
        ) {
          if (!artwork.isNullOrBlank()) {
            AsyncImage(
              model = artwork,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          } else {
            Icon(Icons.Filled.Album, contentDescription = null, modifier = Modifier.size(40.dp))
          }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(albumTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          if (artistName.isNotBlank()) {
            Text(artistName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Text(
            "${tracks.size} tracks \u2022 ${formatBytes(totalBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
          )
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        FilledTonalButton(
          onClick = { onPlayAll(false) },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Play All")
        }
        FilledTonalButton(
          onClick = { onPlayAll(true) },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Shuffle")
        }
      }
    }

    itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
      DownloadedTrackRow(
        track = track,
        onClick = { onPlayTrack(track) },
        onDelete = { onDeleteTrack(track) },
        onSelectArtist = {},
        onSelectAlbum = {},
      )
    }
  }
}
