package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.util.Log
import com.byd.clusternav.launcher.BydFeatureIds
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

    /**
     * `--es op setev` — ghi qua đường **generic** `AbsBYDAutoDevice.set(int[] ids, BYDAutoEventValue)`.
     *
     * ## Vì sao cần một op thứ ba, khi đã có `set`
     * [ĐO nguồn fw-dl3 2026-09-16] `BYDAutoDoorLockDevice` **không có setter nào**: chỉ `getDoorLockStatus(area)`
     * + các hằng `DOOR_LOCK_COMMAND_AREA_*` (960495668 · 70 · 72 · 74 · **76 = back**) và `DOOR_LOCK_STATE_UNLOCK=1` /
     * `LOCK=2`, quyền `BYDAUTO_DOOR_LOCK_SET`. `BYDAutoBodyworkDevice` cũng **không có** `setHetchDoorStatus`
     * (bản jadx-tmap cũ có; xe này không) — khớp với `NoSuchMethod` mà vòng action 09-16 ghi cho `door`/`lock`/
     * `trunk`. ⇒ Đường ghi DUY NHẤT còn lại cho khoá/cốp là `set()` generic, và nó **chưa ai thử**.
     *
     * ## Đây là một ĐẦU DÒ, không phải một đường mặc định
     * `ControlRegistry` **không đổi** một dòng nào: `lock`/`door`/`trunk` vẫn trỏ named-method như cũ. Đổi route
     * mặc định dựa trên một giả thuyết chưa đo là đúng thứ CLAUDE.md §2 cấm. Op này tồn tại để lượt xe sau **đo**,
     * rồi mới sửa route nếu đo xanh.
     *
     * Cú pháp: `--es op setev --es dev <Device> --es m <id thập phân | TÊN_HẰNG> --es args <giá trị>`;
     * lượt GHI ⇒ vẫn qua cổng `--ez auto_confirm true` như mọi lượt `set`.
     */
    const val OP_SETEV = "setev"

    /**
     * `--es op getid` — ĐỌC một feature-id qua đường **generic** `AbsBYDAutoDevice.get(int id)`.
     *
     * ## Vì sao op này phải tồn tại ([ĐO xe 2026-09-16], vệt mưa/sấy)
     * Trước bản này `hal` **không có đường đọc theo feature-id**: `--es op get` chỉ nhận một **tên method**, nên
     * `--es id` rơi thẳng vào `missing_extra:m`. Vệt mưa/sấy tắc đúng ở đó — `getWindscreenWiperRelayState()` trả
     * **0 (INVALID)** suốt lúc owner đang gạt mưa thật, và bước tiếp theo là đọc thẳng `WIPER_FRONT_WIPER_LEVEL`
     * (321912848) · `WIPER_AREA_FRONT_STATE` (540287) trên device 1046 — thứ chỉ đường generic đọc được.
     *
     * ## Nó là bản SOI GƯƠNG chỉ-đọc của [OP_SETEV], có chủ ý
     * Cùng bộ giải tên (`toIntOrNull()` rồi [BydFeatureIds.idByName] — **một** bộ, không fork bộ thứ hai), cùng
     * cách nói device, cùng khuôn lời đáp. Khác đúng hai điểm, và cả hai là hệ quả của *"chỉ đọc"*:
     *  • **KHÔNG** qua cổng `auto_confirm` — nó không đổi một bit nào của xe (cùng luật lượt `get` ở dưới);
     *  • **KHÔNG** cần `--es args`.
     *
     * Cú pháp: `--es op getid --es dev <Device> --es m <id thập phân | TÊN_HẰNG>`.
     *
     * ⚠ Lời đáp nói rõ giá trị đọc được có phải **sentinel** hay không ([HalBindingTable.isSentinelRc]): trên xe
     * này một feature không có trên trim vẫn trả về *một con số*, và đọc `-2147482648` như một mức gạt mưa là
     * đúng kiểu kết luận sai mà CLAUDE.md §2 gọi là trộn cơ chế với quy kết.
     */
    const val OP_GETID = "getid"

    /** `--es m` của `setev`/`getid` không phải số và cũng không phải tên hằng có trên xe. */
    const val ERR_BAD_FEATURE = "bad_feature:"

    fun run(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        val simpleDev = cmd.dev.ifBlank { DEFAULT_DEVICE }
        val fqn = HalBindingTable.deviceFqn(simpleDev)
        if (cmd.op == OP_SETEV) { runSetEv(app, cmd, simpleDev, fqn, reply); return }
        if (cmd.op == OP_GETID) { runGetId(app, cmd, simpleDev, fqn, reply); return }
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

    /**
     * Lượt `setev` — xem KDoc [OP_SETEV]. Cùng cổng CONFIRM và cùng giao thức đọc câu chữ HAL với lượt `set`.
     *
     * `--es m` nhận **cả hai** dạng: số thập phân (`960495676`) và tên hằng (`Door.DOOR_LOCK_COMMAND_AREA_BACK`).
     * Tên hằng là dạng nên dùng — số thì đổi theo cấu hình xe (xem `BindingRoute.FeatureName`).
     */
    private fun runSetEv(
        app: Context,
        cmd: TestBridgeCommand,
        simpleDev: String,
        fqn: String,
        reply: TestBridgeReply,
    ) {
        val featureId = cmd.method.toIntOrNull() ?: BydFeatureIds.idByName(cmd.method)
        if (featureId == null) {
            reply.fail(ERR_BAD_FEATURE + cmd.method)
            return
        }
        val value = parseArgs(cmd.halArgs)?.firstOrNull()
        if (value == null) {
            reply.fail(ERR_BAD_ARGS + cmd.halArgs)
            return
        }
        if (!cmd.autoConfirm) {
            reply.fail(ERR_NEEDS_CONFIRM, "dev" to simpleDev, "feature" to cmd.method, "hint" to NOTE_CONFIRM)
            return
        }
        Log.i(TestBridgeReply.TAG, "AUTO-CONFIRM: hal setev $simpleDev feature=$featureId value=$value")
        val mark = HalWriteProbe.mark()
        HalWriteProbe.clear()
        val rc = BydHalGateway(app).featureSet(fqn, featureId, value)
        val outcome = HalWriteProbe.last
        val halLine = if (outcome != null && outcome.seq > mark) outcome.raw else TestBridgeCtl.HAL_UNMAPPED
        reply.ok(
            listOf(
                "op" to OP_SETEV,
                "dev" to simpleDev,
                "fqn" to fqn,
                "feature" to cmd.method,
                "feature_id" to featureId,
                "device_by_map" to (BydFeatureIds.deviceFqnForFeature(featureId) ?: ""),
                "value" to value,
                "rc" to (rc?.toString() ?: ""),
                "hal_line" to halLine,
                "reply" to when {
                    halLine.contains("permission", ignoreCase = true) -> "denied: no permission / wrong device"
                    halLine == "off_car" -> "unavailable: off-car/emulator"
                    rc != null -> "ok: HAL accepted (rc valid) — owner phai NHIN xe co phan ung khong"
                    else -> "rejected: $halLine"
                },
            ),
        )
    }

    /**
     * Lượt `getid` — xem KDoc [OP_GETID]. **Chỉ đọc** ⇒ không cổng CONFIRM, không `HalWriteProbe` (không có lượt
     * ghi nào để chụp câu chữ), không `--es args`.
     */
    private fun runGetId(
        app: Context,
        cmd: TestBridgeCommand,
        simpleDev: String,
        fqn: String,
        reply: TestBridgeReply,
    ) {
        // MỘT bộ giải tên, dùng chung với `setev` — xem KDoc [OP_GETID]. Không fork bộ thứ hai.
        val featureId = cmd.method.toIntOrNull() ?: BydFeatureIds.idByName(cmd.method)
        if (featureId == null) {
            reply.fail(ERR_BAD_FEATURE + cmd.method)
            return
        }
        val raw = BydHalGateway(app).featureGet(fqn, featureId)
        val sentinel = HalBindingTable.isSentinelRc(raw?.toLongOrNull())
        reply.ok(
            listOf(
                "op" to OP_GETID,
                "dev" to simpleDev,
                "fqn" to fqn,
                "feature" to cmd.method,
                "feature_id" to featureId,
                // Bản đồ feature→device của CHÍNH chiếc xe này: đọc nhầm device là ca hỏng hay gặp nhất, và
                // dòng này cho người đo thấy ngay mình có đang hỏi đúng device không (xem lệnh `featmap`).
                "device_by_map" to (BydFeatureIds.deviceFqnForFeature(featureId) ?: ""),
                "value" to (raw ?: ""),
                "sentinel" to sentinel,
                "reply" to when {
                    raw == null -> "unavailable: null (off-car / feature absent / no permission)"
                    sentinel -> "unavailable: sentinel ($raw) — feature khong co tren trim nay"
                    else -> "ok: read"
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
