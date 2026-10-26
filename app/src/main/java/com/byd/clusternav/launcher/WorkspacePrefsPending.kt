package com.byd.clusternav.launcher

/**
 * Điểm đến chờ (TELEGRAM-REMOTE) — tách khỏi [WorkspacePrefs] theo VAI (trần 500 dòng), cùng cách
 * `WorkspacePrefsSnapshot.kt`. Hàm mở rộng của [WorkspacePrefs]: dùng `sp` + `key()` (internal) nên vẫn cùng tệp
 * prefs `kachi_workspace` + cùng con trỏ hồ sơ.
 *
 * Khoá `pending_nav` theo HỒ SƠ (mỗi tài xế một điểm đến chờ). Đọc/ghi chuỗi thô; mã hoá/giải mã ở `:core`
 * ([com.byd.clusternav.launcher.telegram.PendingDestinations]) qua [com.byd.clusternav.automation.PendingNavStore].
 */

private const val PENDING_NAV_SUFFIX = "pending_nav"

/** Chuỗi thô điểm đến chờ của hồ sơ đang dùng; `null` = chưa đặt / đã consume. */
fun WorkspacePrefs.pendingNavRaw(): String? = sp.stringOrNull(key(PENDING_NAV_SUFFIX))

/** Ghi (hoặc xoá khi `null`/rỗng) chuỗi điểm đến chờ. */
fun WorkspacePrefs.setPendingNavRaw(v: String?) {
    val e = sp.edit()
    if (v.isNullOrBlank()) e.remove(key(PENDING_NAV_SUFFIX)) else e.putString(key(PENDING_NAV_SUFFIX), v)
    e.apply()
}
