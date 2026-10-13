package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.byd.clusternav.testsupport.SourceRoots

/**
 * 3.01 R22 — khoá ảnh owner 13/10 nhóm *Kính* (*"rối mà"* · *"visual thiết kế… cho đẹp"*) ở tầng vẽ:
 *  1. ô nhóm có bảng vật × việc thì dựng [ActionMatrixView] (hình xe → danh sách gọn) và KHÔNG dựng thêm hàng nút phẳng;
 *  2. một luật màu số: "đang bật" (ACTIVE) không đổi màu chữ — chỉ cảnh báo đổi;
 *  3. lưới không để hàng cuối bám trái (ô trống lẻ);
 *  4. tầng vẽ bảng không chép mã thành viên / mã màu / tên nhóm (CLAUDE.md §7 — mọi rẽ nhánh đến từ dữ liệu `:core`).
 */
class ActionMatrixWiringContractTest {

    private val dir = "src/main/java/com/byd/clusternav/launcher/"
    private fun code(f: String) = SourceRoots.codeOf(dir + f)
    private val matrixFiles = listOf(
        "ActionMatrixView.kt", "ActionMatrixList.kt", "ActionMatrixCompact.kt", "CarLevelsLayer.kt", "IconBoardView.kt",
        "GroupBodies.kt", "CarIconBoardView.kt",
    )

    @Test
    fun `o nhom dung bang vat x viec thay cho hang nut phang`() {
        val tile = code("GroupTileViews.kt")
        assertTrue(tile.contains("matrix = GroupBodies.of(context, model, data)"), "thân hình dựng từ model :core")
        val bodies = code("GroupBodies.kt")
        assertTrue(bodies.contains("GroupActionMatrix.of(m)?.let { ActionMatrixView(") && bodies.contains("GroupIconBoard.of(m)?.let { CarIconBoardView("))
        // R24 (owner 13/10 "cái widget đèn cũng làm vậy luôn, có hình xe…"): bảng ICONS lên hình xe, lưới icon là dạng LÙI bên trong.
        assertTrue(code("CarIconBoardView.kt").contains("IconBoardView(context, tiles, data)"), "lưới icon vẫn là dạng lùi")
        assertTrue(tile.contains("model.hasActions && matrix == null"), "có thân hình ⇒ không dựng hàng nút phẳng lần hai")
        assertTrue(tile.contains("(matrix as? GroupBody)?.refresh(model)"), "số trong thân đổi tại chỗ theo nhịp xe")
        assertTrue(code("ActionMatrixView.kt").contains("k.fits(w, h)"), "hình xe chỉ khi vừa khung, không thì danh sách")
        assertTrue(code("ActionMatrixList.kt").contains("GroupActionMatrix.plan("), "danh sách co giãn theo :core")
    }

    @Test
    fun `mot luat mau so - dang bat khong doi mau chu`() {
        val tint = SourceRoots.body(code("GroupTileViews.kt"), "fun tintOf(")
        assertTrue(tint.contains("GroupTone.NEUTRAL, GroupTone.ACTIVE -> KachiTheme.INK"), tint)
        assertFalse(tint.contains("ACTIVE -> KachiTheme.ACCENT"), "màu nhấn trên chữ số là thứ owner thấy 'không có luật'")
    }

    @Test
    fun `luoi khong de hang cuoi bam trai`() {
        val grid = code("GroupCellGrid.kt")
        assertEquals(2, Regex("""val lead = \(""").findAll(grid).count(), "lưới ô con + lưới nút đều canh giữa hàng ngắn")
    }

    @Test
    fun `tang ve bang khong chep ma thanh vien, ma mau, ten nhom`() {
        matrixFiles.forEach { f ->
            val c = code(f)
            listOf("\"win_", "\"window_", "\"g_windows", "\"g_lights", "\"door_", "CapabilityGroups.").forEach {
                assertFalse(c.contains(it), "$f chép tay $it — rẽ nhánh phải đến từ dữ liệu :core")
            }
            assertEquals(0, Regex(""""#[0-9a-fA-F]{6,8}"""").findAll(c).count(), "$f: màu chỉ từ KachiTheme")
            assertTrue(c.lines().size <= 500, "$f vượt trần 500 dòng")
        }
    }

    @Test
    fun `nut trong bang khong xuong dong`() {
        val kit = SourceRoots.body(code("ActionMatrixView.kt"), "fun tile(")
        assertTrue(kit.contains("it.maxLines = 1"), "nhãn nút một dòng — bề rộng đã đo đủ chữ")
        assertTrue(kit.contains("ControlTileFactory") || code("ActionMatrixView.kt").contains("ControlTileFactory("), "cùng bộ dựng nút")
    }

    /**
     * R22·B — ô Kính 2×3 của owner: danh sách gọn BỎ `Kính SP` + `4 kính` (thiếu cao). Hình xe gọn chen giữa hình xe đầy đủ
     * và danh sách, vật trên hình xe là đích chạm, và chạm đi qua ô nút THẬT (không đường ghi thứ hai).
     */
    @Test
    fun `hinh xe gon chen giua hinh xe day du va danh sach, cham qua o nut that`() {
        val view = code("ActionMatrixView.kt")
        val order = listOf("k.fits(w, h) -> k", "else -> list").map { view.indexOf(it) }
        assertTrue(order.all { it >= 0 } && order == order.sorted(), "thứ tự lùi: hình xe → danh sách (chỉ dưới ngưỡng): $order")
        // Owner 13/10: lưới nút chữ lặp bị bác ở MỌI cỡ ⇒ không còn bản hình xe có thẻ + nút chữ cạnh từng cửa.
        assertFalse(SourceRoots.exists("src/main/java/com/byd/clusternav/launcher/ActionMatrixBoard.kt"))
        val compact = code("ActionMatrixCompact.kt")
        assertTrue(compact.contains("GroupActionMatrix.compactForm("), "phép vừa-khung ở :core")
        // 3.01 TAPFIX (owner 13/10 "click lên xa trên top… không đúng vùng"): chủ cú chạm = chấm có tâm VẼ gần nhất trong tầm,
        // quyết ở :core, tâm lấy từ CHÍNH hàm lớp vẽ dùng — không còn phần tư/dải nở tới mép ô.
        assertFalse(compact.contains("GroupActionMatrix.zones("), "vùng chữ nhật nở tới mép ô đã gỡ")
        assertTrue(compact.contains("SpotTouch.owner(") && compact.contains("levels.centerOf("), "chạm theo tâm vẽ thật, quyết ở :core")
        assertTrue(compact.contains("SpotTouch.reachPx(touch)"), "ngoài tầm ⇒ không lệnh")
        assertTrue(SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/CarLevelsLayer.kt").let {
            it.contains("val (x, y) = centerOf(p)") && it.contains("SpotTouch.fitCenter(")
        }, "lớp vẽ dùng CÙNG hàm ánh xạ tâm")
        assertTrue(compact.contains("kit.tile(") && compact.contains("?.performClick()"), "chạm = performClick ô nút dựng bằng ControlTileFactory")
        assertFalse(compact.contains("control()") || compact.contains(".toggle(") || compact.contains(".press("), "không tự ghi HAL")
        // Cùng lớp mức kính với hình xe đầy đủ — không vẽ kính hai cách.
        assertTrue(compact.contains("CarLevelsLayer("))
    }

    /**
     * 3.01 R22·C (owner 13/10 *"nhấn vô các hình tròn cho nó mở đóng luôn… thêm 1 icon mở all, 1 icon đóng all là xong…
     * thêm cả cốp vào đó luôn"*): không công tắc chế độ, chân đúng HAI nút hình chọn ở `:core`, chạm chấm đi vòng ở `:core`
     * và ghi qua ô nút thật (đặt tường minh = CÙNG hàm ghi của ô), cốp lên hình xe bằng nút SẴN CÓ.
     */
    @Test
    fun `cham cham di vong o core, chan hai nut, cop la nut san co`() {
        val compact = code("ActionMatrixCompact.kt")
        listOf("segment(", "select(", "mx.verbs", "showMode", "WindowGlyph").forEach {
            assertFalse(compact.contains(it), "công tắc chế độ / glyph Canvas cũ còn sót: $it")
        }
        assertTrue(compact.contains("WindowCycle.next(") && compact.contains("WindowCycle.plan("), "vòng chạm quyết ở :core")
        assertTrue(compact.contains("kit.set(step.action.id"), "ghi qua ô nút thật (ActionTile.set)")
        assertTrue(compact.contains("mx.openAll") && compact.contains("mx.closeAll"), "chân = mở/đóng tất cả chọn ở :core")
        assertTrue(compact.contains("R.drawable.ic_win_open_all_g") && compact.contains("R.drawable.ic_win_close_all_g"))
        // 3.01 POLISH — owner 13/10 *"phần kính thêm 1 action là 50% tất cả kính"*: nút thứ BA (hé tất cả) GIỮA mở và đóng.
        assertTrue(compact.contains("mx.halfAll") && compact.contains("R.drawable.ic_win_half_all_g"), "chân có nút hé tất cả")
        assertTrue(compact.indexOf("mx.openAll?.let") < compact.indexOf("mx.halfAll?.let") &&
            compact.indexOf("mx.halfAll?.let") < compact.indexOf("mx.closeAll?.let"), "thứ tự chân: mở · hé · đóng")
        assertEquals(3, Regex("barButton\\(R\\.drawable").findAll(compact).count(), "chân đúng BA nút hình")
        assertTrue(compact.contains("mx.onCar"), "cốp lên hình xe theo dữ liệu (placeOf + atCarEnd)")
        // Owner 13/10 "khi click sao nó có mảng xanh hiện lên vậy": vùng chạm ≥ 48 dp KHÔNG có nền/sóng — phản hồi = chấm nảy.
        val zone = SourceRoots.body(compact, "private fun zoneView(")
        assertFalse(zone.contains("background") || zone.contains("ripple("), "vùng chạm còn nền nhấn (mảng xanh): $zone")
        assertTrue(zone.contains("levels.pulse(p)"), "phản hồi chạm = chấm nảy")
        assertTrue(SourceRoots.body(compact, "private fun barButton(").contains("GradientDrawable.OVAL"), "nút chân: sóng chỉ trong đĩa tròn")
        // Đặt tường minh dùng CÙNG hàm ghi với cú chạm ô — không đường HAL thứ hai.
        val toggle = SourceRoots.body(code("ControlTileFactory.kt"), "private fun tileToggle(")
        assertTrue(toggle.contains("tile.setOnClickListener { write(!state.isOn(def.id)) }") && toggle.contains("setter { write(it > 0) }"), toggle)
    }

    /**
     * R22·B — chữ giữ chỗ (`—`) KHÔNG làm mờ bằng alpha: [ĐO] mực × 0,5 = 3,15:1 ở bảng sáng (§6 diagnostics). Một luật:
     * `KachiWidgetType.pending` (vai MUT, đã khoá ≥ 4,5:1 ở `SurfaceContrastContractTest`).
     */
    @Test
    fun `chu giu cho dung vai MUT, khong alpha`() {
        val files = listOf("GroupTileParts.kt", "ReadTile.kt", "ActionMatrixView.kt", "WidgetTelemetry.kt")
        files.forEach { f ->
            val c = code(f)
            assertTrue(c.contains("KachiWidgetType.pending("), "$f: chữ chưa đọc phải đi qua KachiWidgetType.pending")
            val dim = Regex("""\.alpha\s*=\s*if\s*\([^)]*(available|dim)""").findAll(c).map { it.value }.toList()
            assertEquals(emptyList<String>(), dim, "$f: làm mờ chữ chưa đọc bằng alpha (3,15:1 ở bảng sáng)")
        }
        val pending = SourceRoots.body(code("KachiWidgetType.kt"), "fun pending(")
        assertTrue(pending.contains("KachiTheme.MUT") && pending.contains("alpha = 1f"), pending)
    }

    /** R22·B — bảng icon: chạm ô = ô nút thật; không mở đường ghi thứ hai; lưới chọn ở :core. */
    @Test
    fun `bang icon cham qua o nut that, luoi o core`() {
        val v = code("IconBoardView.kt")
        assertTrue(v.contains("GroupIconBoard.grid("), "lưới ở :core")
        assertTrue(v.contains("kit.tile(") && v.contains("hidden.performClick()"))
        assertFalse(v.contains("control()") || v.contains(".toggle(") || v.contains(".press("), "không tự ghi HAL")
    }
}
