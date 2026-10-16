package com.byd.clusternav.launcher.testbridge

/**
 * ═══ 3.1.11 · CLUSTER-NOW-PLAYING — lệnh `np`: bật/tắt công tắc "Tên bài hát lên cụm đồng hồ" + đọc tình trạng ═════════════
 *
 * `am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST
 *  --es cmd np [--es op status|on|off] [--ez auto_confirm true]` — `op` vắng ⇒ `status`. `on` dẫn tới lượt GHI HAL cụm ⇒ cần
 * `--ez auto_confirm true` (cùng cổng `hal set`). Chỉ chạy sau công tắc *Chế độ kiểm thử qua adb* (cổng receiver). Không cần
 * màn chính (chỉ prefs + luồng `kachi-np`). Tách tệp như [TestBridgeScreenCommands] (trần 500 dòng).
 */
object TestBridgeNowPlayingCommands {

    const val NP = "np"
    const val OP_STATUS = "status"
    const val OP_ON = "on"
    const val OP_OFF = "off"

    val OPS: Set<String> = setOf(OP_STATUS, OP_ON, OP_OFF)

    val SPECS: List<TestBridgeCommands.Spec> = listOf(
        TestBridgeCommands.Spec(NP, emptyList(), listOf(TestBridgeCommands.EXTRA_OP, TestBridgeCommands.EXTRA_AUTO_CONFIRM)),
    )

    /** Op thật của lệnh (vắng ⇒ [OP_STATUS]). */
    fun opOf(op: String): String = op.trim().lowercase().ifEmpty { OP_STATUS }

    /** Phép kiểm ở TẦNG PHÂN TÍCH: mã lỗi, hoặc `null` = hợp lệ / không phải lệnh này. */
    fun check(name: String, extras: Map<String, Any?>): String? {
        if (name != NP) return null
        val op = opOf((extras[TestBridgeCommands.EXTRA_OP] as? String).orEmpty())
        return if (op in OPS) null else TestBridgeCommands.ERR_BAD_OP + op
    }
}
