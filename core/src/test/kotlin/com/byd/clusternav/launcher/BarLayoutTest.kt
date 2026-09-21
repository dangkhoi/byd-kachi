package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ UX-OVERHAUL · WP4 — LUẬT SẮP CHỖ (thuần, kiểm off-car) ═══════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-ux-overhaul.html` §WP4 · R4.1. Bài này canh **luật**, không canh bề mặt: một cú bấm ◀/▶ ở
 * màn Cài đặt quy về đúng một phép ở đây, nên mọi ca biên (đụng đầu · đụng cuối · mã lạ · dữ liệu prefs hỏng) kiểm
 * được mà không cần máy.
 */
class BarLayoutTest {

    // ── BarOrder: phép dời dùng CHUNG cho hai thanh ──────────────────────────────────────────────────────

    @Test
    fun `doi mot bac len va xuong`() {
        val l = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), BarOrder.move(l, "b", -1))
        assertEquals(listOf("a", "c", "b"), BarOrder.move(l, "b", +1))
    }

    /**
     * ⚠⚠ Tính chất mà TẦNG VẼ dựa vào: không dời được ⇒ trả về **CHÍNH** danh sách cũ (`assertSame`, không phải
     * `assertEquals`).
     *
     * Đó là cách `SettingsBarOrderRows` biết phải làm mờ nút ◀ ở hàng đầu mà không nhân đôi luật kẹp biên, và cũng
     * là cách `HeaderLayout.move`/`DockConfig.moveEnabled` tránh dựng một vật mới y hệt rồi ghi bền một lượt vô
     * nghĩa. Một `assertEquals` ở đây sẽ **vẫn xanh** nếu ai đó đổi sang trả bản sao — nên phải so tham chiếu.
     */
    @Test
    fun `dung bien thi tra ve CHINH danh sach cu`() {
        val l = listOf("a", "b", "c")
        assertSame(l, BarOrder.move(l, "a", -1), "đầu danh sách không lên được nữa")
        assertSame(l, BarOrder.move(l, "c", +1), "cuối danh sách không xuống được nữa")
        assertSame(l, BarOrder.move(l, "z", -1), "mã không có trong danh sách")
        assertSame(l, BarOrder.move(l, "b", 0), "dời 0 bậc là không dời")
        assertFalse(BarOrder.canMove(l, "a", -1))
        assertTrue(BarOrder.canMove(l, "a", +1))
    }

    /**
     * [delta] **không** bị kẹp về ±1 — một cú kéo-thả dời nhiều bậc là CÙNG phép này (xem KDoc [BarOrder.move]).
     * Quá biên thì vẫn là không-dời-được, KHÔNG phải "dời tới sát biên".
     */
    @Test
    fun `doi nhieu bac duoc, vuot bien thi khong doi`() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "d", "a"), BarOrder.move(l, "a", +3))
        assertSame(l, BarOrder.move(l, "a", +4), "vượt biên ⇒ không dời (không kẹp về sát biên)")
    }

    // ── HeaderLayout ─────────────────────────────────────────────────────────────────────────────────────

    /** Mặc định = ĐÚNG hình dạng 1.85: ai không sửa gì thì không thấy gì khác. */
    @Test
    fun `mac dinh giu dung thu tu cua ban 1_85`() {
        assertEquals(
            listOf(
                HeaderItem.CLOCK, HeaderItem.CHIPS, HeaderItem.VOICE,
                HeaderItem.APPS, HeaderItem.SETTINGS, HeaderItem.PROFILE,
            ),
            HeaderLayout.DEFAULT.order,
        )
    }

    @Test
    fun `thu tu phai chua dung mot lan moi vat`() {
        assertThrows(IllegalArgumentException::class.java) { HeaderLayout(listOf(HeaderItem.CLOCK)) }
        assertThrows(IllegalArgumentException::class.java) {
            HeaderLayout(HeaderLayout.DEFAULT_ORDER + HeaderItem.CLOCK)
        }
    }

    @Test
    fun `move di qua BarOrder va giu bat bien`() {
        val moved = HeaderLayout.DEFAULT.move(HeaderItem.PROFILE, -1)
        assertEquals(HeaderItem.PROFILE, moved.order[4])
        assertEquals(HeaderItem.SETTINGS, moved.order[5])
        assertEquals(HeaderItem.values().size, moved.order.size, "vẫn đủ 6 vật")
        assertSame(HeaderLayout.DEFAULT, HeaderLayout.DEFAULT.move(HeaderItem.CLOCK, -1), "đụng biên ⇒ chính nó")
    }

    @Test
    fun `ma hoa di vong tron`() {
        val l = HeaderLayout.DEFAULT.move(HeaderItem.PROFILE, -2).move(HeaderItem.CHIPS, +1)
        assertEquals(l, HeaderLayout.decode(HeaderLayout.encode(l)))
        assertEquals("CLOCK,CHIPS,VOICE,APPS,SETTINGS,PROFILE", HeaderLayout.encode(HeaderLayout.DEFAULT))
    }

    /**
     * ⚠⚠ Ca QUAN TRỌNG NHẤT của [HeaderLayout.decode]: chuỗi **THIẾU** vật.
     *
     * Nó xảy ra thật khi bản sau THÊM một vật vào thanh trong lúc xe đang chạy bản trước (chuỗi trên đĩa viết bằng
     * bảng cũ). Không chữa thì hàm dựng ném ngay lúc nạp ⇒ **launcher sập khi mở** — đúng lỗi `DEFAULT_WORKSPACE`
     * đã phải vá ở P9, và nó không có bài test nào bắt vì lớp đó cần Context.
     */
    @Test
    fun `decode chua du lieu hong va du lieu THIEU`() {
        assertEquals(HeaderLayout.DEFAULT, HeaderLayout.decode(null), "chưa từng sắp")
        assertEquals(HeaderLayout.DEFAULT, HeaderLayout.decode(""), "chuỗi rỗng")
        assertEquals(HeaderLayout.DEFAULT, HeaderLayout.decode("KHONG_TON_TAI,CUNG_KHONG"), "toàn tên lạ")
        // Thiếu 4 vật ⇒ hai vật đã khai giữ chỗ, bốn vật còn lại nối vào cuối theo thứ tự mặc định.
        assertEquals(
            listOf(
                HeaderItem.PROFILE, HeaderItem.CLOCK, HeaderItem.CHIPS,
                HeaderItem.VOICE, HeaderItem.APPS, HeaderItem.SETTINGS,
            ),
            HeaderLayout.decode("PROFILE,CLOCK").order,
        )
        // Tên lạ lẫn giữa tên thật ⇒ bỏ tên lạ, phần còn lại vẫn đủ 6.
        assertEquals(HeaderItem.values().size, HeaderLayout.decode("PROFILE,RAC,CLOCK").order.size)
        // Trùng lặp ⇒ giữ một lần (nếu không thì `init` ném).
        assertEquals(HeaderItem.values().size, HeaderLayout.decode("CLOCK,CLOCK,CHIPS").order.size)
    }

    /** Luật căn lề hàng chip — ở `:core` để kiểm off-car (xem [HeaderLayout.chipsAlignEnd]). */
    @Test
    fun `hang chip can END tru khi no dung dau thanh`() {
        assertTrue(HeaderLayout.DEFAULT.chipsAlignEnd)
        assertFalse(HeaderLayout.decode("CHIPS").chipsAlignEnd, "chip đứng đầu ⇒ căn START, không mở đầu bằng khoảng trống")
    }

    @Test
    fun `moi vat co nhan EN va phan loai thong tin - nut`() {
        HeaderItem.values().forEach { assertTrue(it.labelEn.isNotBlank(), "${it.name} thiếu nhãn EN") }
        // WP5 giữ nguyên cỡ chữ của vật THÔNG TIN và hạ cỡ NÚT còn 70 % ⇒ phép phân loại này là dữ liệu, không
        // phải một danh sách viết tay ở tầng vẽ.
        assertEquals(listOf(HeaderItem.CLOCK, HeaderItem.CHIPS), HeaderItem.values().filter { it.info })
    }

    // ── DockConfig: cùng phép, dữ liệu khác ──────────────────────────────────────────────────────────────

    @Test
    fun `dock doi cho nut va giu nguyen vien cung co hien`() {
        val cfg = DockConfig(edge = DockEdge.LEFT, enabled = listOf("lock", "window", "trunk"), visible = false)
        val moved = cfg.moveEnabled("trunk", -1)
        assertEquals(listOf("lock", "trunk", "window"), moved.enabled)
        assertEquals(DockEdge.LEFT, moved.edge, "sắp chỗ KHÔNG đổi viền đặt thanh (WP4: vị trí bar giữ nguyên)")
        assertFalse(moved.visible, "…cũng không đổi cờ ẩn/hiện")
    }

    @Test
    fun `dock dung bien hoac ma la thi tra ve chinh cau hinh cu`() {
        val cfg = DockConfig(enabled = listOf("lock", "window"))
        assertSame(cfg, cfg.moveEnabled("lock", -1))
        assertSame(cfg, cfg.moveEnabled("window", +1))
        assertSame(cfg, cfg.moveEnabled("khong_co_trong_thanh", -1))
        assertTrue(cfg.canMove("lock", +1))
        assertFalse(cfg.canMove("lock", -1))
    }

    /**
     * Sắp chỗ KHÔNG được là một đường lách cổng *"mã nào vào được thanh"*.
     *
     * [DockConfig.setEnabled] từ chối mã lạ (RW0); nếu [DockConfig.moveEnabled] nhận một mã lạ rồi **thêm** nó vào
     * danh sách thì cổng ấy vô nghĩa. Phép dời chỉ đổi CHỖ của mã đã có — có bài canh vì đây là loại lỗi im lặng.
     */
    @Test
    fun `sap cho khong them duoc ma moi vao thanh`() {
        val cfg = DockConfig(enabled = listOf("lock"))
        assertEquals(listOf("lock"), cfg.moveEnabled("ma_bat_ky", +1).enabled)
        assertEquals(listOf("lock"), cfg.moveEnabled("ma_bat_ky", -1).enabled)
    }
}
