package com.byd.clusternav.launcher

/**
 * ═══ R29-RESOLVE-STORM — một lời TÔ mực (`AdaptiveInk.text/icon`) có được kéo theo một lượt CHỌN HỌ của mặt gốc không ═════════
 *
 * [ĐO xe 15/10, `docs/diagnostics/oncar-perf-2026-10-15-evening.md` §D] `mặt` (lượt chọn họ) lên ~700/phút trong khi `đổi-họ` chỉ
 * 7–22 ⇒ >95 % công vô ích. [ĐO máy ảo 15/10, §F] kẻ kích: nhịp đổ số (~1 Hz khi tốc độ đổi) tô lại nhãn nhiệt lốp đang `GONE`
 * trên khung 3D — phần tử CHƯA ĐO ĐƯỢC không bao giờ có nền ⇒ mỗi lời tô là một lượt đi cả cây + chọn họ (`tô-chưa-đo` 180/phút).
 *
 * Luật (chung mọi phần tử, không theo tên view):
 *  • [Ink.PAINT] — phần tử đã có nền, mặt chứa nó còn tươi, mặt gốc đã có họ, phần tử đã nằm trong lượt chọn ⇒ chỉ tra/tô.
 *  • [Ink.DEFER] — còn thiếu gì đó NHƯNG phần tử / mặt chứa / mặt gốc chưa đo được (chưa gắn · `GONE` · 0×0) ⇒ một lượt chọn
 *    KHÔNG làm nó đo được (`groundOf`/`measure` cũng bỏ qua đúng ca này) ⇒ chỉ giữ dấu; lượt giải đến từ `OnLayoutChangeListener`
 *    của mặt khi nó thật sự có cỡ (`requestLayout` khi bỏ `GONE` dựng cờ layout cho cả dòng cha — AOSP r47 `View.java:15200`,
 *    `:24465`, `:24563`, `:21926`).
 *  • [Ink.SOLVE] — đo được mà thiếu (phần tử/vai MỚI · bảng/ảnh/độ đục đổi · mặt chưa có họ) ⇒ một lượt, gộp qua [InkCoalescer].
 *
 * THUẦN ⇒ `InkSolveGateTest` khoá off-device.
 */
object InkSolveGate {

    enum class Ink { PAINT, DEFER, SOLVE }

    /**
     * [grounded] phần tử đã có nền đo · [measurable] phần tử + mặt chứa + mặt gốc đều đã gắn và khác 0×0 · [stale] mặt chứa có nền
     * cũ theo bảng/ảnh/độ đục · [chosen] mặt gốc đã có họ · [seen] (phần tử, vai) có trong lượt chọn cuối.
     */
    fun onInk(grounded: Boolean, measurable: Boolean, stale: Boolean, chosen: Boolean, seen: Boolean): Ink = when {
        grounded && !stale && chosen && seen -> Ink.PAINT
        !measurable -> Ink.DEFER
        else -> Ink.SOLVE
    }

    /** Vì sao một lời tô [Ink.SOLVE] — cho bộ đếm [ContrastLedger.trigger]. */
    fun why(stale: Boolean, chosen: Boolean): ContrastLedger.Trigger = when {
        stale -> ContrastLedger.Trigger.INK_STALE
        !chosen -> ContrastLedger.Trigger.INK_FIRST
        else -> ContrastLedger.Trigger.INK_NEW
    }
}

/**
 * Gộp lời xin chọn họ theo MẶT GỐC: lời ĐẦU của một mặt trong một lượt vòng lặp chính chạy NGAY (giữ màu trả về đúng cho chỗ gọi
 * đọc nó — `KachiTopStrip` chip), mọi lời sau của cùng mặt trong lượt đó dồn vào MỘT lượt hẹn ở lượt kế ([post] = `Handler.post`
 * ở `:app`, chạy trước khung vẽ kế). Một hẹn duy nhất mỗi lượt, không nhịp lặp: không ai xin ⇒ không hẹn. CHỈ luồng chính.
 */
class InkCoalescer<K : Any>(private val post: (Runnable) -> Unit, private val solve: (K) -> Unit) {
    private val ran = HashSet<K>()
    private val pending = LinkedHashSet<K>()
    private var armed = false
    private var inTurn = false

    /** `true` ⇒ chỗ gọi giải [k] NGAY; `false` ⇒ đã dồn (hoặc đã có hẹn) cho lượt kế. */
    fun request(k: K): Boolean {
        // INK-COALESCER-EMPTY-TURN (soát Pass 9 [P3]): không hẹn nào đang chờ và không ở trong lượt hẹn ⇒ [ran] là của một
        // lượt ĐÃ XONG ⇒ xoá tại đây, thay vì hẹn thêm một lượt rỗng chỉ để `ran.clear()` sau mỗi lô.
        if (!armed && !inTurn) ran.clear()
        arm()
        if (ran.add(k)) return true
        pending += k
        return false
    }

    private fun arm() { if (!armed) { armed = true; post(turn) } }

    /** Lượt hẹn: giải lô đã dồn. Chỉ hẹn tiếp khi chính lượt giải sinh lời xin mới (qua [request] ⇒ [arm]). */
    private val turn = Runnable {
        armed = false; ran.clear()
        val batch = pending.toList(); pending.clear()
        inTurn = true
        try { for (k in batch) { ran += k; solve(k) } } finally { inTurn = false }
    }
}
