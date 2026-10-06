package com.byd.clusternav.launcher.testbridge

/**
 * 2.91 VOICE-APP-NAMES · A7 — ba lệnh cầu kiểm thử của *Dạy tên app* (spec §4.11). Khai ở tệp riêng vì
 * `TestBridgeCommand.kt` sát trần 500 dòng; [TestBridgeCommands.SPECS] nối danh sách này (một chỗ phân tích extra).
 *
 *  • `teach --es pkg <gói> [--es path <wav>] [--es op save]` — chạy WAV qua ĐÚNG đường giải mã của `wav` rồi
 *    `TeachSample` + `TeachGuard`; trả `heard` · `name` · `verdict`; `op=save` lưu qua cổng ViewModel như UI.
 *  • `teach_text --es pkg <gói> --es text <tên> [--es op save]` — tên gõ, cùng cổng.
 *  • `teach_clear [--es pkg <gói>]` — xoá tên đã dạy của một gói (vắng ⇒ của cả hồ sơ đang dùng).
 * Chỉ chạy khi test-mode bật (cùng cổng mọi lệnh). `op` chỉ nhận rỗng hoặc [OP_SAVE].
 */
object TestBridgeTeachCommands {
    const val TEACH = "teach"
    const val TEACH_TEXT = "teach_text"
    const val TEACH_CLEAR = "teach_clear"
    const val OP_SAVE = "save"

    val SPECS: List<TestBridgeCommands.Spec> = listOf(
        TestBridgeCommands.Spec(TEACH, listOf(TestBridgeCommands.EXTRA_PKG), listOf(TestBridgeCommands.EXTRA_PATH, TestBridgeCommands.EXTRA_OP)),
        TestBridgeCommands.Spec(TEACH_TEXT, listOf(TestBridgeCommands.EXTRA_PKG, TestBridgeCommands.EXTRA_TEXT), listOf(TestBridgeCommands.EXTRA_OP)),
        TestBridgeCommands.Spec(TEACH_CLEAR, emptyList(), listOf(TestBridgeCommands.EXTRA_PKG)),
    )

    val NAMES: Set<String> = SPECS.mapTo(LinkedHashSet()) { it.name }

    /** `op` của ba lệnh này: rỗng hoặc [OP_SAVE]; mọi thứ khác là lỗi (không đoán). */
    fun validOp(name: String, op: String): Boolean = name !in NAMES || op.isEmpty() || op == OP_SAVE
}
