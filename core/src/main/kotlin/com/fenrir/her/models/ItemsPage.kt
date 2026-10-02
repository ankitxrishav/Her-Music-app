package com.fenrir.her.models

import com.music.innertube.models.YTItem

data class ItemsPage(
  val items: List<YTItem>,
  val continuation: String?,
)
