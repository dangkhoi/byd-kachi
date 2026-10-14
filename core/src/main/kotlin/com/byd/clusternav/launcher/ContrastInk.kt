package com.byd.clusternav.launcher

import java.util.concurrent.atomic.AtomicLong

/**
 * ═══ 3.1 R29 — BỘ GIẢI MỰC: chọn mực theo NỀN THẬT TỆ NHẤT, thiếu thì xin lớp đỡ tối thiểu (`:core`, thuần) ═══════════════
 *
 * Spec `kachi-301-cast-steps-dialog-ia.html` §R29. Đầu vào: vai ([InkRole], mang sàn WCAG), chồng nền
 * ([ContrastBackdrop], đo được), và các mực ỨNG VIÊN theo thứ tự ưu tiên ([InkCandidate] — do bảng màu cấp: mực của chủ đề
 * trước, mực bảng kia sau; mã màu không sống ở đây). Đầu ra ([Resolution]):
 *
 *  1. ứng viên ĐẦU TIÊN đạt sàn trên nền **tệ nhất** ⇒ dùng nó, không đỡ. Không chọn "tương phản cao nhất": thứ tự ưu tiên
 *     giữ ý đồ thiết kế (chữ phụ vẫn là chữ phụ) và giữ kết quả ỔN ĐỊNH giữa hai nhịp trình chiếu;
 *  2. không ứng viên nào đạt ⇒ LỚP ĐỠ CỤC BỘ dưới chính phần tử (đệm mờ / đĩa — màu [InkCandidate.halo]), alpha NHỎ NHẤT
 *     theo bậc [HALO_STEP] đạt sàn; trong các ứng viên chọn cái cần alpha nhỏ nhất (hoà ⇒ thứ tự ưu tiên). Ứng viên có
 *     `ratio(ink, halo) ≥ sàn` (kiểm ở đây) thì alpha = 1 LUÔN đạt ⇒ luôn có lời giải;
 *  3. không ứng viên nào đỡ được (bảng màu hỏng) ⇒ `ok = false` + cặp tốt nhất + đếm [ContrastLedger.belowFloor] — bên
 *     `:app` ghi log. KHÔNG có nhánh im lặng dưới sàn.
 *
 * ## Vì sao KHÔNG nâng độ đục cả thẻ
 * Owner 04/10 (2.88, KDoc [ChromeOpacity]): *"tùy user chọn"* — sàn đọc được từng nâng độ đục thẻ đã bị GỠ vì thanh kéo nói
 * 60 % mà mắt thấy 30 %. Lớp đỡ ở đây chỉ nằm dưới nét chữ/icon (cỡ phần tử + đệm), thẻ vẫn đúng độ trong người dùng chọn.
 * Spec §R29 OQ: owner có muốn thêm lựa chọn "nâng thẻ" không.
 *
 * Chi phí: mực đục = 2 phép đo/lớp đỡ thử; tệ nhất ≈ 20 bậc × số ứng viên × 33 điểm (mực có alpha) ⇒ dưới 1 ms/phần tử, và chỉ
 * chạy khi sự kiện đổi (ảnh · chủ đề · độ đục · mức làm tối · khung ô), không chạy mỗi khung hình.
 */
enum class InkRole(val floor: Double) {
    /** Chữ thường (WCAG 1.4.3). */
    TEXT(4.5),

    /** Chữ phụ / chú thích — vẫn là chữ, cùng sàn 4.5 (bài học `GroupCellGrid` MUT 3.65–3.73:1, diagnostics §11). */
    SECONDARY_TEXT(4.5),

    /** Chữ lớn (≥ 18 pt hoặc 14 pt đậm, WCAG 1.4.3) — số chính của widget. */
    LARGE_TEXT(3.0),

    /** Nét icon / chấm trạng thái / thành phần đồ hoạ (WCAG 1.4.11). */
    ICON(3.0),

    /** Chữ màu nhấn (nhãn giá trị SELECT, chip đang bật). */
    ACCENT_TEXT(4.5),

    /** Icon màu nhấn (nút đang BẬT). */
    ACCENT_ICON(3.0),
}

/** Một mực ứng viên + màu LỚP ĐỠ đi kèm (mực sáng ⇒ đỡ tối, mực tối ⇒ đỡ sáng). Cả hai là ARGB; [halo] phải đục. */
data class InkCandidate(val ink: Int, val halo: Int)

/**
 * @property ink mực được chọn · [index] vị trí của nó trong danh sách ứng viên (0 = mực mặc định của chủ đề).
 * @property worst tỉ số tệ nhất ĐÃ TÍNH CẢ lớp đỡ — con số đi vào log/bảng đo.
 * @property haloAlpha 0 = không đỡ; > 0 = vẽ [halo] ở alpha này dưới phần tử.
 */
data class Resolution(
    val role: InkRole,
    val ink: Int,
    val index: Int,
    val worst: Double,
    val halo: Int,
    val haloAlpha: Double,
    val ok: Boolean,
) {
    val reinforced: Boolean get() = haloAlpha > 0.0
    val swapped: Boolean get() = index != 0

    /** Màu lớp đỡ đã nhân alpha (vẽ thẳng); trong suốt nếu không đỡ. */
    fun haloColor(): Int = ColorMath.withAlpha(halo, (haloAlpha * 255).let { Math.round(it).toInt() })

    /** Một dòng log ngắn, không cấp phát chuỗi ở đường nóng (chỉ gọi khi sự kiện đổi). */
    fun describe(): String =
        "${role.name} mực#$index ${ColorMath.hex(ink)} tệ-nhất %.2f/%.1f".format(worst, role.floor) +
            (if (reinforced) " đỡ ${ColorMath.hex(halo)}@%.2f".format(haloAlpha) else "") + (if (ok) "" else " DƯỚI-SÀN")
}

object ContrastInk {

    const val HALO_STEP = 0.05
    private const val EPS = 1e-9

    fun resolve(role: InkRole, backdrop: ContrastBackdrop, candidates: List<InkCandidate>): Resolution {
        require(candidates.isNotEmpty()) { "cần ít nhất một mực ứng viên cho ${role.name}" }
        val floor = role.floor
        var best: Resolution? = null
        for ((i, c) in candidates.withIndex()) {
            val w = backdrop.worst(c.ink)
            val r = Resolution(role, c.ink, i, w, c.halo, 0.0, w >= floor - EPS)
            if (r.ok) return ContrastLedger.note(r)
            if (best == null || w > best.worst) best = r
        }
        var pick: Resolution? = null
        for ((i, c) in candidates.withIndex()) {
            if (ColorMath.alpha(c.halo) != 255 || ColorMath.ratio(c.ink, c.halo) < floor - EPS) continue   // không đỡ được
            val limit = pick?.haloAlpha ?: 1.0
            var k = 1
            while (k * HALO_STEP <= limit + EPS) {
                val a = minOf(1.0, k * HALO_STEP)
                val w = backdrop.with(ContrastBackdrop.Layer.Flat(ColorMath.withAlpha(c.halo, alphaByte(a)))).worst(c.ink)
                if (w >= floor - EPS) {
                    if (pick == null || a < pick.haloAlpha - EPS) pick = Resolution(role, c.ink, i, w, c.halo, a, true)
                    break
                }
                k++
            }
        }
        return ContrastLedger.note(pick ?: best!!)
    }

    /** Alpha 0..1 → byte, làm tròn gần nhất (cùng luật [ChromeOpacity.alphaByte]). */
    fun alphaByte(a: Double): Int = Math.round(a.coerceIn(0.0, 1.0) * 255).toInt()
}

/**
 * Bộ đếm của bộ giải — đường ĐO (cùng tinh thần [KachiPerf]: `AtomicLong`, 0 cấp phát). `:app` in [snapshot] cùng dòng
 * hiệu năng định kỳ và ghi log mỗi lần [belowFloor] tăng (fail-loud).
 */
object ContrastLedger {
    val resolved = AtomicLong()
    val swapped = AtomicLong()
    val reinforced = AtomicLong()
    val belowFloor = AtomicLong()

    internal fun note(r: Resolution): Resolution {
        resolved.incrementAndGet()
        if (r.swapped) swapped.incrementAndGet()
        if (r.reinforced) reinforced.incrementAndGet()
        if (!r.ok) belowFloor.incrementAndGet()
        return r
    }

    fun snapshot(): String = "tương phản: giải=${resolved.get()} đổi-mực=${swapped.get()} đỡ=${reinforced.get()} dưới-sàn=${belowFloor.get()}"

    fun reset() { resolved.set(0); swapped.set(0); reinforced.set(0); belowFloor.set(0) }
}
