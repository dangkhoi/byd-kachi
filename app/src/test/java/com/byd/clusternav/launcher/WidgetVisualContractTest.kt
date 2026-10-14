package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 3.01 R22 — LƯỢT THẨM MỸ WIDGET (owner 13/10 *"nhìn thô lắm"* → brief Kim `docs/diagnostics/kim-design-brief-2026-10-13.md`) ═══
 *
 * Khoá các quyết định của lượt này ở TẦNG CHUNG (không theo widget), để một lượt sửa sau không lặng lẽ đưa "thô" trở lại:
 *  1. số không còn `DEFAULT_BOLD` / giả đậm — nét đi qua [KachiWidgetType] (light số lớn · medium số phụ);
 *  2. khay ô làm việc KHÔNG nhuộm sắc lĩnh vực (thẻ trung tính, sắc lĩnh vực chỉ ở chip đầu thẻ);
 *  3. giá trị chỉ-đọc là HÀNG, không phiến nền riêng (NEUTRAL/ACTIVE ⇒ không nền), nền chỉ đổi khi TONE đổi (không cấp phát
 *     drawable mỗi nhịp 1 Hz);
 *  4. chữ đổi ⇒ ô giá trị tự xin đo ([ĐO] `Bật` kẹt 19 px = bề rộng `—` khi bỏ lượt đặt nền mỗi nhịp);
 *  5. thanh mức không cấp phát trong `onDraw`, rãnh không dùng vai `TRACK` (tàng hình ở bảng sáng);
 *  6. tắt ảnh / chưa có ảnh ⇒ NỀN MẶC ĐỊNH của Kachi qua đúng đường ảnh nền + kính; màu nền chỉ từ bảng hạt giống.
 */
class WidgetVisualContractTest {

    private fun code(name: String): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")

    @Test
    fun `so trong widget khong con dam cung - di qua KachiWidgetType`() {
        listOf("GroupTileViews.kt", "GroupTileParts.kt", "WidgetViews.kt", "WidgetTelemetry.kt", "ReadTile.kt").forEach { f ->
            assertFalse("DEFAULT_BOLD" in code(f), "$f còn DEFAULT_BOLD — số đi qua KachiWidgetType.hero/value (R22)")
        }
        val ring = code("RingView.kt")
        assertFalse("isFakeBoldText = true" in ring, "vòng đo không giả đậm số")
        assertTrue("KachiWidgetType.heroFace" in ring, "số trong vòng đo cùng nét light với số chính")
        assertTrue("KachiWidgetType.value(this)" in code("WidgetViews.kt"), "bộ dựng chữ widget dùng vai medium")
    }

    @Test
    fun `khay o lam viec trung tinh - khong nhuom sac linh vuc`() {
        val makeSlot = SourceRoots.body(code("WorkspaceView.kt"), "private fun makeSlot(")
        assertTrue("KachiGlass.apply(fl, Sp.RADIUS_L, SurfaceTone.WELL)" in makeSlot, "khay = WELL, KHÔNG đối số lĩnh vực")
        assertFalse("slotDomain(" in makeSlot, "khay không còn tra sắc lĩnh vực (brief Kim §2.3)")
        assertTrue("KachiWidgetType.chipIcon(" in code("GroupTileViews.kt"), "sắc lĩnh vực dồn vào chip icon đầu thẻ")
    }

    @Test
    fun `gia tri chi doc la hang - nen chi doi khi tone doi`() {
        val bind = SourceRoots.body(code("GroupTileParts.kt"), "fun bind(cell: GroupCell)")
        assertTrue("cell.tone != shownTone" in bind, "nền ô con chỉ dựng lại khi TONE đổi — không cấp phát mỗi nhịp")
        assertTrue("GroupTone.NEUTRAL, GroupTone.ACTIVE -> KachiGlass.plain(root, null)" in bind, "hàng chỉ-đọc không phiến nền")
        assertTrue("raw != shownText" in bind, "span đơn vị chỉ dựng khi chuỗi đổi")
        assertTrue("value.requestLayout()" in bind, "chuỗi đổi ⇒ ô giá trị tự xin đo (lỗi `Bật` 19 px)")
    }

    @Test
    fun `thanh muc khong cap phat khi ve va ran thay duoc o hai bang`() {
        val meter = code("KachiMeter.kt")
        val draw = SourceRoots.body(meter, "override fun onDraw(")
        listOf("RectF(", "Paint(", "LinearGradient(", "Path(").forEach {
            assertFalse(it in draw, "KachiMeter.onDraw cấp phát $it mỗi khung")
        }
        assertFalse("KachiTheme.TRACK" in meter, "rãnh dùng mực-theo-alpha, không vai TRACK (tàng hình ở bảng sáng)")
        assertTrue("KachiMeter.bounded(cell.unit)" in code("GroupTileViews.kt"), "thanh chọn theo DỮ LIỆU (đơn vị %), không theo mã")
    }

    @Test
    fun `nen mac dinh di qua duong anh nen va kinh`() {
        val ctrl = code("WallpaperController.kt")
        // 3.1.5: tắt ảnh người dùng ⇒ reload chọn nguồn qua applySource() (ảnh đóng gói > nền vẽ); không còn ảnh nào ⇒ step vẽ nền mã.
        assertTrue("applySource()" in SourceRoots.body(ctrl, "fun reload()"), "tắt ảnh người dùng ⇒ chọn lại nguồn nền mặc định")
        assertTrue("WallSourcePolicy.resolve(" in SourceRoots.body(ctrl, "private fun applySource()"), "nguồn chọn bằng chính sách thuần")
        assertTrue("showDefault()" in SourceRoots.body(ctrl, "fun step("), "không có ảnh để chiếu ⇒ nền vẽ bằng mã")
        val show = SourceRoots.body(ctrl, "private fun showDefault()")
        assertTrue("WallArtStore.swap(" in show && "wall.setPhoto(" in show, "nền mặc định đi qua CÙNG kho kính + WallView")
        assertTrue("++wallDecodeGen" in show, "lượt dựng về muộn bị bỏ bằng thẻ giải mã")
        val wd = code("WallDefault.kt")
        assertTrue("KachiPaletteSeeds.BACKDROP_DARK" in wd && "KachiPaletteSeeds.BACKDROP_LIGHT" in wd, "màu nền chỉ từ bảng hạt giống")
        assertEquals(2, Regex("""wallpaper\.onThemeChanged\(\)""").findAll(code("KachiHomeRender.kt")).count(),
            "đổi Sáng/Tối (tại chỗ + lượt đầu) ⇒ dựng lại nền mặc định")
    }
}
