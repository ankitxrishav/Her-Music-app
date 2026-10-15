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

object CoupleSyncNotificationManager {
  private const val CHANNEL_ID = "channel_couple_sync_requests"
  private const val NOTIFICATION_ID = 8803

  fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val name = "Couple Listen Together"
      val descriptionText = "Notifications for real-time couple playback sync invitations"
      val importance = NotificationManager.IMPORTANCE_HIGH
      val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
        description = descriptionText
        enableVibration(true)
        setShowBadge(true)
      }
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  fun showSyncRequestNotification(context: Context, req: CoupleSyncRequest, spaceId: String) {
    createNotificationChannel(context)

    val contentIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      action = "com.fenrir.her.OPEN_SYNC_REQUEST"
      putExtra("EXTRA_SPACE_ID", spaceId)
      putExtra("EXTRA_REQ_ID", req.requestId)
      putExtra("EXTRA_SONG_ID", req.songId)
      putExtra("EXTRA_SONG_TITLE", req.title)
      putExtra("EXTRA_SONG_ARTIST", req.artist)
      putExtra("EXTRA_POSITION_MS", req.positionMs)
      putExtra("EXTRA_REQUESTER_NAME", req.requesterName)
      putExtra("EXTRA_REQUESTER_ROLE", req.requesterRole)
    }
    val contentPendingIntent = PendingIntent.getActivity(
      context,
      101,
      contentIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val acceptIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      action = "com.fenrir.her.ACCEPT_SYNC_REQUEST"
      putExtra("EXTRA_SPACE_ID", spaceId)
      putExtra("EXTRA_REQ_ID", req.requestId)
      putExtra("EXTRA_SONG_ID", req.songId)
      putExtra("EXTRA_SONG_TITLE", req.title)
      putExtra("EXTRA_SONG_ARTIST", req.artist)
      putExtra("EXTRA_POSITION_MS", req.positionMs)
      putExtra("EXTRA_REQUESTER_NAME", req.requesterName)
      putExtra("EXTRA_REQUESTER_ROLE", req.requesterRole)
    }
    val acceptPendingIntent = PendingIntent.getActivity(
      context,
      102,
      acceptIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val declineIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      action = "com.fenrir.her.DECLINE_SYNC_REQUEST"
      putExtra("EXTRA_SPACE_ID", spaceId)
      putExtra("EXTRA_REQ_ID", req.requestId)
      putExtra("EXTRA_REQUESTER_NAME", req.requesterName)
      putExtra("EXTRA_REQUESTER_ROLE", req.requesterRole)
    }
    val declinePendingIntent = PendingIntent.getActivity(
      context,
      103,
      declineIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val senderDisplay = req.requesterName.ifBlank { "Your partner" }
    val title = "💕 $senderDisplay invited you to Listen Together!"
    val content = if (req.title.isNotBlank()) "${req.title} • ${req.artist}" else "Tap to sync in real time"

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.favorite)
      .setContentTitle(title)
      .setContentText(content)
      .setSubText("Listen Together 💕")
      .setStyle(
        NotificationCompat.BigTextStyle()
          .bigText(if (req.title.isNotBlank()) "${req.title}\n${req.artist}\n\nTap Accept to join and sync both phones in real time 💕" else "Tap Accept to join and sync playback together 💕")
          .setSummaryText("Her • $senderDisplay")
      )
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setColor(0xFFE91E63.toInt())
      .setColorized(true)
      .setVibrate(longArrayOf(0, 350, 150, 350))
      .setAutoCancel(true)
      .setContentIntent(contentPendingIntent)
      .addAction(R.drawable.play, "Accept 💕", acceptPendingIntent)
      .addAction(R.drawable.close, "Decline", declinePendingIntent)

    if (
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }
  }

  fun dismissNotification(context: Context) {
    try {
      NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    } catch (_: Exception) {}
  }
}
