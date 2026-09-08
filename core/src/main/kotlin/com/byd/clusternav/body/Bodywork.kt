package com.byd.clusternav.body

/**
 * CỬA SỔ / CỐP (thân xe) — pure model (không Android, unit-test off-car được).
 *
 * ── NGUỒN (RE `docs/diagnostics/bodywork-window-trunk-RE-2026-09-06.md`) ─────────────────────────
 * Device `android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice` (= [com.byd.clusternav.modules.hal.BydHal]
 * `BODYWORK`). Windows/trunk ghi qua **method-TÊN** (KHÔNG raw feature-id):
 *  • Cửa sổ 1 cửa: `setBodyWindowCtrlState(window, state)` — window 1..4, state 0/1.
 *  • Cửa sổ 4 cửa: `setAllWindowState(lf, rf, lr, rr)` — mỗi arg 0/1.
 *  • Cốp: `setHetchDoorStatus(status)` (typo "Hetch" ĐÚNG theo API OEM) — CLOSE=2 (proven), OPEN≈1 ([SUY]).
 *  • Đọc: `getWindowState(window)` 0/1 · `getWindowOpenPercent(window)` 0..100 · `getWindowPermitState()`.
 *
 * Model này CHỈ giữ hằng + ánh xạ thuần (không chạm HAL) để [com.byd.clusternav.body.BodyworkControl] (app)
 * dùng chung và test off-car khoá được. "Mở 50%" CHƯA rõ cơ chế % (xem RE doc) — chưa mô hình hoá ở đây.
 */
object Bodywork {

    // ── Chỉ số cửa sổ (BODYWORK_CMD_WINDOW_*) ────────────────────────────────────────────────────
    const val WINDOW_LF = 1   // trái-trước (lái)
    const val WINDOW_RF = 2   // phải-trước (phụ)
    const val WINDOW_LR = 3   // trái-sau
    const val WINDOW_RR = 4   // phải-sau

    // ── Trạng thái cửa sổ (BODYWORK_STATE_*) — nhị phân ─────────────────────────────────────────
    const val WINDOW_CLOSE = 0
    const val WINDOW_OPEN = 1

    // ── Cốp (setHetchDoorStatus) — CLOSE_HETCH_DOOR=2 (proven); OPEN≈1 ([SUY], cần xác nhận trên xe) ──
    const val HATCH_OPEN = 1
    const val HATCH_CLOSE = 2

    // ── Tên method trên BYDAutoBodyworkDevice (BodyworkControl gọi qua reflection) ────────────────
    const val METHOD_SET_WINDOW = "setBodyWindowCtrlState"        // (window, state)
    const val METHOD_SET_ALL_WINDOWS = "setAllWindowState"        // (lf, rf, lr, rr)
    const val METHOD_SET_HATCH = "setHetchDoorStatus"             // (status)
    const val METHOD_GET_WINDOW_STATE = "getWindowState"          // (window) -> 0/1
    const val METHOD_GET_WINDOW_PERCENT = "getWindowOpenPercent"  // (window) -> 0..100
    const val METHOD_GET_WINDOW_PERMIT = "getWindowPermitState"   // () -> được phép điều khiển không

    /** 4 cửa theo thứ tự LF, RF, LR, RR (khớp thứ tự arg của [METHOD_SET_ALL_WINDOWS]). */
    val ALL_WINDOWS: List<Int> = listOf(WINDOW_LF, WINDOW_RF, WINDOW_LR, WINDOW_RR)

    /** window có hợp lệ (1..4) không. Degrade-safe cho input UI/probe. */
    fun isWindow(window: Int): Boolean = window in WINDOW_LF..WINDOW_RR

    /** open(true) → [WINDOW_OPEN] (1) / open(false) → [WINDOW_CLOSE] (0). */
    fun windowState(open: Boolean): Int = if (open) WINDOW_OPEN else WINDOW_CLOSE

    /** open(true) → [HATCH_OPEN] (1) / open(false) → [HATCH_CLOSE] (2). */
    fun hatchStatus(open: Boolean): Int = if (open) HATCH_OPEN else HATCH_CLOSE

    /**
     * Khoá nhãn song ngữ cho từng cửa (MainActivity dịch qua `Lang.t` KHI có UI ở phiên sau). null nếu
     * [window] không hợp lệ (degrade-safe).
     */
    fun windowLabelKey(window: Int): String? = when (window) {
        WINDOW_LF -> "window_lf"
        WINDOW_RF -> "window_rf"
        WINDOW_LR -> "window_lr"
        WINDOW_RR -> "window_rr"
        else -> null
    }
}
