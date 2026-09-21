package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ KIỂM TOÁN UX mục 4 — ICON PHẢI MANG ĐÚNG MỘT NGHĨA ══════════════════════════════════════════════════════
 *
 * `IconStyleContractTest` (:app) canh **phong cách** (một độ dày nét, đầu nét tròn, khung 24, không màu riêng). Nó
 * KHÔNG canh **nghĩa** — và [ĐO] kiểm toán 2026-09-12 cho thấy nghĩa là chỗ hỏng thật:
 *  • glyph ⊞ (`ic-grid`) mang **BA** nghĩa: cửa kính · widget *"Bảng tổng hợp"* · mọi mục lùi về icon của nhóm;
 *  • hai nút **ĐỐI NGHỊCH** *"Mở hết kính"* / *"Đóng hết kính"* dùng **CÙNG** một icon;
 *  • một nhóm cảnh báo: **8/10 mục cùng một icon sóng radar** ⇒ icon không giúp phân biệt gì (nhóm đó đã gỡ hẳn
 *    2026-09-16 cùng toàn bộ ADAS/an toàn — owner);
 *  • 5 icon **sai nghĩa**: *"Trạng thái xe"* = ổ khoá · *"Trình chiếu ảnh"* = mặt trời (y hệt *"Đồng hồ + thời
 *    tiết"*) · *"Mức xăng"* = pin · *"Công suất mô-tơ"* = đồng hồ tốc · lốp có **hai** hình không liên quan nhau.
 *
 * Bài này ở `:core` vì cả ba nguồn quyết định nghĩa ([WidgetRegistry], [CapabilityIcons], [ActionMacros]) đều ở
 * `:core` — đúng luật *"bài quét gì thì nằm cùng module với thứ đó"*.
 */
class CapabilityIconMeaningTest {

    // ── 1 · Glyph ⊞ chỉ còn MỘT nghĩa ───────────────────────────────────────────────────────────────

    @Test
    fun `glyph luoi chi con nghia bang tong hop`() {
        val widgets = WidgetRegistry.ALL.filter { it.icon == GRID }.map { it.id }
        assertEquals(listOf("w_board"), widgets, "⊞ là hình một cái BẢNG — chỉ widget bảng tổng hợp được dùng")

        val domains = Domain.values().filter { WidgetCatalog.iconFor(it) == GRID }
        assertTrue(domains.isEmpty()) {
            "lĩnh vực $domains lùi về ⊞ ⇒ mọi mục chưa khai icon của lĩnh vực đó mang hình 'bảng' (ESP đã từng vậy)"
        }

        val data = TelemetryRegistry.ALL.filter { CapabilityIcons.forTelemetry(it.id, it.domain) == GRID }.map { it.id }
        assertTrue(data.isEmpty()) { "mục đọc mang hình 'bảng': $data" }

        val controls = ControlRegistry.ALL.filter { it.icon == GRID }.map { it.id }
        assertTrue(controls.isEmpty()) { "nút mang hình 'bảng': $controls" }
    }

    // ── 2 · Hai gói lệnh đối nghịch không được cùng icon ────────────────────────────────────────────

    /**
     * ⚠ Chặn **nguyên nhân**, không chỉ chặn đúng cặp kính: đòi **mọi** gói lệnh có icon riêng.
     *
     * Bốn gói hiện có đều là những việc khác nhau, nên "mỗi gói một icon" là luật đúng chứ không phải luật bó buộc.
     * Nếu mai có hai gói thật sự cùng nghĩa thì bài đỏ và người sửa phải nói ra lý do — đúng cách bản vá P0 ngày
     * 2026-09-11 đã học (bài chỉ chặn hiện tượng thì lần thứ hai lọt).
     */
    @Test
    fun `moi goi lenh mang icon rieng`() {
        val byIcon = ActionMacros.ALL.groupBy { it.icon }.filterValues { it.size > 1 }
        assertTrue(byIcon.isEmpty()) {
            "gói lệnh dùng chung icon ⇒ hai việc khác nhau trông y hệt: " +
                byIcon.map { (i, ms) -> "$i ← ${ms.map { it.label }}" }
        }
        val open = ActionMacros.byId("mac_win_open_all")
        val close = ActionMacros.byId("mac_win_close_all")
        assertTrue(open != null && close != null)
        assertNotEquals(
            open!!.icon, close!!.icon,
            "\"${open.label}\" và \"${close.label}\" là hai việc ĐỐI NGHỊCH — không thể cùng một hình",
        )
    }

    // ── 3 · Nhóm mà icon không phân biệt được thì KHÔNG vẽ dải ──────────────────────────────────────

    /**
     * ⚠⚠ **MODEL phải NÓI RA khi icon không phân biệt được** — đó là điều kiện để bộ vẽ bỏ icon đi.
     *
     * Bản đầu của bài này viết luật *"nhóm có ≥3 mục cùng icon thì không được vẽ STRIP"*, và nó **đỏ ngay lần chạy
     * đầu** với bốn nhóm khác (`g_windows`, `g_doors`, `g_lights`, `g_occupants`). Đó là bằng chứng luật đó SAI:
     * ba nhóm đầu **có nút**, mà bất biến của dự án là *"nút chỉ ở STRIP"* ([CapabilityGroupsTest]) ⇒ chúng
     * **không thể** đổi sang BOARD. Luật đúng không phải "đổi kiểu vẽ" mà là **"đừng vẽ cái icon vô nghĩa đó"**.
     *
     * Nên bài canh chuyển sang khoá đúng thứ dùng được: [GroupBoardModel.iconsDistinguish]. Danh sách dưới là
     * [ĐO] hiện trạng — ai cho các mục đó icon riêng thì bài đỏ và phải xoá tên khỏi danh sách (nó **tự rữa**,
     * không phải chỗ cất nợ).
     */
    @Test
    fun `model noi ra nhom nao co icon khong phan biet duoc`() {
        val blind = CapabilityGroups.ALL
            .filterNot { GroupBoard.of(it, CarStatus()).iconsDistinguish }
            .map { it.id }
        assertEquals(
            // U6 gỡ `g_climate` khỏi danh sách: sau khi tách bụi/cảm biến/nước làm mát/điều hoà, các ô con của
            // nhóm Khí hậu đã phân biệt được bằng icon. Danh sách TỰ RỮA — đây đúng là chiều nó phải rữa.
            // U7 gỡ BẢY nhóm khỏi danh sách (`g_tyres` · `g_windows` · `g_doors` · `g_lights` · `g_ambient` ·
            // `g_adas` · `g_occupants`): cả bảy "mù icon" vì cùng một lý do — ô con của chúng khác nhau ở VỊ
            // TRÍ, mà icon theo khái niệm không nói được vị trí. Bộ hình xe theo vị trí chữa đúng chỗ đó, nên
            // đây là chiều danh sách PHẢI rữa. Còn lại hai nhóm mà ô con khác nhau ở ĐẠI LƯỢNG chứ không ở chỗ.
            // WP8: `g_battery` rời danh sách — 5 ô pin dùng chung hình (3 nhiệt cell + 2 áp cell) đã purge,
            // nên nhóm pin còn lại phân biệt được hình.
            listOf("g_trip"),
            blind,
            "danh sách nhóm mà icon ô con KHÔNG phân biệt được đã đổi — cập nhật danh sách và xem lại bộ vẽ",
        )
        // ⚠ **10/12 nhóm** — con số này nói rằng "icon cho từng ô con" là ý tưởng chỉ đúng ở vài nhóm. Nó KHÔNG có
        // hại ở phần lớn chỗ vì bộ vẽ đã không hiện icon ở đó: `BOARD` vẽ theo vị trí, `CARD` xếp ngang (nhãn · số),
        // `STRIP` có nút thì bỏ icon để nhường bề cao. Chỗ nó THẬT SỰ hại là `STRIP` KHÔNG nút — đúng hai nhóm
        // `g_adas` (nay là BOARD) và `g_occupants` (nay bộ vẽ bỏ icon). Bài canh phía `:app`
        // (`GroupTileWiringContractTest.o con chi hien icon khi icon phan biet duoc`) khoá đúng chỗ đó.
        // Và các nhóm còn lại phải phân biệt được thật (nếu không thì phép đo trên vô nghĩa).
        assertTrue(
            CapabilityGroups.ALL.any { GroupBoard.of(it, CarStatus()).iconsDistinguish },
            "không nhóm nào phân biệt được ⇒ phép đo đang sai, không phải bộ icon sai",
        )
    }

    /**
     * Nhóm có nút thì bộ vẽ vốn đã bỏ icon (cần bề cao cho hàng nút), nên ba nhóm kính/cửa/đèn KHÔNG hiện icon
     * trùng — bài này ghim đúng điều đó để đừng ai "sửa" bằng cách bật icon trở lại.
     *
     * ## ⚠ U9 pha 2 — *Cửa & khoang* rời STRIP, và điều đó KHÔNG nới lỏng kết luận
     * Nhóm cửa nay là [WidgetShape.BOARD] (vẽ hình xe). Bộ vẽ BOARD **cũng** không hiện icon ô con — nó vẽ theo vị
     * trí — nên câu trả lời cho *"ô con của ba nhóm này có hiện icon trùng không"* vẫn là **không**, chỉ khác đường
     * đi tới. Vì thế bài này chuyển từ *"phải là STRIP"* sang *"phải là một hình mà bộ vẽ KHÔNG hiện icon ô con"*:
     * đó mới là điều nó thật sự quan tâm, và bản cũ chỉ đúng nhờ trùng hợp rằng lúc đó ba nhóm đều là STRIP.
     */
    @Test
    fun `nhom co nut thi khong hien icon o con`() {
        listOf(CapabilityGroups.WINDOWS, CapabilityGroups.DOORS, CapabilityGroups.LIGHTS).forEach {
            assertTrue(it.hasWrites, "${it.id} phải có nút — đó là lý do bộ vẽ bỏ icon ô con")
            assertTrue(
                it.shape in CapabilityGroups.SHAPES_WITH_ACTIONS,
                "${it.id} có nút mà bộ vẽ của nó không có hàng nút ⇒ nút vẽ ra rồi không ai chạm được",
            )
            assertTrue(
                it.shape == WidgetShape.STRIP || it.shape == WidgetShape.BOARD,
                "${it.id}: chỉ hai bộ vẽ này KHÔNG hiện icon cho từng ô con (STRIP-có-nút bỏ icon, BOARD vẽ theo " +
                    "vị trí) — hình khác là icon trùng hiện trở lại",
            )
        }
        assertEquals(
            WidgetShape.BOARD, CapabilityGroups.DOORS.shape,
            "U9 pha 2: nhóm cửa vẽ hình xe; trả nó về dải là quay lại mười ô chữ giống hệt nhau",
        )
    }

    // ⚠ Bài `nhom adas xep theo phia` đã gỡ 2026-09-16: nhóm `g_adas` và phép `GroupBoard.sideOf`/`GroupSide`
    // không còn tồn tại sau khi owner gỡ toàn bộ ADAS/an toàn khỏi launcher.

    // ── 4 · Năm icon sai nghĩa ─────────────────────────────────────────────────────────────────────

    @Test
    fun `nam icon sai nghia da duoc sua`() {
        val car = WidgetRegistry.byId("w_car")!!
        assertNotEquals("ic-lock", car.icon, "\"${car.label}\" là sơ đồ TOÀN XE, không phải nút khoá cửa")

        val photos = WidgetRegistry.byId("w_photos")!!
        val clock = WidgetRegistry.byId("w_clock")!!
        assertNotEquals(clock.icon, photos.icon, "trình chiếu ảnh và đồng hồ+thời tiết không thể cùng một hình")

        assertEquals("ic-fuel", icon("fuel_pct"), "xăng ≠ pin (trên xe hybrid là hai bình chứa khác nhau)")
        assertEquals("ic-motor", icon("motor_power"), "công suất ≠ tốc độ")
        // ⚠ Mốc `esp_state → ic-esp` đã gỡ 2026-09-16 cùng datum ESP (owner gỡ toàn bộ ADAS/an toàn).
        // Thay bằng một mốc cùng loại còn sống: nguồn MCU không được lùi về tia sét chung của lĩnh vực Năng lượng.
        // ⚠ (V) 2026-09-17: ca `mcu_status → ic-sensor` đã gỡ cùng datum (owner chấm NO).
    }

    /**
     * MỘT khái niệm = MỘT hình: icon của một bánh (`tyre_p_*`) và icon nhóm Lốp phải **cùng họ**.
     *
     * Không kiểm được "cùng họ" bằng mã, nên kiểm điều kiểm được: chúng là hai tệp KHÁC nhau (một bánh vs cả xe 4
     * bánh — đúng, hai việc khác nhau) nhưng **không** được là hai hình rời rạc do ngẫu nhiên. Phần hình học thì
     * khoá bằng ảnh chụp, và lý do ghi trong chính tệp `ic_tire.xml`.
     */
    @Test
    fun `lop cua mot banh va lop cua ca nhom la hai muc rieng`() {
        // U7: cả hai nay cùng dựng trên KHUNG XE nhìn từ trên — đúng nghĩa "cùng họ" mà KDoc trên đòi, và
        // khác nhau ở đúng chỗ phải khác: một bánh TÔ ĐẶC (nói "bánh nào") vs CẢ BỐN bánh tô (nói "cả cụm").
        assertEquals("ic-car-top-tyre-fl", icon("tyre_p_fl"), "áp suất một bánh: khung xe + ĐÚNG bánh đó tô đặc")
        assertEquals("ic-group-tyres", CapabilityGroups.TYRES.icon, "nhóm dùng icon cả xe + 4 bánh")
        assertNotEquals(icon("tyre_p_fl"), CapabilityGroups.TYRES.icon)
    }

    /** Nhiệt lốp vẫn phải KHÁC áp lốp (thành quả U1, dễ bị vô tình gộp lại khi sửa bảng tiền tố). */
    @Test
    fun `nhiet lop khac ap lop`() {
        assertNotEquals(icon("tyre_p_fl"), icon("tyre_t_fl"))
    }

    private fun icon(id: String): String {
        val spec = TelemetryRegistry.byId(id)
        assertTrue(spec != null) { "$id không còn trong bộ đăng ký" }
        return CapabilityIcons.forTelemetry(spec!!.id, spec.domain)
    }

    private companion object {
        const val GRID = "ic-grid"
    }
}
