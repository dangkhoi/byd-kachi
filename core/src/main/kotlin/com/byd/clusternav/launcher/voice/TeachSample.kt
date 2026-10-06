package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ 2.91 VOICE-APP-NAMES · C2 — MỘT LƯỢT NÓI DẠY ⇒ PHẦN TÊN ═══════════════════════════════════════════════════
 *
 * Spec §4.2 *"Chuẩn hoá một mẫu"*. Người dùng được hướng dẫn nói **cả câu lệnh** (*"mở Netflix"*) — cùng ngữ cảnh âm +
 * cùng cụm hotword *"MỞ …"* như lúc dùng thật. Sau khi nghe, cắt về phần TÊN bằng **đúng các bảng parser đang dùng**
 * (không bảng thứ hai): lịch sự/tiếng đệm ([VoiceLexicon.stripCourtesy] · [VoiceLexicon.dropLeadingFillers]) → động từ
 * MỞ/BẬT ở vị trí 0 ([VoiceGrammar.VERBS], dài trước ngắn) → nhãn launcher *"ứng dụng"* ngay sau → mệnh đề ô
 * ([VoiceLexicon.SLOT_WORDS] + [VoiceTailClause.slotAt]) → kiểm hình dạng.
 *
 * Kết quả mang hai dạng: [Sample.accented] = nối `raw` (chữ thường có dấu — đúng thứ mô hình in) và [Sample.norm] =
 * nối `norm` (để khớp). Hàm thuần, không ném.
 */
object TeachSample {

    /** Trần số từ của một tên (spec §4.2 bước 5) — dài hơn là một câu, không phải một cái tên. */
    const val MAX_WORDS = 4

    /**
     * Sàn TUYỆT ĐỐI số chữ cái (bỏ dấu, bỏ dấu cách) — ≤ 2 chữ cái là tiếng đệm/âm rời, luôn loại.
     *
     * 2.91 quyết định điều phối (spec §7 OQ4 · §9 F1/R1): [ĐO máy ảo TTS 06/10] nhãn Anh ngắn đọc trần ra MỘT âm tiết
     * 3 chữ cái (*"Chrome"* ⇒ «cơm», *"Gmail"* ⇒ «ghe», *"Drive"* ⇒ «nay») — sàn 4 cũ làm đúng những app owner muốn dạy
     * thành KHÔNG dạy được. Sàn hạ về 3; tên 3 chữ cái nguồn GIỌNG chỉ được lưu khi mô hình in ra ĐÚNG chuỗi ấy ở
     * ≥ 2 lượt ([TeachGuard.MIN_TAKES_SHORT]) VÀ qua mọi cổng va chạm. Một lượt nói ⇒ vẫn cần ≥ [SOLO_MIN_LETTERS].
     */
    const val MIN_LETTERS = 3

    /** Sàn của một tên GIỌNG chỉ nghe MỘT lần — cùng bậc sàn tiền tố [VoiceAppPrefix] (4). Tên gõ dùng [MIN_LETTERS]. */
    const val SOLO_MIN_LETTERS = 4

    /**
     * Trần độ dài chuỗi (ký tự, có dấu cách) — 4 âm tiết tiếng Việt dài nhất ~31 ký tự. Chặn chuỗi rác/khổng lồ đi vào
     * qua ô gõ, tệp hồ sơ nhập vào, hoặc intent `TEACH_APP` (Activity HOME nhận intent của mọi app) — R7 · R-nf5.
     */
    const val MAX_CHARS = 48

    enum class Reject { EMPTY, TOO_SHORT, TOO_LONG }

    sealed interface Result

    /** @property letters số chữ cái đã bỏ dấu (không tính dấu cách) — để cổng áp sàn một-lượt / nhiều-lượt. */
    data class Sample(val accented: String, val norm: String, val words: List<String>, val letters: Int) : Result {
        /** Dưới sàn một-lượt ([SOLO_MIN_LETTERS]): tên giọng cần ≥ 2 lượt giống hệt (spec §7 OQ4). */
        val short: Boolean get() = letters < SOLO_MIN_LETTERS
    }

    data class Rejected(val why: Reject) : Result

    /** Câu hộp dạy gợi ý cho một lượt nói: *"mở &lt;tên&gt;"* ([PLAIN]) hay *"đưa &lt;tên&gt; vào ô số hai"* ([SLOT]). */
    enum class Prompt { PLAIN, SLOT }

    /**
     * 2.93 VOICE-TEACH-CONTEXT — lượt nói thứ mấy (1-based) của MỘT lần dạy là câu CÓ Ô.
     *
     * [ĐO máy ảo 06/10, spec voice-app-names §9 F3] câu KHÁC đổi chuỗi tên: *"đưa &lt;tên&gt; vào ô số hai"* ra `google đy` /
     * `cờ rôm` trong khi tên dạy bằng *"mở &lt;tên&gt;"* là `google đ` / `cửa rôm` ⇒ câu có ô không khớp (khớp mờ không cứu —
     * neo 4 ký tự lệch). Lượt thứ hai — trong [MIN_SPOKEN_TAKES] lượt bắt buộc, để không ai lưu mà bỏ sót nó — nói câu có ô:
     * chuỗi KHÁC ⇒ một tên nữa (vẫn trong trần 3 tên giọng của [TaughtNames]); chuỗi GIỐNG ⇒ gộp, thành lượt thứ hai của
     * tên ấy (đủ điều kiện tên ngắn, [TeachGuard.MIN_TAKES_SHORT]). Cách cắt KHÔNG đổi: [normalize] vốn bỏ động từ MỞ (gồm
     * *"đưa"*) + mệnh đề ô.
     */
    const val SLOT_TAKE = 2

    /** Số lượt nói tối thiểu mỗi lần dạy (spec §7 OQ4) — hộp dạy bật Lưu từ lượt này; [SLOT_TAKE] phải nằm trong đó. */
    const val MIN_SPOKEN_TAKES = 2

    /** Câu gợi ý cho lượt nói thứ [take] (1-based). */
    fun promptFor(take: Int): Prompt = if (take == SLOT_TAKE) Prompt.SLOT else Prompt.PLAIN

    fun normalize(heard: String): Result {
        val all = VoiceLexicon.tokenize(heard)
        if (all.isEmpty() || VoiceLexicon.isFillerOnly(heard)) return Rejected(Reject.EMPTY)
        var t = VoiceLexicon.dropLeadingFillers(VoiceLexicon.stripCourtesy(all).ifEmpty { all })
        t = dropOpenVerb(t)
        t = dropLauncherAppsLabel(t)
        t = cutSlotClause(t)
        t = VoiceLexicon.dropLeadingFillers(VoiceLexicon.stripCourtesy(t).ifEmpty { t })
        return shape(t)
    }

    /** Hình dạng của một chuỗi ĐÃ là phần tên (tên gõ tay đi đây — không cắt động từ: người ta gõ đúng cái tên). */
    fun shape(raw: String): Result = shape(VoiceLexicon.tokenize(raw))

    private fun shape(t: List<Token>): Result {
        if (t.isEmpty() || t.all { it.norm in VoiceLexicon.FILLERS }) return Rejected(Reject.EMPTY)
        if (t.size > MAX_WORDS) return Rejected(Reject.TOO_LONG)
        val accented = TaughtNamesCodec.cleanAccented(t.joinToString(" ") { it.raw })
        if (accented.length > MAX_CHARS) return Rejected(Reject.TOO_LONG)
        val letters = t.sumOf { tok -> tok.norm.count { it.isLetter() } }
        if (letters < MIN_LETTERS) return Rejected(Reject.TOO_SHORT)
        return Sample(accented = accented, norm = t.joinToString(" ") { it.norm }, words = t.map { it.norm }, letters = letters)
    }

    /**
     * Động từ MỞ/BẬT tại vị trí 0 — dài trước ngắn (bảng [VoiceGrammar.VERBS] đã sắp như vậy) — hoặc vị trí 1 khi từ
     * đứng trước là MỘT tiếng ậm ừ ngắn (≤ [INTERJECTION_LEN] chữ: *"ờ mở …"*, *"à mở …"*) mà [VoiceLexicon.FILLERS]
     * không khai (bảng ấy cố ý hẹp vì nó áp cho MỌI câu lệnh; ở đây chỉ áp cho lượt dạy). Sau động từ bỏ lịch sự lần nữa.
     */
    private fun dropOpenVerb(t: List<Token>): List<Token> {
        for (at in 0..1) {
            if (at == 1 && (t.isEmpty() || t[0].norm.length > INTERJECTION_LEN)) break
            val hit = VoiceGrammar.VERBS.firstOrNull { (w, v) ->
                (v == VoiceVerb.OPEN || v == VoiceVerb.ON) && VoiceLexicon.phraseAt(t, at, w)
            } ?: continue
            val rest = t.subList(at + hit.first.size, t.size)
            return VoiceLexicon.dropLeadingFillers(VoiceLexicon.stripCourtesy(rest).ifEmpty { rest })
        }
        return t
    }

    private const val INTERJECTION_LEN = 2

    /** *"mở ỨNG DỤNG nep leag"* — nhãn của hành động launcher mở ngăn kéo, đứng ngay sau động từ. */
    private fun dropLauncherAppsLabel(t: List<Token>): List<Token> {
        val hit = VoiceGrammar.matchAt(t, 0, VoiceGrammar.terms())
            .filter { it.kind == VoiceTermKind.LAUNCHER && it.id == LauncherActions.APPS }
            .maxByOrNull { it.words.size } ?: return t
        // Còn gì sau nhãn ⇒ đó là tên; không còn gì ⇒ chính "ứng dụng" là thứ người ta nói (giữ để cổng chặn).
        return if (t.size > hit.words.size) t.subList(hit.words.size, t.size) else t
    }

    /** Cắt từ đầu mệnh đề ô có số trở đi (*"nep leag vào ô số hai"* ⇒ *"nep leag"*). */
    private fun cutSlotClause(t: List<Token>): List<Token> {
        for (i in 1 until t.size) {
            if (t[i].norm in VoiceLexicon.SLOT_WORDS && VoiceTailClause.slotAt(t.subList(i, t.size)) != null) {
                return t.subList(0, i)
            }
        }
        return t
    }
}
