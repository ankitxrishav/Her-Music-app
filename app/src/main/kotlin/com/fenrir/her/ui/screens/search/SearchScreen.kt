package com.fenrir.her.ui.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.utils.YouTubeUrlParser
import com.fenrir.her.LocalDatabase
import com.fenrir.her.LocalIsPlayerExpanded
import com.fenrir.her.LocalPlayerAwareWindowInsets
import com.fenrir.her.LocalPlayerConnection
import com.fenrir.her.R
import com.fenrir.her.constants.GridItemSize
import com.fenrir.her.constants.GridItemsSizeKey
import com.fenrir.her.constants.GridThumbnailHeight
import com.fenrir.her.constants.PauseSearchHistoryKey
import com.fenrir.her.constants.SearchSource
import com.fenrir.her.constants.SearchSourceKey
import com.fenrir.her.db.entities.SearchHistory
import com.fenrir.her.playback.queues.YouTubeQueue
import com.fenrir.her.ui.component.LocalMenuState
import com.fenrir.her.ui.component.NavigationTitle
import com.fenrir.her.ui.component.YouTubeGridItem
import com.fenrir.her.ui.menu.YouTubeAlbumMenu
import com.fenrir.her.ui.screens.search.suggestions.SuggestionsTabContent
import com.fenrir.her.utils.rememberEnumPreference
import com.fenrir.her.utils.rememberPreference
import com.fenrir.her.viewmodels.ExploreViewModel
import com.fenrir.her.viewmodels.MoodAndGenresViewModel
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavController, pureBlack: Boolean) {
  val database = LocalDatabase.current
  val coroutineScope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current
  val isPlayerExpanded = LocalIsPlayerExpanded.current
  val playerConnection = LocalPlayerConnection.current
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

  var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
  var query by
    rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
  val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)

  var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
  var searchActive by rememberSaveable { mutableStateOf(false) }
  var showSearchContent by remember { mutableStateOf(false) }

  LaunchedEffect(searchActive) {
    if (searchActive) {

      kotlinx.coroutines.delay(100)
      showSearchContent = true
    } else {
      showSearchContent = false
    }
  }

  val searchBarHorizontalPadding by
    animateDpAsState(
      targetValue = 16.dp,
      animationSpec = tween(durationMillis = 245, easing = FastOutSlowInEasing),
      label = "SearchBarHorizontalPadding"
    )
  val searchBarTopPadding by
    animateDpAsState(
      targetValue = 8.dp,
      animationSpec = tween(durationMillis = 245, easing = FastOutSlowInEasing),
      label = "SearchBarTopPadding"
    )

  val onSearch: (String) -> Unit = remember {
    { searchQuery ->
      if (searchQuery.isNotEmpty()) {
        focusManager.clearFocus()
        when (val parsedUrl = YouTubeUrlParser.parse(searchQuery)) {
          is YouTubeUrlParser.ParsedUrl.Video -> {
            playerConnection?.playQueue(
              YouTubeQueue(
                WatchEndpoint(videoId = parsedUrl.id),
              ),
            )
          }
          is YouTubeUrlParser.ParsedUrl.Artist -> {
            navController.navigate("artist/${parsedUrl.id}")
          }
          null -> {
            navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}")
          }
        }

        if (!pauseSearchHistory) {
          coroutineScope.launch(Dispatchers.IO) {
            database.query { insert(SearchHistory(query = searchQuery)) }
          }
        }
      }
    }
  }

  val onSearchFromSuggestion: (String) -> Unit = remember {
    { searchQuery ->
      if (searchQuery.isNotEmpty()) {
        focusManager.clearFocus()
        when (val parsedUrl = YouTubeUrlParser.parse(searchQuery)) {
          is YouTubeUrlParser.ParsedUrl.Video -> {
            playerConnection?.playQueue(
              YouTubeQueue(
                WatchEndpoint(videoId = parsedUrl.id),
              ),
            )
          }
          is YouTubeUrlParser.ParsedUrl.Artist -> {
            navController.navigate("artist/${parsedUrl.id}")
          }
          null -> {
            navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}")
          }
        }

        if (!pauseSearchHistory) {
          coroutineScope.launch(Dispatchers.IO) {
            database.query { insert(SearchHistory(query = searchQuery)) }
          }
        }
      }
    }
  }

  val isDark = isSystemInDarkTheme()
  val searchBgColor = if (pureBlack) Color.Black else if (isDark) Color(0xFF171520) else Color(0xFFDCD9F8)
  val searchBarColor = if (pureBlack) Color(0xFF1E1E1E) else if (isDark) Color(0xFF262334) else Color(0xFFFFFDF8)
  val searchBarBorder = if (isDark) Color(0x22FFFFFF) else Color(0x18000000)

  Scaffold(
    topBar = {
      Column(
        modifier =
          Modifier.background(searchBgColor)
            .windowInsetsPadding(WindowInsets.statusBars)
      ) {
        Surface(
          shape = RoundedCornerShape(28.dp),
          color = searchBarColor,
          border = BorderStroke(1.dp, searchBarBorder),
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = searchBarHorizontalPadding)
              .padding(top = searchBarTopPadding)
              .height(56.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
          ) {
            IconButton(
              onClick = {
                if (searchActive) {
                  searchActive = false
                  query = TextFieldValue("")
                } else {
                  searchActive = true
                }
              }
            ) {
              Icon(
                painter =
                  painterResource(
                    if (searchActive) R.drawable.arrow_back else R.drawable.search
                  ),
                contentDescription =
                  if (searchActive) stringResource(R.string.dismiss) else null,
                tint = MaterialTheme.colorScheme.onSurface
              )
            }
            Box(
              modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
              contentAlignment = Alignment.CenterStart
            ) {
              if (query.text.isEmpty()) {
                Text(
                  text = "Search songs, artists, moods...",
                  style =
                    TextStyle(
                      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                      fontSize = 16.sp
                    )
                )
              }
              BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier =
                  Modifier.fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) searchActive = true },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                  KeyboardActions(
                    onSearch = {
                      onSearch(query.text)
                      searchActive = false
                    }
                  ),
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
              )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (query.text.isNotEmpty()) {
                IconButton(onClick = { query = TextFieldValue("") }) {
                  Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                  )
                }
              }
              IconButton(
                onClick = {
                  searchSource =
                    if (searchSource == SearchSource.ONLINE) SearchSource.LOCAL
                    else SearchSource.ONLINE
                }
              ) {
                Icon(
                  painter =
                    painterResource(
                      when (searchSource) {
                        SearchSource.LOCAL -> R.drawable.library_music
                        SearchSource.ONLINE -> R.drawable.globe_search
                      }
                    ),
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

      }
    },
    containerColor = searchBgColor
  ) { paddingValues ->
    val bottomPadding =
      LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.padding(top = paddingValues.calculateTopPadding()).fillMaxSize()) {
      if (searchActive && showSearchContent) {
            when (searchSource) {
              SearchSource.LOCAL ->
                LocalSearchScreen(
                  query = query.text,
                  navController = navController,
                  onDismiss = { searchActive = false },
                  pureBlack = pureBlack
                )
              SearchSource.ONLINE ->
                OnlineSearchScreen(
                  query = query.text,
                  onQueryChange = { query = it },
                  navController = navController,
                  onSearch = {
                    onSearchFromSuggestion(it)
                    searchActive = false
                  },
                  onDismiss = { searchActive = false },
                  pureBlack = pureBlack
                )
            }
      } else if (!searchActive) {
        val tabPadding = PaddingValues(bottom = bottomPadding)
        NeoSearchLandingView(
          navController = navController,
          contentPadding = tabPadding,
          onSelectSearch = {
            query = TextFieldValue(it)
            onSearch(it)
            searchActive = false
          }
        )
      }
    }
  }

  DisposableEffect(lifecycleOwner, isPlayerExpanded) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> {

          if (isPlayerExpanded) {
            keyboardController?.hide()
            focusManager.clearFocus()
          }
        }
        Lifecycle.Event.ON_PAUSE -> {

          focusManager.clearFocus()
          keyboardController?.hide()
        }
        else -> {}
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)

    if (isPlayerExpanded) {
      keyboardController?.hide()
      focusManager.clearFocus()
    }

    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
}

@Composable
fun ExploreTabContent(
  navController: NavController,
  viewModel: MoodAndGenresViewModel = hiltViewModel(),
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  val moodAndGenresList by viewModel.moodAndGenres.collectAsState()

  LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
    moodAndGenresList?.forEach { section ->
      item { NavigationTitle(title = section.title) }

      val rows = section.items.chunked(2)
      items(rows) { row ->
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
          row.forEach { item ->
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier.weight(1f)
                  .padding(6.dp)
                  .height(64.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(MaterialTheme.colorScheme.surfaceContainerLow)
                  .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                  )
                  .clickable {
                    navController.navigate(
                      "youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}"
                    )
                  }
            ) {
              Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          repeat(2 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
      }
    }

    if (moodAndGenresList == null) {
      item {
        Box(
          modifier = Modifier.fillMaxWidth().height(200.dp),
          contentAlignment = Alignment.Center
        ) {
          CircularWavyProgressIndicator()
        }
      }
    }

    item { Spacer(modifier = Modifier.height(16.dp)) }
  }
}

@Composable
fun AlbumsTabContent(
  navController: NavController,
  viewModel: ExploreViewModel = hiltViewModel(),
  contentPadding: PaddingValues = PaddingValues(0.dp)
) {
  val menuState = LocalMenuState.current
  val haptic = LocalHapticFeedback.current
  val playerConnection = LocalPlayerConnection.current
  val mediaMetadata by
    (playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) })
  val isPlaying by
    (playerConnection?.isEffectivelyPlaying?.collectAsState() ?: remember { mutableStateOf(false) })
  val coroutineScope = rememberCoroutineScope()

  val explorePage by viewModel.explorePage.collectAsState()
  val newReleaseAlbums = explorePage?.newReleaseAlbums

  val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

  if (newReleaseAlbums == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      CircularWavyProgressIndicator()
    }
  } else {
    LazyVerticalGrid(
      columns =
        GridCells.Adaptive(
          minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp
        ),
      contentPadding =
        PaddingValues(
          start = 12.dp,
          top = 12.dp,
          end = 12.dp,
          bottom = 12.dp + contentPadding.calculateBottomPadding()
        ),
      modifier = Modifier.fillMaxSize()
    ) {
      items(items = newReleaseAlbums.distinctBy { it.id }, key = { it.id }) { album ->
        YouTubeGridItem(
          item = album,
          isActive = mediaMetadata?.album?.id == album.id,
          isPlaying = isPlaying,
          coroutineScope = coroutineScope,
          fillMaxWidth = true,
          modifier =
            Modifier.combinedClickable(
              onClick = { navController.navigate("album/${album.id}") },
              onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                  YouTubeAlbumMenu(
                    albumItem = album,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                  )
                }
              },
            )
        )
      }
    }
  }
}

@Composable
fun NeoSearchLandingView(
  navController: NavController,
  contentPadding: PaddingValues,
  onSelectSearch: (String) -> Unit
) {
  val isDark = isSystemInDarkTheme()
  val sectionTitleColor = if (isDark) Color.White else Color(0xFF191712)
  val cardBg = if (isDark) Color(0xFF262234) else Color(0xFFFFFDF8)
  val cardBorder = if (isDark) Color(0x22FFFFFF) else Color(0x18000000)

  val trendingChips = listOf("Love songs", "Acoustic", "Lofi Beats", "Night Drive", "Romantic", "Chillhop")

  data class MoodCardItem(val title: String, val emoji: String, val subtitle: String, val bgLight: Color, val bgDark: Color)
  val moodCards = listOf(
    MoodCardItem("Happy", "😄", "Feel-good hits", Color(0xFFFFF7C2), Color(0xFF332D15)),
    MoodCardItem("Sad", "🌧️", "Melancholic tones", Color(0xFFD7E8FA), Color(0xFF152636)),
    MoodCardItem("Love", "💖", "Romantic melodies", Color(0xFFFFD6E0), Color(0xFF361822)),
    MoodCardItem("Focus", "🎯", "Deep concentration", Color(0xFFDCF5E2), Color(0xFF16331E)),
    MoodCardItem("Workout", "⚡", "High energy", Color(0xFFFFE0D2), Color(0xFF362015)),
    MoodCardItem("Chill", "🌙", "Relax & unwind", Color(0xFFE5E2F8), Color(0xFF221E36)),
  )

  data class ArtistItemSimple(val name: String, val genre: String)
  val popularArtists = listOf(
    ArtistItemSimple("Taylor Swift", "Pop"),
    ArtistItemSimple("JVKE", "Pop / Indie"),
    ArtistItemSimple("The Weeknd", "R&B / Pop"),
    ArtistItemSimple("Billie Eilish", "Alt / Pop"),
    ArtistItemSimple("Ed Sheeran", "Pop / Acoustic"),
    ArtistItemSimple("Ariana Grande", "Pop"),
    ArtistItemSimple("Coldplay", "Rock / Alt"),
  )

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = contentPadding
  ) {
    item(key = "trending_title") {
      Text(
        text = "Trending Searches",
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        ),
        color = sectionTitleColor,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
      )
    }

    item(key = "trending_chips") {
      LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(trendingChips) { chip ->
          Surface(
            shape = RoundedCornerShape(100),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            onClick = { onSelectSearch(chip) }
          ) {
            Text(
              text = chip,
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
              color = sectionTitleColor,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
          }
        }
      }
    }

    item(key = "mood_title") {
      Spacer(Modifier.height(16.dp))
      Text(
        text = "Browse by Mood",
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        ),
        color = sectionTitleColor,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
      )
    }

    item(key = "mood_grid") {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        moodCards.chunked(2).forEach { rowMoods ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            rowMoods.forEach { mood ->
              Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isDark) mood.bgDark else mood.bgLight,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                  .weight(1f)
                  .height(84.dp)
                  .clickable { onSelectSearch("${mood.title} mood music") }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = mood.title,
                      style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                      ),
                      color = if (isDark) Color.White else Color(0xFF1E1B26)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                      text = mood.subtitle,
                      style = MaterialTheme.typography.bodySmall,
                      color = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0xFF555555),
                      maxLines = 1
                    )
                  }
                  Text(
                    text = mood.emoji,
                    fontSize = 26.sp
                  )
                }
              }
            }
          }
        }
      }
    }

    item(key = "popular_artists_title") {
      Spacer(Modifier.height(16.dp))
      Text(
        text = "Popular Artists",
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        ),
        color = sectionTitleColor,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
      )
    }

    item(key = "popular_artists_row") {
      LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(popularArtists) { artist ->
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .width(80.dp)
              .clickable { onSelectSearch(artist.name) }
          ) {
            Box(
              modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF332F44) else Color(0xFFECEAF8))
                .border(1.dp, cardBorder, CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = artist.name.take(1),
                style = MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.Bold,
                  fontSize = 24.sp
                ),
                color = if (isDark) Color.White else Color(0xFF4C3E72)
              )
            }
            Spacer(Modifier.height(6.dp))
            Text(
              text = artist.name,
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
              color = sectionTitleColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}

