package com.byd.clusternav.navigation

/**
 * KÊNH ĐỌC dữ liệu dẫn đường của một nguồn (B3.57). Phân loại THUẦN theo roster [NavApps], KHÔNG hardcode tên
 * gói để rẽ nhánh hành vi (§7) — chỉ để NÓI ĐÚNG với người dùng nguồn đang dẫn được đọc bằng cách nào.
 *
 *  • [NOTIFICATION] — đọc qua kênh thông báo (`NavNotificationListener`): Google Maps (+ ReVanced). Đây là
 *    đường đã proven ngoài hiện trường, cần quyền "notification access".
 *  • [SCREEN_READ]  — đọc qua MÀN HÌNH: trợ năng (a11y content-desc/view-id) + chụp màn (glyph mũi tên).
 *    VietMap Live (Flutter, không có notification mang mũi tên) và họ Waze đi đường này.
 *  • [UNKNOWN]      — chưa có nguồn / gói lạ không trong roster.
 */
enum class NavReadChannel { NOTIFICATION, SCREEN_READ, UNKNOWN }

/**
 * PURE labels for the nav-source MENU (T3, spec `b3-full-nav-capture` R2). Lives in :core (JVM only, no Android)
 * next to [NavSourceMode] and [SourceArbiter], so it is unit-testable off-device (`LayeringRulesTest` keeps pure
 * logic out of :app). Two mappings kept in ONE place so the menu (mode → label) and the "active source" status
 * line ([SourceArbiter.activeSource] package → label) never drift apart.
 *
 * The package groups MUST stay in sync with [SourceArbiter] (its GMAPS/WAZE/VIETMAP package sets) — those are
 * the exact strings the arbiter publishes as `activeSource`. They are duplicated here (the arbiter's sets are
 * private) and covered by `NavSourceLabelsTest` so a divergence is caught off-car.
 */
object NavSourceLabels {

    // 08-22: KHÔNG còn là bản sao — cả đây lẫn SourceArbiter đều đọc [NavApps]. Comment cũ ghi "verified by
    // NavSourceLabelsTest" là SAI: test đó gọi shouldFeed ở chế độ AUTO, mà nhánh AUTO không đọc ba set này
    // dòng nào ⇒ xoá sạch cả ba set test vẫn xanh. Đã thay bằng assert thẳng trong test.
    private val GMAPS_PKGS = NavApps.GMAPS
    private val WAZE_PKGS = NavApps.WAZE
    private val VIETMAP_PKGS = NavApps.VIETMAP

    /** Brand label for a nav-source MODE ([NavSourceMode.AUTO]/`PREFER_*`). Unknown ⇒ Auto. */
    fun modeLabel(mode: Int): String = when (mode) {
        NavSourceMode.PREFER_GMAPS -> "Google Maps"
        NavSourceMode.PREFER_WAZE -> "Waze"
        NavSourceMode.PREFER_VIETMAP -> "VietMap"
        else -> "Auto"
    }

    /**
     * Brand label for the currently active source package ([SourceArbiter.activeSource]). Null ⇒ "" (caller
     * renders a "none" placeholder). An unrecognised package is returned verbatim so the status line is never
     * blank for a real, if unknown, navigator.
     */
    fun sourceLabel(activePkg: String?): String = when (activePkg) {
        null -> ""
        in GMAPS_PKGS -> "Google Maps"
        in WAZE_PKGS -> "Waze"
        in VIETMAP_PKGS -> "VietMap"
        else -> activePkg
    }

    /**
     * KÊNH ĐỌC của gói dẫn đang giữ phiên ([NavReadChannel]). Phân loại theo roster [NavApps] (§7 — theo phép
     * đo/roster, KHÔNG theo tên gói hardcode để rẽ hành vi). Dùng cho DÒNG TRẠNG THÁI: nói rõ nguồn được đọc
     * bằng THÔNG BÁO (GMaps) hay ĐỌC MÀN HÌNH (VietMap/Waze), để status không còn ngầm định "chỉ có notification".
     *
     * GMaps ưu tiên [NavReadChannel.NOTIFICATION] (đường proven, `NavApps.NOTIFICATION`); họ Waze + VietMap →
     * [NavReadChannel.SCREEN_READ]; null/gói lạ → [NavReadChannel.UNKNOWN].
     */
    fun readChannel(pkg: String?): NavReadChannel = when {
        pkg == null -> NavReadChannel.UNKNOWN
        pkg in NavApps.NOTIFICATION -> NavReadChannel.NOTIFICATION
        pkg in WAZE_PKGS || pkg in VIETMAP_PKGS -> NavReadChannel.SCREEN_READ
        else -> NavReadChannel.UNKNOWN
    }
}
