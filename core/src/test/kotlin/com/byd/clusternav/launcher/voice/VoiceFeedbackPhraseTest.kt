package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.Strings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * ═══ V1 pha NÓI · BÀI KHOÁ CÂU ĐỌC ═══════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **R1**. Thuần JVM, không Robolectric, không xe.
 *
 * ## Bài này khoá đúng bốn thứ **hỏng-thì-không-ai-thấy**
 *  1. ký hiệu của mắt (`✓ ✗ « » = —`) lọt vào câu đọc ⇒ người lái nghe *"dấu kiểm"*, hoặc tệ hơn là nghe
 *     **giống hệt nhau** cho một việc thành công và một việc thất bại;
 *  2. gộp một vế HỎNG vào dưới chữ *"Đã"* ⇒ câu xác nhận **nói dối** đúng ở chỗ nguy hiểm nhất;
 *  3. dòng tạm (*"đang tra điểm đến…"*) bị đọc ⇒ hai câu cho một việc, câu đầu nói về trạng thái đã hết hạn;
 *  4. câu dài quá [VoiceFeedbackPhrase.MAX_WORDS] ⇒ người đang lái không nghe hết, và nghe nửa chừng thì tưởng
 *     máy làm ít việc hơn thật.
 *
 * Câu vào lấy **nguyên dạng** mà `VoiceReply` sinh ra (`"✓ " + preview + đuôi`), không phải chuỗi bịa cho vừa.
 */
class VoiceFeedbackPhraseTest {

    @BeforeEach fun vi() { Strings.current = Lang.VI }

    @AfterEach fun reset() { Strings.current = Lang.VI }

    // ══ (1) KÝ HIỆU CỦA MẮT KHÔNG ĐƯỢC LỌT VÀO CÂU ĐỌC ════════════════════════════════════════════════════

    @Test
    fun `mot viec xong doc thanh mot cau khong con ky hieu`() {
        val s = VoiceFeedbackPhrase.merge(listOf("✓ Bật Đèn đọc"))
        assertEquals("Đã bật Đèn đọc", s)
    }

    @Test
    fun `dau bang cua bac STEP doc thanh khoang trang`() {
        // `VoiceReply.controlPreview` cho ControlKind.STEP sinh ra "Đặt <nhãn> = <số>".
        val s = VoiceFeedbackPhrase.merge(listOf("✓ Đặt Nhiệt độ = 24"))
        assertEquals("Đã đặt Nhiệt độ 24", s)
        assertFalse(s!!.contains("="), "dấu bằng đọc lên thành \"bằng\" — không phải tiếng Việt nói")
    }

    @Test
    fun `ngoac nhon cua ten rieng bi bo`() {
        val s = VoiceFeedbackPhrase.merge(listOf("✓ Tìm bài «Diễm Xưa» trên YouTube Music"))
        assertNotNull(s)
        assertFalse(s!!.contains("«") || s.contains("»"), "máy đọc phát âm hoặc nuốt luôn cả cụm trong ngoặc nhọn")
    }

    @Test
    fun `gach ngang dai thanh dau phay`() {
        val s = VoiceFeedbackPhrase.merge(listOf("✗ Mở Cốp — xe không nhận lệnh"))
        assertNotNull(s)
        assertFalse(s!!.contains("—"), "gạch ngang dài thành một quãng lặng vô nghĩa")
        assertTrue(s.contains(","), "chỗ ngắt hơi phải là dấu phẩy")
    }

    // ══ (2) GỘP — và cái KHÔNG được gộp ═══════════════════════════════════════════════════════════════════

    @Test
    fun `cau ghep hai ve gop thanh mot cau — dung vi du cua owner`() {
        val s = VoiceFeedbackPhrase.merge(listOf("✓ Đặt Nhiệt độ = 24", "✓ Đặt Quạt gió = 3"))
        assertEquals("Đã đặt Nhiệt độ 24, đặt Quạt gió 3", s)
    }

    @Test
    fun `co ve HONG thi cau mo dau bang ve hong, khong bao gio mo dau bang Da`() {
        val s = VoiceFeedbackPhrase.merge(
            listOf("✓ Đặt Nhiệt độ = 24", "✗ Mở Cốp — xe không nhận lệnh"),
        )
        assertNotNull(s)
        assertTrue(s!!.startsWith("Chưa "), "mở đầu bằng \"Đã\" là nói dối: người lái nghe \"đã\" rồi thôi nhìn màn")
        assertTrue(s.contains("1 việc khác đã xong"), "phải nói ra số việc đã chạy, không im lặng bỏ qua")
    }

    @Test
    fun `chi co ve hong thi khong co duoi dem viec`() {
        val s = VoiceFeedbackPhrase.merge(listOf("✗ Mở Cốp — xe không nhận lệnh"))
        assertEquals("Chưa mở Cốp, xe không nhận lệnh", s)
    }

    @Test
    fun `cau khong mang dau — vd khong hieu — doc nguyen van`() {
        val s = VoiceFeedbackPhrase.merge(listOf("Chưa có câu lệnh nào"))
        assertEquals("Chưa có câu lệnh nào", s)
    }

    // ══ (3) DÒNG TẠM KHÔNG ĐƯỢC ĐỌC ══════════════════════════════════════════════════════════════════════

    @Test
    fun `dong dang tra khong doc, nhung khong nuot mat cau ket qua`() {
        // Đúng cặp mà `VoiceDispatcher.navigate` phát ra: "đang tra…" trước, kết quả sau.
        val lines = listOf("Dẫn đường tới chợ bến thành — đang tra điểm đến…", "✓ Dẫn đường tới Chợ Bến Thành")
        val s = VoiceFeedbackPhrase.merge(lines)
        assertNotNull(s)
        assertFalse(s!!.contains("đang tra"), "dòng tạm hết hạn trước cả lúc nó đọc xong")
        assertTrue(s.contains("Chợ Bến Thành"))
    }

    @Test
    fun `chi co dong tam thi khong doc gi ca`() {
        assertNull(VoiceFeedbackPhrase.merge(listOf("Dẫn đường tới X — đang tra điểm đến…")))
    }

    @Test
    fun `khong co dong nao thi khong doc gi ca`() {
        assertNull(VoiceFeedbackPhrase.merge(emptyList()))
        assertNull(VoiceFeedbackPhrase.merge(listOf("", "   ")))
    }

    // ══ (4) TRẦN SỐ TỪ ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `nhieu viec xong qua tran thi noi SO VIEC, khong doc het`() {
        val lines = (1..6).map { "✓ Bật Đèn đọc hàng ghế sau bên trái số $it" }
        val s = VoiceFeedbackPhrase.merge(lines)
        assertEquals("Đã xong 6 việc", s)
    }

    @Test
    fun `ve hong dai van duoc doc, chi bi cat o ranh gioi tu`() {
        val long = "✗ " + (1..30).joinToString(" ") { "từ$it" }
        val s = VoiceFeedbackPhrase.merge(listOf(long))
        assertNotNull(s)
        assertTrue(s!!.startsWith("Chưa "), "vế hỏng LUÔN phải được nói ra — nó là thứ người lái cần biết")
        assertTrue(
            s.split(' ').count { it.isNotBlank() } <= VoiceFeedbackPhrase.MAX_WORDS + 1,
            "quá trần mà không cắt ⇒ câu 30 từ đọc giữa lúc lái xe",
        )
    }

    // ══ (4b) [SOÁT chuỗi-lời-đáp 2026-09-17] BỐN LỖI ĐỌC ĐƯỢC PHÁT HIỆN BẰNG CÁCH ĐỌC CHUỖI THẬT ═════════
    //
    // Cả bốn đều compile xanh và đều **chỉ nghe thấy được**, không nhìn thấy: chuỗi trên tấm chữ vẫn đúng.

    /**
     * [VoiceReply.doneActual] tự mang lời dẫn *"Đã gửi…"* ⇒ [VoiceFeedbackPhrase.merge] **không** được ghép thêm
     * một chữ *"Đã"* nữa. Trước bản vá: *"Đã **đã** gửi Nhiệt độ 24, xe báo 23"*.
     */
    @Test
    fun `dong da mang loi dan Da thi khong bi ghep them mot lan nua`() {
        val s = VoiceFeedbackPhrase.merge(listOf("✓ Đã gửi Nhiệt độ 24 — xe báo 23"))
        assertEquals("Đã gửi Nhiệt độ 24, xe báo 23", s)
        assertFalse(s!!.contains("Đã đã", ignoreCase = true), "lời dẫn bị ghép hai lần")
    }

    /**
     * ═══ [SOÁT 1.69 · P2] …và luật ấy áp cho CẢ ca NHIỀU dòng ═══════════════════════════════════════════
     *
     * KDoc `hasDoneLead` hứa kiểm bằng **nội dung** — *"bất kỳ dòng nào mở đầu bằng chính lời dẫn ấy"*. Nhưng
     * tới lượt soát này phép kiểm chỉ chạy trên `ok.singleOrNull()`, nên hai vế mà một vế là
     * [VoiceReply.doneActual] vẫn đọc ra *"Đã **đã** gửi Nhiệt độ 24, bật Đèn đọc"*. Gỡ hai dòng `if
     * (hasDoneLead(b)) b.substring(lead.length)` trong [VoiceFeedbackPhrase.merge] ⇒ bài này đỏ ngay.
     *
     * Ca này hôm nay khó tới (dòng đọc-lại của `VoiceDispatcher` về SAU `flushed` nên đi đường một-dòng) —
     * đó đúng là lý do phải khoá bằng bài kiểm: một bẫy chờ sẵn không tự kêu lên bao giờ.
     */
    @Test
    fun `nhieu dong ma mot dong da mang loi dan thi cung khong ghep hai lan`() {
        val s = VoiceFeedbackPhrase.merge(listOf("✓ Đã gửi Nhiệt độ 24 — xe báo 23", "✓ Bật Đèn đọc"))
        assertFalse(s!!.contains("Đã đã", ignoreCase = true), "lời dẫn bị ghép hai lần: «$s»")
        assertTrue(s.startsWith("Đã "), "vẫn phải có ĐÚNG MỘT lời dẫn cho cả câu: «$s»")
    }

    /**
     * MỘT dòng quá trần thì **cắt theo từ**, KHÔNG lùi về *"Đã xong 1 việc"*.
     *
     * Câu đếm việc nói ít hơn hẳn dòng gốc khi chỉ có một việc — và đúng thứ nó nuốt mất là **hai con số** mà
     * `doneActual` sinh ra để đọc. Bài `nhieu viec xong qua tran…` ở trên giữ nguyên hành vi cho ca nhiều việc.
     */
    @Test
    fun `mot dong qua tran thi cat theo tu, khong lui ve cau dem viec`() {
        val one = "✓ Đã gửi Đèn đọc hàng ghế sau bên trái phía trong cùng 24 — xe báo 23"
        val s = VoiceFeedbackPhrase.merge(listOf(one))
        assertNotNull(s)
        assertFalse(s!!.startsWith("Đã xong"), "một việc mà nói \"Đã xong 1 việc\" là nói ít hơn chính dòng gốc")
        assertTrue(s.contains("Đã gửi"), "phần ĐẦU (việc + con số đã gửi) phải sống sót: «$s»")
        assertTrue(
            s.split(' ').count { it.isNotBlank() } <= VoiceFeedbackPhrase.MAX_WORDS + 1,
            "quá trần mà không cắt ⇒ câu dài đọc giữa lúc lái xe",
        )
    }

    /**
     * Vế HỎNG của nhạc phải ra một câu có **động từ**.
     *
     * Trước bản vá `mediaPreview` trả cụm danh từ *"Bài tiếp theo"* ⇒ câu đọc ra *"Chưa bài tiếp theo, chưa có
     * phiên nhạc nào"*. Bài canh đi qua **chính** [VoiceReply.failed] chứ không dựng chuỗi tay — chuỗi tay thì
     * sửa ở `VoiceReply` xong bài này vẫn xanh.
     */
    @Test
    fun `ve hong cua nhac ra cau co dong tu`() {
        listOf(VoiceMediaOp.NEXT, VoiceMediaOp.PREV).forEach { op ->
            val line = VoiceReply.failed(VoiceIntent.Media(op), "chưa có phiên nhạc nào")
            val s = VoiceFeedbackPhrase.merge(listOf(line))
            assertNotNull(s, "$op")
            assertTrue(s!!.startsWith("Chưa "), "$op: «$s»")
            val afterLead = s.removePrefix("Chưa ").substringBefore(' ')
            assertTrue(
                afterLead in setOf("chuyển", "quay"),
                "$op: sau \"Chưa \" phải là ĐỘNG TỪ, đang là «$afterLead» — câu ra: «$s»",
            )
        }
    }

    /**
     * [VoiceFeedbackPhrase.decap] **rồi** [TtsPronunciation.spellOut] — cặp hai tầng, hai module.
     *
     * Nhãn `powertrain_mode` là *"EV / HEV"*. Hạ chữ đầu cho ra `"eV"`, mà `spellOut` chỉ nhận chuỗi toàn HOA ⇒
     * chữ viết tắt trượt khỏi đường đánh vần và Piper đọc thô. Không bên nào một mình thấy được lỗi này.
     */
    @Test
    fun `decap roi spellOut van danh van duoc chu viet tat`() {
        val decapped = VoiceFeedbackPhrase.decap("EV / HEV")
        assertEquals("EV / HEV", decapped, "chữ viết tắt KHÔNG được hạ chữ đầu")
        assertEquals("e vê", TtsPronunciation.spellOut(decapped.substringBefore(' ')))
        // …và luật cũ vẫn nguyên cho từ thường.
        assertEquals("bật đèn đọc", VoiceFeedbackPhrase.decap("Bật đèn đọc"))
    }

    // ══ (5) SONG NGỮ — câu đọc đi theo ngôn ngữ đang chọn ═════════════════════════════════════════════════

    @Test
    fun `ban tieng Anh khong con chu tieng Viet nao`() {
        Strings.current = Lang.EN
        val s = VoiceFeedbackPhrase.merge(listOf("✓ Turn on Reading light"))
        assertNotNull(s)
        assertFalse(s!!.startsWith("Đã"), "máy English mà nghe \"Đã\" là lỗi i18n, không phải lỗi giọng")
    }
}
