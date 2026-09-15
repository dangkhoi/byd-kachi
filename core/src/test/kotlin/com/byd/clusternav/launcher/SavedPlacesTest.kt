package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ SỔ ĐỊA CHỈ · MÔ HÌNH + MÃ HOÁ ═══════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-addresses.html` R1 · R5. Thuần Kotlin ⇒ chạy off-car.
 *
 * ## Bài này khoá lại BA bài học, không phải chỉ kiểm getter/setter
 *  1. **Ký tự ngăn trong chữ người dùng** — [ĐO] 2026-09-12 `SlotCodec.SEP`: một `|` lọt vào chuỗi lưu làm **mất
 *     nguyên một bản ghi** trong im lặng. Địa chỉ là chỗ dễ có `|` nhất trong cả app (người ta chép từ đâu đó dán
 *     vào), nên phép khử phải có bài canh trực tiếp.
 *  2. **Dòng hỏng không được làm sập và không được làm mất cả sổ** — chuỗi này đến từ đĩa và sửa tay được.
 *  3. **Toạ độ ngoài dải bị từ chối** — `200, 300` hợp lệ về cú pháp nhưng không phải một chỗ trên Trái Đất; nhận
 *     nó vào là để app dẫn đường nhận một điểm đến vô nghĩa rồi **im lặng không dẫn**.
 */
class SavedPlacesTest {

    private val home = SavedPlace("Nhà", "123 Nguyễn Trãi, Hà Nội", 21.0045, 105.8412)
    private val work = SavedPlace("Công ty", "Keangnam Landmark 72")

    // ══ (1) Mã hoá ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `ma hoa roi giai ma tra lai dung so - ca co va khong co toa do`() {
        val book = listOf(home, work)
        assertEquals(book, SavedPlaces.decode(SavedPlaces.encode(book)))
        assertTrue(home.hasCoords)
        assertFalse(work.hasCoords, "thiếu toạ độ là ca BÌNH THƯỜNG, không phải mục hỏng")
    }

    /** Chuỗi rỗng / `null` / toàn khoảng trắng ⇒ sổ rỗng, không ném. */
    @Test
    fun `chuoi rong hoac null ra so rong`() {
        assertEquals(emptyList<SavedPlace>(), SavedPlaces.decode(null))
        assertEquals(emptyList<SavedPlace>(), SavedPlaces.decode(""))
        assertEquals(emptyList<SavedPlace>(), SavedPlaces.decode("   \n  "))
    }

    /**
     * Dòng hỏng ⇒ **bỏ đúng dòng đó**, các dòng lành vẫn về.
     *
     * Bốn ca đều có thật: sai số trường (sửa tay / bản khác), nhãn rỗng, địa chỉ rỗng, và **một nửa** cặp toạ độ.
     */
    @Test
    fun `dong hong bi bo, khong nem va khong mat ca so`() {
        val raw = listOf(
            "Nhà|123 Nguyễn Trãi, Hà Nội|21.0045|105.8412",
            "thiếu trường|chỉ có hai",
            "|không có nhãn||",
            "Không có địa chỉ|||",
            "Nửa toạ độ|Đâu đó|21.0|",
            "Công ty|Keangnam Landmark 72||",
        ).joinToString("\n")
        val out = SavedPlaces.decode(raw)
        assertEquals(listOf("Nhà", "Nửa toạ độ", "Công ty"), out.map { it.name })
        assertFalse(out[1].hasCoords, "một nửa cặp toạ độ KHÔNG phải toạ độ — `{lat}`/`{lng}` chỉ có nghĩa khi đủ đôi")
    }

    /** Toạ độ ngoài dải trên đĩa ⇒ đọc thành mục **không toạ độ**, không phải một điểm đến vô nghĩa. */
    @Test
    fun `toa do ngoai dai tren dia bi bo`() {
        val out = SavedPlaces.decode("Lạ|Ở đâu đó|200|300")
        assertEquals(1, out.size)
        assertFalse(out[0].hasCoords)
    }

    /** ⚠ Bài học `SlotCodec.SEP`: ký tự ngăn trong chữ người dùng phải bị khử ở **cửa VÀO**. */
    @Test
    fun `ky tu ngan va xuong dong trong chu nguoi dung bi khu ngay luc dung`() {
        val made = SavedPlaces.of("Nhà | riêng", "số 5|ngõ 2\nHà Nội")!!
        assertFalse(made.name.contains('|'))
        assertFalse(made.query.contains('|'))
        assertFalse(made.query.contains('\n'))
        // …và vì thế một vòng ghi–đọc không bao giờ đẻ ra dòng hỏng.
        assertEquals(listOf(made), SavedPlaces.decode(SavedPlaces.encode(listOf(made))))
    }

    // ══ (2) Dựng một mục ═════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `thieu nhan hoac thieu dia chi thi khong dung duoc muc nao`() {
        assertNull(SavedPlaces.of("", "123 Nguyễn Trãi"))
        assertNull(SavedPlaces.of("Nhà", "   "))
        assertNull(SavedPlaces.of("  |  ", "123 Nguyễn Trãi"), "nhãn chỉ gồm ký tự bị khử ⇒ rỗng ⇒ không hợp lệ")
    }

    @Test
    fun `doc toa do dan tu app ban do`() {
        assertEquals(10.7769 to 106.7009, SavedPlaces.parseCoords("10.7769, 106.7009"))
        assertEquals(10.7769 to 106.7009, SavedPlaces.parseCoords(" 10.7769 106.7009 "), "dán không có dấu phẩy")
        assertEquals(-33.8688 to 151.2093, SavedPlaces.parseCoords("-33.8688,151.2093"), "nam bán cầu")
    }

    @Test
    fun `toa do rac hoac ngoai dai deu bi tu choi`() {
        listOf(null, "", "abc", "10.7769", "10,7769,106", "200, 300", "10, 400").forEach {
            assertNull(SavedPlaces.parseCoords(it), "chuỗi `$it` không phải một cặp toạ độ")
        }
    }

    /** Ô nhập điền sẵn phải đọc lại được bằng chính phép đọc của nó — hai chiều là một vòng kín. */
    @Test
    fun `format roi parse lai ra dung cap so`() {
        assertEquals(21.0045 to 105.8412, SavedPlaces.parseCoords(SavedPlaces.formatCoords(home)))
        assertEquals("", SavedPlaces.formatCoords(work), "chưa có toạ độ thì ô để TRỐNG, không hiện 'null'")
    }

    // ══ (3) Thêm · sửa · xoá ═════════════════════════════════════════════════════════════════════════════

    @Test
    fun `them moi noi vao cuoi, sua thi GIU NGUYEN vi tri`() {
        val book = SavedPlaces.upsert(listOf(home), work)
        assertEquals(listOf("Nhà", "Công ty"), book.map { it.name })
        val fixed = SavedPlaces.upsert(book, home.copy(query = "Địa chỉ mới"))
        assertEquals(listOf("Nhà", "Công ty"), fixed.map { it.name }, "sửa nhà không được đẩy nó xuống cuối sổ")
        assertEquals("Địa chỉ mới", fixed[0].query)
    }

    /** Nhãn khớp **không phân biệt hoa/thường** — người ta gõ *"nhà"* hôm nay và *"Nhà"* hôm sau. */
    @Test
    fun `khop nhan khong phan biet hoa thuong cho ca tim, sua va xoa`() {
        val book = listOf(home, work)
        assertEquals(home, SavedPlaces.find(book, "nhà"))
        assertEquals(1, SavedPlaces.upsert(book, SavedPlace("NHÀ", "Chỗ khác")).count { it.name == "NHÀ" })
        assertEquals(2, SavedPlaces.upsert(book, SavedPlace("NHÀ", "Chỗ khác")).size, "không đẻ ra mục thứ ba")
        assertEquals(listOf("Công ty"), SavedPlaces.remove(book, "nhà").map { it.name })
        assertNull(SavedPlaces.find(book, "nhà ngoại"))
    }

    /**
     * Đầy sổ ⇒ **từ chối thêm MỚI**, không cắt mục cũ.
     *
     * Cắt một mục cũ để nhét mục mới là mất dữ liệu người dùng không hề yêu cầu; chỗ gọi so độ dài để nói ra.
     * Sửa một mục **đã có** thì vẫn phải chạy dù sổ đầy — nếu không thì sổ đầy là sổ đóng băng.
     */
    @Test
    fun `so day thi tu choi them moi nhung van sua duoc muc da co`() {
        val full = (1..SavedPlaces.MAX).map { SavedPlace("Nơi $it", "Địa chỉ $it") }
        assertEquals(full, SavedPlaces.upsert(full, SavedPlace("Nơi mới", "Địa chỉ mới")))
        val edited = SavedPlaces.upsert(full, SavedPlace("Nơi 1", "Địa chỉ đã sửa"))
        assertEquals(SavedPlaces.MAX, edited.size)
        assertEquals("Địa chỉ đã sửa", edited[0].query)
    }

    /** Đĩa có nhiều hơn trần (bản sau hạ trần, hoặc sửa tay) ⇒ đọc tối đa [SavedPlaces.MAX], không ném. */
    @Test
    fun `doc tu dia cat o tran`() {
        val raw = (1..SavedPlaces.MAX + 5).joinToString("\n") { "Nơi $it|Địa chỉ $it||" }
        assertEquals(SavedPlaces.MAX, SavedPlaces.decode(raw).size)
    }
}
