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
     */
    fun hotwordsFile(places: List<String> = emptyList()): String =
        SherpaHotwords.phraseFile(SherpaPhraseHotwords.phrases(places), SherpaPhraseHotwords.appNames())

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
