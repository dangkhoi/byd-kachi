package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ 3.0 R1+R2 `NAV-MULTI-PROVIDER-ALL3` · `NAV-BY-PROVIDER-SPLIT` — *"… bằng A, B và C"* = MỘT điểm đến, NHIỀU app ═══════════
 *
 * Spec `docs/specs/kachi-300-plan.html` R1 · R2. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Bệnh — [ĐO xe 10/10, Seal fw 2606, Kachi 2.99] + [ĐO off-car, `VoiceIntentParser.parse` trước bản vá]
 * *"dẫn đường đến chợ Bến Thành bằng Google Maps, VietMap, và Waze"* ⇒ `[Nav(query = "chợ Bến Thành bằng Google Maps VietMap và Waze",
 * app = null)]`: mệnh đề chọn app chỉ biết MỘT app chạm cuối câu ([VoiceTailClause.appAfterMarker]) ⇒ danh sách app không khớp ⇒ cả
 * đuôi bị nuốt vào TÊN điểm đến, câu đi app mặc định với địa chỉ rác. Phép tách câu ghép ở *"và"* không cứu được: vế *"waze"* / *"vietmap"*
 * trần không hiểu được ⇒ cổng *"mọi vế phải hiểu được"* bỏ phép tách. Trên xe, app nhận được câu ấy (app mặc định) tra địa chỉ rác: VietMap
 * tra hỏng ⇒ nhánh `coords == null` của `VoiceTargetDispatch.runNav` mở app trơn — đúng dòng `am start -a MAIN -c LAUNCHER -n vn.vietmap.live/…`
 * của log 21:24:52 [SUY source, khớp log].
 *
 * ## Luật — đọc từ BẢNG ĐÍCH, không câu mẫu nào
 * Phần đuôi từ một cụm đánh dấu ([VoiceLexicon.BY_APP_MARKERS]) tới HẾT câu phải là một **danh sách ≥ 2 tên app dẫn đường** — mỗi tên khớp
 * đúng các tầng của [VoiceTailClause.appAfterMarker] (tên đã dạy · chính xác · rụng âm cuối · neo tiền tố), xen giữa chỉ có từ nối danh sách
 * [LIST_WORDS] (dấu phẩy đã bị bộ tách từ bỏ ⇒ hai tên đứng sát nhau cũng nhận). Khi đó câu được hiểu là câu MỘT app (tên đầu) rồi nhân ra
 * mỗi app một ý định cùng điểm đến — đúng đường đơn app đã chạy trên xe ([ĐO xe] *"… bằng vietmap"* một mình ⇒ VietMap dẫn), không đường
 * thi hành mới nào.
 *
 * Ba cổng giữ nó không đổi câu khác (CLAUDE.md §6):
 *  1. **phải chạm cuối câu và phủ trọn** — một chữ lạ trong đuôi (*"bằng xe máy và waze"*) ⇒ không phải danh sách ⇒ đường cũ;
 *  2. **≥ 2 tên** — câu một app đi y nguyên đường cũ;
 *  3. **câu đầu phải ra đúng [VoiceIntent.Nav]/[VoiceIntent.NavigateSaved] với app = tên đầu** — không thì đường cũ. Nhạc
 *     (*"phát … bằng spotify và youtube"*) không vào đây: một bài phát trên hai app cùng lúc là hai luồng tiếng.
 */
internal object VoiceNavProviders {

    /** Từ nối giữa các tên app trong danh sách. *"với"* cũng là cụm đánh dấu — ở giữa danh sách nó chỉ là từ nối. */
    private val LIST_WORDS = VoiceHomograph.Words("và", "hoặc", "với", "cùng", "and", "or", "with")

    /**
     * Danh sách ý định (mỗi app một), hoặc `null` = không phải câu nhiều app ⇒ chỗ gọi đi đường cũ.
     *
     * @param one phân tích MỘT vế (cùng hàm của [VoiceIntentParser.parse]) — dùng cho câu đầu *"… bằng &lt;tên đầu&gt;"*.
     */
    fun expand(t: List<Token>, terms: List<VoiceTerm>, one: (List<Token>) -> VoiceIntent): List<VoiceIntent>? {
        for (i in t.indices) {
            if (t[i].norm !in VoiceLexicon.BY_APP_MARKERS) continue
            val items = list(t, i + 1, terms) ?: continue
            val head = one(t.subList(0, items.first().second))
            val keys = items.map { it.first }.distinct()
            return when {
                head is VoiceIntent.Nav && head.app == keys.first() -> keys.map { head.copy(app = it) }
                head is VoiceIntent.NavigateSaved && head.app == keys.first() -> keys.map { head.copy(app = it) }
                else -> null
            }
        }
        return null
    }

    /** Các tên (mã đích + chỉ số HẾT tên) phủ trọn `t[from..]`, ≥ 2 tên — hoặc `null`. Quét tên DÀI trước (*"google maps"* > *"google"*). */
    private fun list(t: List<Token>, from: Int, terms: List<VoiceTerm>): List<Pair<String, Int>>? {
        val out = ArrayList<Pair<String, Int>>()
        var j = from
        while (j < t.size) {
            if (out.isNotEmpty() && LIST_WORDS.matches(t[j])) { j++; continue }
            val (key, end) = (minOf(t.size, j + VoiceAppTargets.LONGEST_SPOKEN) downTo j + 1).firstNotNullOfOrNull { e ->
                name(t.subList(j, e), terms)?.let { it to e }
            } ?: return null
            out.add(key to end)
            j = end
        }
        return out.takeIf { it.size >= 2 && !LIST_WORDS.matches(t[t.size - 1]) }
    }

    /**
     * Mã đích nếu TRỌN [words] là một tên app dẫn đường, hỏi đúng [VoiceTailClause.appAfterMarker] (cùng các tầng khớp, không chép luật).
     * Chỉ nhận khi mệnh đề bắt đầu ở cụm đánh dấu giả (vị trí 0): *"maps với vietmap"* có *"với"* là cụm đánh dấu THẬT bên trong ⇒ không
     * được tính là một tên.
     */
    private fun name(words: List<Token>, terms: List<VoiceTerm>): String? =
        VoiceTailClause.appAfterMarker(listOf(MARK) + words, VoiceAppKind.NAV, terms)?.takeIf { it.second == 0 }?.first

    /** Cụm đánh dấu giả để hỏi [VoiceTailClause.appAfterMarker] đúng một tên — cùng ba tầng khớp, không chép luật. */
    private val MARK = VoiceLexicon.tokenize("bằng").first()
}
