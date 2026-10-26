package com.byd.clusternav.automation

import android.content.Context
import com.byd.clusternav.launcher.WorkspacePrefs
import com.byd.clusternav.launcher.pendingNavRaw
import com.byd.clusternav.launcher.setPendingNavRaw
import com.byd.clusternav.launcher.telegram.PendingDestination
import com.byd.clusternav.launcher.telegram.PendingDestinations

/**
 * Lưu bền **điểm đến chờ** theo hồ sơ (spec `kachi-telegram-remote.html` R1 · R4). `:app` — chỉ đổi Context →
 * `SharedPreferences`; mọi phép mã hoá/kiểm ở `:core` [PendingDestinations].
 *
 * ## Dạng lưu: MỘT dòng trong khoá `pending_nav` (theo hồ sơ, qua [WorkspacePrefs])
 * ```
 * <requireGps 0|1>|<setAtMs>|<dòng PendingDestinations.encode>
 * ```
 * Trường đầu = cờ chờ-GPS (R5); trường hai = mốc đặt (`elapsedRealtime`-độc-lập: `System.currentTimeMillis`) để
 * [isStale] bỏ điểm đến quá cũ (P2-14: đặt tối thứ Hai không được dẫn sáng thứ Năm); phần sau = nội dung điểm
 * đến. Lưu bền qua prefs của xe ⇒ **sống qua lần BYD giết app lúc tắt máy**.
 *
 * ## Một-lần = XOÁ khoá khi consume
 * Không giữ cờ `consumed` riêng: [consume] xoá hẳn khoá ⇒ [read] trả `null` ⇒ [PendingNavPolicy] rơi về lịch.
 * Gọn hơn và không có trạng thái "đã dùng nhưng còn nằm đó" để ai đó đọc nhầm.
 */
object PendingNavStore {

    /** Điểm đến chờ quá [STALE_MS] (24h) ⇒ bỏ (P2-14). Dài đủ cho "đặt tối nay, đi sáng mai", ngắn đủ để không dẫn nhầm chuyến tuần sau. */
    const val STALE_MS = 24L * 60 * 60 * 1000

    data class Pending(val dest: PendingDestination, val requireGps: Boolean, val setAtMs: Long)

    /** Đọc điểm đến chờ của hồ sơ đang dùng; `null` = không có / đã consume / dòng hỏng. */
    fun read(ctx: Context): Pending? {
        val raw = prefs(ctx).pendingNavRaw() ?: return null
        val f = raw.split('|', limit = 3)
        if (f.size < 3) return null
        val requireGps = f[0].trim() == "1"
        val setAtMs = f[1].trim().toLongOrNull() ?: return null
        val dest = PendingDestinations.decode(f[2]) ?: return null
        return Pending(dest, requireGps, setAtMs)
    }

    /** `true` nếu điểm đến chờ đã quá hạn [STALE_MS] so với [nowMs]. */
    fun isStale(p: Pending, nowMs: Long): Boolean = nowMs - p.setAtMs > STALE_MS

    /** Đặt điểm đến chờ (ghi đè cái cũ — chỉ giữ MỘT điểm đến chờ tại một thời điểm). */
    fun write(ctx: Context, dest: PendingDestination, requireGps: Boolean, nowMs: Long = System.currentTimeMillis()) {
        val line = (if (requireGps) "1" else "0") + "|" + nowMs + "|" + PendingDestinations.encode(dest)
        prefs(ctx).setPendingNavRaw(line)
    }

    /** Một-lần: xoá khoá sau khi đã bắn (R4). */
    fun consume(ctx: Context) {
        prefs(ctx).setPendingNavRaw(null)
    }

    private fun prefs(ctx: Context) = WorkspacePrefs(ctx.applicationContext)
}
