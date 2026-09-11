package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [SOÁT P2-4] Cổng giảm cỡ ảnh — ảnh **tỉ lệ lệch** không được ra bitmap khổng lồ.
 *
 * Phép giảm theo luỹ thừa 2 dừng khi MỘT chiều sắp nhỏ hơn khung, nên chiều còn lại có thể vẫn rất lớn. Bước hạ
 * đúng khung dọn phần dư đó.
 */
class WallpaperScaleTest {

    private fun bytes(w: Int, h: Int) = w * h * 4L   // ARGB_8888

    @Test
    fun `ca that trong bao cao soat - anh 12000x9000`() {
        val s = WallpaperStore.sampleSize(12000, 9000, 1920, 1080)
        val sw = 12000 / s; val sh = 9000 / s
        assertEquals(4, s, "phép giảm luỹ thừa 2 dừng ở 4")
        assertEquals(3000, sw); assertEquals(2250, sh)
        assertTrue(bytes(sw, sh) > 25_000_000L, "chỉ giảm luỹ thừa 2 thì còn ~27 MB")

        val target = WallpaperStore.scaledWidth(sw, sh, 1920, 1080)
        assertTrue(target in 1919..1921, "phải hạ về đúng bề rộng khung, đang $target")
        val th = (sh.toDouble() * target / sw).toInt()
        assertTrue(bytes(target, th) < 13_000_000L,
            "sau khi hạ đúng khung phải dưới 13 MB, đang ${bytes(target, th) / 1_000_000} MB")
        assertTrue(th >= 1080, "vẫn phải TRÙM đủ khung (chế độ phủ kín sẽ cắt), cao $th")
    }

    @Test
    fun `anh nho hon khung thi KHONG ha them`() {
        assertEquals(0, WallpaperStore.scaledWidth(800, 600, 1920, 1080),
            "ảnh đã nhỏ hơn khung thì hạ thêm chỉ làm mờ")
        assertEquals(0, WallpaperStore.scaledWidth(1920, 1080, 1920, 1080), "vừa khít thì không hạ")
    }

    @Test
    fun `luon con TRUM du khung o moi ti le`() {
        // Bất biến quan trọng: hạ xong vẫn phải phủ kín, không được để hở mép.
        //
        // ⚠ [SOÁT] Bản cũ có hai chỗ hỏng: (a) assert đầu dùng `||` nên **một chiều trùm là đủ** — trái hẳn tên bài;
        // (b) assert sau viết `A && B || C && D || (E && F)` mà `&&` ưu tiên cao hơn `||` nên vế giữa là **mã chết**,
        // và dung sai 0.99 cho phép hở tới ~19px trong khi tên bài nói "luôn TRÙM đủ". Nay đòi đúng điều cần đòi:
        // **cả hai chiều** đều ≥ khung, không dung sai.
        listOf(4000 to 3000, 3000 to 4000, 8000 to 1000, 1000 to 8000, 2560 to 1440).forEach { (w, h) ->
            val s = WallpaperStore.sampleSize(w, h, 1920, 1080)
            val sw = w / s
            val sh = h / s
            val t = WallpaperStore.scaledWidth(sw, sh, 1920, 1080)
            if (t > 0) {
                val th = (sh.toDouble() * t / sw).toInt()
                assertTrue(
                    t >= 1920 && th >= 1080,
                    "ảnh ${w}x$h hạ thành ${t}x$th — PHẢI trùm đủ CẢ HAI chiều (1920x1080), không thì hở mép",
                )
            }
        }
    }

    @Test
    fun `so xau khong lam sap`() {
        assertEquals(0, WallpaperStore.scaledWidth(0, 0, 1920, 1080))
        assertEquals(0, WallpaperStore.scaledWidth(100, 100, 0, 0))
        assertEquals(0, WallpaperStore.scaledWidth(-5, -5, 1920, 1080))
    }
}
