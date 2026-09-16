package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ TIẾNG ẬM Ừ — cùng một chữ, HAI VAI, và thứ tự xét là thứ giữ cả hai ═════════════════════════════════════
 *
 * Hai phép đo trên **xe thật** 2026-09-16 (DL3, bản 1.68) gặp nhau ở đúng chữ `ừ`:
 *  • `"ừ bật đèn đọc"` — tiếng ậm ừ mở đầu lọt vào chuỗi chữ; `u` không phải tiếng đệm nên cả câu rơi vào
 *    `NO_VERB`: một lệnh nói đúng, hiểu sai;
 *  • mô hình *ảo giác* trong lúc gần như im lặng, in ra `"ừ"` **102 lần** và `"ừm"` **102 lần** liên tiếp —
 *    mỗi chuỗi được coi là một lượt nói sẽ mở lại một vòng hội thoại (vòng lặp chạy mãi khi đang lái).
 *
 * Nhưng owner cũng đã chốt (cùng ngày) rằng một tiếng `"ừ"` đứng một mình **là** câu ĐỒNG Ý cho hộp xác nhận.
 * Nên bài này khoá cả hai vai **và** thứ tự xét giữ chúng: [VoiceLexicon.confirmAnswer] soi bản CHƯA lọc trước.
 */
class VoiceFillerAndConfirmTest {

    @Test
    fun `u va um la tieng dem — cau lenh mo dau bang chung van hieu dung`() {
        assertTrue("u" in VoiceLexicon.FILLERS && "um" in VoiceLexicon.FILLERS)
        listOf("ừ bật đèn đọc", "ừm bật đèn đọc", "ừ, bật đèn đọc").forEach {
            val got = VoiceIntentParser.parseOne(it)
            assertTrue(got is VoiceIntent.Control && got.id == "readl" && got.value == 1, "«$it» ra: $got")
        }
    }

    @Test
    fun `u van la cau DONG Y — tieng dem KHONG duoc nuot mat cong xac nhan`() {
        assertEquals(true, VoiceLexicon.confirmAnswer("ừ"))
        assertEquals(true, VoiceLexicon.confirmAnswer("Ừ"))
        assertEquals(true, VoiceLexicon.confirmAnswer("đồng ý"))
        assertEquals(false, VoiceLexicon.confirmAnswer("huỷ"))
        // Cả câu phải ĐÚNG BẰNG một cụm: một tiếng ậm ừ lẫn trong câu dài vẫn KHÔNG phải câu trả lời.
        assertNull(VoiceLexicon.confirmAnswer("ừ bật đèn đọc"))
        assertNull(VoiceLexicon.confirmAnswer(""))
    }

    @Test
    fun `isFillerOnly nhan ra chuoi khong mang nghia nao`() {
        listOf("", "   ", "ừ", "ừm", "ừ ừm", "kachi ơi", "...").forEach {
            assertTrue(VoiceLexicon.isFillerOnly(it), "«$it» phải là chuỗi không mang nghĩa")
        }
        listOf("bật đèn đọc", "ừ bật đèn", "pin", "lọc").forEach {
            assertFalse(VoiceLexicon.isFillerOnly(it), "«$it» có chữ mang nghĩa, không được coi là rỗng")
        }
    }

    /**
     * Chốt thứ tự bằng máy: hai phép hỏi phải **cùng đúng** cho `"ừ"` — nó vừa là câu ĐỒNG Ý vừa là chuỗi toàn
     * tiếng đệm. Chỗ gọi phải hỏi [VoiceLexicon.confirmAnswer] TRƯỚC; bài này giữ tiền đề ấy còn thật.
     */
    @Test
    fun `u vua la DONG Y vua la toan tieng dem — chinh vi the thu tu hoi moi quan trong`() {
        assertEquals(true, VoiceLexicon.confirmAnswer("ừ"))
        assertTrue(VoiceLexicon.isFillerOnly("ừ"))
    }
}
