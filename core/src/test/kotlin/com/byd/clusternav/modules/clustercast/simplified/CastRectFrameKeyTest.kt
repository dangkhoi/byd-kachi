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
        val rect = DisplayConfig.NORMAL_DEFAULT.copy(bounds = CastBounds(50, 128, 1285, 555))
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

    // ── Khung mặc định — 2.90 · R4: trọn cụm, không vùng chừa ────────────────────────────────────────────────────────

    @Test
    fun `290 - khung mac dinh Chu nhat = tron cum, ban luu thieu khung duoc them khung`() {
        val fresh = ClusterRectLayout.pin(null, ClusterRectLayout.FULL)
        assertEquals(CastBounds(0, 0, 1920, 720), fresh.bounds, "owner 06/10: cứ để full resolution")
        assertEquals(CastGeometryGuard.DENSITY_RESET, fresh.density, "chưa lưu gì ⇒ DPI thường của lượt mở, repin không chạm DPI")
        assertTrue(CastGeometryGuard.isShellSafe(fresh), "bản ghim mặc định phải qua chốt cuối")
        val dpiOnly = DisplayConfig.NORMAL_DEFAULT.copy(density = "160", bounds = null)
        assertEquals(ClusterRectLayout.FULL, ClusterRectLayout.pin(dpiOnly, ClusterRectLayout.FULL).bounds)
        assertEquals("160", ClusterRectLayout.pin(dpiOnly, ClusterRectLayout.FULL).density)
        val mine = DisplayConfig.NORMAL_DEFAULT.copy(bounds = CastBounds(50, 128, 1285, 555))
        assertEquals(mine, ClusterRectLayout.pin(mine, ClusterRectLayout.FULL), "khung người lái lưu thắng")
    }

    @Test
    fun `290 - nua o Chu nhat = nua tron cum, cung phep Bo tron`() {
        val l = ClusterRectLayout.slotFrame(ClusterSlotSide.LEFT, 50)
        val r = ClusterRectLayout.slotFrame(ClusterSlotSide.RIGHT, 50)
        assertEquals(CastBounds(0, 0, 960, 720), l)
        assertEquals(CastBounds(960, 0, 1920, 720), r)
        assertEquals(l to r, ClusterRectLayout.splitFrames(CastStyle.RECT, 50, 1920, 720))
        assertEquals(
            CastBounds(0, 0, 576, 720) to CastBounds(576, 0, 1920, 720),
            ClusterRectLayout.splitFrames(CastStyle.CURVED, 30, 1920, 720),
            "Bo tròn: đúng `width * pct / 100` trên cả cụm như trước B1b",
        )
    }

    /**
     * 2.92 · CLUSTER-FRAME-CHOSEN (thay 2.90 · R2) — kiểu tin thắng lựa chọn; CHƯA RÕ (cổng bỏ theme, tiến trình chưa gửi gì) ⇒
     * khung + khoá theo kiểu NGƯỜI LÁI CHỌN. [ĐO log xe 06/10]: BYD giết Kachi mỗi lần tắt máy, cổng bỏ opcode (bóng nổi 15:13,
     * chưa đủ 15 s 15:17) ⇒ UNKNOWN ⇒ luật cũ "trọn cụm + khoá Bo tròn" bỏ khung Chữ nhật đã lưu và lưu chỉnh tay nhầm ô.
     * Đời xe chỉ có Bo tròn / DL5 (không opcode kiểu): Bo tròn như cũ. Thử ĐỎ: trả nhánh UNKNOWN về `CastStyle.CURVED`.
     */
    @Test
    fun `292 - kieu khung cua phien - chua ro thi theo kieu nguoi lai chon`() {
        val seal = ProjectionRecipe.SEAL_DL3.copy(nativeStyle = CastStyle.RECT)
        fun plan(b: BelievedStyle) = ClusterStylePlan.Plan(null, b, abort = false, why = "")
        assertEquals(CastSessionStyle(CastStyle.RECT, BelievedStyle.RECT, CastStyle.RECT), CastSessionStyle.of(seal, CastStyle.RECT, plan(BelievedStyle.RECT)))
        assertEquals(CastStyle.CURVED, CastSessionStyle.of(seal, CastStyle.RECT, plan(BelievedStyle.CURVED)).frame,
            "chọn Chữ nhật mà cụm vẫn Bo tròn ⇒ khung theo CỤM")
        assertEquals(CastSessionStyle(CastStyle.RECT, BelievedStyle.UNKNOWN, CastStyle.RECT),
            CastSessionStyle.of(seal, CastStyle.RECT, plan(BelievedStyle.UNKNOWN)), "chưa rõ ⇒ kiểu người lái chọn")
        assertEquals(CastStyle.CURVED, CastSessionStyle.of(seal, CastStyle.CURVED, plan(BelievedStyle.UNKNOWN)).frame)
        assertEquals(CastStyle.RECT, CastSessionStyle.of(seal, CastStyle.CURVED, plan(BelievedStyle.RECT)).frame,
            "chọn Bo tròn mà cụm đang Chữ nhật ⇒ khung theo CỤM")
        assertEquals(CastStyle.CURVED, CastSessionStyle.of(ProjectionRecipe.SEAL_DL3, CastStyle.RECT, plan(BelievedStyle.UNKNOWN)).frame,
            "đời xe ẩn Chữ nhật ⇒ Bo tròn như cũ")
        val dl5 = ProjectionRecipe(ProjectionRecipe.SVC_DILINK5, listOf(16), listOf(18, 0))
        assertEquals(CastStyle.CURVED, CastSessionStyle.of(dl5, CastStyle.RECT, null).frame, "DL5 không có opcode kiểu ⇒ đường cũ y nguyên")
    }

    /** "Áp ngay" mời khi phiên chưa rõ kiểu (cụm có thể đang khác lựa chọn) — 2.90 không còn điều kiện quyền vẽ km/h. */
    @Test
    fun `ap ngay - phien chua ro kieu thi moi nut, phien dung kieu thi khong`() {
        val curvedSession = CastSessionStyle(CastStyle.CURVED, BelievedStyle.CURVED, CastStyle.CURVED)
        assertFalse(CastStyleApply.offer(CastStyle.CURVED, curvedSession, SimpleCastState.Idle, castEnabled = true))
        assertTrue(CastStyleApply.offer(CastStyle.RECT, curvedSession, SimpleCastState.Idle, castEnabled = true))
        val unknown = CastSessionStyle(CastStyle.RECT, BelievedStyle.UNKNOWN, CastStyle.RECT)
        assertTrue(CastStyleApply.offer(CastStyle.CURVED, unknown, SimpleCastState.Idle, castEnabled = true))
        assertTrue(CastStyleApply.offer(CastStyle.RECT, unknown, SimpleCastState.Idle, castEnabled = true),
            "khung theo lựa chọn nhưng cụm CHƯA xác nhận ⇒ vẫn mời Áp ngay")
    }
}
