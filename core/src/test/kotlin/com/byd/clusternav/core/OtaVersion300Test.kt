package com.byd.clusternav.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 3.0 — bản kế của 2.99 là "3.0" (owner 10/10), KHÔNG "2.100" ⇒ mọi bản ĐANG chạy trên xe phải thấy 3.0 là MỚI HƠN ═══════════════
 *
 * Kênh OTA chỉ lộ tên tệp `apk/Kachi-<ver>-release.apk`; hộp *"Có bản mới"* (`UpdateChecker`) và chấm cập nhật ([UpdateDot]) so bằng
 * [OtaVersion.cmp] — so TỪNG KHÚC SỐ ([3, 0] vs [2, 99] ⇒ 3 > 2). Bài khoá: (1) luật hiện hành; (2) luật đã đăng ở 2.89…2.98
 * (`numericPrefix`, nguyên văn — [OtaVersion] 2.98 `420f16b` giống hệt bản này, đã `git diff`); (3) luật ≤ 2.88 (`split('.')` +
 * `toIntOrNull() ?: 0`, KDoc [OtaVersion.cmp]) — xe nào còn bản cũ cũng được mời lên 3.0.
 */
class OtaVersion300Test {

    @Test
    fun `3_0 moi hon 2_99, 2_98 va ban thu 2_99-thu1`() {
        listOf("2.99", "2.98", "2.97", "2.85", "2.99-thử1", "2.9").forEach { old ->
            assertTrue(OtaVersion.cmp("3.0", old) > 0, "3.0 phải mới hơn $old")
            assertTrue(OtaVersion.offers("3.0", old), "kênh 3.0 phải mời bản $old")
            assertFalse(OtaVersion.offers(old, "3.0"), "không bao giờ mời hạ từ 3.0 về $old")
        }
        assertEquals(0, OtaVersion.cmp("3.0", "3"))
        assertFalse(OtaVersion.offers("3.0", "3.0"))
        assertTrue(OtaVersion.offers("3.0", "3.0-thử1"), "bản thử cùng số ⇒ mời bản chính thức")
    }

    @Test
    fun `cham co ban moi - 2_99 (202) thay 3_0, cai 3_0 (203) thi tat`() {
        val set = UpdateDot.afterCheck("3.0", errored = false, installedName = "2.99", installedVersionCode = 202)
        assertTrue(set is UpdateDot.Action.Set)
        val rec = (set as UpdateDot.Action.Set).record
        assertTrue(UpdateDot.shows(rec, "2.99", 202))
        assertTrue(UpdateDot.shows(UpdateDot.Record("3.0", 201), "2.98", 201))
        assertFalse(UpdateDot.shows(rec, "3.0", 203), "đã cài 3.0 ⇒ chấm tắt")
        assertFalse(UpdateDot.stillValid(rec, "3.0", 203))
    }

    /** Luật ≤ 2.88 (nguyên văn KDoc [OtaVersion.cmp]): `split('.')` + `toIntOrNull() ?: 0`, so từng khúc. */
    @Test
    fun `luat cu le 2_88 cung thay 3_0 moi hon`() {
        fun legacy(a: String, b: String): Int {
            val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
            val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val d = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
                if (d != 0) return d
            }
            return 0
        }
        listOf("2.88", "2.85", "2.73", "1.99").forEach { assertTrue(legacy("3.0", it) > 0, "luật cũ: 3.0 > $it") }
        // Ca "2.100" mà owner bỏ: theo cùng luật 2.100 > 2.99 (100 > 99) — đổi sang 3.0 không phải vì lỗi so sánh, mà là quyết định tên.
        assertTrue(OtaVersion.cmp("2.100", "2.99") > 0)
    }
}
