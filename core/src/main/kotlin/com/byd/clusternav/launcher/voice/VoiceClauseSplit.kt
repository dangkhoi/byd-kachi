package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ 2.98 R11 `VOICE-COMPOUND-NO-CONNECTOR` — ranh giới vế của câu ghép KHÔNG liên từ ═══════════════════════════════════════
 *
 * Thuần Kotlin (`:core`) ⇒ kiểm off-car. Spec `docs/specs/kachi-298-plan.html` R11.
 *
 * [ĐO off-car 09/10] hai câu owner mất vế sau im lặng:
 *  • *"tắt sưởi ghế phụ tắt gió tự động"* ⇒ chỉ `seath_r` tắt. Gốc: phép tách MIX ([VoiceControlParse.multiVerbSplit]) so ranh giới
 *    trên chữ BỎ DẤU ⇒ *"động"* (trong *"tự động"*) = `dong` = *"đóng"* ⇒ thêm một vế *"động"* không hiểu được ⇒ cổng *"mọi vế phải
 *    hiểu được"* bỏ cả phép tách ⇒ đọc nguyên câu như MỘT lệnh.
 *  • *"sưởi ghế phụ bật gió tự động"* ⇒ `seath_r=1`, vế *"bật gió tự động"* mất. Gốc: vế đầu không có động từ (tên nút tự mang
 *    động từ ngầm — cùng luật câu đơn *"sưởi ghế phụ"* ⇒ bật) nên phép tách bỏ rơi nó, chỉ còn một vế ⇒ không tách.
 *
 * Hai luật, đều đọc từ VỰNG đang dùng (động từ của [VoiceGrammar.VERBS] + cụm của [VoiceGrammar.terms]), không câu mẫu nào:
 *  1. [starts] — ranh giới là động từ HÀNH ĐỘNG THẬT theo dấu ([VoiceVerbSpelling.actionSpan], cùng luật mạch mượn động từ) VÀ không
 *     nằm trong lòng một cụm đã khớp từ trước (chữ không dấu *"tu dong"* vẫn đúng nhờ vế thứ hai này).
 *  2. [parts] — vế đầu không động từ chỉ được nhận khi: mở bằng một cụm ([leadsWithTerm]), tự nó là lệnh GHI (`Control`/`Macro`), và
 *     MỌI vế sau tự nêu đối tượng (*"điều hòa tắt đi"* — vế sau không đối tượng ⇒ không tách, như cũ).
 *
 * Không tách trước một cụm DANH TỪ đứng giữa câu (*"tắt điều hòa đèn đọc"*): ở đó người nói thường muốn mượn động từ (cùng nghĩa
 * *"tắt điều hòa và đèn đọc"*, [VoiceClauseEllipsis]) — đoán *"bật đèn đọc"* là đoán ngược. Giữ một lệnh như cũ.
 */
internal object VoiceClauseSplit {

    /** Chỉ số các token mở đầu một vế có động từ — xem luật 1 ở KDoc lớp. [terms] rỗng ⇒ chỉ luật dấu. */
    fun starts(t: List<Token>, terms: List<VoiceTerm>): List<Int> {
        val out = ArrayList<Int>()
        val inside = insideTerm(t, terms)
        var verbEnd = -1   // hết cụm động từ của ranh giới trước: động từ đứng SÁT sau (*"tắt lấy gió trong"*) là cùng một vế
        for (i in t.indices) {
            if (inside[i]) continue
            val span = VoiceVerbSpelling.actionSpan(t.subList(i, t.size))
            if (span == 0) continue
            if (i > verbEnd) out.add(i)
            verbEnd = maxOf(verbEnd, i + span)
        }
        return out
    }

    /**
     * `inside[i]` = vị trí `i` nằm trong lòng (không phải đầu) một cụm của từ vựng khớp ở vị trí trước nó. Senior review Pass 10
     * [P3]: quét cụm MỘT lượt cho cả câu (n × cụm) thay vì mỗi vị trí quét lại mọi vị trí trước (n² × cụm) — cùng kết quả.
     */
    private fun insideTerm(t: List<Token>, terms: List<VoiceTerm>): BooleanArray {
        val inside = BooleanArray(t.size)
        if (terms.isEmpty()) return inside
        for (j in t.indices) {
            val longest = VoiceGrammar.matchAt(t, j, terms).maxOfOrNull { it.words.size } ?: continue
            for (k in j + 1 until minOf(t.size, j + longest)) inside[k] = true
        }
        return inside
    }

    /** Câu mở bằng một cụm của từ vựng (không phải động từ, không phải từ đệm lạ) — điều kiện CẦN để giữ vế đầu không động từ. */
    fun leadsWithTerm(t: List<Token>, terms: List<VoiceTerm>): Boolean = VoiceGrammar.matchAt(t, 0, terms).isNotEmpty()

    /**
     * Các vế của câu MIX không liên từ đã qua cổng nghĩa, hoặc `null` (giữ nguyên câu). [seg] = phân tích MỘT vế (cùng hàm của
     * [VoiceIntentParser.parse]).
     */
    fun parts(t: List<Token>, terms: List<VoiceTerm>, seg: (List<Token>) -> VoiceIntent): List<List<Token>>? {
        val ps = VoiceControlParse.multiVerbSplit(t, terms) ?: return null
        val each = ps.map(seg)
        if (each.any { it is VoiceIntent.Unknown }) return null
        if (VoiceVerbSpelling.actionSpan(ps[0]) == 0) {
            if (each[0] !is VoiceIntent.Control && each[0] !is VoiceIntent.Macro) return null
            if (ps.drop(1).any { !namesObject(it, terms) }) return null
        }
        return ps
    }

    /** Vế (mở bằng động từ) có nêu một cụm đối tượng sau động từ không. */
    private fun namesObject(p: List<Token>, terms: List<VoiceTerm>): Boolean {
        val from = VoiceVerbSpelling.actionSpan(p).coerceAtLeast(1)
        return (from until p.size).any { VoiceGrammar.matchAt(p, it, terms).isNotEmpty() }
    }
}
