package com.fenrir.her.couple

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.fenrir.her.utils.dataStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

@kotlinx.serialization.Serializable
data class CoupleInvite(
  val code: String = "",
  val role: String = "",
  val name: String = "",
  val matched: Boolean = false,
  val partnerCode: String = "",
  val partnerName: String = "",
  val partnerRole: String = "",
  val spaceId: String = "",
  val timestamp: Long = 0L,
)

@kotlinx.serialization.Serializable
data class CoupleLivePlayback(
  val songId: String = "",
  val title: String = "",
  val artist: String = "",
  val thumbnailUrl: String? = null,
  val isPlaying: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val timestampEpochMs: Long = 0L,
  val senderRole: String = "",
  val senderName: String = "",
)

object FirebaseCoupleSync {
  private val brokers = listOf(
    "tcp://broker.emqx.io:1883",
    "tcp://broker.hivemq.com:1883",
    "tcp://test.mosquitto.org:1883"
  )

  private var client: MqttAsyncClient? = null
  private var brokerIndex = 0
  private var isConnecting = false
  private var appContext: Context? = null

  private val jsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  private val subscribedTopics = ConcurrentHashMap.newKeySet<String>()
  private val invitesCache = ConcurrentHashMap<String, CoupleInvite>()
  private val liveCache = ConcurrentHashMap<String, CoupleLivePlayback>()
  private val pushCache = ConcurrentHashMap<String, CouplePushSong>()
  private val chatCache = ConcurrentHashMap<String, MutableList<CoupleChatMessage>>()
  private val radioCache = ConcurrentHashMap<String, RadioBroadcastSession>()
  private val dailyCache = ConcurrentHashMap<String, Pair<String, String>>()
  private val deviceCache = ConcurrentHashMap<String, DeviceSyncPayload>()

  private val _liveFlow = MutableStateFlow<CoupleLivePlayback?>(null)
  val liveFlow: StateFlow<CoupleLivePlayback?> = _liveFlow.asStateFlow()

  private val _pushFlow = MutableStateFlow<CouplePushSong?>(null)
  val pushFlow: StateFlow<CouplePushSong?> = _pushFlow.asStateFlow()

  private val _deviceSyncFlow = MutableStateFlow<DeviceSyncPayload?>(null)
  val deviceSyncFlow: StateFlow<DeviceSyncPayload?> = _deviceSyncFlow.asStateFlow()

  private val _radioFlow = MutableStateFlow<RadioBroadcastSession?>(null)
  val radioFlow: StateFlow<RadioBroadcastSession?> = _radioFlow.asStateFlow()

  fun init(context: Context) {
    appContext = context.applicationContext
  }

  fun hashEmail(email: String): String {
    val clean = email.trim().lowercase()
    if (clean.isBlank()) return ""
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(clean.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }.take(16)
  }

  fun calculateSpaceId(codeA: String, codeB: String): String {
    val a = codeA.trim().uppercase()
    val b = codeB.trim().uppercase()
    return listOf(a, b).sorted().joinToString("-")
  }

  fun calculateEmailSpaceId(emailA: String, emailB: String): String {
    val a = hashEmail(emailA)
    val b = hashEmail(emailB)
    if (a.isBlank() || b.isBlank()) return ""
    return "couple_" + listOf(a, b).sorted().joinToString("_")
  }

  suspend fun ensureConnected(): Boolean = withContext(Dispatchers.IO) {
    if (client?.isConnected == true) return@withContext true
    if (isConnecting) return@withContext false
    isConnecting = true

    for (attempt in 0 until brokers.size) {
      val brokerUri = brokers[(brokerIndex + attempt) % brokers.size]
      try {
        val clientId = "her_android_" + System.currentTimeMillis() + "_" + (1000..9999).random()
        val c = MqttAsyncClient(brokerUri, clientId, MemoryPersistence())
        c.setCallback(object : MqttCallback {
          override fun connectionLost(cause: Throwable?) {
            subscribedTopics.clear()
          }

          override fun deliveryComplete(token: IMqttDeliveryToken?) {}

          override fun messageArrived(topic: String?, message: MqttMessage?) {
            val t = topic ?: return
            val body = message?.payload?.toString(Charsets.UTF_8) ?: return
            handleIncoming(t, body)
          }
        })

        val options = MqttConnectOptions().apply {
          isAutomaticReconnect = true
          isCleanSession = true
          connectionTimeout = 5
          keepAliveInterval = 30
        }

        val token = c.connect(options)
        token.waitForCompletion(4000)

        if (c.isConnected) {
          client = c
          brokerIndex = (brokerIndex + attempt) % brokers.size
          isConnecting = false
          resubscribeAll()
          return@withContext true
        }
      } catch (_: Exception) {}
    }

    isConnecting = false
    false
  }

  private fun resubscribeAll() {
    val topics = subscribedTopics.toList()
    subscribedTopics.clear()
    for (t in topics) {
      subscribe(t)
    }
  }

  fun subscribe(topic: String) {
    if (subscribedTopics.contains(topic)) return
    subscribedTopics.add(topic)
    try {
      if (client?.isConnected == true) {
        client?.subscribe(topic, 1)
      }
    } catch (_: Exception) {}
  }

  suspend fun publish(topic: String, payload: String, retain: Boolean = false): Boolean = withContext(Dispatchers.IO) {
    ensureConnected()
    val c = client ?: return@withContext false
    if (!c.isConnected) return@withContext false
    try {
      val message = MqttMessage(payload.toByteArray(Charsets.UTF_8)).apply {
        qos = 1
        isRetained = retain
      }
      val token = c.publish(topic, message)
      token.waitForCompletion(3000)
      true
    } catch (_: Exception) {
      false
    }
  }

  private fun handleIncoming(topic: String, payload: String) {
    if (payload.isBlank()) return
    try {
      when {
        topic.startsWith("her_music/space/") && topic.endsWith("/live") -> {
          val live = jsonParser.decodeFromString<CoupleLivePlayback>(payload)
          val spaceId = topic.split("/")[2]
          liveCache["${spaceId}_${live.senderRole.lowercase()}"] = live
          _liveFlow.value = live
        }
        topic.startsWith("her_music/space/") && topic.endsWith("/push") -> {
          val push = jsonParser.decodeFromString<CouplePushSong>(payload)
          val spaceId = topic.split("/")[2]
          pushCache[spaceId] = push
          _pushFlow.value = push
          appContext?.let { ctx ->
            PushSongNotificationManager.showPushSongNotification(ctx, push)
          }
        }
        topic.startsWith("her_music/space/") && topic.endsWith("/chat") -> {
          val chat = jsonParser.decodeFromString<CoupleChatMessage>(payload)
          val spaceId = topic.split("/")[2]
          val list = chatCache.getOrPut(spaceId) { mutableListOf() }
          synchronized(list) {
            if (list.none { it.id == chat.id }) {
              list.add(chat)
            }
          }
        }
        topic.startsWith("her_music/space/") && topic.endsWith("/radio") -> {
          val radio = RadioBroadcastSerialization.parse(payload)
          val spaceId = topic.split("/")[2]
          radioCache[spaceId] = radio
          _radioFlow.value = radio
          appContext?.let { ctx ->
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
              ctx.dataStore.edit { prefs ->
                prefs[com.fenrir.her.constants.CoupleRadioBroadcastKey] = payload
                if (radio.lastHeartEpochMs > 0L) {
                  prefs[com.fenrir.her.constants.CoupleHeartBurstEpochKey] = radio.lastHeartEpochMs
                }
              }
            }
          }
        }
        topic.startsWith("her_music/invite/") -> {
          val inv = jsonParser.decodeFromString<CoupleInvite>(payload)
          val code = topic.split("/").last()
          invitesCache[code] = inv
        }
        topic.startsWith("her_music/user/") && topic.endsWith("/device_sync") -> {
          val dev = jsonParser.decodeFromString<DeviceSyncPayload>(payload)
          deviceCache[dev.email] = dev
          _deviceSyncFlow.value = dev
        }
      }
    } catch (_: Exception) {}
  }

  suspend fun testConnection(customUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
    ensureConnected()
  }

  suspend fun registerInvite(code: String, role: String, name: String, customUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
    val clean = code.trim().uppercase()
    if (clean.isBlank()) return@withContext false
    subscribe("her_music/invite/$clean")
    val invite = CoupleInvite(
      code = clean,
      role = role,
      name = name,
      matched = false,
      timestamp = System.currentTimeMillis()
    )
    invitesCache[clean] = invite
    publish("her_music/invite/$clean", jsonParser.encodeToString(invite), retain = true)
  }

  suspend fun getInvite(code: String, customUrl: String? = null): CoupleInvite? = withContext(Dispatchers.IO) {
    val clean = code.trim().uppercase()
    if (clean.isBlank()) return@withContext null
    subscribe("her_music/invite/$clean")
    invitesCache[clean]
  }

  suspend fun checkInviteMatch(code: String, customUrl: String? = null): CoupleInvite? = withContext(Dispatchers.IO) {
    val clean = code.trim().uppercase()
    if (clean.isBlank()) return@withContext null
    subscribe("her_music/invite/$clean")
    val inv = invitesCache[clean]
    if (inv != null && inv.matched && inv.spaceId.isNotBlank()) inv else null
  }

  suspend fun linkInvite(
    partnerCode: String,
    myCode: String,
    myRole: String,
    myName: String,
    customUrl: String? = null
  ): Boolean = withContext(Dispatchers.IO) {
    val pCode = partnerCode.trim().uppercase()
    val mCode = myCode.trim().uppercase()
    if (pCode.isBlank() || mCode.isBlank()) return@withContext false

    val space = calculateSpaceId(mCode, pCode)
    val now = System.currentTimeMillis()

    val partnerUpdate = CoupleInvite(
      code = pCode,
      matched = true,
      partnerCode = mCode,
      partnerName = myName,
      partnerRole = myRole,
      spaceId = space,
      timestamp = now
    )
    invitesCache[pCode] = partnerUpdate
    publish("her_music/invite/$pCode", jsonParser.encodeToString(partnerUpdate), retain = true)

    val myUpdate = CoupleInvite(
      code = mCode,
      role = myRole,
      name = myName,
      matched = true,
      partnerCode = pCode,
      partnerRole = if (pCode.startsWith("HER")) "HER" else "HIM",
      spaceId = space,
      timestamp = now
    )
    invitesCache[mCode] = myUpdate
    publish("her_music/invite/$mCode", jsonParser.encodeToString(myUpdate), retain = true)
    true
  }

  suspend fun broadcastLivePlayback(
    spaceId: String,
    live: CoupleLivePlayback,
    customUrl: String? = null
  ): Boolean = withContext(Dispatchers.IO) {
    val clean = spaceId.trim()
    if (clean.isBlank()) return@withContext false
    val topic = "her_music/space/$clean/live"
    subscribe(topic)
    liveCache["${clean}_${live.senderRole.lowercase()}"] = live
    _liveFlow.value = live
    publish(topic, jsonParser.encodeToString(live), retain = true)
  }

  suspend fun fetchPartnerLivePlayback(
    spaceId: String,
    partnerRole: String,
    customUrl: String? = null
  ): CoupleLivePlayback? = withContext(Dispatchers.IO) {
    val clean = spaceId.trim()
    if (clean.isBlank()) return@withContext null
    val topic = "her_music/space/$clean/live"
    subscribe(topic)
    liveCache["${clean}_${partnerRole.lowercase()}"]
  }

  suspend fun fetchLivePlayback(spaceId: String, customUrl: String? = null): CoupleLivePlayback? = withContext(Dispatchers.IO) {
    val clean = spaceId.trim()
    if (clean.isBlank()) return@withContext null
    subscribe("her_music/space/$clean/live")
    liveCache["${clean}_her"] ?: liveCache["${clean}_him"]
  }

  suspend fun pushSong(spaceId: String, song: CouplePushSong, customUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
    val clean = spaceId.trim()
    if (clean.isBlank()) return@withContext false
    val topic = "her_music/space/$clean/push"
    subscribe(topic)
    pushCache[clean] = song
    _pushFlow.value = song
    publish(topic, jsonParser.encodeToString(song), retain = true)
  }

  suspend fun fetchLatestPushSong(spaceId: String, customUrl: String? = null): CouplePushSong? = withContext(Dispatchers.IO) {
    val clean = spaceId.trim()
    if (clean.isBlank()) return@withContext null
    subscribe("her_music/space/$clean/push")
    pushCache[clean]
  }

  suspend fun sendChatMessage(spaceId: String, message: CoupleChatMessage, customUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
    val clean = spaceId.trim()
    if (clean.isBlank()) return@withContext false
    val topic = "her_music/space/$clean/chat"
    subscribe(topic)
    val list = chatCache.getOrPut(clean) { mutableListOf() }
    synchronized(list) {
      if (list.none { it.id == message.id }) {
        list.add(message)
      }
    }
    publish(topic, jsonParser.encodeToString(message))
  }

  suspend fun fetchChatMessages(spaceId: String, customUrl: String? = null): List<CoupleChatMessage> = withContext(Dispatchers.IO) {
    val clean = spaceId.trim()
    if (clean.isBlank()) return@withContext emptyList()
    subscribe("her_music/space/$clean/chat")
    chatCache[clean]?.toList() ?: emptyList()
  }

  suspend fun broadcastRadio(spaceId: String, session: RadioBroadcastSession, customUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
    val clean = spaceId.trim()
    if (clean.isBlank()) return@withContext false
    val topic = "her_music/space/$clean/radio"
    subscribe(topic)
    radioCache[clean] = session
    publish(topic, RadioBroadcastSerialization.serialize(session), retain = true)
  }

  suspend fun fetchRadioBroadcast(spaceId: String, customUrl: String? = null): RadioBroadcastSession? = withContext(Dispatchers.IO) {
    val clean = spaceId.trim()
    if (clean.isBlank()) return@withContext null
    subscribe("her_music/space/$clean/radio")
    radioCache[clean]
  }

  suspend fun submitDailyAnswer(spaceId: String, dateKey: String, role: String, answer: String, customUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
    val clean = spaceId.trim()
    if (clean.isBlank()) return@withContext false
    val topic = "her_music/space/$clean/daily/$dateKey/${role.lowercase()}"
    publish(topic, answer, retain = true)
  }

  suspend fun fetchDailyAnswers(spaceId: String, dateKey: String, customUrl: String? = null): Pair<String, String>? = withContext(Dispatchers.IO) {
    dailyCache[dateKey]
  }

  suspend fun broadcastDeviceSync(payload: DeviceSyncPayload): Boolean = withContext(Dispatchers.IO) {
    if (payload.email.isBlank()) return@withContext false
    val hash = hashEmail(payload.email)
    val topic = "her_music/user/$hash/device_sync"
    subscribe(topic)
    deviceCache[payload.email] = payload
    _deviceSyncFlow.value = payload
    publish(topic, jsonParser.encodeToString(payload), retain = true)
  }

  fun subscribeDeviceSync(email: String) {
    if (email.isBlank()) return
    val hash = hashEmail(email)
    subscribe("her_music/user/$hash/device_sync")
  }

  fun getLatestDeviceSync(email: String): DeviceSyncPayload? {
    return deviceCache[email]
  }
}
