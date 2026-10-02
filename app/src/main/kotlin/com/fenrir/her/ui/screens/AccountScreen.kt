package com.fenrir.her.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.fenrir.her.LocalPlayerAwareWindowInsets
import com.fenrir.her.R
import com.fenrir.her.constants.GridItemSize
import com.fenrir.her.constants.GridItemsSizeKey
import com.fenrir.her.constants.GridThumbnailHeight
import com.fenrir.her.ui.component.ChipsRow
import com.fenrir.her.ui.component.IconButton
import com.fenrir.her.ui.component.LocalMenuState
import com.fenrir.her.ui.component.YouTubeGridItem
import com.fenrir.her.ui.component.shimmer.GridItemPlaceHolder
import com.fenrir.her.ui.component.shimmer.ShimmerHost
import com.fenrir.her.ui.menu.YouTubeAlbumMenu
import com.fenrir.her.ui.menu.YouTubeArtistMenu
import com.fenrir.her.ui.menu.YouTubePlaylistMenu
import com.fenrir.her.ui.utils.backToMain
import com.fenrir.her.utils.rememberEnumPreference
import com.fenrir.her.viewmodels.AccountContentType
import com.fenrir.her.viewmodels.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountScreen(
  navController: NavController,
  scrollBehavior: TopAppBarScrollBehavior,
  viewModel: AccountViewModel = hiltViewModel(),
) {
  val menuState = LocalMenuState.current
  val haptic = LocalHapticFeedback.current

  val coroutineScope = rememberCoroutineScope()

  val playlists by viewModel.playlists.collectAsState()
  val albums by viewModel.albums.collectAsState()
  val artists by viewModel.artists.collectAsState()
  val selectedContentType by viewModel.selectedContentType.collectAsState()
  val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

  LazyVerticalGrid(
    columns =
      GridCells.Adaptive(
        minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp
      ),
    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
  ) {
    item(span = { GridItemSpan(maxLineSpan) }) {
      ChipsRow(
        chips =
          listOf(
            AccountContentType.PLAYLISTS to stringResource(R.string.filter_playlists),
            AccountContentType.ALBUMS to stringResource(R.string.filter_albums),
            AccountContentType.ARTISTS to stringResource(R.string.filter_artists),
          ),
        currentValue = selectedContentType,
        onValueUpdate = { viewModel.setSelectedContentType(it) },
      )
    }

    when (selectedContentType) {
      AccountContentType.PLAYLISTS -> {
        items(
          items = playlists.orEmpty().distinctBy { it.id },
          key = { it.id },
        ) { item ->
          YouTubeGridItem(
            item = item,
            fillMaxWidth = true,
            modifier =
              Modifier.combinedClickable(
                onClick = { navController.navigate("online_playlist/${item.id}") },
                onLongClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  menuState.show {
                    YouTubePlaylistMenu(
                      playlist = item,
                      coroutineScope = coroutineScope,
                      onDismiss = menuState::dismiss,
                    )
                  }
                },
              ),
          )
        }

        if (playlists == null) {
          items(8) { ShimmerHost { GridItemPlaceHolder(fillMaxWidth = true) } }
        }
      }
      AccountContentType.ALBUMS -> {
        items(items = albums.orEmpty().distinctBy { it.id }, key = { it.id }) { item ->
          YouTubeGridItem(
            item = item,
            fillMaxWidth = true,
            modifier =
              Modifier.combinedClickable(
                onClick = { navController.navigate("album/${item.id}") },
                onLongClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  menuState.show {
                    YouTubeAlbumMenu(
                      albumItem = item,
                      navController = navController,
                      onDismiss = menuState::dismiss
                    )
                  }
                }
              )
          )
        }

        if (albums == null) {
          items(8) { ShimmerHost { GridItemPlaceHolder(fillMaxWidth = true) } }
        }
      }
      AccountContentType.ARTISTS -> {
        items(items = artists.orEmpty().distinctBy { it.id }, key = { it.id }) { item ->
          YouTubeGridItem(
            item = item,
            fillMaxWidth = true,
            modifier =
              Modifier.combinedClickable(
                onClick = { navController.navigate("artist/${item.id}") },
                onLongClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  menuState.show {
                    YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                  }
                }
              )
          )
        }

        if (artists == null) {
          items(8) { ShimmerHost { GridItemPlaceHolder(fillMaxWidth = true) } }
        }
      }
    }
  }

  TopAppBar(
    title = { Text(stringResource(R.string.account)) },
    navigationIcon = {
      IconButton(
        onClick = navController::navigateUp,
        onLongClick = navController::backToMain,
      ) {
        Icon(
          painterResource(R.drawable.arrow_back),
          contentDescription = null,
        )
      }
    },
  )
}
