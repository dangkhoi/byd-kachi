package com.byd.clusternav.launcher

/**
 * ═══ VISUAL-REFRESH P1b · R8 · AC8.5 — TỰ BẢO VỆ TƯƠNG PHẢN ═══════════════════════════════════════════════════
 *
 * Owner 2026-09-16: *"có cho người ta chọn màu không nhỉ?"* ⇒ AC8.5: *"màu nào kéo chữ xuống < 4,5:1 thì app tự
 * đậm hoá nền hoặc đổi mực (không cấm chọn, không để người dùng tự làm xấu lúc lái đêm)"*.
 *
 * ## Thứ tự ưu tiên — và vì sao là thứ tự đó
 *  1. **Đổi mực trước, giữ nguyên màu người dùng chọn.** Chọn *trắng ấm* làm màu nhấn rồi bị app tối nó lại
 *     thành xám để chữ trắng đọc được là trả lại một màu người dùng **không** chọn. Chữ đậm trên nút sáng là
 *     cách các HMI sáng vẫn làm; màu vẫn là màu của họ.
 *  2. **Chỉ khi không mực nào đủ** mới đẩy nền về phía [pushToward] (nền màn của chủ đề) từng bậc [STEP], và ở
 *     mỗi bậc thử lại **mọi** mực. Đẩy tối thiểu: dừng ở bậc đầu tiên đạt sàn.
 *  3. Hết [maxSteps] mà vẫn chưa đạt ⇒ trả về **cặp tốt nhất đã thấy**, kèm `ok = false` để bài canh nói ra.
 *     Không có nhánh im lặng: cặp nào chưa đạt sàn thì bảng đo §6.4 chấm ❌.
 *
 * Bài canh: `ContrastGuardTest` (`:core`) + `ColorChoiceContractTest` (`:app`, chạy trên mọi lựa chọn thật).
 */
object ContrastGuard {

    /** Mỗi bậc đẩy nền: 8 % về phía nền màn. 8 bậc = tối đa 49 % — đủ để một nút *trắng ấm* trên nền trắng ra xám đọc được. */
    const val STEP = 0.08
    const val MAX_STEPS = 8

    /**
     * @property grounds nền sau khi đẩy (cùng thứ tự đầu vào).
     * @property ink mực đã chọn (có thể là mực gốc).
     * @property steps số bậc đã đẩy nền (0 = nền giữ nguyên).
     * @property swapped mực có bị đổi không.
     * @property worst tỉ số **thấp nhất** trên mọi nền — đây là con số ghi vào bảng đo.
     */
    data class Fit(val grounds: IntArray, val ink: Int, val steps: Int, val swapped: Boolean, val worst: Double) {
        val ok: Boolean get() = worst >= 4.5 - 1e-9
    }

    /**
     * Tìm cặp (mực, nền) đạt [floor] trên **mọi** nền của [grounds] — hai đầu gradient được đưa cùng lúc, vì chữ
     * nằm trên một điểm cụ thể chứ không nằm trên giá trị trung bình (AC3.3).
     *
     * @param ink mực đang dùng.
     * @param grounds các nền ĐỤC (vai có alpha phải trộn trước; xem [fitAlpha]).
     * @param altInks mực thay thế được phép, theo thứ tự ưu tiên.
     * @param pushToward màu để đẩy nền về phía nó (nền màn của chủ đề).
     */
    fun fit(
        ink: Int,
        grounds: IntArray,
        floor: Double = 4.5,
        altInks: List<Int> = emptyList(),
        pushToward: Int,
        maxSteps: Int = MAX_STEPS,
    ): Fit {
        require(grounds.isNotEmpty()) { "cần ít nhất một nền" }
        val inks = listOf(ink) + altInks.filter { it != ink }
        var best: Fit? = null
        var current = grounds.copyOf()
        for (step in 0..maxSteps) {
            for (candidate in inks) {
                val worst = current.minOf { ColorMath.ratio(candidate, it) }
                val fit = Fit(current, candidate, step, candidate != ink, worst)
                if (worst >= floor) return fit
                if (best == null || worst > best.worst) best = fit
            }
            current = IntArray(current.size) { ColorMath.mix(current[it], pushToward, STEP) }
        }
        return best!!
    }

    /**
     * Bản cho **vai có alpha** (ô đang bật, thẻ đang bật): nền thật = `over(role, under)`. Ở đây không đẩy nền
     * về phía nền màn (làm thế là đổi ý nghĩa của vai) mà **chỉnh alpha** của vai — thử cả hai chiều, chọn mức
     * đổi ít nhất đạt sàn; và cũng đổi mực trước khi chỉnh alpha, cùng thứ tự ưu tiên với [fit].
     *
     * @return `(vai đã chỉnh alpha, mực)` cùng tỉ số thấp nhất.
     */
    fun fitAlpha(ink: Int, role: Int, under: Int, floor: Double = 4.5, altInks: List<Int> = emptyList()): AlphaFit {
        val inks = listOf(ink) + altInks.filter { it != ink }
        val a0 = ColorMath.alpha(role)
        var best: AlphaFit? = null
        // 0 → giữ nguyên; rồi ±20, ±40 … tới ±200 (mỗi bước ~8 %) — mức đổi nhỏ nhất thắng.
        for (k in 0..10) {
            for (sign in if (k == 0) intArrayOf(0) else intArrayOf(-1, 1)) {
                val a = (a0 + sign * k * 20).coerceIn(0, 255)
                val adjusted = ColorMath.withAlpha(role, a)
                val ground = ColorMath.over(adjusted, under)
                for (candidate in inks) {
                    val r = ColorMath.ratio(candidate, ground)
                    val f = AlphaFit(adjusted, candidate, candidate != ink, r)
                    if (r >= floor) return f
                    if (best == null || r > best.worst) best = f
                }
            }
        }
        return best!!
    }

    data class AlphaFit(val role: Int, val ink: Int, val swapped: Boolean, val worst: Double) {
        val ok: Boolean get() = worst >= 4.5 - 1e-9
    }

    /**
     * Chỉnh **mực** (không chỉnh nền): kéo [ink] về phía [toward] từng bậc cho tới khi đạt [floor] trên mọi nền.
     * Dùng cho vai *mực nhấn* (`accentInk`): nền là thẻ thường (không phải màu người dùng chọn) nên không được
     * đẩy; thứ phải nhường là chính mực nhấn.
     */
    fun fitInk(ink: Int, grounds: IntArray, floor: Double = 4.5, toward: Int, maxSteps: Int = MAX_STEPS): Int {
        var c = ink
        repeat(maxSteps + 1) {
            if (grounds.all { ColorMath.ratio(c, it) >= floor }) return c
            c = ColorMath.mix(c, toward, STEP * 1.5)
        }
        return c
    }
}
