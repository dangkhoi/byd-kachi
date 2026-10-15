package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * 3.1.7 · STALE-AUDIT — dây của họ lỗi *"phím/giọng nói ghi bảng nhưng màn đứng hình cũ"* (`docs/diagnostics/stale-display-audit-
 * 2026-10-15.md`). Luật ở `:core` khoá bằng `ControlWriteEchoTest`; bài này đỏ khi gỡ một trong các dây nối luật đó vào bề mặt.
 */
class StaleDisplayWiringContractTest {
    private fun src(f: String) = Files.readString(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/$f"))

    @Test
    fun `phim va giong noi di ControlWriteEcho - bang + dich giu + bao man`() {
        val d = src("VoiceControlDispatch.kt")
        assertTrue(d.contains("if (ok) st.echo(def, arg)"), "finish(ok) phải đi echo")
        assertFalse(d.contains("ControlKind.STEP -> st.setValue(def.id, arg)"), "đường cũ chỉ ghi bảng, không vẽ")
        val st = src("ControlTileState.kt")
        assertTrue(st.contains("ControlWriteEcho.applied(def, arg, setStep = ::setValue, sent = sent)"))
    }

    @Test
    fun `goi lenh bao man sau khi ghi bang`() {
        assertTrue(src("VoiceDispatcher.kt").contains("}; ControlTileState.shared.announce()"), "gói lệnh giọng nói")
        assertTrue(src("ControlTileFactory.kt").contains("}; state.announce()"), "gói lệnh ô")
    }

    @Test
    fun `resyncTiles do lai ca o widget - xe 3D, bang Den, nhom`() {
        val r = SourceRoots.body(src("KachiHomeRender.kt"), "internal fun KachiHomeActivity.resyncTiles()")
        assertTrue(r.contains("workspace.slotViews.forEach { WidgetViews.refreshRead(it, d) }"), r)
        assertTrue(r.contains("WidgetRefreshers.resyncActions(workspace, car)"), r)
    }

    @Test
    fun `o TOGGLE theo dich giu, o COVER so voi HINH`() {
        val f = src("ControlTileFactory.kt")
        assertTrue(f.contains("LampHold.shared.shown(def.id, car.controls[def.id], android.os.SystemClock.uptimeMillis()) ?: on(def)"), "TOGGLE")
        assertTrue(f.contains("TileResync.stale(tile, (now ?: state.sel(def.id)).coerceIn(0, levels - 1))?.let(::show)"), "COVER")
        assertFalse(f.contains("var open = false"), "cờ riêng của COVER không phải hình đang vẽ")
    }

    @Test
    fun `bang Den so trang thai diem voi cai dang ve`() {
        val b = src("CarIconBoardView.kt")
        assertTrue(b.contains("val now = tiles.map(::kind)") && b.contains("if (now != drawnKinds) { drawnKinds = now; dirty = true }"), "CarIconBoardView.refresh")
    }

    /**
     * Senior review Pass 7 (3.1.7) — KHÔNG VÒNG LẶP VẼ: `announce` → `resyncTiles` → `refresh` của ô/bảng → `record` (đồng bộ bảng với số
     * đọc) KHÔNG được báo lại. `ControlWriteEchoTest` khoá luật ở `:core` (`record` không tăng `relayed`); bài này khoá DÂY: `announce()`
     * chỉ được gọi ở ba đường KHÔNG vẽ (echo phím/giọng nói · gói lệnh ô · gói lệnh giọng nói) — thêm một chỗ gọi trong bộ đổ số
     * (`WidgetViews` · `WidgetRefreshers` · `Car3dTaps` · `CarIconBoardView` · `GroupTileView` · `TileResync` · `KachiHomeRender`…) là
     * hai bề mặt lệch số đọc đẩy nhau thành vòng trên luồng chính ⇒ bài đỏ.
     */
    @Test
    fun `announce chi o duong khong ve - bo do so khong bao - khong vong lap`() {
        val callers = SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        }.filter { p ->
            val code = com.byd.clusternav.testsupport.KotlinSource.stripComments(Files.readString(p))
            Regex("""\.announce\(\)""").containsMatchIn(code)
        }.map { it.fileName.toString() }.toSortedSet()
        assertEquals(
            sortedSetOf("ControlTileFactory.kt", "ControlTileState.kt", "ControlWriteEcho.kt", "VoiceDispatcher.kt"),
            callers, "chỗ gọi announce() ngoài danh sách cho phép — kiểm xem có nằm trên đường vẽ/đổ số không",
        )
        // Hai đường của `ControlLastSent` đổi bảng mà không báo: `record` (ô tự vẽ) · `absorb` tự tăng bộ đếm (cầu :wake, không gọi announce).
        val sent = Files.readString(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/ControlLastSent.kt"))
        val record = SourceRoots.body(sent, "fun record(id: String, index: Int)")
        assertFalse(record.contains("relayedChanges") || record.contains("announce"), "record KHÔNG báo: $record")
        // Trong `ControlTileFactory` chỗ gọi duy nhất là gói lệnh (`macroTile`), không phải hàm `refresh@` của ô nào.
        val factory = com.byd.clusternav.testsupport.KotlinSource.stripComments(src("ControlTileFactory.kt"))
        assertEquals(1, Regex("""\.announce\(\)""").findAll(factory).count(), "ControlTileFactory: đúng một chỗ (gói lệnh)")
        assertTrue(SourceRoots.body(factory, "fun macroTile(macro: ActionMacro): View").contains("state.announce()"), "chỗ đó phải là gói lệnh")
    }
}
