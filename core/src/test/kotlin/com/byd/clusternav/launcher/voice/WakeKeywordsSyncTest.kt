package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.96 R11 · WAKE-RECOPY-LOOP — khoá lỗi [ĐO log xe 07/10]: mỗi lần nổ máy xoá + chép lại 5 MB + nạp lại bộ nghe vì so đĩa
 * với bảng GHIM (650 B) trong khi bản chép từ APK là 348 B ⇒ không bao giờ khớp.
 */
class WakeKeywordsSyncTest {

    private val shipped = "▁HE Y ▁CA CH I :2.5 #0.15 @HeyKachi\n".toByteArray()
    private val pinOnly = "▁HE Y ▁CA CH I :1.8 #0.25 @HeyKachi\n".toByteArray()

    @Test
    fun `dia khop ban APK thi KHONG chep lai du lech ban ghim`() {
        assertFalse(WakeKeywordsSync.needsRecopy(shipped.copyOf(), shipped) { false })
    }

    @Test
    fun `dia cu sai vocab sau nang cap APK thi chep lai - ca 09-21 van bat duoc`() {
        assertTrue(WakeKeywordsSync.needsRecopy(pinOnly, shipped) { true })
    }

    @Test
    fun `dia thieu tep thi chep lai`() {
        assertTrue(WakeKeywordsSync.needsRecopy(null, shipped) { true })
    }

    @Test
    fun `khong doc duoc ban APK thi giu luat cu so ghim`() {
        assertFalse(WakeKeywordsSync.needsRecopy(pinOnly, null) { true })
        assertTrue(WakeKeywordsSync.needsRecopy(shipped, null) { false })
    }

    @Test
    fun `doc duoc ban APK thi khong hoi ghim`() {
        var asked = false
        WakeKeywordsSync.needsRecopy(shipped, shipped) { asked = true; false }
        assertFalse(asked)
    }
}
