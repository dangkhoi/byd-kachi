package com.byd.clusternav.navigation

/**
 * Trạng thái MỘT kênh ảnh (mũi tên / làn / camera) tại thời điểm một tick: còn tươi không, và **của app nào**.
 *
 * [pkg] vẫn được mang theo cả khi [fresh] = false để log/diag đọc được "kênh này của ai mà đã hết hạn".
 */
data class NavChannelState(val fresh: Boolean, val pkg: String?) {
    companion object {
        /** Kênh chưa từng publish (không tươi, không chủ). */
        val ABSENT = NavChannelState(fresh = false, pkg = null)
    }
}

/**
 * Một sample kênh ảnh: **giá trị + chủ sở hữu + mốc thời gian trong CÙNG một object bất biến**.
 *
 * Vì sao bắt buộc đi cùng nhau (B-I): nếu pkg và giá trị nằm ở hai `@Volatile` rời thì luồng capture có thể
 * publish XEN GIỮA hai lần đọc của owner ⇒ owner ghép pkg của app mới với giá trị của app cũ. Bất biến khi đó
 * chỉ có trên danh nghĩa. Đọc MỘT lần ra một sample ⇒ không thể đọc-xé.
 */
interface NavSignalSample {
    val pkg: String
    val atMs: Long
}

/**
 * Chấm tươi một sample tại [now] với ngưỡng [staleMs] → [NavChannelState]. Null/chưa publish ⇒
 * [NavChannelState.ABSENT] (im lặng, không đoán).
 */
fun NavSignalSample?.stateAt(now: Long, staleMs: Long): NavChannelState =
    if (this != null && atMs > 0L && now - atMs <= staleMs) NavChannelState(true, pkg)
    else NavChannelState(false, this?.pkg)

/**
 * Kế hoạch một tick đầu ra của [com.byd.clusternav.NavOutputOwner] (T4, spec `b3-full-nav-capture`
 * §R3/§R4/§R5 + §R-BI). Thuần dữ liệu — nói owner CHANNEL nào cần bắn frame này, hoặc phải CLEAR.
 *
 * "CỨ BẮN" (R3a/OQ4): kênh nào còn tươi **và thuộc đúng [framePkg]** thì bắn kênh đó, KHÔNG gate theo kênh
 * khác (arrow không chờ lane, lane không chờ camera…). [clear] chỉ bật khi TẤT CẢ kênh đã stale (không còn
 * gì để giữ) → owner nhả frame.
 */
data class NavOutputPlan(
    val pushArrow: Boolean,
    val pushLane: Boolean,
    val pushCamera: Boolean,
    val clear: Boolean,
    /** DANH TÍNH của khung này (§R-BI). null ⇔ không bắn kênh nào. */
    val framePkg: String? = null,
    /** Kênh TƯƠI nhưng KHÁC package khung ⇒ bị DROP. Chỉ để log/diag — không đổi quyết định. */
    val droppedArrow: Boolean = false,
    val droppedLane: Boolean = false,
    val droppedCamera: Boolean = false,
) {
    /** Có ít nhất một kênh cần bắn frame này. */
    val anyPush: Boolean get() = pushArrow || pushLane || pushCamera

    /** Có kênh tươi nào bị loại vì lệch danh tính không (dòng log DROP của owner). */
    val anyDropped: Boolean get() = droppedArrow || droppedLane || droppedCamera
}

/**
 * Logic QUYẾT ĐỊNH THUẦN (không Android) cho đường payload gốc B3 (T4): cho trạng thái từng kênh
 * (tươi + package, dựng từ `ScreenCaptureSignal.arrow/lane/camera` qua [stateAt]) → owner bắn kênh nào /
 * khi nào clear, cộng hai hàm map giá trị (lane-arrow code, camera icon code). Tách ra :core để KHOÁ bằng
 * unit-test off-car (giống [Maneuver]/[ManeuverSignature]); owner (:app) chỉ đọc sample + gọi `BydHal`.
 *
 * KEEP-ALIVE (R-nf, giống [HudKeepAlivePolicy]): owner gọi tick định kỳ (nhịp [HudKeepAlivePolicy.DEFAULT_INTERVAL_MS]
 * = 250ms). Mỗi tick đọc lại mức tươi SỐNG của tín hiệu → [decide] trả cùng plan "bắn" khi còn tươi ⇒ RE-ASSERT
 * nội dung mỗi nhịp (OEM không blank); khi hết tươi ⇒ [NavOutputPlan.clear] = true ⇒ owner nhả frame ĐÚNG một lần.
 * Ngưỡng "hết tươi" là `ScreenCaptureSignal.STALE_MS` (6s) — HỢP LÝ cho nguồn ẢNH (capture tick 2Hz liên tục khi
 * đang đọc; gap > 6s = capture đã dừng), KHÁC trần 180s của đường DATA (noti GMaps có thể giãn 108s mà vẫn đang dẫn).
 *
 * §R-BI — BẤT BIẾN MỘT-PACKAGE-MỘT-KHUNG: xem [NavFrameIdentity]. Danh tính rơi bậc **ARROW > LANE > CAMERA**.
 * ⚠ ĐÃ CÂN NHẮC VÀ LOẠI phương án "kênh mới nhất thắng" (đừng đổi lại): khi `CaptureForegroundSource` dao động
 * — đã xảy ra THẬT, B3.7 overlay WazeMod đè VietMap — danh tính sẽ LẬT mỗi tick ⇒ cụm nhấp nháy trên xe đang
 * chạy. Ưu tiên cố định là tất định; camera của app khác chờ tối đa 6s (STALE_MS) là cái giá đã chấp nhận,
 * vì DROP là hiện ÍT hơn, không bao giờ hiện SAI.
 */
object NavOutputDecision {

    /**
     * Từ trạng thái 3 kênh → [NavOutputPlan].
     *
     * 1. [NavOutputPlan.framePkg] = package của kênh TƯƠI đầu tiên có pkg khác rỗng, theo thứ tự
     *    arrow → lane → camera (rơi bậc: mất mũi tên thì làn dựng khung, mất cả hai thì camera — giữ nguyên
     *    ca camera-only của VietMap và ca lane-only đã chạy).
     * 2. Bắn kênh tươi **cùng** [NavOutputPlan.framePkg] ([NavFrameIdentity.sameFrame]); kênh tươi khác
     *    package ⇒ DROP im lặng (cờ `dropped*` chỉ để log).
     * 3. [NavOutputPlan.clear] = không kênh nào tươi. **DROP tuyệt đối KHÔNG kéo theo clear** — nếu không,
     *    khung đang hiện sẽ nhấp nháy mỗi khi có một kênh lạ xuất hiện.
     */
    fun decide(arrow: NavChannelState, lane: NavChannelState, camera: NavChannelState): NavOutputPlan {
        val framePkg = identityOf(arrow) ?: identityOf(lane) ?: identityOf(camera)
        fun push(c: NavChannelState) = c.fresh && NavFrameIdentity.sameFrame(framePkg, c.pkg)
        fun dropped(c: NavChannelState) = c.fresh && !NavFrameIdentity.sameFrame(framePkg, c.pkg)
        val anyFresh = arrow.fresh || lane.fresh || camera.fresh
        return NavOutputPlan(
            pushArrow = push(arrow),
            pushLane = push(lane),
            pushCamera = push(camera),
            clear = !anyFresh,
            framePkg = framePkg,
            droppedArrow = dropped(arrow),
            droppedLane = dropped(lane),
            droppedCamera = dropped(camera),
        )
    }

    /** Kênh này có đủ tư cách DỰNG danh tính khung không (tươi + có chủ rõ ràng). */
    private fun identityOf(c: NavChannelState): String? = c.pkg?.takeIf { c.fresh && it.isNotEmpty() }

    /**
     * Map các mũi tên đi-được của MỘT làn → một mã ghi vào `LANE_n_GUIDANCE_ARROW_SET`.
     *
     * Dùng từ vựng AMAP NEW_ICON của làn-cụm ([Maneuver.toAmapIcon]) của mũi tên CHÍNH (phần tử đầu) — cùng
     * ngôn ngữ mà dải làn cụm OEM đọc. Làn rỗng (không rõ hướng) → 0 (không glyph).
     *
     * ⚠ ON-CAR-VERIFY (OQ2/OQ3): từ vựng glyph CHÍNH XÁC của register làn (làn nhiều hướng: thẳng+phải…) chưa
     * được chốt trên xe — đây là mã hợp lý, khoá bằng test + tinh chỉnh khi có crop làn thật.
     */
    fun laneArrowCode(arrows: List<Maneuver>): Int = arrows.firstOrNull()?.toAmapIcon() ?: 0

    /**
     * Mã icon cho `INSTRUMENT_GUIDE_INFO_CAMERA_SET`: 1 khi CÓ camera (bật icon), 0 khi không (clear).
     * ⚠ ON-CAR-VERIFY (OQ4): từ vựng loại camera (tốc-độ / đèn-đỏ …) chưa chốt — vòng này chỉ bật/tắt icon.
     */
    fun cameraIconCode(hasCamera: Boolean): Int = if (hasCamera) 1 else 0
}
