package com.byd.clusternav

import android.graphics.Bitmap

/**
 * Parse THUẦN notification dẫn đường -> NavState (không deref Android service -> test được).
 * Vai trò field KHÁC nhau giữa app:
 *   GMaps : android.title = khoảng cách ("250 m"), android.text = đường/lệnh kế.
 *   VietMap: có bản ĐẢO (đường ở title, khoảng cách ở text) -> dò DIST ở CẢ title lẫn text.
 */
object NotificationParser {
    private val DIST = Regex("""\b(\d+([.,]\d+)?)\s?(km|m)\b""", RegexOption.IGNORE_CASE)

    fun parse(pkg: String, title: String, text: String, sub: String, big: String, arrow: Bitmap?, maneuverIcon: Int = -1): NavState? {
        if (title.isEmpty() && text.isEmpty()) return null

        val distance: String
        val road: String
        val maneuver: String
        // GMaps: title = khoảng cách, text = đường/lệnh. Nguồn field-ĐẢO (vd VietMap): cự ly ở text, đường ở title.
        val distInTitle = extractDistance(title)
        val distInText = if (distInTitle == null) extractDistance(text) else null
        distance = distInTitle ?: distInText ?: title
        // Nếu cự ly lấy được từ TEXT (field-đảo) → đường/lệnh nằm ở TITLE; mặc định (GMaps) đường/lệnh ở text.
        val roadSrc = if (distInText != null) title else text
        road = roadSrc
        maneuver = roadSrc
        val eta = sub.ifEmpty { big }

        return NavState(
            active = true,
            arrow = arrow,
            distance = distance,
            road = road,
            maneuverText = maneuver,
            maneuverIcon = maneuverIcon,
            eta = eta,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun extractDistance(s: String): String? {
        if (s.isEmpty()) return null
        val m = DIST.find(s) ?: return null
        return m.value.replace(",", ".")
    }
}
