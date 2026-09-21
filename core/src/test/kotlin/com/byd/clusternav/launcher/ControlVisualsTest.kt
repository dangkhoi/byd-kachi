package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ WP2 · R2 — KHOÁ LUẬT TRẠNG THÁI HIỂN THỊ của ô điều khiển ═══════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-ux-overhaul.html` §WP2. Phủ đủ **năm** [ControlKind] cộng hai ca mà chính lượt WP2 sinh
 * ra để chặn:
 *  • thang mức (`seatc`/`seath`) phải ra **vạch** và **không** ra chữ *"Tắt"*;
 *  • tập lựa chọn (`headlight_mode`, `powertrain_mode`, `camera_view`…) phải **giữ chữ** và **không** ra vạch.
 *
 * Bài này là chỗ duy nhất kiểm được luật ấy off-car: trước WP2 nó nằm trong năm hàm dựng `android.view`, nơi dự án
 * (không dùng Robolectric) chỉ quét được bằng contract-test đọc mã nguồn.
 */
class ControlVisualsTest {

    private fun def(id: String): ControlDef =
        ControlRegistry.byId(id) ?: error("registry không còn nút '$id' — cập nhật bài canh cùng lượt xoá nút")

    // ── TOGGLE (R2.1) ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `TOGGLE bat thi active, tat thi khong — va KHONG bao gio sinh chu Bat hay Tat`() {
        val d = def("defrost")   // ví dụ owner nêu: "sấy kính: icon active/un-active, không phải 'Sấy kính: Bật'"
        val on = ControlVisuals.of(d, 1)
        val off = ControlVisuals.of(d, 0)
        assertTrue(on.active, "bật ⇒ ô mang màu nhấn")
        assertFalse(off.active, "tắt ⇒ ô về mặc định")
        listOf(on, off).forEach {
            assertEquals("", it.option, "ô bật/tắt KHÔNG được sinh chữ trạng thái (owner: 'nhà quê')")
            assertEquals(0, it.ticks, "bật/tắt không phải thang mức ⇒ không vạch")
            assertEquals("", it.valueText, "bật/tắt không có ô giá trị")
        }
    }

    @Test
    fun `chua doc duoc thi coi la TAT, khong doan mot trang thai bat`() {
        // Một ô sáng màu nhấn là lời khẳng định "xe đang làm việc này" — off-car/trim không provision thì phải im.
        assertFalse(ControlVisuals.of(def("defrost"), null).active)
        assertFalse(ControlVisuals.of(def("trunk"), null).active)
        assertEquals(0, ControlVisuals.of(def("seatc"), null).lit)
    }

    // ── SELECT thang mức (R2.2) ──────────────────────────────────────────────────────────────────

    @Test
    fun `ghe mat ra HAI vach, muc 1 sang 1 vach, muc 2 sang 2 vach, Tat mo ca hai`() {
        val d = def("seatc")
        assertTrue(ControlVisuals.isLevelScale(d), "ghế mát khai thang mức ở ControlLevels ⇒ phải vẽ vạch")
        assertEquals(2, ControlVisuals.tickCount(d), "3 lựa chọn (Tắt/Mức 1/Mức 2) ⇒ 2 vạch")
        val off = ControlVisuals.of(d, 0)
        assertEquals(2 to 0, off.ticks to off.lit)
        assertFalse(off.active, "mức Tắt ⇒ ô về mặc định")
        assertEquals("", off.option, "R2.2 — KHÔNG còn chữ 'Tắt' dưới nhãn, trạng thái nói bằng vạch")
        val one = ControlVisuals.of(d, 1)
        assertEquals(2 to 1, one.ticks to one.lit)
        assertTrue(one.active)
        val two = ControlVisuals.of(d, 2)
        assertEquals(2 to 2, two.ticks to two.lit)
        assertTrue(two.active)
    }

    /**
     * `seath` khai **bốn** mã khung ([ControlLevels.RAW_BY_LEVEL], `[SUY]` chờ đo trên xe) nhưng chỉ bày **ba**
     * lựa chọn. Số vạch phải theo thứ người dùng bấm vòng qua (`args`), không theo thang khung — vẽ 3 vạch cho một
     * ô chỉ lên tới vạch 2 là hứa một mức không bấm tới được.
     */
    @Test
    fun `so vach theo LUA CHON cua nut, khong theo so ma khung cua thang`() {
        val d = def("seath")
        assertEquals(4, ControlLevels.levelCount("seath"), "tiền đề của bài: thang khung có 4 mã")
        assertEquals(3, d.args.size, "tiền đề của bài: nút bày 3 lựa chọn")
        assertEquals(2, ControlVisuals.tickCount(d), "⇒ 2 vạch, không phải 3")
    }

    @Test
    fun `chi so ngoai thang bi kep, khong sinh so vach sang am hay vuot tran`() {
        val d = def("seatc")
        assertEquals(0, ControlVisuals.of(d, -1).lit)
        assertEquals(2, ControlVisuals.of(d, 99).lit)
    }

    // ── SELECT tập lựa chọn — ca WP2 CỐ Ý không đổi ──────────────────────────────────────────────

    /**
     * ⚠ Bài quan trọng nhất của lượt này: *"bỏ chữ"* KHÔNG được áp cho SELECT không xếp hạng. Vẽ *"Xanh lá"* thành
     * *"3 trên 5 vạch"* là nói sai — ở đó chữ là thông tin DUY NHẤT của ô.
     */
    @Test
    fun `tap lua chon GIU chu va KHONG ra vach`() {
        // ⚠ WP8 2026-09-20: `ambient_color` + `regen_level` đã purge ⇒ rời danh sách.
        // ⚠⚠ 1.90 2026-09-21: BỐN mã còn lại (`headlight_mode` · `powertrain_mode` · `screen_rotation` ·
        // `camera_view`) cũng bị owner xoá ⇒ **registry KHÔNG còn một nút SELECT không-thang-mức nào** ([ĐO] hai
        // nút SELECT còn sống là `seatc`/`seath`, và cả hai ở trong `ControlLevels.RAW_BY_LEVEL` nên là THANG MỨC;
        // COVER thì không đi qua nhánh SELECT của `ControlVisuals.of`).
        //
        // Vì thế bài này chuyển sang một `ControlDef` **dựng tại chỗ**, và đó là lựa chọn có chủ ý: bất biến cần
        // canh là LUẬT VẼ của `ControlVisuals` (*"tập lựa chọn thì giữ chữ, không ra vạch"*), không phải nội dung
        // registry hôm nay. Xoá bài đi thì ngày ai đó thêm lại một nút SELECT không-thang-mức, lỗi *"vẽ 'Xanh lá'
        // thành 3/5 vạch"* mọc lại mà không bài nào đỏ — đúng họ lỗi mà KDoc phía trên mô tả.
        val synthetic = ControlDef(
            id = "test_select_khong_thang_muc", label = "Ô thử", icon = "ic-mode", kind = ControlKind.SELECT,
            args = listOf("Một", "Hai", "Ba"), argsEn = listOf("One", "Two", "Three"),
        )
        assertTrue(ControlLevels.levelCount(synthetic.id) == 0, "tiền đề: mã này KHÔNG nằm trong thang mức")
        assertFalse(ControlVisuals.isLevelScale(synthetic), "tập lựa chọn, không phải thang mức")
        val v = ControlVisuals.of(synthetic, 1)
        assertEquals(0, v.ticks, "không được vẽ vạch")
        assertEquals(synthetic.argsIn(Lang.VI)[1], v.option, "phải giữ chữ lựa chọn đang chọn")
    }

    @Test
    fun `dung MOT nguon de biet nut nao la thang muc — bang ControlLevels`() {
        // Thêm một nút vào `ControlLevels.RAW_BY_LEVEL` (việc BẮT BUỘC để ghi đúng) là tự có vạch; không có cờ thứ
        // hai phải nhớ bật. Bài này khoá đúng giả định đó.
        val scales = ControlRegistry.ALL.filter { ControlVisuals.isLevelScale(it) }.map { it.id }.toSet()
        assertEquals(
            ControlLevels.RAW_BY_LEVEL.keys.filter { id -> ControlRegistry.byId(id)?.kind == ControlKind.SELECT }.toSet(),
            scales,
            "tập nút vẽ vạch phải bằng đúng tập SELECT khai trong ControlLevels",
        )
    }

    // ── COVER (R2.3) ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `cop MO thi active, DONG thi mac dinh`() {
        val d = def("trunk")
        assertTrue(ControlVisuals.of(d, 1).active, "cốp mở ⇒ màu active")
        assertFalse(ControlVisuals.of(d, 0).active, "cốp đóng ⇒ mặc định")
    }

    @Test
    fun `kinh he mot phan van la DANG MO`() {
        // Bốn kính đọc PHẦN TRĂM mở, không đọc cờ 0/1: một cửa hé 10 % vẫn là cửa chưa đóng.
        val d = def("win_lf")
        assertTrue(ControlVisuals.of(d, 10).active)
        assertTrue(ControlVisuals.of(d, 100).active)
        assertFalse(ControlVisuals.of(d, 0).active)
    }

    // ── STEP (R2.4) ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `stepper co diem tat thi sang khi khac 0, thang khong co diem tat thi khong bao gio sang`() {
        val fan = def("fan")
        assertTrue(ControlVisuals.hasOffPoint(fan), "gió 0..7 có mức 0 = im")
        assertFalse(ControlVisuals.of(fan, 0).active, "gió 0 ⇒ ô về mặc định")
        assertTrue(ControlVisuals.of(fan, 3).active, "gió 3 ⇒ ô đang có tác dụng")

        val temp = def("temp")
        assertFalse(ControlVisuals.hasOffPoint(temp), "nhiệt 17..33 KHÔNG có mức tắt")
        assertFalse(
            ControlVisuals.of(temp, 33).active,
            "nếu không, ô nhiệt độ sáng màu nhấn suốt chuyến chỉ vì 'luôn khác min'",
        )
    }

    @Test
    fun `chu o gia tri lay don vi tu DATUM ma nut doc, khong tu mot nhanh theo ma`() {
        assertEquals("°", ControlVisuals.stepUnit(def("temp")), "temp đọc inside_temp (°C) ⇒ rút về ° cho vừa ô")
        assertEquals("22°", ControlVisuals.stepText(def("temp"), 22))
        assertEquals("", ControlVisuals.stepUnit(def("fan")), "ac_wind không có đơn vị")
        assertEquals("4", ControlVisuals.stepText(def("fan"), 4))
        // ⚠ 1.90 · hai mốc `vol` (media_vol, không đơn vị) và `brightness_gear` gỡ cùng nút (owner 2026-09-21).
        // `fan` ngay trên vẫn phủ đúng ca *"nút STEP đọc một datum KHÔNG có đơn vị"* nên phép canh không mất vế.
        assertEquals("", ControlVisuals.stepUnit(def("defrost")), "nút không phải STEP thì không có ô giá trị")
    }

    @Test
    fun `chu o gia tri luon bi kep ve min max cua chinh nut`() {
        assertEquals("17°", ControlVisuals.stepText(def("temp"), 5))
        assertEquals("33°", ControlVisuals.stepText(def("temp"), 99))
        assertEquals("7", ControlVisuals.stepText(def("fan"), 20))
    }

    /**
     * SÀN bề rộng ô giá trị = con số dài nhất của **mọi** nút STEP, suy từ registry. Đây là điều làm hai ô *"22°"*
     * và *"4"* cân đối giống nhau (R2.4) — `:app` đặt `minWidth` theo nó.
     */
    @Test
    fun `so ky tu o gia tri suy tu REGISTRY va phu duoc moi nut STEP`() {
        val steps = ControlRegistry.ALL.filter { it.kind == ControlKind.STEP }
        // ⚠ 1.90 · sàn hạ **3 → 2**: hai nút STEP `vol` + `brightness_gear` bị owner xoá 2026-09-21 ⇒ còn `temp`
        // (nhiệt độ) và `fan` (gió). Vẫn đủ hai vế mà bài cần: một nút CÓ đơn vị (`temp` → "33°") và một nút KHÔNG
        // (`fan` → "7"), nên phép đo bề rộng lớn nhất vẫn có cái để so.
        assertTrue(steps.size >= 2, "tiền đề: registry có nút STEP (nhiệt · gió)")
        val widest = steps.maxOf { d ->
            maxOf(ControlVisuals.stepText(d, d.min).length, ControlVisuals.stepText(d, d.max).length)
        }
        assertEquals(widest, ControlVisuals.STEP_VALUE_CHARS, "phải là bề rộng lớn nhất, không phải một số gõ tay")
        assertEquals(3, ControlVisuals.STEP_VALUE_CHARS, "[ĐO] hôm nay = 3 (\"33°\" của nhiệt độ)")
        steps.forEach { d ->
            listOf(d.min, d.max).forEach { v ->
                assertTrue(
                    ControlVisuals.stepText(d, v).length <= ControlVisuals.STEP_VALUE_CHARS,
                    "ô giá trị phải chứa được '${ControlVisuals.stepText(d, v)}' của nút ${d.id}",
                )
            }
        }
    }

    // ── BUTTON ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nut bam mot phat sang khi dang bam roi ve mac dinh`() {
        val d = def("door")
        assertTrue(ControlVisuals.of(d, 1).active)
        assertFalse(ControlVisuals.of(d, 0).active)
        assertEquals("", ControlVisuals.of(d, 1).option)
    }

    // ── Bất biến chung cho cả năm kiểu ───────────────────────────────────────────────────────────

    /**
     * ⚠ Bài này khoá **lời hứa của WP2**: mọi ô điều khiển đi qua CÙNG một cửa và không ô nào sinh ra chữ trạng
     * thái bật/tắt. Trước WP2 câu ấy không đo được vì luật nằm trong năm hàm dựng view khác nhau.
     */
    @Test
    fun `khong nut nao trong registry sinh chu Bat hoac Tat lam trang thai`() {
        val banned = setOf("Bật", "Tắt", "On", "Off")
        ControlRegistry.ALL.forEach { d ->
            (listOf<Int?>(null) + (0..3)).forEach { v ->
                val opt = ControlVisuals.of(d, v).option
                // Tập lựa chọn ĐƯỢC có chữ, nhưng chữ đó là một LỰA CHỌN của nút (vd "Auto", "Tắt" của chế độ đèn
                // pha) chứ không phải trạng thái bật/tắt do tầng vẽ bịa ra ⇒ chỉ chặn chữ KHÔNG nằm trong args.
                if (opt.isNotEmpty() && opt in banned) {
                    assertTrue(
                        opt in d.argsIn(Lang.VI) || opt in d.argsIn(Lang.EN),
                        "nút ${d.id} sinh chữ trạng thái '$opt' không đến từ args của chính nó",
                    )
                }
            }
        }
    }

    @Test
    fun `vach chi xuat hien o SELECT thang muc, khong o kieu nao khac`() {
        ControlRegistry.ALL.forEach { d ->
            val v = ControlVisuals.of(d, 1)
            if (d.kind != ControlKind.SELECT || !ControlVisuals.isLevelScale(d)) {
                assertEquals(0, v.ticks, "nút ${d.id} (${d.kind}) không được vẽ vạch")
            }
        }
    }
}
