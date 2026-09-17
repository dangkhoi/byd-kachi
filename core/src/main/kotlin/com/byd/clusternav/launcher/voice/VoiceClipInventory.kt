package com.byd.clusternav.launcher.voice

import java.text.Normalizer

/**
 * ═══ T3 · TRA GÓI CLIP "Giọng Kachi bé" — MỘT CHUỖI TRẢ LỜI → DANH SÁCH CLIP CẦN GHÉP ══════════════════════
 *
 * Spec `docs/specs/kachi-voice-clone.html` **R3 · §4.3 · §4.5**. Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm
 * off-car với một `index.tsv`/`num.tsv` **giả**; phần chạm đĩa + giải mã AAC nằm ở `:app` ([ClipSpeaker]).
 *
 * ## Vì sao tách bảng tra khỏi máy đọc
 * Máy đọc ([ClipSpeaker]) phải chạm `MediaCodec`/`AudioTrack` nên buộc ở `:app`. Nhưng thứ **quyết định câu này
 * đọc bằng clip nào** là luật thuần — và luật ấy có ba cạm bẫy off-car bắt được còn on-car thì không (§4.5): so
 * chuỗi CHÍNH XÁC (không mờ hoá), luật ghép ba mảnh, và cái ranh giới *"trượt thì trả rỗng"* để [ClipSpeaker]
 * rơi xuống Piper thay vì đọc nhầm câu. Để luật ở đây thì bài canh chạy không cần thiết bị.
 *
 * ## Ba tầng, hai đường ra (§4.3)
 * Gói có bốn tầng clip, mã hoá trong **tiền tố đường dẫn** của cột `file`: `fixed/` (câu trọn, KHÔNG số) ·
 * `head/` (phần TRƯỚC số) · `unit/` (phần SAU số) · `num/` (số 0–999 đọc thành chữ). [resolve] chỉ có hai kết
 * cục: **[Resolution.Hit]** = danh sách clip nối liền theo đúng thứ tự phát, hoặc **[Resolution.Miss]** = không
 * tra được (câu động, tên riêng, số ngoài 0–999…) ⇒ [ClipSpeaker] nhường Piper.
 *
 * ## ⚠ Chuỗi vào phải là chuỗi GỐC, chưa qua [TtsPronunciation.normalise]
 * Bảng khoá theo **chuỗi hiển thị gốc** (`"Nhiệt trong cabin: 24 °C"`), đúng như KDoc [TtsPronunciation] mục 3
 * dặn: phiên âm Latin→Việt là việc của *cửa ra tiếng*, chạy SAU chỗ tra clip. Tra bằng chuỗi đã phiên âm là
 * trượt cả gói, và trượt **im lặng**.
 */
class VoiceClipInventory private constructor(
    private val fixedByNorm: Map<String, Clip>,
    /** Đầu câu, xếp **dài trước** để khớp cái cụ thể nhất trước (`"Nhiệt trong cabin:"` thắng `"Nhiệt"`). */
    private val heads: List<Head>,
    private val unitsByNorm: Map<String, Clip>,
    private val numsByText: Map<String, Clip>,
) {

    /** Một clip trong gói: [tier] (fixed/head/unit/num) · [text] chuỗi gốc · [file] đường dẫn tương đối · [ms]. */
    data class Clip(val tier: String, val text: String, val file: String, val ms: Int)

    private class Head(val normText: String, val clip: Clip)

    /** Kết cục tra: [Hit] = các clip nối liền theo thứ tự phát; [Miss] = không tra được ⇒ nhường Piper. */
    sealed interface Resolution {
        data class Hit(val clips: List<Clip>) : Resolution
        object Miss : Resolution
    }

    val fixedCount: Int get() = fixedByNorm.size
    val headCount: Int get() = heads.size
    val unitCount: Int get() = unitsByNorm.size
    val numCount: Int get() = numsByText.size

    /**
     * Tra [reply] (chuỗi GỐC) → clip.
     *
     * Thứ tự: (1) trúng NGUYÊN CÂU tầng `fixed` — đường của mọi câu bật/tắt/mở/đóng, phát **một clip liền mạch**;
     * (2) ghép **đầu câu + số + đơn vị** cho câu đọc số đo / đặt bậc; (3) trượt ⇒ [Resolution.Miss].
     */
    fun resolve(reply: String): Resolution {
        val q = norm(reply)
        if (q.isEmpty()) return Resolution.Miss
        fixedByNorm[q]?.let { return Resolution.Hit(listOf(it)) }
        return composeNumberFrame(q) ?: Resolution.Miss
    }

    /**
     * Ghép `head + " " + <số> + [đơn vị]` — dựng ngược đúng cách gói được cắt (§4.5, `enumerate-clips.py`).
     *
     * Khung thật là `"<đầu câu> \x00<đuôi>"`: chỗ cắt bỏ dấu cách khỏi đầu câu (rstrip) và giữ nguyên đuôi (đơn
     * vị `" °C"` mang sẵn dấu cách, đuôi câu `", xe không nhận lệnh"` mang sẵn dấu phẩy). Nên dựng ngược là: sau
     * đầu câu có **đúng một dấu cách**, rồi tới số, rồi phần còn lại phải KHỚP CHÍNH XÁC một đơn vị (hoặc rỗng —
     * ca đọc số không đơn vị như *"Số: 3"*).
     */
    private fun composeNumberFrame(q: String): Resolution.Hit? {
        for (h in heads) {
            if (!q.startsWith(h.normText + " ")) continue
            val rest = q.substring(h.normText.length).trimStart()
            val digits = rest.takeWhile { it.isDigit() }
            if (digits.isEmpty()) continue
            val numClip = numsByText[digits] ?: continue
            val after = norm(rest.substring(digits.length))
            if (after.isEmpty()) return Resolution.Hit(listOf(h.clip, numClip))
            val unitClip = unitsByNorm[after] ?: continue
            return Resolution.Hit(listOf(h.clip, numClip, unitClip))
        }
        return null
    }

    /**
     * Trong [replies], câu nào KHÔNG tra được — chính là *"registry mọc nhãn mới mà gói thiếu clip"* (R2).
     *
     * Bật một nhãn thông tin mới ⇒ câu *"NhãnMới: {n} đơn vị"* cần một clip `head/` mới; gói chưa có ⇒ câu ấy
     * rơi vào đây. Một bài canh nạp `index.tsv` thật + danh sách câu trả lời đại diện rồi assert list này rỗng
     * sẽ **đỏ** đúng lúc gói tụt lại sau registry — thay vì im lặng đọc Piper trên xe.
     */
    fun missing(replies: Collection<String>): List<String> =
        replies.filter { resolve(it) is Resolution.Miss }

    companion object {

        /** Chuẩn hoá tra bảng: **NFC + gộp khoảng trắng + cắt hai đầu** (§4.5). KHÔNG bỏ dấu, KHÔNG mờ hoá. */
        fun norm(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFC).replace(WHITESPACE, " ").trim()

        private val WHITESPACE = Regex("\\s+")

        /**
         * Dựng từ nội dung hai tệp của gói.
         *
         * @param indexTsv nội dung `index.tsv` — mỗi dòng `<chuỗi>\t<đường dẫn>\t<ms>`; tầng lấy từ **tiền tố**
         *   đường dẫn (`fixed/`·`head/`·`unit/`). Không có dòng tiêu đề.
         * @param numTsv nội dung `num.tsv` — mỗi dòng `<số>\t<num/N.aac>\t<ms>`.
         *
         * Dòng hỏng (thiếu cột) bị **bỏ qua**, không ném: một tệp gói rách không được làm sập cả đường ra tiếng —
         * cùng lẽ *"trượt thì nhường Piper"* của [resolve].
         */
        fun parse(indexTsv: String, numTsv: String): VoiceClipInventory {
            val fixed = HashMap<String, Clip>()
            val heads = ArrayList<Head>()
            val units = HashMap<String, Clip>()
            for (clip in rows(indexTsv)) {
                when (clip.tier) {
                    "fixed" -> fixed[norm(clip.text)] = clip
                    "head" -> heads.add(Head(norm(clip.text), clip))
                    "unit" -> units[norm(clip.text)] = clip
                    else -> Unit // tầng lạ trong index.tsv ⇒ bỏ qua (num.tsv là nguồn của số)
                }
            }
            val nums = HashMap<String, Clip>()
            for (clip in rows(numTsv)) nums[clip.text.trim()] = clip
            heads.sortByDescending { it.normText.length }
            return VoiceClipInventory(fixed, heads, units, nums)
        }

        /** Mỗi dòng `<text>\t<file>\t<ms>` → [Clip]; tầng = tiền tố đường dẫn. Dòng thiếu cột ⇒ null (bỏ qua). */
        private fun rows(tsv: String): List<Clip> = tsv.lineSequence().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split('\t')
            if (parts.size < 2) return@mapNotNull null
            val text = parts[0]
            val file = parts[1].trim()
            if (file.isEmpty()) return@mapNotNull null
            val ms = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
            Clip(tier = file.substringBefore('/'), text = text, file = file, ms = ms)
        }.toList()
    }
}
