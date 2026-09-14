package com.byd.clusternav.launcher.testbridge

import android.util.Log
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.CtlSafetyPolicy
import com.byd.clusternav.launcher.HalBindingTable
import com.byd.clusternav.launcher.HalWriteProbe

/**
 * ═══ T-BRIDGE · LỆNH `ctl` — BẮN MỘT CONTROL + GHI NHẬN KẾT QUẢ HAL ══════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-test-bridge.html` §9 (grab-list HAL). Tách khỏi [KachiTestBridge] vì trần 500 dòng
 * (CLAUDE.md §4.1) — CÙNG lý do và cùng hình dạng với [TestBridgeState] (`state`): receiver lo **cổng + vòng
 * đời**, tệp này lo **một lệnh**.
 *
 * ## Đi qua ĐÚNG một đường — sạch hơn `say`
 * `say` phải phân tích câu chữ rồi mới ra ý định; `ctl` bắn thẳng vào [TestBridgeHooks.control] = cổng
 * `CarControlAdapter.actByKind` mà một cú chạm ô nút đi. Không dựng adapter thứ hai, không tự chọn cửa
 * (TOGGLE/STEP/COVER/SELECT/BUTTON do [com.byd.clusternav.launcher.ControlKind] của mã quyết, đúng bảng định
 * tuyến duy nhất `CarControlPort.actByKind`).
 *
 * ## Câu chữ HAL đọc TRONG tiến trình, KHÔNG spawn logcat
 * Cổng port chỉ trả `Boolean`, nên `no permission`/rc bị mất. [HalWriteProbe] chụp trộm câu chữ tại tầng gateway.
 * Trình tự: [HalWriteProbe.clear] → bắn → đọc [HalWriteProbe.last]. `accepted` suy từ port (rc hợp lệ, khác
 * sentinel); `hal_line` là câu chữ thật; `reply` là câu người đọc. [ĐO] 2026-09-14: named-method chạy, feature-id
 * `1de0000c` chạy, `0x4f50003a` device 1004 *"no permission"* — đây là dữ liệu cần cho grab-list §9.
 *
 * ## Cổng CONFIRM (CLAUDE.md §4·§5): mặc định TỪ CHỐI control mở/khoá thân xe
 * Cầu này `exported` ⇒ mọi app trên xe bắn được (khi chế độ kiểm thử bật). Một control mở cửa/kính/nóc/cốp
 * ([CtlSafetyPolicy.CONFIRM_REQUIRED]) vì thế bị **từ chối** (`needs_confirm`) trừ khi lệnh nói rõ
 * `--ez auto_confirm true`, và luôn để lại dấu `AUTO-CONFIRM` — CÙNG cổng mà `KachiTestBridge.runSay` dùng.
 */
internal object TestBridgeCtl {

    /** `--es id` không phải một control trong `ControlRegistry` — lời đáp kèm danh sách mã hợp lệ. */
    const val ERR_UNKNOWN_CONTROL = "unknown_control"

    /** Control mở/khoá thân xe bắn không kèm `--ez auto_confirm true` — xem [CtlSafetyPolicy]. */
    const val ERR_NEEDS_CONFIRM = "needs_confirm"

    /** Chỉ đường cho người đo khi bị chặn CONFIRM — ASCII (một lệnh để gõ, không phải chữ trên màn). */
    const val NOTE_CONFIRM = "them --ez auto_confirm true de ban control mo/khoa than xe"

    /** `hal_line` khi route = none / lượt ghi không tới gateway (control chưa map — grab-list §9). */
    const val HAL_UNMAPPED = "unmapped"

    private const val SENTINEL_NOT_PROVISIONED = "-2147482648"
    private const val SENTINEL_INVALID = "-2147482645"

    fun run(cmd: TestBridgeCommand, hooks: TestBridgeHooks, reply: TestBridgeReply) {
        val def = ControlRegistry.byId(cmd.id)
        if (def == null) {
            reply.fail(
                ERR_UNKNOWN_CONTROL,
                "id" to cmd.id,
                "all" to TestBridgeJson.Raw(TestBridgeJson.arr(ControlRegistry.ALL.map { it.id })),
            )
            return
        }
        if (CtlSafetyPolicy.needsConfirm(def.id)) {
            if (!cmd.autoConfirm) {
                reply.fail(ERR_NEEDS_CONFIRM, "id" to def.id, "label" to def.displayLabel, "hint" to NOTE_CONFIRM)
                return
            }
            // Việc mức CONFIRM không được xảy ra mà không có dấu grep được trong logcat (cùng luật `runSay`).
            Log.i(TestBridgeReply.TAG, "AUTO-CONFIRM: ctl ${def.id} (${def.displayLabel})")
        }
        val primary = def.clamp(cmd.v ?: HalBindingTable.defaultPrimary(def))
        val (route, device) = HalBindingTable.describeWrite(def)
        // Mốc `seq` TRƯỚC khi xoá: chỉ nhận `hal_line` nếu sổ có một lượt ghi MỚI HƠN mốc này — nếu một lượt ghi
        // khác (nút UI) chen vào giữa clear→đọc thì `last` cũ không bị nhận nhầm là kết quả của lượt `ctl` này
        // (đúng hợp đồng KDoc [HalWriteProbe]: `seq` để chỗ đọc biết "đã có lượt ghi kể từ khi tôi xoá").
        val mark = HalWriteProbe.mark()
        HalWriteProbe.clear()
        val accepted = hooks.control(def.id, primary)
        val outcome = HalWriteProbe.last
        val halLine = if (outcome != null && outcome.seq > mark) outcome.raw else HAL_UNMAPPED
        reply.ok(
            listOf(
                "id" to def.id,
                "label" to def.displayLabel,
                "kind" to def.kind.name,
                "v" to primary,
                "route" to route,
                "device" to device,
                "accepted" to accepted,
                "hal_line" to halLine,
                "reply" to summarize(accepted, route, halLine),
            ),
        )
    }

    /**
     * Câu ĐỌC ĐƯỢC suy từ `accepted` + route + câu chữ HAL — **ASCII, KHÔNG dịch**, đúng lệ mã lỗi của cầu
     * (`TestBridgeParse.Err`): đầu đọc là một script `adb` grep bằng MỘT chuỗi, hai lượt đo trên hai máy khác
     * ngôn ngữ phải so được. Mã máy đã có ở `route`/`hal_line`; câu này chỉ gộp lại thành một phân loại.
     */
    private fun summarize(accepted: Boolean, route: String, halLine: String): String = when {
        route == "none" -> "unavailable: unmapped (command-wrapper/uncertain id — grab-list s9)"
        halLine.contains("permission", ignoreCase = true) -> "denied: no permission (feature/device not granted)"
        halLine == "off_car" -> "unavailable: off-car/emulator (device null)"
        halLine == "setting_unsupported" -> "unavailable: car-setting not proven"
        halLine.contains(SENTINEL_NOT_PROVISIONED) || halLine.contains(SENTINEL_INVALID) ->
            "not_provisioned: feature absent on trim (sentinel)"
        accepted -> "ok: HAL accepted (rc valid)"
        else -> "rejected: $halLine"
    }
}
