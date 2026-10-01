package com.byd.clusternav.modules.navaccess

/**
 * ═══ READY-AT-HOME §4.8 — KIỂM PHÍM VÔ-LĂNG TRONG CHUỖI CHUẨN BỊ (luật thuần) ═════════════════════════════════════
 *
 * Owner 2026-10-01: *"khi lên HOME thì mọi thứ phải ready, bao gồm kiểm tra binding phím"*. KHÔNG có đường chữa thứ hai
 * (DRY): mọi việc chữa vẫn là của 2.83 — lớp 2 (`A11yLifecycleHeal`, mở xe) và `NavConnect.grantAccessibility` (cùng hàm
 * B1 gọi, có single-flight). Luật ở đây chỉ quyết THỨ TỰ và CHỜ:
 *
 *  1. Phím-thoại tắt ⇒ [KeyStep.OFF].
 *  2. Binder `AccessibilityManager` nói đã gắn ⇒ [KeyStep.BOUND] — 0 lệnh shell (cùng nguồn `mBoundServices`, KDoc
 *     [AccessibilityHealGates]).
 *  3. Lớp 2 đang / sắp xét lần mở xe này — hoặc lượt ÂN HẠN KHỞI ĐỘNG (lớp 2 mở rộng, 02/10: tiến trình dựng lại lúc màn
 *     đang bật, [AccessibilityHealGates.HealPhase.KHOI_DONG]) chưa kết luận ⇒ [KeyStep.WAIT_LAYER2] (một lần, có trần):
 *     cả hai có thể force-stop launcher — cấp quyền chen giữa lúc nó đang đo là đọc-sửa-ghi chồng lên phép đo.
 *  4. Vẫn chưa gắn: tiến trình sinh ra từ CHÍNH lượt chữa của mình ⇒ [KeyStep.SKIP_OWN_HEAL] (lượt chữa vừa làm việc
 *     của nó; watchdog/B1 là lưới), còn lại ⇒ [KeyStep.GRANT].
 *
 * `bound = null` = binder không trả lời được ⇒ coi như CHƯA gắn (đi đường cấp — đúng như `grantOrSkip` ⇒ shell).
 */
object KeyReadyPlan {

    /** Trần chờ kết luận lớp 2: 5 s khoảng xác nhận kẹt ([AccessibilityHealGates.STUCK_CONFIRM_GAP_MS]) + hai lần dump. */
    const val KEY_HOLD_MAX_MS = 8_000L

    /**
     * Sau một lượt cấp trả `NOT_BOUND`, chuỗi kiểm phím còn hỏi binder bao lâu để biết phím có gắn MUỘN không (thua
     * single-flight / hệ gắn chậm — KDoc `KeyReady.confirmLateBind`). Phủ trọn một lượt toggle của lượt THẮNG: settle 1,2 s
     * + toggle 0,8 s + tối đa 6 lượt đọc cách 1 s (`NavConnect.REBIND_*`) ≈ 8 s, cộng lề.
     */
    const val LATE_BIND_WATCH_MS = 10_000L

    /** Nhịp hỏi binder trong [LATE_BIND_WATCH_MS] — 0 lệnh shell mỗi nhịp. */
    const val LATE_BIND_POLL_MS = 1_000L

    enum class KeyStep { OFF, BOUND, WAIT_LAYER2, SKIP_OWN_HEAL, GRANT }

    fun step(
        voiceKeyEnabled: Boolean,
        bound: Boolean?,
        layer2Pending: Boolean,
        waitedLayer2: Boolean,
        bornFromOwnHeal: Boolean,
    ): KeyStep = when {
        !voiceKeyEnabled -> KeyStep.OFF
        bound == true -> KeyStep.BOUND
        layer2Pending && !waitedLayer2 -> KeyStep.WAIT_LAYER2
        bornFromOwnHeal -> KeyStep.SKIP_OWN_HEAL
        else -> KeyStep.GRANT
    }

    /**
     * Ô có phải CHỜ trước khi gắn app không (R-A2 của 2.83 "trước khi dựng app vào ô"): chỉ khi phím-thoại bật, binder
     * nói chưa gắn VÀ lớp 2 còn chưa kết luận. Ca thường (đã gắn) không chờ một mili-giây nào.
     */
    fun holdTile(voiceKeyEnabled: Boolean, bound: Boolean?, layer2Pending: Boolean): Boolean =
        voiceKeyEnabled && bound != true && layer2Pending

    /**
     * Lớp 2 có SẮP xét lần màn bật này không, khi bộ thu của nó CHƯA nhận `ACTION_SCREEN_ON` (thứ tự hai bộ thu / lượt
     * activity `onStart` có thể đi TRƯỚC broadcast — [ĐO xe c2 29/09] `on_restart` 11:34:13.153, `power_screen_state`
     * 11:34:14.236). Đúng khi: tiến trình này bật lúc màn TẮT (lượt BYD dựng lại khi tắt máy), chưa thấy lần màn bật nào,
     * và [AccessibilityHealGates.moXeFollowsTatMay] nói lần màn bật kế là MỞ XE — tức lớp 2 sẽ được mở.
     */
    fun layer2Expected(
        startedNonInteractive: Boolean,
        screenOnSeen: Boolean,
        voiceKeyEnabled: Boolean,
        lastTatMayAt: Long,
        lastMoXeAt: Long,
        nowElapsed: Long,
    ): Boolean = startedNonInteractive && !screenOnSeen && voiceKeyEnabled &&
        AccessibilityHealGates.moXeFollowsTatMay(lastTatMayAt, lastMoXeAt, nowElapsed)
}
