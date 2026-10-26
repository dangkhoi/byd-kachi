package com.byd.clusternav.launcher.automation

import com.byd.clusternav.launcher.telegram.PendingDestination

/**
 * ═══ LUẬT ƯU TIÊN ĐIỂM ĐẾN CHỜ — trái tim usecase TELEGRAM-REMOTE ════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-telegram-remote.html` R2 · R3 · R4 · R5. Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm
 * off-car.
 *
 * ## Vấn đề
 * Owner đặt điểm đến từ điện thoại TRƯỚC khi lên xe. Lên xe, [com.byd...trip.TripStart] phải **ưu tiên** điểm đó
 * hơn [ScheduledNavRule] (lịch dẫn đường tự động): *"có telegram pending thì ưu tiên"* (owner 26/10).
 *
 * ## Ba tính chất, ba cơ chế tái dùng
 *  - **Ưu tiên** = chỗ gọi đọc pending TRƯỚC, [resolve] trả [Decision.FireNav] ⇒ **short-circuit**, không gọi
 *    [ScheduledNavApplier]. (R2)
 *  - **Overwrite lịch** = sau khi bắn, chỗ gọi đóng dấu [NavAutomationFired] của NGÀY bằng [BLOCK_KEY] ⇒ mọi
 *    luật lịch cùng chuyến bị chặn im (tái dùng cơ chế "một luật một ngày" sẵn có). (R3) — [blockScheduleFired]
 *    dựng bản đồ đã-dẫn để chỗ gọi lưu.
 *  - **Một-lần** = [Decision.FireNav] kèm lệnh [Decision.FireNav] ⇒ chỗ gọi gọi `consume`. Pending bắn rồi là
 *    hết ⇒ nó chỉ thắng CHUYẾN SẮP TỚI, các khung lịch sau trong ngày vẫn chạy (owner 26/10: "một lần thôi").
 *    (R4)
 *
 * ## Chờ GPS — R5
 * Pending mang cờ `requireGps` (giống [ScheduledNavRule.requireGps]). `requireGps == true` mà GPS chưa sẵn ⇒
 * [Decision.WaitForGps]: để nguyên pending, nhịp `TripStart`/periodic thử lại khi có định vị. Không xin quyền vị
 * trí mới (tái dùng cổng đã có của lịch).
 *
 * ## Vì sao thuần, nhận `gpsOk: Boolean?`
 * Đọc `LocationManager` là việc `:app`. Ở đây nhận kết quả đã đọc: `true` = có định vị · `false` = chưa · `null`
 * = chưa cấp quyền/không đọc được. `null`/`false` với `requireGps` ⇒ CHỜ (không dẫn mù), cùng hướng sai an toàn
 * của [ScheduledNavPolicy] (`gpsOk != false` sẽ dẫn trong hầm — ở đây chỉ dẫn khi `== true`).
 */
object PendingNavPolicy {

    sealed class Decision {
        /** Có pending dùng được + GPS thoả ⇒ bắn dẫn cho [pending]. Chỗ gọi: expand → đóng dấu → consume. */
        data class FireNav(val pending: PendingDestination) : Decision()

        /** Pending đòi GPS mà chưa có ⇒ giữ nguyên, thử lại nhịp sau. KHÔNG consume, KHÔNG chạm lịch. */
        object WaitForGps : Decision()

        /** Không pending (hoặc đã dùng) ⇒ chỗ gọi chạy [ScheduledNavApplier] như cũ. */
        object FallThroughToSchedule : Decision()
    }

    /**
     * Quyết định của một nhịp.
     *
     * @param pending điểm đến chờ đã đọc (null = không có / đã consume ⇒ [Decision.FallThroughToSchedule]).
     * @param requireGps pending này có đòi GPS không (cờ lưu cùng pending ở `:app`).
     * @param gpsOk `true` có định vị · `false` chưa · `null` chưa cấp quyền/không đọc (xem KDoc lớp).
     */
    fun resolve(pending: PendingDestination?, requireGps: Boolean, gpsOk: Boolean?): Decision {
        if (pending == null) return Decision.FallThroughToSchedule
        if (requireGps && gpsOk != true) return Decision.WaitForGps
        return Decision.FireNav(pending)
    }

    /**
     * Bản đồ đã-dẫn dùng để **overwrite lịch** — chặn CHỈ các luật thuộc CHUYẾN NÀY (owner 26/10: *"một lần thôi"*).
     *
     * Đóng dấu `todayKey` cho các luật đang **trong khung giờ + đúng thứ** lúc này ([ScheduledNavRules.inWindow]) ⇒
     * [ScheduledNavPolicy] bỏ chúng lượt này; luật của khung giờ KHÁC trong ngày (vd 17:30 "về nhà") KHÔNG bị chạm,
     * vẫn chạy bình thường. Đây là chỗ bản đầu sai (senior review P1-3): đóng dấu MỌI id = chặn cả ngày.
     *
     * Hợp nhất với sổ đã có (giữ dấu các ngày khác). Chỗ gọi lưu bản đồ trả về vào [NavAutomationFired].
     *
     * @param existing sổ đã-dẫn hiện tại ([NavAutomationFired.EMPTY] nếu chưa có).
     * @param rules mọi luật lịch đang có (từ [NavAutomationBook]).
     * @param nowMinOfDay phút-trong-ngày hiện tại · @param dow thứ ISO-8601 (1=T2…7=CN).
     * @param todayKey khoá ngày hôm nay (quy ước `yyyy-MM-dd` của `:app`).
     */
    fun blockScheduleFired(
        existing: Map<String, String>,
        rules: List<ScheduledNavRule>,
        nowMinOfDay: Int,
        dow: Int,
        todayKey: String,
    ): Map<String, String> {
        if (todayKey.isBlank()) return existing
        val out = existing.toMutableMap()
        rules.filter { it.enabled && dow in it.days && ScheduledNavRules.inWindow(it, nowMinOfDay) }
            .forEach { if (it.id.isNotBlank()) out[it.id] = todayKey }
        return out
    }
}
