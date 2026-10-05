package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.launcher.ProfileSharePolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * B1b · CLUSTER-RECT-OPTION (C.3 mục 11) — khung lưu ở cụm Chữ nhật TÁCH khỏi khung Bo tròn bằng hậu tố `__RECT`.
 *
 * Bất biến khoá ở đây:
 *  • khoá Bo tròn GIỮ NGUYÊN từng byte (`config_bounds_<gói>`, `…__L30`) — bản ghi đã lưu từ 2.84 vẫn đọc đúng (CLAUDE.md §6);
 *  • khung Chữ nhật không bao giờ đè khung Bo tròn (hai lỗ khác nhau: thấu kính (195,174)-(1738,509) vs vùng trống
 *    (50,128)-(1285,555) [ĐO QML + PNG]);
 *  • họ `cast_geometry` (ảnh chụp hồ sơ, tệp xuất/nhập, bản chia sẻ) nhận khoá `__RECT` — không thì đổi hồ sơ là mất khung
 *    Chữ nhật, im lặng.
 */
class CastRectFrameKeyTest {

    private val pkg = "vn.vietmap.live"

    @Test
    fun `khoa Bo tron giu nguyen tung byte, Chu nhat them hau to o cuoi`() {
        assertEquals(pkg, CastProfile.FULL.recordKey(pkg))
        assertEquals("${pkg}__L30", CastProfile.of(ClusterSlotSide.LEFT, 30).recordKey(pkg))
        assertEquals("${pkg}__R70", CastProfile.of(ClusterSlotSide.RIGHT, 70).recordKey(pkg))
        assertEquals("${pkg}__RECT", CastProfile.FULL.inStyle(CastStyle.RECT).recordKey(pkg))
        assertEquals("${pkg}__L30__RECT", CastProfile.of(ClusterSlotSide.LEFT, 30, CastStyle.RECT).recordKey(pkg))
        assertEquals("FULL", CastProfile.FULL.key)
        assertEquals("L30__RECT", CastProfile.of(ClusterSlotSide.LEFT, 30, CastStyle.RECT).key)
    }

    @Test
    fun `hai kieu la hai o nho khac nhau, key di vong duoc`() {
        val curved = CastProfile.of(ClusterSlotSide.LEFT, 30)
        val rect = curved.inStyle(CastStyle.RECT)
        assertNotEquals(curved, rect)
        assertNotEquals(curved.hashCode(), rect.hashCode())
        assertEquals(curved, rect.inStyle(CastStyle.CURVED))
        assertTrue(rect.isRect && !curved.isRect)
        listOf(CastProfile.FULL, curved, rect, CastProfile.FULL.inStyle(CastStyle.RECT)).forEach {
            assertEquals(it, CastProfile.fromKey(it.key), "fromKey(${it.key})")
        }
        assertNull(CastProfile.fromKey("FULL__RECT__RECT"), "hậu tố lặp ⇒ khoá hỏng")
    }

    @Test
    fun `luu khung Chu nhat khong de khung Bo tron`() {
        val prefs = FakePrefs()
        val curved = DisplayConfig.NORMAL_DEFAULT.copy(bounds = CastBounds(195, 174, 1738, 509))
        val rect = DisplayConfig.NORMAL_DEFAULT.copy(bounds = ClusterRectLayout.FREE_AREA)
        prefs.saveDisplayConfig(pkg, CastProfile.FULL, curved)
        prefs.saveDisplayConfig(pkg, CastProfile.FULL.inStyle(CastStyle.RECT), rect)
        assertEquals(curved, prefs.displayConfigFor(pkg, CastProfile.FULL))
        assertEquals(curved, prefs.displayConfigFor(pkg), "hàm không hồ sơ = FULL Bo tròn (khoá cũ)")
        assertEquals(rect, prefs.displayConfigFor(pkg, CastProfile.FULL.inStyle(CastStyle.RECT)))
        assertEquals(setOf(pkg, "${pkg}__RECT"), prefs.savedRecordKeys)
    }

    @Test
    fun `ho cast_geometry nhan khoa RECT, dem dung mot app, ban chia se mang theo`() {
        val ok = listOf(
            "config_bounds_${pkg}__RECT", "config_density_${pkg}__L30__RECT", "config_size_${pkg}__R90__RECT",
            "config_overscan_${pkg}", "config_bounds_${pkg}__L30",
        )
        ok.forEach { assertTrue(CastGeometryGuard.isFamilyKey(it), "khoá thật bị coi là lạ: $it") }
        // Gạch dưới là ký tự hợp lệ của tên gói (`CastGeometryGuard.PACKAGE`, xem `config_density_a.b__L55` ở
        // CastGeometryGuardTest) ⇒ `…__RECT__L30` khớp như một tên gói lạ — vô hại (không ai đọc khoá đó). Thứ phải chặn là
        // ký tự mang lệnh.
        listOf("config_bounds_${pkg}__RECT;reboot", "config_bounds_${pkg}__RECT \$(id)", "config_bounds___RECT")
            .forEach { assertFalse(CastGeometryGuard.isFamilyKey(it), "khoá độc/sai dạng lọt: $it") }
        assertEquals(pkg, CastGeometryGuard.appOfRecord("${pkg}__RECT"))
        assertEquals(pkg, CastGeometryGuard.appOfRecord("${pkg}__L30__RECT"))
        assertEquals(pkg, CastGeometryGuard.appOfRecord("${pkg}__R90"))
        assertTrue(ProfileSharePolicy.shareable("config_bounds_${pkg}__RECT"), "khung Chữ nhật là hình học px, không vị trí")
    }

    // ── Khung mặc định (D2) ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `khung mac dinh Chu nhat = vung trong, ban luu thieu khung duoc them khung`() {
        val fresh = ClusterRectLayout.pin(null, ClusterRectLayout.FREE_AREA)
        assertEquals(CastBounds(50, 128, 1285, 555), fresh.bounds, "D2: vùng không bị nền ADAS che")
        assertEquals(CastGeometryGuard.DENSITY_RESET, fresh.density, "chưa lưu gì ⇒ repin không chạm DPI")
        assertTrue(CastGeometryGuard.isShellSafe(fresh), "bản ghim mặc định phải qua chốt cuối")
        val dpiOnly = DisplayConfig.NORMAL_DEFAULT.copy(density = "160", bounds = null)
        assertEquals(ClusterRectLayout.FREE_AREA, ClusterRectLayout.pin(dpiOnly, ClusterRectLayout.FREE_AREA).bounds)
        assertEquals("160", ClusterRectLayout.pin(dpiOnly, ClusterRectLayout.FREE_AREA).density)
        val mine = DisplayConfig.NORMAL_DEFAULT.copy(bounds = CastBounds(0, 0, 1920, 720))
        assertEquals(mine, ClusterRectLayout.pin(mine, ClusterRectLayout.FREE_AREA), "khung người lái lưu (kể cả trọn cụm) thắng")
    }

    @Test
    fun `nua o Chu nhat chia vung trong, Bo tron giu dung phep cu`() {
        val l = ClusterRectLayout.slotFrame(ClusterSlotSide.LEFT, 50)
        val r = ClusterRectLayout.slotFrame(ClusterSlotSide.RIGHT, 50)
        assertEquals(CastBounds(50, 128, 667, 555), l)
        assertEquals(CastBounds(667, 128, 1285, 555), r)
        listOf(l, r).forEach { assertFalse(ClusterRectLayout.intersects(it, ClusterRectLayout.ADAS_PANEL)) }
        assertEquals(l to r, ClusterRectLayout.splitFrames(CastStyle.RECT, 50, 1920, 720))
        assertEquals(
            CastBounds(0, 0, 576, 720) to CastBounds(576, 0, 1920, 720),
            ClusterRectLayout.splitFrames(CastStyle.CURVED, 30, 1920, 720),
            "Bo tròn: đúng `width * pct / 100` trên cả cụm như trước B1b",
        )
    }

    @Test
    fun `kieu khung cua phien - kieu tin thang lua chon, chua ro thi theo lua chon ap duoc`() {
        val seal = ProjectionRecipe.SEAL_DL3.copy(nativeStyle = CastStyle.RECT)
        fun plan(b: BelievedStyle) = ClusterStylePlan.Plan(null, b, abort = false, why = "")
        assertEquals(CastStyle.RECT, CastSessionStyle.of(seal, CastStyle.RECT, plan(BelievedStyle.RECT)).frame)
        assertEquals(CastStyle.CURVED, CastSessionStyle.of(seal, CastStyle.RECT, plan(BelievedStyle.CURVED)).frame,
            "chọn Chữ nhật mà cụm vẫn Bo tròn (cổng bỏ 31) ⇒ khung theo CỤM")
        assertEquals(CastStyle.RECT, CastSessionStyle.of(seal, CastStyle.RECT, plan(BelievedStyle.UNKNOWN)).frame)
        assertEquals(CastStyle.RECT, CastSessionStyle.of(seal, CastStyle.CURVED, plan(BelievedStyle.RECT)).frame,
            "chọn Bo tròn mà cụm đang Chữ nhật ⇒ khung + km/h theo CỤM")
        assertEquals(CastStyle.CURVED, CastSessionStyle.of(ProjectionRecipe.SEAL_DL3, CastStyle.RECT, plan(BelievedStyle.UNKNOWN)).frame,
            "xe không gốc chữ nhật ⇒ Chữ nhật ẩn ⇒ Bo tròn")
        val dl5 = ProjectionRecipe(ProjectionRecipe.SVC_DILINK5, listOf(16), listOf(18, 0))
        assertEquals(CastStyle.CURVED, CastSessionStyle.of(dl5, CastStyle.RECT, null).frame)
    }

    /**
     * Review 2.89 Pass 3 · cluster-r2-5 — Chữ nhật (31 ⇒ cụm mất km/h gốc) chỉ đi vào lượt mở khi Kachi vẽ được km/h; không quyền vẽ ⇒
     * Bo tròn (cụm giữ km/h gốc). Bo tròn không bao giờ bị đổi. Thử ĐỎ: trả `withReadout` về `chosen`.
     */
    @Test
    fun `Pass 3 - Chu nhat chi khi ve duoc km-h, khong thi Bo tron`() {
        assertEquals(CastStyle.RECT, CastStyleApply.withReadout(CastStyle.RECT, canDrawReadout = true))
        assertEquals(CastStyle.CURVED, CastStyleApply.withReadout(CastStyle.RECT, canDrawReadout = false))
        assertEquals(CastStyle.CURVED, CastStyleApply.withReadout(CastStyle.CURVED, canDrawReadout = false))
        assertEquals(CastStyle.CURVED, CastStyleApply.withReadout(CastStyle.CURVED, canDrawReadout = true))
    }

    /**
     * Review 2.89 vòng 3 · r3a-3: chọn Chữ nhật mà chưa có quyền vẽ ⇒ phiên mở ra Bo tròn. Nút "Áp ngay" phải so với kiểu HIỆU LỰC
     * (Bo tròn) ⇒ không hiện; so với lựa chọn thô (Chữ nhật) thì nút hiện mãi, bấm lại vẫn ra Bo tròn.
     */
    @Test
    fun `ap ngay - chon Chu nhat chua ve duoc km_h va phien Bo tron - khong moi nut`() {
        val curvedSession = CastSessionStyle(CastStyle.CURVED, BelievedStyle.CURVED, CastStyle.CURVED)
        val effective = CastStyleApply.withReadout(CastStyle.RECT, canDrawReadout = false)
        assertFalse(CastStyleApply.offer(effective, curvedSession, SimpleCastState.Idle, castEnabled = true))
        // Đối chứng: có quyền vẽ ⇒ hiệu lực Chữ nhật ≠ phiên Bo tròn ⇒ mời nút như cũ.
        val drawable = CastStyleApply.withReadout(CastStyle.RECT, canDrawReadout = true)
        assertTrue(CastStyleApply.offer(drawable, curvedSession, SimpleCastState.Idle, castEnabled = true))
    }
}
