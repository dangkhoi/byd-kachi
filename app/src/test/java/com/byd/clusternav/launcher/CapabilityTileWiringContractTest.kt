package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá DÂY NỐI của RW0 ("ô khả năng hợp nhất" — spec `docs/specs/kachi-unified-capability-tile.html`): những bất
 * biến nằm ở chỗ ghép giữa Activity và View Android, nơi test đơn vị không tới được (dự án không dùng Robolectric).
 *
 * Bốn nhóm, đúng hai chiều R2 + hai ràng buộc:
 *  - **Đ4 · bơm trạng thái xe vào thanh nút**: thanh nút phải NHẬN được [CarStatus] + [UnitPrefs], và Activity phải
 *    bơm nó cùng lúc với thanh trên.
 *  - **C5 · không nháy**: nhịp trạng thái xe chỉ đổ lại số của ô ĐỌC, KHÔNG dựng lại thanh, KHÔNG chạm ô hành động.
 *  - **ranh giới ĐỌC/HÀNH ĐỘNG**: ô đọc KHÔNG gắn chạm; số của ô đọc BẮT BUỘC đi qua lớp đơn vị.
 *  - **R2 chiều hai + bảo toàn**: ô giữa màn rẽ nhánh theo [CapabilityCatalog.isWrite]; 8 widget dựng tay và đường
 *    telemetry giữ nguyên.
 */
class CapabilityTileWiringContractTest {

    private val dock by lazy { code("src/main/java/com/byd/clusternav/launcher/ControlDockView.kt") }
    private val factory by lazy { code("src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt") }
    private val widgets by lazy { code("src/main/java/com/byd/clusternav/launcher/WidgetViews.kt") }
    private val activity by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val workspace by lazy { code("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt") }
    private val drawer by lazy { code("src/main/java/com/byd/clusternav/launcher/AppDrawer.kt") }
    /** Nhóm "Màn hình chính" của màn Cài đặt (S1·T3) — lưới khả năng nằm ở đây. */
    private val bars by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsBars.kt") }

    /**
     * Đọc source rồi **bỏ mọi chú thích** trước khi quét: test này canh **CODE**, không canh văn xuôi. Cùng lý do
     * với `OpenAppWiringContractTest.code` — (a) câu giải thích thường nhắc chính tên hàm đang bị cấm gọi ⇒ quét thô
     * sẽ báo sai; (b) chặn kiểu "đạt test" bằng cách viết token vào comment thay vì nối dây thật.
     */
    private fun code(relative: String): String = SourceRoots.text(relative)
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    // ── Đ4: thanh nút nhận trạng thái xe ─────────────────────────────────────────────────────────

    @Test
    fun `thanh nut NHAN trang thai xe va lua chon don vi`() {
        assertTrue(dock.contains("fun setCarStatus("), "thanh nút phải có đường bơm trạng thái xe (sự thật Đ4)")
        assertTrue(
            Regex("""fun setCarStatus\([^)]*status:\s*CarStatus""").containsMatchIn(dock),
            "phải nhận CarStatus",
        )
        assertTrue(
            Regex("""fun setCarStatus\([^)]*prefs:\s*UnitPrefs""").containsMatchIn(dock),
            "phải nhận lựa chọn đơn vị của người dùng (R11) — không thì thanh nút tự ép đơn vị",
        )
        assertTrue(dock.contains("private var carStatus"), "thanh nút phải giữ trạng thái xe đã bơm để dựng ô đọc")
    }

    @Test
    fun `Activity bom trang thai xe vao thanh nut CUNG LUC voi thanh tren`() {
        val fn = SourceRoots.body(activity, "private fun render(state: HomeUiState)")
        // Prefix: chip thanh trên nay nhận thêm lựa chọn đơn vị (R11) nên chữ ký dài hơn — khoá sự TỒN TẠI
        // của lời gọi, không khoá số tham số.
        assertTrue(fn.contains("topStrip.refreshChips(state.carStatus"), "thanh trên vẫn được làm mới như cũ")
        assertTrue(fn.contains("dock.setCarStatus(state.carStatus, unitPrefs)"), "thanh nút phải được bơm cùng chỗ")
        val guard = fn.indexOf("prev.carStatus != state.carStatus")
        val chips = fn.indexOf("topStrip.refreshChips(state.carStatus")
        val dockAt = fn.indexOf("dock.setCarStatus(state.carStatus")
        assertTrue(guard in 1 until chips && chips < dockAt, "cả hai phải nằm trong CÙNG nhánh 'trạng thái xe đổi'")
        assertTrue(
            activity.contains("viewModel.uiState.value.unitPrefs"),
            "lựa chọn đơn vị phải đọc từ NGUỒN SỰ THẬT (HomeUiState), không phải bản sao/hằng số trong view. " +
                "Trước lượt soát 2026-09-11 thứ này có 4 bản sao đồng bộ bằng tay.",
        )
    }

    // ── C5: nhịp trạng thái xe KHÔNG dựng lại thanh ──────────────────────────────────────────────

    @Test
    fun `cap nhat trang thai xe CHI do lai so cua o DOC`() {
        val fn = SourceRoots.body(dock, "fun setCarStatus(")
        assertTrue(fn.contains("readTiles"), "phải đi qua danh sách ô ĐỌC đang hiện")
        assertTrue(fn.contains(".bind("), "phải đổ số TẠI CHỖ vào ô đã dựng")
        listOf("rebuild()", "removeAllViews()", "addView(", "control").forEach {
            assertFalse(
                fn.contains(it),
                "nhịp trạng thái xe KHÔNG được $it — dựng lại/chạm ô hành động là nháy + mất trạng thái vừa bấm (C5)",
            )
        }
    }

    // ── Ranh giới ĐỌC / HÀNH ĐỘNG ────────────────────────────────────────────────────────────────

    @Test
    fun `o DOC KHONG gan cham`() {
        val fn = SourceRoots.body(factory, "fun readTile(")
        assertFalse(
            fn.contains("setOnClickListener"),
            "thông tin đọc KHÔNG phải nút — gắn chạm vào đây là xoá ranh giới ĐỌC/HÀNH ĐỘNG của cả gói",
        )
        assertTrue(fn.contains("TelemetryView.PLACEHOLDER"), "chưa đọc được phải hiện dấu gạch ngang, KHÔNG bịa số")
    }

    @Test
    fun `so cua o DOC di qua lop don vi`() {
        assertTrue(
            Regex("""UnitFormat\.apply\([^)]*unitPrefs""").containsMatchIn(dock),
            "giá trị phải đi qua UnitFormat với lựa chọn của người dùng (R11/R12), không hiện thẳng đơn vị gốc",
        )
        val fn = SourceRoots.body(dock, "private fun readout(")
        assertTrue(fn.contains("TelemetryReadout.of("), "đọc số qua đúng một cửa (TelemetryReadout)")
    }

    @Test
    fun `thanh nut dung BO DUNG O DUNG CHUNG chu khong tu dung o nut`() {
        assertTrue(dock.contains("ControlTileFactory("), "thanh nút phải gọi bộ dựng chung")
        listOf("private fun tileToggle(", "private fun tileStep(", "private fun tileCover(", "private fun tileSelect(", "private fun tileButton(")
            .forEach { assertFalse(dock.contains(it), "$it phải nằm ở bộ dựng chung, không còn trong thanh nút") }
        assertTrue(dock.contains("CapabilityKind.WRITE"), "thanh nút phải phân loại khả năng trước khi dựng ô")
        assertTrue(dock.contains("CapabilityKind.READ"), "thanh nút phải dựng được cả ô ĐỌC (R2 chiều một)")
    }

    // ── R2 chiều hai: hành động trong ô giữa màn ──────────────────────────────────────────────────

    @Test
    fun `o giua man re nhanh theo loai kha nang va dung CUNG bo dung o`() {
        assertTrue(widgets.contains("CapabilityCatalog.isWrite(id)"), "ô giữa màn phải rẽ nhánh theo loại khả năng")
        assertTrue(widgets.contains("ControlTileFactory("), "hành động trong ô phải dùng CÙNG bộ dựng với thanh nút")
        assertTrue(workspace.contains("var control: CarControlPort"), "ô giữa màn cần đường ra xe để bấm được")
        // Khoá Ý ĐỊNH, không khoá nguyên văn danh sách tham số: bản đầu khớp đúng chuỗi
        // "WidgetData(carStatus, mediaProvider(), onMedia, control)" nên vừa thêm tham số ĐƠN VỊ (R11) là đỏ, dù
        // cổng ra lệnh vẫn chảy xuống đúng. Nay kiểm: có dựng WidgetData với cổng ra lệnh, VÀ có truyền đơn vị.
        // Khoá Ý ĐỊNH: cổng ra lệnh + đơn vị phải chảy xuống bộ dựng widget. KHÔNG khoá nguyên văn danh sách tham
        // số — bản trước làm vậy nên vừa thêm bộ nhớ tạm cho lượt đọc nhạc (P2) là đỏ oan dù luật không đổi.
        assertTrue(
            Regex("""WidgetData\(carStatus,[^)]*control""").containsMatchIn(workspace),
            "cổng ra lệnh phải chảy xuống bộ dựng widget",
        )
        assertTrue(
            workspace.contains("unitPrefs)") || workspace.contains("unitPrefs,"),
            "lựa chọn đơn vị (R11) phải chảy xuống bộ dựng widget",
        )
        assertTrue(activity.contains("control = container.carControl"), "Activity phải nối cổng ra lệnh thật vào ô")
    }

    @Test
    fun `8 widget dung tay va duong telemetry giu NGUYEN`() {
        listOf("w_clock", "w_energy", "w_pm25", "w_speed", "w_tire", "w_media", "w_car", "w_board").forEach {
            assertTrue(widgets.contains("\"$it\" ->"), "widget dựng tay $it phải còn nhánh riêng (không gộp — §4.7)")
        }
        // Prefix (không đóng ngoặc) — đường telemetry vẫn là nhánh mặc định cho ô ĐỌC, nhưng nay nhận thêm lựa
        // chọn đơn vị nên chữ ký dài hơn. Khoá sự TỒN TẠI của nhánh, không khoá số tham số.
        assertTrue(widgets.contains("telemetry(ctx, id, data.car"), "đường telemetry (ô ĐỌC) phải giữ nguyên")
        assertTrue(
            widgets.contains("telemetry(ctx, id, data.car, data.units)"),
            "ô ĐỌC ở giữa màn phải theo lựa chọn đơn vị của người dùng (R11)",
        )
        assertTrue(widgets.contains("telemetryMini(ctx, id, car"), "đường telemetry trong lưới phải giữ nguyên")
        assertEquals(
            2,
            Regex("""CapabilityCatalog\.isWrite\(id\)""").findAll(widgets).count(),
            "đúng 2 chỗ rẽ nhánh: ô một-widget và ô lưới nhiều-widget",
        )
    }

    /**
     * RW0 — ngăn kéo phải bày CẢ hành động, không chỉ mục đọc.
     *
     * ⚠ Trước 2026-09-11 chỗ này gọi `WidgetCatalog.telemetryByDomain()` = **chỉ mục ĐỌC** ⇒ tuy `WidgetViews` vẽ
     * được ô hành động thì người dùng vẫn **không có nút nào** để đặt hành động/gói lệnh vào ô giữa màn. Bài này khoá
     * ngăn kéo và màn Cài đặt dùng **CÙNG một nguồn**, để hai màn chọn không thể lệch nhau.
     */
    @Test
    fun `ngan keo phai bay ca hanh dong khong chi muc doc`() {
        assertTrue(
            drawer.contains("CapabilityCatalog.byDomain()"),
            "ngăn kéo phải lấy từ không gian khả năng (đọc + hành động + gói lệnh)",
        )
        assertTrue(
            !drawer.contains("WidgetCatalog.telemetryByDomain()"),
            "không được quay lại nguồn CHỈ-ĐỌC — đó chính là chỗ chặn cũ",
        )
        assertTrue(
            drawer.contains("pick.displayLabel"),
            "hai loại nằm cùng một lưới ⇒ phải dùng nhãn có gợi ý loại ở chỗ nhãn trùng",
        )
        // T4 · IA v2 R-UI (m): lưới 123 ô đã RỜI khỏi màn Cài đặt — nhóm "Thanh trạng thái & thanh nút" nay
        // mở CHÍNH bộ chọn của ngăn kéo (`AppDrawer.Mode.PICK_DOCK`). Một bộ chọn, một nguồn ⇒ phép so "hai
        // màn phải giống nhau" không còn đối tượng, và `CapabilityGridSection` đã bị xoá.
        // Chỗ duy nhất còn phải canh là màn Cài đặt **mở đúng bộ chọn đó**, không dựng bản thứ hai.
        assertTrue(
            bars.contains("deps.openDockPicker("),
            "màn Cài đặt phải MỞ bộ chọn của ngăn kéo, không dựng lưới ô thứ hai",
        )
        assertFalse(bars.contains("CapabilityCatalog.byDomain()"), "và không được tự duyệt lại không gian khả năng")
    }

}
