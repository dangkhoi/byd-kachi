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
    private val car by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsCar.kt") }
    private val bridgeKt by lazy { code("src/main/java/com/byd/clusternav/launcher/ClusterNavBridge.kt") }
    private val bridgeSystemKt by lazy { code("src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeSystem.kt") }   // `applyRecircNow` tách sang tệp mở rộng (L6-debt 2026-09-27)
    private val drawerKt by lazy { code("src/main/java/com/byd/clusternav/launcher/AppDrawer.kt") }

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
        // U5·T3 — chữ dời sang tài nguyên: kiểm CẢ dây nối (mã gọi khoá) LẪN nội dung (chữ thật vẫn nói "chưa kiểm").
        assertTrue(
            widgets.contains("R.string.kachi_tyre_temp_unverified"),
            "bề mặt phải dựng chân bảng từ chuỗi 'nhiệt chưa kiểm'",
        )
        assertTrue(
            res("kachi_tyre_temp_unverified").contains("chưa kiểm"),
            "phải nói rõ nhiệt lốp chưa kiểm trên xe (R8/R10)",
        )
        assertFalse(board.contains("chưa kiểm"), "ô vẽ KHÔNG tự dựng chữ — chuỗi do chỗ gọi đưa")
        // ⚠⚠ 2.74 · R2 — ghim CẢ BỀ MẶT, không chỉ chỗ dựng chuỗi. Lỗ cũ: `84f91e6` gỡ dòng kết luận dưới bảng
        // (owner 2026-09-23) nhưng chuỗi vẫn được dựng + truyền vào ô vẽ ⇒ [ĐO] `grep drawText` trong TyreBoardView
        // = 0 chỗ vẽ nó ⇒ đường CHẾT sống 3 tháng với test xanh (đúng bệnh CLAUDE.md §8: compile xanh ≠ có ai gọi).
        // Nay chuỗi đi vào NHÃN TRỢ NĂNG của cả ô (TalkBack/uiautomator đọc được) — và bài này canh đúng chỗ đó.
        val set = SourceRoots.body(board, "fun set(")
        assertTrue(
            set.contains("contentDescription = summary"),
            "câu kết luận + dấu 'nhiệt chưa kiểm' phải hạ cánh xuống MỘT bề mặt thật (nhãn trợ năng của ô)",
        )
        // …và bề mặt đó phải THẬT đến được người dùng TalkBack: [ĐO AOSP android-10.0.0_r47 `View.java:12691-12713`]
        // `isImportantForAccessibility()` ở chế độ AUTO KHÔNG xét `contentDescription` (chỉ xét bấm/focus/listener/
        // pane) — bảng lốp không bấm được, nên thiếu dòng dưới thì nhãn chỉ có `uiautomator` đọc được ⇒ lại gần-chết.
        assertTrue(
            board.contains("importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES"),
            "phải đánh dấu ô là QUAN TRỌNG với trợ năng, không thì nhãn kia TalkBack không đọc",
        )
        assertTrue(
            widgets.contains("boardView.set(readings, values, unit, temps, summary)"),
            "chỗ gọi phải truyền chuỗi đó vào bảng",
        )
    }

    // ── 2.74 · R2 · hình học ô giá trị: hằng ma thuật → hàm THUẦN ở :core ─────────────────────────

    @Test
    fun `o gia tri lay hinh hoc tu CellTextLayout, khong con hang ma thuat trong ve`() {
        // [ĐO ảnh owner 2026-09-25] (A) chữ dán mép trên chừa ~66 px dưới ⇐ `numBase = centerY + big*0.10 −
        // sub*0.60`; (C)/(D) xe bị thẻ cắt ⇐ thẻ chạy tới `wheelX ∓ gap` (neo bánh nằm TRONG thân xe). Cả ba phép
        // tính nay ở `:core` (`CellTextLayoutTest` kiểm bằng số) ⇒ bài này chỉ canh DÂY NỐI: ô vẽ phải GỌI chúng.
        val draw = SourceRoots.body(board, "override fun onDraw")
        assertTrue(draw.contains("CellTextLayout.cardSpanX("), "mép thẻ phải kẹp theo khung ảnh thật, không theo neo bánh")
        val cell = SourceRoots.body(board, "private fun drawCell(")
        assertTrue(cell.contains("CellTextLayout.twoLineTopBaseline("), "baseline khối 2 dòng phải tính từ số đo phông")
        assertTrue(cell.contains("CellTextLayout.lineStartX("), "con số phải nằm đúng trục ô (đơn vị treo bên phải)")
        assertTrue(cell.contains("CellTextLayout.fitScale("), "thẻ hẹp đi sau khi kẹp ⇒ chữ phải co cho vừa")
        for (magic in listOf("* 0.10f", "* 0.60f")) {
            assertFalse(cell.contains(magic), "hằng ma thuật '$magic' đã quay lại — đó chính là lỗi (A) của owner")
        }
        // Thẻ vẽ SAU ảnh xe (FrameLayout xếp lớp) nên nó KHÔNG được lấn vào khung ảnh; chấm thì vẫn ở neo thật.
        assertTrue(draw.contains("canvas.drawCircle(wheelX, wheelY"), "chấm trạng thái vẫn ở neo bánh thật")
        // Nhãn ngắn của bánh phải đi qua lớp ngôn ngữ (cùng luật đã áp cho ô nhóm: 'Lốp TT' không được lọt sang EN).
        assertTrue(cell.contains("corner.displayShortLabel"), "viết tắt bánh phải theo ngôn ngữ đang dùng")
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
        // T4: ô tick chuyển sang [SettingsCarSection] cùng lúc nhóm "Tiện nghi xe" nhận thêm ghế + lọc bụi mịn.
        assertTrue(car.contains("R.string.kachi_recirc_title"), "ô tick phải nằm trong màn Cài đặt (dựng bằng code)")
        // U5·T3 — chữ dời sang tài nguyên; kiểm cả dây nối lẫn nội dung (xem [res]).
        assertTrue(car.contains("R.string.kachi_recirc_sub"), "dòng phụ của ô tick phải là chuỗi cảnh báo đó")
        assertFalse(
            res("kachi_recirc_sub").contains("chưa kiểm"),
            "V1 (owner 2026-09-25): lấy gió ĐÃ kiểm trên xe — bỏ chú thích chưa-kiểm thừa",
        )
    }

    @Test
    fun `bat thi ap ngay tat thi chi dat lai co`() {
        // T4: hành vi chuyển NGUYÊN từ `HomePanels.onRecircOnStart` sang [SettingsCarSection.recirc]; cả đường GHI
        // (`bridge.setRecircOnStart` → `Prefs.setRecircOnStartEnabled`) lẫn đường ÁP NGAY (`bridge.applyRecircNow` →
        // `RecircApplier.applyNowAsync`) đều đi qua cầu (IA v2 · N2, 2026-09-13: 0 ngoại lệ).
        val block = SourceRoots.body(car, "private fun recirc(")
        assertTrue(block.contains("setRecircOnStart("), "phải lưu bền lựa chọn")
        assertTrue(bridgeKt.contains("fun setRecircOnStart("), "và đường lưu bền phải nằm ở CẦU, không ở section")
        assertTrue(block.contains("if (on)") && block.contains("applyRecircNow"),
            "bật thì áp NGAY, không chờ lần nổ máy sau")
        assertTrue(bridgeSystemKt.contains("fun ClusterNavBridge.applyRecircNow()") && bridgeSystemKt.contains("RecircApplier.applyNowAsync(app)"),
            "đường áp ngay nằm ở CẦU và gọi đúng RecircApplier.applyNowAsync")
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
        // T4: khối nối dây HomePanels chuyển sang `KachiHomeWiring.homePanels(...)` để Activity về ≤ 500 dòng;
        // lambda "đổi đơn vị" vẫn ở Activity (nó chạm `dock`/`workspace`/`topStrip` — thứ chỉ Activity giữ) nhưng
        // đổi tên tham số thành `onUnitsChanged`.
        val block = SourceRoots.body(activity, "onUnitsChanged =")
        assertTrue(block.contains("setUnitPrefs("), "phải lưu bền")
        assertTrue(block.contains("dock.setCarStatus("), "thanh nút phải cập nhật ngay")
        assertTrue(block.contains("workspace.setUnitPrefs("), "ô giữa màn phải cập nhật ngay")
        assertTrue(prefs.contains("UnitPrefs.decode(") && prefs.contains(".encode()"),
            "lưu bền phải đi qua encode/decode của :core")
    }

    @Test
    fun `bang chon bay CA hai loai kha nang`() {
        // T4 · IA v2 R-UI (m): lưới 123 ô đã RỜI khỏi màn Cài đặt — nhóm "Thanh trạng thái & thanh nút" nay
        // mở CHÍNH bộ chọn của ngăn kéo (`AppDrawer.Mode.PICK_DOCK`). Một bộ chọn, một nguồn ⇒ phép so "hai
        // màn phải giống nhau" không còn đối tượng, và `CapabilityGridSection` đã bị xoá.
        // Tính chất "bày CẢ ĐỌC lẫn HÀNH ĐỘNG" vì thế phải canh ở **ngăn kéo** — bề mặt duy nhất còn dựng lưới đó.
        assertTrue(
            drawerKt.contains("CapabilityCatalog.byDomain()"),
            "bảng chọn phải bày cả ĐỌC lẫn HÀNH ĐỘNG — nếu chỉ bày nút thì người dùng không có đường thêm ô đọc " +
                "vào thanh, và việc nới cổng ở DockConfig thành vô nghĩa",
        )
        assertFalse(drawerKt.contains("ControlPanels.byDomain()"), "không còn dùng danh sách chỉ-có-nút")
    }

    /**
     * Chữ THẬT sẽ hiện trên màn, đọc từ tệp tài nguyên bản Việt.
     *
     * ⚠ U5·T3 — trước đây bài này đọc chuỗi viết cứng trong `.kt`. Chữ nay nằm trong `res/values/strings_kachi.xml`,
     * nên phép kiểm phải đi tới đó: nếu chỉ kiểm *"mã có gọi khoá này không"* thì ai xoá nội dung câu cảnh báo vẫn
     * xanh. `app/build.gradle.kts` đã khai `inputs.dir("src/main/res")` nên đổi tệp đó là task chạy lại.
     */
    private fun res(name: String): String =
        Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(SourceRoots.text("src/main/res/values/strings_kachi.xml"))
            ?.groupValues?.get(1)
            ?: error("không có chuỗi '$name' trong values/strings_kachi.xml — bài test đang quét vùng không tồn tại")

}
