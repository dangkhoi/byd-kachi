package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * T4 — widget Android bên thứ ba: **phép thu hồi id** ([AppWidgetIds]) + **dạng lưu** ([SlotCodec]).
 *
 * Hai thứ này ở `:core` chính là để kiểm được off-car. Cái đắt nhất chúng chặn: một id không được thu hồi thì nhà cung
 * cấp **vẫn đẩy cập nhật mãi** cho một ô không còn ai xem — hỏng im lặng, không có gì trên màn hình cho thấy.
 */
class AppWidgetIdsTest {

    private fun aw(id: Int, provider: String = "com.x/.W") = SlotContent.AppWidget(id, provider)

    private fun state(vararg contents: SlotContent) = WorkspaceState.of(LayoutPreset.QUAD, *contents)

    /**
     * Trạng thái đầy đủ — [AppWidgetIds.orphaned]/[AppWidgetIds.unused] nhận [HomeUiState] chứ không phải
     * [WorkspaceState], vì "còn ai dùng" phải tính **cả sổ cảnh** (xem KDoc [AppWidgetIds.used]).
     */
    private fun ui(ws: WorkspaceState, scenes: SceneBook = SceneBook.EMPTY) =
        HomeUiState(workspace = ws, scenes = scenes)

    /** Một sổ cảnh giữ đúng những ô [contents] (dùng để chốt "id trong cảnh vẫn đang dùng"). */
    private fun sceneHolding(vararg contents: SlotContent): SceneBook =
        SceneBook.EMPTY.saved("Cảnh", ui(state(*contents)))

    // ── Thu hồi id ────────────────────────────────────────────────────────────────

    @Test
    fun `bo widget khoi o thi id do thanh rac`() {
        val old = state(aw(11), SlotContent.Widget("w_board"))
        val new = state(SlotContent.Empty, SlotContent.Widget("w_board"))
        assertEquals(setOf(11), AppWidgetIds.orphaned(ui(old), ui(new)))
    }

    @Test
    fun `doi widget sang nha cung cap khac thi id cu thanh rac`() {
        val old = state(aw(11, "com.a/.W"))
        val new = state(aw(12, "com.b/.W"))
        assertEquals(setOf(11), AppWidgetIds.orphaned(ui(old), ui(new)))
    }

    /**
     * ⚠ CA DỄ SAI NHẤT — kéo-thả đổi chỗ hai ô.
     *
     * Cùng một id chuyển từ ô 0 sang ô 1. Tính "orphan" theo **từng ô** (ô 0 trước có id 11, giờ không có ⇒ thu hồi)
     * sẽ **xoá đúng cái widget vừa kéo**: người dùng thả tay ra là ô thành trống. Phải so theo TẬP HỢP.
     */
    @Test
    fun `doi cho hai o thi KHONG thu hoi gi`() {
        val old = state(aw(11), SlotContent.Widget("w_board"))
        val new = state(SlotContent.Widget("w_board"), aw(11))
        assertEquals(emptySet<Int>(), AppWidgetIds.orphaned(ui(old), ui(new)))
    }

    @Test
    fun `khong doi gi thi khong thu hoi gi`() {
        val s = state(aw(11), aw(12))
        assertEquals(emptySet<Int>(), AppWidgetIds.orphaned(ui(s), ui(s)))
    }

    @Test
    fun `nhieu id cung bi bo thi thu hoi ca hai`() {
        val old = state(aw(11), aw(12), aw(13))
        val new = state(aw(12))
        assertEquals(setOf(11, 13), AppWidgetIds.orphaned(ui(old), ui(new)))
    }

    /**
     * Ô **đang ẩn** theo bố cục vẫn giữ nội dung (thiết kế của [WorkspaceState]) ⇒ id ở đó vẫn ĐANG DÙNG.
     *
     * Quét theo `visibleSlots()` sẽ thu hồi id của mọi ô ẩn: đổi bố cục từ 6 ô về 2 ô rồi quay lại là **mất sạch**
     * widget ở 4 ô kia. Bài này khoá đúng chỗ đó — `preset` chỉ cho 2 ô hiện, id ở ô thứ 3 vẫn phải được tính.
     */
    @Test
    fun `id o o dang AN van tinh la dang dung`() {
        val hidden = WorkspaceState.of(LayoutPreset.TWO_COL, SlotContent.Empty, SlotContent.Empty, aw(99))
        assertEquals(setOf(99), AppWidgetIds.idsIn(hidden))
        assertEquals(emptySet<Int>(), AppWidgetIds.orphaned(ui(hidden), ui(hidden)))
    }

    @Test
    fun `o app va thẻ dung tay khong sinh id nao`() {
        val s = state(SlotContent.App("com.x"), SlotContent.Widget(listOf("w_a", "w_b")), SlotContent.Empty)
        assertEquals(emptySet<Int>(), AppWidgetIds.idsIn(s))
    }

    // ── Dọn rác lúc khởi động ─────────────────────────────────────────────────────

    @Test
    fun `id nen tang giu ma bo cuc khong dung la rac`() {
        val s = state(aw(11))
        assertEquals(setOf(12, 13), AppWidgetIds.unused(setOf(11, 12, 13), ui(s)))
    }

    /**
     * ⚠ ĐÓNG CÁI BẪY, không phải chứng minh nó an toàn: bố cục rỗng thì [AppWidgetIds.unused] trả về **mọi** id.
     *
     * Phép tính không có cách nào phân biệt *"chưa nạp bố cục"* với *"người dùng đã bỏ hết widget"*, nên thứ tự là
     * trách nhiệm của chỗ gọi. Bài này để người đọc sau thấy ngay hậu quả nếu gọi sai thứ tự; phía Android chặn bằng
     * `AppWidgetSlotHost.sweep` (bố cục không có widget nào ⇒ không xoá gì) + bài canh dây nối.
     */
    @Test
    fun `BAY - bo cuc rong thi MOI id thanh rac`() {
        val empty = WorkspaceState.of(LayoutPreset.QUAD)
        assertEquals(setOf(11, 12), AppWidgetIds.unused(setOf(11, 12), ui(empty)))
    }

    // ── Id nằm trong CẢNH ĐÃ LƯU vẫn đang được dùng ───────────────────────────────

    /**
     * ⚠⚠ **CA CỦA GIAO ĐIỂM HAI TÍNH NĂNG — đo được trên xe-ảo trước khi vá.**
     *
     * Cảnh lưu **nguyên nội dung ô**, tức lưu chính con số id; mà id thì `deleteAppWidgetId` xong là **không cấp lại
     * được**. [ĐO] `emulator-5554` bản trước bản vá: đặt widget đồng hồ vào ô ⇒ lưu cảnh ⇒ đổi ô đó sang một app ⇒
     * `dumpsys appwidget` cho thấy **id 651 đã bị xoá** ⇒ gọi lại cảnh thì ô hiện *"widget của
     * com.google.android.deskclock không còn — app đã bị gỡ hoặc bị tắt"* trong khi app **vẫn còn nguyên**. Tức
     * launcher tự xoá widget của người dùng rồi báo sai nguyên nhân.
     */
    @Test
    fun `id nam trong canh da luu thi KHONG duoc thu hoi du bo cuc bo no`() {
        val scenes = sceneHolding(aw(11))
        val old = ui(state(aw(11)), scenes)
        val new = ui(state(SlotContent.App("com.waze")), scenes)   // bố cục bỏ widget, cảnh vẫn giữ
        assertEquals(
            emptySet<Int>(), AppWidgetIds.orphaned(old, new),
            "gọi lại cảnh sẽ ra thẻ 'widget không còn' nếu id bị xoá ở đây — mà app thì vẫn còn trên máy",
        )
    }

    /** Và lượt dọn rác lúc khởi động cũng phải thấy cảnh, không thì nó xoá ngay lần mở app kế tiếp. */
    @Test
    fun `don rac luc khoi dong khong xoa id ma canh dang giu`() {
        val loaded = ui(state(SlotContent.Empty), sceneHolding(aw(11)))
        assertEquals(setOf(12), AppWidgetIds.unused(setOf(11, 12), loaded))
        assertEquals(setOf(11), AppWidgetIds.used(loaded), "id của cảnh phải được tính là ĐANG DÙNG")
    }

    /**
     * XOÁ một cảnh là đường **duy nhất** nhả id mà bố cục KHÔNG đổi một ô nào.
     *
     * Bản chỉ-xét-bố-cục sẽ trả tập rỗng ở ca này ⇒ id sống mãi, nhà cung cấp cứ đẩy cập nhật cho một ô không còn ai
     * xem — đúng thứ đắt nhất mà [AppWidgetIds] sinh ra để chặn.
     */
    @Test
    fun `xoa canh giu id thi id do thanh rac`() {
        val ws = state(SlotContent.Widget("w_board"))               // bố cục KHÔNG hề có widget bên thứ ba
        val old = ui(ws, sceneHolding(aw(11)))
        val new = ui(ws, SceneBook.EMPTY)
        assertEquals(setOf(11), AppWidgetIds.orphaned(old, new), "xoá cảnh cuối cùng giữ id ⇒ phải thu hồi")
    }

    /** Hai cảnh cùng giữ một id: xoá một cảnh thì id **vẫn đang dùng**. */
    @Test
    fun `hai canh cung giu mot id thi xoa mot canh khong thu hoi`() {
        val two = SceneBook.EMPTY
            .saved("A", ui(state(aw(11))))
            .saved("B", ui(state(aw(11))))
        val ws = state(SlotContent.Empty)
        assertEquals(setOf(11), AppWidgetIds.idsIn(two))
        assertEquals(emptySet<Int>(), AppWidgetIds.orphaned(ui(ws, two), ui(ws, two.removed("s1"))))
        assertEquals(setOf(11), AppWidgetIds.orphaned(ui(ws, two), ui(ws, SceneBook.EMPTY)))
    }

    @Test
    fun `so canh khong co widget nao thi khong sinh id`() {
        val book = sceneHolding(SlotContent.App("com.x"), SlotContent.Widget("w_board"))
        assertEquals(emptySet<Int>(), AppWidgetIds.idsIn(book))
        assertEquals(emptySet<Int>(), AppWidgetIds.idsIn(SceneBook.EMPTY))
    }

    // ── Dạng lưu ──────────────────────────────────────────────────────────────────

    @Test
    fun `ma hoa roi giai ma lai ra dung o`() {
        val c = SlotContent.AppWidget(42, "com.google.android.deskclock/com.android.alarmclock.DigitalAppWidgetProvider")
        assertEquals(c, SlotCodec.decode(SlotCodec.encode(c)))
    }

    /**
     * Dạng lưu là `aw:<id>@<provider>`.
     *
     * ⚠ Dấu ngăn là `@`, **KHÔNG** phải `|`: chuỗi này được nhúng vào chuỗi lưu của [SceneBook], nơi `|` ngăn TRƯỜNG.
     * Bản đầu dùng `|` và [ĐO] trên `emulator-5554` cho thấy nó làm **mất cảnh trong im lặng** (bản ghi ra 8 trường
     * thay vì 7 ⇒ `decode` bỏ cả cảnh). Luật đó được canh ở `SceneBookTest` cho MỌI loại ô.
     */
    @Test
    fun `dang luu la aw id a-móc provider`() {
        assertEquals("aw:42@com.x/.W", SlotCodec.encode(SlotContent.AppWidget(42, "com.x/.W")))
        assertFalse("|" in SlotCodec.encode(SlotContent.AppWidget(42, "com.x/.W")), "xem KDoc — `|` làm mất cảnh")
    }

    /**
     * Dạng lưu của BA loại cũ **không đổi một byte** ⇒ cấu hình đã nằm trên đĩa của xe đọc lên nguyên vẹn.
     * Đây là chốt chống chính tôi: thêm một loại ô là lúc dễ nhất để vô tình đổi dạng của loại khác.
     */
    @Test
    fun `dang luu cua ba loai cu khong doi mot byte`() {
        assertEquals("", SlotCodec.encode(SlotContent.Empty))
        assertEquals("app:com.byd.x", SlotCodec.encode(SlotContent.App("com.byd.x")))
        assertEquals("widget:w_a,w_b", SlotCodec.encode(SlotContent.Widget(listOf("w_a", "w_b"))))
        assertEquals(SlotContent.App("com.byd.x"), SlotCodec.decode("app:com.byd.x"))
        assertEquals(SlotContent.Widget(listOf("w_a", "w_b")), SlotCodec.decode("widget:w_a,w_b"))
    }

    /** Chuỗi hỏng đến từ đĩa (sửa tay / bản cũ) ⇒ **ô trống**, KHÔNG ném. */
    @Test
    fun `chuoi hong ra o trong khong nem`() {
        listOf(
            "aw:", "aw:@", "aw:abc@com.x/.W", "aw:42", "aw:42@", "aw:@com.x/.W",
            "aw:0@com.x/.W",      // id 0: nền tảng không bao giờ cấp ⇒ chỉ đến từ rác
            "aw:-3@com.x/.W",     // id âm: cùng lý do
            // Dạng `|` là dạng của bản T4 CHƯA VÁ (nó làm mất cảnh). Đọc ra ô trống là đúng: giữ nó lại nghĩa là giữ
            // một dạng chuỗi thứ hai mà chuỗi cảnh không chở nổi — và chỉ tồn tại trên máy đã chạy bản chưa vá.
            "aw:42|com.x/.W",
        ).forEach {
            assertEquals(SlotContent.Empty, SlotCodec.decode(it), "chuỗi '$it' phải ra ô trống")
        }
    }

    /** Provider có `/` và `.` nên dấu phân cách không được là hai ký tự đó — chuỗi thật của một widget đồng hồ. */
    @Test
    fun `provider chua gach cheo va dau cham van giai ma dung`() {
        val p = "com.google.android.deskclock/com.android.alarmclock.AnalogAppWidgetProvider"
        val c = SlotCodec.decode("aw:7@$p")
        assertEquals(SlotContent.AppWidget(7, p), c)
        assertTrue((c as SlotContent.AppWidget).provider.contains("/"))
    }
}
