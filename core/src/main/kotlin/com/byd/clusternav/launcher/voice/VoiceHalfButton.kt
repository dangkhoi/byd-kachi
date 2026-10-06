package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ 2.93 · NÚT "NỬA" CỦA MỘT NÚT — *"mở kính trước trái một nửa"* ⇒ nút 50% của kính lái ════════════════════════════════
 *
 * Spec `docs/specs/kachi-293-voice.html` §10. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Bệnh — [ĐO off-car 07/10, bộ phân tích thật]
 * Từ 1.94 kính là nút TOGGLE và mức 50% là một nút RIÊNG (`ControlRegistry`: *"5 nút 50%"*). Cờ NỬA của câu
 * ([VoiceControlParse.mentionsHalf]) chỉ được đọc cho nút COVER, nên câu chỉ ra nút 50% khi nó trùng NGUYÊN một cách gọi đã khai
 * (*"mở kính lái một nửa"*); mọi cách nói khác — *"mở kính trước trái một nửa"* · *"mở một nửa kính lái"* · *"mở kính phụ 50%"* —
 * ra nút MỞ HẾT của kính ấy (`Control(win_lf, 1)`): người lái xin hé kính thì kính hạ hết.
 *
 * ## Luật — quan hệ SUY từ chính từ vựng, không bảng tay, không mã nút (CLAUDE.md §7)
 * Nút H là nút "nửa" của nút C khi MỘT cách gọi của H = dấu nửa + MỘT cách gọi của C, ở đầu hay cuối (*"nửa kính lái"* ·
 * *"kính lái một nửa"* · *"50% kính lái"* — nhãn của nút 50% là chính nhãn C thêm *"50%"*), dấu nửa chỉ gồm *"nửa"* · *"một"* · con số
 * và phải nói lên NỬA ([VoiceControlParse.mentionsHalf]); H là TOGGLE và là ứng viên DUY NHẤT. [ĐO 07/10] quan hệ ra đúng 5 cặp:
 * 4 kính riêng + nút gộp 4 kính. Thêm một nút có nhãn *"50% …"* ở bộ đăng ký là câu tự nói được — không sửa gì ở đây.
 */
internal object VoiceHalfButton {

    /** Nút "nửa" của nút [id], hoặc `null` khi không có (hay nhập nhằng). */
    fun of(id: String): String? = HALF_OF[id]

    /**
     * Dấu nửa đứng NGAY cạnh tên nút — liền trước ([before] = các từ đứng trước tên trong vế) hay liền sau ([after]). Cờ nửa của
     * CẢ vế ([VoiceControlParse.mentionsHalf]) không đủ: [ĐO 07/10] *"hạ kiếng phải xong mở nửa kính lái"* (một vế, hai lệnh) mà
     * dùng cờ cả vế thì kính PHẢI cũng thành nút 50%.
     */
    fun markedNear(before: List<Token>, after: List<Token>): Boolean =
        VoiceControlParse.mentionsHalf(before.takeLastWhile { mark(it) }) ||
            VoiceControlParse.mentionsHalf(after.takeWhile { mark(it) })

    /** Từ có thể nằm trong một dấu nửa: *"nửa"* · số (*"một"*, *"năm mươi"*, *"50%"*) · *"phần trăm"*. */
    private fun mark(t: Token): Boolean =
        t.norm == HALF || t.norm in PERCENT || t.norm in VoiceLexicon.NUMBER_WORDS || t.norm.trimEnd('%').toIntOrNull() != null

    /** *"phần trăm"* đã bỏ dấu. */
    private val PERCENT = setOf("phan", "tram")

    /** Bảng C → H, dựng MỘT lần từ từ vựng tĩnh (nhãn + nhãn ngắn + đồng nghĩa của bộ đăng ký). */
    internal val HALF_OF: Map<String, String> by lazy {
        val names = VoiceGrammar.terms().filter { it.kind == VoiceTermKind.CONTROL }.groupBy({ it.id }, { it.words })
        names.keys.mapNotNull { base ->
            val baseNames = names.getValue(base)
            val halves = names.filter { (h, hNames) -> h != base && hNames.any { w -> baseNames.any { b -> halfForm(w, b) } } }.keys
            halves.singleOrNull()?.takeIf { ControlRegistry.byId(it)?.kind == ControlKind.TOGGLE }?.let { base to it }
        }.toMap()
    }

    /**
     * [h] = dấu nửa + [b] (hay [b] + dấu nửa). Dấu nửa ≤ 2 từ, MỖI từ là *"nửa"* · *"một"* (của *"một nửa"*) · số 50 (*"50%"*), và
     * cả dấu nói lên nửa. ⚠ [ĐO 07/10] nhận mọi con số thì *"50% 4 kính"* (nút 50% của cả 4 kính) thành dấu nửa + *"kính"* (một cách
     * gọi của kính LÁI) ⇒ kính lái có HAI ứng viên ⇒ mất quan hệ đúng của nó.
     */
    private fun halfForm(h: List<String>, b: List<String>): Boolean {
        if (h.size <= b.size) return false
        val mark = when {
            h.subList(h.size - b.size, h.size) == b -> h.subList(0, h.size - b.size)
            h.subList(0, b.size) == b -> h.subList(b.size, h.size)
            else -> return false
        }
        return mark.size <= 2 &&
            mark.all { it == HALF || it == ONE || it.trimEnd('%').toIntOrNull() == FIFTY } &&
            VoiceControlParse.mentionsHalf(mark.map { Token(it, it) })
    }

    /** Chữ *"nửa"* đã bỏ dấu — cùng chữ mà [VoiceControlParse.mentionsHalf] đọc. */
    private const val HALF = "nua"

    /** *"một"* của *"một nửa"* (bỏ dấu); đứng một mình thì [VoiceControlParse.mentionsHalf] loại. */
    private const val ONE = "mot"

    /** *"50%"* — cùng con số mà [VoiceControlParse.mentionsHalf] coi là nửa. */
    private const val FIFTY = 50
}
