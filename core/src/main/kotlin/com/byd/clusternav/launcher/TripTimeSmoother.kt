package com.byd.clusternav.launcher

/**
 * LÀM MƯỢT THỜI GIAN CHUYẾN (TRIP-TIME-6MIN · owner duyệt "cách 1" 2026-09-27 ~11:55) — thuần Kotlin, **một instance
 * cho một [CarDataAdapter]** vì nó có trạng thái (mốc HAL + đồng hồ trôi).
 *
 * ## Sự thật đo được [ĐO xe Seal 2026-09-27 11:48–11:52 · cầu kiểm thử `hal get`]
 * `BYDAutoInstrumentDevice.getCurrentJourneyDriveTime` trả **GIỜ** với **một chữ số lẻ**: `1.8000001` lúc 11:48:19,
 * `1.9` từ 11:49:08 rồi đứng yên nhiều phút. Độ phân giải 0,1 h = **6 phút** (API OEM `DRIVE_TIME_MAX = 99.9`,
 * `jadx-tmap/sources/android/hardware/bydauto/instrument/BYDAutoInstrumentDevice.java:305`). `TelemetryReadout.hoursToHm`
 * in `h:mm` ⇒ ô hiện "1:48" rồi 6 phút sau nhảy "1:54" — **phút là độ chính xác giả**, không phải nhịp đọc của Kachi.
 *
 * ## Cơ chế
 * hiển thị = **mốc HAL cuối** + **số phút tròn** đã trôi kể từ lúc mốc ấy được thấy lần đầu, **kẹp ≤ mốc + [MAX_EXTRA_MIN]
 * phút** (= 5/60 h < [HAL_STEP_H]) ⇒ không bao giờ chạm/vượt bậc kế tiếp.
 *  • HAL **tăng** ⇒ nhảy TIẾN tới mốc mới, đặt lại đồng hồ trôi — từ đây **pha là chính xác** (mốc đổi đúng lúc xe đổi).
 *  • HAL **giảm** (chuyến mới / reset) ⇒ quên hết, đi thẳng mốc mới.
 *  • HAL `null` (một nhịp đọc không ra) ⇒ trả `null` ("—") nhưng **GIỮ mốc + số phút đang hiện**; khi đọc lại cùng mốc
 *    thì phút vắng **không tính** (neo dời theo độ dài khoảng vắng) và số không lùi. [Review Pass 2 · P3 #10] bản đầu
 *    reset ở `null` ⇒ một nhịp HAL hụt làm "1:59" tụt về "1:54" — người lái thấy số lùi dù xe không hề đổi.
 *  • Giữa hai lần reset, giá trị trả ra **không bao giờ lùi** — kể cả khi đồng hồ hệ thống lùi (giữ số phút đang hiện).
 *  • `1.8000001` và `1.8` là **cùng một mốc** (float → double), không phải bậc: so lệch với [RAW_EPS].
 *
 * ## Giới hạn (ghi rõ, không giấu)
 *  • Trước bậc đổi ĐẦU TIÊN sau khi app khởi động (hoặc sau khi datum rời màn rồi về, hoặc sau `null`), mốc được thấy
 *    ở một pha bất kỳ ⇒ hiển thị có thể **trễ tới 6 phút** so với sự thật; tự sửa ở bậc đổi đầu tiên.
 *  • Bậc HAL tới muộn hơn 6 phút (xe đứng máy nhưng HAL vẫn đếm? — [CHƯA BIẾT]) ⇒ hiển thị đứng ở `mốc + 5 phút` cho tới
 *    khi HAL đổi. Đây là kẹp cố ý: đứng còn hơn vượt rồi phải lùi.
 *
 * ⚠ Chỉ được cho **giá trị ĐỌC THẬT từ HAL** vào [smooth]. Giá trị đã làm mượt (vd `prev.tripHours` khi ô không hiện)
 * đưa vào lại sẽ bị coi là mốc mới cao hơn ⇒ tự nâng mốc ⇒ vượt bậc kế — xem `CarDataAdapter.Gate.dblVia`.
 *
 * ⚠ **KHÔNG đồng bộ hoá bên trong** (bốn trường `var` trần). An toàn hôm nay vì mọi đường vào đi qua
 * `CarStatusRepository.publish`, và hàm ấy `synchronized(readLock)` ⇒ loại trừ lẫn nhau **và** mốc happens-before
 * cho hai vòng poll + `refreshNow()` (đường câu hỏi bằng giọng). Ai gọi [smooth] **ngoài** khoá ấy phải tự khoá,
 * nếu không hai luồng đọc/ghi `anchorMs` có thể cho ra một lượt nhảy số mà không bài test nào bắt được.
 */
class TripTimeSmoother(private val nowMs: () -> Long = System::currentTimeMillis) {
    private var lastRaw: Double? = null
    private var anchorMs = 0L
    private var shownMin = 0
    private var lastOut = 0.0
    /** Mốc bắt đầu khoảng `null` đang mở (−1 = không có) — để dời neo, không tính phút vắng vào số hiện. */
    private var gapStartMs = -1L

    /** Mốc HAL THÔ cuối đã thấy (chưa làm mượt) — cho chẩn đoán/test; `null` = chưa có / vừa reset. */
    val raw: Double? get() = lastRaw

    /**
     * [raw] = giá trị HAL vừa đọc (giờ, bậc 0,1) hoặc `null` (đọc không ra). Trả giá trị hiển thị (giờ thập phân) —
     * `TelemetryReadout.hoursToHm` in `h:mm`.
     */
    fun smooth(raw: Double?): Double? {
        val now = nowMs()
        val r = raw ?: run {                                // đọc không ra: "—" nhưng KHÔNG quên — mở khoảng vắng
            if (lastRaw != null && gapStartMs < 0) gapStartMs = now
            return null
        }
        val last = lastRaw
        if (last == null || r < last - RAW_EPS) {          // lần đầu thấy / chuyến mới (HAL lùi) ⇒ đi thẳng, quên quá khứ
            gapStartMs = -1L; anchor(r, now); lastOut = r; return r
        }
        if (gapStartMs >= 0) {                              // đọc lại sau khoảng vắng: dời neo, phút vắng không tính
            anchorMs += (now - gapStartMs).coerceAtLeast(0L); gapStartMs = -1L
        }
        if (r > last + RAW_EPS) anchor(r, now)              // bậc HAL tăng ⇒ nhảy tiến, đồng hồ trôi đặt lại: pha chính xác
        var elapsed = now - anchorMs
        if (elapsed < 0) {                                  // đồng hồ hệ thống lùi ⇒ neo lại sao cho số phút đang hiện GIỮ nguyên
            anchorMs = now - shownMin * MIN_MS
            elapsed = shownMin * MIN_MS
        }
        shownMin = (elapsed / MIN_MS).toInt().coerceIn(0, MAX_EXTRA_MIN)
        val candidate = (lastRaw ?: r) + shownMin / 60.0
        lastOut = maxOf(candidate, lastOut)                 // không lùi giữa hai lần reset (cả khi bậc HAL nhỏ hơn số đang hiện)
        return lastOut
    }

    private fun anchor(r: Double, now: Long) { lastRaw = r; anchorMs = now; shownMin = 0 }

    companion object {
        /** Bậc của `getCurrentJourneyDriveTime` [ĐO xe 2026-09-27]: 0,1 h. Kẹp dưới đây phải < bậc này (có bài canh). */
        const val HAL_STEP_H = 0.1

        /** Số phút tối đa cộng thêm trên mốc: 5/60 h ≈ 0,083 < [HAL_STEP_H] — "1:53" là cao nhất trước khi HAL nói "1:54". */
        const val MAX_EXTRA_MIN = 5

        private const val MIN_MS = 60_000L

        /** 1e-6 h = 3,6 ms: nhỏ hơn mọi bậc thật, lớn hơn sai số float→double (`1.8000001 − 1.8 ≈ 1e-7`). */
        private const val RAW_EPS = 1e-6
    }
}
