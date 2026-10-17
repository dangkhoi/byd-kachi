package com.byd.clusternav.launcher.testbridge

import com.byd.clusternav.navigation.HudRoadNameMode

/**
 * ═══ 3.1.12 · HUD-ROADNAME-ALT-PATH — lệnh `hudroad`: đọc/đổi "Đường gửi tên đường lên HUD" ══════════════════════════════
 *
 * `am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST
 *  --es cmd hudroad [--es op status|a|a48|b|c] [--ez auto_confirm true]` — `op` vắng ⇒ `status`. `b`/`c` dẫn tới lượt GỬI
 * bản ghi vào TestDevice qua ClusterDebug ⇒ cần `--ez auto_confirm true` (cùng cổng `hal set`). Chỉ chạy sau công tắc *Chế độ
 * kiểm thử qua adb* (cổng receiver). Không cần màn chính (chỉ prefs). Tách tệp như [TestBridgeScreenCommands] (trần 500 dòng).
 */
object TestBridgeHudRoadCommands {

    const val HUDROAD = "hudroad"
    const val OP_STATUS = "status"

    val SPECS: List<TestBridgeCommands.Spec> = listOf(
        TestBridgeCommands.Spec(HUDROAD, emptyList(), listOf(TestBridgeCommands.EXTRA_OP, TestBridgeCommands.EXTRA_AUTO_CONFIRM)),
    )

    /** Op thật (vắng ⇒ [OP_STATUS]). */
    fun opOf(op: String): String = op.trim().lowercase().ifEmpty { OP_STATUS }

    /** Chế độ mà op muốn đặt, hoặc `null` = chỉ đọc ([OP_STATUS]). */
    fun modeOf(op: String): HudRoadNameMode? = HudRoadNameMode.of(opOf(op))

    /** Op dẫn tới GỬI qua ClusterDebug ⇒ phải có `auto_confirm`. */
    fun needsConfirm(op: String): Boolean = modeOf(op)?.clusterDebug == true

    /** Phép kiểm ở TẦNG PHÂN TÍCH: mã lỗi, hoặc `null` = hợp lệ / không phải lệnh này. */
    fun check(name: String, extras: Map<String, Any?>): String? {
        if (name != HUDROAD) return null
        val op = opOf((extras[TestBridgeCommands.EXTRA_OP] as? String).orEmpty())
        return if (op == OP_STATUS || HudRoadNameMode.of(op) != null) null else TestBridgeCommands.ERR_BAD_OP + op
    }
}
