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

    /**
     * T7 (owner 2026-09-15 "nút mở 50%"): mức 2 phải đi THẲNG xuống writeArgs thành WINDOW_OPEN_HALF=4 — qua cả
     * `coverLevel()` (dock/CapTest bấm nút "Nửa") lẫn `act()`/actByKind (voice/gói lệnh). Nếu một đường nào gập mức
     * về bool (`level > 0` ⇒ mở) thì nút "Nửa" sẽ MỞ HẾT — đúng lỗi phải khoá.
     */
    @Test fun `coverLevel 2 di thang xuong OPEN_HALF qua ca coverLevel lan act`() {
        val gw = FakeHalGateway(namedRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.coverLevel("win_lf", 2))
        assertEquals(listOf(1, 4), gw.namedCalls[0].args, "coverLevel(2) → [cửa 1, OPEN_HALF=4], không phải mở hết")
        assertTrue(adapter.act("win_lf", 2))
        assertEquals(listOf(1, 4), gw.namedCalls[1].args, "act(2) qua actByKind cũng phải giữ mức, không gập về bool")
        // 0/1 y như cũ — cover(bool) đi qua coverLevel nhưng byte không đổi.
        assertTrue(adapter.cover("win_lf", false))
        assertEquals(listOf(1, 2), gw.namedCalls[2].args, "cover(false) vẫn → CLOSE=2")
    }

    @Test fun `select routes index for feature-id control`() {
        val gw = FakeHalGateway(featureRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        // ⚠ WP8 2026-09-20: `ambient_color` cũng đã purge (drive_mode từng đứng đây, gỡ ở (V) 2026-09-17) ⇒ mốc
        // nay là `screen_rotation` — SELECT còn sống DUY NHẤT mà `bindingKey` là một feature-id SỐ (`1330643005`),
        // tức đúng hình dạng bài này đo. `camera_view` không dùng được: nó route named-method.
        assertTrue(adapter.select("screen_rotation", 2))
        assertEquals(1330643005, gw.featureSetCalls[0].id)
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
        assertTrue(adapter.act("headlight_mode", 3))  // SELECT (feature)
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
        assertTrue(p.actByKind("headlight_mode", 2))    // SELECT
        assertEquals(
            listOf("toggle(lock,true)", "step(fan,5)", "cover(win_lf,true)", "select(headlight_mode,2)"),
            p.calls,
        )
    }

    @Test fun `buoc STEP va SELECT KHONG bi nen thanh 1-0`() {
        // Đây là lỗi thật đã vá: ô gói lệnh từng bắn MỌI bước qua toggle ⇒ `step(fan,5)` thành `toggle(fan,true)`
        // (ghi 1 thay vì 5) và `select(headlight_mode,2)` thành ghi 1.
        val p = RecordingPort()
        p.actByKind("fan", 5); p.actByKind("headlight_mode", 2)
        assertEquals(listOf("step(fan,5)", "select(headlight_mode,2)"), p.calls, "tham số phải đi nguyên vẹn")
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
