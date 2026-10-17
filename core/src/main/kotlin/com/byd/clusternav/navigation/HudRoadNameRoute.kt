package com.byd.clusternav.navigation

/**
 * ═══ 3.1.12 · HUD-ROADNAME-ALT-PATH — chọn ĐƯỜNG GỬI tên đường lên HUD (người dùng đo, không rẽ nhánh theo đời xe) ════════
 *
 * Owner 17/10: *"SL6 đã lên mũi tên + khoảng cách, nên làm thêm đường nữa để SL6 thử được"*. [ĐO anh em] HUD SL6 hiện mũi tên +
 * cự ly qua HAL của Kachi nhưng KHÔNG bao giờ hiện tên đường, dù Kachi ghi tên đúng như SDK OEM `sendNextPathName`
 * (`set(0x43FA1008, UTF-16LE)`, doc `hud-clusterdebug-path-2026-10-16.md` §4). Nên thêm đường thử, để chính người lái chọn:
 *
 * | Chế độ | Tên đường đi đâu | Ghi chú |
 * |---|---|---|
 * | [A] (mặc định) | HAL `0x43FA1008` + oversea + SDK, ≤ 127 đơn vị | y hệt 3.1.11 |
 * | [A48] | như A nhưng cắt ≤ 48 đơn vị | [ĐOÁN] trần HAL thật là 96 byte (chú thích DiAuto); OEM AmapService gửi nguyên văn, SDK Java nhận ≤ 255 byte ⇒ KHÔNG đổi mặc định |
 * | [B] | CHỈ ClusterDebug (khúc 7 byte, [ClusterDebugRoadName]) | cách DiAuto đi |
 * | [C] | cả A và B | |
 *
 * Mũi tên + cự ly + trạng thái dẫn LUÔN ở đường HAL, mọi chế độ. Chọn B/C mà xe không có ClusterDebug dùng được ⇒ [plan] lùi về
 * A và đánh dấu [Plan.degraded] (degrade-to-resolvable: dòng tình trạng nói rõ, không im lặng mất tên).
 */
enum class HudRoadNameMode(val code: String, val halMaxBytes: Int, val clusterDebug: Boolean) {
    A("a", HudPathName.MAX_BYTES, false),
    A48("a48", ClusterDebugRoadName.MAX_UNITS * 2, false),
    B("b", 0, true),
    C("c", HudPathName.MAX_BYTES, true),
    ;

    companion object {
        val DEFAULT = A

        /** Mã lưu/mã lệnh → chế độ; lạ/vắng ⇒ `null` (người gọi tự quyết: prefs ⇒ [DEFAULT], lệnh ⇒ lỗi). */
        fun of(code: String?): HudRoadNameMode? {
            val c = code?.trim()?.lowercase() ?: return null
            return values().firstOrNull { it.code == c }
        }
    }
}

object HudRoadNameRoute {

    /** Kế hoạch cho MỘT lượt ghi tên: [halMaxBytes] = 0 ⇒ không ghi tên qua HAL; [clusterDebug] ⇒ gửi khúc qua ClusterDebug. */
    data class Plan(val mode: HudRoadNameMode, val halMaxBytes: Int, val clusterDebug: Boolean, val degraded: Boolean) {
        val hal: Boolean get() = halMaxBytes > 0

        companion object {
            /** Đường của 3.1.11 (A) — dùng ở keep-alive và ở nơi gọi không có Context. */
            val DEFAULT = Plan(HudRoadNameMode.A, HudPathName.MAX_BYTES, clusterDebug = false, degraded = false)
        }
    }

    fun plan(mode: HudRoadNameMode, clusterDebugAvailable: Boolean): Plan =
        if (mode.clusterDebug && !clusterDebugAvailable) {
            Plan(mode, HudPathName.MAX_BYTES, clusterDebug = false, degraded = true)
        } else {
            Plan(mode, mode.halMaxBytes, mode.clusterDebug, degraded = false)
        }
}

/**
 * Khoá prefs (tệp `clusternav_prefs`, `PrefsHudRoadName.kt` ở `:app`). THEO XE: đường nào hiện được tên là sự thật phần cứng/
 * coding của CHIẾC XE này (SL6 khác Seal), không phải sở thích của một tài xế. `ProfileScope.DEVICE_KEYS` cộng [DEVICE_KEYS].
 */
object HudRoadNameKeys {
    const val MODE_KEY = "hud_road_name_mode"

    val DEVICE_KEYS: Map<String, String> = mapOf(
        MODE_KEY to "3.1.12 — 'Đường gửi tên đường lên HUD' (A/A48/B/C). Theo XE: đường nào HUD nhận được là sự thật coding/" +
            "phần cứng của chiếc xe này (SL6 hiện mũi tên mà không hiện tên, Seal khoá HUD) — đổi hồ sơ không được đổi nó",
    )
}
