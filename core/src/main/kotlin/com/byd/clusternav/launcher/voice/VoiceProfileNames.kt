package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ TÊN HỒ SƠ — BIAS ĐƯỢC, KHỚP MỜ ĐƯỢC, VÀ THIẾU TÊN THÌ HỎI LẠI ══════════════════════════════════════════
 *
 * VOICE-PROFILE-NAME-PHONETIC. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Bệnh nó chữa — [ĐO xe 2026-09-26] + [ĐO máy ảo 2026-09-26, bản thu thật]
 * Hồ sơ tên tiếng Anh *"Test"*: **8/8 lượt** mô hình in ra *"chuyển sang hồ sơ"* / *"đổi sang hồ sơ"* — **không có
 * cái tên**. Phát lại hai bản thu `…-183215` · `…-183304` off-car cho ra đúng hai chuỗi ấy ⇒ ca [ĐO]. Và câu
 * *"chuyển sang hồ sơ định"* (nuốt chữ *"Mặc"*) ra `MISMATCH`: nhãn datum *"Số"* khớp giữa câu, còn *"định"* thì
 * không khớp hồ sơ nào.
 *
 * Ba chỗ hụt, ba việc trong tệp này:
 *  1. **Không bias.** [SherpaBiasing] cố ý bỏ tên hồ sơ (*"mô hình VN không phát ra token tiếng Anh"*) — đúng cho
 *     tên tiếng Anh, **sai** cho tên tiếng Việt: *"Mặc định"* là hai từ tiếng Việt thường, đúng thứ biasing kéo về
 *     được, mà nó vẫn rụng chữ đầu. ⇒ [phrases] sinh cụm *"HỒ SƠ MẶC ĐỊNH"* · *"CHUYỂN SANG HỒ SƠ MẶC ĐỊNH"*.
 *     Tên tiếng Anh thì đi qua [VoiceAppPhonetics.spokenForms] để có **dạng đọc tiếng Việt** (*"Test"* ⇒ *"tét"*) —
 *     cùng phép mà tên app đã dùng từ H3, không phải một bảng thứ hai.
 *  2. **Không khớp mờ.** [pick] nhận cái tên đã bị rụng một từ / lệch vài ký tự, qua [VoiceNameFuzzy].
 *  3. **Thiếu tên thì im lặng.** [missingName] cho [VoiceClarify] hỏi lại *"Hồ sơ nào?"* thay vì trả một câu
 *     *"việc đó không đi với thứ đó"* mà người lái không biết phải làm gì tiếp.
 *
 * ## Vì sao khớp mờ ở đây AN TOÀN hơn hẳn khớp mờ tên app
 * Ba cổng cộng dồn: (a) câu phải có **cụm đánh dấu** *"hồ sơ"* ([MARKERS]) — người nói đã tự nói ra rằng họ đang
 * gọi tên một hồ sơ; (b) chỉ xét phần **sau** cụm đánh dấu; (c) nhập nhằng ⇒ **không chọn**
 * ([VoiceNameFuzzy.pickUnique]) ⇒ rơi về câu hỏi lại. Đó cũng là lý do KDoc [VoicePhoneticMatch] cấm đem tên hồ sơ
 * vào tầng chữa chính tả **chung**: ở đó không có cụm đánh dấu nào chứng minh người ta đang gọi hồ sơ.
 */
internal object VoiceProfileNames {

    /**
     * Cụm đánh dấu *"…hồ sơ &lt;tên&gt;"* — cửa vào duy nhất của mọi phép nới trong tệp này.
     *
     * Khai **không dấu** (hợp đồng của [VoiceLexicon.Token.norm]). `profile` cho câu tiếng Anh.
     */
    val MARKERS: List<List<String>> = listOf(listOf("ho", "so"), listOf("profile"))

    /**
     * Ý định đổi hồ sơ khi câu có cụm đánh dấu + một cái tên **gần** một hồ sơ có thật, hoặc `null`.
     *
     * @param rest phần câu **sau động từ** (đã bỏ tiếng đệm) — đúng thứ [VoiceIntentParser] đang cầm ở nhánh cuối.
     * @param terms từ vựng đang dùng; chỉ đọc cụm [VoiceTermKind.PROFILE] (tên hồ sơ của máy này).
     */
    fun pick(rest: List<Token>, terms: List<VoiceTerm>): VoiceIntent? {
        val said = afterMarker(rest) ?: return null
        if (said.isEmpty()) return null
        val cands = terms.filter { it.kind == VoiceTermKind.PROFILE }.map { it.id to it.words }
        if (cands.isEmpty()) return null
        // Tên khớp CHÍNH XÁC đã do vòng quét của bộ phân tích lo xong; tới đây chỉ còn ca lệch.
        val id = VoiceNameFuzzy.pickUnique(cands) { full -> VoiceNameFuzzy.matches(said, full) } ?: return null
        return VoiceIntent.Profile(id)
    }

    /**
     * Danh sách hồ sơ để hỏi lại, khi câu **nêu cụm đánh dấu mà không có tên nào** (ca 8/8 lượt của xe).
     *
     * @return tên hồ sơ theo đúng thứ tự của [terms], hoặc `null` khi không phải ca ấy (không có cụm đánh dấu ·
     *   có tên khớp · máy chỉ có một hồ sơ nên không có gì để hỏi).
     */
    fun missingName(tokens: List<Token>, terms: List<VoiceTerm>): List<String>? {
        val said = afterMarker(tokens) ?: return null
        val profiles = terms.filter { it.kind == VoiceTermKind.PROFILE }
        if (profiles.size < 2) return null
        // Có cụm nào khớp (chính xác hay mờ) ⇒ đây không phải ca "thiếu tên".
        if (said.isNotEmpty() && profiles.any { VoiceNameFuzzy.matches(said, it.words) }) return null
        if (said.any { w -> profiles.any { w in it.words } }) return null
        return profiles.map { it.id }
    }

    /**
     * Cụm hotword **có dấu** cho tên hồ sơ — nguồn cho [SherpaPhraseHotwords.phrases].
     *
     * Mỗi tên sinh: `HỒ SƠ <tên>` + `<động từ đổi> HỒ SƠ <tên>`. Cụm mang theo hai chữ *"hồ sơ"* có chủ ý — đó là
     * phần mà mô hình **nghe đúng** ở cả 8 lượt, nên nó là cái mỏ neo kéo theo cái tên đứng sau. Tên rỗng / chỉ
     * gồm ký hiệu ⇒ bỏ (tầng lọc [SherpaHotwords.normalize] cũng bỏ, nhưng không dựa vào tầng khác để đúng).
     *
     * Tên **không đọc được bằng âm Việt** (không phủ hết [VoiceAppPhonetics.SYLLABLES]) thì vẫn sinh cụm với chính
     * cái tên: mô hình có thể không phát ra được token ấy (dòng vô hại, xem KDoc [SherpaBiasing]), nhưng hai chữ
     * *"HỒ SƠ"* trong cùng dòng thì vẫn là một cụm đúng — và ngày ai đó thêm âm tiết còn thiếu vào bảng thì dòng
     * dạng đọc tự xuất hiện, không phải sửa ở đây.
     */
    fun phrases(profiles: List<String>): List<String> {
        if (profiles.isEmpty()) return emptyList()
        val out = ArrayList<String>(profiles.size * (1 + SWITCH_FORMS.size) * 2)
        profiles.forEach { name ->
            val forms = LinkedHashSet<String>()
            forms.add(name)
            VoiceAppPhonetics.spokenForms(name).forEach { forms.add(it) }
            forms.forEach { f ->
                out.add("$HEAD $f")
                SWITCH_FORMS.forEach { v -> out.add("$v $HEAD $f") }
            }
        }
        return out
    }

    /** Phần câu **sau** cụm đánh dấu, hoặc `null` khi câu không có cụm đánh dấu nào. */
    private fun afterMarker(t: List<Token>): List<String>? {
        MARKERS.forEach { m ->
            t.indices.forEach { i ->
                if (VoiceLexicon.phraseAt(t, i, m)) {
                    return t.drop(i + m.size).map { it.norm }.filterNot { it in VoiceLexicon.FILLERS }
                }
            }
        }
        return null
    }

    /** Dạng **có dấu** của cụm đánh dấu — một chỗ, dùng cho mọi cụm hotword. */
    private const val HEAD = "hồ sơ"

    /**
     * Dạng có dấu của động từ ĐỔI, lấy từ [SherpaSpokenWords.VERBS] — không chép tay một bản thứ hai.
     *
     * Chỉ lấy dạng **một từ** thì mất *"chuyển sang"*; lấy hết thì có cả *"đổi"* và *"đổi sang"*, và cụm ngắn hơn
     * sẽ là **tiền tố theo từ** của cụm dài hơn nên [SherpaHotwords.dropPrefixes] tự bỏ nó — đúng luật của tệp
     * hotword, không cần lọc thêm ở đây.
     */
    private val SWITCH_FORMS: List<String> = SherpaSpokenWords.VERBS[VoiceVerb.SWITCH].orEmpty()
}
