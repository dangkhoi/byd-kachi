package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * "Hey Kachi" KHÔNG-train ([WakeAsrMatcher]): nổ đúng câu gọi, KHÔNG nổ câu thường/dài. Off-car (chuỗi giả — đây
 * là text mà model NGHE tiếng Việt phát ra, không phải mic).
 */
class WakeAsrMatcherTest {

    @Test fun `no cac bien the goi kachi`() {
        listOf(
            "kachi", "ka chi", "ca chi", "ga chi", "kachi ơi", "ok kachi",
            "hey kachi", "hi ca chi", "kachi oi", "ok ca chi",
        ).forEach { assertTrue(WakeAsrMatcher.isWake(it), "phải nổ: \"$it\"") }
    }

    @Test fun `KHONG no cau thuong hoac dai`() {
        listOf(
            "hôm nay trời đẹp quá", "bật đèn đọc", "mở kính lái", "cho tôi nghe nhạc",
            "", "ừ", "cái gì đó tôi không biết nói dài dòng lắm luôn", "chào buổi sáng",
            // [P1 fix 2026-09-24] câu thường mở đầu hay/này/ok + fragment yếu — luật (f)/(g) KHÔNG được nổ.
            "này chị ơi", "ok chị", "hoặc chỉ", "khá hay", "hai quá trình", "ok anh", "này em", "hay là các bạn",
            // [ĐO harness 16/10] "co chi" từng là ca NỔ giả định (chưa từng đo được câu gọi nào ra "co") — nó chính là "có chị" của
            // câu thường; cửa sổ "chỉ có" của TTS dẫn đường cũng vậy. Nguyên âm khác "a" không phải lỗi nghe của "ka".
            "co chi", "có chị", "có chị gọi", "chỉ có", "chỉ có camera", "có ca", "chỉ ca sĩ",
        ).forEach { assertFalse(WakeAsrMatcher.isWake(it), "KHÔNG được nổ: \"$it\"") }
    }

    /**
     * WAKE-INTERMITTENT-1017 [ĐO xe 17/10, log `:wake`]: ba cửa sổ câu gọi ra "hay ca trên" / "hai ca trên" (đuôi "chi" → âm `tr`)
     * và KHÔNG nổ ⇒ người lái bấm phím. Nhận đúng dáng câu gọi ba từ; "trên" trong câu lệnh / câu thường không được nổ.
     */
    @Test fun `duoi tr cua cau goi ba tu no, tren trong cau thuong khong`() {
        listOf("hay ca trên", "hai ca trên", "hey ka trên").forEach { assertTrue(WakeAsrMatcher.isWake(it), "phải nổ: \"$it\"") }
        listOf(
            "ca trên", "trên", "nghe ca trên youtube", "ca trên youtube", "hai ca trên đường", "mở bài ca trên youtube",
            "hai cái trên", "hay có trên", "phía trên", "ở trên kia", "nhạc đen trên youtube", "xin trân",
        ).forEach { assertFalse(WakeAsrMatcher.isWake(it), "KHÔNG được nổ: \"$it\"") }
    }

    /** WAKE-INTERMITTENT-1017 [ĐO xe 17/10 12:11]: chuyện trong xe có chữ "khách" ⇒ nổ nhầm qua luật (a). `kh` ≠ k-start. */
    @Test fun `chu kh cua cau thuong khong no`() {
        // Câu đầu là câu DỰNG (cùng dáng câu xe: ≤ 6 từ, chữ "khách" + đệm) — không chép lời nói trong xe vào fixture.
        listOf("còn khách nào nữa không", "à cái khách là", "khách sạn", "mời khách", "khách hàng đến rồi").forEach {
            assertFalse(WakeAsrMatcher.isWake(it), "KHÔNG được nổ: \"$it\"")
        }
    }

    /** Câu dài (>4 từ) chứa "kachi" giữa cũng KHÔNG nổ — người đang nói chuyện khác, không phải gọi. */
    @Test fun `kachi giua cau dai khong no`() {
        assertFalse(WakeAsrMatcher.isWake("tôi vừa mua con kachi màu đỏ hôm qua"))
    }
}
