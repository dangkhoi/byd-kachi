package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá công cụ "kiểm tra từng nút" (owner 2026-09-15): danh sách phải phủ ĐỦ mọi datum + control, mỗi mục có
 * DIỄN GIẢI thật (không lùi về nhãn), mã hoá nhật ký đi-về đúng. Dựng từ hai registry ⇒ thêm nút mới mà quên
 * diễn giải = đỏ off-car (không phải phát hiện trên xe).
 */
class CapabilityTestPlanTest {

    private val items = CapabilityTestPlan.items()

    @Test
    fun `phu du moi datum va control`() {
        assertEquals(TelemetryRegistry.ALL.size, items.count { it.kind == CapTestKind.INFO }, "thiếu datum")
        assertEquals(ControlRegistry.ALL.size, items.count { it.kind == CapTestKind.ACTION }, "thiếu control")
        assertEquals(TelemetryRegistry.ALL.size + ControlRegistry.ALL.size, items.size)
        // Không trùng id trong cùng một danh sách (id toàn cục duy nhất giữa telemetry+control).
        assertEquals(items.size, items.map { it.id }.toSet().size, "id trùng trong bảng test")
    }

    @Test
    fun `moi muc co dien giai that — khong lui ve nhan`() {
        val missing = items.filter { CapabilityDescriptions.of(it.id) == null }.map { it.id }
        assertEquals(emptyList<String>(), missing, "mục chưa có diễn giải trong CapabilityDescriptions: $missing")
        items.forEach {
            assertTrue(it.descVi.isNotBlank(), "diễn giải VI rỗng: ${it.id}")
            assertTrue(it.descEn.isNotBlank(), "diễn giải EN rỗng: ${it.id}")
        }
    }

    @Test
    fun `hanh dong co runArg — thong tin thi khong`() {
        items.filter { it.kind == CapTestKind.INFO }.forEach { assertEquals(0, it.runArg, "INFO không cần runArg: ${it.id}") }
        // Nút thân xe (kính/cửa/cốp…) phải được đánh dấu cần cảnh báo.
        assertTrue(items.first { it.id == "window" }.needsConfirm, "kính lái phải cần xác nhận")
        assertFalse(items.first { it.id == "readl" }.needsConfirm, "đèn đọc không cần xác nhận")
    }

    @Test
    fun `nhat ky ma hoa di ve dung`() {
        val map = mapOf(
            "readl" to CapTestResult("readl", CapTestVerdict.OK, 1000L, ""),
            "window" to CapTestResult("window", CapTestVerdict.NOT_OK, 2000L, "kẹt nửa chừng"),
        )
        val back = CapTestCodec.decodeAll(CapTestCodec.encodeAll(map))
        assertEquals(map, back)
        // Dòng hỏng ⇒ bỏ qua, không ngã.
        assertEquals(emptyMap<String, CapTestResult>(), CapTestCodec.decodeAll("rác\nlung tung"))
    }

    @Test
    fun `tong ket dem dung`() {
        val results = mapOf(
            "readl" to CapTestResult("readl", CapTestVerdict.OK, 1L),
            "temp" to CapTestResult("temp", CapTestVerdict.NOT_OK, 1L),
        )
        val sum = CapTestSummary.of(items, results)
        assertEquals(items.size, sum.total)
        assertEquals(1, sum.ok)
        assertEquals(1, sum.notOk)
        assertEquals(2, sum.tested)
        assertEquals(items.size - 2, sum.untested)
    }
}
