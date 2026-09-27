package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.76 (spec `kachi-276-closing.html` R8 · R9) — phần đọc-ra của lượt đóng UX8 ═══════════════════════════
 * Tách khỏi [TelemetryReadoutTest] theo VAI (tệp kia = hợp đồng chung của bộ đọc; tệp này = họ 4 bảng mã OEM +
 * chỉ báo tự động/tay + cửa), vì gộp thì vượt trần 500 dòng (CLAUDE.md §4.1). Mồi `wiredStatus` dùng chung
 * ở `TelemetryReadoutFixtures.kt`.
 */
class TelemetryReadout276Test {

    // ── 2.76 R8 · họ 4 — bảng mã OEM → chữ ────────────────────────────────────────────────────────

    /**
     * Mọi hằng trong bảng ánh xạ ra CHỮ (không tiền tố *"mã"*), mã ngoài bảng lùi về "mã N" / "code N", cả VI lẫn
     * EN — và hai datum [P3 UX8] đọc qua đúng bảng ấy (không còn `toString()` mã thô).
     * [ĐO source] `BODYWORK_POWER_LEVEL_*` `bodywork/BYDAutoBodyworkDevice.java:197-202` ·
     * CarSettings OEM `outsidelight/view/LightControl.java:81,106-117` (chỉ số radio = mã − 1).
     */
    @Test fun `bang enum OEM - moi hang anh xa ra chu, ma la lui ve ma N, VI va EN`() {
        listOf(TelemetryEnums.POWER_LEVEL, TelemetryEnums.HEADLIGHT_MODE).forEach { t ->
            assertTrue(t.entries.size >= 3, "${t.id}: ≥ 3 mục mới là họ 4")
            t.entries.keys.forEach { code ->
                listOf(Lang.VI, Lang.EN).forEach { lang ->
                    val txt = t.text(code, lang)
                    assertFalse(txt.startsWith("mã ") || txt.startsWith("code "), "${t.id}[$code] $lang: '$txt' chưa có nghĩa")
                    assertFalse(txt.isBlank())
                }
            }
            val odd = (t.entries.keys.maxOrNull()!! + 1)
            assertEquals("mã $odd", t.text(odd, Lang.VI)); assertEquals("code $odd", t.text(odd, Lang.EN))
            assertEquals(t.entries.size, TelemetryEnums.size(t.id))
        }
        // Đúng các hằng OEM, đúng nghĩa.
        assertEquals("Tắt", TelemetryEnums.POWER_LEVEL.text(0, Lang.VI)); assertEquals("ACC", TelemetryEnums.POWER_LEVEL.text(1, Lang.VI))
        assertEquals("On", TelemetryEnums.POWER_LEVEL.text(2, Lang.EN)); assertEquals("Ready", TelemetryEnums.POWER_LEVEL.text(3, Lang.EN))
        assertEquals("Invalid", TelemetryEnums.POWER_LEVEL.text(255, Lang.EN), "INVALID = 255 là mục của bảng, không phải mã lạ")
        assertEquals("Tắt", TelemetryEnums.HEADLIGHT_MODE.text(1, Lang.VI)); assertEquals("Auto", TelemetryEnums.HEADLIGHT_MODE.text(2, Lang.VI))
        assertEquals("Đèn hông", TelemetryEnums.HEADLIGHT_MODE.text(3, Lang.VI)); assertEquals("Low beam", TelemetryEnums.HEADLIGHT_MODE.text(4, Lang.EN))
        assertEquals("mã 0", TelemetryEnums.HEADLIGHT_MODE.text(0, Lang.VI), "0 = chưa phản hồi, KHÔNG bịa thành Tắt")
        assertEquals(0, TelemetryEnums.size("soc")); assertNull(TelemetryEnums.text("soc", 1))

        // Đường đọc THẬT: datum đi qua bảng (không còn mã thô).
        assertEquals("Sẵn sàng", TelemetryReadout.of("power_level", CarStatus(body = CarStatus.Body(powerLevel = 3)))!!.display)
        assertEquals("mã 9", TelemetryReadout.of("power_level", CarStatus(body = CarStatus.Body(powerLevel = 9)))!!.display)
        assertEquals("Cốt", TelemetryReadout.of("headlight_feedback", CarStatus(lights = CarStatus.Lights(headlightMode = 4)))!!.display)
        assertEquals("—", TelemetryReadout.of("headlight_feedback", CarStatus())!!.display)
        // Mồi "2" qua adapter giả: cả hai datum ra chữ, không ra "2".
        val status = wiredStatus("2")
        assertEquals("Bật", TelemetryReadout.of("power_level", status)!!.display)
        assertEquals("Auto", TelemetryReadout.of("headlight_feedback", status)!!.display)
    }

    /** Sentinel của hai chỉ báo tự động/tay ⇒ chữ *"mã N"* VÀ mã trạng thái `null` — chữ và hình không nói hai điều. */
    @Test fun `chi bao tu dong-tay - sentinel ra ma N va trang thai null, khong bia Chinh tay`() {
        val weird = TelemetryReadout.of("ac_mode_auto", CarStatus(climate = CarStatus.Climate(acModeRaw = 65535)))!!
        assertEquals("mã 65535", weird.display); assertNull(weird.state)
        val manual = TelemetryReadout.of("ac_mode_auto", CarStatus(climate = CarStatus.Climate(acModeRaw = 1)))!!
        assertEquals("Chỉnh tay", manual.display); assertEquals(1, manual.state)
        assertEquals(0, TelemetryReadout.of("ac_mode_auto", CarStatus(climate = CarStatus.Climate(acModeRaw = 0)))!!.state)
        val w2 = TelemetryReadout.of("ac_wind_auto", CarStatus(climate = CarStatus.Climate(acWindAutoRaw = -2147482648)))!!
        assertEquals("mã -2147482648", w2.display); assertNull(w2.state)
        assertEquals(0, TelemetryReadout.of("ac_wind_auto", CarStatus(climate = CarStatus.Climate(acWindAutoRaw = 0)))!!.state)
        // Cửa: mã trạng thái 1 = MỞ (BODYWORK_STATE_OPEN = 1, :204), chữ vẫn "Mở" cho ô lớn.
        val open = TelemetryReadout.of("door_rr", CarStatus(body = CarStatus.Body(doorRrOpen = true)))!!
        assertEquals(1, open.state); assertEquals("Mở", open.display)
        assertNull(TelemetryReadout.of("sunroof_state", CarStatus())!!.state)
    }

}
