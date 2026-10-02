package com.fenrir.her.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class AppIconType(val value: Int) {
  DEFAULT(0),
  LEGACY(1),
  STATIC(2),
  CAT(3),
  CRAZY_BLUE(4),
  POOKIE(5),
  SKY(6),
  ECHO_CAT(7),
  BILLIE_EILISH(13),
  SABRINA_CARPENTER(14),
  SABRINA_CARPENTER_2(15)
}

object IconUtils {
  fun setIcon(context: Context, iconType: AppIconType) {
    val pm = context.packageManager

    val dynamic = ComponentName(context, "com.fenrir.her.MainActivityAlias")
    val legacy = ComponentName(context, "com.fenrir.her.MainActivityLegacy")
    val static = ComponentName(context, "com.fenrir.her.MainActivityStatic")
    val cat = ComponentName(context, "com.fenrir.her.MainActivityCat")
    val crazyBlue = ComponentName(context, "com.fenrir.her.MainActivityCrazyBlue")
    val pookie = ComponentName(context, "com.fenrir.her.MainActivityPookie")
    val sky = ComponentName(context, "com.fenrir.her.MainActivitySky")
    val echoCat = ComponentName(context, "com.fenrir.her.MainActivityEchoCat")
    val billieEilish = ComponentName(context, "com.fenrir.her.MainActivityBillieEilish")
    val sabrina = ComponentName(context, "com.fenrir.her.MainActivitySabrina")
    val sabrina2 = ComponentName(context, "com.fenrir.her.MainActivitySabrina2")

    pm.setComponentEnabledSetting(
      dynamic,
      if (iconType == AppIconType.DEFAULT) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      legacy,
      if (iconType == AppIconType.LEGACY) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      static,
      if (iconType == AppIconType.STATIC) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      cat,
      if (iconType == AppIconType.CAT) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      crazyBlue,
      if (iconType == AppIconType.CRAZY_BLUE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      pookie,
      if (iconType == AppIconType.POOKIE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      sky,
      if (iconType == AppIconType.SKY) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      echoCat,
      if (iconType == AppIconType.ECHO_CAT) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      billieEilish,
      if (iconType == AppIconType.BILLIE_EILISH) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      sabrina,
      if (iconType == AppIconType.SABRINA_CARPENTER) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
    pm.setComponentEnabledSetting(
      sabrina2,
      if (iconType == AppIconType.SABRINA_CARPENTER_2) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP
    )
  }
}
