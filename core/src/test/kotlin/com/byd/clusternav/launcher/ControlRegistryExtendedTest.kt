package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ControlRegistryExtendedTest {

    @Test fun `co du 5 ControlKind`() {
        val kinds = ControlRegistry.ALL.map { it.kind }.toSet()
        assertEquals(ControlKind.values().toSet(), kinds, "thieu ControlKind: ${ControlKind.values().toSet() - kinds}")
    }

    @Test fun `19 nut goc con nguyen (id + thu tu + co default)`() {
        // ⚠ 1.85 · **20 → 19**: `hood` (vị trí 12) đã xoá — [ĐO xe 2026-09-20 §4] owner xác nhận xe KHÔNG có
        // ca-pô điện, chỉ cốp sau điện; từ 1.66 nút đã bị ẩn khỏi bộ chọn vì đúng lý do ấy, lượt này bỏ hẳn.
        // Đây là mã ĐẦU TIÊN rời khối gốc. Ý của bài canh KHÔNG đổi: **thứ tự các nút còn lại phải nguyên vẹn**
        // (chúng quyết định `defaultEnabledIds()` và thứ tự thanh nút mà người dùng đã quen), nên nếu ai đó
        // chèn/đảo một nút trong khối này thì bài vẫn đỏ. Xoá thêm một mã ⇒ phải sửa danh sách Ở ĐÂY và nói rõ
        // lý do, chứ không lặng lẽ hạ con số.
        val original = listOf(
            "lock", "window", "trunk", "readl", "pm25", "seatc", "temp", "fan",
            "defrost", "cam", "door", "sunroof", "headl", "seath", "recirc", "drl", "vol", "wiper", "cast",
        )
        assertEquals(original, ControlRegistry.ALL.take(19).map { it.id })
    }

    @Test fun `defaultEnabledIds bat bien - 8 nut mac dinh dung thu tu`() {
        assertEquals(
            listOf("lock", "window", "trunk", "readl", "pm25", "seatc", "temp", "fan"),
            ControlRegistry.defaultEnabledIds(),
        )
        // Nut moi KHONG duoc tu bat (giu dock mac dinh gon).
        assertTrue("wiper" !in ControlRegistry.defaultEnabledIds())
        assertTrue("powertrain_mode" !in ControlRegistry.defaultEnabledIds())   // (V) 2026-09-17: cũ là `drive_mode`
    }

    @Test fun `moi control bindingKey khong rong`() {
        val empty = ControlRegistry.ALL.filter { it.bindingKey.isBlank() }
        assertTrue(empty.isEmpty(), "bindingKey rong: ${empty.map { it.id }}")
    }

    @Test fun `moi control id duy nhat`() {
        val ids = ControlRegistry.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `SELECT co danh sach args`() {
        val selects = ControlRegistry.ALL.filter { it.kind == ControlKind.SELECT }
        assertTrue(selects.isNotEmpty())
        selects.forEach { assertTrue(it.args.isNotEmpty(), "${it.id} SELECT thieu args") }
    }

    @Test fun `COVER va BUTTON co mat`() {
        assertTrue(ControlRegistry.ALL.any { it.kind == ControlKind.COVER })
        assertTrue(ControlRegistry.ALL.any { it.kind == ControlKind.BUTTON })
        // Kinh tung cua = COVER, proven.
        val winLf = ControlRegistry.byId("win_lf")!!
        assertEquals(ControlKind.COVER, winLf.kind)
        assertEquals(EvidenceTier.PROVEN, winLf.tier)
        // Loc-ngay = BUTTON, proven.
        assertEquals(ControlKind.BUTTON, ControlRegistry.byId("pm25_clean_now")!!.kind)
    }

    @Test fun `control gom nhieu domain (panel)`() {
        val domains = ControlRegistry.ALL.map { it.domain }.toSet()
        assertTrue(domains.size >= 6, "control chi phu $domains")
        // drive + energy + infotainment deu co nut. (`Domain.SAFETY` da go 2026-09-16 cung toan bo ADAS/an toan.)
        listOf(Domain.DRIVETRAIN, Domain.ENERGY, Domain.INFOTAINMENT, Domain.BODY, Domain.LIGHTS, Domain.CLIMATE)
            .forEach { d -> assertTrue(ControlRegistry.byDomain(d).isNotEmpty(), "domain $d khong co control") }
    }

    @Test fun `nut proven cluster nav dung tier`() {
        listOf("window", "trunk", "pm25", "seatc", "seath", "pm25_clean_now").forEach { id ->
            assertEquals(EvidenceTier.PROVEN, ControlRegistry.byId(id)?.tier, "$id phai PROVEN")
        }
    }
}
