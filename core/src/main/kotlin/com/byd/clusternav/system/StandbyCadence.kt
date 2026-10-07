package com.byd.clusternav.system

/**
 * ═══ 2.96 · R18 — NHỊP CỦA VIỆC NỀN KHI MÀN TẮT (standby) — luật thuần, test off-car ═══════════════════════════════
 *
 * Spec `docs/specs/kachi-296-plan.html` §3 R18 · số đo `docs/diagnostics/perf-inventory-2026-10-07.md`.
 *
 * ## Vì sao [ĐO log xe 07/10 `usage-1791336721400.log`]
 * Tiến trình bật lúc màn TẮT (`KachiReady proc interactive=false`) sống 8 h 20 ph ở standby; trạng thái chiếu còn là
 * "đang chiếu" ⇒ nhịp 2 s của nút nổi gọi lượt dò repin ⇒ `am stack list` mỗi 4 s MỖI KHI SoC thức (548 lệnh, chùm
 * 09:13–09:16 đúng 4 s/lệnh). Màn tắt = không ai lái, cụm tối: không có gì để kéo về cụm, mỗi lệnh là một lượt dadb +
 * một lần giữ khoá WM của system_server trên SoC yếu, đúng lúc tiến trình khác của xe đang dựng lại.
 *
 * ## Luật
 * Chỉ `interactive == false` (đọc ĐƯỢC là tắt) mới thưa lại; `null` (không hỏi được) ⇒ coi như bật = hành vi cũ
 * (fail-safe: không bao giờ vì lỗi đọc mà bỏ lưới giữ cụm). Màn bật ⇒ đúng nhịp cũ, không đổi một mili-giây.
 *
 * ## Vì sao THƯA chứ không TẮT hẳn
 * [CHƯA BIẾT] nút "tắt màn hình" của BYD khi đang lái có làm `isInteractive` = false không (cùng câu hỏi mở ở
 * `AccessibilityHealGates` KDoc `GIỮA LÚC LÁI`). Nếu có, cụm vẫn đang chiếu cho người lái ⇒ lưới repin vẫn phải chạy,
 * chỉ chậm hơn ([REPIN_OFF_MS]) — đổi lấy 7,5 lần ít lệnh shell ở standby mà không mất đường tự chữa.
 */
object StandbyCadence {

    /** Nhịp nút nổi khi màn bật — đúng `FloatingBubbleService.REFRESH_INTERVAL_MS` cũ. */
    const val BUBBLE_REFRESH_ON_MS = 2_000L

    /** Nhịp nút nổi khi màn tắt (đọc lại công tắc, vị trí/bóng VietMap, gọi lượt dò repin). */
    const val BUBBLE_REFRESH_OFF_MS = 10_000L

    /** Khoảng tối thiểu giữa hai lượt dò repin khi màn bật — đúng `REPIN_PROBE_MIN_INTERVAL_MS` cũ. */
    const val REPIN_ON_MS = 4_000L

    /** Khoảng tối thiểu giữa hai lượt dò repin khi màn tắt. */
    const val REPIN_OFF_MS = 30_000L

    /** Đọc ĐƯỢC là màn tắt. `null` (không hỏi được) KHÔNG phải tắt. */
    fun screenOff(interactive: Boolean?): Boolean = interactive == false

    fun bubbleRefreshMs(interactive: Boolean?): Long =
        if (screenOff(interactive)) BUBBLE_REFRESH_OFF_MS else BUBBLE_REFRESH_ON_MS

    fun repinMinIntervalMs(interactive: Boolean?): Long =
        if (screenOff(interactive)) REPIN_OFF_MS else REPIN_ON_MS
}
