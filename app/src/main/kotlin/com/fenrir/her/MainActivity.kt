package com.fenrir.her

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import com.fenrir.her.constants.*
import com.fenrir.her.constants.AiRecommendationsKey
import com.fenrir.her.constants.AppBarHeight
import com.fenrir.her.constants.AppLanguageKey
import com.fenrir.her.constants.DarkModeKey
import com.fenrir.her.constants.DefaultOpenTabKey
import com.fenrir.her.constants.DisableScreenshotKey
import com.fenrir.her.constants.DynamicThemeKey
import com.fenrir.her.constants.EnableHighRefreshRateKey
import com.fenrir.her.constants.FloatingToolbarBottomPadding
import com.fenrir.her.constants.FloatingToolbarHorizontalPadding
import com.fenrir.her.constants.ListenTogetherInTopBarKey
import com.fenrir.her.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.fenrir.her.ui.component.GlassEffectConfig
import com.fenrir.her.ui.component.GlassComponent
import com.fenrir.her.ui.component.LocalGlassEffectConfig
import com.fenrir.her.ui.component.LocalAppBackdrop
import com.fenrir.her.ui.component.liquidGlass
import com.fenrir.her.ui.component.backdrop.backdrops.layerBackdrop
import com.fenrir.her.constants.LiquidGlassGlobalEnabledKey
import com.fenrir.her.constants.LiquidGlassVibrancyKey
import com.fenrir.her.constants.LiquidGlassBlurRadiusKey
import com.fenrir.her.constants.LiquidGlassLensHeightKey
import com.fenrir.her.constants.LiquidGlassLensAmountKey
import com.fenrir.her.constants.LiquidGlassChromaticAberrationKey
import com.fenrir.her.constants.LiquidGlassDepthEffectKey
import com.fenrir.her.constants.LiquidGlassSurfaceTintColorKey
import com.fenrir.her.constants.LiquidGlassSurfaceOpacityKey
import com.fenrir.her.constants.LiquidGlassTextColorKey
import com.fenrir.her.constants.LiquidGlassPlayerEnabledKey
import com.fenrir.her.constants.LiquidGlassMiniPlayerEnabledKey
import com.fenrir.her.constants.LiquidGlassNavBarEnabledKey
import com.fenrir.her.constants.UseFloatingNavBarKey
import com.fenrir.her.constants.ListenTogetherUsernameKey
import com.fenrir.her.constants.MiniPlayerBottomSpacing
import com.fenrir.her.constants.MiniPlayerHeight
import com.fenrir.her.constants.NavigationBarAnimationSpec
import com.fenrir.her.constants.NavigationBarHeight
import com.fenrir.her.constants.PauseListenHistoryKey
import com.fenrir.her.constants.PauseSearchHistoryKey
import com.fenrir.her.constants.PureBlackKey
import com.fenrir.her.constants.SYSTEM_DEFAULT
import com.fenrir.her.constants.SelectedThemeColorKey
import com.fenrir.her.constants.StopMusicOnTaskClearKey
import com.fenrir.her.constants.*
import com.fenrir.her.constants.UseNewMiniPlayerDesignKey
import com.fenrir.her.db.MusicDatabase
import com.fenrir.her.db.entities.SearchHistory
import com.fenrir.her.echomusic.UpdateNotificationHelper
import com.fenrir.her.echomusic.updater.checkForUpdate
import com.fenrir.her.echomusic.updater.getAutoUpdateCheckSetting
import com.fenrir.her.echomusic.updater.getUpdateNotificationsSetting
import com.fenrir.her.echomusic.updater.saveUpdateAvailableState
import com.fenrir.her.extensions.toEnum
import com.fenrir.her.models.toMediaMetadata
import com.fenrir.her.playback.DownloadUtil
import com.fenrir.her.playback.MusicService
import com.fenrir.her.playback.MusicService.MusicBinder
import com.fenrir.her.playback.PlayerConnection
import com.fenrir.her.playback.queues.YouTubeQueue
import com.fenrir.her.ui.component.*
import com.fenrir.her.ui.component.AppFloatingNavBar
import com.fenrir.her.ui.component.RingtoneProgressDialog
import com.fenrir.her.ui.component.RingtoneTrimmerDialog
import com.fenrir.her.ui.component.floatingtabbar.rememberFloatingTabBarScrollConnection
import com.fenrir.her.ui.component.shimmer.getShimmerTheme
import com.fenrir.her.ui.menu.YouTubeSongMenu
import com.fenrir.her.ui.player.BottomSheetPlayer
import com.fenrir.her.ui.screens.Screens
import com.fenrir.her.ui.screens.SettingDialoge
import com.fenrir.her.ui.screens.WelcomeDialog
import com.fenrir.her.ui.screens.navigationBuilder
import com.fenrir.her.ui.screens.settings.DarkMode
import com.fenrir.her.ui.screens.settings.NavigationTab
import com.fenrir.her.ui.screens.settings.RingtoneViewModel
import com.fenrir.her.ui.theme.ColorSaver
import com.fenrir.her.ui.theme.DefaultThemeColor
import com.fenrir.her.ui.theme.echomusicTheme
import com.fenrir.her.ui.theme.extractThemeColor
import com.fenrir.her.ui.utils.appBarScrollBehavior
import com.fenrir.her.ui.utils.resetHeightOffset
import com.fenrir.her.utils.SyncUtils
import com.fenrir.her.utils.dataStore
import com.fenrir.her.utils.get
import com.fenrir.her.utils.rememberEnumPreference
import com.fenrir.her.utils.rememberPreference
import com.fenrir.her.utils.reportException
import com.fenrir.her.utils.setAppLocale
import com.fenrir.her.viewmodels.HomeViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  companion object {
    const val ACTION_SEARCH = "com.fenrir.her.action.SEARCH"
    const val ACTION_LIBRARY = "com.fenrir.her.action.LIBRARY"
    const val ACTION_RECOGNITION = "com.fenrir.her.action.RECOGNITION"
    const val EXTRA_AUTO_START_RECOGNITION = "auto_start_recognition"
  }

  @Inject lateinit var database: MusicDatabase

  @Inject lateinit var downloadUtil: DownloadUtil

  @Inject lateinit var syncUtils: SyncUtils

  @Inject
  lateinit var listenTogetherManager: com.fenrir.her.listentogether.ListenTogetherManager
  private lateinit var navController: NavHostController
  private var pendingIntent: Intent? = null

  private var playerConnection by mutableStateOf<PlayerConnection?>(null)

  private val serviceConnection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        if (service is MusicBinder) {
          try {
            playerConnection =
              PlayerConnection(this@MainActivity, service, database, lifecycleScope)
            Timber.tag("MainActivity").d("PlayerConnection created successfully")

            listenTogetherManager.setPlayerConnection(playerConnection)
          } catch (e: Exception) {
            Timber.tag("MainActivity").e(e, "Failed to create PlayerConnection")

            lifecycleScope.launch {
              delay(500)
              try {
                playerConnection =
                  PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                listenTogetherManager.setPlayerConnection(playerConnection)
              } catch (e2: Exception) {
                Timber.tag("MainActivity").e(e2, "Failed to create PlayerConnection on retry")
              }
            }
          }
        }
      }

      override fun onServiceDisconnected(name: ComponentName?) {

        listenTogetherManager.setPlayerConnection(null)
        playerConnection?.dispose()
        playerConnection = null
      }
    }

  override fun onStart() {
    super.onStart()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
      ) {
        ActivityCompat.requestPermissions(
          this,
          arrayOf(Manifest.permission.POST_NOTIFICATIONS),
          1000
        )
      }
    }

    bindService(Intent(this, MusicService::class.java), serviceConnection, BIND_AUTO_CREATE)
  }

  override fun onStop() {
    unbindService(serviceConnection)
    super.onStop()
  }

  override fun onDestroy() {
    super.onDestroy()
    if (
      dataStore.get(StopMusicOnTaskClearKey, false) &&
        playerConnection?.isPlaying?.value == true &&
        isFinishing
    ) {
      stopService(Intent(this, MusicService::class.java))
      unbindService(serviceConnection)
      playerConnection = null
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    if (::navController.isInitialized) {
      handleDeepLinkIntent(intent, navController)
      handleRecognitionIntent(intent, navController)
      handleAssistantSearchIntent(intent, navController)
      handlePushSongIntent(intent)
    } else {
      pendingIntent = intent
    }
  }

  private var isPlaying = false

  override fun startForegroundService(service: Intent): android.content.ComponentName? {
    return try {
      super.startForegroundService(service)
    } catch (e: Exception) {
      if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
          e is android.app.ForegroundServiceStartNotAllowedException
      ) {
        Timber.e(e, "Suppressed ForegroundServiceStartNotAllowedException in MainActivity")
        null
      } else {
        throw e
      }
    }
  }

  @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
  @OptIn(ExperimentalMaterial3Api::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)

    try {
      startService(Intent(this, com.fenrir.her.playback.MusicService::class.java))
    } catch (e: Exception) {
      timber.log.Timber.e(e, "Failed to pre-start MusicService for warmup")
    }

    window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
    WindowCompat.setDecorFitsSystemWindows(window, false)

    listenTogetherManager.initialize()

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      val locale =
        dataStore[AppLanguageKey]
          ?.takeUnless { it == SYSTEM_DEFAULT }
          ?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
      setAppLocale(this, locale)
    }

    if (java.io.File(filesDir, "clear_export_state").exists()) {
      lifecycleScope.launch {
        dataStore.edit { preferences ->
          preferences.remove(com.fenrir.her.constants.ExportingSongIdsKey)
          preferences.remove(com.fenrir.her.constants.ExportedSongIdsKey)
          preferences.remove(com.fenrir.her.constants.ExportProgressKey)
        }
        java.io.File(filesDir, "clear_export_state").delete()
      }
    }

    lifecycleScope.launch {
      dataStore.data
        .map {
          (try {
            it[DisableScreenshotKey]
          } catch (e: Exception) {
            null
          }) ?: false
        }
        .distinctUntilChanged()
        .collectLatest {
          if (it) {
            window.setFlags(
              WindowManager.LayoutParams.FLAG_SECURE,
              WindowManager.LayoutParams.FLAG_SECURE,
            )
          } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
          }
        }
    }

    lifecycleScope.launch {
      dataStore.data
        .map {
          (try {
            it[AiRecommendationsKey]
          } catch (e: Exception) {
            null
          }) ?: false
        }
        .distinctUntilChanged()
        .collectLatest { enabled ->
          val workManager = androidx.work.WorkManager.getInstance(this@MainActivity)
          if (enabled) {
            val request =
              androidx.work
                .PeriodicWorkRequestBuilder<com.fenrir.her.ai.AiRecommendationWorker>(
                  1,
                  java.util.concurrent.TimeUnit.DAYS
                )
                .setConstraints(
                  androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
                )
                .build()
            workManager.enqueueUniquePeriodicWork(
              "AiRecommendationWorker",
              androidx.work.ExistingPeriodicWorkPolicy.KEEP,
              request
            )
          } else {
            workManager.cancelUniqueWork("AiRecommendationWorker")
          }
        }
    }

    setContent {
      HerMusicApp(
        playerConnection = playerConnection,
        database = database,
        downloadUtil = downloadUtil,
        syncUtils = syncUtils,
      )
    }
  }

  @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
  @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
  @Composable
  private fun HerMusicApp(
    playerConnection: PlayerConnection?,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    syncUtils: SyncUtils,
  ) {
    val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
    val enableHighRefreshRate by rememberPreference(EnableHighRefreshRateKey, defaultValue = true)
    val context = LocalContext.current
    var showUpdateDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    var availableUpdateVersion by remember { androidx.compose.runtime.mutableStateOf("") }
    var availableUpdateChangelog by remember {
      androidx.compose.runtime.mutableStateOf<
        List<com.fenrir.her.echomusic.updater.ChangelogSection>
      >(
        emptyList()
      )
    }
    var availableUpdateDescription by remember {
      androidx.compose.runtime.mutableStateOf<String?>(null)
    }
    var whatsNewInfo by remember {
      androidx.compose.runtime.mutableStateOf<com.fenrir.her.echomusic.updater.WhatsNewInfo?>(
        null
      )
    }

    LaunchedEffect(Unit) {
      val currentVersion = BuildConfig.VERSION_NAME
      val lastSeenVersion =
        com.fenrir.her.echomusic.updater.getLastSeenChangelogVersion(context)
        
      if (lastSeenVersion.isEmpty() || lastSeenVersion != currentVersion) {
        // Run migrations for fresh installs and updates
        context.dataStore.edit { preferences ->
          preferences[com.fenrir.her.constants.ForceOpusKey] = true
          preferences[com.fenrir.her.constants.EnableCronetKey] = true
        }
      }

      if (lastSeenVersion.isEmpty()) {
        // Fresh install, not an update — nothing "new" to show, so mark this
        // version seen right away rather than waiting on a dialog dismissal.
        com.fenrir.her.echomusic.updater.saveLastSeenChangelogVersion(context, currentVersion)
      } else if (lastSeenVersion != currentVersion) {
        // Only mark the version seen once its changelog is actually shown (see
        // onDismiss below) — if the fetch fails here, retry on the next launch
        // instead of losing that version's release notes forever.
        whatsNewInfo = com.fenrir.her.echomusic.updater.fetchChangelogForVersion(currentVersion)
      }
    }

    LaunchedEffect(Unit) {
      val prefs = context.dataStore.data.first()

      if (getAutoUpdateCheckSetting(context)) {

        delay(2000L)
        checkForUpdate(
          context = context,
          onSuccess = { latestVersion, isAvailable, changelog, _, _, description, _, _ ->
            val currentVersion = BuildConfig.VERSION_NAME
            Log.d(
              "UpdateCheck",
              "Startup check success. Latest: $latestVersion, Current: $currentVersion, isAvailable: $isAvailable"
            )
            saveUpdateAvailableState(context, isAvailable)

            if (isAvailable) {
              availableUpdateVersion = latestVersion
              availableUpdateChangelog = changelog
              availableUpdateDescription = description
              showUpdateDialog = false
            }

            if (isAvailable && getUpdateNotificationsSetting(context)) {
              Log.d("UpdateCheck", "Posting update notification for $latestVersion")
              UpdateNotificationHelper.showUpdateNotification(context, latestVersion)
            }
          },
          onError = { Log.e("UpdateCheck", "Startup check failed") }
        )
      }
    }

    LaunchedEffect(enableHighRefreshRate) {
      val window = this@MainActivity.window
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val layoutParams = window.attributes
        if (enableHighRefreshRate) {
          layoutParams.preferredDisplayModeId = 0
        } else {
          val modes = window.windowManager.defaultDisplay.supportedModes
          val mode60 =
            modes.firstOrNull { kotlin.math.abs(it.refreshRate - 60f) < 1f }
              ?: modes.minByOrNull { kotlin.math.abs(it.refreshRate - 60f) }

          if (mode60 != null) {
            layoutParams.preferredDisplayModeId = mode60.modeId
          }
        }
        window.attributes = layoutParams
      } else {
        val params = window.attributes
        if (enableHighRefreshRate) {
          params.preferredRefreshRate = 0f
        } else {
          params.preferredRefreshRate = 60f
        }
        window.attributes = params
      }
    }

    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme =
      remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
      }

    LaunchedEffect(useDarkTheme) { setSystemBarAppearance(useDarkTheme) }

    val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
    val pureBlack = remember(pureBlackEnabled, useDarkTheme) { pureBlackEnabled && useDarkTheme }

    val (selectedThemeColorInt) =
      rememberPreference(SelectedThemeColorKey, defaultValue = DefaultThemeColor.toArgb())
    val selectedThemeColor = Color(selectedThemeColorInt)

    var themeColor by
      rememberSaveable(stateSaver = ColorSaver) { mutableStateOf(selectedThemeColor) }

    LaunchedEffect(selectedThemeColor) {
      if (!enableDynamicTheme) {
        themeColor = selectedThemeColor
      }
    }

    LaunchedEffect(playerConnection, enableDynamicTheme, selectedThemeColor) {
      val playerConnection = playerConnection
      if (!enableDynamicTheme || playerConnection == null) {
        themeColor = selectedThemeColor
        return@LaunchedEffect
      }

      playerConnection.service.currentMediaMetadata.collectLatest { song ->
        if (song?.thumbnailUrl != null) {
          withContext(Dispatchers.IO) {
            try {
              val result =
                imageLoader.execute(
                  ImageRequest.Builder(this@MainActivity)
                    .data(song.thumbnailUrl)
                    .allowHardware(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .crossfade(false)
                    .build()
                )
              themeColor = result.image?.toBitmap()?.extractThemeColor() ?: selectedThemeColor
            } catch (e: Exception) {

              themeColor = selectedThemeColor
            }
          }
        } else {
          themeColor = selectedThemeColor
        }
      }
    }

    val (enableHaptics) =
      rememberPreference(com.fenrir.her.constants.EnableHapticsKey, defaultValue = false)
    val view = LocalView.current
    var lastScrollHapticTime by remember { mutableStateOf(0L) }

    echomusicTheme(
      darkTheme = useDarkTheme,
      pureBlack = pureBlack,
      themeColor = themeColor,
    ) {
      if (false && showUpdateDialog) {
        com.fenrir.her.echomusic.component.UpdateAvailableDialog(
          version = availableUpdateVersion,
          changelog = availableUpdateChangelog,
          description = availableUpdateDescription,
          onDismiss = { showUpdateDialog = false }
        )
      } else {
        whatsNewInfo?.let { info ->
          com.fenrir.her.echomusic.updater.WhatsNewDialog(
            version = BuildConfig.VERSION_NAME,
            info = info,
            onDismiss = {
              com.fenrir.her.echomusic.updater.saveLastSeenChangelogVersion(
                context,
                BuildConfig.VERSION_NAME,
              )
              whatsNewInfo = null
            }
          )
        }
      }
      BoxWithConstraints(
        modifier =
          Modifier.fillMaxSize()
            .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
            .pointerInput(enableHaptics) {
              if (enableHaptics) {
                awaitPointerEventScope {
                  while (true) {
                    val event =
                      awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    val isClick = event.changes.any { it.changedToDown() }
                    val isScroll =
                      event.changes.any { it.positionChange() != Offset.Zero && it.pressed }
                    if (isClick) {
                      view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    } else if (isScroll) {
                      val currentTime = System.currentTimeMillis()
                      if (currentTime - lastScrollHapticTime > 100) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        lastScrollHapticTime = currentTime
                      }
                    }
                  }
                }
              }
            }
      ) {
        val focusManager = LocalFocusManager.current
        val density = LocalDensity.current
        val configuration = LocalWindowInfo.current
        val cutoutInsets = WindowInsets.displayCutout
        val windowsInsets = WindowInsets.systemBars
        val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
        val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

        val navController = rememberNavController()
        val homeViewModel: HomeViewModel = hiltViewModel()
        val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val onRailSearchLongClick: () -> Unit =
          remember(navController) {
            { } // User requested to disable opening the recognize music page on long press
          }

        val (previousTab, setPreviousTab) = rememberSaveable { mutableStateOf("home") }

        val (listenTogetherInTopBar) =
          rememberPreference(ListenTogetherInTopBarKey, defaultValue = true)
        val navigationItems = remember { Screens.MainScreens }
        val (useNewMiniPlayerDesign) =
          rememberPreference(UseNewMiniPlayerDesignKey, defaultValue = true)
        val defaultOpenTab = remember {
          dataStore[DefaultOpenTabKey].toEnum(defaultValue = NavigationTab.HOME)
        }
        val tabOpenedFromShortcut = remember {
          when (intent?.action) {
            ACTION_SEARCH -> NavigationTab.LIBRARY
            ACTION_LIBRARY -> NavigationTab.SEARCH
            else -> null
          }
        }

        val navigateTo = remember { intent?.getStringExtra("navigate_to") }
        val openCoupleSetup = remember { intent?.getBooleanExtra("open_couple_setup", false) == true }
        LaunchedEffect(navigateTo, openCoupleSetup) {
          if (navigateTo == "couple_space" || openCoupleSetup) {
            navController.navigate(Screens.CoupleSpace.route)
          }
        }

        val topLevelScreens = remember {
          listOf(
            Screens.Home.route,
            Screens.Downloads.route,
            Screens.CoupleSpace.route,
            Screens.Search.route,
            Screens.Library.route,
            Screens.ListenTogether.route,
            "settings",
          )
        }

        val (query, onQueryChange) =
          rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

        val onSearch: (String) -> Unit = remember {
          { searchQuery ->
            if (searchQuery.isNotEmpty()) {
              navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}")

              if (dataStore[PauseSearchHistoryKey] != true) {
                lifecycleScope.launch(Dispatchers.IO) {
                  database.query { insert(SearchHistory(query = searchQuery)) }
                }
              }
            }
          }
        }

        val currentRoute by remember { derivedStateOf { navBackStackEntry?.destination?.route } }

        val inSearchScreen by remember {
          derivedStateOf { currentRoute?.startsWith("search/") == true }
        }
        val navigationItemRoutes =
          remember(navigationItems) { navigationItems.map { it.route }.toSet() }

        val shouldShowNavigationBar =
          remember(currentRoute, navigationItemRoutes) {
            currentRoute == null ||
              navigationItemRoutes.contains(currentRoute) ||
              currentRoute!!.startsWith("search/") ||
              currentRoute!!.startsWith("album/") ||
              currentRoute!!.startsWith("online_playlist/") ||
              currentRoute!!.startsWith("local_playlist/") ||
              currentRoute!!.startsWith("artist/")
          }

        val isLandscape = configuration.containerDpSize.width > configuration.containerDpSize.height

        val showRail = isLandscape && !inSearchScreen && currentRoute != "ambient_mode"

        val navPadding =
          if (shouldShowNavigationBar && !showRail) {
            NavigationBarHeight + FloatingToolbarBottomPadding
          } else {
            0.dp
          }

        val navigationBarHeight by
          animateDpAsState(
            targetValue = if (shouldShowNavigationBar && !showRail) NavigationBarHeight else 0.dp,
            animationSpec = NavigationBarAnimationSpec,
            label = "navBarHeight",
          )

        val (useFloatingNavBar) = rememberPreference(UseFloatingNavBarKey, defaultValue = false)
        val floatingNavBarScrollConnection = rememberFloatingTabBarScrollConnection()

        val playerBottomSheetState =
          rememberBottomSheetState(
            dismissedBound = 0.dp,
            collapsedBound =
              if (useFloatingNavBar && !showRail && shouldShowNavigationBar) {
                0.dp
              } else {
                bottomInset +
                  (if (!showRail && shouldShowNavigationBar) navPadding else 0.dp) +
                  (if (useNewMiniPlayerDesign) MiniPlayerBottomSpacing else 0.dp) +
                  MiniPlayerHeight
              },
            expandedBound = maxHeight,
          )

        val onShuffleClick: (() -> Unit)? =
          remember(playerConnection, playerBottomSheetState) {
            playerConnection?.let { connection ->
              {
                if (playerBottomSheetState.isExpanded) {
                  playerBottomSheetState.collapseSoft()
                }
                connection.player.shuffleModeEnabled = !connection.player.shuffleModeEnabled
              }
            }
          }
        val shuffleEnabled by
          playerConnection?.shuffleModeEnabled?.collectAsState()
            ?: remember { mutableStateOf(false) }

        val onMusicRecognitionClick: (() -> Unit) =
          remember(navController, playerBottomSheetState) {
            {
              if (playerBottomSheetState.isExpanded) {
                playerBottomSheetState.collapseSoft()
              }
              navController.navigate("recognition") { launchSingleTop = true }
            }
          }

        val playerMediaMetadata = playerConnection?.player?.currentMediaItem?.mediaMetadata
        val hasDockedPlayerAccessory =
          useFloatingNavBar && playerMediaMetadata != null && !showRail && shouldShowNavigationBar

        val playerAwareWindowInsets =
          remember(
            bottomInset,
            shouldShowNavigationBar,
            playerBottomSheetState.isDismissed,
            showRail,
            hasDockedPlayerAccessory,
          ) {
            var bottom = bottomInset
            if (shouldShowNavigationBar && !showRail) {
              bottom += NavigationBarHeight
            }
            if (!playerBottomSheetState.isDismissed || hasDockedPlayerAccessory) bottom += MiniPlayerHeight
            windowsInsets
              .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
              .add(WindowInsets(top = AppBarHeight, bottom = bottom))
          }
        appBarScrollBehavior(
          canScroll = {
            !inSearchScreen &&
              (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
          }
        )

        val topAppBarScrollBehavior =
          appBarScrollBehavior(
            canScroll = {
              !inSearchScreen &&
                (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
            },
          )

        LaunchedEffect(navBackStackEntry) {
          if (inSearchScreen) {
            val searchQuery =
              withContext(Dispatchers.IO) {
                val rawQuery = navBackStackEntry?.arguments?.getString("query")!!
                try {
                  URLDecoder.decode(rawQuery, "UTF-8")
                } catch (e: IllegalArgumentException) {
                  rawQuery
                }
              }
            onQueryChange(TextFieldValue(searchQuery, TextRange(searchQuery.length)))
          } else if (
            navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }
          ) {
            onQueryChange(TextFieldValue())
          }

          if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
            if (navigationItems.fastAny { it.route == previousTab }) {
              topAppBarScrollBehavior.state.resetHeightOffset()
            }
          }

          topAppBarScrollBehavior.state.resetHeightOffset()

          navController.currentBackStackEntry?.destination?.route?.let { setPreviousTab(it) }
        }

        LaunchedEffect(playerConnection) {
          val player = playerConnection?.player ?: return@LaunchedEffect
          if (player.currentMediaItem == null) {
            if (!playerBottomSheetState.isDismissed) {
              playerBottomSheetState.dismiss()
            }
          } else {
            if (playerBottomSheetState.isDismissed) {
              playerBottomSheetState.collapseSoft()
            }
          }
        }

        val (isCouplePaired) = rememberPreference(CoupleIsPairedKey, false)
        val (coupleSpaceId) = rememberPreference(CoupleSpaceIdKey, "")
        val (coupleRolePref) = rememberPreference(CoupleRoleKey, "HIM")
        val (customCoupleMyName) = rememberPreference(CoupleMyNameKey, "")
        val (customCoupleFirebaseUrl) = rememberPreference(CoupleFirebaseUrlKey, "")
        val (coupleAccountEmail) = rememberPreference(AccountEmailKey, "")
        val coupleRole = remember(coupleAccountEmail, coupleRolePref) {
          if (coupleRolePref == "HER" || coupleRolePref == "RITIKA") com.fenrir.her.auth.CoupleAuthGuard.CoupleRole.HER
          else com.fenrir.her.auth.CoupleAuthGuard.CoupleRole.HIM
        }
        val coupleMyName = com.fenrir.her.auth.CoupleAuthGuard.getMyName(coupleRole, customCoupleMyName)

        LaunchedEffect(isCouplePaired, coupleSpaceId, coupleRole, coupleMyName, playerConnection) {
          if (isCouplePaired && coupleSpaceId.isNotBlank()) {
            com.fenrir.her.couple.CouplePlaybackSyncManager.startBroadcaster(
              scope = this,
              spaceId = coupleSpaceId,
              role = coupleRole,
              myName = coupleMyName,
              playerConnection = playerConnection,
              customUrl = customCoupleFirebaseUrl
            )
          } else {
            com.fenrir.her.couple.CouplePlaybackSyncManager.stopBroadcaster()
          }
        }

        DisposableEffect(playerConnection, playerBottomSheetState) {
          val player = playerConnection?.player ?: return@DisposableEffect onDispose {}
          val listener =
            object : Player.Listener {
              override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
              ) {
                if (
                  reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                    mediaItem != null &&
                    playerBottomSheetState.isDismissed
                ) {
                  playerBottomSheetState.collapseSoft()
                }
              }
            }
          player.addListener(listener)
          onDispose { player.removeListener(listener) }
        }

        var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(navBackStackEntry, listenTogetherInTopBar) {
          val currentRoute = navBackStackEntry?.destination?.route
          val isListenTogetherScreen =
            currentRoute == Screens.ListenTogether.route ||
              currentRoute == "listen_together_from_topbar"
          shouldShowTopBar =
            currentRoute in topLevelScreens &&
              currentRoute != "settings" &&
              currentRoute != "couple_space" &&
              currentRoute != Screens.Search.route &&
              !(isListenTogetherScreen && listenTogetherInTopBar)
        }

        val coroutineScope = rememberCoroutineScope()
        var sharedSong: SongItem? by remember { mutableStateOf(null) }
        val snackbarHostState = remember { SnackbarHostState() }
        var showSettingDialoge by remember { mutableStateOf(false) }

        val (lastOpenedVersionCode, setLastOpenedVersionCode) =
          rememberPreference(com.fenrir.her.constants.LastOpenedVersionCodeKey, -1)
        var showWelcomeDialog by remember { mutableStateOf(false) }

        LaunchedEffect(lastOpenedVersionCode) {
          if (lastOpenedVersionCode < BuildConfig.VERSION_CODE) {
            setLastOpenedVersionCode(BuildConfig.VERSION_CODE)
          }
        }

        LaunchedEffect(Unit) {
          if (pendingIntent != null) {
            handleDeepLinkIntent(pendingIntent!!, navController)
            handleRecognitionIntent(pendingIntent!!, navController)
            handleAssistantSearchIntent(pendingIntent!!, navController)
            handlePushSongIntent(pendingIntent!!)
            pendingIntent = null
          } else if (
            intent != null &&
              (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND)
          ) {
            handleDeepLinkIntent(intent, navController)
          } else if (intent != null && intent.action == ACTION_RECOGNITION) {
            handleRecognitionIntent(intent, navController)
          } else if (
            intent != null &&
              intent.action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
          ) {
            handleAssistantSearchIntent(intent, navController)
          } else if (intent != null && intent.action == "com.fenrir.her.PLAY_PUSHED_SONG") {
            handlePushSongIntent(intent)
          }
        }

        DisposableEffect(Unit) {
          val listener =
            Consumer<Intent> { intent ->
              if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND) {
                handleDeepLinkIntent(intent, navController)
              } else if (intent.action == ACTION_RECOGNITION) {
                handleRecognitionIntent(intent, navController)
              } else if (
                intent.action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
              ) {
                handleAssistantSearchIntent(intent, navController)
              } else if (intent.action == "com.fenrir.her.PLAY_PUSHED_SONG") {
                handlePushSongIntent(intent)
              }
            }

          addOnNewIntentListener(listener)
          onDispose { removeOnNewIntentListener(listener) }
        }

        val currentTitle =
          when (navBackStackEntry?.destination?.route) {
            Screens.Home.route -> "Her"
            Screens.Downloads.route -> stringResource(R.string.music)
            Screens.CoupleSpace.route -> "Her & Him"
            Screens.Search.route -> stringResource(R.string.search)
            Screens.Library.route -> stringResource(R.string.filter_library)
            Screens.ListenTogether.route -> stringResource(R.string.together)
            else -> ""
          }

        val pauseListenHistory by rememberPreference(PauseListenHistoryKey, defaultValue = false)
        val eventCount by database.eventCount().collectAsState(initial = 0)
        val showHistoryButton =
          remember(pauseListenHistory, eventCount) { !(pauseListenHistory && eventCount == 0) }

        val baseBg = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer

        val (liquidGlassGlobalEnabled) = rememberPreference(LiquidGlassGlobalEnabledKey, defaultValue = false)
        val (liquidGlassVibrancy) = rememberPreference(LiquidGlassVibrancyKey, defaultValue = 1f)
        val (liquidGlassBlurRadius) = rememberPreference(LiquidGlassBlurRadiusKey, defaultValue = 8f)
        val (liquidGlassLensHeight) = rememberPreference(LiquidGlassLensHeightKey, defaultValue = 0.5f)
        val (liquidGlassLensAmount) = rememberPreference(LiquidGlassLensAmountKey, defaultValue = 0.5f)
        val (liquidGlassChromaticAberration) = rememberPreference(LiquidGlassChromaticAberrationKey, defaultValue = true)
        val (liquidGlassDepthEffect) = rememberPreference(LiquidGlassDepthEffectKey, defaultValue = true)
        val (liquidGlassSurfaceTintColorInt) = rememberPreference(LiquidGlassSurfaceTintColorKey, defaultValue = 0)
        val (liquidGlassSurfaceOpacity) = rememberPreference(LiquidGlassSurfaceOpacityKey, defaultValue = 0.4f)
        val (liquidGlassTextColorInt) = rememberPreference(LiquidGlassTextColorKey, defaultValue = 0)
        val (liquidGlassPlayerEnabled) = rememberPreference(LiquidGlassPlayerEnabledKey, defaultValue = true)
        val (liquidGlassMiniPlayerEnabled) = rememberPreference(LiquidGlassMiniPlayerEnabledKey, defaultValue = true)
        val (liquidGlassNavBarEnabled) = rememberPreference(LiquidGlassNavBarEnabledKey, defaultValue = true)

        val glassEffectConfig = remember(
            liquidGlassGlobalEnabled, useFloatingNavBar, liquidGlassVibrancy, liquidGlassBlurRadius,
            liquidGlassLensHeight, liquidGlassLensAmount, liquidGlassChromaticAberration,
            liquidGlassDepthEffect, liquidGlassSurfaceTintColorInt,
            liquidGlassSurfaceOpacity, liquidGlassTextColorInt, liquidGlassPlayerEnabled,
            liquidGlassMiniPlayerEnabled, liquidGlassNavBarEnabled,
        ) {
            GlassEffectConfig(
                globalEnabled = liquidGlassGlobalEnabled,
                vibrancy = liquidGlassVibrancy,
                blurRadius = liquidGlassBlurRadius,
                lensHeight = liquidGlassLensHeight,
                lensAmount = liquidGlassLensAmount,
                chromaticAberration = liquidGlassChromaticAberration,
                depthEffect = liquidGlassDepthEffect,
                surfaceTintColor = if (liquidGlassSurfaceTintColorInt == 0) Color.Unspecified else Color(liquidGlassSurfaceTintColorInt),
                surfaceOpacity = liquidGlassSurfaceOpacity,
                textColor = if (liquidGlassTextColorInt == 0) Color.Unspecified else Color(liquidGlassTextColorInt),
                playerEnabled = liquidGlassPlayerEnabled,
                miniPlayerEnabled = liquidGlassMiniPlayerEnabled,
                navBarEnabled = liquidGlassNavBarEnabled,
            )
        }
        
        val appBackdrop = rememberLayerBackdrop {
            drawRect(baseBg)
            drawContent()
        }

        val ringtoneViewModel: RingtoneViewModel = viewModel()
        val ringtoneUiState by ringtoneViewModel.uiState.collectAsState()

        CompositionLocalProvider(
          LocalGlassEffectConfig provides glassEffectConfig,
          LocalAppBackdrop provides appBackdrop,
          LocalRingtoneViewModel provides ringtoneViewModel,
          LocalDatabase provides database,
          LocalContentColor provides
            if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
          LocalPlayerConnection provides playerConnection,
          LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
          LocalDownloadUtil provides downloadUtil,
          LocalShimmerTheme provides getShimmerTheme(),
          LocalSyncUtils provides syncUtils,
          LocalListenTogetherManager provides listenTogetherManager,
        ) {
          Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
              AnimatedVisibility(
                visible = shouldShowTopBar,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = fadeOut(animationSpec = tween(durationMillis = 200))
              ) {
                Row {
                  TopAppBar(
                    title = {
                      Text(
                        text = currentTitle,
                        style =
                          MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                          ),
                      )
                    },
                    actions = {
                      if (showHistoryButton) {
                        IconButton(onClick = { navController.navigate("history") }) {
                          Icon(
                            painter = painterResource(R.drawable.music_history),
                            contentDescription = stringResource(R.string.history)
                          )
                        }
                      }
                      IconButton(onClick = { navController.navigate("stats") }) {
                        Icon(
                          painter = painterResource(R.drawable.stats),
                          contentDescription = stringResource(R.string.stats)
                        )
                      }
                      IconButton(
                        onClick = { navController.navigate("couple_space") }
                      ) {
                        Icon(
                          painter = painterResource(R.drawable.favorite),
                          contentDescription = "Her & Him",
                          tint = Color(0xFFFF4081)
                        )
                      }
                      IconButton(onClick = { showSettingDialoge = true }) {
                        BadgedBox(badge = {}) {
                          if (accountImageUrl != null) {
                            AsyncImage(
                              model = accountImageUrl,
                              contentDescription = stringResource(R.string.account),
                              modifier = Modifier.size(24.dp).clip(CircleShape)
                            )
                          } else {
                            Icon(
                              painter = painterResource(R.drawable.settings),
                              contentDescription = stringResource(R.string.account),
                              modifier = Modifier.size(24.dp)
                            )
                          }
                        }
                      }
                    },
                    scrollBehavior = topAppBarScrollBehavior,
                    colors =
                      TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                      ),
                    windowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Top),
                    modifier =
                      Modifier.windowInsetsPadding(
                        if (showRail) {
                          WindowInsets(left = NavigationBarHeight)
                            .add(cutoutInsets.only(WindowInsetsSides.Start))
                        } else {
                          cutoutInsets.only(WindowInsetsSides.Start + WindowInsetsSides.End)
                        }
                      )
                  )
                }
              }
            },
            bottomBar = {
              val onNavItemClick: (Screens, Boolean) -> Unit =
                remember(
                  navController,
                  coroutineScope,
                  topAppBarScrollBehavior,
                  playerBottomSheetState
                ) {
                  { screen: Screens, isSelected: Boolean ->
                    if (playerBottomSheetState.isExpanded) {
                      playerBottomSheetState.collapseSoft()
                    }

                    if (screen == Screens.Home) {
                      if (currentRoute == Screens.Home.route) {
                        navController.currentBackStackEntry
                          ?.savedStateHandle
                          ?.set("scrollToTop", true)
                        coroutineScope.launch { topAppBarScrollBehavior.state.resetHeightOffset() }
                      } else {
                        val popped = navController.popBackStack(Screens.Home.route, inclusive = false)
                        if (!popped) {
                          navController.navigate(Screens.Home.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                          }
                        }
                      }
                    } else if (isSelected) {
                      navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("scrollToTop", true)
                      coroutineScope.launch { topAppBarScrollBehavior.state.resetHeightOffset() }
                    } else {
                      navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                      }
                    }
                  }
                }

              if (
                !showRail &&
                  currentRoute != "update" &&
                  currentRoute != "listen_together/chat" &&
                  currentRoute != "ambient_mode" &&
                  currentRoute != "uptime" &&
                  currentRoute?.startsWith("settings") != true
              ) {
                Box {
                  BottomSheetPlayer(
                    state = playerBottomSheetState,
                    navController = navController,
                    pureBlack = pureBlack
                  )

                  val navSlideDistance =
                    bottomInset + FloatingToolbarBottomPadding + NavigationBarHeight

                  val navOffsetY =
                    if (navigationBarHeight == 0.dp) {
                      navSlideDistance
                    } else {
                      val slideOffset =
                        navSlideDistance * playerBottomSheetState.progress.coerceIn(0f, 1f)
                      val hideOffset =
                        navSlideDistance *
                          (1 -
                            navigationBarHeight.coerceAtMost(NavigationBarHeight) /
                              NavigationBarHeight)
                      slideOffset + hideOffset
                    }

                  if (useFloatingNavBar) {
                    AppFloatingNavBar(
                      navigationItems = navigationItems,
                      currentRoute = currentRoute,
                      onItemClick = onNavItemClick,
                      scrollConnection = floatingNavBarScrollConnection,
                      pureBlack = pureBlack,
                      showPlayerAccessory = hasDockedPlayerAccessory,
                      onAccessoryClick = { playerBottomSheetState.expandSoft() },
                      onMusicRecognitionClick = onMusicRecognitionClick,
                      musicRecognitionContentDescription = stringResource(R.string.recognition),
                      onSearchLongClick = onRailSearchLongClick,
                      modifier =
                        Modifier.align(Alignment.BottomCenter)
                          .padding(horizontal = 16.dp)
                          .padding(bottom = bottomInset + 8.dp)
                          .graphicsLayer {
                            val hiddenOffset = size.height + (bottomInset + 8.dp).toPx()
                            val navBarHeightPx = navigationBarHeight.toPx()
                            translationY =
                              if (navBarHeightPx == 0f) {
                                hiddenOffset
                              } else {
                                val progress = playerBottomSheetState.progress.coerceIn(0f, 1f)
                                val slideOffset = hiddenOffset * progress
                                val hideOffset =
                                  hiddenOffset * (1 - navBarHeightPx / NavigationBarHeight.toPx())
                                slideOffset + hideOffset
                              }
                          }
                    )
                  } else {
                    Box(
                      modifier =
                        Modifier.align(Alignment.BottomCenter)
                          .height(navSlideDistance)
                          .offset(y = navOffsetY),
                    ) {
                      FloatingNavigationToolbar(
                        items = navigationItems,
                        pureBlack = pureBlack,
                        onShuffleClick = onShuffleClick,
                        shuffleEnabled = shuffleEnabled,
                        shuffleIconRes = R.drawable.shuffle,
                        shuffleContentDescription = stringResource(R.string.shuffle),
                        onMusicRecognitionClick = onMusicRecognitionClick,
                        musicRecognitionContentDescription = stringResource(R.string.recognition),
                        onAiHubClick = {
                          navController.navigate("settings/ai") { launchSingleTop = true }
                        },
                        aiHubIconRes = R.drawable.sparks,
                        aiHubContentDescription = stringResource(R.string.ai_lyrics_translation),
                        onSearchLongClick = onRailSearchLongClick,
                        isSelected = { screen ->
                          currentRoute == screen.route ||
                            currentRoute?.startsWith("${screen.route}/") == true
                        },
                        onItemClick = onNavItemClick,
                        modifier =
                          Modifier.align(Alignment.BottomCenter)
                            .padding(
                              start = FloatingToolbarHorizontalPadding,
                              end = FloatingToolbarHorizontalPadding,
                              bottom = bottomInset + FloatingToolbarBottomPadding,
                            )
                            .height(NavigationBarHeight)
                      )
                    }

                    Box(
                      modifier =
                        Modifier.fillMaxWidth()
                          .align(Alignment.BottomCenter)
                          .height(bottomInsetDp)
                          .graphicsLayer {
                            val progress = playerBottomSheetState.progress
                            alpha =
                              if (
                                progress > 0f ||
                                  (useNewMiniPlayerDesign && !shouldShowNavigationBar)
                              )
                                0f
                              else 1f
                          }
                          .background(baseBg)
                    )
                  }
                }
              } else {
                if (
                  currentRoute != "update" &&
                    currentRoute != "listen_together/chat" &&
                    currentRoute != "ambient_mode" &&
                    currentRoute != "uptime" &&
                    currentRoute?.startsWith("settings") != true
                ) {
                  BottomSheetPlayer(
                    state = playerBottomSheetState,
                    navController = navController,
                    pureBlack = pureBlack
                  )
                }

                Box(
                  modifier =
                    Modifier.fillMaxWidth()
                      .align(Alignment.BottomCenter)
                      .height(bottomInsetDp)
                      .graphicsLayer {
                        val progress = playerBottomSheetState.progress
                        alpha =
                          if (progress > 0f || (useNewMiniPlayerDesign && !shouldShowNavigationBar))
                            0f
                          else 1f
                      }
                      .background(baseBg)
                )
              }
            },
            modifier =
              Modifier.fillMaxSize()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .then(
                  if (useFloatingNavBar) {
                    Modifier.nestedScroll(floatingNavBarScrollConnection)
                  } else {
                    Modifier
                  }
                )
          ) {
            Row(Modifier.fillMaxSize()) {
              val onRailItemClick: (Screens, Boolean) -> Unit =
                remember(
                  navController,
                  coroutineScope,
                  topAppBarScrollBehavior,
                  playerBottomSheetState
                ) {
                  { screen: Screens, isSelected: Boolean ->
                    if (playerBottomSheetState.isExpanded) {
                      playerBottomSheetState.collapseSoft()
                    }

                    if (screen == Screens.Home) {
                      if (currentRoute == Screens.Home.route) {
                        navController.currentBackStackEntry
                          ?.savedStateHandle
                          ?.set("scrollToTop", true)
                        coroutineScope.launch { topAppBarScrollBehavior.state.resetHeightOffset() }
                      } else {
                        val popped = navController.popBackStack(Screens.Home.route, inclusive = false)
                        if (!popped) {
                          navController.navigate(Screens.Home.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                          }
                        }
                      }
                    } else if (isSelected) {
                      navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("scrollToTop", true)
                      coroutineScope.launch { topAppBarScrollBehavior.state.resetHeightOffset() }
                    } else {
                      navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                      }
                    }
                  }
                }



              if (showRail && currentRoute != "update") {
                AppNavigationRail(
                  navigationItems = navigationItems,
                  currentRoute = currentRoute,
                  onItemClick = onRailItemClick,
                  pureBlack = pureBlack,
                  onSearchLongClick = onRailSearchLongClick,
                  onMusicRecognitionClick = onMusicRecognitionClick,
                  musicRecognitionContentDescription = stringResource(R.string.recognition),
                  onShuffleClick = onShuffleClick,
                  shuffleEnabled = shuffleEnabled,
                  shuffleIconRes = R.drawable.shuffle,
                  shuffleContentDescription = stringResource(R.string.shuffle),
                  onAiHubClick = {
                    navController.navigate("settings/ai") { launchSingleTop = true }
                  },
                  aiHubIconRes = R.drawable.sparks,
                  aiHubContentDescription = stringResource(R.string.ai_lyrics_translation)
                )
              }
              Box(Modifier.weight(1f)) {
                NavHost(
                  navController = navController,
                  startDestination =
                    when (tabOpenedFromShortcut ?: defaultOpenTab) {
                      NavigationTab.HOME -> Screens.Home
                      NavigationTab.LIBRARY -> Screens.Library
                      else -> Screens.Home
                    }.route,
                  enterTransition = {
                    val currentRouteIndex =
                      navigationItems.indexOfFirst { it.route == targetState.destination.route }
                    val previousRouteIndex =
                      navigationItems.indexOfFirst { it.route == initialState.destination.route }

                    if (currentRouteIndex == -1 || currentRouteIndex > previousRouteIndex)
                      slideInHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                        it / 8
                      } + fadeIn(tween(400, easing = EmphasizedEasing))
                    else
                      slideInHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                        -it / 8
                      } + fadeIn(tween(400, easing = EmphasizedEasing))
                  },
                  exitTransition = {
                    val currentRouteIndex =
                      navigationItems.indexOfFirst { it.route == initialState.destination.route }
                    val targetRouteIndex =
                      navigationItems.indexOfFirst { it.route == targetState.destination.route }

                    if (targetRouteIndex == -1 || targetRouteIndex > currentRouteIndex)
                      slideOutHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                        -it / 8
                      } + fadeOut(tween(400, easing = EmphasizedEasing))
                    else
                      slideOutHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                        it / 8
                      } + fadeOut(tween(400, easing = EmphasizedEasing))
                  },
                  popEnterTransition = {
                    val currentRouteIndex =
                      navigationItems.indexOfFirst { it.route == targetState.destination.route }
                    val previousRouteIndex =
                      navigationItems.indexOfFirst { it.route == initialState.destination.route }

                    if (previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex)
                      slideInHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                        it / 8
                      } + fadeIn(tween(400, easing = EmphasizedEasing))
                    else
                      slideInHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                        -it / 8
                      } + fadeIn(tween(400, easing = EmphasizedEasing))
                  },
                  popExitTransition = {
                    val currentRouteIndex =
                      navigationItems.indexOfFirst { it.route == initialState.destination.route }
                    val targetRouteIndex =
                      navigationItems.indexOfFirst { it.route == targetState.destination.route }

                    if (currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex)
                      slideOutHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                        -it / 8
                      } + fadeOut(tween(400, easing = EmphasizedEasing))
                    else
                      slideOutHorizontally(animationSpec = tween(400, easing = EmphasizedEasing)) {
                        it / 8
                      } + fadeOut(tween(400, easing = EmphasizedEasing))
                  },
                  modifier = Modifier.layerBackdrop(appBackdrop).nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                ) {
                  navigationBuilder(
                    navController = navController,
                    scrollBehavior = topAppBarScrollBehavior,
                    activity = this@MainActivity,
                    snackbarHostState = snackbarHostState
                  )
                }

                val (heartBurstEpoch) = rememberPreference(com.fenrir.her.constants.CoupleHeartBurstEpochKey, 0L)
                com.fenrir.her.couple.FloatingHeartBurstOverlay(
                  heartBurstEpochMs = heartBurstEpoch,
                  modifier = Modifier.fillMaxSize()
                )
              }
            }
          }

          BottomSheetMenu(
            state = LocalMenuState.current,
            modifier = Modifier.align(Alignment.BottomCenter)
          )

          BottomSheetPage(
            state = LocalBottomSheetPageState.current,
            modifier = Modifier.align(Alignment.BottomCenter)
          )

          sharedSong?.let { song ->
            playerConnection?.let {
              Dialog(
                onDismissRequest = { sharedSong = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
              ) {
                Surface(
                  modifier = Modifier.padding(24.dp),
                  shape = RoundedCornerShape(16.dp),
                  color = AlertDialogDefaults.containerColor,
                  tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                  ) {
                    YouTubeSongMenu(
                      song = song,
                      navController = navController,
                      onDismiss = { sharedSong = null },
                    )
                  }
                }
              }
            }
          }

          RingtoneTrimmerDialog(
            isVisible = ringtoneUiState.showTrimmer,
            songId = ringtoneUiState.targetSongId,
            songTitle = ringtoneUiState.targetSongTitle,
            duration = ringtoneUiState.targetSongDuration,
            onDismiss = { ringtoneViewModel.hideTrimmer() },
            onResolveStreamUrl = { ringtoneViewModel.getStreamUrl(this@MainActivity, it) },
            onConfirm = { start, end ->
              ringtoneViewModel.setAsRingtone(this@MainActivity, start, end)
            }
          )

          if (ringtoneUiState.showProgress) {
            RingtoneProgressDialog(
              isVisible = ringtoneUiState.showProgress,
              progress = ringtoneUiState.progress,
              statusMessage = ringtoneUiState.statusMessage,
              isComplete = ringtoneUiState.isComplete,
              isSuccess = ringtoneUiState.isSuccess,
              onDismiss = { ringtoneViewModel.dismissProgress() },
              onOpenSettings = { ringtoneViewModel.openRingtoneSettings(this@MainActivity) }
            )
          }

          if (showSettingDialoge) {
            SettingDialoge(
              onDismissRequest = { showSettingDialoge = false },
              onNavigate = { route ->
                showSettingDialoge = false
                navController.navigate(route)
              },
              homeViewModel = homeViewModel
            )
          }

        }
      }
    }
  }

  private fun handleDeepLinkIntent(intent: Intent, navController: NavHostController) {
    var uri = intent.data
    if (uri == null) {
      val extraText = intent.extras?.getString(Intent.EXTRA_TEXT)
      if (extraText != null) {
        val urlRegex = "(https?://[^\\s]+)".toRegex()
        val match = urlRegex.find(extraText)
        if (match != null) {
          uri = match.value.toUri()
        }
      }
    }
    if (uri == null) return

    intent.data = null
    intent.removeExtra(Intent.EXTRA_TEXT)
    val coroutineScope = lifecycle.coroutineScope

    val listenCode =
      uri.getQueryParameter("code")
        ?: uri.getQueryParameter("room")
        ?: uri.pathSegments.getOrNull(1)
    val isListenLink =
      uri.pathSegments.firstOrNull() == "listen" ||
        uri.host?.equals("listen", ignoreCase = true) == true
    if (!listenCode.isNullOrBlank() && isListenLink) {
      val username = dataStore.get(ListenTogetherUsernameKey, "").ifBlank { "Guest" }
      listenTogetherManager.joinRoom(listenCode, username)
      return
    }

    when (val path = uri.pathSegments.firstOrNull()) {
      "playlist" ->
        uri.getQueryParameter("list")?.let { playlistId ->
          if (playlistId.startsWith("OLAK5uy_")) {
            coroutineScope.launch(Dispatchers.IO) {
              YouTube.albumSongs(playlistId)
                .onSuccess { songs ->
                  songs.firstOrNull()?.album?.id?.let { browseId ->
                    withContext(Dispatchers.Main) { navController.navigate("album/$browseId") }
                  }
                }
                .onFailure { reportException(it) }
            }
          } else {
            navController.navigate("online_playlist/$playlistId")
          }
        }
      "browse" -> uri.lastPathSegment?.let { browseId -> navController.navigate("album/$browseId") }
      "channel",
      "c" -> uri.lastPathSegment?.let { artistId -> navController.navigate("artist/$artistId") }
      "search" -> {
        uri.getQueryParameter("q")?.let {
          navController.navigate("search/${URLEncoder.encode(it, "UTF-8")}")
        }
      }
      else -> {
        val videoId =
          when {
            path == "watch" -> uri.getQueryParameter("v")
            uri.host == "youtu.be" || uri.host == "share.echomusic.fun" ->
              uri.pathSegments.firstOrNull()
            else -> null
          }

        val playlistId = uri.getQueryParameter("list")

        if (videoId != null) {
          coroutineScope.launch(Dispatchers.IO) {
            YouTube.queue(listOf(videoId), playlistId)
              .onSuccess { queue ->
                withContext(Dispatchers.Main) {
                  var attempts = 0
                  while (playerConnection == null && attempts < 20) {
                    delay(100)
                    attempts++
                  }
                  playerConnection?.playQueue(
                    YouTubeQueue(
                      WatchEndpoint(videoId = queue.firstOrNull()?.id, playlistId = playlistId),
                      queue.firstOrNull()?.toMediaMetadata()
                    )
                  )
                }
              }
              .onFailure { reportException(it) }
          }
        } else if (playlistId != null) {
          coroutineScope.launch(Dispatchers.IO) {
            YouTube.queue(null, playlistId)
              .onSuccess { queue ->
                val firstItem = queue.firstOrNull()
                withContext(Dispatchers.Main) {
                  var attempts = 0
                  while (playerConnection == null && attempts < 20) {
                    delay(100)
                    attempts++
                  }
                  playerConnection?.playQueue(
                    YouTubeQueue(
                      WatchEndpoint(videoId = firstItem?.id, playlistId = playlistId),
                      firstItem?.toMediaMetadata()
                    )
                  )
                }
              }
              .onFailure { reportException(it) }
          }
        }
      }
    }
  }

  @SuppressLint("ObsoleteSdkInt")
  private fun setSystemBarAppearance(isDark: Boolean) {
    WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
      isAppearanceLightStatusBars = !isDark
      isAppearanceLightNavigationBars = !isDark
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      window.statusBarColor =
        (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      window.navigationBarColor =
        (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
    }
  }

  private fun handleRecognitionIntent(
    intent: Intent,
    navController: NavHostController,
  ) {
    if (intent.action != ACTION_RECOGNITION) return
    val autoStart = intent.getBooleanExtra(EXTRA_AUTO_START_RECOGNITION, false)

    intent.removeExtra(EXTRA_AUTO_START_RECOGNITION)
    navController.navigate(if (autoStart) "recognition?autoStart=true" else "recognition") {
      launchSingleTop = true
    }
  }

  private fun handleAssistantSearchIntent(
    intent: Intent,
    navController: NavHostController,
  ) {
    if (intent.action == android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
      val query = intent.getStringExtra(android.app.SearchManager.QUERY) ?: return
      navController.navigate("search/${URLEncoder.encode(query, "UTF-8")}")
    }
  }

  private fun handlePushSongIntent(intent: Intent) {
    if (intent.action == "com.fenrir.her.PLAY_PUSHED_SONG") {
      val songId = intent.getStringExtra("EXTRA_SONG_ID") ?: intent.getStringExtra("pushed_song_id") ?: return
      lifecycle.coroutineScope.launch {
        var attempts = 0
        while (playerConnection == null && attempts < 25) {
          delay(100)
          attempts++
        }
        playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = songId)))
      }
      intent.action = null
    }
  }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalRingtoneViewModel =
  compositionLocalOf<RingtoneViewModel> { error("No RingtoneViewModel provided") }

val LocalPlayerConnection =
  staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }

val LocalPlayerAwareWindowInsets =
  compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalListenTogetherManager =
  staticCompositionLocalOf<com.fenrir.her.listentogether.ListenTogetherManager?> { null }
val LocalIsPlayerExpanded = compositionLocalOf { false }
