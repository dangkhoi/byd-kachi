package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ NHÁNH CUỐI CỦA BỘ PHÂN TÍCH — chạy khi **không cách hiểu nào CÓ NGHĨA** ═════════════════════════════════
 *
 * Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Vì sao là một tệp riêng, không phải ba dòng thêm vào [VoiceIntentParser]
 * `VoiceIntentParser.kt` đang **499 dòng**, trần là 500 (CLAUDE.md §4.1, canh bằng
 * `VoiceListenWiringContractTest.pha NGHE khong day tep nao qua tran 500 dong`). Thêm tính năng ⇒ thêm **tệp**,
 * không nén dòng. Nhánh cuối cũng là một vai rõ: *"mọi đường chính xác đã trượt, còn cách nào đọc lại câu này"*.
 *
 * ## Thứ tự BA đường, và vì sao thứ tự là hợp đồng
 *  1. **Cách gọi app bằng tiếng Việt** ([VoiceTailClause.spokenApp]) — đường có từ V1.1, khớp **chính xác** một
 *     cách nói đã khai. Nó đứng đầu vì nó không đoán gì.
 *  2. **Tên app bị ASR bóp méo** ([appFuzzy]) — mới 2026-09-26, có đoán, nên đứng **sau** mọi phép khớp chính xác.
 *  3. **Tên hồ sơ bị ASR bóp méo** ([VoiceProfileNames.pick]) — cũng có đoán, nhưng phải có cụm đánh dấu
 *     *"hồ sơ"* mới chạy, nên nó không tranh chấp với (2).
 *
 * `null` ⇒ chỗ gọi giữ nguyên hành vi cũ (`firstMiss` rồi `noObject`), **không đổi một câu nào đang chạy**.
 */
internal object VoiceLastResort {

    /**
     * Cách hiểu cuối cùng cho [rest] (phần sau động từ [verb]), hoặc `null` khi không dám đọc lại.
     *
     * @param original câu nguyên văn — chỉ để mang vào [VoiceIntent.Unknown] của đường (1).
     */
    fun pick(verb: VoiceVerb, rest: List<Token>, terms: List<VoiceTerm>, original: String): VoiceIntent? =
        VoiceTailClause.spokenApp(verb, rest, original)
            ?: appFuzzy(verb, rest, terms)
            ?: VoiceProfileNames.pick(rest, terms)

    /**
     * ═══ TÊN APP **BỊ ASR BÓP MÉO**, đứng ngay sau động từ — *"mở youtubex vào ô hai"* ⇒ YouTube ô 2 ═══════════
     *
     * ## Bệnh nó chữa — [ĐO xe 2026-09-26, phát lại off-car cùng mô hình]
     * | mô hình in ra | trước | sau |
     * |---|---|---|
     * | *"mở **youtubex** vào ô hai"* | `NO_OBJECT` (*"không tìm thấy thứ đó"*) | `OpenApp(YouTube → ô 2)` |
     * | *"đặt **vietp** vào ô số một"* | `MISMATCH` (nhãn datum *"Số"* khớp giữa câu) | `OpenApp(VietMap → ô 1)` |
     *
     * Tên app là danh từ riêng: mô hình VN không có token cho nó nên chỗ lệch luôn ở **đuôi** (thêm `x`, rụng
     * `ma`). [VoiceAppTargets.bySpokenFuzzy] đã chữa đúng hình dạng lệch này từ 2026-09-20 — nhưng **chỉ sau cụm
     * đánh dấu** *"bằng / trên / dùng"* (câu nhạc/dẫn đường). Câu *"mở &lt;app&gt; vào ô N"* không có cụm đánh dấu
     * nào, nên nó chưa bao giờ đi qua phép so ấy.
     *
     * ## Bốn cổng — mỗi cổng chặn một kiểu đoán sai
     *  1. **Chỉ vị trí 0** (ngay sau động từ), và chỉ **động từ hành động, không phải động từ đóng** — cùng ba
     *     cổng của [VoiceTailClause.appAtHead]. Quét mù mọi vị trí là mời mọi câu lạ mở app.
     *  2. **Dừng trước mệnh đề ô.** Dải từ đem đi so cắt tại từ đầu tiên thuộc [VoiceLexicon.SLOT_WORDS]: không có
     *     cổng này thì *"youtubex vào ô hai"* ghép liền thành một chuỗi dài và **mệnh đề ô bị nuốt vào tên app** —
     *     đúng cái lỗi ta đang chữa, chỉ ở tầng khác.
     *  3. **Ứng viên khớp phải DUY NHẤT** ([VoiceNameFuzzy.pickUnique]), tính theo **nhãn** (không theo nguồn):
     *     nhãn app đã cài *"YouTube"* và cách gọi `youtube` của bảng đích là **một** app, không phải hai. Hai app
     *     **khác nhãn** cùng khớp ⇒ trả `null` ⇒ câu giữ nguyên *"không hiểu"* ⇒ [VoiceClarify] hỏi lại.
     *  4. **Phép so là [VoiceNameFuzzy]**: neo 4 ký tự đầu · lệch ≤ 2 · cả hai chuỗi ≥ 5 ký tự. [ĐO off-car] nó tự
     *     loại *"youtubemusic"* cho *"youtubex"* (lệch 5) và loại mọi cụm ngắn (*"yt"*, *"waze"*).
     *
     * Dải từ quét từ **dài xuống ngắn** (luật *"dãy dài nhất thắng"* của [VoiceGrammar]) — nhờ vậy *"youtub newt"*
     * thử cả cụm hai từ (trượt) rồi tới *"youtub"* (khớp YouTube), và phần còn lại *"newt vào ô hai"* vẫn qua
     * [VoiceTailClause.slotAt] nên mệnh đề ô không mất.
     */
    private fun appFuzzy(verb: VoiceVerb, rest: List<Token>, terms: List<VoiceTerm>): VoiceIntent? {
        if (!VoiceGrammar.isAction(verb) || VoiceTailClause.closesApp(verb)) return null
        val headLen = headWords(rest)
        if (headLen == 0) return null
        val cands = candidates(terms)
        if (cands.isEmpty()) return null
        for (len in headLen downTo 1) {
            val said = rest.take(len).map { it.norm }
            val hit = VoiceNameFuzzy.pickUnique(cands) { full -> VoiceNameFuzzy.nearly(full.joinToString(""), said.joinToString("")) }
                ?: continue
            val tail = rest.subList(len, rest.size)
            return VoiceIntent.OpenApp(hit.label, VoiceTailClause.slotAt(tail), appKey = hit.key)
        }
        return null
    }

    /**
     * Số từ ở **đầu** [rest] còn có thể là một phần của cái tên — dừng trước mệnh đề ô (cổng 2 ở KDoc [appFuzzy]).
     *
     * Trần [MAX_NAME_WORDS]: một cái tên dài hơn thế thì đó là một câu, và *"dãy dài nhất thắng"* cũng không cứu
     * được (phép so ghép liền sẽ lệch quá xa).
     */
    private fun headWords(rest: List<Token>): Int {
        var n = 0
        while (n < rest.size && n < MAX_NAME_WORDS) {
            if (rest[n].norm in VoiceLexicon.SLOT_WORDS) break
            n++
        }
        return n
    }

    /**
     * Ứng viên: **nhãn app đã cài** (cụm [VoiceTermKind.APP] của từ vựng) + mọi **cách gọi** của bảng đích.
     *
     * Danh tính là [Named] mang nhãn + mã đích: `pickUnique` so theo `equals` của nó, nên hai dòng cùng nhãn (nhãn
     * thật và một cách gọi của cùng app) chỉ được tính là một — xem cổng 3 ở KDoc [appFuzzy]. Nhãn so **không phân
     * biệt hoa thường** để *"YouTube"* (nhãn máy) và `youtube` (bảng đích) không thành hai app.
     */
    private fun candidates(terms: List<VoiceTerm>): List<Pair<Named, List<String>>> {
        val out = ArrayList<Pair<Named, List<String>>>(64)
        terms.forEach { t -> if (t.kind == VoiceTermKind.APP) out.add(Named(t.id, null) to t.words) }
        VoiceAppTargets.ALL.forEach { target ->
            val named = Named(target.label, target.key)
            target.spoken.forEach { s ->
                val w = VoiceLexicon.tokenize(s).map { it.norm }
                if (w.isNotEmpty()) out.add(named to w)
            }
        }
        return out
    }

    /**
     * Một app ứng viên: [label] là chữ người dùng nghe Kachi đọc lại, [key] là mã đích (`null` = nhãn app đã cài,
     * không phải một trong bảy app đích).
     *
     * `equals` so nhãn **đã hạ chữ**, và **bỏ qua** [key]: nhãn *"YouTube"* của máy và mã `youtube` của bảng đích
     * cùng mở một app, nên chúng không được tính là nhập nhằng. Ưu tiên giữ [key] của bản có mã (chỗ gọi lấy ứng
     * viên đầu tiên khớp, và bảng đích nằm sau nhãn máy ⇒ [key] chỉ được dùng khi nhãn máy không khớp).
     */
    private data class Named(val label: String, val key: String?) {
        override fun equals(other: Any?): Boolean =
            other is Named && other.label.lowercase() == label.lowercase()

        override fun hashCode(): Int = label.lowercase().hashCode()
    }

    /** Tên app dài nhất mà một câu đáng đem đi so mờ — cùng bậc với [VoiceAppTargets.LONGEST_SPOKEN]. */
    private const val MAX_NAME_WORDS = 3
}
