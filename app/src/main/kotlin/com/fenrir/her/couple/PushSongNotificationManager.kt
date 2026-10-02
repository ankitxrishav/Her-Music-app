package com.fenrir.her.couple

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fenrir.her.MainActivity
import com.fenrir.her.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CouplePushSong(
  val id: String = "",
  val songId: String = "",
  val title: String = "",
  val artist: String = "",
  val thumbnailUrl: String? = null,
  val senderRole: String = "",
  val senderName: String = "",
  val message: String? = "Listen to this with me ❤️",
  val timestamp: Long = System.currentTimeMillis()
)

object PushSongNotificationManager {
  private const val CHANNEL_ID = "channel_couple_push_songs"
  private const val NOTIFICATION_ID = 8802

  fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val name = "Couple Push Songs"
      val descriptionText = "Notifications when your partner pushes a song to listen together"
      val importance = NotificationManager.IMPORTANCE_HIGH
      val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
        description = descriptionText
        enableVibration(true)
      }
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  fun showPushSongNotification(context: Context, pushSong: CouplePushSong) {
    createNotificationChannel(context)

    val contentIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra("navigate_to", "couple_space")
    }
    val contentPendingIntent = PendingIntent.getActivity(
      context,
      0,
      contentIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val playIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      action = "com.fenrir.her.PLAY_PUSHED_SONG"
      putExtra("EXTRA_SONG_ID", pushSong.songId)
      putExtra("pushed_song_id", pushSong.songId)
      putExtra("pushed_song_title", pushSong.title)
    }
    val playPendingIntent = PendingIntent.getActivity(
      context,
      1,
      playIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val senderDisplay = pushSong.senderName.ifBlank { "Your partner" }
    val title = "💖 $senderDisplay pushed a song!"
    val content = "${pushSong.title} • ${pushSong.artist}"
    val subText = pushSong.message?.ifBlank { "Listen together" } ?: "Listen together"

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.favorite)
      .setContentTitle(title)
      .setContentText(content)
      .setSubText(subText)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .setContentIntent(contentPendingIntent)
      .addAction(R.drawable.play, "▶ Listen Now", playPendingIntent)

    if (
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }
  }
}
