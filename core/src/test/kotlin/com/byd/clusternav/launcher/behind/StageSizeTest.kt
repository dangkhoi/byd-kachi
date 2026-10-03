package com.byd.clusternav.launcher.behind

import com.byd.clusternav.launcher.behind.StageSize.Verdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Soát vòng 3 [P3] — luật đọc dòng `stage create … phys=PxQ` trên xe. Bản cũ *"WxH ≠ PxQ ⇒ phải sửa"* đọc màn XOAY (cỡ logic hoán
 * W/H — `DisplayContent.updateDisplayAndOrientation`; `Display.Mode` không xoay — r34 `Display.java:1750-1761`) thành bằng chứng
 * "cỡ đi theo maxBounds". Bảng viết tay; số 1920×1080 (tấm nền ngang), 1080×1920 (xoay), 1920×720 (cỡ cụm minh hoạ).
 */
class StageSizeTest {

    @Test
    fun `xoay KHONG phai lech - wm size hay co cum moi la lech can doi chieu`() {
        data class Row(val w: Int, val h: Int, val pw: Int?, val ph: Int?, val want: Verdict)
        listOf(
            Row(1920, 1080, 1920, 1080, Verdict.SAME),
            Row(1080, 1920, 1920, 1080, Verdict.ROTATED),          // màn xoay dọc — luật cũ báo "phải sửa"
            Row(1280, 720, 1920, 1080, Verdict.DIFFERENT),          // `wm size 1280x720` — lệch nhưng KHÔNG phải maxBounds
            Row(1920, 720, 1920, 1080, Verdict.DIFFERENT),          // cỡ cụm — chỉ ca này (đối chiếu xong) mới là bằng chứng
            Row(1920, 1080, null, null, Verdict.UNKNOWN),
            Row(1920, 1080, 0, 1080, Verdict.UNKNOWN),
        ).forEach { r -> assertEquals(r.want, StageSize.verdict(r.w, r.h, r.pw, r.ph), "$r") }
    }

    @Test
    fun `nhan trong log khong co dau cach - grep duoc tren xe`() {
        Verdict.entries.forEach { v -> assertEquals(false, v.tag.contains(' '), v.tag) }
        assertEquals("xoay", Verdict.ROTATED.tag)
    }
}
