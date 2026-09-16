package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.LayoutPreset
import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ L7 · **BỐ CỤC BẰNG GIỌNG NÓI** — cách nói, và chỉ ở ĐÚNG MỘT vị trí ═════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **L7** (owner duyệt 2026-09-16). Thuần Kotlin (`:core`) ⇒ kiểm
 * off-car.
 *
 * ## Vì sao một lớp riêng, không thêm vào [VoiceSynonyms]/[VoiceGrammar.terms]
 * Cùng lý do đã ghi ở KDoc [VoicePlaces]: mọi thứ vào từ vựng chung đều được tra ở **mọi vị trí trong câu**, và
 * ba từ *"ô"*, *"cột"*, *"hàng"* quá thường để mang một nghĩa cố định. Thả *"ô"* vào từ vựng chung là mọi tiếng
 * *"ờ/ồ/ô"* thành một lệnh đổi bố cục ([VoiceLexicon.SLOT_HEADS] đã phải chừa đúng cái bẫy ấy). Ở đây cách nói
 * chỉ có nghĩa khi đứng **sau cụm đánh dấu** [MARKERS], và phần đuôi phải khớp **trọn vẹn** một bố cục.
 *
 * ## ⚠ *"về bố cục 2 cột"* — câu mà `VoiceIntentParserTest` cố ý giữ ở `NO_VERB` từ 1.64
 * Câu đó là bài canh sinh ra để chặn việc đưa `về`/`đi`/`đến` vào [VoiceGrammar.VERBS] như động từ dẫn đường
 * **vô điều kiện** (xem chú thích ⚠⚠ trong bảng đó). Ý ấy **không đổi**: `về` vẫn KHÔNG phải một động từ trong
 * bảng, và [VoicePlaces.PLACE_VERBS] vẫn chỉ nhận nó khi cả phần đuôi là một nơi đã lưu. Thứ đổi là câu ấy nay
 * có một **cách hiểu khác đúng hơn** — đuôi của nó là *"bố cục 2 cột"*, một BỐ CỤC, không phải một NƠI.
 *
 * ⇒ kỳ vọng của bài canh đổi từ `NO_VERB` sang `Layout(TWO_COL)` **có chủ ý**, và bài canh mới (cùng tệp) khoá
 * lại đúng cái nó luôn canh: *"về &lt;một chuỗi không phải nơi&gt;"* KHÔNG được thành `Nav`/`NavigateSaved`.
 *
 * ## Vì sao bảng 5 dòng chứ không suy từ `LayoutPreset`
 * [LayoutPreset] chỉ mang `slotCount`; nó **không** biết hai ô nằm cạnh nhau (cột) hay chồng lên nhau (hàng) —
 * thông tin ấy nằm trong hình học ở `WorkspaceLayout.slots`, dưới dạng phép tính. Rút nó ra bằng mã là đọc toạ
 * độ rồi đoán, mà đoán thì sai. Bảng dưới khai thẳng, và `VoiceLayoutParseTest` ép **mọi** giá trị của enum có
 * ít nhất một cách nói: thêm một bố cục mà quên khai ở đây ⇒ đỏ tại chỗ, không phải im lặng trên xe.
 */
object VoiceLayouts {

    /**
     * Cụm đánh dấu mở đầu phần bố cục. Có [MARKERS] trong câu thì mới xét tiếp — không có thì lớp này im lặng
     * trả `null` và câu đi tiếp y như chưa có gì xảy ra.
     */
    val MARKERS: List<List<String>> = listOf(listOf("bo", "cuc"), listOf("layout"))

    /**
     * Từ được phép đứng TRƯỚC [MARKERS] (*"đổi sang bố cục…"*, *"chuyển bố cục…"*, *"về bố cục…"*).
     *
     * Danh sách đóng chứ không phải *"cái gì cũng được"*: mở thì một câu dài bất kỳ có chữ *"bố cục"* ở giữa
     * (vd một tên bài hát) cũng thành lệnh đổi bố cục. Mọi từ ở đây đều là **động từ đổi/chọn** hoặc giới từ đi
     * kèm — chúng không mang nghĩa nào khác khi đứng trước hai chữ *"bố cục"*.
     */
    val LEAD_WORDS: Set<String> = setOf(
        "doi", "chuyen", "sang", "ve", "dat", "chinh", "chon", "dung", "thanh",
        "set", "change", "switch", "to", "use", "select",
    )

    /** Đơn vị *"ô"* — bố cục nói bằng **số ô** (*"bố cục 4 ô"*). */
    private val SLOT_UNITS = setOf("o", "slot", "slots")

    /** Đơn vị *"cột"* — hai ô nằm cạnh nhau. */
    private val COLUMN_UNITS = setOf("cot", "column", "columns")

    /** Đơn vị *"hàng"* — hai ô chồng lên nhau. */
    private val ROW_UNITS = setOf("hang", "dong", "row", "rows")

    /**
     * Một cách nói: `<số> <đơn vị>` → bố cục.
     *
     * @param units rỗng = **không cần đơn vị** (*"bố cục 4"*) — xem [presetOf].
     */
    private data class Spec(val count: Int, val units: Set<String>, val preset: LayoutPreset)

    /**
     * Bảng cách nói, xét theo thứ tự khai.
     *
     * ⚠ *"2 ô"* trỏ về [LayoutPreset.TWO_COL], không phải [LayoutPreset.TWO_ROW] — và đó là một **quyết định**,
     * không phải một chỗ bỏ sót: hai ô trên một màn hình xe nằm ngang (rộng gấp hơn hai lần chiều cao), nên
     * *"chia đôi"* mặc định là chia dọc. Ai muốn hai hàng thì nói đúng chữ *"hàng"* — có chữ ấy thì bảng trả
     * đúng [LayoutPreset.TWO_ROW].
     */
    private val SPECS: List<Spec> = listOf(
        Spec(1, SLOT_UNITS, LayoutPreset.ONE),
        Spec(2, COLUMN_UNITS, LayoutPreset.TWO_COL),
        Spec(2, ROW_UNITS, LayoutPreset.TWO_ROW),
        Spec(2, SLOT_UNITS, LayoutPreset.TWO_COL),
        Spec(3, SLOT_UNITS, LayoutPreset.THREE),
        Spec(4, SLOT_UNITS, LayoutPreset.QUAD),
    )

    /**
     * Câu [t] có phải một lệnh đổi bố cục không; `null` = **không phải**, và chỗ gọi đi tiếp như chưa gọi.
     *
     * Ba điều kiện, điều kiện thứ ba là thứ làm cơ chế này an toàn:
     *  1. có [MARKERS] trong câu;
     *  2. **mọi** từ đứng trước nó thuộc [LEAD_WORDS] (hoặc là từ đệm) — không có thì đây là một câu khác;
     *  3. **toàn bộ** phần đuôi khớp trọn một dòng của [SPECS] — thừa một từ là `null`, không đoán.
     */
    fun match(t: List<Token>): LayoutPreset? {
        val hit = MARKERS.firstNotNullOfOrNull { m ->
            t.indices.firstOrNull { VoiceLexicon.phraseAt(t, it, m) }?.let { i -> i to m.size }
        } ?: return null
        val (at, len) = hit
        val lead = t.subList(0, at)
        if (!lead.all { it.norm in LEAD_WORDS || it.norm in VoiceLexicon.FILLERS }) return null
        return presetOf(t.subList(at + len, t.size))
    }

    /**
     * `<số> [đơn vị]` → bố cục. Không có đơn vị (*"bố cục 4"*) thì hiểu là **số ô** — đó là cách đếm mà cả năm
     * bố cục đều nói được, và không có cách đọc thứ hai nào cho một con số trần ở vị trí ấy.
     */
    private fun presetOf(tail: List<Token>): LayoutPreset? {
        val body = tail.filterNot { it.norm in VoiceLexicon.FILLERS }
        if (body.isEmpty()) return null
        val num = VoiceLexicon.readNumber(body, 0) ?: return null
        val rest = body.subList(num.consumed, body.size)
        return when (rest.size) {
            0 -> SPECS.firstOrNull { it.count == num.value && it.units == SLOT_UNITS }?.preset
            1 -> SPECS.firstOrNull { it.count == num.value && rest[0].norm in it.units }?.preset
            else -> null
        }
    }

    /**
     * Cách nói **CÓ DẤU** cho tầng nghe (ngữ pháp + hotwords). Số viết bằng CHỮ có chủ ý:
     * [SherpaHotwords.normalize] bỏ mọi token mang chữ số, nên *"BỐ CỤC 2 CỘT"* sẽ rụng mất con số và thành
     * *"BỐ CỤC CỘT"* — một cụm không ai nói.
     *
     * *"bố cục"* trần cũng có mặt: nó là **tiền tố** của năm cụm dưới nên [SherpaHotwords.dropPrefixes] sẽ bỏ nó
     * khỏi tệp hotwords (đúng ý — xem KDoc [SherpaPhraseHotwords]), nhưng ngữ pháp của bộ nhận dạng thì **cần**
     * nó để hai chữ ấy nối được với nhau.
     */
    val SPOKEN: List<String> = listOf(
        "bố cục",
        "bố cục một ô",
        "bố cục hai cột",
        "bố cục hai hàng",
        "bố cục ba ô",
        "bố cục bốn ô",
        "đổi bố cục",
        "chuyển bố cục",
    )

    /**
     * MỌI từ (đã bỏ dấu) có thể tham gia một câu bố cục — để tầng NGHE khai đủ với bộ nhận dạng.
     *
     * Gom từ chính ba bảng đơn vị + [MARKERS] + [LEAD_WORDS], **không** chép tay: cùng lý do với
     * [VoiceLexicon.NUMBER_WORDS] và [VoiceLexicon.SLOT_WORDS] — thêm một đơn vị ở trên mà quên bên tầng nghe
     * thì câu gõ được mà **không nói được**, và cái thiếu ấy im lặng.
     */
    val WORDS: Set<String> = buildSet {
        MARKERS.forEach { addAll(it) }
        addAll(LEAD_WORDS)
        addAll(SLOT_UNITS); addAll(COLUMN_UNITS); addAll(ROW_UNITS)
    }
}
