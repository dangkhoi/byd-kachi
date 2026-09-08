package com.byd.clusternav

import com.byd.clusternav.navigation.SourceArbiter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Unit test THUẦN cho SourceArbiter — máy trạng thái chọn nguồn khi mở >1 app dẫn đường. */
class SourceArbiterTest {

    private val GMAPS = "com.google.android.apps.maps"

    @BeforeEach fun setup() { SourceArbiter.clear() }

    @Test fun `AUTO app dẫn TRƯỚC giữ khoá, app sau bị bỏ qua`() {
        assertTrue(SourceArbiter.shouldFeed(GMAPS, Prefs.AUTO, 1000L))
        assertEquals(GMAPS, SourceArbiter.activeSource)
        assertFalse(SourceArbiter.shouldFeed("vn.vietmap.app", Prefs.AUTO, 1100L))   // còn tươi → chặn
    }

    @Test fun `AUTO nhả khoá đúng nguồn thì app khác lên được`() {
        SourceArbiter.shouldFeed(GMAPS, Prefs.AUTO, 1000L)
        assertFalse(SourceArbiter.release("vn.vietmap.app"))   // không phải nguồn giữ
        assertTrue(SourceArbiter.release(GMAPS))               // đúng nguồn giữ
        assertTrue(SourceArbiter.shouldFeed("vn.vietmap.app", Prefs.AUTO, 1200L))
    }

    @Test fun `AUTO nguồn IM quá STALE thì nhường`() {
        SourceArbiter.shouldFeed(GMAPS, Prefs.AUTO, 1000L)
        val staleLater = 1000L + SourceArbiter.STALE_MS + 1
        assertTrue(SourceArbiter.shouldFeed("vn.vietmap.app", Prefs.AUTO, staleLater))
        assertEquals("vn.vietmap.app", SourceArbiter.activeSource)
    }

    /**
     * KHOÁ (owner chốt 2026-08-23 — backlog B3.48): `PREFER_GMAPS` chặn app khác **luôn luôn**, không phụ
     * thuộc GMaps có đang dẫn hay không.
     *
     * BÀI HỌC CŨ ĐÃ BỊ THAY: bản trước của test này (tên `PREFER_GMAPS chặn app khác khi GMaps còn tươi`)
     * khẳng định `shouldFeed("vn.vietmap.app", PREFER_GMAPS, t)` = **true** khi GMaps chưa tươi — đó là cửa
     * thoát `|| !isGroupFresh(GMAPS)` trong `SourceArbiter.allowedByMode`. Cửa đó đã bị gỡ: owner chốt *"chọn
     * đích danh app thì luôn lấy app đích danh, nếu app đó ko dẫn thì ko hiện gì"*. Cùng lúc đó sổ
     * `lastSeenByPkg` bị xoá, nên test cũng không còn phải né chuyện map singleton không bị `clear()` reset.
     */
    @Test fun `PREFER_GMAPS chặn app khác KỂ CẢ khi GMaps im lặng`() {
        val t = 10_000_000L
        assertFalse(SourceArbiter.shouldFeed("vn.vietmap.app", Prefs.PREFER_GMAPS, t))        // GMaps chưa dẫn → vẫn CHẶN
        assertTrue(SourceArbiter.shouldFeed(GMAPS, Prefs.PREFER_GMAPS, t + 100))
        assertFalse(SourceArbiter.shouldFeed("vn.vietmap.app", Prefs.PREFER_GMAPS, t + 200))  // GMaps tươi → chặn
        // GMaps im quá STALE: cửa thoát cũ mở ở đây. Nay ĐÓNG — cụm im lặng, không app nào thay chỗ.
        assertFalse(
            SourceArbiter.shouldFeed("vn.vietmap.app", Prefs.PREFER_GMAPS, t + 100 + SourceArbiter.STALE_MS + 1),
        )
    }

    @Test fun `isFresh phản ánh độ tươi của nguồn đang giữ`() {
        assertFalse(SourceArbiter.isFresh(1000L))   // chưa có nguồn
        SourceArbiter.shouldFeed(GMAPS, Prefs.AUTO, 1000L)
        assertTrue(SourceArbiter.isFresh(2000L))
        assertFalse(SourceArbiter.isFresh(1000L + SourceArbiter.STALE_MS + 1))
    }
}
