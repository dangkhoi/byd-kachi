package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · HAL-PENDING-PARSE-DRY — một phép parse Int cho mọi lượt đọc · "getter đã trả lời" phải có nghĩa ═══════
 *
 * [ĐO mã soát 2.88 Pass 3] lối *"mã không hợp lệ ⇒ chưa có số"* của `CarDataAdapter` (`Gate.pending`) tự gọi
 * [HalBindingTable.coerceInt] — bỏ qua [HalReadTables.ARRAY_INDEX] mà [HalBindingTable.readInt] tôn trọng (lặp phép parse,
 * lệch nhau) — và coi MỌI chuỗi khác `null` là *"getter đã trả lời"* ⇒ một lời đáp rác không bao giờ cho cache vắng nguội.
 * Hôm nay chưa chạm tới (getter lốp trả số, không phần tử mảng) — bài này khoá trước lượt khai datum kế.
 */
class HalPendingParseTest {

    @Test
    fun `parseInt la phep parse DUY NHAT - ton trong chi so mang, khong duong lui`() {
        assertEquals(7, HalBindingTable.parseInt("pm25_outside", "[3, 7]"), "ARRAY_INDEX ⇒ phần tử thứ 1")
        assertNull(HalBindingTable.parseInt("pm25_outside", "35"), "không phải mảng + chỉ số > 0 ⇒ null (không lùi về số thuần)")
        assertEquals(3, HalBindingTable.parseInt("soc", "[3, 7]"), "datum thường ⇒ phần tử đầu, như coerceInt")
        assertEquals(42, HalBindingTable.parseInt("soc", "int=42 float=- buf=-"))
        assertNull(HalBindingTable.parseInt("soc", null))
    }

    @Test
    fun `readInt va loi cho-co-so cung di qua parseInt`() {
        val table = SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/HalBindingTable.kt")
        assertTrue(SourceRoots.body(table, "fun readInt(id: String): Int?").contains("parseInt(id, readRaw(id))"))
        val adapter = SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/CarDataAdapter.kt")
        val int = SourceRoots.body(adapter, "fun int(id: String, prev: Int?): Int?")
        assertTrue(int.contains("HalBindingTable.parseInt(id, it)"), "lối chờ-có-số không được tự parse:\n$int")
        assertTrue(!int.contains("::coerceInt"), "phép parse thứ hai quay lại:\n$int")
        // Senior review Pass 1 [P3]: phép soi MÃ không hợp lệ cũng đọc qua parseInt — cùng phần tử mảng với con số sẽ hiện.
        val gate = SourceRoots.body(table, "fun isInvalidValue(id: String, raw: String): Boolean")
        assertTrue(gate.contains("parseInt(id, raw)") && !gate.contains("coerceInt("), "soi mã bằng phép parse thứ hai:\n$gate")
    }

    /** Gateway đếm lượt tra tên feature (mỗi lượt đọc màu cụm = một lượt tra tên). */
    private class Counting(private val inner: HalGateway) : HalGateway by inner {
        val names = ArrayList<String>()
        override fun featureIdByName(constName: String): Int? { names += constName; return inner.featureIdByName(constName) }
    }

    private fun adapterFor(colour: String?, clock: () -> Long): Pair<CarDataAdapter, Counting> {
        val names = listOf("LF", "RF", "LB", "RB").mapIndexed { i, c -> "INSTRUMENT_2IN1_${c}_TYRE_COLOR" to 500 + i }.toMap()
        val gw = Counting(FakeHalGateway(featureNames = names, features = (0..3).associate { 500 + it to colour }))
        return CarDataAdapter(HalBindingTable(gw), { TyreIds.COLOUR.toSet() }, clock = clock) to gw
    }

    @Test
    fun `loi dap rac khong parse duoc thi nguoi nhu moi datum`() {
        var now = 0L
        val (adapter, gw) = adapterFor("int=- float=- buf=4") { now }
        var s = CarStatus()
        repeat(3) { s = adapter.readSlow(s); now += 10_000 }       // 3 lượt trượt của cache vắng
        assertTrue(listOf(s.tyres.cFl, s.tyres.cFr, s.tyres.cRl, s.tyres.cRr).all { it == null })
        gw.names.clear()
        repeat(3) { s = adapter.readSlow(s); now += 10_000 }
        assertEquals(0, gw.names.size, "chuỗi rác ⇒ nguội như lối đọc thường (bản trước: đọc lại MỖI nhịp mãi)")
    }

    @Test
    fun `ma chua-co-so van khong nguoi - so that den la nhip ke thay ngay`() {
        var now = 0L
        val (adapter, gw) = adapterFor("int=0") { now }             // màu cụm 0 = COLOUR_INVALID = "chưa có màu"
        var s = CarStatus()
        repeat(4) { s = adapter.readSlow(s); now += 10_000 }
        gw.names.clear()
        s = adapter.readSlow(s)
        assertEquals(4, gw.names.size, "mã chưa-có-số là getter CÓ trả lời ⇒ không nguội (soát 2.88 Pass 2 giữ nguyên)")
        assertNull(s.tyres.cFl)
    }
}
