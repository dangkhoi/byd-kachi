package com.byd.clusternav.carexec

/**
 * Kết quả một lệnh shell trên xe. Chỉ là dữ liệu.
 *
 * Tách ra khỏi `CarExecShell` ngày 2026-07-27 khi review B1–B5 theo checklist: `CarExecCommands` có 276
 * dòng và **0** lần dùng dadb — logic thuần nằm trong module transport, đúng loại nhầm chuồng mà quy tắc
 * Q1 nói tới. Nó bị giữ ở đó chỉ vì phụ thuộc một kiểu kết quả; đưa kiểu đó xuống :core là đủ để giải phóng.
 */
data class ShellOutcome(
    val command: String,
    val exitCode: Int,
    val output: String,
    val elapsedMs: Long,
) {
    val ok: Boolean get() = exitCode == 0
}
