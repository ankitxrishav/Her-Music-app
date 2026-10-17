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

object CustomPushNotificationManager {
  private const val CHANNEL_ID = "channel_custom_push"
  private const val NOTIFICATION_ID = 8804

  fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val name = "Her Custom Notifications"
      val descriptionText = "Custom messages and alerts for Her"
      val importance = NotificationManager.IMPORTANCE_HIGH
      val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
        description = descriptionText
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 300, 150, 300)
        enableLights(true)
        lightColor = 0xFFE91E63.toInt()
        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
      }
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  fun showNotification(context: Context, title: String, message: String, sender: String? = null) {
    createNotificationChannel(context)

    val notifId = (System.currentTimeMillis() % 100000).toInt() + 1000
    val contentIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra("navigate_to", "couple_space")
    }
    val contentPendingIntent = PendingIntent.getActivity(
      context,
      notifId,
      contentIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val displayTitle = if (!sender.isNullOrBlank()) "$sender: $title" else title

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.favorite)
      .setContentTitle(displayTitle)
      .setContentText(message)
      .setStyle(NotificationCompat.BigTextStyle().bigText(message))
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_MESSAGE)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setColor(0xFFE91E63.toInt())
      .setColorized(true)
      .setAutoCancel(true)
      .setContentIntent(contentPendingIntent)

    if (
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    ) {
      NotificationManagerCompat.from(context).notify(notifId, builder.build())
    }
  }
}
