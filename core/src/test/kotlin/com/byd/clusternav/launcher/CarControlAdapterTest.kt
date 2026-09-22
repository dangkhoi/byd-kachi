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

    @Test fun `toggle kinh lai mo derives (window,state)`() {
        val gw = FakeHalGateway(namedRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.toggle("win_lf", true))
        assertEquals(listOf(1, 1), gw.namedCalls[0].args)
    }

    /**
     * 1.94 (owner 2026-09-22): nút 50% nay là control RIÊNG `win_half_*` (TOGGLE mở-50%↔đóng), không còn là
     * mức 2 của COVER. Chạm 1 (primary>0) → WINDOW_OPEN_HALF=4; chạm lại (primary 0) → WINDOW_CLOSE=2.
     * act()/actByKind (voice/gói lệnh) phải giữ nguyên byte đó.
     */
    @Test fun `nut 50% kinh la control rieng mo half dong close`() {
        val gw = FakeHalGateway(namedRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        assertTrue(adapter.toggle("win_half_lf", true))
        assertEquals(listOf(1, 4), gw.namedCalls[0].args, "50% kính lái → [cửa 1, OPEN_HALF=4]")
        assertTrue(adapter.act("win_half_lf", 1))
        assertEquals(listOf(1, 4), gw.namedCalls[1].args, "act qua actByKind giữ nguyên byte")
        assertTrue(adapter.toggle("win_half_lf", false))
        assertEquals(listOf(1, 2), gw.namedCalls[2].args, "chạm lại → CLOSE=2")
    }

    @Test fun `select va step day nguyen gia tri xuong duong ghi`() {
        val gw = FakeHalGateway(featureRc = 0L, namedRc = 0L)
        val adapter = CarControlAdapter(HalBindingTable(gw))
        // ⚠⚠ 1.90 2026-09-21 — bài này ĐỔI HÌNH vì mốc cũ đã hết: `drive_mode` gỡ ở (V), `ambient_color` purge ở
        // WP8, và lượt này owner xoá `screen_rotation` (mốc cuối). [ĐO] registry nay **không còn nút SELECT nào có
        // `bindingKey` là feature-id SỐ** — hai nút SELECT còn sống (`seatc`/`seath`) đều route named-method.
        //
        // Bất biến cần canh vẫn là cái cũ: **chỉ số/mức đi NGUYÊN VẸN xuống đường ghi, không bị nén về 1/0**. Nên
        // bài đo nó ở CẢ HAI đường còn thật: SELECT qua named-method, và feature-id SỐ qua một nút STEP.
        assertTrue(adapter.select("seatc", 2))
        assertEquals("setSeatVentilatingState", gw.namedCalls[0].method)
        assertEquals(listOf(1, 3), gw.namedCalls[0].args, "index 2 → mức 2 → state khung 3 (ControlLevels), seatID 1")
        // Đường feature-id SỐ: `fan` (STEP, id 501219340) — giá trị 5 phải tới nguyên, không thành 1.
        assertTrue(adapter.step("fan", 5))
        assertEquals(501219340, gw.featureSetCalls[0].id)
        assertEquals(5, gw.featureSetCalls[0].value)
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
        assertTrue(adapter.act("seatc", 2))           // SELECT (⚠ 1.90: mốc cũ `headlight_mode` đã xoá)
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
        assertTrue(p.actByKind("sunshade", 1))          // COVER (kính nay TOGGLE; sunshade còn COVER)
        assertTrue(p.actByKind("seatc", 2))             // SELECT (⚠ 1.90: mốc cũ `headlight_mode` đã xoá)
        assertEquals(
            listOf("toggle(lock,true)", "step(fan,5)", "cover(sunshade,true)", "select(seatc,2)"),
            p.calls,
        )
    }

    @Test fun `buoc STEP va SELECT KHONG bi nen thanh 1-0`() {
        // Đây là lỗi thật đã vá: ô gói lệnh từng bắn MỌI bước qua toggle ⇒ `step(fan,5)` thành `toggle(fan,true)`
        // (ghi 1 thay vì 5) và `select(<SELECT>,2)` thành ghi 1. ⚠ 1.90: mốc cũ `headlight_mode` xoá ⇒ dùng `seatc`.
        val p = RecordingPort()
        p.actByKind("fan", 5); p.actByKind("seatc", 2)
        assertEquals(listOf("step(fan,5)", "select(seatc,2)"), p.calls, "tham số phải đi nguyên vẹn")
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
        assertFalse(adapter.toggle("win_lf", true))
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
