package com.byd.clusternav.launcher.voice

import java.text.Normalizer

/**
 * ═══ 2.93 VOICE-ALT-LABEL-HOTWORD — NHÃN APP VIẾT CHỮ VIỆT vào hotword (mọi nhãn chữ Việt, không riêng locale thứ hai) ═══
 *
 * Spec `docs/specs/kachi-293-voice.html` §7 OQ1 (đã giải) + §9. Thuần Kotlin (`:core`) ⇒ kiểm off-car.
 *
 * ## Quyết định bằng số đo host — luật hotword R5: đo TRƯỚC khi đổi tệp hotword của mọi người dùng
 * [ĐO host 2026-10-06] sherpa-onnx 1.13.8 + đúng 4 tệp mô hình đã ghim sha256 ở [SherpaModelCatalog] + tệp tĩnh do
 * CHÍNH Kotlin sinh; 27 câu cũ của `voice-wavgen.sh` + 28 câu gọi app bằng nhãn (`scripts/voice/label-wavgen.sh`), bản
 * sạch và 9 phép làm khó tất định (`scripts/voice/degrade-wavs.py`) = 550 WAV; chấm cả CHỮ lẫn Ý ĐỊNH qua chính
 * [VoiceIntentParser] (số đầy đủ ở spec §9):
 *  • chỉ nhãn LOCALE THỨ HAI — xe locale vi ⇒ đó là nhãn tiếng Anh (*"Settings"*, *"Camera"*): 0 ca đổi trên 550 WAV;
 *  • MỌI nhãn chữ Việt: 0 ca tụt (chữ lẫn ý định) · câu gọi app khó nghe đúng ý định nhiều hơn rõ rệt (*"MỞ MÁI TIẾNG"*
 *    → *"MỞ MÁY TÍNH"*, *"MỞ VI ÂM"* → *"MỞ GHI ÂM"*); 100 nhãn (+264 dòng) vẫn 0 tụt, `createStream` +0,5 ms (host).
 *    Rủi ro còn lại [ĐO]: cài cặp nhãn GẦN ÂM (*"Lịch"* hay *"Dịch"*) ⇒ 1/252 ca hỏi-lại thành mở-nhầm (tổng mở-nhầm 4 → 2);
 *  • thêm cả nhãn chữ Anh (*"Video"*, *"YouTube"*…): *"mở ghi chú"* (app không cài) ⇒ `OpenApp(Video)` — MỞ NHẦM app.
 * ⇒ nguồn = mọi nhãn VIẾT BẰNG CHỮ VIỆT trong bảng gọi app của phiên (nhãn máy + nhãn locale thứ hai của `AppAltLabels`
 * bên `:app`), không rẽ nhánh theo locale: máy locale vi thì nhãn Việt là nhãn máy, máy locale en thì là nhãn phụ —
 * [isVietnamese] tự chọn đúng ở cả hai (CLAUDE.md §7).
 *
 * ## Không phải đường riêng — đi đúng luật của tên đã dạy ([SherpaTaughtHotwords.push])
 * Mỗi nhãn sinh `MỞ/ĐƯA/BẬT <NHÃN>`; nhãn làm rụng một dòng của tệp TRƯỚC nó (tĩnh + tên đã dạy) hoặc nối dài một nhãn
 * khác thì bị loại (luật đơn điệu — vd *"Cài đặt"* giết dòng tĩnh `CÀI ĐẶT`, *"Đèn"* giết `ĐÈN ĐỌC SÁCH` ⇒ không bias).
 * Tên đã dạy ĐI TRƯỚC: nhãn là lớp 2, xét trên tệp đã có lớp 1 ⇒ không nhãn nào làm một tên đã dạy mất bias — tệp
 * khi không có nhãn y nguyên 2.91 từng byte.
 */
object SherpaLabelHotwords {

    /**
     * Trần số nhãn — bằng cỡ ĐÃ ĐO: 100 nhãn chữ Việt vào xét ⇒ +264 dòng, 0 ca tụt, `createStream` 7,2 → 7,7 ms trên host.
     * [SUY] tỉ lệ xe/host ≈ 9 lần (`createStream` 55–70 ms trên xe ở log 2.70) ⇒ ≈ +5 ms trên xe, trong ngân sách 150 ms của
     * spec hotword R-nf1 — cổng thật vẫn là phép đo 🚗.
     */
    const val MAX_LABELS = 100

    /** Chủ giả của một nhãn ở [SherpaTaughtHotwords.plan]: mỗi nhãn một chủ ⇒ luật "nối dài tên của app KHÁC" áp giữa nhãn. */
    private const val OWNER = "nhan:"

    private const val VI_LETTERS = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ"

    /**
     * Nhãn viết bằng CHỮ VIỆT: mọi chữ cái là Latin `a–z` hoặc chữ Việt, và có ÍT NHẤT một chữ mang dấu / `đ`. Chữ Hán,
     * Thái… ⇒ `false` (mô hình VN không có token); nhãn toàn chữ Anh ⇒ `false` (đo được: kéo nhầm câu sang app khác).
     */
    fun isVietnamese(label: String): Boolean {
        var vi = false
        for (c in Normalizer.normalize(label, Normalizer.Form.NFC)) {
            if (!c.isLetter()) continue
            val l = c.lowercaseChar()
            if (l in VI_LETTERS) vi = true else if (l !in 'a'..'z') return false
        }
        return vi
    }

    /**
     * Nhãn đáng bias trong BẢNG GỌI APP của phiên (khoá của `VoiceAppIndex` — nhãn máy, tên đã dạy, nhãn phụ, dạng đọc).
     * Bỏ: khoá là một tên đã dạy ([taught], MỌI nguồn — tên gõ không bias (OQ3), tên giọng đi lớp của nó) · khoá là DẠNG
     * ĐỌC sinh từ một khoá khác ([VoiceAppPhonetics.spokenForms]) · nhãn không chữ Việt. Khử trùng theo chữ bỏ dấu, giữ
     * thứ tự bảng (nhãn máy trước nhãn phụ), cắt ở [MAX_LABELS].
     *
     * Dạng đọc: [ĐO host 2026-10-07, VOICE-PHONETIC-LABEL-HOTWORD — ĐO, KHÔNG NHẬN] thêm 8 dạng (*"za lô"* · *"nét phờ
     * lích"* · *"ca plây"* …, +24 dòng): câu cũ 0 tụt, câu đọc ĐÚNG dạng ấy +20 ý định, nhưng chính app ấy đọc kiểu Anh
     * −11 — trong đó *"đưa Netflix vào ô số hai"* mất cả mệnh đề ô (bẫy `w10`: đồ thị kẹt giữa `ĐƯA NÉT PHỜ…`). Dạng đọc
     * là chữ đoán từ bảng âm tiết, không phải chữ mô hình in ⇒ đường an toàn là DẠY tên (spec 2.93 §4.11).
     */
    fun labels(keys: Collection<String>, taught: Collection<TaughtName>): List<String> {
        val own = taught.mapTo(HashSet()) { it.accented }
        val derived = HashSet<String>()
        keys.forEach { k -> VoiceAppPhonetics.spokenForms(k).forEach { f -> if (f != k.lowercase()) derived += f } }
        val seen = HashSet<String>()
        return keys.asSequence()
            .filter { it !in own && it !in derived && isVietnamese(it) && seen.add(normOf(it)) }
            .take(MAX_LABELS)
            .toList()
    }

    /**
     * Ứng viên cho [SherpaTaughtHotwords.push] — mỗi nhãn một chủ giả, nguồn GIỌNG (đúng bộ lọc của `plan`). Bỏ trước khi
     * xét: nhãn không chữ Việt (phòng chỗ gọi khác [labels]) · nhãn NỐI DÀI một cách gọi app của bảng tĩnh
     * ([SherpaPhraseHotwords.appNames], vd *"bản đồ"* ⊂ *"Bản đồ số"*): cùng bẫy `w10` mà `extendsAnotherApp` của
     * [SherpaPhraseHotwords] chặn — dòng `MỞ BẢN ĐỒ` đã rụng (tiền tố của `MỞ BẢN ĐỒ GOOGLE`) nên
     * [SherpaTaughtHotwords.killedBy] không thấy, phải chặn ở đây. Nhãn trùng / là tiền tố của một tên đã dạy KHÔNG cần
     * chặn: lớp nhãn xét trên tệp đã có tên đã dạy, dòng của nhãn ấy chỉ rụng ở `dropPrefixes` (bài canh khoá).
     * NFC trước mọi phép: chữ tổ hợp NFD vỡ ở [SherpaHotwords.normalize] (dấu kết hợp không phải chữ cái).
     */
    fun names(labels: List<String>): List<TaughtName> {
        if (labels.isEmpty()) return emptyList()
        val aliases = SherpaPhraseHotwords.appNames().map { a -> VoiceLexicon.tokenize(a).map { it.norm } }
        return labels.asSequence()
            .map { Normalizer.normalize(it.trim(), Normalizer.Form.NFC) }
            .filter { isVietnamese(it) }
            .map { TaughtName(OWNER + normOf(it), TaughtSource.SPEECH, it.lowercase(), it) }
            .filter { n -> n.words.isNotEmpty() && aliases.none { a -> a.size < n.words.size && n.words.take(a.size) == a } }
            .distinctBy { it.norm }
            .take(MAX_LABELS)
            .toList()
    }

    private fun normOf(s: String): String = VoiceLexicon.tokenize(s).joinToString(" ") { it.norm }
}
