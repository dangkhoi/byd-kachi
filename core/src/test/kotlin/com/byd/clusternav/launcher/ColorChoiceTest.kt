package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** P1b · R8 — lưu bền lựa chọn màu: đi vòng tròn, rác ⇒ mặc định, nhãn đủ và khác nhau. */
class ColorChoiceTest {

    @Test
    fun `encode decode di vong tron cho moi to hop`() {
        AccentChoice.values().forEach { a ->
            CardTone.values().forEach { t ->
                val c = ColorChoice(a, t)
                assertEquals(c, ColorChoice.decode(c.encode()), c.encode())
            }
        }
    }

    @Test
    fun `chuoi cu co truong du paint va model van doc duoc, khong sap`() {
        // WP3-v5 gỡ paint/model; cấu hình đã lưu trên xe còn dạng cũ `ACCENT;TONE;paint;model` ⇒ bỏ qua trường dư.
        assertEquals(ColorChoice(AccentChoice.VIOLET, CardTone.WARM), ColorChoice.decode("VIOLET;WARM;pearl"))
        assertEquals(ColorChoice(AccentChoice.VIOLET, CardTone.WARM), ColorChoice.decode("VIOLET;WARM;pearl;sealion6"))
    }

    @Test
    fun `thieu hoac rac thi ve mac dinh tung phan, khong sap`() {
        assertEquals(ColorChoice.DEFAULT, ColorChoice.decode(null))
        assertEquals(ColorChoice.DEFAULT, ColorChoice.decode(""))
        assertEquals(ColorChoice.DEFAULT, ColorChoice.decode("garbage"))
        assertEquals(ColorChoice(AccentChoice.TEAL, CardTone.NEUTRAL), ColorChoice.decode("TEAL;???"))
        assertEquals(ColorChoice(AccentChoice.KACHI_BLUE, CardTone.WARM), ColorChoice.decode("nope;WARM"))
    }

    /**
     * 2.87 · R-OP1 / 2.88 — độ đục nền đi vòng tròn ở MỌI vị trí thanh kéo (0..100 bước 5, kể cả `o0` = nền trong hẳn)
     * × mọi màu × mọi tông (xuất/nhập hồ sơ mang theo chuỗi này). Khoá: hồ sơ xuất ra ở vị trí nào nhập lại ĐÚNG vị trí đó.
     */
    @Test
    fun `do duc nen di vong tron o moi vi tri thanh keo`() {
        (0..ChromeOpacity.POSITIONS).map(ChromeOpacity::ofPosition).forEach { pct ->
            AccentChoice.values().forEach { a ->
                CardTone.values().forEach { t ->
                    val c = ColorChoice(a, t, pct)
                    assertEquals(c, ColorChoice.decode(c.encode()), c.encode())
                }
            }
        }
    }

    /**
     * Mặc định giữ đúng từng byte chuỗi của ≤ 2.86 (người chưa chỉnh gì không đổi tệp cấu hình), và chuỗi MỚI đọc bằng
     * bộ đọc CŨ (chỉ hai trường đầu) vẫn ra đúng màu — bản cũ nhập hồ sơ của bản mới không mất màu.
     */
    @Test
    fun `mac dinh giu chuoi cu, ban cu doc chuoi moi van dung mau`() {
        assertEquals("KACHI_BLUE;NEUTRAL", ColorChoice.DEFAULT.encode())
        assertEquals("VIOLET;WARM", ColorChoice(AccentChoice.VIOLET, CardTone.WARM).encode())
        val enc = ColorChoice(AccentChoice.TEAL, CardTone.COOL, 55).encode()
        assertEquals("TEAL;COOL;o55", enc)
        fun oldReader(s: String) = s.split(";").let { p -> p[0] to p[1] }   // đúng hai trường mà decode ≤ 2.86 đọc
        assertEquals("TEAL" to "COOL", oldReader(enc))
    }

    /** Dạng cũ `ACCENT;TONE;paint;model` KHÔNG bao giờ đọc nhầm thành độ đục — trường 3/4 là chữ, không mang nhãn `o`. */
    @Test
    fun `chuoi cu paint model doc ra do duc mac dinh`() {
        listOf("VIOLET;WARM;pearl;sealion6", "VIOLET;WARM;pearl", "VIOLET;WARM;ocean;o", "VIOLET;WARM;70;55").forEach {
            assertEquals(ChromeOpacity.DEFAULT, ColorChoice.decode(it).surfaceOpacity, it)
            assertEquals(AccentChoice.VIOLET, ColorChoice.decode(it).accent, it)
        }
        // Trường mang nhãn đứng SAU paint/model (một bản tương lai nối thêm) vẫn đọc được.
        assertEquals(70, ColorChoice.decode("VIOLET;WARM;pearl;sealion6;o70").surfaceOpacity)
    }

    /**
     * 2.88 — rác trong trường độ đục ⇒ bội của 5 gần nhất trong `[0, 100]` hoặc mặc định, KHÔNG sập. Khoá: (1) năm bậc
     * 2.87 đọc ra ĐÚNG số cũ (hồ sơ 2.87 giữ dáng cũ từng byte); (2) `o0`/`o5`/`o100` là giá trị hợp lệ, không bị kéo
     * về 40 như 2.87; (3) mọi chuỗi giải mã ra một vị trí có thật của thanh kéo.
     */
    @Test
    fun `do duc rac thi kep ve gia tri hop le`() {
        listOf(100, 85, 70, 55, 40).forEach { assertEquals(it, ColorChoice.decode("TEAL;NEUTRAL;o$it").surfaceOpacity, "bậc 2.87 $it") }
        assertEquals(0, ColorChoice.decode("TEAL;NEUTRAL;o0").surfaceOpacity, "o0 = nền trong hẳn (2.88), không còn kéo về 40")
        assertEquals(5, ColorChoice.decode("TEAL;NEUTRAL;o5").surfaceOpacity)
        assertEquals(100, ColorChoice.decode("TEAL;NEUTRAL;o100").surfaceOpacity)
        assertEquals(5, ColorChoice.decode("TEAL;NEUTRAL;o7").surfaceOpacity, "lệch bước ⇒ bội của 5 gần nhất")
        assertEquals(0, ColorChoice.decode("TEAL;NEUTRAL;o2").surfaceOpacity)
        assertEquals(90, ColorChoice.decode("TEAL;NEUTRAL;o88").surfaceOpacity)
        assertEquals(100, ColorChoice.decode("TEAL;NEUTRAL;o250").surfaceOpacity, "trên dải ⇒ 100")
        listOf("o", "o-5", "o1234", "oo70", "O70", "o7x", "  ").forEach {
            assertEquals(100, ColorChoice.decode("TEAL;NEUTRAL;$it").surfaceOpacity, it)
        }
        assertEquals(70, ColorChoice.decode("TEAL;NEUTRAL; o70 ").surfaceOpacity, "khoảng trắng quanh trường được bỏ")
        val valid = (0..ChromeOpacity.POSITIONS).map(ChromeOpacity::ofPosition).toSet()
        listOf("o0", "o1", "o3", "o42", "o99", "o100", "o999").forEach {
            assertTrue(ColorChoice.decode("TEAL;NEUTRAL;$it").surfaceOpacity in valid, it)
        }
        // Mặc định vẫn không ghi trường (chuỗi ≤ 2.86 từng byte); mọi giá trị khác ghi đúng số.
        assertEquals("TEAL;NEUTRAL", ColorChoice(AccentChoice.TEAL, CardTone.NEUTRAL, 100).encode())
        assertEquals("TEAL;NEUTRAL;o0", ColorChoice(AccentChoice.TEAL, CardTone.NEUTRAL, 0).encode())
    }

    @Test
    fun `nhan tung lua chon khong rong va khong trung`() {
        listOf(Lang.VI, Lang.EN).forEach { lang ->
            val a = AccentChoice.values().map { Strings.t(it.label(), it.label(), lang) }
            assertEquals(a.size, a.distinct().size, "nhãn màu nhấn trùng nhau ($lang)")
            assertTrue(a.all { it.isNotBlank() })
            val t = CardTone.values().map { it.label() }
            assertEquals(t.size, t.distinct().size)
        }
        assertEquals(9, AccentChoice.values().size, "AC8.1: 8 ô chọn nhanh + 1 ô theo ảnh nền")
        assertEquals(3, CardTone.values().size, "AC8.2: trung tính · ấm · lạnh")
    }
}
