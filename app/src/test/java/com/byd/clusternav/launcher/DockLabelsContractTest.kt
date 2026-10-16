package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ 3.1.11 · DOCK-LABELS-310 — ô THANH NÚT XE có nhãn (hình 3.0 đã chạy ngoài hiện trường) ═══════════════════════════════════
 *
 * Owner 16/10 (ảnh xe, bản OTA 3.1.10, tiếng Anh): *"taskbar hiện các widget mất hết chữ rồi? trước có chữ mà, này là setting hay sao
 * nhỉ?"* — ô Front defrost / Reading light / All windows / 50% all windows chỉ còn icon. Gốc [ĐO source]: 3.1.1 R22·R gắn
 * `iconOnly = true` cho [TileSize.DOCK] ⇒ `ControlTileFactory.actionTile` đặt nhãn `GONE`; 3.1.1–3.1.9 là bản thử nên người dùng OTA
 * (3.0 → 3.1.10) thấy lần đầu. KHÔNG phải công tắc Cài đặt (không có khoá nào). Doc `docs/diagnostics/dock-labels-310-2026-10-16.md`.
 *
 * Thử ĐỎ [ĐO 16/10]: đặt lại `iconOnly = true` cho `DOCK` ⇒ bài 1 ĐỎ; cho `ControlDockView` dùng `TileSize.MINI` ⇒ bài 2 ĐỎ.
 */
class DockLabelsContractTest {

    @Test fun `o thanh nut co nhan, dung co 3_0`() {
        assertFalse(TileSize.DOCK.iconOnly, "ô thanh nút phải hiện nhãn (owner 16/10)")
        assertEquals(Sp.ICON_S, TileSize.DOCK.iconDp, "icon 20 dp + nhãn hai dòng = hình 3.0 (204) đã chạy ngoài hiện trường")
        assertEquals(Sp.ICON_S, TileSize.DOCK.stepIconDp)
        // R22·R giữ cho ô nút TRONG widget: lưới nén + hàng nút nhóm vẫn chỉ icon.
        assertTrue(TileSize.MINI.iconOnly && TileSize.GROUP.iconOnly, "ô nút trong widget giữ chỉ-icon (R22·R)")
    }

    @Test fun `thanh nut dung DOCK, luoi nen widget dung MINI`() {
        fun src(f: String) = Files.readString(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/$f"))
        assertTrue(src("ControlDockView.kt").contains("size = TileSize.DOCK"), "thanh nút xe")
        val mini = SourceRoots.body(src("WidgetViews.kt"), "private fun mini(")
        assertTrue(mini.contains("actionTile(ctx, id, data, TileSize.MINI)") && !mini.contains("TileSize.DOCK"), "lưới nén không mượn cỡ thanh: $mini")
        // Nhãn chỉ ẩn qua cờ của cỡ — không đường ẩn thứ hai cho ô thanh nút.
        assertEquals(1, Regex("size\\.iconOnly").findAll(src("ControlTileFactory.kt")).count())
    }
}
