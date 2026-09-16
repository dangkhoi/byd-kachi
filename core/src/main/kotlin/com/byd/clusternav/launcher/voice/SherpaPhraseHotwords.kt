package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.TelemetryRegistry

/**
 * ═══ V2 pha NGHE · HOTWORD THEO **CỤM LỆNH** (động từ + đối tượng) — KHÔNG TỪ RỜI ═══════════════════════════
 *
 * Spec `docs/specs/kachi-voice-hotword-phrases.html`. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Vì sao cụm, không phải nhãn rời — ba ma trận đo trên host 2026-09-16 (cùng model, 25 WAV, score 3.0)
 * Bản 1.64 đổ **623 dòng** nhãn/động từ/danh từ rời vào tệp hotwords, và `xem pin` vẫn ra *"xem tin"*, `dừng nhạc`
 * ra *"rừng nhạc"* dù `PIN` · `XEM` · `DỪNG` · `NHẠC` **đều có trong tệp**. Kết luận đầu (09-15) là "tệp lớn làm
 * loãng" — [ĐO] 09-16 **bác**: tệp 144 dòng, 39 dòng vẫn sai y hệt; tệp **1440 dòng toàn cụm** lại đúng cả bốn câu
 * canh (21/25, tốt nhất). Biến số quyết định là **dòng một từ**: thêm 39 động từ rời vào tệp cụm 756 dòng kéo
 * 21/25 xuống **17/25** (bằng không hotword); thêm 8 danh từ rời (`NHẠC`…) làm `dừng nhạc` thua lại.
 *
 * Cơ chế (source sherpa-onnx v1.13.8 `csrc/context-graph.cc` `ForwardOneStep`): khớp trọn một hotword (`is_end`)
 * ⇒ đồ thị **quay về gốc**. Nên (a) `XEM` rời là tiền tố của `XEM PIN`: khớp xong `XEM` là về gốc, cụm dài không
 * bao giờ được cộng đủ; (b) `NHẠC` rời đứng cuối cộng +3 cho **cả đường sai** *"RỪNG NHẠC"*, biên lợi thế của
 * *"DỪNG NHẠC"* (+6) so với đường sai co còn 3 và thua âm học. Cụm ≥ 2 từ thì chỉ đường **đúng** mới ăn trọn điểm.
 *
 * ## Vì sao sinh từ registry, không chép tay
 * Cùng lẽ với [VoicePhrases]/[VoiceGrammar]: thêm một nút là tự có cụm, không ai phải nhớ. Động từ đi theo
 * **loại** nút ([ControlKind]) đúng như [VoiceIntentParser] chấp nhận — cụm nào parser không hiểu thì không bias.
 *
 * ## Vì sao KHÔNG thu nhỏ theo hồ sơ/ngữ cảnh
 * [ĐO] kích thước không phải biến số (trên), và `createStream(hotwords)` trên host tốn 1,5 ms với tệp 623 dòng,
 * **6,6 ms với đúng tệp đang ship** (1902 dòng — ma trận 5). [SUY] chậm ×20 trên đầu xe ⇒ ~130 ms mỗi phiên
 * nghe: vượt ngân sách 100 ms mà spec đặt ra, nên đây là con số **phải đo lại trên xe** (OQ2), không phải con
 * số đã đạt. Một tệp đủ cho mọi lệnh thì người lái nói được cả thứ **không** đang hiện trên màn.
 */
object SherpaPhraseHotwords {

    /**
     * Động từ được phép theo loại nút — **cùng bảng** với nhánh `control()` của [VoiceIntentParser]: TOGGLE/COVER
     * nhận bật/tắt/mở/đóng, STEP nhận tăng/giảm/đặt, BUTTON nhận bật/mở (mọi động từ đều là "bấm"), SELECT nhận
     * đổi/chuyển/đặt (+ tên lựa chọn).
     */
    val CONTROL_VERBS: Map<ControlKind, List<VoiceVerb>> = mapOf(
        ControlKind.TOGGLE to listOf(VoiceVerb.ON, VoiceVerb.OFF, VoiceVerb.OPEN, VoiceVerb.CLOSE),
        ControlKind.COVER to listOf(VoiceVerb.OPEN, VoiceVerb.CLOSE, VoiceVerb.ON, VoiceVerb.OFF),
        ControlKind.STEP to listOf(VoiceVerb.UP, VoiceVerb.DOWN, VoiceVerb.SET),
        ControlKind.BUTTON to listOf(VoiceVerb.ON, VoiceVerb.OPEN),
        ControlKind.SELECT to listOf(VoiceVerb.SWITCH, VoiceVerb.SET),
    )

    /**
     * Mọi cụm **có dấu** đáng bias, theo thứ tự ổn định (để `diff` hai lượt đo). Chưa lọc/chuẩn hoá — việc đó của
     * [SherpaHotwords.phraseFile], nơi dòng **một từ** ([SherpaHotwords.isPhrase]) và dòng là **tiền tố theo từ**
     * của dòng khác ([SherpaHotwords.dropPrefixes]) bị loại.
     *
     * ⚠ KHÔNG có luật *"loại dòng ASCII thuần"* (bản nháp của spec có, đã bị bác): *"XEM PIN"* — câu được đo
     * nhiều nhất — cũng là ASCII thuần. Nhãn tiếng Anh không lọt vào vì hàm này **không lấy** `labelEn`/
     * `shortEn`/`argsEn` ngay từ nguồn, không phải vì có ai đó đếm ký tự ASCII ở tầng dưới.
     *
     * @param places nhãn sổ địa chỉ của hồ sơ đang dùng — thành cụm *"về Nhà"* / *"đến Công ty"* qua
     *   [VoicePlaces.PLACE_VERBS]; nhãn trần (một từ) tự rụng ở tầng lọc.
     */
    fun phrases(places: List<String> = emptyList()): List<String> {
        val out = ArrayList<String>(2048)
        ControlRegistry.ALL.forEach { c ->
            val nouns = nounsOf(c.label, c.short, VoiceSynonyms.CONTROL[c.id])
            val verbs = CONTROL_VERBS[c.kind].orEmpty().flatMap { forms(it) }
            nouns.forEach { n ->
                // Nhãn nhiều từ đứng một mình cũng là cụm (*"kính trước trái"*); cách nói đã mang sẵn động từ
                // (*"mở khoá cửa"*, *"sấy kính trước"*) thì KHÔNG chồng thêm động từ (*"mở mở khoá cửa"* là rác).
                out.add(n)
                if (!startsWithVerb(n)) verbs.forEach { v -> out.add("$v $n") }
            }
            if (c.kind == ControlKind.SELECT) {
                // *"chế độ lái thể thao"* · *"đổi sang thể thao"* — tên lựa chọn đi cùng nhãn hoặc động từ đổi.
                c.args.forEach { a -> out.add("${c.label} $a"); forms(VoiceVerb.SWITCH).forEach { out.add("$it $a") } }
            }
        }
        val read = forms(VoiceVerb.READ)
        TelemetryRegistry.ALL.forEach { t ->
            nounsOf(t.label, t.short, VoiceSynonyms.TELEMETRY[t.id]).forEach { n ->
                out.add(n)   // *"pin còn bao nhiêu"* / *"áp suất lốp trước trái"* — nhãn nhiều từ tự đứng được
                if (!startsWithVerb(n)) read.forEach { out.add("$it $n") }
            }
        }
        // Gói lệnh: nhãn đã là một câu lệnh (*"Mở hết kính"*, *"Mở cửa + đèn đọc"* → hai cụm ở tầng chuẩn hoá).
        ActionMacros.ALL.forEach { out.add(it.label) }
        // Hành động launcher: *"mở ứng dụng"* · *"mở cài đặt"* — nhãn rời (*"Ứng dụng"*) là một từ, sẽ rụng.
        LauncherActions.ALL.forEach { a -> out.add(a.label); forms(VoiceVerb.OPEN).forEach { out.add("$it ${a.label}") } }
        // Nhạc: *"phát nhạc"* · *"dừng nhạc"* · *"bài tiếp theo"*; động từ NEXT/PREV đã là cụm, từ rời (*"tiếp"*) rụng.
        val media = accented(VoiceSynonyms.MEDIA_WORDS)
        (forms(VoiceVerb.PLAY) + forms(VoiceVerb.PAUSE)).forEach { v -> media.forEach { out.add("$v $it") } }
        out.addAll(forms(VoiceVerb.NEXT)); out.addAll(forms(VoiceVerb.PREV))
        // Dẫn đường: *"dẫn đường đến"* · *"chỉ đường tới"* — cụm động từ đứng trước tên nơi (tên nơi tự do, không bias).
        out.addAll(forms(VoiceVerb.NAV)); out.addAll(accented(VoiceSynonyms.NAV_WORDS))
        // Sổ địa chỉ: *"về Nhà"* · *"đến Công ty"* · *"đi làm"* (bí danh nhiều từ của [VoicePlaces.ALIASES]).
        VoicePlaces.PLACE_VERBS.forEach { v -> places.forEach { out.add("$v $it") } }
        out.addAll(VoicePlaces.spokenPhrases(places))
        return out
    }

    /** Nhãn + nhãn ngắn + cách nói đời thường **có dấu** ([SherpaSpokenWords.ACCENTED]) của một nút/datum. */
    private fun nounsOf(label: String, short: String?, synonyms: List<String>?): List<String> =
        (listOf(label) + listOfNotNull(short) + accented(synonyms.orEmpty())).distinct()

    /** Dạng có dấu của các cụm không dấu; cụm khai `NO_VI_FORM` (tiếng Anh/chữ số) không có dạng ⇒ bỏ. */
    private fun accented(unaccented: List<String>): List<String> =
        unaccented.mapNotNull { SherpaSpokenWords.ACCENTED[it] }

    private fun forms(verb: VoiceVerb): List<String> = SherpaSpokenWords.VERBS[verb].orEmpty()

    /**
     * Từ đầu của mọi dạng động từ có dấu — để nhận ra cách nói đã mang sẵn động từ.
     *
     * ⚠ Đây là phép đo THÔ và nó bắt nhầm — đã đếm trên danh mục thật (2026-09-16): 5 nhãn/cách nói bị coi là
     * "đã có động từ" dù chúng là **danh từ**, chỉ vì từ đầu trùng đầu một dạng động từ khác loại —
     * *"Phát hiện trẻ em"* (`phát` của PLAY), *"kiểm soát lực kéo"* · *"kiểm soát mô men"* (`kiểm` của
     * *"kiểm tra"*), *"Chuyển làn trái/phải"* (`chuyển` của SWITCH), *"Mở cửa cảnh báo trái/phải"* (`mở`).
     * Hậu quả **giới hạn**: mấy nhãn ấy mất bản cụm có động từ (*"BẬT KIỂM SOÁT LỰC KÉO"*), nhưng **vẫn được
     * bias** vì bản thân nhãn đã ≥ 2 từ nên vào tệp nguyên văn (`SherpaBiasingCoverageTest` ép điều đó).
     *
     * Chưa siết lại (ví dụ chỉ chặn khi nhãn bắt đầu bằng CHÍNH dạng động từ sắp ghép) vì mọi thay đổi ở đây
     * đổi NỘI DUNG tệp hotword ⇒ phải đo lại host + máy ảo trước khi ship (spec R5) — chưa đo thì chưa đổi.
     */
    private val VERB_HEADS: Set<String> =
        SherpaSpokenWords.VERBS.values.flatten().map { it.substringBefore(' ').lowercase() }.toSet()

    private fun startsWithVerb(phrase: String): Boolean = phrase.substringBefore(' ').lowercase() in VERB_HEADS
}
