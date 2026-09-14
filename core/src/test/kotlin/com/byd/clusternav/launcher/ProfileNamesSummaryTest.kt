package com.byd.clusternav.launcher

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Owner 2026-09-14: "chưa thấy hồ sơ nó gắn với bố cục chỗ nào?" — câu tóm tắt bố cục đặt cạnh tên hồ sơ. */
class ProfileNamesSummaryTest {

    /**
     * ⚠ TỰ DỌN [Strings.current] sau MỌI bài — kể cả bài không chạm nó, để không phải nhớ bài nào có chạm. Cùng luật
     * (và cùng lý do) với `LangCoverageTest.dọn`: `var` toàn cục rò từ bài này sang bài khác là họ lỗi *"chạy một mình
     * thì xanh, chạy cả gói thì đỏ"*.
     */
    @AfterEach
    fun `don`() {
        Strings.current = Lang.VI
    }

    @Test
    fun `bo cuc san + so o co noi dung`() {
        Strings.current = Lang.VI
        assertEquals("2 cột · 3 ô có nội dung", ProfileNames.summary(LayoutPreset.TWO_COL, 3))
    }

    @Test
    fun `bo cuc tu ve khi preset null`() {
        Strings.current = Lang.VI
        assertTrue(ProfileNames.summary(null, 0).startsWith("Tự vẽ"))
        Strings.current = Lang.EN
        assertEquals("Custom · 0 slots filled", ProfileNames.summary(null, 0))
    }

    /**
     * ⚠ Số ít của tiếng Anh, khoá lại **finding #18** (`layoutSummary` từng in *"1 frames"*): `:core` không có
     * `Context` nên không có `getQuantityString` — phép chia số phải viết tay, và thứ viết tay thì phải có bài canh.
     */
    /** S4 · R8 — hồ sơ nay giữ cả chủ đề và cờ tự mở, câu tóm tắt phải nói ra. */
    @Test
    fun `S4 - them chu de va tu mo vao cau tom tat`() {
        Strings.current = Lang.VI
        assertEquals(
            "1 ô · 1 ô có nội dung · Tối · Tự mở",
            ProfileNames.summary(LayoutPreset.ONE, 1, ThemeMode.NIGHT, autostart = true),
        )
        Strings.current = Lang.EN
        assertEquals(
            "1 slot · 1 slot filled · Light",
            ProfileNames.summary(LayoutPreset.ONE, 1, ThemeMode.DAY, autostart = false),
            "tự mở TẮT thì không nói gì — thêm 'No auto-start' vào mọi thẻ chỉ làm loãng câu",
        )
    }

    /** `null` = **chưa biết** (chỗ gọi chưa đọc được), khác hẳn "biết và bằng mặc định" ⇒ không bịa ra chữ nào. */
    @Test
    fun `khong truyen chu de hay tu mo thi cau tom tat KHONG doi`() {
        Strings.current = Lang.VI
        assertEquals(
            ProfileNames.summary(LayoutPreset.TWO_COL, 3),
            ProfileNames.summary(LayoutPreset.TWO_COL, 3, theme = null, autostart = null),
        )
    }

    @Test
    fun `mot o co noi dung KHONG in 1 slots`() {
        Strings.current = Lang.EN
        assertEquals("1 slot · 1 slot filled", ProfileNames.summary(LayoutPreset.ONE, 1))
        Strings.current = Lang.VI
        assertEquals("1 ô · 1 ô có nội dung", ProfileNames.summary(LayoutPreset.ONE, 1))
    }
}
