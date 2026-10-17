package com.fenrir.her.couple

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.datastore.preferences.core.edit
import androidx.core.app.NotificationCompat
import com.fenrir.her.MainActivity
import com.fenrir.her.R
import com.fenrir.her.constants.CoupleMyCodeKey
import com.fenrir.her.constants.CoupleSpaceIdKey
import com.fenrir.her.echomusic.UpdateNotificationHelper
import com.fenrir.her.utils.dataStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class HerNotificationService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

  private val seenNotificationIds = ConcurrentHashMap.newKeySet<String>()
  private val isWorkStarted = AtomicBoolean(false)

  @Volatile private var currentSpaceId: String = ""
  @Volatile private var currentPairCode: String = ""
  @Volatile private var lastTime: Long = (System.currentTimeMillis() / 1000L) - 300L
  @Volatile private var currentCall: Call? = null

  private var pollingJob: Job? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    startForegroundServiceInternal()
    startServiceWork()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForegroundServiceInternal()
    startServiceWork()
    return START_STICKY
  }

  override fun onDestroy() {
    serviceScope.cancel()
    currentCall?.cancel()
    stopForeground(STOP_FOREGROUND_REMOVE)
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun startForegroundServiceInternal() {
    try {
      val notification = buildForegroundNotification()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
    } catch (_: Exception) {
      stopSelf()
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Her Connection",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Persistent connection for Her Music"
        setShowBadge(false)
        setSound(null, null)
        enableVibration(false)
      }
      val nm = getSystemService(NotificationManager::class.java)
      nm?.createNotificationChannel(channel)
    }
  }

  private fun buildForegroundNotification(): Notification {
    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.favorite)
      .setContentTitle("Her is connected 💕")
      .setOngoing(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setContentIntent(pendingIntent)
      .build()
  }

  private fun startServiceWork() {
    if (!isWorkStarted.compareAndSet(false, true)) return

    serviceScope.launch {
      try {
        val prefs = dataStore.data.first()
        currentSpaceId = prefs[CoupleSpaceIdKey].orEmpty()
        currentPairCode = prefs[CoupleMyCodeKey].orEmpty()
      } catch (_: Exception) {}

      try {
        FirebaseCoupleSync.ensureConnected()
        if (currentSpaceId.isNotBlank()) {
          FirebaseCoupleSync.subscribeSpace(currentSpaceId)
        }
      } catch (_: Exception) {}

      startPolling()
      startDataStoreObserver()
    }
  }

  private fun startDataStoreObserver() {
    serviceScope.launch {
      dataStore.data.collect { prefs ->
        try {
          val spaceId = prefs[CoupleSpaceIdKey].orEmpty()
          val pairCode = prefs[CoupleMyCodeKey].orEmpty()
          if (spaceId != currentSpaceId || pairCode != currentPairCode) {
            currentSpaceId = spaceId
            currentPairCode = pairCode
            if (spaceId.isNotBlank()) {
              FirebaseCoupleSync.subscribeSpace(spaceId)
            }
            restartPolling()
          }
          FirebaseCoupleSync.ensureConnected()
        } catch (_: Exception) {}
      }
    }
  }

  private fun restartPolling() {
    lastTime = (System.currentTimeMillis() / 1000L) - 300L
    try {
      currentCall?.cancel()
    } catch (_: Exception) {}
    startPolling()
  }

  private fun startPolling() {
    pollingJob?.cancel()
    pollingJob = serviceScope.launch {
      var backoffMs = 2_000L
      while (isActive) {
        val spaceId = currentSpaceId
        val pairCode = currentPairCode
        val topics = buildList {
          add("her_music_broadcast_notify")
          add("her_music_broadcast_update")
          if (pairCode.isNotBlank()) {
            add("her_music_invite_${pairCode}")
            add("her_music_invite_${pairCode}_notify")
          }
          if (spaceId.isNotBlank()) {
            add("her_music_space_${spaceId}_notify")
            add("her_music_space_${spaceId}_sync_req")
            add("her_music_space_${spaceId}_sync_resp")
            add("her_music_space_${spaceId}_mood")
            add("her_music_space_${spaceId}_live_event")
          }
        }
        val url = "https://ntfy.sh/${topics.joinToString(",")}/json?since=$lastTime"
        val request = Request.Builder().url(url).build()
        val call = httpClient.newCall(request)
        currentCall = call

        try {
          call.execute().use { response ->
            if (response.isSuccessful) {
              backoffMs = 2_000L
              val source = response.body.source()
              while (!source.exhausted() && isActive && spaceId == currentSpaceId && pairCode == currentPairCode) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank() || line.startsWith(":")) continue
                handleNtfyMessage(line)
              }
            } else {
              delay(backoffMs)
              backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
            }
          }
        } catch (_: Exception) {
          if (isActive) {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
          }
        } finally {
          currentCall = null
        }
      }
    }
  }

  private val jsonParser = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  private fun handleNtfyMessage(line: String) {
    try {
      val obj = JSONObject(line)
      if (obj.optString("event") == "message") {
        val id = obj.optString("id")
        if (id.isNotBlank() && !seenNotificationIds.add(id)) return
        if (seenNotificationIds.size > 1000) {
          seenNotificationIds.clear()
          if (id.isNotBlank()) seenNotificationIds.add(id)
        }
        val t = obj.optLong("time", 0L)
        if (t > lastTime) lastTime = t
        val topic = obj.optString("topic")
        val title = obj.optString("title", "Her Music")
        val message = obj.optString("message", "")
        if (message.isNotBlank()) {
          when {
            topic == "her_music_broadcast_update" -> {
              val cleanVersion = message.removePrefix("v").removePrefix("b").trim()
              val currentVersion = com.fenrir.her.BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("b").trim()
              if (com.fenrir.her.echomusic.updater.isNewerVersion(cleanVersion, currentVersion)) {
                UpdateNotificationHelper.showUpdateNotification(this, message)
              }
            }
            topic.startsWith("her_music_invite_") && !topic.endsWith("_notify") -> {
              try {
                val parsed = jsonParser.decodeFromString<CoupleInvite>(message)
                if (parsed.matched && parsed.spaceId.isNotBlank()) {
                  serviceScope.launch {
                    dataStore.edit { prefs ->
                      prefs[com.fenrir.her.constants.CoupleIsPairedKey] = true
                      prefs[com.fenrir.her.constants.CouplePartnerCodeKey] = parsed.partnerCode
                      if (parsed.partnerName.isNotBlank()) {
                        prefs[com.fenrir.her.constants.CouplePartnerNameKey] = parsed.partnerName
                      }
                      prefs[com.fenrir.her.constants.CoupleSpaceIdKey] = parsed.spaceId
                      prefs[com.fenrir.her.constants.CoupleLinkedTimestampKey] = parsed.timestamp
                    }
                  }
                  CustomPushNotificationManager.showNotification(
                    this,
                    "💕 Partner Connected!",
                    "${parsed.partnerName.ifBlank { "Partner" }} linked with your space! Welcome to your space together 💕"
                  )
                }
              } catch (_: Exception) {}
            }
            topic.endsWith("_sync_req") -> {
              try {
                val syncReq = jsonParser.decodeFromString<CoupleSyncRequest>(message)
                FirebaseCoupleSync.onSyncRequestReceived(syncReq)
                CoupleSyncNotificationManager.showSyncRequestNotification(this, syncReq, currentSpaceId)
              } catch (_: Exception) {}
            }
            topic.endsWith("_sync_resp") -> {
              try {
                val syncResp = jsonParser.decodeFromString<CoupleSyncResponse>(message)
                FirebaseCoupleSync.onSyncResponseReceived(syncResp)
              } catch (_: Exception) {}
            }
            topic.endsWith("_mood") -> {
              try {
                val mood = jsonParser.decodeFromString<CoupleMood>(message)
                serviceScope.launch {
                  dataStore.edit { prefs ->
                    prefs[com.fenrir.her.constants.CouplePartnerMoodEmojiKey] = mood.emoji
                    prefs[com.fenrir.her.constants.CouplePartnerMoodTextKey] = mood.moodText
                    prefs[com.fenrir.her.constants.CouplePartnerMoodTimestampKey] = mood.timestamp
                  }
                }
              } catch (_: Exception) {}
            }
            topic.endsWith("_live_event") -> {
              try {
                val live = jsonParser.decodeFromString<CoupleLivePlayback>(message)
                CouplePlaybackSyncManager.applyLiveEvent(live)
              } catch (_: Exception) {}
            }
            else -> {
              CustomPushNotificationManager.showNotification(this, title, message)
            }
          }
        }
      }
    } catch (_: Exception) {}
  }

  companion object {
    private const val CHANNEL_ID = "channel_her_persistent"
    private const val NOTIFICATION_ID = 9901

    fun start(context: Context) {
      try {
        val intent = Intent(context, HerNotificationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (_: Exception) {}
    }

    fun stop(context: Context) {
      try {
        val intent = Intent(context, HerNotificationService::class.java)
        context.stopService(intent)
      } catch (_: Exception) {}
    }
  }
}
