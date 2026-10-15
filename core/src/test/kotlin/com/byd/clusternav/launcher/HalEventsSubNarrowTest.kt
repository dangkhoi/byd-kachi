package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.8 · HAL-EVENTS-SUB-NARROW — khoá lỗi thu hẹp tập xin [ĐO xe 15/10 16:13:54–55, Seal, 3.1.7]: lượt đầu đăng ký đủ 8 device;
 * HOME `onStop` 0,5 s sau ⇒ dòng `sub` chỉ còn 4 id ghế (SEAT_WATCH) ⇒ helper huỷ 7 device ngay. Doc
 * `docs/diagnostics/hal-events-body-seat-2026-10-15.md` §"Đo xe 15/10 16:13".
 *
 * Thử ĐỎ [chạy thật, mỗi dòng một lần sửa rồi hoàn tác]:
 *  - `CarEventFeed.replan` bỏ nhánh `if (narrows && !due)` (thu hẹp ngay) ⇒ 3 bài ĐỎ (`man khuat ngan…`, `het han…`, `them id…`);
 *  - nhánh hoãn gửi `target` thay `applied + target` ⇒ cùng 3 bài ĐỎ;
 *  - `HalEventCache.onReg` bỏ khối `device != null` ⇒ `hal reg thay tap cua device` ĐỎ.
 */
class HalEventsSubNarrowTest {

    private val names = mapOf(
        "BODYWORK_LEFT_HAND_FRONT_DOOR" to 692060168, "BODYWORK_LUGGAGE_DOOR" to 692060186,
        "SET_DRIVER_SEAT_VENTILATING_STATE" to 1335885832,
        "Setting.SETTING_PAD_EXIT_PIN_CODE_PROCESS" to 156237839, "Setting.SETTING_UNLOCK_DRIVING_PASSWORD_POPUP_TYPE" to 156237858,
    )

    private fun gw() = FakeHalGateway(gettersByArg = mapOf("getDoorState" to mapOf(1 to "0", 6 to "0")), featureNames = names)

    /** Bộ hẹn giả: giữ việc, [fire] chạy việc chưa huỷ. */
    private class FakeTimer {
        val tasks = mutableListOf<Pair<() -> Unit, BooleanArray>>()
        val schedule: (Long, () -> Unit) -> (() -> Unit)? = { _, t -> val c = BooleanArray(1); tasks += t to c; { c[0] = true } }
        fun pending() = tasks.count { !it.second[0] }
        fun fire() = tasks.toList().forEach { (t, c) -> if (!c[0]) { c[0] = true; t() } }
    }

    private val wants = mutableListOf<Pair<HalSignalDemand.Source, Boolean>>()
    private val lines = mutableListOf<String>()
    private val logs = mutableListOf<String>()

    private fun feed(timer: FakeTimer?) = CarEventFeed(
        gw(), HalEventCache(), { s, on -> wants += s to on }, { lines += it }, {}, { logs += it }, count = {},
        schedule = timer?.schedule ?: { _, _ -> null },
    )

    private val door = "692060168"
    private val seatVent = "1335885832"

    @Test fun `tap xin la hop man va canh ghe trong mot dong`() {
        val f = feed(FakeTimer())
        f.seatWatch(setOf("seat_vent_state"))
        f.screen(setOf("door_lf"), emptySet())
        assertTrue(lines.last().contains(door) && lines.last().contains(seatVent), lines.last())
    }

    @Test fun `man khuat ngan khong huy dang ky, quay lai khong gui dong moi`() {
        val t = FakeTimer()
        val f = feed(t)
        f.seatWatch(setOf("seat_vent_state"))
        f.screen(setOf("door_lf"), emptySet())
        val full = lines.last()
        val sent = lines.size
        f.screen(null, emptySet())   // HOME onStop (16:13:55 trên xe)
        assertEquals(full, lines.last(), "đang hoãn ⇒ id cửa còn xin")
        assertEquals(sent, lines.size, "giữ nguyên tập ⇒ 0 dòng sub mới")
        assertFalse(wants.contains(HalSignalDemand.Source.CAR_EVENTS to false), "nguồn helper giữ tới lúc thu hẹp thật")
        assertEquals(1, t.pending())
        f.screen(setOf("door_lf"), emptySet())   // HOME dựng lại trong hạn
        assertEquals(sent, lines.size, "quay lại trong hạn ⇒ không đăng ký lại")
        assertEquals(0, t.pending(), "hẹn thu hẹp bị huỷ")
        t.fire()
        assertEquals(full, lines.last())
    }

    @Test fun `het han ma nhu cau van thap thi thu hep, ghe van giu, ly do duoc log`() {
        val t = FakeTimer()
        val f = feed(t)
        f.seatWatch(setOf("seat_vent_state"))
        f.screen(setOf("door_lf"), emptySet())
        f.screen(null, emptySet())
        t.fire()
        assertFalse(lines.last().contains(door), lines.last())
        assertTrue(lines.last().contains(seatVent), "id ghế còn được canh ⇒ không bao giờ bị bỏ")
        assertEquals(HalSignalDemand.Source.CAR_EVENTS to false, wants.last())
        assertTrue(logs.any { it.startsWith("đăng ký sự kiện:") && it.contains("hết hoãn thu hẹp") && it.contains("−1") }, logs.toString())
        // Màn quay lại SAU khi đã thu ⇒ xin lại NGAY (không hẹn).
        f.screen(setOf("door_lf"), emptySet())
        assertTrue(lines.last().contains(door), lines.last())
        assertEquals(HalSignalDemand.Source.CAR_EVENTS to true, wants.last())
    }

    @Test fun `them id gui ngay ca khi dang hoan thu hep`() {
        val t = FakeTimer()
        val f = feed(t)
        f.screen(setOf("door_lf"), emptySet())
        f.screen(setOf("tailgate_status"), emptySet())   // ảnh state trung gian: bớt cửa, thêm cốp
        assertTrue(lines.last().contains("692060186"), "id mới gửi NGAY")
        assertTrue(lines.last().contains(door), "id cũ còn giữ trong hạn")
        t.fire()
        assertFalse(lines.last().contains(door))
        assertTrue(lines.last().contains("692060186"))
    }

    @Test fun `khong co bo hen thi thu hep ngay nhu truoc`() {
        val f = feed(null)
        f.seatWatch(setOf("seat_vent_state"))
        f.screen(setOf("door_lf"), emptySet())
        f.screen(null, emptySet())
        assertFalse(lines.last().contains(door))
        assertTrue(lines.last().contains(seatVent))
    }

    @Test fun `hal reg thay tap cua device, id thoi dang ky roi registered`() {
        val c = HalEventCache()
        val dev = "android.hardware.bydauto.setting.BYDAutoSettingDevice"
        c.onReg(setOf(156237839, 156237858), emptySet(), dev)
        assertTrue(c.signalRegistered(156237839))
        c.onReg(setOf(156237858), emptySet(), dev)   // helper thu hẹp: `ok` là TOÀN BỘ tập mới
        assertFalse(c.signalRegistered(156237839))
        assertTrue(c.signalRegistered(156237858))
        c.onReg(setOf(1), emptySet(), "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice")
        assertTrue(c.signalRegistered(156237858), "device khác không đụng tập của device này")
    }

    @Test fun `hal reg thieu dev khong khoa theo chuoi rong - hai dong khong xoa nhau`() {
        // Soát Pass 8 [P3]: `HalEventWire` trả `dev=""` khi dòng thiếu trường ⇒ không được coi "" là một device (dòng sau xoá tập dòng trước).
        val c = HalEventCache()
        c.onReg(setOf(156237839), emptySet(), "")
        c.onReg(setOf(692060168), emptySet(), "")
        assertTrue(c.signalRegistered(156237839) && c.signalRegistered(692060168), "thiếu device ⇒ cộng như cũ, không thay tập theo \"\"")
    }
}
