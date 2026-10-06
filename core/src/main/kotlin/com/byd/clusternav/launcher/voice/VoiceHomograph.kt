package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceLexicon.Token
import java.text.Normalizer

/**
 * ═══ 2.93 VOICE-ROI-CONNECTOR — TỪ CHỨC NĂNG đồng hình sau khi bỏ dấu: chữ CÓ DẤU thì dấu là dữ liệu ═══════════════
 *
 * Tầng chữ so trên bản ĐÃ BỎ DẤU ([Token.norm]) — bắt buộc, vì nguồn chữ không bảo đảm dấu (bàn phím xe, nhật ký,
 * kịch bản test — KDoc [VoiceLexicon.deaccent]). Cái giá: bỏ dấu thì một TỪ CHỨC NĂNG (liên từ nối hai lệnh) trùng hệt
 * một từ thường. [ĐO off-car 06/10, spec `kachi-290-voice-app-names.html` §9 F4] *"phát bài mưa rơi bằng spotify"* ⇒
 * *"rơi"* = `roi` = *"rồi"* ⇒ câu bị tách đôi ⇒ vế 2 *"bằng spotify"* được phép chữa chính tả đọc thành *"bật spotify"*
 * ⇒ `[Media(QUERY "mưa"), OpenApp(Spotify)]` — tên bài mất một nửa và một app không ai xin bị mở.
 *
 * ## Một luật, generic (CLAUDE.md §7) — không danh sách từ cấm nào
 * Token **mang dấu** (chữ thô khác bản bỏ dấu) chỉ là từ [Words] khi chữ thô — chuẩn NFC, chữ thường — **đúng bằng** một
 * cách viết đã khai. Token **không dấu** (gõ không dấu · nhật ký cũ · chữ Anh) ⇒ khớp theo `norm` như mọi bản trước:
 * không có dữ liệu để phân biệt thì không đoán, và hành vi cũ giữ nguyên từng byte.
 *
 * Vì sao dấu đáng tin ở đây: mô hình nghe in chữ HOA CÓ DẤU (KDoc `VoiceRecognizer` ở `:app`), từ chức năng là từ thông
 * dụng nhất của câu nên mô hình in đúng thanh của nó; nếu có lúc in lệch thanh thì câu chỉ rơi về luật *"không tách"*
 * (phân tích nguyên câu — KDoc [VoiceIntentParser.parse]), không bao giờ thành một lệnh khác.
 */
object VoiceHomograph {

    /** Chữ thô ở dạng so được với một cách viết có dấu: chữ thường + NFC (phòng thủ — spec 2.93 §9 về chuỗi NFD). */
    fun spelling(raw: String): String = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFC)

    /**
     * 2.93 wave 2A · VOICE-TAUGHT-ACCENT-MATCH (OQ2 spec `kachi-293-voice.html`; spec `kachi-293-wave2a.html` §4.7) — cách viết
     * BẮT BUỘC của một tên đã dạy, theo CÙNG luật lớp này: chỉ tên GIỌNG ([TaughtSource.SPEECH] — chữ do mô hình nghe in, đủ thanh)
     * MỘT âm tiết mang cách viết (đó là ca đồng hình: «nay» dạy cho Drive ⇒ *"mở cái này"* mở Drive — [ĐO off-car 2.91 §9 F6]);
     * tên nhiều từ / tên GÕ ⇒ `null` (không ràng — chuỗi gõ không bảo đảm dấu). Cùng phép tách với bộ phân tích.
     */
    fun taughtSpelling(a: VoiceAppAlias): String? =
        if (a.source != TaughtSource.SPEECH || a.words.size != 1) null
        else VoiceLexicon.tokenize(a.accented).singleOrNull()?.let { spelling(it.raw) }

    /**
     * Token [tok] có khớp cách viết [spelled] của một cụm không: không ràng (`null`) ⇒ có; chữ thô KHÔNG mang dấu (gõ không dấu,
     * nhật ký cũ — không dữ liệu để phân biệt) ⇒ có, như mọi bản trước; mang dấu ⇒ chỉ khi đúng cách viết đã dạy (không kể CHỖ
     * đặt dấu thanh — [toneKey]).
     */
    fun spelledOk(tok: Token, spelled: String?): Boolean {
        if (spelled == null) return true
        val s = spelling(tok.raw)
        return s == tok.norm || s == spelled || toneKey(s) == toneKey(spelled)
    }

    /**
     * Senior review wave 2A Pass 1 [P3] — khoá so một âm tiết KHÔNG phụ thuộc CHỖ đặt dấu thanh: *"hoà"* ↔ *"hòa"*, *"thuỷ"* ↔
     * *"thủy"*, *"khoẻ"* ↔ *"khỏe"* là CÙNG một chữ (cùng âm, cùng thanh — hai quy ước chính tả) nhưng là hai chuỗi Unicode khác
     * nhau kể cả sau NFC ⇒ so chuỗi thẳng làm tên GIỌNG đã dạy trượt khi nguồn chữ (bàn phím, bản ghi khác) đặt dấu kiểu kia — hồi
     * quy so với 2.92 (khớp theo bản bỏ dấu). Khoá = chữ đã gỡ DẤU THANH (giữ mũ · trăng · móc và `đ`) + chính dấu thanh: khác
     * thanh (*"này"* ↔ *"nay"*) hay khác nguyên âm (*"cơm"* ↔ *"còm"*) vẫn khác khoá — luật đồng hình giữ nguyên.
     */
    private fun toneKey(s: String): String {
        val d = Normalizer.normalize(s, Normalizer.Form.NFD)
        return Normalizer.normalize(d.filterNot { it in TONE_MARKS }, Normalizer.Form.NFC) + "|" + d.filter { it in TONE_MARKS }
    }

    /** Năm dấu thanh dạng tổ hợp: huyền U+0300 · sắc U+0301 · ngã U+0303 · hỏi U+0309 · nặng U+0323. */
    private const val TONE_MARKS = "\u0300\u0301\u0303\u0309\u0323"

    /**
     * Một tập TỪ CHỨC NĂNG khai bằng cách viết CÓ DẤU (nguồn duy nhất; bản bỏ dấu suy ra — [norms]).
     *
     * @param spelled cách viết chữ thường có dấu, vd `"rồi"`; chữ không dấu (`"and"`) thì cách viết = bản bỏ dấu.
     */
    class Words(vararg spelled: String) {
        private val byNorm: Map<String, Set<String>> =
            spelled.groupBy({ VoiceLexicon.deaccent(it) }, { spelling(it) }).mapValues { (_, v) -> v.toSet() }

        /** Bản bỏ dấu — cho chỗ chỉ có `norm` (tầng nghe khai từ vựng, cổng dạy tên chặn từ nối). */
        val norms: Set<String> = byNorm.keys

        /**
         * [tok] có phải một từ của tập không — xem luật ở KDoc lớp ngoài: `s == tok.norm` ⇒ chữ thô KHÔNG mang dấu (không dữ
         * liệu để tách đồng hình ⇒ hành vi cũ); ngược lại chỉ nhận đúng một cách viết đã khai.
         */
        fun matches(tok: Token): Boolean {
            val forms = byNorm[tok.norm] ?: return false
            val s = spelling(tok.raw)
            return s == tok.norm || s in forms
        }
    }
}
