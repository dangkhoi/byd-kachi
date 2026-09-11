package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** W1b: [CarControlAdapter] route theo kind, off-car no-op, sentinel = fail, KHÔNG gate. */
class CarControlAdapterTest {

    @Test fun `toggle on routes named-method and returns true when rc ok`() {
        val gw = FakeHalGateway(namedRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.toggle("pm25", true))
        assertEquals("setAutoCleanAirState", gw.namedCalls[0].method)
        assertEquals(listOf(1), gw.namedCalls[0].args)
    }

    @Test fun `step routes value`() {
        val gw = FakeHalGateway(featureRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.step("fan", 5))       // fan = feature 501219340
        assertEquals(5, gw.featureSetCalls[0].value)
    }

    @Test fun `cover open derives (window,state)`() {
        val gw = FakeHalGateway(namedRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.cover("win_lf", true))
        assertEquals(listOf(1, 1), gw.namedCalls[0].args)
    }

    @Test fun `select routes index for feature-id control`() {
        val gw = FakeHalGateway(featureRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.select("drive_mode", 2))   // drive_mode = feature 1272971280
        assertEquals(1272971280, gw.featureSetCalls[0].id)
        assertEquals(2, gw.featureSetCalls[0].value)
    }

    @Test fun `press fires momentary named-method with arg 1`() {
        val gw = FakeHalGateway(namedRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.press("pm25_clean_now"))   // setQuickCleanAirState(1)
        assertEquals("setQuickCleanAirState", gw.namedCalls[0].method)
        assertEquals(listOf(1), gw.namedCalls[0].args)
    }

    @Test fun `act dispatches by ControlKind`() {
        val gw = FakeHalGateway(namedRc = 0L, featureRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.act("lock", 1))            // TOGGLE (named)
        assertTrue(adapter.act("drive_mode", 3))      // SELECT (feature)
    }

    @Test fun `act uy quyen ve actByKind - COVER van dung args (window,state)`() {
        val gw = FakeHalGateway(namedRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.act("win_lf", 1))
        assertEquals(listOf(1, 1), gw.namedCalls[0].args, "act phải đi qua cùng cửa với cover()")
    }

    // ── actByKind: MỘT bảng định tuyến duy nhất cho chỗ chỉ giữ interface (ô gói lệnh W2) ───────────────

    /** Ghi lại CỬA nào bị gọi — điều cần chứng minh là định tuyến, không phải rc. */
    private class RecordingPort : CarControlPort {
        val calls = ArrayList<String>()
        override fun toggle(id: String, on: Boolean): Boolean { calls.add("toggle($id,$on)"); return true }
        override fun step(id: String, value: Int): Boolean { calls.add("step($id,$value)"); return true }
        override fun cover(id: String, open: Boolean): Boolean { calls.add("cover($id,$open)"); return true }
        override fun select(id: String, index: Int): Boolean { calls.add("select($id,$index)"); return true }
        override fun press(id: String): Boolean { calls.add("press($id)"); return true }
    }

    @Test fun `actByKind di DUNG cua theo ControlKind`() {
        val p = RecordingPort()
        assertTrue(p.actByKind("lock", 1))              // TOGGLE
        assertTrue(p.actByKind("fan", 5))               // STEP
        assertTrue(p.actByKind("win_lf", 1))            // COVER
        assertTrue(p.actByKind("drive_mode", 2))        // SELECT
        assertEquals(
            listOf("toggle(lock,true)", "step(fan,5)", "cover(win_lf,true)", "select(drive_mode,2)"),
            p.calls,
        )
    }

    @Test fun `buoc STEP va SELECT KHONG bi nen thanh 1-0`() {
        // Đây là lỗi thật đã vá: ô gói lệnh từng bắn MỌI bước qua toggle ⇒ `step(fan,5)` thành `toggle(fan,true)`
        // (ghi 1 thay vì 5) và `select(drive_mode,2)` thành ghi 1.
        val p = RecordingPort()
        p.actByKind("fan", 5); p.actByKind("drive_mode", 2)
        assertEquals(listOf("step(fan,5)", "select(drive_mode,2)"), p.calls, "tham số phải đi nguyên vẹn")
    }

    @Test fun `buoc BUTTON van bam du arg bang 0`() {
        // `toggle(id, 0 > 0)` = tắt ⇒ nút bấm-một-phát KHÔNG bấm gì cả. BUTTON phải bỏ qua arg.
        val p = RecordingPort()
        assertTrue(p.actByKind("pm25_clean_now", 0))
        assertEquals(listOf("press(pm25_clean_now)"), p.calls)
    }

    @Test fun `actByKind voi ma la thi khong goi cua nao`() {
        val p = RecordingPort()
        assertFalse(p.actByKind("khong_ton_tai", 1))
        assertTrue(p.calls.isEmpty(), "mã lạ ⇒ không bơm gì vào xe")
    }

    @Test fun `off-car write is a no-op returning false`() {
        val adapter = CarControlAdapter(HalBindingTable(FakeHalGateway()))   // rc null
        assertFalse(adapter.toggle("pm25", true))
        assertFalse(adapter.cover("win_lf", true))
        assertFalse(adapter.press("pm25_clean_now"))
    }

    @Test fun `sentinel rc is treated as failure`() {
        val adapter = CarControlAdapter(HalBindingTable(FakeHalGateway(namedRc = -2147482648L)))
        assertFalse(adapter.toggle("pm25", true))
    }

    @Test fun `unknown control id returns false`() {
        assertFalse(CarControlAdapter(HalBindingTable(FakeHalGateway(namedRc = 0L))).toggle("khong_co", true))
    }
}
