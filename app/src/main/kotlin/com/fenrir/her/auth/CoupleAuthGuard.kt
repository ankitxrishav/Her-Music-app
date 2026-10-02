package com.fenrir.her.auth

import java.util.UUID

object CoupleAuthGuard {
  enum class CoupleRole {
    HIM,
    HER,
    ANKIT,
    RITIKA
  }

  fun isWhitelisted(email: String?): Boolean = true

  fun resolveRole(email: String?, selectedProfile: String?): CoupleRole {
    val prof = selectedProfile?.trim()?.uppercase().orEmpty()
    if (prof == "HER" || prof == "RITIKA") return CoupleRole.HER
    return CoupleRole.HIM
  }

  fun generatePairCode(role: CoupleRole): String {
    val prefix = if (role == CoupleRole.HER || role == CoupleRole.RITIKA) "HER" else "HIM"
    val randomPart = UUID.randomUUID().toString().replace("-", "").take(4).uppercase()
    return "$prefix-$randomPart"
  }

  fun calculateSpaceId(codeA: String, codeB: String): String {
    val cleanA = codeA.trim().uppercase()
    val cleanB = codeB.trim().uppercase()
    return listOf(cleanA, cleanB).sorted().joinToString("-")
  }

  fun getMyName(role: CoupleRole, customName: String? = null): String {
    if (!customName.isNullOrBlank()) return customName
    return if (role == CoupleRole.HER || role == CoupleRole.RITIKA) "Her" else "Him"
  }

  fun getPartnerName(role: CoupleRole, customPartnerName: String? = null): String {
    if (!customPartnerName.isNullOrBlank()) return customPartnerName
    return if (role == CoupleRole.HER || role == CoupleRole.RITIKA) "Him" else "Her"
  }
}
