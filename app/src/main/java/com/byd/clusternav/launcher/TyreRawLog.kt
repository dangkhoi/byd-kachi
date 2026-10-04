package com.byd.clusternav.launcher

import android.util.Log

/**
 * ═══ 2.88 · KÊNH 2 — dòng log `TYRE raw …` cho tester đời xe khác, KHÔNG cần adb (spec `kachi-288-tyre-car-state` §4.4) ═══
 *
 * Mỗi ảnh chụp trạng thái xe ⇒ [TyreBoard.rawLine] (thuần, `:core`) dựng một dòng mã thô + màu đã phán + nguồn; dòng
 * chỉ được GHI khi nó ĐỔI. Logcat của chính tiến trình tự vào `kachi-logs/usage-*.log` (`KachiLog.startCapture`) nên
 * tester không phải làm gì; owner kéo log về sau.
 *
 * ## Vì sao [LogLineThrottle] với `maxKeys = 1`
 * Bộ tiết chế dùng chung nhớ **một** dòng cuối ⇒ dòng y hệt trong [HEARTBEAT_MS] bị bỏ, dòng KHÁC đi qua ngay, và quay
 * về một dòng cũ (A → B → A) cũng đi qua (khoá A đã bị đẩy ra) — tức đúng *"chỉ ghi khi đổi"*, cộng một dòng nhắc lại
 * mỗi 10 phút để người đọc log biết nó vẫn sống. Không dựng bộ nhớ "dòng cuối" thứ hai (CLAUDE.md §4.1 DRY).
 *
 * Gọi từ luồng thu trạng thái xe của màn chính ([collectHome]); `@Synchronized` ở [LogLineThrottle] đủ cho chỗ gọi
 * duy nhất này.
 */
internal object TyreRawLog {
    private const val TAG = "KachiTyre"

    /** Nhịp nhắc lại khi không có gì đổi — 10 phút. */
    private const val HEARTBEAT_MS = 10L * 60 * 1000

    private val throttle = LogLineThrottle(windowMs = HEARTBEAT_MS, maxKeys = 1)

    fun note(tyres: CarStatus.Tyres, nowMs: Long = System.currentTimeMillis()) {
        val line = TyreBoard.rawLine(tyres) ?: return
        if (throttle.suppressedBefore(line, nowMs) != null) Log.i(TAG, line)
    }
}
