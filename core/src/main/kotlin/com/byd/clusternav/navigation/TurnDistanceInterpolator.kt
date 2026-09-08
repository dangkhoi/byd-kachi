package com.byd.clusternav.navigation

/**
 * Nội suy cự ly-tới-rẽ: **BÁM SÁT BẢN ĐỒ (GMaps) + chỉ tự lùi khi map chưa notify kịp** (model "baseline − quãng-đi").
 *
 * baseline = cự ly từ NOTIFICATION GMaps gần nhất (nguồn sự thật). Giữa 2 noti, TRỪ DẦN bằng QUÃNG ĐI THẬT =
 * tốc-độ-xe × dt × FACTOR (dead-reckon). Mỗi noti mới: SNAP baseline về giá trị map + reset quãng = 0 → bám sát.
 *
 * ★ SỬA BUG α-β (2026-07-05, user báo: dừng đèn đỏ mà km vẫn giảm rồi nhảy loạn): bản α-β ước lượng VẬN TỐC
 * riêng từ delta noti — khi dừng, GMaps ngừng gửi noti → vận tốc giữ giá trị cũ → km vẫn trừ dù đứng yên, rồi noti
 * tới thì correction lớn = nhảy. Model này TRỪ THEO TỐC-ĐỘ-THẬT (getCurrentSpeed): dừng → speed 0 → quãng không
 * tăng → km GIỮ NGUYÊN. FACTOR<1 (hơi bảo thủ) để dead-reckon lùi CHẬM hơn thực → correction luôn hướng XUỐNG,
 * không nhảy-lùi. Thay đổi hiển thị bị slew-limit cả 2 chiều → mượt, không nhảy bậy.
 *
 * THUẦN (không Android) → test được. API giữ nguyên (anchor/project/refine/reset/clearAnchor/...).
 */
object TurnDistanceInterpolator {
    private var baseline = -1              // cự ly từ noti GMaps gần nhất (m); <0 = chưa có
    private var traveled = 0.0             // quãng dead-reckon từ baseline (m); reset mỗi noti
    private var lastT = 0L                 // lần project/anchor gần nhất (tính dt)
    private var key = ""
    @Volatile private var lastOut = -1     // giá trị hiển thị gần nhất (slew baseline + debug)
    private var lastOutAt = 0L
    private var jumpStreak = 0
    @Volatile private var lastSpeed = 0.0  // tốc độ lần project gần nhất (debug/closingRate)
    @Volatile private var lastRefineSeg = -1  // J2: cự ly tới rẽ ĐỌC TRÊN MÀN GMaps gần nhất (ground-truth cho log); <0 = chưa có
    @Volatile private var lastRefineAt = 0L   // J2: mốc (elapsedRealtime ms) của lần đọc-màn gần nhất; 0 = chưa có

    // Bù over-read đồng hồ + đường cong; <1 → lùi bảo thủ (correction hướng xuống).
    // CÔNG KHAI 2026-08-22 (B-III): [TurnDistancePlausibility] trừ quãng-đi theo CÙNG mô hình nên phải dùng
    // CHUNG một hằng số — chép số 0.95 sang file thứ hai là mở đường cho hai giá trị lệch nhau về sau.
    // Chỉ đổi visibility, KHÔNG đổi thân hàm nào ⇒ 0 thay đổi hành vi.
    const val FACTOR = 0.95
    private const val MAX_EXTRAPOLATE_MS = 6000L  // map trễ >6s → NGỪNG lùi (giữ số, không trôi về 0)
    private const val JUMP_UP_M = 60       // noti cao hơn hiện >60m = đã QUA rẽ (maneuver kế) → cho reset
    private const val JUMP_UP_HYSTERESIS = 2
    private const val ARRIVED_M = 100      // cur ≤100m + cự-ly vọt lên = đã tới/qua rẽ → maneuver kế, snap NGAY (không chờ hysteresis)
    private const val SLEW_MIN = 12        // bước hiển thị tối thiểu/lần vẽ (m) khi correction (cho bắt kịp map)

    // ── B4 (2026-08-19): vệ sinh chẩn đoán screen-read ground-truth ──────────────────────────────────
    // Khi GMaps chạy NỀN, accessibility scan KHÔNG đọc lại được GMaps → mẫu đọc-màn cũ ĐÓNG BĂNG (tuổi vọt
    // lên hàng phút — đo được 224s trên chuyến chiều 2026-08-18, road rỗng). Ngưỡng này phân định "mẫu còn
    // TƯƠI" để (a) log ghi -1 (INVALID) thay vì số cũ gây nhiễu phân tích off-car, (b) refine() TỪ CHỐI snap
    // baseline bằng anchor cũ/rác. THUẦN (không Android) → test được.
    const val SCREEN_READ_STALE_MS = 2500L   // đọc-màn cũ hơn mốc này = STALE (không tin: không refine, log -1)
    const val INVALID = -1                    // giá trị log cho screen-read stale / không-đường (INVALID)

    fun anchorMeters(): Int = baseline
    fun lastProjected(): Int = lastOut
    fun closingRate(): Double = lastSpeed * FACTOR   // tốc-độ-tiếp-cận hiệu dụng (debug/log)
    /** J2: cự ly tới rẽ ĐỌC TRÊN MÀN GMaps gần nhất (ground-truth cho log so-sánh nội suy). -1 = chưa đọc được. */
    fun lastRefined(): Int = lastRefineSeg
    /** J2: mốc (elapsedRealtime ms) của lần đọc-màn gần nhất; 0 nếu chưa có (để tính tuổi mẫu). */
    fun lastRefinedAt(): Long = lastRefineAt

    /**
     * B4: PHÂN ĐỊNH screen-read cho LOG (thuần, test được). Ground-truth đọc-màn CHỈ đáng tin khi: là mẫu
     * THẬT ([meters] ≥ 0), CÒN TƯƠI (0 ≤ [ageMs] ≤ [SCREEN_READ_STALE_MS]) và CÓ ĐƯỜNG ([road] khác rỗng).
     * Khi GMaps chạy nền, scan đóng băng mẫu cũ (tuổi vọt lên, road rỗng) — ghi số đó ra log sẽ đánh lừa phân
     * tích và có nguy cơ refine bằng anchor rác. Trả [INVALID] (-1) cho mẫu stale / không-đường. Fresh+valid
     * → trả nguyên [meters] (hành vi log GIỮ NGUYÊN khi mẫu tươi hợp lệ).
     */
    fun freshScreenRead(meters: Int, ageMs: Long, road: String): Int =
        if (meters >= 0 && ageMs in 0..SCREEN_READ_STALE_MS && road.isNotBlank()) meters else INVALID

    private fun cur(): Int = if (baseline < 0) -1 else (baseline - traveled).toInt().coerceAtLeast(0)

    @Synchronized
    fun anchor(seg: Int, maneuverKey: String, nowMs: Long, speedMps: Double = -1.0) {
        if (seg < 0) return
        val c = cur()
        val keyChanged = maneuverKey != key
        val bigJump = c >= 0 && seg > c + JUMP_UP_M
        // ĐÃ SÁT/QUA rẽ (cur ≤ ARRIVED_M) mà cự-ly VỌT LÊN = maneuver KẾ. GMaps GIỮ nguyên "key" khi 2 rẽ liên
        // tiếp CÙNG tên đường (vd "Nguyễn Văn Linh|Đường NVL") → keyChanged=false → không rơi vào nhánh trên.
        // Phải SNAP NGAY (không chờ hysteresis): nếu chờ, dead-reckon kéo hiển thị VỀ 0 rồi mới nhảy = bug "nhảy số"
        // (đo trên xe: disp kẹt 0 suốt ~15s khi raw=1600 rồi mới nhảy +1500). Khi cur LỚN thì vẫn qua hysteresis
        // (chống 1 noti cũ lẻ vọt lên giữa maneuver).
        val passedTurnJump = bigJump && c in 0..ARRIVED_M
        when {
            // MANEUVER MỚI (đổi key) → cự ly của rẽ KHÁC (thường vọt lên). Cú NHẢY ĐÚNG, bỏ slew để SNAP thẳng
            // (không thì slew kẹp ±step khiến display bò dần lên = "km tăng dần"). resetSlew=true xử lý.
            baseline < 0 || keyChanged -> { key = maneuverKey; snapTo(seg, nowMs, resetSlew = true); jumpStreak = 0 }
            passedTurnJump -> { jumpStreak = 0; snapTo(seg, nowMs, resetSlew = true) }   // qua rẽ (cùng key) → maneuver kế, snap NGAY
            bigJump -> {
                jumpStreak++
                if (jumpStreak < JUMP_UP_HYSTERESIS) return    // 1 noti cũ lẻ vọt lên GIỮA maneuver (cur lớn): bỏ
                snapTo(seg, nowMs, resetSlew = true)           // xác nhận → snap (nhảy đúng, bỏ slew)
            }
            else -> { jumpStreak = 0; snapTo(seg, nowMs) }     // SNAP baseline về map (bám sát), reset quãng — GIỮ slew cho mượt
        }
    }

    /** Cự ly hiển thị (m). -1 nếu chưa có. Trừ theo TỐC-ĐỘ-THẬT (dừng → giữ số). slew-limit 2 chiều cho mượt. */
    @Synchronized
    fun project(speedMps: Double, nowMs: Long): Int {
        if (baseline < 0) return -1
        val v = speedMps.coerceAtLeast(0.0)
        lastSpeed = v
        val dt = (nowMs - lastT).coerceIn(0L, MAX_EXTRAPOLATE_MS) / 1000.0
        traveled += v * FACTOR * dt             // rate = TỐC ĐỘ THẬT → dừng (v=0) → không tăng → km GIỮ
        lastT = nowMs
        var out = (baseline - traveled).toInt().coerceAtLeast(0)
        // slew: giới hạn thay đổi hiển thị mỗi lần vẽ CẢ 2 CHIỀU → cú snap-về-map trượt mượt, không nhảy bậy.
        if (lastOutAt != 0L && lastOut >= 0) {
            val cdt = ((nowMs - lastOutAt) / 1000.0).coerceIn(0.0, 1.0)
            val step = maxOf((v * FACTOR * cdt * 2.0).toInt(), SLEW_MIN)   // ≤2× tốc độ: đủ bắt kịp correction
            out = out.coerceIn((lastOut - step).coerceAtLeast(0), lastOut + step)
        }
        lastOut = out; lastOutAt = nowMs
        return out
    }

    /**
     * Tinh chỉnh bằng cự ly ĐỌC MÀN (accessibility) = ground-truth → snap baseline (như 1 noti tươi).
     *
     * B4 (2026-08-19): [readAgeMs] = tuổi mẫu đọc-màn (ms). GUARD: mẫu STALE (readAgeMs > [SCREEN_READ_STALE_MS])
     * hoặc INVALID (seg < 0) → TỪ CHỐI hẳn: KHÔNG ghi ground-truth, KHÔNG snap (khỏi refine bằng anchor cũ/rác
     * khi GMaps chạy nền, a11y không refresh được). Mặc định readAgeMs=0 (mẫu vừa scan = tươi) → caller đọc-màn
     * hiện tại GIỮ NGUYÊN hành vi khi fresh+valid.
     */
    @Synchronized
    fun refine(seg: Int, nowMs: Long, readAgeMs: Long = 0L) {
        if (seg < 0 || readAgeMs > SCREEN_READ_STALE_MS) return       // stale/invalid → reject (no ground-truth, no snap)
        lastRefineSeg = seg; lastRefineAt = nowMs                     // J2: GHI ground-truth đọc-màn cho log (kể cả khi chưa có baseline)
        if (baseline < 0) return
        snapTo(seg, nowMs)
    }

    @Synchronized
    fun reset() {
        baseline = -1; traveled = 0.0; lastT = 0L; key = ""; lastOut = -1; lastOutAt = 0L; jumpStreak = 0; lastSpeed = 0.0
        lastRefineSeg = -1; lastRefineAt = 0L
    }

    /** Q5: frame chỉ-hướng → xoá track NHƯNG giữ key (khỏi coi noti sau là maneuver mới). */
    @Synchronized
    fun clearAnchor() {
        baseline = -1; traveled = 0.0; lastT = 0L; lastOut = -1; lastOutAt = 0L; jumpStreak = 0
    }

    private fun snapTo(seg: Int, nowMs: Long, resetSlew: Boolean = false) {
        baseline = seg; traveled = 0.0; lastT = nowMs
        // resetSlew: cú snap sang MANEUVER MỚI (cự ly rẽ khác) → xoá lịch sử slew để project() kế snap THẲNG về
        // baseline mới (out=baseline), không bị kẹp ±step bò dần từ giá trị rẽ cũ. Snap TRONG-CÙNG-rẽ giữ slew (mượt).
        if (resetSlew) { lastOut = -1; lastOutAt = 0L }
    }
}
