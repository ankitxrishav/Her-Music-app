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
import androidx.core.app.NotificationCompat
import com.fenrir.her.MainActivity
import com.fenrir.her.R
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
        currentSpaceId = dataStore.data.first()[CoupleSpaceIdKey].orEmpty()
      } catch (_: Exception) {}

      try {
        FirebaseCoupleSync.ensureConnected()
        if (currentSpaceId.isNotBlank()) {
          FirebaseCoupleSync.subscribeSpace(currentSpaceId)
        }
      } catch (_: Exception) {}

      startPolling()
      startPeriodicSync()
    }
  }

  private fun startPeriodicSync() {
    serviceScope.launch {
      while (isActive) {
        delay(30_000L)
        try {
          val spaceId = dataStore.data.first()[CoupleSpaceIdKey].orEmpty()
          if (spaceId != currentSpaceId) {
            currentSpaceId = spaceId
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
        val topics = buildList {
          add("her_music_broadcast_notify")
          add("her_music_broadcast_update")
          if (spaceId.isNotBlank()) {
            add("her_music_space_${spaceId}_notify")
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
              while (!source.exhausted() && isActive && spaceId == currentSpaceId) {
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
          if (topic == "her_music_broadcast_update") {
            UpdateNotificationHelper.showUpdateNotification(this, message)
          } else {
            CustomPushNotificationManager.showNotification(this, title, message)
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
