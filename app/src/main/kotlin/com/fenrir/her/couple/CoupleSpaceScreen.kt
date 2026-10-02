package com.fenrir.her.couple

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.innertube.models.WatchEndpoint
import com.fenrir.her.LocalListenTogetherManager
import com.fenrir.her.LocalPlayerAwareWindowInsets
import com.fenrir.her.LocalPlayerConnection
import com.fenrir.her.R
import com.fenrir.her.auth.CoupleAuthGuard
import com.fenrir.her.constants.AccountEmailKey
import com.fenrir.her.constants.AccountNameKey
import com.fenrir.her.constants.AccountChannelHandleKey
import com.fenrir.her.constants.InnerTubeCookieKey
import com.fenrir.her.constants.CoupleHeartBurstEpochKey
import com.fenrir.her.constants.CoupleRadioBroadcastKey
import com.fenrir.her.constants.CoupleRoleKey
import com.fenrir.her.constants.CoupleMyNameKey
import com.fenrir.her.constants.CouplePartnerNameKey
import com.fenrir.her.constants.CoupleMyCodeKey
import com.fenrir.her.constants.CouplePartnerCodeKey
import com.fenrir.her.constants.CoupleSpaceIdKey
import com.fenrir.her.constants.CoupleIsPairedKey
import com.fenrir.her.constants.CoupleLinkedTimestampKey
import com.fenrir.her.constants.CoupleFirebaseUrlKey
import com.fenrir.her.constants.CoupleLastSeenPushTimestampKey
import com.fenrir.her.constants.CoupleSharedSongsKey
import com.fenrir.her.playback.queues.YouTubeQueue
import com.fenrir.her.ui.component.IconButton
import com.fenrir.her.ui.screens.Screens
import com.fenrir.her.utils.rememberPreference
import androidx.datastore.preferences.core.stringPreferencesKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LoveNote(
  val id: String = System.currentTimeMillis().toString(),
  val sender: String,
  val text: String,
  val timestamp: Long = System.currentTimeMillis(),
)

@Serializable
data class DailyAnswer(
  val dateKey: String = "",
  val himAnswer: String = "",
  val herAnswer: String = "",
  val ankitAnswer: String = "",
  val ritikaAnswer: String = "",
) {
  fun getMyAnswer(role: CoupleAuthGuard.CoupleRole): String =
    if (role == CoupleAuthGuard.CoupleRole.HER) herAnswer.ifBlank { ritikaAnswer }
    else himAnswer.ifBlank { ankitAnswer }

  fun getPartnerAnswer(role: CoupleAuthGuard.CoupleRole): String =
    if (role == CoupleAuthGuard.CoupleRole.HER) himAnswer.ifBlank { ankitAnswer }
    else herAnswer.ifBlank { ritikaAnswer }

  val isBothAnswered: Boolean
    get() = (himAnswer.isNotBlank() || ankitAnswer.isNotBlank()) &&
            (herAnswer.isNotBlank() || ritikaAnswer.isNotBlank())
}

private val CoupleLoveNotesKey = stringPreferencesKey("couple_love_notes_json")
private val CoupleDailyAnswersKey = stringPreferencesKey("couple_daily_answers_json")

private val DAILY_QUESTIONS = listOf(
  "What song best describes how you feel about us right now?",
  "What is your all-time favorite memory of us together?",
  "What was the exact moment you realized you loved me?",
  "If we could teleport to a concert anywhere in the world tonight, who are we seeing?",
  "What is one cute habit of mine that never fails to make you smile?",
  "If our love story had a soundtrack album, what would Track #1 be named?",
  "What is something small I did recently that made your day brighter?",
  "Where is the first place we should travel to next?",
  "What song should always play whenever we reunite?",
  "What are you most grateful for in our relationship today?",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleSpaceScreen(
  navController: NavController,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val clipboardManager = LocalClipboardManager.current
  val listenTogetherManager = LocalListenTogetherManager.current
  val playerConnection = LocalPlayerConnection.current
  val currentMediaMetadata by playerConnection?.service?.currentMediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) }

  val (accountEmail) = rememberPreference(AccountEmailKey, "")
  val (accountName) = rememberPreference(AccountNameKey, "")
  val (accountChannelHandle) = rememberPreference(AccountChannelHandleKey, "")
  val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")

  val isSignedIn = remember(innerTubeCookie, accountEmail, accountName) {
    innerTubeCookie.isNotBlank() || accountEmail.isNotBlank() || accountName.isNotBlank()
  }
  val accountDisplayName = remember(accountName, accountChannelHandle, accountEmail) {
    when {
      accountName.isNotBlank() && accountChannelHandle.isNotBlank() -> "$accountName ($accountChannelHandle)"
      accountName.isNotBlank() -> accountName
      accountEmail.isNotBlank() -> accountEmail
      accountChannelHandle.isNotBlank() -> accountChannelHandle
      else -> "Connected YouTube Account"
    }
  }

  var coupleRolePref by rememberPreference(CoupleRoleKey, "HIM")
  var customMyName by rememberPreference(CoupleMyNameKey, "")
  var customPartnerName by rememberPreference(CouplePartnerNameKey, "")
  var myCode by rememberPreference(CoupleMyCodeKey, "")
  var partnerCode by rememberPreference(CouplePartnerCodeKey, "")
  var spaceId by rememberPreference(CoupleSpaceIdKey, "")
  var isPaired by rememberPreference(CoupleIsPairedKey, false)
  var linkedTimestamp by rememberPreference(CoupleLinkedTimestampKey, 0L)
  var customFirebaseUrl by rememberPreference(CoupleFirebaseUrlKey, "")
  var lastSeenPushTimestamp by rememberPreference(CoupleLastSeenPushTimestampKey, 0L)
  var sharedSongsJson by rememberPreference(CoupleSharedSongsKey, "[]")
  var loveNotesJson by rememberPreference(CoupleLoveNotesKey, "[]")
  var dailyAnswersJson by rememberPreference(CoupleDailyAnswersKey, "{}")

  val role = remember(accountEmail, coupleRolePref) {
    if (coupleRolePref == "HER" || coupleRolePref == "RITIKA") CoupleAuthGuard.CoupleRole.HER
    else CoupleAuthGuard.CoupleRole.HIM
  }
  val myName = CoupleAuthGuard.getMyName(role, customMyName.ifBlank { accountName })
  val partnerName = CoupleAuthGuard.getPartnerName(role, customPartnerName)

  var selectedTabIndex by remember { mutableIntStateOf(0) }
  val spaceTabTitle = if (role == CoupleAuthGuard.CoupleRole.HER) "Him & Her" else "Her & Him"
  val chatTabTitle = if (role == CoupleAuthGuard.CoupleRole.HER) "HimChat" else "HerChat"
  val songsTabTitle = if (role == CoupleAuthGuard.CoupleRole.HER) "Songs from Him" else "Songs from Her"
  val tabTitles = listOf(spaceTabTitle, chatTabTitle, songsTabTitle)

  var broadcastJson by rememberPreference(CoupleRadioBroadcastKey, "{}")
  var heartBurstEpochMs by rememberPreference(CoupleHeartBurstEpochKey, 0L)
  val broadcastSession = remember(broadcastJson) { RadioBroadcastSerialization.parse(broadcastJson) }
  val isMeBroadcasting = broadcastSession.isBroadcasting && broadcastSession.broadcasterRole == role.name

  LaunchedEffect(role) {
    val neededPrefix = if (role == CoupleAuthGuard.CoupleRole.HER) "HER-" else "HIM-"
    if (myCode.isBlank() || !myCode.startsWith(neededPrefix)) {
      myCode = CoupleAuthGuard.generatePairCode(role)
    }
    if (customMyName.isBlank() && accountName.isNotBlank()) {
      customMyName = accountName
    }
  }

  LaunchedEffect(isPaired, myCode, myName, role) {
    if (!isPaired && myCode.isNotBlank()) {
      var loopCount = 0
      while (!isPaired) {
        if (loopCount % 5 == 0) {
          FirebaseCoupleSync.registerInvite(myCode, role.name, myName, customFirebaseUrl)
        }
        loopCount++
        val match = FirebaseCoupleSync.checkInviteMatch(myCode, customFirebaseUrl)
        if (match != null && match.matched && match.spaceId.isNotBlank()) {
          partnerCode = match.partnerCode
          if (customPartnerName.isBlank() && match.partnerName.isNotBlank()) {
            customPartnerName = match.partnerName
          }
          spaceId = match.spaceId
          linkedTimestamp = if (match.timestamp > 0L) match.timestamp else System.currentTimeMillis()
          isPaired = true
          Toast.makeText(context, "Partner connected! Welcome to your space 💕", Toast.LENGTH_SHORT).show()
          break
        }
        delay(2000)
      }
    } else if (isPaired && spaceId.isNotBlank() && partnerCode.isNotBlank()) {
      FirebaseCoupleSync.linkInvite(partnerCode, myCode, role.name, myName, customFirebaseUrl)
    }
  }

  var latestIncomingPushSong by remember { mutableStateOf<CouplePushSong?>(null) }
  LaunchedEffect(isPaired, spaceId) {
    if (isPaired && spaceId.isNotBlank()) {
      while (true) {
        val fetched = FirebaseCoupleSync.fetchLatestPushSong(spaceId, customFirebaseUrl)
        if (fetched != null && fetched.timestamp > lastSeenPushTimestamp && fetched.senderRole != role.name) {
          lastSeenPushTimestamp = fetched.timestamp
          PushSongNotificationManager.showPushSongNotification(context, fetched)
          latestIncomingPushSong = fetched

          val currentSongs = CoupleSerialization.parseSharedSongs(sharedSongsJson)
          val newEntry = SharedSongAttachment(
            id = fetched.songId,
            title = fetched.title,
            artist = fetched.artist,
            thumbnailUrl = fetched.thumbnailUrl,
            addedBy = fetched.senderName
          )
          if (currentSongs.none { it.id == newEntry.id }) {
            sharedSongsJson = CoupleSerialization.serializeSharedSongs(listOf(newEntry) + currentSongs)
          }
        }
        delay(7000)
      }
    }
  }

  LaunchedEffect(isPaired, spaceId, role, myName, playerConnection) {
    if (isPaired && spaceId.isNotBlank()) {
      CouplePlaybackSyncManager.startBroadcaster(
        scope = this,
        spaceId = spaceId,
        role = role,
        myName = myName,
        playerConnection = playerConnection,
        customUrl = customFirebaseUrl
      )
    }
  }

  val todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
  val dayOfYear = remember {
    val cal = java.util.Calendar.getInstance()
    cal.get(java.util.Calendar.DAY_OF_YEAR)
  }
  val currentQuestion = remember(dayOfYear) {
    DAILY_QUESTIONS[dayOfYear % DAILY_QUESTIONS.size]
  }

  val loveNotes = remember(loveNotesJson) {
    try {
      Json.decodeFromString<List<LoveNote>>(loveNotesJson)
    } catch (_: Exception) {
      emptyList()
    }
  }

  val allAnswers = remember(dailyAnswersJson) {
    try {
      Json.decodeFromString<Map<String, DailyAnswer>>(dailyAnswersJson)
    } catch (_: Exception) {
      emptyMap()
    }
  }

  val todayAnswer = allAnswers[todayKey] ?: DailyAnswer(dateKey = todayKey)
  val myAnswer = todayAnswer.getMyAnswer(role)
  val partnerAnswer = todayAnswer.getPartnerAnswer(role)
  val isBothAnswered = todayAnswer.isBothAnswered

  var noteInput by remember { mutableStateOf("") }
  var answerInput by remember { mutableStateOf(myAnswer) }
  var isSubmittingAnswer by remember { mutableStateOf(false) }
  var showUnlinkConfirmDialog by remember { mutableStateOf(false) }
  var showEditNamesDialog by remember { mutableStateOf(false) }

  LaunchedEffect(spaceId, todayKey) {
    if (isPaired && spaceId.isNotBlank()) {
      while (true) {
        val remoteAnswers = FirebaseCoupleSync.fetchDailyAnswers(spaceId, todayKey, customFirebaseUrl)
        if (remoteAnswers != null) {
          val (him, her) = remoteAnswers
          val cur = allAnswers[todayKey] ?: DailyAnswer(dateKey = todayKey)
          val newHim = if (him.isNotBlank()) him else cur.himAnswer.ifBlank { cur.ankitAnswer }
          val newHer = if (her.isNotBlank()) her else cur.herAnswer.ifBlank { cur.ritikaAnswer }
          if (newHim != cur.himAnswer || newHer != cur.herAnswer) {
            val updated = cur.copy(himAnswer = newHim, herAnswer = newHer)
            val updatedMap = allAnswers + (todayKey to updated)
            dailyAnswersJson = Json.encodeToString(updatedMap)
          }
        }
        delay(12000)
      }
    }
  }

  val daysTogether = remember(linkedTimestamp) {
    if (linkedTimestamp > 0L) {
      val diffMs = System.currentTimeMillis() - linkedTimestamp
      ((diffMs / (1000 * 60 * 60 * 24)) + 1).coerceAtLeast(1)
    } else 1L
  }

  val sharedSongsList = remember(sharedSongsJson) {
    CoupleSerialization.parseSharedSongs(sharedSongsJson)
  }

  if (showUnlinkConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showUnlinkConfirmDialog = false },
      title = { Text("Unlink Couple Space?") },
      text = { Text("Are you sure you want to disconnect? You can re-link anytime by exchanging your codes again.") },
      confirmButton = {
        Button(
          onClick = {
            isPaired = false
            spaceId = ""
            partnerCode = ""
            showUnlinkConfirmDialog = false
            Toast.makeText(context, "Unlinked space", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text("Unlink")
        }
      },
      dismissButton = {
        TextButton(onClick = { showUnlinkConfirmDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  if (showEditNamesDialog) {
    var editMyName by remember { mutableStateOf(myName) }
    var editPartnerName by remember { mutableStateOf(partnerName) }

    AlertDialog(
      onDismissRequest = { showEditNamesDialog = false },
      title = { Text("Update Couple Names 💕") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = editMyName,
            onValueChange = { editMyName = it },
            label = { Text("Your Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = editPartnerName,
            onValueChange = { editPartnerName = it },
            label = { Text("Partner's Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (editMyName.isNotBlank()) customMyName = editMyName.trim()
            if (editPartnerName.isNotBlank()) customPartnerName = editPartnerName.trim()
            showEditNamesDialog = false
            Toast.makeText(context, "Names updated 💕", Toast.LENGTH_SHORT).show()
          }
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showEditNamesDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  Scaffold(
    topBar = {
      Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
          title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = Color(0xFFFF4081),
                modifier = Modifier.size(22.dp)
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = spaceTabTitle,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
              )
            }
          },
          navigationIcon = {
            IconButton(
              onClick = {
                val popped = navController.popBackStack(Screens.Home.route, false)
                if (!popped) navController.navigateUp()
              },
              onLongClick = {}
            ) {
              Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          )
        )

        PrimaryTabRow(
          selectedTabIndex = selectedTabIndex,
          containerColor = MaterialTheme.colorScheme.background
        ) {
          tabTitles.forEachIndexed { index, title ->
            Tab(
              selected = selectedTabIndex == index,
              onClick = { selectedTabIndex = index },
              text = {
                Text(
                  text = title,
                  fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                )
              }
            )
          }
        }
      }
    }
  ) { padding ->
    val playerAwareBottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .imePadding()
    ) {
      when (selectedTabIndex) {
        1 -> CoupleChatView(modifier = Modifier.fillMaxSize())
        2 -> CoupleSharedSongsView(modifier = Modifier.fillMaxSize())
        else -> {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
              start = 16.dp,
              end = 16.dp,
              top = 8.dp,
              bottom = playerAwareBottom + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            if (isPaired && spaceId.isNotBlank()) {
              item {
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(28.dp),
                  colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .background(
                        Brush.linearGradient(
                          colors = listOf(
                            Color(0xFF880E4F),
                            Color(0xFF4A148C),
                            Color(0xFF1A237E)
                          )
                        )
                      )
                      .padding(22.dp)
                  ) {
                    Column(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Surface(
                          shape = RoundedCornerShape(12.dp),
                          color = Color(0x33FFFFFF)
                        ) {
                          Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                          ) {
                            Box(
                              modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                            )
                            Text(
                              text = "Space: $spaceId",
                              style = MaterialTheme.typography.labelSmall,
                              color = Color.White,
                              fontWeight = FontWeight.SemiBold
                            )
                          }
                        }

                        Row(
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                          Text(
                            text = "✏️ Names",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier
                              .clip(RoundedCornerShape(8.dp))
                              .clickable { showEditNamesDialog = true }
                              .padding(horizontal = 8.dp, vertical = 4.dp)
                          )
                          Text(
                            text = "Unlink",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                              .clip(RoundedCornerShape(8.dp))
                              .clickable { showUnlinkConfirmDialog = true }
                              .padding(horizontal = 8.dp, vertical = 4.dp)
                          )
                        }
                      }

                      Spacer(Modifier.height(14.dp))

                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                      ) {
                        AvatarPill(
                          name = myName,
                          label = "$myName (You • ${if (role == CoupleAuthGuard.CoupleRole.HER) "Her 💖" else "Him 💙"})",
                          isMe = true,
                          onClick = { showEditNamesDialog = true }
                        )
                        Icon(
                          painter = painterResource(R.drawable.favorite),
                          contentDescription = null,
                          tint = Color(0xFFFF4081),
                          modifier = Modifier.size(28.dp)
                        )
                        AvatarPill(
                          name = partnerName,
                          label = "$partnerName (${if (role == CoupleAuthGuard.CoupleRole.HER) "Him 💙" else "Her 💖"})",
                          isMe = false,
                          onClick = { showEditNamesDialog = true }
                        )
                      }

                      Spacer(Modifier.height(18.dp))

                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                      ) {
                        StatColumn(label = "Days in Love", value = "Day $daysTogether")
                        StatColumn(label = "Music Shared", value = "🎵 ${sharedSongsList.size}")
                      }
                    }
                  }
                }
              }

              item {
                CouplePlayTogetherCard(
                  partnerName = partnerName,
                  role = role,
                  spaceId = spaceId,
                  customFirebaseUrl = customFirebaseUrl,
                  playerConnection = playerConnection
                )
              }

              if (broadcastSession.isBroadcasting) {
                item {
                  RadioBroadcastLiveBanner(
                    session = broadcastSession,
                    isMeBroadcasting = isMeBroadcasting,
                    onTuneIn = {
                      playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = broadcastSession.songId)))
                    },
                    onStopBroadcast = {
                      broadcastJson = RadioBroadcastSerialization.serialize(RadioBroadcastSession())
                    },
                    onSendHeart = {
                      val updated = broadcastSession.copy(heartCount = broadcastSession.heartCount + 1, lastHeartEpochMs = System.currentTimeMillis())
                      broadcastJson = RadioBroadcastSerialization.serialize(updated)
                      heartBurstEpochMs = System.currentTimeMillis()
                    }
                  )
                }
              }

              item {
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(22.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.favorite), null, tint = Color(0xFFFF4081), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                          text = "Live Radio Broadcast",
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.Bold
                        )
                      }
                      Spacer(Modifier.height(4.dp))
                      Text(
                        text = if (isMeBroadcasting) "Broadcasting live to $partnerName" else "Share what you're hearing right now with $partnerName in real-time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }

                    Button(
                      onClick = {
                        if (isMeBroadcasting) {
                          broadcastJson = RadioBroadcastSerialization.serialize(RadioBroadcastSession())
                          coroutineScope.launch {
                            FirebaseCoupleSync.broadcastRadio(spaceId, RadioBroadcastSession(), customFirebaseUrl)
                          }
                        } else {
                          val song = currentMediaMetadata
                          if (song != null) {
                            val newSession = RadioBroadcastSession(
                              isBroadcasting = true,
                              broadcasterRole = role.name,
                              broadcasterName = myName,
                              songId = song.id,
                              songTitle = song.title,
                              songArtist = song.artists.joinToString { it.name },
                              artworkUrl = song.thumbnailUrl,
                              startedEpochMs = System.currentTimeMillis()
                            )
                            broadcastJson = RadioBroadcastSerialization.serialize(newSession)
                            coroutineScope.launch {
                              FirebaseCoupleSync.broadcastRadio(spaceId, newSession, customFirebaseUrl)
                            }
                            Toast.makeText(context, "Broadcasting to $partnerName!", Toast.LENGTH_SHORT).show()
                          } else {
                            Toast.makeText(context, "Play a track first to broadcast", Toast.LENGTH_SHORT).show()
                          }
                        }
                      },
                      colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMeBroadcasting) Color(0xFFC2185B) else Color(0xFFFF4081)
                      ),
                      shape = RoundedCornerShape(16.dp)
                    ) {
                      Text(if (isMeBroadcasting) "Stop" else "Go Live", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                  }
                }
              }

              latestIncomingPushSong?.let { pushed ->
                item {
                  Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE91E63).copy(alpha = 0.15f))
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth().padding(16.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Icon(painterResource(R.drawable.music_note), null, tint = Color(0xFFFF4081), modifier = Modifier.size(18.dp))
                          Spacer(Modifier.width(6.dp))
                          Text(
                            text = "💌 $partnerName pushed a song!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF4081)
                          )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                          text = "${pushed.title} • ${pushed.artist}",
                          style = MaterialTheme.typography.bodyMedium,
                          fontWeight = FontWeight.SemiBold,
                          maxLines = 1
                        )
                      }

                      Button(
                        onClick = {
                          playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = pushed.songId)))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)),
                        shape = RoundedCornerShape(16.dp)
                      ) {
                        Icon(painterResource(R.drawable.play), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Listen", fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                }
              }

              item {
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(22.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.share), null, tint = Color(0xFFFF4081), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                          text = "Push Song to $partnerName",
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.Bold
                        )
                      }
                      Spacer(Modifier.height(4.dp))
                      Text(
                        text = if (currentMediaMetadata != null) {
                          "“${currentMediaMetadata?.title}” by ${currentMediaMetadata?.artists?.firstOrNull()?.name.orEmpty()}"
                        } else {
                          "Play any song in the app, then tap push to notify $partnerName instantly!"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                      )
                    }

                    Button(
                      onClick = {
                        val song = currentMediaMetadata
                        if (song != null) {
                          coroutineScope.launch {
                            val pushed = CouplePushSong(
                              songId = song.id,
                              title = song.title,
                              artist = song.artists.joinToString { it.name },
                              thumbnailUrl = song.thumbnailUrl,
                              senderRole = role.name,
                              senderName = myName,
                              timestamp = System.currentTimeMillis()
                            )
                            val ok = FirebaseCoupleSync.pushSong(spaceId, pushed, customFirebaseUrl)
                            val currentSongs = CoupleSerialization.parseSharedSongs(sharedSongsJson)
                            val newEntry = SharedSongAttachment(
                              id = song.id,
                              title = song.title,
                              artist = song.artists.joinToString { it.name },
                              thumbnailUrl = song.thumbnailUrl,
                              addedBy = myName
                            )
                            if (currentSongs.none { it.id == newEntry.id }) {
                              sharedSongsJson = CoupleSerialization.serializeSharedSongs(listOf(newEntry) + currentSongs)
                            }
                            if (ok) {
                              Toast.makeText(context, "Pushed to $partnerName! 💌", Toast.LENGTH_SHORT).show()
                            } else {
                              Toast.makeText(context, "Saved locally. Push will sync once online.", Toast.LENGTH_SHORT).show()
                            }
                          }
                        } else {
                          Toast.makeText(context, "Play a track first to push", Toast.LENGTH_SHORT).show()
                        }
                      },
                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)),
                      shape = RoundedCornerShape(16.dp)
                    ) {
                      Text("Push Song", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                  }
                }
              }

              item {
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(24.dp),
                  colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                  )
                ) {
                  Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      Icon(
                        painter = painterResource(R.drawable.connect_people),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                      )
                      Text(
                        text = "Live Listen Together",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                      )
                    }

                    Text(
                      text = "Sync playback seamlessly with $partnerName in real time. Private Room: her-$spaceId",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                      onClick = {
                        if (listenTogetherManager != null) {
                          coroutineScope.launch {
                            listenTogetherManager.connect()
                            listenTogetherManager.joinRoom("her-$spaceId", myName)
                            Toast.makeText(context, "Joining shared room with $partnerName...", Toast.LENGTH_SHORT).show()
                            navController.navigate("listen_together")
                          }
                        } else {
                          navController.navigate("listen_together")
                        }
                      },
                      modifier = Modifier.fillMaxWidth(),
                      shape = RoundedCornerShape(16.dp),
                      colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE91E63)
                      )
                    ) {
                      Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                      )
                      Spacer(Modifier.width(8.dp))
                      Text(
                        text = "1-Tap Sync with $partnerName",
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }
                }
              }

              item {
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(24.dp),
                  colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                  )
                ) {
                  Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    Text(
                      text = "Question of the Day",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold
                    )

                    Text(
                      text = currentQuestion,
                      style = MaterialTheme.typography.bodyLarge,
                      color = MaterialTheme.colorScheme.primary
                    )

                    if (myAnswer.isNotBlank()) {
                      AnswerCard(author = "$myName (You)", text = myAnswer, isMe = true)
                    }

                    if (isBothAnswered) {
                      AnswerCard(author = partnerName, text = partnerAnswer, isMe = false)
                    } else if (myAnswer.isNotBlank()) {
                      Text(
                        text = "🔒 $partnerName's answer will reveal once both of you answer!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }

                    if (myAnswer.isBlank()) {
                      OutlinedTextField(
                        value = answerInput,
                        onValueChange = { answerInput = it },
                        placeholder = { Text("Your answer to $partnerName...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        maxLines = 3
                      )

                      Button(
                        onClick = {
                          if (answerInput.isNotBlank()) {
                            isSubmittingAnswer = true
                            val updatedToday = if (role == CoupleAuthGuard.CoupleRole.HER) {
                              todayAnswer.copy(herAnswer = answerInput.trim(), ritikaAnswer = answerInput.trim())
                            } else {
                              todayAnswer.copy(himAnswer = answerInput.trim(), ankitAnswer = answerInput.trim())
                            }
                            val updatedMap = allAnswers + (todayKey to updatedToday)
                            dailyAnswersJson = Json.encodeToString(updatedMap)
                            coroutineScope.launch {
                              FirebaseCoupleSync.submitDailyAnswer(spaceId, todayKey, role.name, answerInput.trim(), customFirebaseUrl)
                              isSubmittingAnswer = false
                            }
                          }
                        },
                        enabled = answerInput.isNotBlank() && !isSubmittingAnswer,
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(14.dp)
                      ) {
                        Text("Submit Answer")
                      }
                    }
                  }
                }
              }

              item {
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(24.dp),
                  colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                  )
                ) {
                  Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    Text(
                      text = "Love Notes & Memories",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                      value = noteInput,
                      onValueChange = { noteInput = it },
                      placeholder = { Text("Leave a sweet note for $partnerName...") },
                      modifier = Modifier.fillMaxWidth(),
                      shape = RoundedCornerShape(16.dp),
                      maxLines = 4
                    )

                    Button(
                      onClick = {
                        if (noteInput.isNotBlank()) {
                          val newNote = LoveNote(
                            sender = myName,
                            text = noteInput.trim()
                          )
                          val updatedList = listOf(newNote) + loveNotes
                          loveNotesJson = Json.encodeToString(updatedList)
                          noteInput = ""
                        }
                      },
                      enabled = noteInput.isNotBlank(),
                      modifier = Modifier.align(Alignment.End),
                      shape = RoundedCornerShape(14.dp),
                      colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE91E63)
                      )
                    ) {
                      Icon(
                        painter = painterResource(R.drawable.favorite),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                      )
                      Spacer(Modifier.width(6.dp))
                      Text("Pin Note")
                    }
                  }
                }
              }

              if (loveNotes.isNotEmpty()) {
                item {
                  Text(
                    text = "Pinned Notes (${loveNotes.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                  )
                }

                items(loveNotes, key = { it.id }) { note ->
                  LoveNoteCard(note = note, isMe = note.sender == myName)
                }
              }
            } else {
              item {
                Card(
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(28.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                  Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                      Icon(
                        painter = painterResource(R.drawable.favorite),
                        contentDescription = null,
                        tint = Color(0xFFFF4081),
                        modifier = Modifier.size(28.dp)
                      )
                      Column {
                        Text(
                          text = "Set Up Your Couple Space",
                          style = MaterialTheme.typography.titleLarge,
                          fontWeight = FontWeight.Bold
                        )
                        Text(
                          text = "Sync YouTube music, exchange codes with your partner, and listen together in real time.",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                    }

                    Card(
                      modifier = Modifier.fillMaxWidth(),
                      shape = RoundedCornerShape(18.dp),
                      colors = CardDefaults.cardColors(
                        containerColor = if (isSignedIn) Color(0xFF4CAF50).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                      )
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                        Row(
                          modifier = Modifier.weight(1f),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                          if (isSignedIn) {
                            Box(
                              modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32)),
                              contentAlignment = Alignment.Center
                            ) {
                              Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                              )
                            }
                          }
                          Column {
                            Text(
                              text = if (isSignedIn) "✓ Signed in to YouTube Music" else "Step 1: Sign in with YouTube",
                              fontWeight = FontWeight.Bold,
                              style = MaterialTheme.typography.titleSmall,
                              color = if (isSignedIn) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            )
                            Text(
                              text = if (isSignedIn) accountDisplayName else "Sign in to sync your playlists and connect with your partner",
                              style = MaterialTheme.typography.bodySmall,
                              color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                          }
                        }

                        if (!isSignedIn) {
                          Button(
                            onClick = { navController.navigate("login") },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                          ) {
                            Text("Sign In")
                          }
                        } else {
                          TextButton(onClick = { navController.navigate("login") }) {
                            Text("Switch")
                          }
                        }
                      }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Text(
                        text = "Step 2: Choose Your Role",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                      )
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                      ) {
                        val isHim = coupleRolePref == "HIM"
                        Box(
                          modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isHim) Color(0xFF1976D2).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                            .border(
                              width = if (isHim) 2.dp else 1.dp,
                              color = if (isHim) Color(0xFF1976D2) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                              shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                              coupleRolePref = "HIM"
                              myCode = CoupleAuthGuard.generatePairCode(CoupleAuthGuard.CoupleRole.HIM)
                            }
                            .padding(14.dp),
                          contentAlignment = Alignment.Center
                        ) {
                          Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💙", fontSize = 24.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Him (Male)", fontWeight = FontWeight.Bold, color = if (isHim) Color(0xFF1976D2) else MaterialTheme.colorScheme.onSurface)
                          }
                        }

                        val isHer = coupleRolePref == "HER"
                        Box(
                          modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isHer) Color(0xFFE91E63).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                            .border(
                              width = if (isHer) 2.dp else 1.dp,
                              color = if (isHer) Color(0xFFE91E63) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                              shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                              coupleRolePref = "HER"
                              myCode = CoupleAuthGuard.generatePairCode(CoupleAuthGuard.CoupleRole.HER)
                            }
                            .padding(14.dp),
                          contentAlignment = Alignment.Center
                        ) {
                          Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💖", fontSize = 24.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Her (Female)", fontWeight = FontWeight.Bold, color = if (isHer) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface)
                          }
                        }
                      }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Text(
                        text = "Step 3: What should we call you two?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                      )
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                      ) {
                        var myNameInput by remember(customMyName) { mutableStateOf(customMyName) }
                        var partnerNameInput by remember(customPartnerName) { mutableStateOf(customPartnerName) }

                        OutlinedTextField(
                          value = myNameInput,
                          onValueChange = {
                            myNameInput = it
                            customMyName = it
                          },
                          label = { Text("Your Name") },
                          placeholder = { Text(if (role == CoupleAuthGuard.CoupleRole.HER) "Her" else "Him") },
                          modifier = Modifier.weight(1f),
                          shape = RoundedCornerShape(14.dp),
                          singleLine = true
                        )
                        OutlinedTextField(
                          value = partnerNameInput,
                          onValueChange = {
                            partnerNameInput = it
                            customPartnerName = it
                          },
                          label = { Text("Partner's Name") },
                          placeholder = { Text(if (role == CoupleAuthGuard.CoupleRole.HER) "Him" else "Her") },
                          modifier = Modifier.weight(1f),
                          shape = RoundedCornerShape(14.dp),
                          singleLine = true
                        )
                      }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Text(
                        text = "Step 4: Share & Exchange Codes",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                      )

                      Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                      ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                          Text(
                            text = "Your Unique Code (Give to partner)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                          )
                          Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                          ) {
                            Text(
                              text = myCode.ifBlank { "GENERATING..." },
                              fontWeight = FontWeight.Bold,
                              style = MaterialTheme.typography.titleLarge,
                              fontFamily = FontFamily.Monospace,
                              color = MaterialTheme.colorScheme.primary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                              Button(
                                onClick = {
                                  clipboardManager.setText(AnnotatedString(myCode))
                                  Toast.makeText(context, "Code copied! Send to your partner.", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                              ) {
                                Text("Copy")
                              }

                              OutlinedButton(
                                onClick = {
                                  val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Here is my Couple Space code for our music app: $myCode. Enter it in your app to link our couple space! 💕")
                                  }
                                  context.startActivity(Intent.createChooser(shareIntent, "Share Space Code"))
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                              ) {
                                Text("Share")
                              }
                            }
                          }
                        }
                      }

                      var partnerInput by remember(partnerCode) { mutableStateOf(partnerCode) }
                      var isLinking by remember { mutableStateOf(false) }

                      OutlinedTextField(
                        value = partnerInput,
                        onValueChange = { partnerInput = it.uppercase() },
                        label = { Text("Partner's Space Code") },
                        placeholder = { Text(if (role == CoupleAuthGuard.CoupleRole.HER) "e.g. HIM-XXXX" else "e.g. HER-XXXX") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                      )

                      Button(
                        enabled = !isLinking,
                        onClick = {
                          val clean = partnerInput.trim().uppercase()
                          if (clean.isNotBlank()) {
                            if (clean == myCode) {
                              Toast.makeText(context, "Please enter your partner's code, not your own!", Toast.LENGTH_SHORT).show()
                            } else {
                              isLinking = true
                              coroutineScope.launch {
                                try {
                                  val partnerInvite = FirebaseCoupleSync.getInvite(clean, customFirebaseUrl)
                                  if (partnerInvite != null && partnerInvite.name.isNotBlank() && customPartnerName.isBlank()) {
                                    customPartnerName = partnerInvite.name
                                  }
                                  FirebaseCoupleSync.linkInvite(clean, myCode, role.name, myName, customFirebaseUrl)
                                  partnerCode = clean
                                  spaceId = CoupleAuthGuard.calculateSpaceId(myCode, clean)
                                  linkedTimestamp = System.currentTimeMillis()
                                  isPaired = true
                                  Toast.makeText(context, "Connected to Space $spaceId! Welcome 💕", Toast.LENGTH_SHORT).show()
                                } catch (_: Exception) {
                                  Toast.makeText(context, "Connection error, retrying...", Toast.LENGTH_SHORT).show()
                                } finally {
                                  isLinking = false
                                }
                              }
                            }
                          } else {
                            Toast.makeText(context, "Please enter your partner's code to link", Toast.LENGTH_SHORT).show()
                          }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081))
                      ) {
                        if (isLinking) {
                          CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                          Spacer(Modifier.width(8.dp))
                          Text("Connecting Hearts...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        } else {
                          Icon(painterResource(R.drawable.favorite), null, modifier = Modifier.size(18.dp))
                          Spacer(Modifier.width(8.dp))
                          Text("Link Hearts & Connect 💕", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AvatarPill(name: String, label: String, isMe: Boolean, onClick: () -> Unit = {}) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick() }.padding(4.dp)
  ) {
    Box(
      modifier = Modifier
        .size(62.dp)
        .clip(CircleShape)
        .background(if (isMe) Color(0xFFFF4081) else Color(0xFF7C4DFF)),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = name.take(1).uppercase(),
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
      )
    }
    Spacer(Modifier.height(6.dp))
    Text(
      text = label,
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium,
      color = Color.White
    )
  }
}

@Composable
private fun StatColumn(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      fontSize = 20.sp,
      fontWeight = FontWeight.Bold,
      color = Color.White
    )
    Text(
      text = label,
      fontSize = 12.sp,
      color = Color.White.copy(alpha = 0.8f)
    )
  }
}

@Composable
private fun AnswerCard(author: String, text: String, isMe: Boolean) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    )
  ) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = author,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium
      )
      Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium
      )
    }
  }
}

@Composable
private fun LoveNoteCard(note: LoveNote, isMe: Boolean) {
  val dateFormatted = remember(note.timestamp) {
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(note.timestamp))
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (isMe) "You" else note.sender,
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.titleSmall,
          color = if (isMe) Color(0xFFFF4081) else Color(0xFF7C4DFF)
        )
        Text(
          text = dateFormatted,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Text(
        text = note.text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
    }
  }
}
