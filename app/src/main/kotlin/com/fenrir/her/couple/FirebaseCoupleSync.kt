package com.fenrir.her.couple

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

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
  const val DEFAULT_BASE_URL = "https://her-music-53197-default-rtdb.firebaseio.com"
  private const val RELAY_BASE_URL = "https://ntfy.sh"
  private const val PROJECT_TAG = "her_sync_53197"
  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  private val client = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .writeTimeout(5, TimeUnit.SECONDS)
    .build()

  private val jsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  private var workingFirebaseUrl: String? = null
  private var lastFirebaseCheckTime: Long = 0L

  private val candidateFirebaseUrls = listOf(
    "https://her-music-53197-default-rtdb.firebaseio.com",
    "https://her-music-53197-default-rtdb.asia-southeast1.firebasedatabase.app",
    "https://her-music-53197-default-rtdb.europe-west1.firebasedatabase.app",
    "https://her-music-53197.firebaseio.com"
  )

  fun getBaseUrl(customUrl: String? = null): String {
    val clean = customUrl?.trim().orEmpty()
    if (clean.isNotBlank() && (clean.startsWith("http://") || clean.startsWith("https://"))) {
      return clean.removeSuffix("/")
    }
    return workingFirebaseUrl ?: DEFAULT_BASE_URL
  }

  private suspend fun getWorkingFirebase(customUrl: String? = null): String? = withContext(Dispatchers.IO) {
    val custom = customUrl?.trim().orEmpty()
    if (custom.isNotBlank() && (custom.startsWith("http://") || custom.startsWith("https://"))) {
      return@withContext custom.removeSuffix("/")
    }
    val now = System.currentTimeMillis()
    if (workingFirebaseUrl != null && (now - lastFirebaseCheckTime) < 60_000L) {
      return@withContext workingFirebaseUrl
    }
    lastFirebaseCheckTime = now
    for (url in candidateFirebaseUrls) {
      try {
        val req = Request.Builder().url("$url/.json?shallow=true").get().build()
        client.newCall(req).execute().use { res ->
          if (res.isSuccessful || res.code == 401) {
            workingFirebaseUrl = url
            return@withContext url
          }
        }
      } catch (_: Exception) {}
    }
    null
  }

  private suspend fun publishRelay(topicSuffix: String, payload: String): Boolean = withContext(Dispatchers.IO) {
    try {
      val cleanTopic = "${PROJECT_TAG}_${topicSuffix.replace(Regex("[^a-zA-Z0-9_-]"), "_")}"
      val url = "$RELAY_BASE_URL/$cleanTopic"
      val req = Request.Builder()
        .url(url)
        .post(payload.toRequestBody(jsonMediaType))
        .build()
      client.newCall(req).execute().use { it.isSuccessful }
    } catch (_: Exception) {
      false
    }
  }

  private suspend fun pollRelay(topicSuffix: String, sinceAll: Boolean = false): List<String> = withContext(Dispatchers.IO) {
    try {
      val cleanTopic = "${PROJECT_TAG}_${topicSuffix.replace(Regex("[^a-zA-Z0-9_-]"), "_")}"
      val url = "$RELAY_BASE_URL/$cleanTopic/json?poll=1${if (sinceAll) "&since=all" else ""}"
      val req = Request.Builder().url(url).get().build()
      client.newCall(req).execute().use { res ->
        if (!res.isSuccessful) return@withContext emptyList()
        val text = res.body?.string().orEmpty()
        text.lineSequence()
          .filter { it.isNotBlank() }
          .mapNotNull { line ->
            try {
              val elem = jsonParser.parseToJsonElement(line)
              if (elem is JsonObject && elem["event"]?.jsonPrimitive?.content == "message") {
                elem["message"]?.jsonPrimitive?.content
              } else null
            } catch (_: Exception) {
              null
            }
          }
          .toList()
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  suspend fun testConnection(customUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
    try {
      val fbUrl = getWorkingFirebase(customUrl)
      if (fbUrl != null) return@withContext true
      val req = Request.Builder().url("$RELAY_BASE_URL/${PROJECT_TAG}_health").head().build()
      client.newCall(req).execute().use { it.isSuccessful }
    } catch (_: Exception) {
      false
    }
  }

  suspend fun registerInvite(code: String, role: String, name: String, customUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
    if (code.isBlank()) return@withContext false
    try {
      val invite = CoupleInvite(
        code = code,
        role = role,
        name = name,
        matched = false,
        timestamp = System.currentTimeMillis()
      )
      val payload = jsonParser.encodeToString(CoupleInvite.serializer(), invite)
      publishRelay("inv_$code", payload)

      val fb = getWorkingFirebase(customUrl)
      if (fb != null) {
        val url = "$fb/invites/$code.json"
        val request = Request.Builder().url(url).put(payload.toRequestBody(jsonMediaType)).build()
        client.newCall(request).execute().close()
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  suspend fun getInvite(code: String, customUrl: String? = null): CoupleInvite? = withContext(Dispatchers.IO) {
    if (code.isBlank()) return@withContext null
    try {
      val fb = getWorkingFirebase(customUrl)
      if (fb != null) {
        val url = "$fb/invites/$code.json"
        val request = Request.Builder().url(url).get().build()
        val fromFb = client.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            val body = response.body?.string()
            if (!body.isNullOrBlank() && body != "null") {
              jsonParser.decodeFromString(CoupleInvite.serializer(), body)
            } else null
          } else null
        }
        if (fromFb != null) return@withContext fromFb
      }

      val relayMessages = pollRelay("inv_$code")
      relayMessages.lastOrNull()?.let { msg ->
        try {
          return@withContext jsonParser.decodeFromString(CoupleInvite.serializer(), msg)
        } catch (_: Exception) {}
      }
      null
    } catch (_: Exception) {
      null
    }
  }

  suspend fun checkInviteMatch(code: String, customUrl: String? = null): CoupleInvite? = withContext(Dispatchers.IO) {
    if (code.isBlank()) return@withContext null
    try {
      val fb = getWorkingFirebase(customUrl)
      if (fb != null) {
        val url = "$fb/invites/$code.json"
        val req = Request.Builder().url(url).get().build()
        val fromFb = client.newCall(req).execute().use { response ->
          if (response.isSuccessful) {
            val body = response.body?.string()
            if (!body.isNullOrBlank() && body != "null") {
              val inv = jsonParser.decodeFromString(CoupleInvite.serializer(), body)
              if (inv.matched && inv.spaceId.isNotBlank()) inv else null
            } else null
          } else null
        }
        if (fromFb != null) return@withContext fromFb
      }

      val relayMessages = pollRelay("inv_$code")
      for (msg in relayMessages.reversed()) {
        try {
          val inv = jsonParser.decodeFromString(CoupleInvite.serializer(), msg)
          if (inv.matched && inv.spaceId.isNotBlank()) {
            return@withContext inv
          }
        } catch (_: Exception) {}
      }
      null
    } catch (_: Exception) {
      null
    }
  }

  suspend fun linkInvite(
    partnerCode: String,
    myCode: String,
    myRole: String,
    myName: String,
    customUrl: String? = null
  ): Boolean = withContext(Dispatchers.IO) {
    if (partnerCode.isBlank() || myCode.isBlank()) return@withContext false
    try {
      val space = com.fenrir.her.auth.CoupleAuthGuard.calculateSpaceId(myCode, partnerCode)
      val now = System.currentTimeMillis()

      val partnerUpdate = CoupleInvite(
        code = partnerCode,
        matched = true,
        partnerCode = myCode,
        partnerName = myName,
        partnerRole = myRole,
        spaceId = space,
        timestamp = now
      )
      val partnerJson = jsonParser.encodeToString(CoupleInvite.serializer(), partnerUpdate)
      publishRelay("inv_$partnerCode", partnerJson)

      val myUpdate = CoupleInvite(
        code = myCode,
        role = myRole,
        name = myName,
        matched = true,
        partnerCode = partnerCode,
        spaceId = space,
        timestamp = now
      )
      val myJson = jsonParser.encodeToString(CoupleInvite.serializer(), myUpdate)
      publishRelay("inv_$myCode", myJson)
      publishRelay("spc_${space}_info", """{"spaceId":"$space","createdAt":$now}""")

      val fb = getWorkingFirebase(customUrl)
      if (fb != null) {
        val urlPartner = "$fb/invites/$partnerCode.json"
        val reqPartner = Request.Builder().url(urlPartner).patch(partnerJson.toRequestBody(jsonMediaType)).build()
        client.newCall(reqPartner).execute().close()

        val urlMine = "$fb/invites/$myCode.json"
        val reqMine = Request.Builder().url(urlMine).put(myJson.toRequestBody(jsonMediaType)).build()
        client.newCall(reqMine).execute().close()

        val urlSpace = "$fb/spaces/$space/info.json"
        val reqSpace = Request.Builder().url(urlSpace).put("""{"spaceId":"$space","createdAt":$now}""".toRequestBody(jsonMediaType)).build()
        client.newCall(reqSpace).execute().close()
      }

      true
    } catch (_: Exception) {
      false
    }
  }

  suspend fun pushSong(spaceId: String, song: CouplePushSong, customUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
    if (spaceId.isBlank()) return@withContext false
    try {
      val payload = jsonParser.encodeToString(CouplePushSong.serializer(), song)
      publishRelay("spc_${spaceId}_push", payload)

      val fb = getWorkingFirebase(customUrl)
      if (fb != null) {
        val url = "$fb/spaces/$spaceId/push_song.json"
        val request = Request.Builder().url(url).put(payload.toRequestBody(jsonMediaType)).build()
        client.newCall(request).execute().close()
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  suspend fun fetchLatestPushSong(spaceId: String, customUrl: String? = null): CouplePushSong? = withContext(Dispatchers.IO) {
    if (spaceId.isBlank()) return@withContext null
    try {
      val fb = getWorkingFirebase(customUrl)
      if (fb != null) {
        val url = "$fb/spaces/$spaceId/push_song.json"
        val request = Request.Builder().url(url).get().build()
        val fromFb = client.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            val body = response.body?.string()
            if (!body.isNullOrBlank() && body != "null") {
              jsonParser.decodeFromString(CouplePushSong.serializer(), body)
            } else null
          } else null
        }
        if (fromFb != null) return@withContext fromFb
      }

      val msgs = pollRelay("spc_${spaceId}_push")
      msgs.lastOrNull()?.let {
        try {
          return@withContext jsonParser.decodeFromString(CouplePushSong.serializer(), it)
        } catch (_: Exception) {}
      }
      null
    } catch (_: Exception) {
      null
    }
  }

  suspend fun sendChatMessage(spaceId: String, message: CoupleChatMessage, customUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
    if (spaceId.isBlank()) return@withContext false
    try {
      val payload = jsonParser.encodeToString(CoupleChatMessage.serializer(), message)
      publishRelay("spc_${spaceId}_chat", payload)

      val fb = getWorkingFirebase(customUrl)
      if (fb != null) {
        val url = "$fb/spaces/$spaceId/messages/${message.id}.json"
        val request = Request.Builder().url(url).put(payload.toRequestBody(jsonMediaType)).build()
        client.newCall(request).execute().close()
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  suspend fun fetchChatMessages(spaceId: String, customUrl: String? = null): List<CoupleChatMessage> = withContext(Dispatchers.IO) {
    if (spaceId.isBlank()) return@withContext emptyList()
    val result = mutableMapOf<String, CoupleChatMessage>()
    try {
      val fb = getWorkingFirebase(customUrl)
      if (fb != null) {
        val url = "$fb/spaces/$spaceId/messages.json"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            val body = response.body?.string()
            if (!body.isNullOrBlank() && body != "null") {
              val element = jsonParser.parseToJsonElement(body)
              if (element is JsonObject) {
                element.values.forEach {
                  try {
                    val msg = jsonParser.decodeFromString(CoupleChatMessage.serializer(), it.toString())
                    result[msg.id] = msg
                  } catch (_: Exception) {}
                }
              }
            }
          }
        }
      }

      val relayMsgs = pollRelay("spc_${spaceId}_chat", sinceAll = true)
      for (line in relayMsgs) {
        try {
          val msg = jsonParser.decodeFromString(CoupleChatMessage.serializer(), line)
          result[msg.id] = msg
        } catch (_: Exception) {}
      }

      result.values.sortedBy { it.timestamp }
    } catch (_: Exception) {
      result.values.sortedBy { it.timestamp }
    }
  }

  suspend fun broadcastRadio(spaceId: String, session: RadioBroadcastSession, customUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
    if (spaceId.isBlank()) return@withContext false
    try {
      val payload = RadioBroadcastSerialization.serialize(session)
      publishRelay("spc_${spaceId}_radio", payload)

      val fb = getWorkingFirebase(customUrl)
      if (fb != null) {
        val url = "$fb/spaces/$spaceId/radio.json"
        val request = Request.Builder().url(url).put(payload.toRequestBody(jsonMediaType)).build()
        client.newCall(request).execute().close()
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  suspend fun fetchRadioBroadcast(spaceId: String, customUrl: String? = null): RadioBroadcastSession? = withContext(Dispatchers.IO) {
    if (spaceId.isBlank()) return@withContext null
    try {
      val fb = getWorkingFirebase(customUrl)
      if (fb != null) {
        val url = "$fb/spaces/$spaceId/radio.json"
        val request = Request.Builder().url(url).get().build()
        val fromFb = client.newCall(request).execute().use { response ->
          if (response.isSuccessful) {
            val body = response.body?.string()
            if (!body.isNullOrBlank() && body != "null") {
              RadioBroadcastSerialization.parse(body)
            } else null
          } else null
        }
        if (fromFb != null) return@withContext fromFb
      }

      val msgs = pollRelay("spc_${spaceId}_radio")
      msgs.lastOrNull()?.let {
        return@withContext RadioBroadcastSerialization.parse(it)
      }
      null
    } catch (_: Exception) {
      null
    }
  }

  suspend fun submitDailyAnswer(spaceId: String, dateKey: String, role: String, answer: String, customUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
    if (spaceId.isBlank() || dateKey.isBlank()) return@withContext false
    try {
      val isHer = role.equals("HER", ignoreCase = true) || role.equals("RITIKA", ignoreCase = true)
      val payload = """{"role":"${if (isHer) "HER" else "HIM"}","answer":${jsonParser.encodeToString(answer)},"timestamp":${System.currentTimeMillis()}}"""
      publishRelay("spc_${spaceId}_daily_$dateKey", payload)

      val fb = getWorkingFirebase(customUrl)
      if (fb != null) {
        val field = if (isHer) "herAnswer" else "himAnswer"
        val patchPayload = "{\"$field\": ${jsonParser.encodeToString(answer)}}"
        val url = "$fb/spaces/$spaceId/daily_answers/$dateKey.json"
        val request = Request.Builder().url(url).patch(patchPayload.toRequestBody(jsonMediaType)).build()
        client.newCall(request).execute().close()
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  suspend fun fetchDailyAnswers(spaceId: String, dateKey: String, customUrl: String? = null): Pair<String, String>? = withContext(Dispatchers.IO) {
    if (spaceId.isBlank() || dateKey.isBlank()) return@withContext null
    try {
      var him = ""
      var her = ""
      val fb = getWorkingFirebase(customUrl)
      if (fb != null) {
        val url = "$fb/spaces/$spaceId/daily_answers/$dateKey.json"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { response ->
          if (response.isSuccessful) {
            val body = response.body?.string()
            if (!body.isNullOrBlank() && body != "null") {
              val element = jsonParser.parseToJsonElement(body)
              if (element is JsonObject) {
                him = element["himAnswer"]?.jsonPrimitive?.content ?: element["ankitAnswer"]?.jsonPrimitive?.content ?: ""
                her = element["herAnswer"]?.jsonPrimitive?.content ?: element["ritikaAnswer"]?.jsonPrimitive?.content ?: ""
              }
            }
          }
        }
      }

      val msgs = pollRelay("spc_${spaceId}_daily_$dateKey", sinceAll = true)
      for (line in msgs) {
        try {
          val elem = jsonParser.parseToJsonElement(line)
          if (elem is JsonObject) {
            val r = elem["role"]?.jsonPrimitive?.content.orEmpty()
            val ans = elem["answer"]?.jsonPrimitive?.content.orEmpty()
            if (r.equals("HER", ignoreCase = true) || r.equals("RITIKA", ignoreCase = true)) {
              if (ans.isNotBlank()) her = ans
            } else {
              if (ans.isNotBlank()) him = ans
            }
          }
        } catch (_: Exception) {}
      }

      if (him.isNotBlank() || her.isNotBlank()) Pair(him, her) else null
    } catch (_: Exception) {
      null
    }
  }

  suspend fun broadcastLivePlayback(
    spaceId: String,
    playback: CoupleLivePlayback,
    customUrl: String? = null
  ): Boolean = withContext(Dispatchers.IO) {
    if (spaceId.isBlank()) return@withContext false
    try {
      val roleKey = playback.senderRole.lowercase()
      val payload = jsonParser.encodeToString(CoupleLivePlayback.serializer(), playback)
      publishRelay("spc_${spaceId}_pb_$roleKey", payload)

      val fb = getWorkingFirebase(customUrl)
      if (fb != null) {
        val url = "$fb/spaces/$spaceId/playback_$roleKey.json"
        val req = Request.Builder().url(url).put(payload.toRequestBody(jsonMediaType)).build()
        client.newCall(req).execute().close()
        val legUrl = "$fb/spaces/$spaceId/live_playback.json"
        val legReq = Request.Builder().url(legUrl).put(payload.toRequestBody(jsonMediaType)).build()
        client.newCall(legReq).execute().close()
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  suspend fun fetchPartnerLivePlayback(
    spaceId: String,
    partnerRole: String,
    customUrl: String? = null
  ): CoupleLivePlayback? = withContext(Dispatchers.IO) {
    if (spaceId.isBlank()) return@withContext null
    try {
      val roleKey = partnerRole.lowercase()
      val fb = getWorkingFirebase(customUrl)
      if (fb != null) {
        val url = "$fb/spaces/$spaceId/playback_$roleKey.json"
        val req = Request.Builder().url(url).get().build()
        val fromFb = client.newCall(req).execute().use { response ->
          if (response.isSuccessful) {
            val body = response.body?.string()
            if (!body.isNullOrBlank() && body != "null") {
              jsonParser.decodeFromString(CoupleLivePlayback.serializer(), body)
            } else null
          } else null
        }
        if (fromFb != null) return@withContext fromFb
      }

      val msgs = pollRelay("spc_${spaceId}_pb_$roleKey")
      msgs.lastOrNull()?.let {
        try {
          return@withContext jsonParser.decodeFromString(CoupleLivePlayback.serializer(), it)
        } catch (_: Exception) {}
      }
      null
    } catch (_: Exception) {
      null
    }
  }

  suspend fun fetchLivePlayback(
    spaceId: String,
    customUrl: String? = null
  ): CoupleLivePlayback? = withContext(Dispatchers.IO) {
    fetchPartnerLivePlayback(spaceId, "", customUrl)
  }
}

