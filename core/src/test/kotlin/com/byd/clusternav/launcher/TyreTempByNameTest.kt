package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · TYRE-TEMP-BY-NAME — nhiệt lốp bind theo TÊN hằng, không theo số của một chiếc xe ═════════════════════
 *
 * `tyre_t_*` từng gắn feature-id SỐ 1246797848/860/872/884 — số của featmap xe owner 09-16 [SUY: dữ liệu cũ]. Feature-id
 * của `BYDAutoFeatureIds` gán LÚC KHỞI TẠO theo cấu hình xe (KDoc `HalRoutes`: cùng một tín hiệu mang hai số tuỳ
 * `isCanFD`/`isToyota`) ⇒ bind theo số là hardcode theo xe (CLAUDE.md §7). Nay theo tên, cùng khuôn màu cụm `tyre_c_*`.
 * Thử ĐỎ: trả một dòng về `"1246797848"` ⇒ bài đầu đỏ; tráo `LB`/`RB` ⇒ bài đầu đỏ.
 */
class TyreTempByNameTest {

    private val corners = mapOf("tyre_t_fl" to "LF", "tyre_t_fr" to "RF", "tyre_t_rl" to "LB", "tyre_t_rr" to "RB")

    @Test
    fun `bon datum nhiet lop bind theo ten hang, dung goc banh`() {
        corners.forEach { (id, c) ->
            val spec = TelemetryRegistry.byId(id)!!
            assertEquals("BYDAutoFeatureIds.INSTRUMENT_2IN1_${c}_TYRE_TEMPERATURE", spec.bindingKey, "$id ↔ $c (LB = sau-trái)")
            assertEquals(
                BindingRoute.FeatureName("INSTRUMENT_2IN1_${c}_TYRE_TEMPERATURE"), HalBindingTable.routeOf(spec.bindingKey), id,
            )
            assertEquals(Domain.TYRES, spec.domain, "device đọc = Instrument theo miền (như màu cụm)")
        }
    }

    @Test
    fun `hai xe gan so khac nhau cho cung ten - doc theo ten cua tung xe`() {
        val names = corners.values.mapIndexed { i, c -> "INSTRUMENT_2IN1_${c}_TYRE_TEMPERATURE" to i }.toMap()
        val carA = HalBindingTable(FakeHalGateway(
            featureNames = names.mapValues { 900 + it.value },
            features = mapOf(900 to "int=31", 901 to "int=32", 902 to "int=33", 903 to "int=34"),
        ))
        val carB = HalBindingTable(FakeHalGateway(
            featureNames = names.mapValues { 1246797848 + 12 * it.value },   // đúng bộ số xe 09-16
            features = mapOf(1246797848 to "int=21", 1246797860 to "int=22", 1246797872 to "int=23", 1246797884 to "int=24"),
        ))
        assertEquals(listOf(31, 32, 33, 34), corners.keys.map { carA.readInt(it) }, "xe A: số khác ⇒ vẫn đúng bánh")
        assertEquals(listOf(21, 22, 23, 24), corners.keys.map { carB.readInt(it) }, "xe B (featmap 09-16)")
        // Xe không có tên hằng ⇒ "—", KHÔNG đoán một con số (V3 · R11).
        assertNull(HalBindingTable(FakeHalGateway(features = mapOf(1246797848 to "int=21"))).readInt("tyre_t_fl"))
    }

    @Test
    fun `mien LOP khong con datum nao bind feature-id so`() {
        val numeric = TelemetryRegistry.ALL.filter { it.domain == Domain.TYRES && HalBindingTable.routeOf(it.bindingKey) is BindingRoute.Feature }
        assertTrue(numeric.isEmpty(), "datum lốp bind theo SỐ (hardcode theo xe): ${numeric.map { it.id to it.bindingKey }}")
    }
}
