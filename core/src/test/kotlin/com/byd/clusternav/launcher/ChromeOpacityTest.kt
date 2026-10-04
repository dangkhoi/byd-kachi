package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * R-OP — số học của độ đục nền chung ([ChromeOpacity]). 2.88 (owner 04/10: *"cho trong suốt lên 100% luôn, tùy user
 * chọn, có thay kéo từ 0-100%"*): thanh kéo 0..100 bước 5, áp ĐÚNG số người chọn (không còn sàn đọc được).
 */
class ChromeOpacityTest {

    /** Khoá dải + bước: 21 vị trí, đầu = hôm nay (trong suốt 0 %), cuối = trong suốt 100 % (độ đục 0). */
    @Test
    fun `thanh keo 0 toi 100 buoc 5, vi tri 0 la hom nay`() {
        assertEquals(100, ChromeOpacity.DEFAULT)
        assertEquals(5, ChromeOpacity.STEP)
        assertEquals(20, ChromeOpacity.POSITIONS)
        val all = (0..ChromeOpacity.POSITIONS).map(ChromeOpacity::ofPosition)
        assertEquals((100 downTo 0 step 5).toList(), all, "vị trí i ⇒ độ đục 100 − 5i")
        assertEquals(ChromeOpacity.DEFAULT, ChromeOpacity.ofPosition(0), "vị trí đầu = hôm nay")
        assertEquals(0, ChromeOpacity.ofPosition(ChromeOpacity.POSITIONS), "vị trí cuối = nền trong hẳn")
        all.forEachIndexed { i, pct ->
            assertEquals(i, ChromeOpacity.position(pct), "position ∘ ofPosition = id ($pct)")
            assertEquals(i * 5, ChromeOpacity.transparencyPct(pct), "số hiện = độ TRONG SUỐT")
        }
        assertEquals(0, ChromeOpacity.ofPosition(99), "kẹp vị trí ngoài dải")
        assertEquals(100, ChromeOpacity.ofPosition(-3))
    }

    /**
     * Khoá luật kẹp: bội của 5 gần nhất trong [0, 100]. Năm bậc 2.87 (100/85/70/55/40) phải về ĐÚNG số cũ — hồ sơ 2.87
     * giữ dáng cũ từng byte sau khi nâng lên 2.88.
     */
    @Test
    fun `snap ve boi cua 5 gan nhat va kep hai dau`() {
        val table = mapOf(
            100 to 100, 85 to 85, 70 to 70, 55 to 55, 40 to 40,       // bậc 2.87 giữ nguyên
            0 to 0, 5 to 5, 1 to 0, 2 to 0, 3 to 5, 4 to 5, 7 to 5, 8 to 10, 42 to 40, 43 to 45, 97 to 95, 98 to 100,
            101 to 100, 999 to 100, -1 to 0, -50 to 0, Int.MIN_VALUE to 0, Int.MAX_VALUE to 100,
        )
        table.forEach { (pct, want) -> assertEquals(want, ChromeOpacity.snap(pct), "snap($pct)") }
        (-20..130).forEach { pct ->
            val s = ChromeOpacity.snap(pct)
            assertTrue(s in 0..100 && s % 5 == 0, "snap($pct) = $s ngoài lưới")
            assertTrue(abs(s - pct.coerceIn(0, 100)) <= 2, "snap($pct) = $s không phải bội gần nhất")
        }
        assertEquals(0.45, ChromeOpacity.fraction(44), 1e-12, "fraction đi qua snap")
        assertEquals(1.0, ChromeOpacity.fraction(100), 0.0)
        assertEquals(0.0, ChromeOpacity.fraction(0), 0.0)
    }

    /** Khoá đường tắt "hôm nay": hệ số ≥ 1 trả ĐÚNG alpha gốc ở mọi alpha (0 % trong suốt = không đổi một byte). */
    @Test
    fun `he so 1 tra dung alpha goc, khong qua phep tinh nao`() {
        assertEquals(255, ChromeOpacity.alphaByte(1.0))
        assertEquals(255, ChromeOpacity.alphaByte(1.5))
        assertEquals(0, ChromeOpacity.alphaByte(0.0))
        assertEquals(0, ChromeOpacity.alphaByte(-0.2))
        (0..255).forEach { a ->
            assertEquals(a, ChromeOpacity.drawnAlpha(a, 1.0), "alpha $a ở 100 %")
            assertEquals(a, ChromeOpacity.drawnAlpha(a, 1.5))
        }
    }

    /** Khoá phép nhân: đúng `GradientDrawable.modulateAlpha` của AOSP r47 — số đo nói về cái được VẼ. */
    @Test
    fun `alpha ve ra theo dung phep nhan modulateAlpha`() {
        assertEquals(102, ChromeOpacity.alphaByte(0.4))
        assertEquals((0xcc * (102 + 0)) shr 8, ChromeOpacity.drawnAlpha(0xcc, 0.4))
        assertEquals((255 * (217 + 1)) shr 8, ChromeOpacity.drawnAlpha(255, 0.85))
        assertEquals(0, ChromeOpacity.drawnAlpha(255, 0.0), "100 % trong suốt ⇒ alpha 0 — không vẽ gì")
    }

    /**
     * 2.88 — ÁP ĐÚNG SỐ NGƯỜI CHỌN (thay bất biến sàn của 2.87). Ở MỌI vị trí thanh kéo × MỌI alpha gốc: không bao giờ
     * ĐỤC hơn gốc, đơn điệu theo vị trí (kéo thêm thì không bao giờ đục lại), và lệch phép nhân đúng `gốc × f` không quá
     * 2/255 (chỉ là làm tròn của `modulateAlpha`, không còn mức "cần để đọc" nào kéo lên). Vị trí cuối ⇒ 0 với mọi gốc.
     */
    @Test
    fun `do duc ve ra dung goc nhan he so, don dieu, khong bao gio duc hon goc`() {
        var worst = 0
        (0..255).forEach { base ->
            var prev = Int.MAX_VALUE
            (0..ChromeOpacity.POSITIONS).forEach { pos ->
                val f = ChromeOpacity.fraction(ChromeOpacity.ofPosition(pos))
                val drawn = ChromeOpacity.drawnAlpha(base, f)
                assertTrue(drawn <= base, "đục hơn gốc: base=$base f=$f drawn=$drawn")
                assertTrue(drawn <= prev, "không đơn điệu: base=$base pos=$pos drawn=$drawn prev=$prev")
                val err = abs(drawn - base * f)
                worst = maxOf(worst, kotlin.math.ceil(err).toInt())
                assertTrue(err <= 2.0, "lệch phép nhân base×f quá 2/255: base=$base f=$f drawn=$drawn")
                prev = drawn
            }
            assertEquals(0, ChromeOpacity.drawnAlpha(base, ChromeOpacity.fraction(ChromeOpacity.ofPosition(ChromeOpacity.POSITIONS))))
        }
        assertTrue(worst >= 1, "bài đo phải thật sự chạm làm tròn (worst=$worst)")
    }
}
