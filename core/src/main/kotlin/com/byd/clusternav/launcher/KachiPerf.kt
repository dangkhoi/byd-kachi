package com.byd.clusternav.launcher

import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * ═══ BỘ ĐẾM HIỆU NĂNG — công cụ ĐO, không phải công cụ tối ưu ═════════════════════════════════════════════
 *
 * Owner 2026-09-16: *"làm 1 vòng profiling và optimise performance đi, làm sâu"*. CLAUDE.md §2 nói **đo trước,
 * sửa sau, đo lại** — mà thứ cần đo (số lệnh HAL/phút, số lệnh shell/phút, byte log/phút) chỉ tồn tại **trên xe
 * đang chạy**: máy ảo không có HAL BYDAuto nên mọi phép đo gián tiếp (CPU, logcat) ở đó chỉ nói được một phần.
 *
 * Vì thế bộ đếm này **ship theo app**: nó rẻ (một `AtomicLong.incrementAndGet` mỗi sự kiện, không cấp phát,
 * không khoá), và nó là đường DUY NHẤT để trả lời *"bản mới có thật sự giảm tải không"* bằng một dòng log lấy
 * được qua `adb`/`ClusterDiag` thay vì bằng suy đoán.
 *
 * ## Vì sao ở `:core` mà không ở `:app`
 * Ba nơi đếm nằm ở ba tầng khác nhau (đường đọc HAL ở `:app`, đường shell ở `:app`, lịch poll ở `:core`), nên
 * cái đếm phải nằm ở tầng mà cả ba đều thấy — và `:core` là tầng duy nhất **test được off-device**. Việc IN ra
 * (`android.util.Log`) vẫn ở `:app`: `:core` không biết Android.
 *
 * ## Ngữ nghĩa từng bộ đếm
 *  • [HAL_READ] — một lượt đọc **đi vào** `BydHalGateway`, đếm ở cửa (trước khi resolve device).
 *    ⚠ [SOÁT P3-1] Cố ý đếm ở CỬA chứ không sau khi chạm được HAL: bộ đếm này sinh ra để đo *"cổng H1/nhịp poll
 *    có cắt được số lượt hỏi không"*, mà câu đó phải trả lời được **cả off-car** (máy ảo không có HAL BYDAuto —
 *    đếm sau khi resolve thì mọi số ở đó là 0 và bộ đếm vô dụng đúng nơi nó được dùng nhiều nhất). Trên xe, gần
 *    như mọi lượt vào cửa là một binder IPC nên hai cách đếm trùng nhau; off-car thì đọc nó là *"số lượt hỏi"*.
 *  • [HAL_SKIP_OFFSCREEN] — một datum **không hiện trên màn** nên không đọc (cổng H1, xem [CarDataDemand]).
 *  • [HAL_SKIP_ABSENT] — một datum đã **chứng minh là không có trên xe này** nên tạm ngưng đọc (xem
 *    `BydHalGateway`); khác [HAL_SKIP_OFFSCREEN] vì đây là *"xe không có"*, kia là *"màn không hiện"*.
 *  • [SHELL_CMD] — một lệnh shell qua dadb (mỗi lệnh là một lượt chặn trên hàng đợi dùng chung).
 *  • [LOG_BYTES] — số byte app tự ghi ra thẻ (nguồn I/O + hao thẻ).
 *
 * ⚠ **Không** đặt thêm bộ đếm cho thứ có thể suy ra từ những cái trên; mỗi bộ đếm là một dòng log dài thêm, mà
 * chính log là một trong những thứ đang phải giảm.
 */
object KachiPerf {

    enum class Counter { HAL_READ, HAL_SKIP_OFFSCREEN, HAL_SKIP_ABSENT, SHELL_CMD, LOG_BYTES }

    private val values: Map<Counter, AtomicLong> =
        Counter.values().associateWith { AtomicLong(0) }

    /**
     * Mốc lần báo cáo gần nhất (ms).
     *
     * ⚠ Sentinel "chưa báo lần nào" là [Long.MIN_VALUE], **không phải `0`**: `0` là một mốc giờ HỢP LỆ (đồng hồ
     * giả của test bắt đầu từ đó, và `SystemClock` cũng có thể), nên dùng nó làm sentinel là một bộ đếm im lặng
     * không bao giờ báo cáo — đúng họ lỗi mà `NOT_PROVISIONED_RC ≠ Int.MIN_VALUE` đã dạy một lần.
     */
    private val lastReportAt = AtomicLong(NEVER)

    fun add(c: Counter, n: Long = 1) { values.getValue(c).addAndGet(n) }

    /** Giá trị hiện tại (KHÔNG xả) — cho test và cho cầu kiểm thử. */
    fun value(c: Counter): Long = values.getValue(c).get()

    /** Xả sạch (test + khi người dùng bật/tắt một thứ làm số cũ hết nghĩa). */
    fun reset() {
        values.values.forEach { it.set(0) }
        lastReportAt.set(NEVER)
    }

    /**
     * Dòng báo cáo nếu đã đủ [periodMs] kể từ lần trước (và xả bộ đếm), ngược lại `null`.
     *
     * Trả **số trên phút** đã chuẩn hoá theo cửa sổ thật (chỗ gọi là nhịp 10 s của màn chính — nhịp đó có thể
     * trôi khi máy bận, nên chia cho cửa sổ ĐO ĐƯỢC chứ không nhân với một hằng số giả định).
     *
     * THUẦN: nhận [nowMs] làm tham số để test không phụ thuộc đồng hồ tường.
     */
    fun dueLine(nowMs: Long, periodMs: Long = REPORT_PERIOD_MS): String? {
        val last = lastReportAt.get()
        if (last == NEVER) { lastReportAt.set(nowMs); return null }
        val elapsed = nowMs - last
        // Đồng hồ lùi (chỗ gọi truyền nhầm giờ tường, máy chỉnh giờ) ⇒ đặt lại mốc, KHÔNG chia cho số âm.
        if (elapsed < 0) { lastReportAt.set(nowMs); return null }
        if (elapsed < periodMs) return null
        if (!lastReportAt.compareAndSet(last, nowMs)) return null   // hai nhịp chồng nhau ⇒ chỉ một cái in
        val perMin = { c: Counter -> values.getValue(c).getAndSet(0) * 60_000.0 / elapsed }
        val read = perMin(Counter.HAL_READ)
        val offscreen = perMin(Counter.HAL_SKIP_OFFSCREEN)
        val absent = perMin(Counter.HAL_SKIP_ABSENT)
        val shell = perMin(Counter.SHELL_CMD)
        val logKb = perMin(Counter.LOG_BYTES) / 1024.0
        // ⚠ `Locale.ROOT`: `String.format` không có locale dùng locale MẶC ĐỊNH của máy, mà xe của owner chạy
        // `vi-VN` ⇒ `%.1f` in ra `1,5` thay vì `1.5`. Dòng này là **số đo** được chép vào `docs/diagnostics/perf-*`
        // và so giữa hai lần chạy; đổi dấu thập phân theo ngôn ngữ máy là làm hai lần đo không so được với nhau.
        // Cùng luật với [TelemetryReadout] (`Locale.US`) và [Units.format] (`Locale.ROOT`).
        return String.format(
            Locale.ROOT,
            "cửa sổ %ds · HAL đọc=%.0f/phút · bỏ-không-hiện=%.0f · bỏ-xe-không-có=%.0f · shell=%.1f/phút · log=%.1f KB/phút",
            elapsed / 1000, read, offscreen, absent, shell, logKb,
        )
    }

    /** Nhịp báo cáo mặc định — một phút, đúng đơn vị mà mọi con số trong `docs/diagnostics/perf-*` dùng. */
    const val REPORT_PERIOD_MS = 60_000L

    /** "Chưa báo cáo lần nào" — xem ⚠ ở [lastReportAt]. */
    private const val NEVER = Long.MIN_VALUE
}
