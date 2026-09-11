package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá logic HÌNH NỀN + TRÌNH CHIẾU (U4 — spec `kachi-wallpaper.html`).
 *
 * Test quan trọng nhất: `chua toi han thi GIU CA moc thoi gian` — đổi mốc mà không đổi ảnh sẽ đẩy hạn lùi mãi ⇒ ảnh
 * **không bao giờ đổi**. Đó là lỗi kinh điển của kiểu đếm theo thời gian, và nó im lặng: không ai thấy lỗi, chỉ thấy
 * "trình chiếu không chạy".
 */
class SlideshowTest {

    private val paths = listOf("/a/1.jpg", "/a/2.png", "/a/3.webp")

    // ── Luật đổi ảnh ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `chua tung doi thi doi NGAY, khong phai cho mot chu ky`() {
        // Mốc = 0 nghĩa là chưa từng đổi. Nếu bắt chờ một chu kỳ thì mở launcher lên sẽ thấy nền trống 60 giây.
        val s = Slideshow.next(SlideshowState(index = 0, lastChangeMs = 0L), count = 3, nowMs = 1_000L)
        assertEquals(1, s.index)
        assertEquals(1_000L, s.lastChangeMs)
    }

    @Test
    fun `chua toi han thi GIU CA moc thoi gian`() {
        val before = SlideshowState(index = 1, lastChangeMs = 10_000L)
        val after = Slideshow.next(before, count = 3, nowMs = 15_000L, intervalSec = 60)
        assertEquals(before, after,
            "Chưa tới hạn thì phải giữ NGUYÊN cả chỉ số và mốc. Đổi mốc mà không đổi ảnh sẽ đẩy hạn lùi mãi ⇒ " +
                "ảnh KHÔNG BAO GIỜ đổi, và lỗi này im lặng.")
    }

    @Test
    fun `toi han thi sang anh ke va dat lai moc`() {
        val s = Slideshow.next(SlideshowState(1, 10_000L), count = 3, nowMs = 70_000L, intervalSec = 60)
        assertEquals(2, s.index)
        assertEquals(70_000L, s.lastChangeMs)
    }

    @Test
    fun `dung dung han cung tinh la toi han`() {
        val s = Slideshow.next(SlideshowState(0, 10_000L), count = 3, nowMs = 70_000L, intervalSec = 60)
        assertEquals(1, s.index, "now == hạn phải tính là tới hạn (>=), không phải chờ thêm một nhịp")
    }

    @Test
    fun `vong lai dau khi het danh sach`() {
        val s = Slideshow.next(SlideshowState(2, 0L), count = 3, nowMs = 1L)
        assertEquals(0, s.index, "ảnh cuối ⇒ vòng về đầu")
    }

    @Test
    fun `mot anh thi KHONG BAO GIO doi`() {
        val before = SlideshowState(0, 10_000L)
        val after = Slideshow.next(before, count = 1, nowMs = 999_999L)
        assertEquals(before, after, "một ảnh thì đổi đi đâu")
    }

    @Test
    fun `khong anh thi ve 0 va khong sap`() {
        val s = Slideshow.next(SlideshowState(5, 10_000L), count = 0, nowMs = 999_999L)
        assertEquals(0, s.index)
    }

    @Test
    fun `chi so ra ngoai pham vi thi ve 0 chu khong sap`() {
        // Ca thật: người dùng bớt ảnh trong thư mục, chỉ số cũ trỏ ra ngoài.
        val s = Slideshow.next(SlideshowState(9, 10_000L), count = 3, nowMs = 15_000L, intervalSec = 60)
        assertEquals(0, s.index, "chỉ số sai ⇒ về đầu")
        val t = Slideshow.next(SlideshowState(9, 10_000L), count = 3, nowMs = 99_000L, intervalSec = 60)
        assertEquals(1, t.index, "và khi tới hạn thì đi tiếp từ đầu, không nhảy loạn")
    }

    @Test
    fun `chu ky khong hop le thi coi nhu 1 giay chu khong chia cho 0`() {
        listOf(0, -5).forEach { bad ->
            val s = Slideshow.next(SlideshowState(0, 1_000L), count = 3, nowMs = 2_001L, intervalSec = bad)
            assertEquals(1, s.index, "chu kỳ $bad phải được coi là tối thiểu, không làm sập")
        }
    }

    // ── Chọn ảnh + lọc tệp ───────────────────────────────────────────────────────────────────────

    @Test
    fun `chon dung anh theo chi so`() {
        assertEquals("/a/2.png", Slideshow.pick(paths, 1))
        assertEquals("/a/1.jpg", Slideshow.pick(paths, 99), "chỉ số sai ⇒ ảnh đầu, không sập")
        assertNull(Slideshow.pick(emptyList(), 0), "không ảnh ⇒ null để chỗ gọi vẽ nền mặc định")
    }

    @Test
    fun `chi nhan duoi anh Android chac chan giai ma duoc`() {
        listOf("a.jpg", "b.JPEG", "c.png", "d.WEBP").forEach {
            assertTrue(Slideshow.isImage(it), "$it phải được nhận")
        }
        listOf("a.txt", "b.mp4", "c.heic", "d.gif", "khong_duoi", "e.jpg.txt").forEach {
            assertFalse(Slideshow.isImage(it), "$it KHÔNG được nhận")
        }
    }

    @Test
    fun `thu tu ON DINH de trinh chieu chay vong chu khong nhay loan`() {
        val messy = listOf("c.png", "a.jpg", "b.webp", "note.txt", "d.JPEG")
        val a = Slideshow.imagesFrom(messy)
        val b = Slideshow.imagesFrom(messy.reversed())
        assertEquals(a, b, "đọc thư mục ra thứ tự khác nhau thì trình chiếu phải vẫn cho cùng một dãy")
        assertFalse(a.any { it.endsWith(".txt") }, "tệp không phải ảnh bị bỏ")
        assertEquals(4, a.size)
    }

    // ── Lựa chọn của người dùng ──────────────────────────────────────────────────────────────────

    @Test
    fun `mac dinh la TAT - nguoi khong quan tam khong thay gi doi`() {
        assertFalse(WallpaperPrefs.DEFAULT.enabled,
            "bật sẵn hình nền là đổi thứ người dùng đang thấy mà họ không yêu cầu")
        assertEquals(Slideshow.DEFAULT_INTERVAL_SEC, WallpaperPrefs.DEFAULT.intervalSec)
        assertEquals(ImageFit.FILL, WallpaperPrefs.DEFAULT.fit, "hình nền phải phủ kín, không để viền đen")
    }

    @Test
    fun `lam toi anh la CAN THIET, khong phai trang tri`() {
        // Chữ và ô của launcher là màu sáng trên nền tối; ảnh sáng sẽ làm chữ không đọc được.
        assertTrue(WallpaperPrefs.DEFAULT.dim > 0, "phải làm tối mặc định")
        assertEquals(90, WallpaperPrefs(dimPercent = 200).dim, "cắt về trần 90 — làm tối 100% thì mất hẳn ảnh")
        assertEquals(0, WallpaperPrefs(dimPercent = -10).dim, "cắt về sàn 0")
    }

    @Test
    fun `luu roi doc lai duoc nguyen ven`() {
        val p = WallpaperPrefs(enabled = true, intervalSec = 300, fit = ImageFit.FIT, dimPercent = 20)
        assertEquals(p, WallpaperPrefs.decode(p.encode()))
    }

    @Test
    fun `chuoi luu rac thi lay mac dinh cho phan do, khong mat phan doc duoc`() {
        listOf(null, "", "   ", "rác", ";;;", "true").forEach {
            val p = runCatching { WallpaperPrefs.decode(it) }.getOrNull()
            assertTrue(p != null, "chuỗi '$it' không được làm sập")
        }
        // Phần đọc được vẫn giữ: bật=true đọc ra, chu kỳ rác thì lấy mặc định
        val mixed = WallpaperPrefs.decode("true;rác;FIT;30")
        assertTrue(mixed.enabled)
        assertEquals(Slideshow.DEFAULT_INTERVAL_SEC, mixed.intervalSec, "phần rác lấy mặc định")
        assertEquals(ImageFit.FIT, mixed.fit, "phần đọc được KHÔNG bị mất")
        assertEquals(30, mixed.dimPercent)
    }

    @Test
    fun `chu ky co nhan cho nguoi doc`() {
        assertEquals("15 giây", Slideshow.intervalLabel(15))
        assertEquals("5 phút", Slideshow.intervalLabel(300))
        assertEquals("1 giờ", Slideshow.intervalLabel(3600))
        Slideshow.INTERVAL_CHOICES_SEC.forEach {
            assertTrue(Slideshow.intervalLabel(it).isNotBlank(), "chu kỳ $it phải có nhãn")
            assertTrue(it >= 15, "ngắn hơn 15 giây thì thành nhấp nháy, không phải trình chiếu")
        }
    }
}
