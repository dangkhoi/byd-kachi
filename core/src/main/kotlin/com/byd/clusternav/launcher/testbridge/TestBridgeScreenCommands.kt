package com.byd.clusternav.launcher.testbridge

/**
 * ═══ 2.93 wave 2B · DIAG-SCREENS-UNREACHABLE — lệnh `diag_screen`: lối DUY NHẤT tới hai màn chẩn đoán ═══════════════════
 *
 * Spec `docs/specs/kachi-293-misc.html` §7 OQ3 + `kachi-293-cam.html` (Pass 2 — wave 2B). [ĐO mã] `DiagActivity` ·
 * `VietMapWidgetDiagActivity` (`exported=false`) không có lối vào nào trên bản phát hành: nút Cài đặt đã gỡ (owner 21/09 —
 * `DevSurfaceGateContractTest` cấm dựng lại), `ClusterNavBridge.openDiagnostics()`/`openVietMapData()` 0 chỗ gọi (gỡ hẳn ở
 * wave 2C · DIAG-BRIDGE-DEAD-OPENERS), và
 * `am start` từ uid shell bị từ chối (AOSP 10 r47 `ActivityStackSupervisor.checkStartAnyActivityPermission`; [ĐO xe 29/09]).
 * Quyết định (điều phối thay owner, mặc định đã chọn): GIỮ hai màn, mở được CHỈ qua cầu kiểm thử — tức sau công tắc
 * *Chế độ kiểm thử qua adb* do người trong xe bật (cổng `TestBridgeStore.isOn` của receiver, trước mọi phép phân tích).
 * Đây KHÔNG phải một bề mặt UI: không nút, không hàng Cài đặt — luật owner 21/09 giữ nguyên.
 *
 * Tách tệp như [TestBridgeTeachCommands] (trần 500 dòng của `TestBridgeCommand.kt`): bảng lệnh + phép kiểm đối số ở đây,
 * phép ánh xạ tên → lớp Activity ở `:app` (`TestBridgeScreens` — `:core` không biết lớp Android nào).
 */
object TestBridgeScreenCommands {

    /** `diag_screen --es name diag|vietmap` — mở màn chẩn đoán đã chọn (tác vụ mới). */
    const val DIAG_SCREEN = "diag_screen"

    /** Màn *Chẩn đoán ClusterNav* (`DiagActivity`). */
    const val DIAG = "diag"

    /** Màn *Dữ liệu VietMap* (`VietMapWidgetDiagActivity`). */
    const val VIETMAP = "vietmap"

    /** Hai đích — danh sách TRẮNG (tên lạ bị chặn ở tầng phân tích, không bao giờ thành lệnh chạy được). */
    val TARGETS: List<String> = listOf(DIAG, VIETMAP)

    /** Tên màn không có trong [TARGETS] — nối nguyên văn để người gõ thấy mình gõ gì. */
    const val ERR_BAD_SCREEN = "bad_screen:"

    val SPECS: List<TestBridgeCommands.Spec> = listOf(
        TestBridgeCommands.Spec(DIAG_SCREEN, listOf(TestBridgeCommands.EXTRA_ARG)),
    )

    val NAMES: Set<String> = SPECS.mapTo(LinkedHashSet()) { it.name }

    /** Đích của đối số [arg] (không phân biệt hoa/thường); không thuộc [TARGETS] ⇒ `null`. */
    fun targetOf(arg: String): String? = arg.trim().lowercase().takeIf { it in TARGETS }

    /** Phép kiểm ở TẦNG PHÂN TÍCH (`TestBridgeCommands.parse`): mã lỗi, hoặc `null` = hợp lệ / không phải lệnh này. */
    fun check(name: String, extras: Map<String, Any?>): String? {
        if (name !in NAMES) return null
        val arg = (extras[TestBridgeCommands.EXTRA_ARG] as? String).orEmpty()
        return if (targetOf(arg) == null) ERR_BAD_SCREEN + arg.trim() else null
    }
}
