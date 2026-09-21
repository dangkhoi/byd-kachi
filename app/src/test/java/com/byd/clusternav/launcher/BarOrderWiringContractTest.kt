package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ UX-OVERHAUL · WP4 + WP5 — DÂY NỐI phải ĐI HẾT, và cỡ phải lấy từ THANG ══════════════════════════════════
 *
 * Spec `docs/specs/kachi-ux-overhaul.html` §WP4 · §WP5.
 *
 * ## Vì sao bài này tồn tại bên cạnh [BarLayoutTest]
 * [BarLayoutTest] chứng minh **luật đúng** (`:core`, thuần). Nó **không** chứng minh người dùng chạm được tới luật
 * ấy — đúng khoảng trống mà RW0 đã trả giá: *"vẽ được ≠ đặt được"* (`WidgetViews` vẽ được ô hành động suốt một gói
 * việc mà ngăn kéo không bày ra, nên 48 bài test xanh mà tính năng **không giao được**). Nên bài này đi theo cả
 * chuỗi: **màn Cài đặt → intent → ViewModel → prefs → state → tầng vẽ**, mỗi mắt một khẳng định.
 */
class BarOrderWiringContractTest {

    private fun src(name: String) =
        SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name.kt")

    private val bars = src("SettingsSectionsBars")
    private val orderRows = src("SettingsBarOrderRows")
    private val vm = src("HomeViewModel")
    private val prefs = src("WorkspacePrefs")
    private val repo = src("PrefsWorkspaceRepository")
    private val strip = src("KachiTopStrip")
    private val activity = src("KachiHomeActivity")
    private val dock = src("ControlDockView")

    // ── WP4 · chuỗi dây nối của THANH TRÊN ───────────────────────────────────────────────────────────────

    @Test
    fun `thu tu thanh tren di het chuoi tu Cai dat toi man hinh`() {
        assertTrue(bars.contains("SettingsBarOrderRows<HeaderItem>"), "màn Cài đặt phải bày danh sách sắp chỗ")
        assertTrue(bars.contains("deps.onHeaderLayout("), "…và báo ra qua intent, không tự ghi bền")
        assertTrue(vm.contains("fun setHeaderLayout("), "ViewModel phải có intent")
        val setter = SourceRoots.body(vm, "fun setHeaderLayout(")
        assertTrue(setter.contains("copy(header = layout)"), "state đổi…")
        assertTrue(setter.contains("repository.setHeaderLayout("), "…và lưu bền, trong MỘT lượt")
        assertTrue(prefs.contains("HeaderLayout.decode("), "phải nạp lại được sau khi tắt app")
        assertTrue(
            SourceRoots.body(repo, "override fun load()").contains("header = prefs.headerLayout()"),
            "nạp CÙNG lượt với mọi thứ khác ⇒ đổi hồ sơ là thanh sắp lại theo hồ sơ đó",
        )
        assertTrue(strip.contains("fun setLayout("), "tầng vẽ phải có đường đặt lại chỗ")
        assertTrue(
            activity.contains("prev?.header != state.header") && activity.contains("topStrip.setLayout("),
            "state đổi ⇒ màn hình phải đổi NGAY — thiếu điều kiện diff thì bấm ◀/▶ là 'màn hình không đổi gì' " +
                "(đúng lỗi nút bố cục sẵn ở P9)",
        )
    }

    /**
     * Thứ tự thanh NÚT **không** được có khoá lưu thứ hai — nó LÀ thứ tự của `dock_enabled`.
     *
     * Đây là bẫy hai-bản-sao ở dạng dễ mắc nhất: thêm một khoá `dock_order` nghe rất hợp lý, và nó sẽ lệch với
     * `dock_enabled` ở đúng lần người dùng bỏ tích một nút ở bộ chọn.
     */
    @Test
    fun `thu tu thanh nut khong co khoa luu rieng`() {
        assertTrue(bars.contains("dock.moveEnabled("), "dời chỗ đi qua DockConfig.moveEnabled")
        assertTrue(bars.contains("deps.onDockConfig("), "…rồi đi đúng intent mà bộ chọn nút đang dùng")
        listOf(prefs, vm, repo).forEach {
            assertFalse(it.contains("dock_order"), "không được sinh khoá lưu thứ hai cho thứ tự thanh nút")
        }
        assertFalse(vm.contains("fun setDockOrder("), "không được mở cổng thứ hai tới cùng một cấu hình")
    }

    /**
     * Danh sách sắp chỗ phải **đọc lại** nguồn sự thật mỗi lượt, KHÔNG giữ ảnh chụp.
     *
     * Trang Cài đặt được **nhớ** ([SettingsPanel.pages]) nên một ảnh chụp lúc dựng có thể đã cũ vài phút — và với
     * hồ sơ thì nó còn thuộc về người khác. Cùng bài học `openPicker` (*"đọc lại ngay lúc mở"*).
     */
    @Test
    fun `danh sach sap cho doc lai nguon su that, khong giu anh chup`() {
        assertTrue(bars.contains("current = { deps.state().header.order }"), "thanh trên đọc từ state")
        assertTrue(bars.contains("current = { deps.state().dock.enabled }"), "thanh nút đọc từ state")
        assertFalse(orderRows.contains("WorkspacePrefs"), "bộ sắp chỗ không được chạm prefs")
        assertFalse(orderRows.contains("setHeaderLayout("), "…cũng không được tự ghi bền")
        // Bộ chọn nút đổi DANH SÁCH vật ⇒ danh sách sắp chỗ phải dựng lại (trang được nhớ nên không tự dựng lại).
        assertTrue(
            SourceRoots.body(bars, "private fun openPicker()").contains("dockOrder.refresh()"),
            "Áp dụng ở bộ chọn nút phải làm danh sách sắp chỗ theo — nếu không nó còn hàng của nút vừa bỏ",
        )
    }

    /**
     * Nút hết dời được phải **nói ra** (mờ + bỏ nhận chạm), không bỏ qua im lặng.
     *
     * Họ lỗi này đã trả giá hai lần: trần 8 chip chặn im lặng (G1) và nút bố cục sẵn không đổi gì (P9).
     */
    @Test
    fun `nut het doi duoc thi mo va bo nhan cham`() {
        val btn = SourceRoots.body(orderRows, "private fun moveButton(")
        assertTrue(btn.contains("isEnabled = enabled"), "bỏ nhận chạm")
        assertTrue(btn.contains("alpha = if (enabled)"), "…và nói ra bằng mắt")
        assertTrue(btn.contains("if (enabled) setOnClickListener"), "không gắn lệnh cho nút không dùng được")
        // Đích chạm của nút dời: đây là bề mặt Cài đặt (không phải thanh trên) nên nó KHÔNG được hạ 70 %.
        assertTrue(
            btn.contains("Sp.TOUCH"),
            "nút ◀/▶ ở màn Cài đặt phải đủ Sp.TOUCH — mốc 70 % của WP5 chỉ áp cho THANH TRÊN",
        )
    }

    // ── WP5 · cỡ phải lấy từ thang, không hardcode ───────────────────────────────────────────────────────

    /**
     * Mọi cỡ của hai thanh đọc từ [KachiBars] — và **bề cao thanh trên khai tường minh**.
     *
     * Khai tường minh là điều kiện để *"cao 75 %"* đo được trên ảnh. Để `WRAP_CONTENT` thì con số phụ thuộc việc
     * vật nào tình cờ cao nhất, tức lời hứa đúng nhờ may mắn (xem KDoc [KachiBars.HEADER_H]).
     */
    @Test
    fun `co cua hai thanh lay tu thang KachiBars`() {
        assertTrue(
            activity.contains("dp(KachiBars.HEADER_H)"),
            "bề cao thanh trên phải khai tường minh bằng hằng của thang",
        )
        assertTrue(strip.contains("dp(Bars.HEADER_BTN)"), "nút thanh trên lấy cỡ từ thang")
        assertTrue(strip.contains("dp(Bars.HEADER_AVATAR)"), "đĩa chip hồ sơ lấy cỡ từ thang")
        assertTrue(dock.contains("Bars.DOCK_PAD"), "lề trong thanh nút lấy từ thang")
        assertTrue(dock.contains("Bars.DOCK_TILE_W") && dock.contains("Bars.DOCK_TILE_H"), "cỡ ô lấy từ thang")
    }

    /**
     * ⚠⚠ **Ô phải NẰM TRONG thanh** — phép cộng, kiểm bằng số học chứ không bằng mắt.
     *
     * WP5 hạ thanh 80 % nhưng ô chỉ 85 %, nên hai mốc kéo về hai phía. [ĐO] trước khi hạ lề trong, ô ngang cần
     * 97dp trong thanh 93dp và ô dọc cần 107dp trong thanh 99dp — **cả hai đều tràn**, và `LinearLayout` cắt IM
     * LẶNG. Bài này là thứ duy nhất bắt được nếu ai đổi một trong bốn hằng mà quên ba hằng kia.
     */
    @Test
    fun `o thanh nut luon nam trong be day thanh`() {
        val chrome = 2 * KachiSpace.XS + 2 * KachiBars.DOCK_PAD   // lề ngoài ô + lề trong thanh
        assertTrue(
            KachiBars.DOCK_TILE_H + chrome <= KachiBars.DOCK_THICK,
            "ô ngang cao ${KachiBars.DOCK_TILE_H} + khung $chrome phải ≤ bề dày ${KachiBars.DOCK_THICK}",
        )
        assertTrue(
            KachiBars.DOCK_TILE_W_VERTICAL + chrome <= KachiBars.DOCK_WIDE,
            "ô dọc rộng ${KachiBars.DOCK_TILE_W_VERTICAL} + khung $chrome phải ≤ bề rộng ${KachiBars.DOCK_WIDE}",
        )
    }

    /**
     * Nội dung ô STEP phải nằm trong ô **DỌC** — chiều chật nhất của cả launcher sau WP5.
     *
     * Ô STEP không vẽ nhãn (xem `ControlTileFactory.tileStep`), nên nội dung = lề trong + (icon + khe) + hàng nút.
     * [ĐO số học] `8 + 24 + 27 = 59 ≤ 60`; giữ lề trong cũ (8dp) hoặc nút −/+ cũ (32dp) thì tràn.
     */
    @Test
    fun `noi dung o STEP nam trong o doc`() {
        val need = 2 * KachiBars.DOCK_PAD + (KachiSpace.ICON_S + KachiSpace.XS) + KachiSpace.TOUCH_TIGHT
        assertTrue(
            need <= KachiBars.DOCK_TILE_H_VERTICAL,
            "ô STEP cần ${need}dp mà ô dọc chỉ cao ${KachiBars.DOCK_TILE_H_VERTICAL}dp ⇒ bị cắt im lặng",
        )
        assertTrue(
            need <= KachiBars.DOCK_TILE_H,
            "…và cả ô ngang (${KachiBars.DOCK_TILE_H}dp)",
        )
    }

    /**
     * Hai mốc phần trăm của owner, khoá bằng SỐ HỌC thay vì bằng lời.
     *
     * Ghim cả mốc gốc: đổi một hằng mà quên mốc thì bài này nói ra con số mới, thay vì để tài liệu và mã lệch nhau
     * (dự án đã có ba con số tài liệu bị phồng — xem Reviewer Log của G1).
     */
    @Test
    fun `hai moc phan tram cua WP5 dung nhu owner chot`() {
        // R5.2 — thanh trên: cao 75 % của 56dp; nút 70 % của 48dp.
        assertEquals(42, KachiBars.HEADER_H, "56 × 0.75 = 42")
        assertEquals(34, KachiBars.HEADER_BTN, "48 × 0.7 = 33.6 → 34")
        assertEquals(22, KachiBars.HEADER_AVATAR, "32 × 0.7 = 22.4 → 22")
        // R5.1 — thanh nút: thanh 80 % (116/124); ô 85 % (84/86/70). Ô DỌC lệch có chủ ý: xem KDoc
        // [KachiBars.DOCK_TILE_W_VERTICAL] (giữ đúng mốc THANH vì đó là thứ owner nhìn thấy).
        assertEquals(93, KachiBars.DOCK_THICK, "116 × 0.8 = 92.8 → 93")
        assertEquals(99, KachiBars.DOCK_WIDE, "124 × 0.8 = 99.2 → 99")
        assertEquals(71, KachiBars.DOCK_TILE_W, "84 × 0.85 = 71.4 → 71")
        assertEquals(73, KachiBars.DOCK_TILE_H, "86 × 0.85 = 73.1 → 73")
        assertEquals(60, KachiBars.DOCK_TILE_H_VERTICAL, "70 × 0.85 = 59.5 → 60")
        assertEquals(83, KachiBars.DOCK_TILE_W_VERTICAL, "suy từ thanh: 99 − 8 − 8 (83 % của 100, lệch mốc 2 điểm)")
        assertEquals(27, KachiSpace.TOUCH_TIGHT, "32 × 0.85 = 27.2 → 27 (bề cao VẼ của nút −/+)")
    }
}
