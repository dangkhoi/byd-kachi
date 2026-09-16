package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.LayoutPreset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ L7 · BỐ CỤC BẰNG GIỌNG NÓI — bài canh của [VoiceLayouts] ═══════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **L7** (owner duyệt 2026-09-16). `:core` thuần ⇒ off-car.
 *
 * Phần *"câu nào ra ý định nào"* đã có ở `VoiceIntentParserTest`; bài này khoá ba thứ mà bài kia không nói tới:
 * **độ phủ enum** (thêm một bố cục mà quên khai cách nói ⇒ đỏ), **luật tiền tố** của cụm đánh dấu, và **hợp đồng
 * với tầng nghe** (cách nói phải viết số bằng CHỮ, và mọi từ phải được khai với bộ nhận dạng).
 */
class VoiceLayoutParseTest {

    private fun match(s: String): LayoutPreset? = VoiceLayouts.match(VoiceLexicon.tokenize(s))

    /**
     * ⚠⚠ **Độ phủ**: mọi giá trị của [LayoutPreset] phải nói được.
     *
     * Đây là bài chặn đúng cái bẫy mà KDoc [VoiceLayouts] mô tả: bảng cách nói là 5 dòng **chép tay** (enum
     * không mang thông tin cột/hàng), nên thêm một bố cục thứ sáu mà quên khai ở đó sẽ **im lặng** — người dùng
     * thấy nó trong Cài đặt mà nói thì máy bảo không hiểu.
     */
    @Test
    fun `moi bo cuc deu co it nhat mot cach noi`() {
        val reachable = VoiceLayouts.SPOKEN.mapNotNull { match(it) }.toSet()
        LayoutPreset.values().forEach { p ->
            assertTrue(p in reachable, "bố cục ${p.name} (${p.label}) không có cách nói nào — khai vào VoiceLayouts.SPECS")
        }
    }

    /** Cụm đánh dấu là điều kiện CẦN: không có nó thì lớp này im lặng, không đoán. */
    @Test
    fun `khong co cum danh dau thi khong khop gi`() {
        assertNull(match("hai cột"))
        assertNull(match("bốn ô"))
        assertNull(match("chuyển sang hai hàng"))
    }

    /** Phần đuôi phải khớp TRỌN — thừa một từ là `null`, không cắt bừa. */
    @Test
    fun `duoi phai khop tron`() {
        assertNotNull(match("bố cục hai cột"))
        assertNull(match("bố cục hai cột màu xanh"))
        assertNull(match("bố cục"))
        assertNull(match("bố cục mười hai ô"))
    }

    /** Từ đứng TRƯỚC cụm đánh dấu phải thuộc danh sách đóng — nếu không, câu đó là một câu khác. */
    @Test
    fun `tu dung truoc cum danh dau phai thuoc danh sach dong`() {
        assertEquals(LayoutPreset.QUAD, match("đổi sang bố cục 4 ô"))
        assertEquals(LayoutPreset.QUAD, match("về bố cục 4 ô"))
        assertNull(match("mở bài bố cục hai cột"), "hai chữ 'bố cục' giữa một câu khác không được cướp câu ấy")
        assertNull(match("phát bố cục hai cột"))
    }

    /**
     * Hợp đồng với tầng NGHE — hai vế, và vế thứ hai là thứ im lặng nhất nếu sai.
     *
     * 1. Cách nói phải viết số bằng **CHỮ**: [SherpaHotwords.normalize] bỏ mọi token mang chữ số, nên
     *    *"BỐ CỤC 2 CỘT"* sẽ rụng con số và thành *"BỐ CỤC CỘT"* — một cụm không ai nói, chiếm chỗ trong tệp
     *    hotwords mà không bao giờ khớp.
     * 2. Mọi từ trong [VoiceLayouts.SPOKEN] phải nằm trong [VoiceLayouts.WORDS] (tập khai với bộ nhận dạng).
     *    Thiếu một từ ở đó thì câu **gõ được mà không nói được**, và cái thiếu ấy không làm gì đỏ.
     */
    @Test
    fun `cach noi hop dong duoc voi tang nghe`() {
        VoiceLayouts.SPOKEN.forEach { phrase ->
            assertTrue(phrase.none { it.isDigit() }, "cách nói `$phrase` còn chữ số — tầng hotword sẽ bỏ token đó")
            VoiceLexicon.tokenize(phrase).forEach { t ->
                assertTrue(t.norm in VoiceLayouts.WORDS || VoiceLexicon.NUMBER_WORDS.contains(t.norm),
                    "từ `${t.raw}` của `$phrase` không được khai với bộ nhận dạng (VoiceLayouts.WORDS)")
            }
        }
    }

    /**
     * Luật **tiền tố** của tệp hotwords: *"BỐ CỤC"* trần là tiền tố của năm cụm dài hơn nên nó phải RỤNG, còn
     * năm cụm dài thì phải ở lại.
     *
     * [ĐO] 2026-09-16 (KDoc [SherpaPhraseHotwords]): khớp trọn một hotword là đồ thị **về gốc**, nên để lại cụm
     * ngắn là cụm dài không bao giờ cộng đủ điểm — đúng bệnh đã đo với *"CHẾ ĐỘ LÁI"* vs *"CHẾ ĐỘ LÁI THỂ THAO"*.
     */
    @Test
    fun `bo cuc tran rung khoi tep hotwords, cum dai o lai`() {
        val file = SherpaHotwords.phraseFile(SherpaPhraseHotwords.phrases()).lines()
        assertTrue("BỐ CỤC HAI CỘT" in file, "cụm dài phải có trong tệp hotwords")
        assertTrue("BỐ CỤC BỐN Ô" in file, "cụm dài phải có trong tệp hotwords")
        assertTrue("BỐ CỤC" !in file, "cụm trần là tiền tố ⇒ phải rụng (luật dropPrefixes)")
    }
}
