package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ 2.93 · READY-RESTART-MID-CAST — "khởi động lại tiến trình NGAY BÂY GIỜ có làm hỏng chiếu cụm không" (chỉ đọc) ═══════════
 *
 * [ĐO log xe 06/10, xe 2.91] 15:17:27.751 `theme 31 cụm=[2] → SEND` → 15:17:29.855 `16` → 15:17:31.392 `KachiReady:
 * keys=STUCK(tat-may)->RESTARTING` (lượt chữa phím tự force-stop Kachi) ⇒ chuỗi mở chiếu chết giữa 16 và 35 ⇒ tiến trình mới
 * 15:17:34.286 `theme 31 … Skip(reason=TOO_SOON, … còn 8468 ms) ⇒ SKIP_KNOWN` ⇒ phiên "chưa rõ kiểu". Lượt chữa phím
 * (`A11yLifecycleHeal`, `:app`) hỏi ở đây trước khi bắn, và CHỜ (có trần — luật ở `HealCastDeferral`, `:core` navaccess)
 * khi câu trả lời khác `null`.
 *
 * Hai mối nguy, đều đọc từ sự thật TRONG tiến trình (không shell, không ghi — gọi được từ mọi luồng):
 *  • [OP_IN_FLIGHT] — executor chiếu đang chạy hoặc có thao tác chờ ([BoundedCastExecutor.isIdle] sai), hoặc trạng thái còn
 *    `Opening`. Giết lúc này = chuỗi lệnh cụm dở dang (opcode đã gửi một nửa, ClusterBlack/`wm` chưa đặt, app chưa đáp).
 *  • [THEME_GAP] — một opcode theme đã gửi chưa đủ [ThemeLedger.MIN_GAP_MS] ([ClusterThemeGuard.remainingGapMs]: mốc muộn hơn
 *    giữa sổ bền và RAM). Giết lúc này ⇒ lượt mở của tiến trình mới (tự chiếu khi khởi động) gặp `TOO_SOON` ⇒ bỏ theme ⇒ kiểu
 *    tin UNKNOWN — đúng chuỗi log trên. Sổ là sự thật phạm vi XE (ghi `pending` TRƯỚC lệnh) nên kể cả mục do tiến trình
 *    TRƯỚC ghi cũng tính: tiến trình kế vẫn phải chờ đúng khoảng ấy.
 *
 * Không quyết gì ở đây — chỉ báo. Luật chờ (trần, pha, cửa sổ) nằm ở bên gọi.
 */
enum class CastRestartHazard { OP_IN_FLIGHT, THEME_GAP }

/** `null` = khởi động lại lúc này không chạm chuỗi chiếu cụm nào. KDoc [CastRestartHazard]. */
fun SimpleCastCoordinator.restartHazard(): CastRestartHazard? = when {
    !executor.isIdle || state == SimpleCastState.Opening -> CastRestartHazard.OP_IN_FLIGHT
    (themeGuard.remainingGapMs() ?: 0L) > 0L -> CastRestartHazard.THEME_GAP
    else -> null
}
