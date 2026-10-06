package com.byd.clusternav.launcher.voice

/**
 * ═══ V2 pha NGHE · NGUỒN HOTWORDS — CỤM LỆNH **CÓ DẤU** TỪ CHÍNH DANH MỤC ════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-engine-v2.html` §Design + `kachi-voice-hotword-phrases.html` (cụm, không từ rời).
 * Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Vì sao tệp riêng, không dùng [VoiceGrammar.terms]
 * [VoiceGrammar.terms] trả chữ **đã bỏ dấu** (phục vụ tầng so khớp chữ). Hotwords của sherpa cần chữ **HOA CÓ
 * DẤU** để bộ mã hoá BPE khớp bảng token của mô hình VN ([SherpaHotwords] giải thích). Nên ở đây đi thẳng vào 4
 * bộ đăng ký lấy **nhãn gốc có dấu** ([SherpaPhraseHotwords]) — cùng NGUỒN với [VoicePhrases.build] (Vosk).
 *
 * ## Chỉ tập TĨNH (không hồ sơ/app)
 * Biasing chỉ giúp các **lệnh control tiếng Việt** (đèn/kính/nhiệt độ/âm lượng…). Tên hồ sơ + tên app do người
 * dùng đặt (thường là danh từ riêng / tiếng Anh) — mô hình VN không phát ra được token đó nên biasing vô nghĩa;
 * [VoiceIntentParser] khớp nhãn app lo phần ấy ([ĐO] evidence: `youtube` → "ô tường", biasing không cứu được).
 * ⚠ Luật này đã có BA ngoại lệ, mỗi cái có số đo riêng: sổ địa chỉ + tên hồ sơ (KDoc [hotwordsFile]), tên app đã dạy
 * (2.91, [SherpaTaughtHotwords]) và nhãn app **viết bằng chữ Việt** (2.93, [SherpaLabelHotwords]). Nhãn chữ Anh vẫn
 * KHÔNG vào — [ĐO host 2026-10-06] thêm chúng là kéo câu sang MỞ NHẦM app.
 */
object SherpaBiasing {

    /**
     * Nội dung tệp hotwords (mỗi dòng một cụm HOA có dấu), đã lọc + khử trùng qua [SherpaHotwords].
     * Rỗng ⇒ chạy không biasing.
     *
     * @param places nhãn trong **sổ địa chỉ** của hồ sơ đang dùng (spec `kachi-voice-addresses.html` R6).
     *   ⚠ Đây là NGOẠI LỆ có lý do của luật *"chỉ tập tĩnh"* ở KDoc lớp: luật đó loại tên hồ sơ/app vì chúng là
     *   danh từ riêng / tiếng Anh mà mô hình VN **không phát ra được token**. Nhãn địa chỉ thì ngược lại — đó là
     *   tiếng Việt đời thường (*"Nhà"*, *"Công ty"*, *"Nhà ngoại"*), đúng thứ biasing kéo về được. Nhãn nào có
     *   chữ số/ký tự lạ vẫn bị [SherpaHotwords.normalize] loại, nên không cần lọc thêm ở đây.
     * @param profiles tên hồ sơ — **ngoại lệ thứ hai**, và nó bác đúng một nửa của luật ở KDoc lớp bằng phép đo:
     *   [ĐO xe 2026-09-26] hồ sơ *"Test"* 8/8 lượt nghe ra *"chuyển sang hồ sơ"* (rụng tên), và *"Mặc định"* —
     *   **hai từ tiếng Việt thường** — ra *"hồ sơ định"* (rụng chữ đầu). Tên tiếng Việt thì biasing kéo về được;
     *   tên tiếng Anh vào tệp qua **dạng đọc tiếng Việt** ([VoiceAppPhonetics]). Xem [VoiceProfileNames.phrases].
     */
    fun hotwordsFile(
        places: List<String> = emptyList(),
        profiles: List<String> = emptyList(),
        /**
         * 2.91 VOICE-APP-NAMES R8 — tên app ĐÃ DẠY còn sống của hồ sơ đang dùng (chỉ nguồn GIỌNG được bias, OQ3).
         * Rỗng ⇒ tệp y nguyên bản trước. Luật đơn điệu (tệp mới ⊇ tệp cũ) ở [SherpaTaughtHotwords].
         */
        taught: List<TaughtName> = emptyList(),
        /**
         * 2.93 VOICE-ALT-LABEL-HOTWORD — nhãn app của bảng gọi app phiên ([SherpaLabelHotwords.labels]); chỉ nhãn viết chữ
         * Việt được bias, xếp SAU tên đã dạy (lớp 2). Rỗng ⇒ tệp y nguyên bản trước.
         */
        labels: List<String> = emptyList(),
    ): String {
        val key = Inputs(places.toList(), profiles.toList(), taught.toList(), labels.toList())   // chép: người gọi đổi list sau ⇒ không lệch khoá
        last?.let { (k, file) -> if (k == key) return file }
        return build(key.places, key.profiles, key.taught, key.labels).also { last = key to it }
    }

    /**
     * Phép dựng THẬT, không nhớ — [hotwordsFile] gọi khi bộ đầu vào khác lượt trước; bài kiểm tính ổn định gọi thẳng.
     * Hàm thuần: 4 bộ đăng ký là hằng của bản build, nên cùng đầu vào ⇒ cùng tệp. Người gọi `:core` có bộ đầu vào KHÁC
     * phiên lệnh ([TeachGuard]) cũng gọi thẳng — ô nhớ chỉ có MỘT chỗ, để dành cho đường *bấm → micro mở*.
     */
    internal fun build(places: List<String>, profiles: List<String>, taught: List<TaughtName>, labels: List<String>): String {
        val phrases = SherpaPhraseHotwords.phrases(places, profiles)
        val appNames = SherpaPhraseHotwords.appNames()
        val static = SherpaTaughtHotwords.Stack(SherpaHotwords.phraseFile(phrases, appNames), phrases, appNames)
        val withTaught = SherpaTaughtHotwords.push(static, taught)
        return SherpaTaughtHotwords.push(withTaught, SherpaLabelHotwords.names(labels)).file
    }

    /** Khoá của ô nhớ [last] — bản CHÉP bốn đầu vào của [hotwordsFile] (so bằng giá trị). */
    private data class Inputs(
        val places: List<String>,
        val profiles: List<String>,
        val taught: List<TaughtName>,
        val labels: List<String>,
    )

    /**
     * 2.93 soát giọng Pass 2 (P3 b): tệp của LƯỢT TRƯỚC, nhớ theo đúng bộ đầu vào. Đường bấm → micro mở dựng tệp mỗi phiên
     * ([ĐO host] 5,6–6,3 ms với 21 nhãn, chậm hơn 2.92 ~1–1,7 ms); bộ đầu vào hiếm khi đổi giữa hai phiên ⇒ lượt sau
     * trả ngay. Hai luồng ghi đè nhau chỉ tốn thêm một lượt dựng — giá trị nào cũng đúng (cùng đầu vào, cùng tệp).
     */
    @Volatile private var last: Pair<Inputs, String>? = null

    /*
     * ## Lịch sử — vì sao không còn `accentedControlPhrases()` (nhãn + động từ + danh từ RỜI)
     * Bản 1.60–1.64 đổ thẳng nhãn của 4 bộ đăng ký (VI + EN), động từ có dấu và cách nói đời thường — 623 dòng, phần
     * lớn là **một từ**. [ĐO] 2026-09-16 (ba ma trận host, KDoc [SherpaPhraseHotwords]): chính những dòng một từ ấy
     * làm `xem pin` → *"xem tin"*, `dừng nhạc` → *"rừng nhạc"* dù `PIN`/`DỪNG` có trong tệp — khớp trọn một hotword
     * là đồ thị ngữ cảnh về gốc, nên từ rời vừa chặn cụm dài vừa cộng điểm cho đường sai. Nguồn hotword nay là
     * **cụm động từ + đối tượng** sinh từ cùng 4 bộ đăng ký ([SherpaPhraseHotwords]); [SherpaSpokenWords] vẫn là
     * nơi duy nhất khai dạng có dấu của từ vựng (động từ + cách nói), chỉ không còn được đổ RỜI vào tệp.
     */
}
