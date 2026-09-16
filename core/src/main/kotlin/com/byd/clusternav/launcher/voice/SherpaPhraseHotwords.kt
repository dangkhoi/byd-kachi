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
        // ═══ H3 · TÊN APP đọc theo âm Việt — *"MỞ GU GỒ MÁP"*, *"BẬT VIỆT MÁP"* ═════════════════════════
        // [ĐO] `voice-mishear-2026-09-16.md` §3: loại ý định `app` đúng 12,8 % — thấp nhất bảng; §1 cho thấy
        // đúng tệp hotword có thêm các cụm này (`hotwords-existing+proposed.txt`) kéo tổng 49,2 % → 52,1 % và
        // riêng loại `app` 12,6 % → 20,6 %. Sinh từ [VoiceSynonyms.APP_TARGETS] qua [SherpaSpokenWords.ACCENTED]
        // (tên tiếng Anh nằm ở `NO_VI_FORM` ⇒ tự rụng ở [accented], đúng luật *"mô hình VN không phát được"*).
        //
        // ⚠ Dòng **tên app đứng một mình** vẫn được sinh ra ở đây rồi bị [SherpaHotwords.dropAppNameLeading]
        // loại ở tầng lọc — cố ý: tầng này chỉ nói *"cụm nào đáng bias"*, mọi luật LỌC nằm ở một chỗ duy nhất.
        val appVerbs = forms(VoiceVerb.OPEN) + forms(VoiceVerb.ON)
        accentedAppNames().filterNot { extendsAnotherApp(it) }.forEach { n ->
            out.add(n)
            if (!startsWithVerb(n)) appVerbs.forEach { v -> out.add("$v $n") }
        }
        // L7 — bố cục: *"BỐ CỤC HAI CỘT"* · *"ĐỔI BỐ CỤC"*. Số viết bằng CHỮ (xem KDoc [VoiceLayouts.SPOKEN]);
        // dòng *"bố cục"* trần là **tiền tố** của năm dòng kia nên [SherpaHotwords.dropPrefixes] tự bỏ nó — đúng
        // luật tiền tố mà cả tệp này dựng lên để giữ.
        out.addAll(VoiceLayouts.SPOKEN)
        return out
    }

    /**
     * MỌI cách gọi app ở dạng đem đi so với một dòng hotword — nguồn cho [SherpaHotwords.dropAppNameLeading].
     *
     * Lấy **cả hai** dạng: bản có dấu (*"gu gồ máp"*) và bản khai gốc (*"youtube music"* — tên tiếng Anh không có
     * dạng có dấu). Bản gốc không bao giờ trở thành một dòng hotword hôm nay (nó nằm ở [SherpaSpokenWords.NO_VI_FORM]
     * nên [accented] bỏ), nhưng luật cấm phải tính cả nó: ngày ai đó khai một dạng có dấu cho *"youtube music"*
     * thì cái bẫy phải đã bị chặn sẵn, không phải chờ đo lại mới phát hiện.
     *
     * ## ⚠ Trừ ra cách gọi app **cũng là chữ mở đầu của một cụm ĐỘNG TỪ** ([ĐO] lượt sinh tệp 2026-09-16)
     * Bí danh của Waze là *"quay"* — đúng chữ mở đầu của cụm động từ `quay lại bài` (= lệnh BÀI TRƯỚC). Luật
     * cấm-tên-app-đứng-đầu vì thế **xoá mất dòng `QUAY LẠI BÀI`**, và `SherpaBiasingCoverageTest` bắt được ngay
     * (*"khai dạng có dấu mà không cụm nào mang nó"*). Đó là một luật an toàn ăn mất một lệnh có thật — đúng họ
     * lỗi mà CLAUDE.md §6 cấm: đường mới không được phá đường cũ đang chạy.
     *
     * Phép trừ là **dữ liệu, không phải một tên bị viết cứng**: hỏi thẳng [VoiceGrammar.VERBS] xem dãy từ của
     * cách gọi ấy có phải **tiền tố** của một cụm động từ nào không. Thêm một bí danh app trùng động từ mai sau
     * thì nó tự được trừ; thêm một động từ mới trùng một bí danh cũng vậy.
     */
    fun appNames(): List<String> = VoiceSynonyms.APP_TARGETS.values.flatten()
        .flatMap { listOfNotNull(SherpaSpokenWords.ACCENTED[it], it) }
        .distinct()
        .filterNot { clashesWithVerb(it) }

    /**
     * Cách gọi này có phải **tiền tố theo từ** của một cụm động từ đã khai không (*"quay"* ⊂ *"quay lại bài"*).
     *
     * So trên dạng **đã bỏ dấu**, vì [VoiceGrammar.VERBS] khai không dấu còn cách gọi app có cả hai dạng — hai
     * bảng, một phép so, không có bảng chuẩn hoá thứ hai.
     */
    private fun clashesWithVerb(appName: String): Boolean {
        val words = VoiceLexicon.tokenize(appName).map { it.norm }
        if (words.isEmpty()) return false
        return VoiceGrammar.VERBS.any { (verbWords, _) ->
            verbWords.size >= words.size && verbWords.take(words.size) == words
        }
    }

    /** Cách gọi app **có dạng đọc tiếng Việt** — phần đáng bias (xem KDoc [appNames] về phần còn lại). */
    private fun accentedAppNames(): List<String> =
        accented(VoiceSynonyms.APP_TARGETS.values.flatten()).distinct()

    /**
     * Cách gọi này có **nối dài** cách gọi của một app KHÁC không (*"youtube nhạc"* nối dài *"youtube"*)?
     *
     * ## Bẫy [ĐO] — máy ảo 2026-09-16, ca `w10` *"đưa YouTube vào ô số hai"*
     * Lượt sinh tệp đầu tiên có khối tên app này làm `w10` tụt xuống **"đưa youtube"**, mất sạch mệnh đề ô — đúng
     * ca mà `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §8.4 đã mô tả, chỉ khác hình dạng. Thủ phạm là ba
     * dòng `MỞ/ĐƯA/BẬT YOUTUBE NHẠC` (sinh từ bí danh `youtube nhac` của **YT Music**): nghe xong `ĐƯA YOUTUBE`
     * thì đồ thị ngữ cảnh đang đứng **GIỮA** một hotword, nên nó cộng điểm cho `NHẠC` và trừ mọi đường khác — kể
     * cả `VÀO Ô SỐ HAI`. Người lái mất mệnh đề ô mà không có gì báo.
     *
     * ## Vì sao luật là *"app KHÁC"*, không phải *"mọi tiền tố"*
     * Nếu cấm mọi tiền tố thì `gu gồ máp` (nối dài `gu gồ`) cũng rụng — và đó chính là cụm mà H3 sinh ra để thêm.
     * Khác biệt THẬT nằm ở chỗ đồ thị dừng giữa chừng **dẫn tới đâu**: dừng ở `gu gồ` vẫn ra **Google Maps** (cùng
     * một đích, vô hại), còn dừng ở `youtube` rồi bị kéo sang `nhạc` là đổi sang **một app khác** — hoặc, như
     * `w10`, nuốt mất phần đuôi của câu. Nên phép so là *"hai bí danh này của cùng một `key` hay không"*, và nó
     * đọc thẳng [VoiceSynonyms.APP_TARGETS] chứ không có danh sách ngoại lệ nào viết tay.
     *
     * ⚠ Chỉ chặn **bias**, không chặn hiểu: bí danh vẫn nằm nguyên trong [VoiceSynonyms.APP_TARGETS] nên câu
     * *"mở du túp miu dích"* vẫn ra đúng YT Music ở tầng CHỮ ([ĐO] ca `t77` xanh) — nó chỉ không được cộng điểm
     * ở tầng âm.
     */
    /**
     * Cách gọi app **cố ý KHÔNG bias** — dạng công khai của [extendsAnotherApp], cho bài canh đọc.
     *
     * Có mặt để phép loại trừ là một **danh sách đọc được**, không phải một chỗ hụt im lặng: bài canh
     * `SherpaBiasingCoverageTest` đòi mọi dạng có dấu phải nằm trong một cụm của tệp hotword, và nó phải phân
     * biệt được *"hụt vì quên"* với *"vắng vì đã quyết"*. Thêm một bí danh nối dài app khác mai sau thì nó tự
     * xuất hiện ở đây và bài canh tự biết.
     */
    fun notBiasedAppNames(): List<String> = accentedAppNames().filter { extendsAnotherApp(it) }

    private fun extendsAnotherApp(accentedName: String): Boolean {
        val key = ownerOf(accentedName) ?: return false
        val words = VoiceLexicon.tokenize(accentedName).map { it.norm }
        if (words.size < 2) return false
        return VoiceSynonyms.APP_TARGETS.any { (otherKey, aliases) ->
            otherKey != key && aliases.any { a ->
                val w = VoiceLexicon.tokenize(a).map { it.norm }
                w.isNotEmpty() && w.size < words.size && words.take(w.size) == w
            }
        }
    }

    /** Mã app sở hữu một cách gọi **có dấu** — tra ngược qua chính bảng bí danh, không có bảng thứ hai. */
    private fun ownerOf(accentedName: String): String? =
        VoiceSynonyms.APP_TARGETS.entries.firstOrNull { (_, aliases) ->
            aliases.any { SherpaSpokenWords.ACCENTED[it] == accentedName }
        }?.key

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
     * ⚠ Đây là phép đo THÔ và nó bắt nhầm: một nhãn **danh từ** có từ đầu trùng đầu một dạng động từ khác loại
     * (vd *"Mở khoá cửa"* bắt đầu bằng `mở`) bị coi là "đã có động từ". Hậu quả **giới hạn**: nhãn ấy mất bản cụm
     * có động từ, nhưng **vẫn được bias** vì bản thân nhãn đã ≥ 2 từ nên vào tệp nguyên văn
     * (`SherpaBiasingCoverageTest` ép điều đó). [ĐO 2026-09-16] trên danh mục trước lượt gỡ ADAS: 5 nhãn dính;
     * bốn trong số đó là nhãn ADAS/an toàn nay đã xoá.
     *
     * Chưa siết lại (ví dụ chỉ chặn khi nhãn bắt đầu bằng CHÍNH dạng động từ sắp ghép) vì mọi thay đổi ở đây
     * đổi NỘI DUNG tệp hotword ⇒ phải đo lại host + máy ảo trước khi ship (spec R5) — chưa đo thì chưa đổi.
     */
    private val VERB_HEADS: Set<String> =
        SherpaSpokenWords.VERBS.values.flatten().map { it.substringBefore(' ').lowercase() }.toSet()

    private fun startsWithVerb(phrase: String): Boolean = phrase.substringBefore(' ').lowercase() in VERB_HEADS
}
