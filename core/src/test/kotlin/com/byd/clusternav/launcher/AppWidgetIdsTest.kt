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
     * [WorkspaceState], vì "còn ai dùng" phải tính **cả id của hồ sơ tài xế khác** (xem KDoc [AppWidgetIds.used]).
     */
    private fun ui(ws: WorkspaceState, others: Set<Int> = emptySet()) =
        HomeUiState(workspace = ws, widgetIdsOtherProfiles = others)

    /**
     * Chuỗi cảnh ĐỜI CŨ giữ đúng những ô [contents] — dạng `<mã>|<tên>|<preset>|<lưới>|<ô;…>|<viền>|<nút,…>`,
     * literal để vẫn chạy sau khi T2 xoá `SceneBook` (cùng lý do đã ghi ở `ScenesMigrationTest`).
     */
    private fun legacyScenesRaw(vararg contents: SlotContent): String =
        "s1|Cảnh|QUAD||" + contents.joinToString(";") { SlotCodec.encode(it) } + "|BOTTOM|lock"

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

    // ── Id nằm trong dữ liệu CẢNH ĐỜI CŨ chưa chuyển vẫn đang được dùng (S4 · R2) ─

    /**
     * ⚠⚠ **CA CỦA GIAO ĐIỂM HAI TÍNH NĂNG — đo được trên xe-ảo trước khi vá.**
     *
     * Cảnh lưu **nguyên nội dung ô**, tức lưu chính con số id; mà id thì `deleteAppWidgetId` xong là **không cấp lại
     * được**. [ĐO] `emulator-5554` bản trước bản vá: đặt widget đồng hồ vào ô ⇒ lưu cảnh ⇒ đổi ô đó sang một app ⇒
     * `dumpsys appwidget` cho thấy **id 651 đã bị xoá** ⇒ gọi lại cảnh thì ô hiện *"widget của
     * com.google.android.deskclock không còn — app đã bị gỡ hoặc bị tắt"* trong khi app **vẫn còn nguyên**.
     *
     * S4 · R1 bỏ khái niệm cảnh, nhưng **chuỗi cảnh vẫn nằm trên đĩa của xe đang chạy** cho tới khi
     * [ScenesMigration] chuyển xong. Trong cửa sổ đó `WorkspacePrefs.widgetIdsOtherProfiles` phải đọc nó qua
     * [AppWidgetIds.idsInStored] — không thì lượt khởi động ĐẦU TIÊN của bản mới xoá sạch widget trong cảnh, tức
     * chính lượt nâng cấp làm mất dữ liệu.
     */
    @Test
    fun `id trong chuoi canh doi cu chua chuyen van tinh la dang dung`() {
        val raw = legacyScenesRaw(aw(11))
        assertEquals(setOf(11), AppWidgetIds.idsInLegacyScenes(raw))
        assertEquals(setOf(11), AppWidgetIds.idsInStored(List(WorkspaceState.SLOT_CAP) { "" }, raw))

        // …và khi đã đọc ra thì nó đi vào state qua vế "hồ sơ khác" ⇒ lượt dọn rác lúc khởi động KHÔNG chạm nó.
        val loaded = ui(state(SlotContent.Empty), others = setOf(11))
        assertEquals(setOf(12), AppWidgetIds.unused(setOf(11, 12), loaded))
        assertEquals(setOf(11), AppWidgetIds.used(loaded), "id chưa chuyển phải được tính là ĐANG DÙNG")
    }

    /** Cảnh không có widget bên thứ ba ⇒ không sinh id nào; chuỗi rỗng/rác cũng vậy, và KHÔNG ném. */
    @Test
    fun `chuoi canh khong co widget nao thi khong sinh id`() {
        val raw = legacyScenesRaw(SlotContent.App("com.x"), SlotContent.Widget("w_board"))
        assertEquals(emptySet<Int>(), AppWidgetIds.idsInLegacyScenes(raw))
        assertEquals(emptySet<Int>(), AppWidgetIds.idsInLegacyScenes(""))
    }

    // ── HỒ SƠ TÀI XẾ KHÁC: id của họ KHÔNG được thu hồi (SOÁT P0-1) ────────────────

    /**
     * ⚠⚠⚠ **LỖI P0 — ĐỔI HỒ SƠ TÀI XẾ XOÁ VĨNH VIỄN WIDGET CỦA HỒ SƠ KIA.**
     *
     * [ĐO] `emulator-5554` bản trước bản vá: đặt *Digital clock* vào ô ở hồ sơ **Mặc định** ⇒ nhận id 654; đổi sang
     * hồ sơ **Vợ** ⇒ 654 **biến khỏi** `dumpsys appwidget`; quay lại **Mặc định** ⇒ ô hiện *"widget của
     * com.google.android.deskclock không còn — app đã bị gỡ hoặc bị tắt"* trong khi `pm list packages` cho thấy app
     * **vẫn còn cài** (`pm list packages -d` đếm 0 = không bị tắt). `force-stop` rồi mở lại vẫn vậy ⇒ **vĩnh viễn**,
     * và còn **báo sai nguyên nhân**.
     *
     * Gốc: `WorkspacePrefs.key()` = `"<hồ sơ>__<hậu tố>"`, nên `workspace` trong [HomeUiState] **chỉ** của hồ
     * sơ đang dùng — trong khi id widget là của **HOST** (mọi hồ sơ). Lỗi ở **KIỂU DỮ LIỆU**, không ở nhánh code: bài
     * canh cũ chứng minh *"có gọi đúng hàm với cả hai state"* mà không hỏi *"hàm có thấy đủ dữ liệu chưa"*.
     *
     * Bài này hỏi đúng câu đó: hai hồ sơ, **chỉ đổi hồ sơ** (id 654 chuyển từ "của tôi" sang "của hồ sơ khác", id 700
     * thì ngược lại) ⇒ [AppWidgetIds.orphaned] phải trả **RỖNG**.
     */
    @Test
    fun `chi doi ho so tai xe thi KHONG thu hoi id nao`() {
        // Hồ sơ A đang dùng: ô giữ 654. Hồ sơ B (chưa dùng) giữ 700.
        val onA = ui(state(aw(654)), others = setOf(700))
        // Sau khi đổi sang B: ô giữ 700, còn 654 nay là "của hồ sơ khác".
        val onB = ui(state(aw(700)), others = setOf(654))
        assertEquals(
            emptySet<Int>(), AppWidgetIds.orphaned(onA, onB),
            "đổi hồ sơ KHÔNG được thu hồi id nào — [ĐO] bản cũ xoá 654 vĩnh viễn rồi báo 'app đã bị gỡ'",
        )
        assertEquals(
            emptySet<Int>(), AppWidgetIds.orphaned(onB, onA),
            "và đổi ngược lại cũng vậy (đường về phải đối xứng, không thì lượt thứ hai mới chết)",
        )
    }

    /**
     * ⚠⚠ Đường thứ hai, **nặng hơn vì không cần ai chạm gì**: lượt dọn rác lúc khởi động.
     *
     * `allocated` = `host.appWidgetIds` = **mọi** id của host (tức mọi hồ sơ), còn "còn dùng" thì chỉ của hồ sơ đang
     * dùng. Nghĩa là chỉ cần nổ máy với hồ sơ A là widget của hồ sơ B chết — miễn A có ít nhất một widget để qua chốt
     * `if (used.isEmpty()) return`. Bài này khoá đúng ca đó: 700 là của hồ sơ khác, 999 là rác thật.
     */
    @Test
    fun `don rac luc khoi dong khong xoa id cua ho so khac`() {
        val loaded = ui(state(aw(654)), others = setOf(700))
        assertEquals(
            setOf(999), AppWidgetIds.unused(setOf(654, 700, 999), loaded),
            "chỉ 999 là rác thật; 700 là của hồ sơ khác và 654 là của hồ sơ đang dùng",
        )
        assertEquals(setOf(654, 700), AppWidgetIds.used(loaded), "'còn dùng' phải gồm cả id của hồ sơ khác")
    }

    /** Và ca ngược: hồ sơ khác **hết** giữ id đó (hồ sơ bị xoá) ⇒ id thành rác thật ⇒ phải nhả. */
    @Test
    fun `xoa ho so thi id cua no thanh rac`() {
        val before = ui(state(aw(654)), others = setOf(700))
        val after = ui(state(aw(654)), others = emptySet())      // hồ sơ giữ 700 vừa bị xoá
        assertEquals(
            setOf(700), AppWidgetIds.orphaned(before, after),
            "xoá hồ sơ = xoá mọi khoá của nó ⇒ id nó giữ không còn ai dùng ⇒ phải thu hồi, không để rò",
        )
    }

    // ── Đọc id từ DỮ LIỆU ĐÃ LƯU của một hồ sơ (nguồn của vế thứ ba) ──────────────

    /**
     * [AppWidgetIds.idsInStored] là chỗ **giải mã** dữ liệu hồ sơ khác. Phần này thuần để kiểm được off-car — tệp
     * `WorkspacePrefs` cần `Context` nên nếu để phép giải mã ở đó thì không có bài nào chạm tới được.
     */
    @Test
    fun `doc id tu chuoi da luu cua mot ho so - ca o va ca canh`() {
        val slots = listOf("", "aw:654@com.x/.W", "app:com.waze", "widget:w_board", "aw:655@com.y/.W", "")
        assertEquals(setOf(654, 655), AppWidgetIds.idsInStored(slots, null))

        // Chuỗi cảnh đời cũ của hồ sơ đó cũng giữ id — cùng lý do như hồ sơ đang dùng (xem khối trên).
        val scenes = legacyScenesRaw(aw(700))
        assertEquals(setOf(654, 655, 700), AppWidgetIds.idsInStored(slots, scenes))
        assertEquals(setOf(700), AppWidgetIds.idsInStored(List(6) { "" }, scenes))
    }

    /** Hồ sơ chưa có gì / chuỗi rác ⇒ tập rỗng, KHÔNG ném (dữ liệu đến từ đĩa, sửa tay được). */
    @Test
    fun `ho so trong hoac chuoi rac ra tap rong khong nem`() {
        assertEquals(emptySet<Int>(), AppWidgetIds.idsInStored(emptyList(), null))
        assertEquals(emptySet<Int>(), AppWidgetIds.idsInStored(List(6) { "" }, ""))
        assertEquals(emptySet<Int>(), AppWidgetIds.idsInStored(listOf("aw:", "rác", "aw:0@com.x/.W"), "rác|rác"))
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
     * ⚠ Dấu ngăn là `@`, **KHÔNG** phải `|`: chuỗi này từng được nhúng vào chuỗi lưu của cảnh, nơi `|` ngăn TRƯỜNG.
     * Bản đầu dùng `|` và [ĐO] trên `emulator-5554` cho thấy nó làm **mất cảnh trong im lặng** (bản ghi ra 8 trường
     * thay vì 7 ⇒ `decode` bỏ cả cảnh). Luật đó vẫn canh được ở `ScenesMigrationTest` cho dữ liệu chưa chuyển.
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
