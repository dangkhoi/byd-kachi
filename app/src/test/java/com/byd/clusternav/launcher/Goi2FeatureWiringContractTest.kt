package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá DÂY NỐI của 3 bề mặt người dùng gói 2: bảng lốp 4 bánh (W4) · ô tick tự lấy gió (W3) · chọn đơn vị (R11–R13).
 *
 * Đây là test QUÉT SOURCE (không dựng được View trong JVM thuần). Mọi phép quét đi qua [code] để **bỏ chú thích
 * trước khi kiểm** — nếu không thì chỉ cần viết tên hàm vào một dòng comment là test xanh, tức là test tự lừa mình.
 */
class Goi2FeatureWiringContractTest {

    private fun code(relative: String): String =
        SourceRoots.text(relative)
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .lines().joinToString("\n") { it.substringBefore("//") }

    private val widgets by lazy { code("src/main/java/com/byd/clusternav/launcher/WidgetViews.kt") }
    private val board by lazy { code("src/main/java/com/byd/clusternav/launcher/TyreBoardView.kt") }
    /** Nhóm "Tiện nghi xe" + "Hiển thị & đơn vị" của màn Cài đặt (S1·T3). */
    private val panel by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt") }

    /** Nhóm "Màn hình chính" — lưới khả năng nằm ở đây (tách vì trần 500 dòng). */
    private val panelHome by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsHome.kt") }

    /** Dòng chọn đơn vị — chuyển sang bộ dựng dòng dùng chung (S1·T2). */
    private val rows by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsRows.kt") }
    private val panels by lazy { code("src/main/java/com/byd/clusternav/launcher/HomePanels.kt") }
    private val activity by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val boot by lazy { code("src/main/java/com/byd/clusternav/BootSetupService.kt") }
    private val applier by lazy { code("src/main/java/com/byd/clusternav/comfort/RecircApplier.kt") }
    private val prefs by lazy { code("src/main/java/com/byd/clusternav/launcher/WorkspacePrefs.kt") }

    // ── W4 · bảng áp suất lốp ────────────────────────────────────────────────────────────────────

    @Test
    fun `bang lop dung phan quyet dinh o core chu KHONG tu tinh`() {
        assertTrue(widgets.contains("TyreBoard.readings("), "widget lốp phải lấy trạng thái từ TyreBoard (:core)")
        assertTrue(widgets.contains("TyreBoardView("), "ô lớn phải dùng ô vẽ bảng 4 bánh")
        // [SOÁT] bản cũ dùng `&&` ⇒ viết cứng MỘT ngưỡng vẫn qua. Chặn TỪNG ngưỡng.
        // ⚠ CHỈ hai ngưỡng áp suất là kiểm được bằng chuỗi: ngưỡng lệch `0.3` trùng với **tỉ lệ vẽ** (`0.34f`,
        // `0.30f`) nên quét chuỗi cho nó là dương tính giả — đã thử và nó báo sai ngay. Phần "không tự phán xét"
        // được khoá bằng cấu trúc ở hai assert dưới (ô vẽ chỉ NHẬN kết quả, không gọi bộ quyết định).
        listOf("2.0", "3.2").forEach { th ->
            assertFalse(
                board.contains(th),
                "ô VẼ chứa ngưỡng '$th' — ngưỡng chỉ được nằm ở TyreBoard (:core) để đổi MỘT chỗ",
            )
        }
        // [SOÁT] bản cũ có nhánh `|| contains("TyreReading")` — mà tên KIỂU đó bắt buộc xuất hiện trong chữ ký ô
        // vẽ, nên assert KHÔNG THỂ đỏ. Luật thật: ô vẽ chỉ NHẬN kết quả, không tự tính trạng thái.
        assertTrue(board.contains("TyreReading"), "ô vẽ phải nhận kiểu kết quả đã quyết định từ :core")
        assertFalse(board.contains("TyreBoard.readings("),
            "ô VẼ không được tự gọi bộ quyết định — chỗ gọi là WidgetViews, ô vẽ chỉ nhận kết quả")
    }

    @Test
    fun `KHONG con chia 100 tai cho va KHONG con nguong cung 2 phay 2`() {
        // Đây là hai vết của bản cũ: registry khai kPa nhưng widget tự chia 100 ra bar, và ngưỡng "non" viết
        // thẳng vào bộ vẽ (< 2.2) — lệch với ngưỡng ở :core. Cả hai phải hết.
        assertFalse(widgets.contains("/ 100.0"), "không được tự đổi kPa→bar tại chỗ; phải đi qua UnitFormat")
        assertFalse(widgets.contains("< 2.2"), "không được có ngưỡng lốp viết cứng trong bộ vẽ")
        assertFalse(widgets.contains("fun tyreBars("), "hàm đổi đơn vị cũ phải bị xoá, không để song song")
    }

    @Test
    fun `so lop di qua lop don vi`() {
        assertTrue(widgets.contains("formatPressure("), "phải có một chỗ duy nhất format áp suất")
        assertTrue(widgets.contains("UnitFormat.apply("), "áp suất phải đi qua UnitFormat")
        assertTrue(widgets.contains("Quantity.PRESSURE"), "phải hỏi lựa chọn theo loại áp suất")
    }

    @Test
    fun `nhiet do tren bang lop CUNG phai qua lop don vi`() {
        // [ĐO] máy ảo 2026-09-10: bản đầu ghép "°C" CỨNG trong ô vẽ ⇒ người dùng chọn °F mà bảng vẫn ghi °C.
        // Đúng loại lỗi gói này đi dọn, nên khoá lại.
        assertFalse(board.contains("°C"), "ô vẽ KHÔNG được chứa đơn vị nhiệt cứng — phải nhận chuỗi đã format")
        assertTrue(widgets.contains("formatTemp("), "phải có một chỗ format nhiệt qua lớp đơn vị")
        assertTrue(widgets.contains("Quantity.TEMPERATURE"), "phải hỏi lựa chọn theo loại nhiệt độ")
    }

    @Test
    fun `mot canh bao mot mau`() {
        // Bản đầu: số màu đỏ + dòng phụ màu hổ phách trên CÙNG một bánh ⇒ hai màu cảnh báo, không rõ báo gì.
        // ⚠ Canh QUAN HỆ, không canh cách gõ: dòng phụ phải lấy `col` (chính màu của số) khi trạng thái là cảnh
        // báo. Bản trước so nguyên văn `if (rd.status.alert) col`, nên nó đỏ khi ô vẽ đổi tên biến trạng thái
        // (`rd.status` → `st`, cần thiết vì bánh nay có thể chưa có dữ liệu) dù bất biến không hề đổi.
        assertTrue(
            Regex("""subP\.color = if \(\w+(?:\.\w+)*\.alert\) col""").containsMatchIn(board),
            "dòng phụ phải dùng CHÍNH màu của số khi có cảnh báo",
        )
    }

    @Test
    fun `doi don vi KHONG duoc dung lai o vo co`() {
        val ws = code("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt")
        val fn = SourceRoots.body(ws, "fun setUnitPrefs(")
        assertTrue(fn.contains("if (prefs == unitPrefs) return"),
            "gọi lại với cùng lựa chọn KHÔNG được dựng lại ô — dựng lại là ngắt kênh chạm của app trong ô (C5)")
    }

    @Test
    fun `doi don vi chi dung lai o WIDGET chu khong dung lai o dang chieu app`() {
        // Bản đầu gọi `rebuild()` (dựng lại TẤT CẢ) ⇒ đổi chữ "bar"→"psi" cũng tháo VdAppHost, tạo màn ảo mới và
        // bắt app trong ô mở lại. Đơn vị chỉ ảnh hưởng ô widget — cùng luật WorkspaceRenderPlanner đã áp cho nhịp
        // trạng thái xe (ô App không bị chạm).
        val ws = code("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt")
        val fn = SourceRoots.body(ws, "fun setUnitPrefs(")
        assertFalse(fn.contains("rebuild()"), "KHÔNG được dựng lại toàn bộ ô chỉ vì đổi đơn vị")
        assertTrue(fn.contains("rebuildWidgetSlots()"), "phải dựng lại đúng ô widget")
        val only = SourceRoots.body(ws, "private fun rebuildWidgetSlots()")
        assertTrue(only.contains("!is SlotContent.Widget) continue"), "phải BỎ QUA ô App và ô trống")
        assertFalse(only.contains("removeAllViews()"), "không được xoá sạch con — đó là dựng lại tất cả")
    }

    @Test
    fun `o ve khong cap phat trong onDraw va khong dung API loi thoi`() {
        // [SOÁT] bản cũ lấy "từ onDraw tới hết tệp" ⇒ (a) field khai TRƯỚC onDraw không bị soi, (b) hàm phụ SAU
        // onDraw bị soi oan. Nay lấy đúng thân onDraw.
        val draw = SourceRoots.body(board, "override fun onDraw")
        assertFalse(draw.contains("Paint("), "cấm cấp phát Paint trong onDraw (vẽ lại 2 lần/giây sẽ rác bộ nhớ)")
        // `Color.parseColor` cắt chuỗi + parse số mỗi lần gọi — cũng là cấp phát, đúng thứ KDoc của ô vẽ hứa là
        // không có. Bản đầu gọi nó 6 lần MỖI lượt vẽ (4 bánh + 2 nhãn) mà test cũ chỉ canh `Paint(` nên không bắt.
        assertFalse(draw.contains("Color.parseColor("), "màu phải phân giải MỘT LẦN ở field, không parse trong onDraw")
        listOf("setElegantTextHeight", "setWillNotCacheDrawing", "setChildrenDrawnWithCacheEnabled").forEach {
            assertFalse(board.contains(it), "API '$it' đã lỗi thời (Context7) — không dùng")
        }
    }

    @Test
    fun `bang lop mang dau CHUA KIEM cho phan nhiet (R8)`() {
        // [ĐO] senior review 2026-09-10: `TyreBoard.tempTier` được khai + có bài kiểm hằng số, nhưng KHÔNG bề mặt nào
        // đọc nó ⇒ R8 ("kèm dấu hiệu đúng mức bằng chứng") chưa có trên màn. Thêm nữa `EvidenceTier.needsBadge` chỉ
        // đúng cho OVERDRIVE/DASHCAST nên chấm amber KHÔNG bao giờ áp cho nhiệt lốp (NEEDS_CAR).
        assertTrue(widgets.contains("TyreBoard.tempTier"), "bề mặt phải ĐỌC mức bằng chứng của kênh nhiệt")
        assertTrue(widgets.contains("nhiệt chưa kiểm"), "phải nói rõ nhiệt lốp chưa kiểm trên xe (R8/R10)")
        assertFalse(board.contains("chưa kiểm"), "ô vẽ KHÔNG tự dựng chữ — chuỗi do chỗ gọi đưa")
    }

    @Test
    fun `ba danh sach song song cua bang lop dung tu CUNG mot nguon`() {
        // Rủi ro thật của 3 danh sách song song không phải độ dài mà là LỆCH THỨ TỰ. Khoá lại: cả values lẫn temps
        // phải map trên CHÍNH danh sách readings (một biểu thức, một thứ tự), không tự đọc lại CarStatus lần nữa.
        val fn = SourceRoots.body(widgets, "private fun tyreBoard(")
        assertTrue(fn.contains("val readings = TyreBoard.readings("), "phải có đúng một nguồn readings")
        assertTrue(fn.contains("readings.map"), "values/temps phải map trên chính readings đó")
        assertEquals(2, Regex("""readings\.map""").findAll(fn).count(), "đúng 2 danh sách song song sinh từ readings")
    }

    // ── W3 · ô tick tự lấy gió trong ─────────────────────────────────────────────────────────────

    @Test
    fun `o tick nam o be mat dung bang code va co canh bao chua kiem tren xe`() {
        assertTrue(panel.contains("recircRow("), "ô tick phải nằm trong màn Cài đặt (dựng bằng code)")
        assertTrue(
            panel.contains("chưa kiểm trên xe"),
            "PHẢI có chú thích chưa-kiểm cạnh ô tick (R10) — lệnh lấy gió chưa xác nhận trên xe owner",
        )
    }

    @Test
    fun `bat thi ap ngay tat thi chi dat lai co`() {
        val block = SourceRoots.body(panels, "onRecircOnStart")
        assertTrue(block.contains("setRecircOnStartEnabled"), "phải lưu bền lựa chọn")
        assertTrue(block.contains("if (on)") && block.contains("applyNowAsync"),
            "bật thì áp NGAY, không chờ lần nổ máy sau")
        assertFalse(block.contains("toggle(\"recirc\", false)"),
            "tắt ô tick KHÔNG được tắt chế độ đang bật trên xe")
    }

    @Test
    fun `ap luc khoi dong va suy giam an toan`() {
        assertTrue(boot.contains("RecircApplier.applyOnStart"), "phải được gọi trong chuỗi khởi động")
        val pos = boot.indexOf("RecircApplier.applyOnStart")
        val pm25 = boot.indexOf("Pm25FilterApplier.applyOnStart")
        assertTrue(pm25 in 0 until pos, "phải đặt SAU hai bộ đã proven để nếu nó hỏng thì không ảnh hưởng chúng")
        assertTrue(applier.contains("runCatching"), "phải bắt mọi lỗi — không được kéo sập chuỗi khởi động")
        assertTrue(applier.contains("recircOnStartEnabled"), "phải có cổng theo công tắc (mặc định TẮT)")
    }

    // ── R11–R13 · chọn đơn vị ────────────────────────────────────────────────────────────────────

    @Test
    fun `bang chon don vi chi bay loai thuc su co dung`() {
        assertTrue(panel.contains("UnitFormat.quantitiesInUse()"),
            "chỉ bày loại đại lượng có mục thật, không bày lựa chọn giả (R13)")
        assertTrue(rows.contains("Units.options("), "danh sách lựa chọn phải lấy từ bảng tra")
        assertTrue(panel.contains("unitRow("), "phải có hàng chọn cho từng loại")
    }

    @Test
    fun `doi don vi thi luu ben va ap lai ngay cho ca hai vung`() {
        val block = SourceRoots.body(activity, "onUnitPrefs =")
        assertTrue(block.contains("setUnitPrefs("), "phải lưu bền")
        assertTrue(block.contains("dock.setCarStatus("), "thanh nút phải cập nhật ngay")
        assertTrue(block.contains("workspace.setUnitPrefs("), "ô giữa màn phải cập nhật ngay")
        assertTrue(prefs.contains("UnitPrefs.decode(") && prefs.contains(".encode()"),
            "lưu bền phải đi qua encode/decode của :core")
    }

    @Test
    fun `bang chon bay CA hai loai kha nang`() {
        assertTrue(
            panelHome.contains("CapabilityCatalog.byDomain()"),
            "bảng chọn phải bày cả ĐỌC lẫn HÀNH ĐỘNG — nếu chỉ bày nút thì người dùng không có đường thêm ô đọc " +
                "vào thanh, và việc nới cổng ở DockConfig thành vô nghĩa",
        )
        assertFalse(panelHome.contains("ControlPanels.byDomain()"), "không còn dùng danh sách chỉ-có-nút")
    }
}
