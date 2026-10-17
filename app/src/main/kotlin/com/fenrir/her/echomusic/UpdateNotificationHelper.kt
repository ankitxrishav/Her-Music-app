package com.fenrir.her.echomusic

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.fenrir.her.MainActivity
import com.fenrir.her.R

import com.fenrir.her.BuildConfig
import com.fenrir.her.echomusic.updater.isNewerVersion

object UpdateNotificationHelper {
  private const val CHANNEL_ID = "updates"
  private const val NOTIFICATION_ID = 1001

  fun showUpdateNotification(context: Context, versionName: String) {
    if (!isNewerVersion(versionName, BuildConfig.VERSION_NAME)) return
    val nm = context.getSystemService(NotificationManager::class.java)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          context.getString(R.string.app_updates_title),
          NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = "Her Music update alerts"
          enableVibration(true)
          vibrationPattern = longArrayOf(0, 300, 150, 300)
          enableLights(true)
          lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
      nm.createNotificationChannel(channel)
    }

    val appIntent = Intent(context, MainActivity::class.java).apply {
      action = "com.fenrir.her.action.UPDATE"
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    val appPending = PendingIntent.getActivity(context, NOTIFICATION_ID, appIntent, flags)

    val downloadIntent = Intent(
      Intent.ACTION_VIEW,
      "https://github.com/ankitxrishav/Her-Music-app/releases/latest".toUri()
    )
    val downloadPending = PendingIntent.getActivity(context, NOTIFICATION_ID + 1, downloadIntent, flags)

    val notif =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.favorite)
        .setContentTitle(context.getString(R.string.update_available_title))
        .setContentText("Her Music $versionName is available. Tap to download and install.")
        .setStyle(
          NotificationCompat.BigTextStyle()
            .bigText("Her Music $versionName is available. Tap to download and install the newest release.")
        )
        .setContentIntent(appPending)
        .addAction(R.drawable.deployed_app_update, "Update Now", appPending)
        .addAction(android.R.drawable.stat_sys_download, "Direct Download", downloadPending)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setColor(0xFFE91E63.toInt())
        .build()

    if (
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
    ) {
      NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notif)
    }
  }
}
