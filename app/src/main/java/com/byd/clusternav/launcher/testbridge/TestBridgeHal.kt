package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.util.Log
import com.byd.clusternav.launcher.BydHalGateway
import com.byd.clusternav.launcher.HalBindingTable
import com.byd.clusternav.launcher.HalWriteProbe

/**
 * ═══ T-BRIDGE · LỆNH `hal` — GỌI MỘT METHOD HAL BYDAuto THÔ (chẩn đoán cơ chế) ═══════════════════════════════
 *
 * Spec `docs/specs/kachi-test-bridge.html` §9 (grab-list) + `docs/diagnostics/oncar-captest-results-2026-09-15.md`.
 *
 * ## Vì sao cần, khi đã có `ctl`
 * `ctl` đi qua `ControlRegistry` → `HalBindingTable.writeArgs`, nên nó chỉ bắn được **value đã ánh xạ** (kính:
 * mở=1/đóng=2) và **không có đường đọc getter**. Khi một control "xanh mà vẫn hỏng" (kính đóng chu-kỳ-2 không
 * ăn dù `rc=0`), ta cần đọc `getWindowPermitState`/`getWindowState`/`getWindowOpenPercent` và thử state khác
 * (STOP=3…) — thứ `ctl` không làm được. Đây đúng là "đầu dò shell-thô trên xe thật" mà CLAUDE.md §14 đòi TRƯỚC
 * khi mã hoá một fix thành policy: không đoán giá trị đóng, mà ĐO trực tiếp trên xe đang đỗ.
 *
 * ## Đi qua ĐÚNG một gateway — không mở reflection thứ hai
 * Dùng [BydHalGateway] (bọc [com.byd.clusternav.modules.hal.BydHal]) y như [HalBindingTable] thật, nên câu chữ
 * HAL (`rc=…`/`no permission`/off-car) chụp qua [HalWriteProbe] giống hệt `ctl`. KHÔNG tự `Class.forName` ở đây.
 *
 * ## Cổng an toàn (CLAUDE.md §4·§6)
 *  • Cả cầu chỉ chạy khi chế độ kiểm thử BẬT (tự tắt 60 phút — [KachiTestBridge]).
 *  • Lượt **ghi** (`set`) chạm thân xe ⇒ **từ chối** nếu thiếu `--ez auto_confirm true`, và luôn để lại dấu
 *    `AUTO-CONFIRM` trong logcat — CÙNG cổng mà `ctl`/`say` dùng cho control mở/khoá thân xe.
 *  • Lượt **đọc** (`get`) không đổi state ⇒ không cần confirm.
 */
internal object TestBridgeHal {

    /** Device mặc định khi lệnh không nói `--es dev`: thân xe (kính/cửa/đèn/rèm) — nơi mọi ca "xanh mà hỏng" đang nằm. */
    const val DEFAULT_DEVICE = "BYDAutoBodyworkDevice"

    /** Lượt `set` (ghi thân xe) thiếu `--ez auto_confirm true` — xem cổng CONFIRM ở [TestBridgeCtl.ERR_NEEDS_CONFIRM]. */
    const val ERR_NEEDS_CONFIRM = "needs_confirm"

    /** Chỉ đường cho người đo khi bị chặn CONFIRM — ASCII (một lệnh để gõ, không phải chữ trên màn). */
    const val NOTE_CONFIRM = "them --ez auto_confirm true de ghi (set) mot method HAL than xe"

    /** `--es args` có phần tử không phải số nguyên — nối phần sai để người đo biết gõ nhầm chỗ nào. */
    const val ERR_BAD_ARGS = "bad_hal_args:"

    fun run(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        val simpleDev = cmd.dev.ifBlank { DEFAULT_DEVICE }
        val fqn = HalBindingTable.deviceFqn(simpleDev)
        val isGet = when (cmd.op) {
            "get" -> true
            "set" -> false
            // Suy theo tiền tố `get` khi lệnh không nói rõ — sát cách người đọc nghĩ về HAL (`getWindowState`=đọc).
            else -> cmd.method.startsWith("get", ignoreCase = true)
        }

        val args = parseArgs(cmd.halArgs)
        if (args == null) {
            reply.fail(ERR_BAD_ARGS + cmd.halArgs)
            return
        }

        val gateway = BydHalGateway(app)

        if (isGet) {
            // Getter BydHal nhận 0/1 int arg (getWindowState(int) / getWindowPermitState()). arg đầu hoặc null.
            val value = gateway.getter(fqn, cmd.method, args.firstOrNull())
            reply.ok(
                listOf(
                    "op" to "get",
                    "dev" to simpleDev,
                    "fqn" to fqn,
                    "method" to cmd.method,
                    "arg" to (args.firstOrNull()?.toString() ?: ""),
                    "value" to (value ?: ""),
                    "reply" to if (value != null) "ok: read" else "unavailable: null (off-car / method absent / no permission)",
                ),
            )
            return
        }

        // ── set: lượt GHI thân xe → cổng CONFIRM ────────────────────────────────────────────────
        if (!cmd.autoConfirm) {
            reply.fail(ERR_NEEDS_CONFIRM, "dev" to simpleDev, "method" to cmd.method, "hint" to NOTE_CONFIRM)
            return
        }
        Log.i(TestBridgeReply.TAG, "AUTO-CONFIRM: hal set $simpleDev.${cmd.method}(${args.joinToString(",")})")

        // Cùng giao thức đọc câu chữ HAL với `ctl`: mốc seq TRƯỚC clear để không nhận nhầm lượt ghi khác chen vào.
        val mark = HalWriteProbe.mark()
        HalWriteProbe.clear()
        val rc = gateway.namedInt(fqn, cmd.method, args)
        val outcome = HalWriteProbe.last
        val halLine = if (outcome != null && outcome.seq > mark) outcome.raw else TestBridgeCtl.HAL_UNMAPPED
        reply.ok(
            listOf(
                "op" to "set",
                "dev" to simpleDev,
                "fqn" to fqn,
                "method" to cmd.method,
                "args" to args.joinToString(","),
                "rc" to (rc?.toString() ?: ""),
                "hal_line" to halLine,
                "reply" to when {
                    halLine.contains("permission", ignoreCase = true) -> "denied: no permission"
                    halLine == "off_car" -> "unavailable: off-car/emulator"
                    rc != null -> "ok: HAL accepted (rc valid)"
                    else -> "rejected: $halLine"
                },
            ),
        )
    }

    /** `"1,2"` → `[1,2]`; `""` → `[]`; phần tử không-số ⇒ null (lỗi có tên). Cùng luật parse ASCII của cầu. */
    private fun parseArgs(csv: String): IntArray? {
        if (csv.isBlank()) return IntArray(0)
        val parts = csv.split(',').map { it.trim() }
        val ints = parts.map { it.toIntOrNull() ?: return null }
        return ints.toIntArray()
    }
}
