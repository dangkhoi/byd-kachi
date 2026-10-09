package com.byd.clusternav.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · OTA-UPDATE-DOT — khoá luật chấm *có bản mới*. Số phiên bản/versionCode lấy theo lịch sử thật của kênh
 * (2.97 (200) đã đăng · 2.98 (201) WIP · bản thử `2.89-thử1` (190) đo máy ảo 05/10).
 */
class UpdateDotTest {

    private val rec299 = UpdateDot.Record("2.99", seenAtVersionCode = 200)

    @Test
    fun `do thay ban moi hon thi ghi ban kenh va versionCode dang cai`() {
        assertEquals(UpdateDot.Action.Set(UpdateDot.Record("2.99", 200)), UpdateDot.afterCheck("2.99", false, "2.97", 200))
    }

    @Test
    fun `do loi mang thi giu ban ghi cu`() {
        assertEquals(UpdateDot.Action.Keep, UpdateDot.afterCheck(null, true, "2.97", 200))
        assertEquals(UpdateDot.Action.Keep, UpdateDot.afterCheck("2.99", true, "2.97", 200))
    }

    @Test
    fun `kenh khong moi thi xoa`() {
        assertEquals(UpdateDot.Action.Clear, UpdateDot.afterCheck("2.97", false, "2.97", 200), "cùng bản")
        assertEquals(UpdateDot.Action.Clear, UpdateDot.afterCheck("2.88", false, "2.89-thử1", 190), "không mời hạ cấp (05/10)")
        assertEquals(UpdateDot.Action.Clear, UpdateDot.afterCheck(null, false, "2.97", 200), "kênh không có APK")
    }

    @Test
    fun `ban thu co duoi thi kenh chinh thuc cung so van moi`() {
        assertEquals(UpdateDot.Action.Set(UpdateDot.Record("2.89", 190)), UpdateDot.afterCheck("2.89", false, "2.89-thử1", 190))
    }

    @Test
    fun `khong doc duoc ban dang cai thi khong ghi cham`() {
        assertEquals(UpdateDot.Action.Clear, UpdateDot.afterCheck("2.99", false, "?", 200))
        assertEquals(UpdateDot.Action.Clear, UpdateDot.afterCheck("2.99", false, "2.97", null))
    }

    @Test
    fun `cham hien khi ban ghi con moi hon ban dang cai`() {
        assertTrue(UpdateDot.shows(rec299, "2.97", 200))
    }

    @Test
    fun `cai xong ban do thi cham tat`() {
        assertFalse(UpdateDot.shows(rec299, "2.99", 202))
        assertFalse(UpdateDot.stillValid(rec299, "2.99", 202))
    }

    @Test
    fun `versionCode dang cai lon hon luc thay thi ban ghi cu`() {
        // Cài tay một bản khác (vd 2.98 (201)) sau khi thấy 2.99 — tên vẫn "cũ hơn" nhưng đã có lượt cài kể từ lúc dò ⇒
        // bản ghi không còn được xác nhận; lượt dò kế (tiến trình mới sau cài) ghi lại nếu kênh vẫn mời.
        assertFalse(UpdateDot.shows(rec299, "2.98", 201))
    }

    @Test
    fun `khong co ban ghi hoac khong doc duoc ban dang cai thi khong cham`() {
        assertFalse(UpdateDot.shows(null, "2.97", 200))
        assertFalse(UpdateDot.shows(rec299, "?", 200))
        assertFalse(UpdateDot.shows(rec299, "2.97", null))
        assertFalse(UpdateDot.shows(UpdateDot.Record("rác", 200), "2.97", 200))
    }

    @Test
    fun `khong doc duoc ban dang cai thi giu ban ghi`() {
        assertTrue(UpdateDot.stillValid(rec299, "?", null))
    }

    @Test
    fun `OtaVersion giu nguyen luat cu`() {
        assertTrue(OtaVersion.cmp("0.56", "0.9") > 0)
        assertEquals(0, OtaVersion.cmp("2.89", "2.89-thử1"))
        assertFalse(OtaVersion.offers("2.88", "2.89-thử1"))
        assertTrue(OtaVersion.offers("2.99", "?"), "hộp thoại: không đọc được bản cài ⇒ hành vi cũ (mời)")
        assertFalse(OtaVersion.readable("?"))
    }
}
