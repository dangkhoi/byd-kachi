package com.byd.clusternav.launcher

/**
 * ═══ 3.01 R22/R23 · DOT — NGÔN NGỮ CHẤM TRÒN trạng thái trên ảnh xe (thuần `:core`) ═══════════════════════════════
 *
 * Owner 13/10: *"thay vì vẽ hẳn 1 cái gạch màu xanh, mình vẽ chấm tròn thôi, như bọn kinex, kimlauncher thấy nó đẹp
 * hơn"* ⇒ thay lớp "kính hạ" (khoang tối + nét nhấn dọc mảnh kính) bằng MỘT chấm mỗi vật, cùng một ngôn ngữ cho bảng
 * kính ([ActionMatrixCompact]/`CarLevelsLayer`) và bảng cửa (`DoorBoardView`), cho ảnh mặc định lẫn ảnh BYD.
 *
 * Bốn trạng thái (quyết ở đây, bộ vẽ chỉ tô):
 *  • [Kind.UNKNOWN] chưa đọc được — vòng rỗng ĐỨT nét, mờ (không giấu "chưa biết" thành "đóng");
 *  • [Kind.CLOSED] đã đọc, đóng — vòng rỗng mảnh màu mực lùi;
 *  • [Kind.PARTIAL] mở một phần — vòng nhấn + hình QUẠT tô theo tỉ lệ (chọn quạt, không nửa-tô: ở 8–12 dp, 35 % và 50 %
 *    khác nhau rõ bằng góc quạt — 126° vs 180° — còn mực nước nửa-tô chỉ chênh ~1,5 dp chiều cao);
 *  • [Kind.OPEN] mở hẳn — chấm đặc màu nhấn + quầng mềm.
 * Owner 13/10 *"đẹp, bỏ số luôn em, vì cái chấm nó có thể hiện tương đối % rồi"* ⇒ chấm là chỉ báo DUY NHẤT trên màn (không
 * nhãn `%`); để mở-một-phần không lẫn với đóng/mở hẳn ở 8 dp, quạt bị kẹp [MIN_SWEEP]..`360 − MIN_SWEEP` (3 % vẫn thấy một
 * miếng; 97 % vẫn hở một khe) và chỉ mở HẲN mới có quầng. Cỡ chấm (dp) là việc trình bày ⇒ ở `:app` (`StateDotPainter`).
 *
 * Vị trí chấm = DỮ LIỆU đo trên ảnh: tâm hộp bao mảnh kính ([CarArtPanes.paneCenter], nằm đúng trên dải kính — ảnh mặc
 * định x ≈ 123/678, Seal y ≈ 395/576 giữa dải 386–404) hoặc neo bộ phận của ảnh ([CarArt.part]) cho vật không có kính.
 */
object CarDots {

    enum class Kind { UNKNOWN, CLOSED, PARTIAL, OPEN }

    /** @property fraction 0..1 (đã kẹp); [warn] = sắc thái cảnh báo ⇒ bộ vẽ lấy màu cảnh báo thay màu nhấn. */
    data class Dot(val kind: Kind, val fraction: Float, val warn: Boolean)

    /**
     * Chấm của một ô đọc số (kính): [fraction] = phân số `%` đọc được (`null` = đơn vị không phải `%`) ⇒ lùi theo sắc
     * thái (NEUTRAL = đóng, khác = mở hẳn) — cùng luật bảng kính đã dùng từ R22.
     */
    fun ofCell(available: Boolean, fraction: Float?, tone: GroupTone): Dot {
        val warn = tone == GroupTone.WARN || tone == GroupTone.ALERT
        if (!available) return Dot(Kind.UNKNOWN, 0f, warn)
        val f = (fraction ?: if (tone == GroupTone.NEUTRAL) 0f else 1f).coerceIn(0f, 1f)
        val kind = when {
            f <= 0f -> Kind.CLOSED
            f >= 1f -> Kind.OPEN
            else -> Kind.PARTIAL
        }
        return Dot(kind, f, warn)
    }

    /** Chấm của một bộ phận bảng cửa ([GroupBoard.doorPlan] đã quyết mở/đóng/chưa đọc). */
    fun ofPart(s: CarPartState): Dot {
        val warn = s.tone == GroupTone.WARN || s.tone == GroupTone.ALERT
        return when {
            s.open -> Dot(Kind.OPEN, 1f, warn)
            !s.available -> Dot(Kind.UNKNOWN, 0f, warn)
            else -> Dot(Kind.CLOSED, 0f, warn)
        }
    }

    /** Góc quạt (độ, chiều kim đồng hồ từ 12 giờ). */
    fun sweep(d: Dot): Float = when (d.kind) {
        Kind.OPEN -> FULL_TURN
        Kind.PARTIAL -> (FULL_TURN * d.fraction).coerceIn(MIN_SWEEP, FULL_TURN - MIN_SWEEP)
        else -> 0f
    }

    /** Tâm chấm (chuẩn hoá theo khung ảnh [art]): tâm mảnh kính nếu vật có kính, không thì neo bộ phận của ảnh. */
    fun center(p: CarPart, art: CarArt): CarAnchor = CarArtPanes.paneCenter(p, art) ?: art.part(p)

    const val FULL_TURN = 360f
    /** Quạt mở-một-phần nhỏ/lớn nhất (độ) — tách khỏi vòng đóng và chấm đầy ở cỡ nhỏ nhất. */
    const val MIN_SWEEP = 30f
}
