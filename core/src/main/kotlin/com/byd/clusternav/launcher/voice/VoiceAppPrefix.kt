package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ R7 (2.76) · TÊN APP RỤNG CÒN MỘT **TIỀN TỐ** — *"viet vào ô số hai"* ⇒ VietMap ô 2 ═══════════════════════
 *
 * Spec `docs/specs/kachi-276-closing.html` R7. Thuần Kotlin (`:core`) ⇒ kiểm off-car. Chạy ở nhánh **áp chót**
 * của [VoiceLastResort] — sau mọi phép khớp chính xác và sau khớp mờ [VoiceNameFuzzy], trước tên hồ sơ.
 *
 * ## Bệnh nó chữa — họ D của buổi xe 27/09 ([ĐO xe 10:30:24 · 10:35:35 · 10:35:46 · 10:36:36])
 * Mô hình in ra *"**viet** vào ô số hai"* · *"có **yout** vào ô số một"*: tên app rụng còn 4 ký tự. [ĐO máy ảo,
 * `voice-car-0927.md` §3] cùng cơ chế với im lặng dẫn đầu (pre-roll đã vá ở 2.75), nhưng khi nó vẫn xảy ra thì
 * [VoiceNameFuzzy.nearly] **cố ý** trượt (đòi cả hai chuỗi ≥ 5 ký tự) ⇒ `Unknown` ⇒ hỏi lại. 2.75 giữ thế có chủ ý
 * (*"nới cổng là mở cửa mở NHẦM app"*); 2.76 nới đúng **một** khe, có ba cổng, owner duyệt (R7).
 *
 * ## Ba cổng — cùng tinh thần ba cổng của [VoiceSlotNoVerb]
 *  1. **Câu phải có mệnh đề ô CÓ SỐ** ngay sau phần tên ([VoiceTailClause.slotAt] trên đuôi bắt đầu bằng một từ
 *     của [VoiceLexicon.SLOT_WORDS]). *"mở viet"* trần ⇒ `null`: một tiền tố đứng một mình là quá ít để mở app; còn
 *     mệnh đề ô nói rõ *"đây là một lệnh gắn app vào ô"*, tức cái tên là thứ **duy nhất** còn thiếu.
 *  2. **Tiền tố ≥ [MIN_PREFIX] ký tự** (ghép liền, đã bỏ dấu). *"mát"* (3) · *"áp"* (2) ⇒ `null` — giữ nguyên
 *     `Unknown` như 2.75 (bài `ten app rung thi KHONG duoc doan`), vì hai âm tiết ấy không còn liên hệ gì tới một
 *     cái tên.
 *  3. **Duy nhất theo NHÃN.** Mọi nhãn app đã cài + mọi cách gọi của bảng đích ([VoiceLastResort.candidates]) bắt đầu
 *     bằng tiền tố ấy phải cùng trỏ về **một** app — trừ một ca có lý: các nhãn khớp **lồng nhau theo từ** (*"YouTube"*
 *     ⊂ *"YouTube Music"*). Ở đó người nói đã rụng phần đuôi của chính từ *"youtube"*; nếu họ định nói *YouTube
 *     Music* thì còn cả một từ *"music"* nữa phải rụng. Nhãn **ngắn nhất là tiền tố theo từ của mọi nhãn còn lại**
 *     ⇒ chọn nó; hai nhãn không lồng nhau (*"Netflix"* / *"Netflax"* cho *"netf"*) ⇒ `null` ⇒ hỏi lại.
 *
 * Không có nhánh theo tên gói hay tên app nào ở đây (CLAUDE.md §7): mọi thứ đo trên **dữ liệu** của phiên.
 */
internal object VoiceAppPrefix {

    /** Tiền tố ngắn hơn ngần này ký tự thì không đủ làm bằng chứng — xem cổng 2. */
    const val MIN_PREFIX = 4

    /**
     * Ý định mở app từ *"&lt;tiền tố&gt; vào ô số N"*, hoặc `null` khi một trong ba cổng đóng.
     *
     * @param rest phần sau động từ (cùng dải mà [VoiceLastResort] cầm).
     * @param terms từ vựng của phiên — nguồn nhãn app đã cài.
     */
    fun pick(verb: VoiceVerb, rest: List<Token>, terms: List<VoiceTerm>): VoiceIntent.OpenApp? {
        if (!VoiceGrammar.isAction(verb) || VoiceTailClause.closesApp(verb)) return null
        val headLen = nameWords(rest)
        // Cổng 1 — phải còn một mệnh đề ô CÓ SỐ đứng ngay sau phần tên.
        if (headLen == 0 || headLen >= rest.size) return null
        val tail = rest.subList(headLen, rest.size)
        val slot = VoiceTailClause.slotAt(tail) ?: return null
        // Cổng 2 — tiền tố đủ dài.
        val said = rest.take(headLen).joinToString("") { it.norm }
        if (said.length < MIN_PREFIX) return null
        // Cổng 3 — duy nhất theo nhãn (hoặc lồng nhau theo từ).
        // 2.93 VOICE-TAUGHT-ACCENT-FUZZY — tên GIỌNG một âm tiết giữ luật dấu ở cả đường tiền tố (KDoc [VoiceLastResort.candidates]).
        val matched = VoiceLastResort.candidates(terms, rest.first())
            .filter { (_, words) -> words.joinToString("").startsWith(said) }
        val label = unique(matched) ?: return null
        // Cùng nhãn có thể xuất hiện hai lần (nhãn máy `key = null` + dòng bảng đích có mã): giữ bản CÓ mã để chỗ
        // thi hành còn tra được gói qua bảng đích khi nhãn máy không trùng (`VoiceDispatcher.runOpenApp`).
        val hit = matched.firstOrNull { it.first == label && it.first.key != null }?.first ?: label
        return VoiceIntent.OpenApp(hit.label, slot, appKey = hit.key)
    }

    /**
     * Ứng viên duy nhất — theo danh tính nhãn ([VoiceLastResort.Named]) — hoặc nhãn **ngắn nhất** khi mọi nhãn khớp
     * lồng nhau theo từ; `null` khi rỗng hay nhập nhằng thật.
     */
    private fun unique(matched: List<Pair<VoiceLastResort.Named, List<String>>>): VoiceLastResort.Named? {
        if (matched.isEmpty()) return null
        // Gom theo danh tính; giữ dãy từ NGẮN nhất của mỗi nhãn (một app có nhiều cách gọi).
        val byLabel = LinkedHashMap<VoiceLastResort.Named, List<String>>()
        matched.forEach { (named, words) ->
            val cur = byLabel[named]
            if (cur == null || words.size < cur.size) byLabel[named] = words
        }
        if (byLabel.size == 1) return byLabel.keys.first()
        val shortest = byLabel.minByOrNull { it.value.size } ?: return null
        val base = shortest.value
        val nested = byLabel.all { (_, words) -> words.size >= base.size && words.take(base.size) == base }
        return if (nested) shortest.key else null
    }

    /** Số từ ở đầu [rest] trước mệnh đề ô — cùng phép cắt mà [VoiceSlotNoVerb]/[VoiceLastResort] dùng. */
    private fun nameWords(rest: List<Token>): Int {
        var n = 0
        while (n < rest.size && rest[n].norm !in VoiceLexicon.SLOT_WORDS) n++
        return n
    }
}
