package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * R29 — tầng ĐO ([LumaField]). Khoá: (1) độ chói bảng tra = `ColorMath.luminance` từng bit; (2) hộp p10/p90 từng kênh BẢO
 * THỦ (chứa ≥ 70 % điểm ảnh của các ô bị chạm; mỗi kênh không hẹp hơn p10/p90 thật); (3) ảnh nhiều chi tiết lộ ra là hộp
 * RỘNG — đúng ca phương án A (trung bình) của 2.86 R-OQ8 đã chết: sọc 2 px trung bình ra xám giữa.
 */
class LumaFieldTest {

    private val black = ColorMath.parse("#000000")
    private val white = ColorMath.parse("#ffffff")

    private fun image(w: Int, h: Int, f: (Int, Int) -> Int) = IntArray(w * h) { f(it % w, it / w) }

    private fun inBox(p: Int, b: ToneBox) =
        ColorMath.red(p) in ColorMath.red(b.lo)..ColorMath.red(b.hi) &&
            ColorMath.green(p) in ColorMath.green(b.lo)..ColorMath.green(b.hi) &&
            ColorMath.blue(p) in ColorMath.blue(b.lo)..ColorMath.blue(b.hi)

    @Test
    fun `bang tra do choi khop ColorMath tung bit`() {
        for (v in 0..255) {
            val g = ColorMath.argb(255, v, v, v)
            assertEquals(ColorMath.luminance(g), Luma.of(g), 0.0, "xám $v")
        }
        val rnd = Random(29)
        repeat(2000) {
            val c = ColorMath.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            assertEquals(ColorMath.luminance(c), Luma.of(c), 0.0, ColorMath.hex(c))
        }
    }

    @Test
    fun `anh dong mau cho hop dung mot diem bang mau that`() {
        for (hex in listOf("#000000", "#010101", "#1e1e1e", "#808080", "#c86432", "#fefefe", "#ffffff")) {
            val g = ColorMath.parse(hex)
            val r = LumaField.of(image(320, 120) { _, _ -> g }, 320, 120).range(0, 0, 320, 120)!!
            assertEquals(g, r.lo, hex); assertEquals(g, r.hi, hex)
            assertEquals(ColorMath.luminance(g), r.mean, 1e-5, hex)
        }
    }

    @Test
    fun `luoi toi da 64 cot, hang theo ti le, o gan vuong`() {
        val f = LumaField.of(IntArray(1920 * 720), 1920, 720)
        assertEquals(64, f.cols); assertEquals(24, f.rows)
        val small = LumaField.of(IntArray(40 * 10), 40, 10)
        assertEquals(40, small.cols); assertEquals(10, small.rows)
    }

    @Test
    fun `anh chia doi sang toi - vung vat qua ranh cho hop den toi trang, vung mot ben cho dong mau`() {
        val w = 640; val h = 240
        val f = LumaField.of(image(w, h) { x, _ -> if (x < w / 2) white else black }, w, h)
        val across = f.range(w / 2 - 40, 50, w / 2 + 40, 90)!!
        assertEquals(black, across.lo); assertEquals(white, across.hi)
        assertEquals(white, f.range(10, 50, 200, 90)!!.lo)
        assertEquals(black, f.range(400, 50, 600, 90)!!.hi)
    }

    @Test
    fun `soc 2 px - trung binh noi xam giua, hop noi su that den toi trang (ca phuong an A da chet)`() {
        val w = 1920; val h = 720
        val f = LumaField.of(image(w, h) { x, _ -> if ((x / 2) % 2 == 0) white else black }, w, h)
        for (r in 0 until f.rows) for (c in 0 until f.cols) {
            val cell = f.cell(c, r)
            assertTrue(cell.lo == black && cell.hi == white, "ô ($c,$r) = $cell")
            assertEquals(0.5, cell.mean, 0.05, "trung bình ô ($c,$r) — con số A đã dùng")
        }
    }

    @Test
    fun `p90 bo qua dom nho duoi 10 phan tram, khong bo qua mang lon`() {
        val w = 300; val h = 30
        val rnd = Random(7)
        val specks = image(w, h) { _, _ -> if (rnd.nextDouble() < 0.05) white else black }
        assertEquals(black, LumaField.of(specks, w, h, maxCols = 1).range(0, 0, w, h)!!.hi, "5 % đốm sáng không kéo p90")
        val patches = image(w, h) { x, _ -> if (x < w / 5) white else black }   // 20 %
        assertEquals(white, LumaField.of(patches, w, h, maxCols = 1).range(0, 0, w, h)!!.hi, "20 % sáng phải lộ ra")
    }

    @Test
    fun `hop bao tho - chua it nhat 70 phan tram diem anh cua cac o bi cham, moi kenh rong hon p10 p90 that`() {
        val rnd = Random(2026)
        repeat(20) { round ->
            val w = 200 + rnd.nextInt(400); val h = 60 + rnd.nextInt(200)
            val px = image(w, h) { x, y ->
                val n = (((x * 7 + y * 3 + round * 31) % 256) + rnd.nextInt(-60, 61)).coerceIn(0, 255)
                ColorMath.argb(255, n, (n + rnd.nextInt(-40, 41)).coerceIn(0, 255), (255 - n + rnd.nextInt(-30, 31)).coerceIn(0, 255))
            }
            val f = LumaField.of(px, w, h)
            val col = { x: Int -> (x.toLong() * f.cols / w).toInt() }
            val row = { y: Int -> (y.toLong() * f.rows / h).toInt() }
            repeat(30) {
                val l = rnd.nextInt(w - 10); val t = rnd.nextInt(h - 5)
                val r = l + 5 + rnd.nextInt(w - l - 4); val b = t + 3 + rnd.nextInt(h - t - 2)
                val got = f.range(l, t, r, b)!!
                // Đảm bảo ở cấp Ô: tập điểm của mọi ô bị vùng chạm (chi tiết < 10 % của một ô bị p10/p90 bỏ qua CÓ CHỦ Ý).
                val cs = col(l)..col(r - 1); val rs = row(t)..row(b - 1)
                val pts = ArrayList<Int>()
                for (y in 0 until h) if (row(y) in rs) for (x in 0 until w) if (col(x) in cs) pts += px[y * w + x]
                val inside = pts.count { inBox(it, got) }.toDouble() / pts.size
                assertTrue(inside >= 0.7 - 1e-9, "vòng $round vùng [$l,$t,$r,$b]: chỉ ${"%.2f".format(inside)} điểm trong hộp")
                for (ch in listOf(ColorMath::red, ColorMath::green, ColorMath::blue)) {
                    val v = pts.map { ch(it) }.sorted()
                    val p10 = v[((v.size - 1) * 0.10).toInt()]; val p90 = v[kotlin.math.ceil((v.size - 1) * 0.90).toInt()]
                    assertTrue(ch(got.lo) <= p10 && ch(got.hi) >= p90, "kênh hẹp hơn p10/p90 thật: $got vs [$p10, $p90]")
                }
            }
        }
    }

    @Test
    fun `scale doi toa do ben goi sang luoi, vung ngoai anh tra null khong tra so gia`() {
        val w = 400; val h = 100
        val f = LumaField.of(image(w, h) { x, _ -> if (x < 200) white else black }, w, h)
        // bên gọi ở toạ độ màn rộng 800 (ảnh vẽ phóng ×2) ⇒ scale 0.5: màn [0, 380) = ảnh [0, 190) = toàn trắng
        assertEquals(white, f.range(0, 0, 380, 200, scale = 0.5)!!.lo)
        assertNull(f.range(900, 0, 1000, 50, scale = 0.5))
        assertNull(f.range(10, 10, 10, 20))
        assertNotNull(f.range(-50, -50, 5, 5))
    }

    @Test
    fun `builder doc tung hang, sai thu tu hoac stride rong hon o thi nem`() {
        val w = 64; val h = 8
        val row = IntArray(w) { white }
        val b = LumaField.Builder(w, h, maxCols = 8)
        b.addRow(0, row)
        assertThrows(IllegalArgumentException::class.java) { b.addRow(0, row) }
        val thin = LumaField.Builder(w, h, maxCols = 8)          // 8 ô rộng 8 px, 1 hàng ô
        for (y in 0 until h) thin.addRow(y, row, stride = 16)   // bước 16 ⇒ có ô không mẫu
        assertThrows(IllegalStateException::class.java) { thin.build() }
        val sparse = LumaField.Builder(w, h)                     // 64 × 8 ô, mỗi hàng ảnh một hàng ô
        assertThrows(IllegalStateException::class.java) {        // bỏ hàng ⇒ hàng ô không mẫu (ném ngay khi đi qua nó)
            for (y in 0 until h step 4) sparse.addRow(y, row)
            sparse.build()
        }
    }

    @Test
    fun `ma hoa doc lai y het, chuoi hong tra null`() {
        val rnd = Random(3)
        val w = 256; val h = 96
        val f = LumaField.of(image(w, h) { _, _ -> ColorMath.argb(255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)) }, w, h)
        val back = LumaField.decode(f.encode())!!
        assertEquals(f.cols, back.cols); assertEquals(f.rows, back.rows)
        for (r in 0 until f.rows) for (c in 0 until f.cols) {
            val a = f.cell(c, r); val z = back.cell(c, r)
            assertTrue(z.lo == a.lo && z.hi == a.hi && kotlin.math.abs(z.mean - a.mean) < 2e-5, "ô ($c,$r): $a → $z")
        }
        listOf(null, "", "T0;1;1;1;1;0;0;0", "T1;2;1;2;1;0;0;0", "T1;1;1;1;1;ffffff;0;0", "rác").forEach {
            assertNull(LumaField.decode(it), "phải từ chối: $it")
        }
    }

    @Test
    fun `sample doc thang mot vung bang hop cac o`() {
        val w = 120; val h = 40
        val px = image(w, h) { x, _ -> if (x < 60) white else black }
        val r = LumaField.sample(px, w, h, 50, 0, 70, 40)!!
        assertTrue(r.lo == black && r.hi == white, "$r")
        assertEquals(white, LumaField.sample(px, w, h, 0, 0, 40, 40)!!.lo)
        assertNull(LumaField.sample(px, w, h, 200, 0, 300, 10))
    }

    @Test
    fun `hop tu mau biet truoc va hop xam`() {
        val r = ToneBox.span(ColorMath.parse("#ff0000"), ColorMath.parse("#0000ff"), ColorMath.parse("#808080"))
        assertEquals(ColorMath.parse("#000000"), r.lo); assertEquals(ColorMath.parse("#ff80ff"), r.hi)
        assertThrows(IllegalArgumentException::class.java) { ToneBox(white, black) }
        assertThrows(IllegalArgumentException::class.java) { ToneBox.gray(0.6, 0.4) }
        val g = ToneBox.gray(0.2, 0.4)
        assertTrue(g.lumLo <= 0.2 && g.lumHi >= 0.4)
    }
}
