package com.byd.clusternav.comfort

/**
 * LỌC BỤI MỊN (PM2.5) TỰ ĐỘNG · pure model (không Android, unit-test off-car được).
 *
 * ── NGUỒN (RE: javap SDK + jadx OpenBYD stub) ────────────────────────────────────────────────────
 * ĐỌC: `android.hardware.bydauto.pm2p5.BYDAutoPM2p5Device` (device_type 1008) — `getPM2p5Level()` trả
 * `int[]`, phần tử `[0]` = MỨC bụi (bảng dưới, càng CAO càng BẨN); `getPM2p5Value()[0]` = raw µg/m³ (0..3000).
 * GHI (tự lọc, không popup): các method trên `BYDAutoAcDevice` — `enablePurificationFunctionPrompt(int)` /
 * `setAutoCleanAirState(int)` / `setQuickCleanAirState(int)` — CHỈ có trên ROM xe, KHÔNG có trong SDK jar ⇒
 * app gọi qua REFLECTION ở `:app` ([com.byd.clusternav.comfort.Pm25FilterApplier]). Lớp này CHỈ giữ bảng
 * mức + phân loại + nhãn song ngữ (thuần, không chạm HAL), để test off-car khoá được.
 *
 * Bảng mức PM2.5 (getPM2p5Level()[0]) — càng cao càng bẩn:
 * | Hằng          | Giá trị | Nghĩa                       |
 * |---------------|---------|-----------------------------|
 * | INVALID       | 0       | không đọc được / chưa có     |
 * | EXCELLENT     | 1       | rất sạch                    |
 * | GOOD          | 2       | tốt                         |
 * | LOW_GRADE     | 3       | khá                         |
 * | MIDDLE        | 4       | trung bình                  |
 * | HEAVY         | 5       | nặng   ← ngưỡng "bẩn" mặc định |
 * | SERIOUS       | 6       | nghiêm trọng                |
 */
object Pm25Filter {

    /** Mức PM2.5 (getPM2p5Level()[0]). Càng cao càng bẩn. INVALID = chưa đọc được. */
    const val INVALID = 0
    const val EXCELLENT = 1
    const val GOOD = 2
    const val LOW_GRADE = 3
    const val MIDDLE = 4
    const val HEAVY = 5
    const val SERIOUS = 6

    /**
     * Ngưỡng "bẩn" mặc định = [HEAVY] (5). Owner: khi bật lọc, nếu KHI ĐÓ mức đã ≥ HEAVY thì lọc-ngay
     * (`setQuickCleanAirState(1)`) chứ không chỉ bật lọc-liên-tục. Ngưỡng này là hằng chỉnh-được duy nhất.
     */
    const val DEFAULT_THRESHOLD = HEAVY

    /**
     * Không khí có "bẩn" ở [level] so với [threshold] không? True khi [level] HỢP LỆ và ≥ [threshold]
     * (tức trong dải `threshold..SERIOUS`). INVALID(0) và mức < threshold → false. Degrade-safe: mức ngoài
     * dải (âm / > SERIOUS) đều false, không ném.
     */
    fun isDirty(level: Int, threshold: Int = DEFAULT_THRESHOLD): Boolean = level in threshold..SERIOUS

    /** Nhãn mức bụi tiếng Việt. INVALID / mức không rõ → "—" (dấu gạch, dùng khi off-car không đọc được). */
    fun levelLabelVi(level: Int): String = when (level) {
        EXCELLENT -> "Rất tốt"
        GOOD -> "Tốt"
        LOW_GRADE -> "Khá"
        MIDDLE -> "Trung bình"
        HEAVY -> "Nặng"
        SERIOUS -> "Nghiêm trọng"
        else -> "—"
    }

    /** Nhãn mức bụi tiếng Anh. INVALID / mức không rõ → "—". */
    fun levelLabelEn(level: Int): String = when (level) {
        EXCELLENT -> "Excellent"
        GOOD -> "Good"
        LOW_GRADE -> "Low grade"
        MIDDLE -> "Moderate"
        HEAVY -> "Heavy"
        SERIOUS -> "Serious"
        else -> "—"
    }
}
