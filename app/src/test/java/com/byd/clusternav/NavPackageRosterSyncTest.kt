package com.byd.clusternav

import com.byd.clusternav.navigation.NavApps
import com.byd.clusternav.navigation.NavChannel
import com.byd.clusternav.navigation.NavSourceLabels
import com.byd.clusternav.navigation.NavSourceMode
import com.byd.clusternav.navigation.SourceArbiter
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá **roster app dẫn đường** khỏi lệch nhau (08-22), CẬP NHẬT 2026-08-28.
 *
 * ── VÌ SAO PHẢI CÓ ───────────────────────────────────────────────────────────────────────────────────────
 * Bản **XML** (`res/xml/nav_accessibility_config.xml` → `android:packageNames`) là cổng của `system_server`.
 * Thiếu một gói ở đó thì framework **không giao AccessibilityEvent** cho service ⇒ nguồn đó chết câm — không
 * crash, không log, không test đỏ. Kotlin không import được XML nên chỉ có test này bắc cầu được.
 *
 * ── 2026-08-28: đường ĐỌC dẫn đường VietMap/Waze qua a11y ĐÃ GỠ ─────────────────────────────────────────
 * Roster a11y [NavApps.ALL] nay CHỈ còn Google Maps (booster cự-ly ground-truth). VietMap/Waze không còn đọc
 * qua a11y ⇒ [NavApps.DESC_ONLY] rỗng, và WAZE/VIETMAP KHÔNG còn trong ALL (chúng chỉ còn là nhóm chọn nguồn
 * của [SourceArbiter]). Speed badge của VietMap đi qua widget, không qua a11y.
 */
class NavPackageRosterSyncTest {

    private val xml by lazy { SourceRoots.text("src/main/res/xml/nav_accessibility_config.xml") }

    /** `android:packageNames` trong XML phải khớp CHÍNH XÁC [NavApps.ALL] (nay = Google Maps). */
    @Test
    fun `XML packageNames khop chinh xac NavApps ALL`() {
        val attr = Regex("""android:packageNames="([^"]+)"""").find(xml)
        assertTrue(attr != null, "không tìm thấy android:packageNames trong nav_accessibility_config.xml")
        val fromXml = attr!!.groupValues[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        assertEquals(
            NavApps.ALL, fromXml,
            "roster XML lệch NavApps.ALL — gói thiếu ở XML sẽ KHÔNG nhận được event a11y (chết câm)",
        )
        assertEquals(NavApps.GMAPS, NavApps.ALL, "sau 2026-08-28 roster a11y CHỈ còn Google Maps")
    }

    /** Roster Kotlin ở `:app` phải là chính [NavApps], không phải bản chép. */
    @Test
    fun `roster Kotlin khong con ban chep`() {
        assertEquals(NavApps.NOTIFICATION, NavNotificationListener.MAPS_PACKAGES)
        val svc = SourceRoots.text("src/main/java/com/byd/clusternav/modules/navaccess/NavAccessibilityService.kt")
        assertTrue(svc.contains("maps = NavApps.GMAPS"), "nhánh GMaps-only phải trỏ NavApps.GMAPS")
    }

    /**
     * Mọi gói trong roster a11y phải có nhãn hãng (status line không hiện tên gói thô). Nay chỉ còn GMaps.
     */
    @Test
    fun `moi goi trong roster deu co nhan hang, khong roi ve ten goi tho`() {
        for (pkg in NavApps.ALL) {
            val label = NavSourceLabels.sourceLabel(pkg)
            assertNotEquals(pkg, label, "gói $pkg chưa có nhãn hãng — status line sẽ hiện tên gói thô")
            assertTrue(label.isNotBlank(), "gói $pkg cho nhãn rỗng")
        }
    }

    /**
     * [NavApps.DESC_ONLY] rỗng sau khi gỡ đường content-desc (2026-08-28) — và rỗng thì trivially ⊆ ALL.
     * Giữ khẳng định "⊆ ALL" làm canh cửa: nếu ai đó thêm lại một gói vào DESC_ONLY mà quên thêm vào ALL
     * (⇒ mất event a11y) thì test này đỏ.
     */
    @Test
    fun `roster DESC_ONLY rong va la tap con cua ALL`() {
        assertTrue(NavApps.DESC_ONLY.isEmpty(), "đường content-desc đã gỡ ⇒ DESC_ONLY phải rỗng")
        assertTrue(NavApps.ALL.containsAll(NavApps.DESC_ONLY), "gói ngoài ALL sẽ không nhận được event a11y")
    }

    /**
     * Roster **KÊNH** [NavApps.NOTIFICATION] vs roster **ĐỌC-ĐƯỢC** [NavApps.ALL].
     *
     * Sau 2026-08-28 cả ba (ALL, NOTIFICATION, GMAPS) đều bằng nhau = Google Maps. VietMap/Waze KHÔNG còn ở
     * bất kỳ roster a11y/notification nào; chúng chỉ còn là nhóm chọn nguồn của SourceArbiter.
     */
    @Test
    fun `roster kenh NOTIFICATION va doc-duoc ALL deu la GMaps`() {
        assertEquals(NavApps.GMAPS, NavApps.NOTIFICATION, "chỉ GMaps có mũi tên trong notification (đo 08-20)")
        assertEquals(NavApps.GMAPS, NavApps.ALL, "a11y roster chỉ còn GMaps (booster cự-ly)")
        assertTrue(NavApps.ALL.containsAll(NavApps.NOTIFICATION), "NOTIFICATION ⊆ ALL")
        for (pkg in NavApps.VIETMAP) {
            assertTrue(pkg !in NavApps.NOTIFICATION, "$pkg không đi kênh notification")
            assertTrue(pkg !in NavApps.ALL, "$pkg không còn đọc qua a11y (đường VietMap/Waze đã gỡ)")
        }
        for (pkg in NavApps.WAZE) {
            assertTrue(pkg !in NavApps.NOTIFICATION, "$pkg: notification chỉ có tickerText (đo 08-22)")
            assertTrue(pkg !in NavApps.ALL, "$pkg không còn đọc qua a11y (đường VietMap/Waze đã gỡ)")
        }
    }

    /**
     * [SourceArbiter] vẫn phân biệt kênh DATA↔IMAGE (logic thuần KHÔNG đổi dù đường ẢNH đã gỡ ở tầng app):
     * gói đi notification (GMaps) — một nhịp DATA chặn kênh IMAGE; gói KHÔNG đi notification (VietMap) —
     * không ai đóng mốc DATA nên kênh IMAGE vẫn mở ở mức trọng tài. Khoá logic trọng tài, không phải sự tồn
     * tại của consumer ảnh.
     */
    @Test
    fun `SourceArbiter phan biet DATA va IMAGE theo roster notification`() {
        SourceArbiter.clear()
        val vietmap = NavApps.VIETMAP.first()
        val gmaps = NavApps.GMAPS.first()

        assertTrue(gmaps in NavApps.NOTIFICATION)
        assertTrue(SourceArbiter.shouldFeed(gmaps, NavSourceMode.AUTO, 1_000L, NavChannel.DATA))
        assertFalse(
            SourceArbiter.shouldFeed(gmaps, NavSourceMode.AUTO, 1_100L, NavChannel.IMAGE),
            "kênh DATA còn tươi thì ảnh phải thua — cơ chế trọng tài",
        )

        SourceArbiter.clear()
        assertTrue(vietmap !in NavApps.NOTIFICATION)
        assertFalse(SourceArbiter.isDataFresh(vietmap, 1_000L), "không ai đóng mốc DATA cho VietMap")
        SourceArbiter.clear()
    }

    /**
     * Tiền tố resource-id của họ Waze phủ CẢ bản zin lẫn bản mod (giữ nguyên — SourceArbiter PREFER_WAZE dùng).
     * Đo tĩnh bằng aapt2 (2026-08-22): WazeMod DUAL manifest `com.chisadin.wazemod` nhưng arsc `com.waze`.
     */
    @Test
    fun `tien to resource Waze phu ca ban zin lan ban mod`() {
        assertTrue(NavApps.WAZE_RES_PREFIX in NavApps.WAZE, "tiền tố phải chính là gói Waze zin")
        assertTrue("com.chisadin.wazemod" in NavApps.WAZE, "bản mod phải nằm trong nhóm Waze")
        assertEquals(2, NavApps.WAZE.size, "nhóm Waze = {zin, mod}")
    }
}
