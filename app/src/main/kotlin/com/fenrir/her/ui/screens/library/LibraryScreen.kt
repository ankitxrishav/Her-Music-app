package com.fenrir.her.ui.screens.library

import androidx.compose.foundation.clickable
import com.fenrir.her.ui.component.Material3SettingsGroup
import com.fenrir.her.ui.component.Material3SettingsItem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fenrir.her.LocalDatabase
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.fenrir.her.LocalPlayerAwareWindowInsets
import com.fenrir.her.R
import com.fenrir.her.constants.ChipSortTypeKey
import com.fenrir.her.constants.LibraryFilter
import com.fenrir.her.ui.component.ChipsRow
import com.fenrir.her.ui.component.DefaultDialog
import com.fenrir.her.ui.component.TextFieldDialog
import com.fenrir.her.utils.rememberEnumPreference

@Composable
fun LibraryScreen(navController: NavController) {
  var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
  var showFabMenu by remember { mutableStateOf(false) }
  var showImportMenu by remember { mutableStateOf(false) }
  var showYoutubeImportDialog by remember { mutableStateOf(false) }
  var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
  var showCreatePlaylistOptionsDialog by rememberSaveable { mutableStateOf(false) }
  var showAiPlaylistDialog by rememberSaveable { mutableStateOf(false) }
  val context = LocalContext.current

  BackHandler(enabled = filterType != LibraryFilter.LIBRARY) { filterType = LibraryFilter.LIBRARY }

  val filterContent =
    @Composable {
      Row {
        ChipsRow(
          chips =
            listOf(
              LibraryFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
              LibraryFilter.SONGS to stringResource(R.string.filter_songs),
              LibraryFilter.ALBUMS to stringResource(R.string.filter_albums),
              LibraryFilter.ARTISTS to stringResource(R.string.filter_artists),
              LibraryFilter.LOCAL to stringResource(R.string.filter_local),
            ),
          currentValue = filterType,
          onValueUpdate = {
            filterType =
              if (filterType == it) {
                LibraryFilter.LIBRARY
              } else {
                it
              }
          },
          modifier = Modifier.weight(1f),
        )
      }
    }

  val currentInsets = LocalPlayerAwareWindowInsets.current
  val density = LocalDensity.current
  val layoutDirection = LocalLayoutDirection.current
  val fabHeightPlusSpacing = 128.dp
  val newInsets =
    remember(currentInsets, density, layoutDirection) {
      object : WindowInsets {
        override fun getLeft(density: Density, layoutDirection: LayoutDirection) =
          currentInsets.getLeft(density, layoutDirection)

        override fun getTop(density: Density) = currentInsets.getTop(density)

        override fun getRight(density: Density, layoutDirection: LayoutDirection) =
          currentInsets.getRight(density, layoutDirection)

        override fun getBottom(density: Density) =
          currentInsets.getBottom(density) + with(density) { fabHeightPlusSpacing.roundToPx() }
      }
    }

  CompositionLocalProvider(LocalPlayerAwareWindowInsets provides newInsets) {
    val isDark = isSystemInDarkTheme()
    val libraryBgColor = if (isDark) Color(0xFF141922) else Color(0xFFCEE5FD)

    Box(
      modifier = Modifier.fillMaxSize().background(libraryBgColor),
    ) {
      when (filterType) {
        LibraryFilter.LIBRARY -> NeoLibraryLandingView(
          navController = navController,
          onSelectFilter = { filterType = it }
        )
        LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(navController, filterContent)
        LibraryFilter.SONGS ->
          LibrarySongsScreen(navController, { filterType = LibraryFilter.LIBRARY })
        LibraryFilter.ALBUMS ->
          LibraryAlbumsScreen(navController, { filterType = LibraryFilter.LIBRARY })
        LibraryFilter.ARTISTS ->
          LibraryArtistsScreen(navController, { filterType = LibraryFilter.LIBRARY })
        LibraryFilter.LOCAL ->
          LocalSongScreen(navController, { filterType = LibraryFilter.LIBRARY }, isEmbedded = true)
      }

      val bottomPadding = with(density) { currentInsets.getBottom(density).toDp() }
      Box(modifier = Modifier.fillMaxSize().padding(end = 16.dp, bottom = bottomPadding + 20.dp)) {
        Box(modifier = Modifier.align(Alignment.BottomEnd)) {
          androidx.compose.material3.FloatingActionButton(
            onClick = { showFabMenu = true },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface
          ) {
            Icon(painter = painterResource(R.drawable.add), contentDescription = "Add")
          }




        }
      }
    }
  }

  if (showFabMenu) {
    @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class) androidx.compose.material3.ModalBottomSheet(
      onDismissRequest = { showFabMenu = false },
      sheetState = @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class) androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
      Column(modifier = Modifier.padding(bottom = 24.dp, start = 16.dp, end = 16.dp)) {
        Material3SettingsGroup(
          compact = true,
          items = listOf(
            Material3SettingsItem(
              title = { Text(stringResource(R.string.create_playlist)) },
              icon = painterResource(R.drawable.add),
              onClick = {
                showFabMenu = false
                showCreatePlaylistOptionsDialog = true
              }
            ),
            Material3SettingsItem(
              title = { Text(stringResource(R.string.import_playlist)) },
              icon = painterResource(R.drawable.download),
              onClick = {
                showFabMenu = false
                showImportMenu = true
              }
            )
          )
        )
      }
    }
  }

  if (showImportMenu) {
    @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class) androidx.compose.material3.ModalBottomSheet(
      onDismissRequest = { showImportMenu = false },
      sheetState = @kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class) androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
      Column(modifier = Modifier.padding(bottom = 24.dp)) {
        androidx.compose.material3.ListItem(
          headlineContent = { Text(stringResource(R.string.import_from_spotify)) },
          
          modifier = Modifier.clickable {
            showImportMenu = false
            navController.navigate("settings/spotify_import")
          }
        )
        androidx.compose.material3.ListItem(
          headlineContent = { Text(stringResource(R.string.import_from_youtube_music)) },
          
          modifier = Modifier.clickable {
            showImportMenu = false
            showYoutubeImportDialog = true
          }
        )
      }
    }
  }

  if (showYoutubeImportDialog) {
    var url by remember { mutableStateOf(TextFieldValue("")) }
    val invalidUrlMessage = stringResource(R.string.invalid_playlist_url)
    com.fenrir.her.ui.component.TextFieldDialog(
      icon = { Icon(painter = painterResource(R.drawable.link), contentDescription = null) },
      title = {
        Column {
          Text(text = stringResource(R.string.import_youtube_music_playlist_title))
          Text(
            text = stringResource(R.string.import_youtube_music_playlist_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
          )
        }
      },
      initialTextFieldValue = url,
      autoFocus = true,
      onDismiss = { showYoutubeImportDialog = false },
      onDone = { finalUrl ->
        val listId = Regex("[?&]list=([a-zA-Z0-9_-]+)").find(finalUrl)?.groupValues?.get(1)
        if (listId != null) {
          navController.navigate("online_playlist/$listId")
        } else {
          android.widget.Toast.makeText(
              context,
              invalidUrlMessage,
              android.widget.Toast.LENGTH_SHORT
            )
            .show()
        }
        showYoutubeImportDialog = false
      }
    )
  }

  if (showCreatePlaylistDialog) {
    com.fenrir.her.ui.component.CreatePlaylistDialog(
      onDismiss = { showCreatePlaylistDialog = false },
      initialTextFieldValue = null,
      allowSyncing = true,
      onPlaylistCreated = { playlistId ->
        showCreatePlaylistDialog = false
        navController.navigate("local_playlist/$playlistId")
      }
    )
  }

  if (showCreatePlaylistOptionsDialog) {
    DefaultDialog(
      onDismiss = { showCreatePlaylistOptionsDialog = false },
      title = { Text(stringResource(R.string.create_playlist)) },
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        androidx.compose.material3.Surface(
          shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          modifier = Modifier.fillMaxWidth()
        ) {
          com.fenrir.her.ui.component.PreferenceEntry(
            icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
            title = { Text(stringResource(R.string.create_playlist_normally)) },
            description = "Standard playlist creation",
            onClick = {
              showCreatePlaylistOptionsDialog = false
              showCreatePlaylistDialog = true
            }
          )
        }

        androidx.compose.material3.Surface(
          shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          modifier = Modifier.fillMaxWidth()
        ) {
          com.fenrir.her.ui.component.PreferenceEntry(
            icon = {
              Icon(painter = painterResource(R.drawable.sparks), contentDescription = null)
            },
            title = { Text(stringResource(R.string.create_playlist_with_ai)) },
            description = "AI-powered playlist generation",
            onClick = {
              showCreatePlaylistOptionsDialog = false
              showAiPlaylistDialog = true
            }
          )
        }
      }
    }
  }

  if (showAiPlaylistDialog) {
    com.fenrir.her.ui.component.CreateAiPlaylistDialog(
      onDismiss = { showAiPlaylistDialog = false },
      onPlaylistCreated = { playlistId ->
        showAiPlaylistDialog = false
        navController.navigate("local_playlist/$playlistId")
      }
    )
  }
}

@Composable
fun NeoLibraryLandingView(
  navController: NavController,
  onSelectFilter: (LibraryFilter) -> Unit
) {
  val database = LocalDatabase.current
  val likedCount by database.likedSongsCount().collectAsState(initial = 0)
  val isDark = isSystemInDarkTheme()
  val titleColor = if (isDark) Color.White else Color(0xFF141922)
  val cardBg = if (isDark) Color(0xFF1E2530) else Color(0xFFFFFDF8)
  val cardBorder = if (isDark) Color(0x22FFFFFF) else Color(0x18000000)

  data class LibraryItemData(
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val iconTint: Color,
    val iconBg: Color,
    val filter: LibraryFilter,
    val route: String? = null
  )

  val libraryItems = listOf(
    LibraryItemData(
      title = "Liked Songs",
      subtitle = if (likedCount > 0) "$likedCount songs" else "Your favorite tracks",
      iconRes = R.drawable.favorite,
      iconTint = Color(0xFFE11D48),
      iconBg = Color(0xFFFFE4E8),
      filter = LibraryFilter.SONGS
    ),
    LibraryItemData(
      title = "Playlists",
      subtitle = "Created & saved playlists",
      iconRes = R.drawable.queue_music,
      iconTint = Color(0xFF7C3AED),
      iconBg = Color(0xFFEDE9FE),
      filter = LibraryFilter.PLAYLISTS
    ),
    LibraryItemData(
      title = "Albums",
      subtitle = "Saved albums & EPs",
      iconRes = R.drawable.album,
      iconTint = Color(0xFFEA580C),
      iconBg = Color(0xFFFFEDD5),
      filter = LibraryFilter.ALBUMS
    ),
    LibraryItemData(
      title = "Artists",
      subtitle = "Artists you follow",
      iconRes = R.drawable.artist,
      iconTint = Color(0xFF0D9488),
      iconBg = Color(0xFFCCFBF1),
      filter = LibraryFilter.ARTISTS
    ),
    LibraryItemData(
      title = "Recently Played",
      subtitle = "Jump back into music",
      iconRes = R.drawable.history,
      iconTint = Color(0xFF0284C7),
      iconBg = Color(0xFFE0F2FE),
      filter = LibraryFilter.LIBRARY,
      route = "history"
    ),
    LibraryItemData(
      title = "Device Files",
      subtitle = "Locally downloaded & cached",
      iconRes = R.drawable.download,
      iconTint = Color(0xFF16A34A),
      iconBg = Color(0xFFDCFCE7),
      filter = LibraryFilter.LOCAL
    ),
  )

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
  ) {
    item(key = "library_header") {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Library",
          style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            letterSpacing = (-0.5).sp
          ),
          color = titleColor
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(cardBg)
              .border(1.dp, cardBorder, CircleShape)
              .clickable { navController.navigate("search_input") },
            contentAlignment = Alignment.Center
          ) {
            Icon(
              painter = painterResource(R.drawable.search),
              contentDescription = "Search",
              tint = titleColor,
              modifier = Modifier.size(20.dp)
            )
          }

          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(cardBg)
              .border(1.dp, cardBorder, CircleShape)
              .clickable { navController.navigate("settings") },
            contentAlignment = Alignment.Center
          ) {
            Icon(
              painter = painterResource(R.drawable.settings),
              contentDescription = "Settings",
              tint = titleColor,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }

    item(key = "library_items_card") {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 4.dp)
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          libraryItems.forEachIndexed { index, item ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  if (item.route != null) {
                    navController.navigate(item.route)
                  } else {
                    onSelectFilter(item.filter)
                  }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
              ) {
                Box(
                  modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) item.iconTint.copy(alpha = 0.2f) else item.iconBg),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    painter = painterResource(item.iconRes),
                    contentDescription = null,
                    tint = item.iconTint,
                    modifier = Modifier.size(22.dp)
                  )
                }

                Column {
                  Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                      fontWeight = FontWeight.Bold,
                      fontSize = 16.sp
                    ),
                    color = titleColor
                  )
                  Spacer(Modifier.height(2.dp))
                  Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF666666)
                  )
                }
              }

              Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = if (isDark) Color.White.copy(alpha = 0.4f) else Color(0xFF999999),
                modifier = Modifier.size(20.dp)
              )
            }

            if (index < libraryItems.size - 1) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(start = 74.dp)
                  .height(0.5.dp)
                  .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color(0x18000000))
              )
            }
          }
        }
      }
    }

    item(key = "couple_music_card") {
      Spacer(Modifier.height(14.dp))
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .clickable { navController.navigate("couple_space") }
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Good music feels better together ♥",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
              ),
              color = titleColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text = "Listen in sync with your partner in Couple Space",
              style = MaterialTheme.typography.bodySmall,
              color = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF666666)
            )
          }

          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(Color(0xFFE11D48)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              painter = painterResource(R.drawable.favorite),
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(22.dp)
            )
          }
        }
      }
      Spacer(Modifier.height(32.dp))
    }
  }
}

