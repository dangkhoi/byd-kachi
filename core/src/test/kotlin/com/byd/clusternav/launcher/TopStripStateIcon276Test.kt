package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.76 (spec `kachi-276-closing.html` R8 · R9) — đóng bảng hoãn UX8 của chip thanh trên ═══════════════════
 *
 * Tách khỏi [TopStripStateIconTest] theo VAI (tệp kia = luật chung + máy soát nguồn; tệp này = các ca của lượt
 * đóng 2.76), vì gộp thì vượt trần 500 dòng (CLAUDE.md §4.1). Bốn việc:
 *  • R8 — cửa ×4 + cửa sổ trời: cặp hình MỞ/ĐÓNG (gen-car.py), chữ "Mở/Đóng" rời chip; VI + EN.
 *  • R9 — `ac_mode_auto`/`ac_wind_auto`: cặp hình TỰ ĐỘNG/TAY (`ic-mode-auto`/`ic-mode`), không chữ đuôi; VI + EN.
 *  • R9 — OQ3: `ac_wind_auto` ẩn khỏi bộ chọn chip + mặc định ([TopStripConfig.CHIP_HIDDEN]), KHÔNG gộp datum.
 *  • R8 — họ 4: `power_level`/`headlight_feedback` mã thô → chữ từ bảng OEM ([TelemetryEnums]), mã lạ ⇒ "mã N".
 */
class TopStripStateIcon276Test {

    private fun one(id: String, status: CarStatus, labels: Boolean = true): ChipView =
        TopStripChips.render(TopStripConfig(listOf(id), showLabels = labels), status).single()

    // ── 4b. 2.76 R8 · CỬA ×4 + CỬA SỔ TRỜI — cặp hình MỞ/ĐÓNG, chữ "Mở"/"Đóng" biến mất ─────────────────────

    private fun doors(open: Boolean?) = CarStatus(
        body = CarStatus.Body(doorLfOpen = open, doorRfOpen = open, doorLrOpen = open, doorRrOpen = open, sunroofOpen = open),
    )

    /** Hình MỞ/ĐÓNG của từng cửa — cùng bộ sinh `gen-car.py`, cùng góc xe; mở = vạt xoè (hình khái niệm cũ). */
    private val DOOR_ICONS = mapOf(
        "door_lf" to ("ic-car-top-door-lf-shut" to "ic-car-top-door-lf"),
        "door_rf" to ("ic-car-top-door-rf-shut" to "ic-car-top-door-rf"),
        "door_lr" to ("ic-car-top-door-lr-shut" to "ic-car-top-door-lr"),
        "door_rr" to ("ic-car-top-door-rr-shut" to "ic-car-top-door-rr"),
        "sunroof_state" to ("ic-car-top-sunroof" to "ic-car-top-sunroof-open"),
    )

    /**
     * [ĐO đọc mã 2.75] chip in `"Cửa trước-trái · Mở"` / `"Cửa sổ trời · Đóng"` (`openShut` trong `format`). Nay:
     * nhãn ngắn + HÌNH của đúng trạng thái; MỞ vẫn là hình khái niệm của datum (vạt xoè) ⇒ R1 giữ nguyên.
     * Chưa đọc ⇒ hình ĐÓNG + trung tính (mở là trạng thái ĐÁNG BÁO — không được vẽ sẵn lúc chưa biết).
     * Cửa sổ trời — [P2 · SOÁT Opus 2026-09-27] sửa một [ĐO] bị thổi: [ĐO 4 lượt quét xe owner] `getSunroofState = 0`
     * ⇒ trên xe ấy datum này **ĐỌC ĐƯỢC** (nhánh *đóng + SÁNG*), không phải nhánh *chưa đọc*. 65535 là số của
     * `getSunroofPosition` (datum đã gỡ 09-25). Hình MỞ là generic, [CHƯA BIẾT] trên xe này.
     */
    @Test
    fun `chip cua va cua so troi doi HINH mo-dong, khong con chu Mo-Dong (VI)`() {
        DOOR_ICONS.forEach { (id, pair) ->
            val (shutIcon, openIcon) = pair
            val open = one(id, doors(true))
            val shut = one(id, doors(false))
            val unread = one(id, doors(null))
            assertEquals(openIcon, open.icon, "$id mở")
            assertEquals(shutIcon, shut.icon, "$id đóng")
            assertEquals(shutIcon, unread.icon, "$id chưa đọc dùng hình LÀNH (đóng), không vẽ sẵn cửa mở")
            assertNotEquals(open.icon, shut.icon, "$id: mở/đóng cùng hình thì chip không nói được gì")
            assertEquals(ChipTone.ACTIVE, open.tone); assertEquals(ChipTone.ACTIVE, shut.tone)
            assertEquals(ChipTone.NEUTRAL, unread.tone, "$id chưa đọc ⇒ trung tính, không mờ")
            val short = TelemetryRegistry.byId(id)!!.shortLabel
            listOf(open, shut, unread).forEach { c ->
                assertEquals(short, c.text, "$id: chip chỉ còn NHÃN, thấy '${c.text}'")
                setOf("Mở", "Đóng", "Open", "Closed", "—").forEach { w ->
                    assertFalse(c.text.contains(w), "$id KHÔNG được còn chữ '$w', thấy: '${c.text}'")
                }
            }
            // Trình đọc màn hình vẫn nghe đủ.
            val label = TelemetryRegistry.byId(id)!!.label
            assertEquals("$label: Mở", open.desc); assertEquals("$label: Đóng", shut.desc)
        }
    }

    @Test
    fun `chip cua noi tieng Anh bang nhan EN, khong in Open-Closed`() {
        try {
            Strings.current = Lang.EN
            val open = one("door_lf", doors(true))
            assertEquals(TelemetryRegistry.byId("door_lf")!!.labelIn(Lang.EN), open.desc.substringBefore(":"))
            assertTrue(open.desc.endsWith(": Open"), "câu đọc EN: '${open.desc}'")
            assertFalse(open.text.contains("Open") || open.text.contains("Closed"), "thấy: '${open.text}'")
            assertEquals("ic-car-top-door-lf", open.icon)
            assertEquals("ic-car-top-sunroof-open", one("sunroof_state", doors(true)).icon)
        } finally {
            Strings.current = Lang.VI
        }
    }

    // ── 4c. 2.76 R9 · TỰ ĐỘNG / CHỈNH TAY — chip icon hai trạng thái, KHÔNG chữ đuôi ─────────────────────────

    private fun acMode(raw: Int?) = CarStatus(climate = CarStatus.Climate(acModeRaw = raw))
    private fun windAuto(raw: Int?) = CarStatus(climate = CarStatus.Climate(acWindAutoRaw = raw))

    /**
     * [ĐO source] `AC_CTRLMODE_AUTO = 0` · `_MANUAL = 1` (`ac/BYDAutoAcDevice.java:20-21`); [ĐO xe 09-16]
     * `getAcControlMode = 0` khi màn AC đang AUTO. Chip: AUTO = núm chữ **A** (`ic-mode-auto`), tay = núm có kim
     * (`ic-mode`, hình khái niệm cũ), sentinel (65535) ⇒ núm có kim + trung tính + câu đọc *"mã 65535"* (không bịa
     * "Chỉnh tay"). Owner từng xin GIỮ chữ `AUTO` (viết thường) — đó là cho chip **Gió** (`ac_wind`, vẫn `auto n`,
     * bài mục 3 ở trên); chip *Chế độ ĐH* thì R9 chốt: hình nói, không chữ đuôi.
     */
    @Test
    fun `chip che do dieu hoa doi HINH tu dong-chinh tay, khong con chu AUTO hay Chinh tay`() {
        val auto = one("ac_mode_auto", acMode(0))
        val manual = one("ac_mode_auto", acMode(1))
        val weird = one("ac_mode_auto", acMode(65535))
        val unread = one("ac_mode_auto", acMode(null))
        assertEquals("ic-mode-auto", auto.icon); assertEquals("ic-mode", manual.icon)
        assertEquals("ic-mode", weird.icon, "mã lạ ⇒ hình trung tính, không đoán"); assertEquals("ic-mode", unread.icon)
        assertEquals(ChipTone.ACTIVE, auto.tone); assertEquals(ChipTone.ACTIVE, manual.tone)
        assertEquals(ChipTone.NEUTRAL, weird.tone); assertEquals(ChipTone.NEUTRAL, unread.tone)
        val short = TelemetryRegistry.byId("ac_mode_auto")!!.shortLabel
        listOf(auto, manual, weird, unread).forEach { c ->
            assertEquals(short, c.text, "chip chỉ còn nhãn, thấy '${c.text}'")
            setOf("AUTO", "auto", "Chỉnh tay", "Manual", "—").forEach { w ->
                assertFalse(c.text.contains(w), "KHÔNG được còn chữ '$w', thấy: '${c.text}'")
            }
        }
        assertEquals("Chế độ điều hòa: AUTO", auto.desc)
        assertEquals("Chế độ điều hòa: Chỉnh tay", manual.desc)
        assertEquals("Chế độ điều hòa: mã 65535", weird.desc, "sentinel không được thành 'Chỉnh tay'")
    }

    /** Chỉ báo gió auto (`AC_WINDLEVEL_MANUAL_SIGN_OFF = 0` · `_ON = 1`, `:107-108`): cùng cặp hình, cùng luật. */
    @Test
    fun `chip gio auto dung cung cap hinh tu dong-chinh tay`() {
        assertEquals("ic-mode-auto", one("ac_wind_auto", windAuto(0)).icon)
        assertEquals("ic-mode", one("ac_wind_auto", windAuto(1)).icon)
        assertEquals("ic-mode", one("ac_wind_auto", windAuto(-2147482648)).icon, "sentinel ⇒ trung tính")
        assertEquals(ChipTone.NEUTRAL, one("ac_wind_auto", windAuto(-2147482648)).tone)
        assertEquals(TelemetryRegistry.byId("ac_wind_auto")!!.shortLabel, one("ac_wind_auto", windAuto(0)).text)
        assertTrue(one("ac_wind_auto", windAuto(-2147482648)).desc.endsWith(": mã -2147482648"))
    }

    @Test
    fun `chip tu dong-chinh tay noi tieng Anh - nhan EN, cau doc Manual, khong chu duoi`() {
        try {
            Strings.current = Lang.EN
            val manual = one("ac_mode_auto", acMode(1))
            assertEquals("A/C mode", manual.text)
            assertEquals("A/C control mode: Manual", manual.desc)
            assertEquals("A/C control mode: code 7", one("ac_mode_auto", acMode(7)).desc)
        } finally {
            Strings.current = Lang.VI
        }
    }

    /**
     * **Quyết định OQ3 (owner hỏi 26/09, chốt 27/09):** `ac_wind_auto` ẨN khỏi bộ chọn CHIP + không vào mặc định,
     * vì chip Gió đã nói `auto n`; KHÔNG gộp với `ac_mode_auto` — hai getter, hai feature id (bằng chứng ở
     * `TelemetryReadout.stateTable`). Cơ chế y hệt `HIDDEN_FROM_PICKER`: decode GIỮ, khối "đang bật" vẫn bày để gỡ.
     */
    @Test
    fun `ac_wind_auto an khoi bo chon chip va mac dinh, nhung ai da dat van giu va go duoc`() {
        assertTrue("ac_wind_auto" in TopStripConfig.CHIP_HIDDEN)
        TopStripConfig.CHIP_HIDDEN.forEach { (id, why) ->
            assertNotNull(TelemetryRegistry.byId(id), "$id không còn trong registry — bỏ khỏi CHIP_HIDDEN")
            assertTrue(why.length >= 40, "$id: lý do ẩn quá mỏng")
            assertTrue(TopStripConfig.isChippable(id), "$id: ẩn khỏi bộ chọn ≠ cấm đặt (khoá lưu bền phải sống)")
            assertFalse(id in TopStripConfig.DEFAULT_IDS, "$id không được vào mặc định")
            assertTrue(TopStripConfig.choices().none { it.id == id }, "$id vẫn bày trong bộ chọn chip")
            val cfg = TopStripConfig.decode("$id,${TopStripConfig.PM25}", applyMigration = false)
            assertTrue(cfg.has(id), "decode phải GIỮ mã đã ẩn")
            assertTrue(TopStripConfig.picks(cfg).first().picks.any { it.id == id }, "khối 'đang bật' phải bày để gỡ")
            assertTrue(TopStripConfig.picks(cfg).drop(1).flatMap { it.picks }.none { it.id == id }, "khối lĩnh vực im lặng")
        }
        // Chip Gió — lý do của quyết định — vẫn nói auto n (bài mục 3), và `ac_mode_auto` KHÔNG bị ẩn.
        assertTrue(TopStripConfig.choices().any { it.id == "ac_mode_auto" })
    }

    // ── 4d. 2.76 R8 · họ 4 — MÃ THÔ → CHỮ (power_level · headlight_feedback), mã lạ ⇒ "mã N" ─────────────────

    /**
     * [P3 UX8, owner nhìn xe 27/09] chip in `"Nguồn xe · 2"` / `"Chế độ đèn pha · 2"`. Nay chữ từ bảng OEM
     * ([TelemetryEnums] — `BODYWORK_POWER_LEVEL_*` `:197-202` · CarSettings `LightControl.java:81,106-117`).
     */
    @Test
    fun `chip nguon xe va den pha in CHU tu bang OEM, ma la in ma N`() {
        fun power(raw: Int?) = one("power_level", CarStatus(body = CarStatus.Body(powerLevel = raw)))
        fun head(raw: Int?) = one("headlight_feedback", CarStatus(lights = CarStatus.Lights(headlightMode = raw)))
        val ps = TelemetryRegistry.byId("power_level")!!.shortLabel
        val hs = TelemetryRegistry.byId("headlight_feedback")!!.shortLabel
        assertEquals("$ps · Bật", power(2).text, "BODYWORK_POWER_LEVEL_ON = 2")
        assertEquals("$ps · Sẵn sàng", power(3).text, "BODYWORK_POWER_LEVEL_OK = 3")
        assertEquals("$ps · mã 9", power(9).text, "mã lạ ⇒ vẫn hiện số, có tiền tố")
        assertEquals("$hs · Auto", head(2).text, "CarSettings: chỉ số 1 = auto ⇒ mã 2")
        assertEquals("$hs · Cốt", head(4).text)
        assertEquals("$hs · mã 0", head(0).text, "0 = chưa có phản hồi, KHÔNG bịa thành Tắt")
        assertEquals("$ps · —", power(null).text, "chưa đọc vẫn là dấu gạch như mọi datum số/chữ")
        listOf(power(2), head(2)).forEach { assertEquals(ChipTone.NEUTRAL, it.tone, "họ 4 không tô sắc thái") }
        try {
            Strings.current = Lang.EN
            assertTrue(power(2).text.endsWith(" · On"), "EN: '${power(2).text}'")
            assertTrue(head(3).text.endsWith(" · Position"), "EN: '${head(3).text}'")
            assertTrue(power(9).text.endsWith(" · code 9"), "EN: '${power(9).text}'")
        } finally {
            Strings.current = Lang.VI
        }
    }

}
