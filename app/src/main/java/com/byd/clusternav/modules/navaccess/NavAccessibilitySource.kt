package com.byd.clusternav.modules.navaccess

/**
 * Snapshot những gì NavAccessibilityService đọc được trên màn GMaps (cự ly tới rẽ, đường, info đáy).
 * Service GHI, module navaccess ĐỌC để hiện debug + status. Thuần volatile, không khoá.
 */
object NavAccessibilitySource {
    @Volatile var connected = false       // service đã onServiceConnected chưa (đã bật trong Cài đặt)
    @Volatile var lastEventAt = 0L        // lần cuối có event GMaps (đang ở foreground?)
    @Volatile var lastReadAt = 0L         // lần cuối ĐỌC ĐƯỢC cự ly tới rẽ hợp lệ
    @Volatile var turnMeters = -1         // cự ly tới rẽ đọc trên màn (m)
    @Volatile var road = ""               // tên đường/lệnh kế đọc trên màn
    @Volatile var bottomInfo = ""         // dòng đáy thô (giờ tới · còn lại · phút) — debug, chưa đẩy cụm
    @Volatile var refines = 0L            // số lần đã tinh chỉnh interpolator (đếm để biết booster có tác dụng)

    fun fresh(now: Long): Boolean = lastReadAt > 0 && now - lastReadAt <= FRESH_MS
    fun foreground(now: Long): Boolean = lastEventAt > 0 && now - lastEventAt <= FRESH_MS

    const val FRESH_MS = 3000L
}
