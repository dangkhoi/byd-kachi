package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ControlRegistryExtendedTest {

    @Test fun `co du 5 ControlKind`() {
        val kinds = ControlRegistry.ALL.map { it.kind }.toSet()
        assertEquals(ControlKind.values().toSet(), kinds, "thieu ControlKind: ${ControlKind.values().toSet() - kinds}")
    }

    @Test fun `20 nut goc con nguyen (id + thu tu + co default)`() {
        val original = listOf(
            "lock", "window", "trunk", "readl", "pm25", "seatc", "temp", "fan",
            "defrost", "cam", "door", "hood", "sunroof", "headl", "seath", "recirc", "drl", "vol", "wiper", "cast",
        )
        // 20 nut goc phai la 20 phan tu DAU tien, dung thu tu.
        assertEquals(original, ControlRegistry.ALL.take(20).map { it.id })
    }

    @Test fun `defaultEnabledIds bat bien - 8 nut mac dinh dung thu tu`() {
        assertEquals(
            listOf("lock", "window", "trunk", "readl", "pm25", "seatc", "temp", "fan"),
            ControlRegistry.defaultEnabledIds(),
        )
        // Nut moi KHONG duoc tu bat (giu dock mac dinh gon).
        assertTrue("wiper" !in ControlRegistry.defaultEnabledIds())
        assertTrue("drive_mode" !in ControlRegistry.defaultEnabledIds())
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
