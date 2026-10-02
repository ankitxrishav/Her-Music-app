package com.fenrir.her.models

import com.music.innertube.models.YTItem
import com.fenrir.her.db.entities.LocalItem

data class SimilarRecommendation(
  val title: LocalItem,
  val items: List<YTItem>,
)
