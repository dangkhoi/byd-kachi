package com.byd.clusternav.launcher

import com.byd.clusternav.comfort.SeatAutoOffGuard
import com.byd.clusternav.comfort.SeatAutoOffGuard.Signature
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.6 · HAL-EVENTS — khoá tầng sự kiện: bảng getter ↔ hằng ([HalEventMap]), giao thức ([HalEventWire]), bộ nhớ + ba vai còn lại của
 * poll ([HalEventCache]), gateway bọc, nhịp thân xe chỉ còn là đường lùi, lượt đọc theo sự kiện 0 HAL, kế hoạch/nguồn helper
 * ([CarEventFeed]) và dấu vân tay ghế. Doc: `docs/diagnostics/hal-events-body-seat-2026-10-15.md`.
 *
 * Thử ĐỎ [ĐO 15/10, mỗi dòng một lần sửa rồi hoàn tác]:
 *  - bỏ `if (!b.trusted && id !in seen) return null` (HalEventCache.hit) ⇒ `id chua chung minh chi dung sau su kien dau` ĐỎ;
 *  - bỏ `if (!hello) return null` ⇒ `helper vang thi khong bao gio thay poll` ĐỎ;
 *  - bodyNeeded bỏ vế `covered` ⇒ `nhip than xe chi la duong lui` ĐỎ;
 *  - `signatureOf` trả NOT_SEEN khi chưa có sự kiện tắt ⇒ `dau van tay` ĐỎ.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HalEventsTest {

    private val names = mapOf(
        "BODYWORK_LEFT_HAND_FRONT_DOOR" to 692060168, "BODYWORK_RIGHT_HAND_FRONT_DOOR" to 692060170,
        "BODYWORK_LEFT_HAND_REAR_DOOR" to 692060172, "BODYWORK_RIGHT_HAND_REAR_DOOR" to 692060174,
        "BODYWORK_LUGGAGE_DOOR" to 692060186, "AC_WIND_LEVEL" to 111, "PM2P5_VALUE_IN" to 201, "PM2P5_VALUE_OUT" to 202,
        "TYRE_PRESSURE_VALUE_LEFT_FRONT" to -1728052956, "SET_DRIVER_SEAT_VENTILATING_STATE" to 1335885832,
        "Setting.SETTING_PAD_EXIT_PIN_CODE_PROCESS" to 156237839, "Setting.SETTING_UNLOCK_DRIVING_PASSWORD_POPUP_TYPE" to 156237858,
    )

    private class Counting(private val inner: HalGateway) : HalGateway by inner {
        var reads = 0
        override fun getter(deviceFqn: String, method: String, arg: Int?): String? { reads++; return inner.getter(deviceFqn, method, arg) }
        override fun featureGet(deviceFqn: String, id: Int): String? { reads++; return inner.featureGet(deviceFqn, id) }
    }

    private fun gw(getters: Map<String, Map<Int, String?>> = mapOf("getDoorState" to mapOf(1 to "0", 2 to "0", 3 to "0", 4 to "0", 6 to "0"))) =
        Counting(FakeHalGateway(gettersByArg = getters, getters = mapOf("getAcWindLevel" to "3", "getPM2p5Value" to "[10, 20]"), featureNames = names))

    private var now = 1_000_000L
    private fun cache() = HalEventCache(clock = { now })

    private fun live(c: HalEventCache, g: HalGateway, vararg datums: String): List<HalEventMap.Binding> {
        val b = datums.map { requireNotNull(HalEventMap.bindingOf(it, g)) { it } }
        c.setPlan(b)
        c.onHello()
        c.onReg(b.flatMap { it.ids }.toSet(), emptySet())
        return b
    }

    // ── bảng ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun `moi getter dang poll deu co quyet dinh su kien hoac ly do giu poll`() {
        val undecided = TelemetryRegistry.ALL.mapNotNull { spec ->
            val r = HalBindingTable.routeOf(spec.bindingKey) as? BindingRoute.NamedMethod ?: return@mapNotNull null
            val simple = r.fqn.substringAfterLast('.')
            val key = "$simple.${r.method}#${HalBindingTable.readArg(spec.id) ?: ""}"
            if (key in HalEventMap.GETTERS || "$simple.${r.method}" in HalEventMap.POLL_ONLY) null else "${spec.id}=$key"
        }
        assertEquals(emptyList<String>(), undecided)
    }

    @Test fun `cua va cop map vao dung hang, tin ngay vi da do su kien tren xe`() {
        val g = gw()
        val trunk = HalEventMap.bindingOf("tailgate_status", g)!!
        assertEquals(listOf(692060186), trunk.ids)
        assertEquals("android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice", trunk.device)
        assertTrue(trunk.trusted)
        assertEquals(listOf(692060168), HalEventMap.bindingOf("door_lf", g)!!.ids)
        assertFalse(HalEventMap.bindingOf("ac_wind", g)!!.trusted)
    }

    @Test fun `datum nhanh va getter bien doi giu poll, hang vang tren xe thi khong map`() {
        val g = gw()
        assertNull(HalEventMap.bindingOf("speed", g))
        assertNull(HalEventMap.bindingOf("odometer", g))
        assertNull(HalEventMap.bindingOf("soc", g))   // tên hằng không có trong xe giả ⇒ poll
    }

    // ── giao thức ─────────────────────────────────────────────────────────────────────────────────────────

    @Test fun `giao thuc doc dung ba loai dong, dong den cu khong bi nuot`() {
        assertEquals(HalEventWire.Msg.Hello(2), HalEventWire.parse("""{"topic":"hal.hello","type":2}"""))
        assertEquals(
            HalEventWire.Msg.Reg(1001, "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice", setOf(0x29400008, 0x2940001a), setOf(0x123), ""),
            HalEventWire.parse("""{"topic":"hal.reg","type":1001,"dev":"android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice","ok":"29400008,2940001a","rej":"123","err":""}"""),
        )
        assertEquals(
            HalEventWire.Msg.Event(-1728052956, "x", 250, 250.0),
            HalEventWire.parse("""{"topic":"hal.ev","type":-1728052956,"dev":"x","v":250,"d":250.0}"""),
        )
        assertNull(HalEventWire.parse("""{"topic":"light.onLightOn","type":4}"""))
        assertNull(HalEventWire.parse("""{"topic":"hal.ev","type":"x"}"""))
    }

    // ── bộ nhớ ────────────────────────────────────────────────────────────────────────────────────────────

    @Test fun `helper vang thi khong bao gio thay poll`() {
        val c = cache(); val g = gw()
        val b = HalEventMap.bindingOf("door_lf", g)!!
        c.setPlan(listOf(b))
        c.onReg(setOf(692060168), emptySet())
        c.onEvent(692060168, 1, 1.0)
        assertNull(c.hit(b.key), "chưa hal.hello ⇒ gateway thật")
        c.onHello()
        assertEquals("1", c.hit(b.key))
        c.linkDown()
        assertNull(c.hit(b.key), "đứt dây ⇒ poll như trước")
    }

    @Test fun `id bi tu choi dang ky khong thay poll`() {
        val c = cache(); val g = gw()
        val b = live(c, g, "door_lf").single()
        c.onReg(emptySet(), setOf(692060168))
        c.onEvent(692060168, 1, 1.0)
        assertNull(c.hit(b.key))
    }

    @Test fun `id chua chung minh chi dung sau su kien dau, het han doi soat thi doc that`() {
        val c = cache(); val g = gw()
        val b = live(c, g, "ac_wind").single()
        c.store(b.key, "3")
        assertNull(c.hit(b.key), "AC chưa có sự kiện nào ⇒ chưa tin (bus có thể không đẩy id này)")
        c.onEvent(111, 4, 4.0)
        assertEquals("4", c.hit(b.key))
        now += HalEventCache.RECONCILE_MS
        assertNull(c.hit(b.key), "quá 60 s không làm mới ⇒ một lượt đọc thật")
        c.store(b.key, "5")
        assertEquals("5", c.hit(b.key))
    }

    /** Soát Pass 6 [P2] — thử ĐỎ: bỏ vế `b.device in seenDevice` trong `HalEventCache.hit` ⇒ assert đầu đỏ. */
    @Test fun `id tin ngay cung chi thay poll sau khi DEVICE ay da day it nhat mot su kien toi helper`() {
        val c = cache(); val g = gw()
        val (lf, trunk) = live(c, g, "door_lf", "tailgate_status")
        c.store(lf.key, "0"); c.store(trunk.key, "0")
        assertNull(c.hit(lf.key), "đăng ký OK + đã đọc thật, nhưng Bodywork chưa đẩy gì tới helper ⇒ vẫn poll (bằng chứng bus ≠ bằng chứng uid shell)")
        c.onEvent(692060186, 1, 1.0)   // cốp đẩy ⇒ device Bodywork đã chứng minh cho uid shell
        assertEquals("0", c.hit(lf.key), "cửa trước-trái (tin ngay, cùng device) dùng số đối soát đã cất")
        assertEquals("1", c.hit(trunk.key))
        c.linkDown(); val again = live(c, g, "door_lf").single(); c.store(again.key, "0")
        assertNull(c.hit(again.key), "lần nối mới ⇒ chứng minh lại từ đầu")
    }

    @Test fun `mang va dai hop le dung y getter`() {
        val c = cache(); val g = gw()
        val pm = live(c, g, "pm25_value", "pm25_outside")
        assertEquals(pm[0].key, pm[1].key, "hai datum cùng getter ⇒ cùng khoá")
        c.onEvent(201, 11, 0.0); c.onEvent(202, 22, 0.0)
        assertEquals("[11, 22]", c.hit(pm[0].key))
        assertEquals(22, HalBindingTable.parseInt("pm25_outside", c.hit(pm[0].key)))
        val tyre = live(c, g, "tyre_p_fl").single()
        c.onEvent(-1728052956, 4095, 0.0)
        assertEquals(HalEventCache.SENTINEL_INVALID.toString(), c.hit(tyre.key))
    }

    @Test fun `su kien tra ve datum doi, khong doi thi rong`() {
        val c = cache(); val g = gw()
        live(c, g, "door_lf", "tailgate_status")
        assertEquals(setOf("tailgate_status"), c.onEvent(692060186, 1, 1.0).datums)
        assertTrue(c.onEvent(692060186, 1, 1.0).datums.isEmpty())
        assertTrue(c.onEvent(999, 1, 1.0).datums.isEmpty())
    }

    // ── gateway bọc + adapter ────────────────────────────────────────────────────────────────────────────

    @Test fun `gateway boc tra gia tri su kien, 0 luot HAL`() {
        val c = cache(); val g = gw()
        live(c, g, "door_lf")
        val table = HalBindingTable(EventCachedGateway(g, c))
        assertEquals(false, table.readBool("door_lf"))
        assertEquals(1, g.reads, "chưa sự kiện ⇒ đọc thật (và cất)")
        c.onEvent(692060168, 1, 1.0)
        assertEquals(true, table.readBool("door_lf"))
        assertEquals(1, g.reads, "có sự kiện ⇒ không gọi HAL")
    }

    @Test fun `nhip than xe chi la duong lui`() {
        val c = cache(); val g = gw()
        val demand = setOf("door_lf", "door_rf", "door_lr", "door_rr", "tailgate_status")
        val adapter = CarDataAdapter(HalBindingTable(EventCachedGateway(g, c)), { demand }, covered = c::covers)
        assertTrue(adapter.bodyNeeded(), "helper vắng ⇒ nhịp 3 s như trước")
        live(c, g, *demand.toTypedArray())
        assertTrue(adapter.bodyNeeded(), "đăng ký OK nhưng chưa có giá trị ⇒ vẫn đọc")
        listOf(692060168, 692060170, 692060172, 692060174, 692060186).forEach { c.onEvent(it, 0, 0.0) }
        assertFalse(adapter.bodyNeeded(), "mọi cửa + cốp có sự kiện ⇒ nhịp 3 s nghỉ")
        now += HalEventCache.RECONCILE_MS
        assertTrue(adapter.bodyNeeded(), "hết hạn đối soát ⇒ đọc lại một lượt")
    }

    @Test fun `luot doc theo su kien chi lam moi datum do, 0 HAL`() {
        val c = cache(); val g = gw()
        val adapter = CarDataAdapter(HalBindingTable(EventCachedGateway(g, c)), { setOf("door_lf", "ac_wind") })
        live(c, g, "door_lf")
        c.onEvent(692060168, 1, 1.0)
        val before = CarStatus(climate = CarStatus().climate.copy(fanLevel = 7))
        val after = adapter.readEvents(before, setOf("door_lf"))
        assertEquals(true, after.body.doorLfOpen)
        assertEquals(7, after.climate.fanLevel, "datum khác giữ nguyên, không đọc")
        assertEquals(0, g.reads)
    }

    @Test fun `su kien cop vao ca datum va nut cop, khong can man bay datum cop`() {
        val c = cache(); val g = gw()
        val adapter = CarDataAdapter(HalBindingTable(EventCachedGateway(g, c)), { emptySet() }, controlDemand = { setOf("trunk") }, covered = c::covers)
        live(c, g, "tailgate_status")
        assertTrue(adapter.bodyNeeded(), "chưa có sự kiện ⇒ nhịp thân xe vẫn đọc cốp")
        c.onEvent(692060186, 1, 1.0)
        assertFalse(adapter.bodyNeeded(), "cốp có sự kiện ⇒ nhịp 3 s nghỉ")
        val s = adapter.readEvents(CarStatus(), setOf("tailgate_status"))
        assertEquals(true, s.body.tailgateOpen)
        assertEquals(1, s.controls["trunk"])
        c.onEvent(692060186, 0, 0.0)
        assertEquals(0, adapter.readEvents(s, setOf("tailgate_status")).controls["trunk"])
        assertEquals(0, g.reads)
    }

    @Test fun `repo gop su kien thanh mot luot phat`() = runTest {
        val seen = mutableListOf<Set<String>>()
        val reader = object : CarStatusReader {
            override fun readFast(prev: CarStatus) = prev
            override fun readSlow(prev: CarStatus) = prev
            override fun readEvents(prev: CarStatus, ids: Set<String>): CarStatus { seen += ids; return prev }
        }
        val repo = CarStatusRepository(reader, backgroundScope)
        repo.onEvents(setOf("door_lf")); repo.onEvents(setOf("tailgate_status"))
        runCurrent()
        advanceTimeBy(CarStatusRepository.EVENT_COALESCE_MS + 1); runCurrent()
        assertEquals(listOf(setOf("door_lf", "tailgate_status")), seen)
        repo.onEvents(setOf("door_rf"))
        advanceTimeBy(CarStatusRepository.EVENT_COALESCE_MS + 1); runCurrent()
        assertEquals(setOf("door_rf"), seen.last())
    }

    // ── kế hoạch + nguồn helper ──────────────────────────────────────────────────────────────────────────

    @Test fun `ke hoach theo nhu cau man, man tat thi tha nguon`() {
        val wants = mutableListOf<Pair<HalSignalDemand.Source, Boolean>>()
        val lines = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val c = cache()
        val feed = CarEventFeed(gw(), c, { s, on -> wants += s to on }, { lines += it }, {}, { logs += it }, count = {})
        feed.screen(setOf("door_lf", "speed"), emptySet())
        assertEquals(listOf(HalSignalDemand.Source.CAR_EVENTS to true), wants)
        assertEquals("sub android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice=692060168", lines.last())
        feed.screen(setOf("door_lf", "speed"), emptySet())
        assertEquals(1, lines.size, "nhu cầu không đổi ⇒ không gửi lại")
        feed.lit(false)
        assertEquals("sub", lines.last())
        assertEquals(HalSignalDemand.Source.CAR_EVENTS to false, wants.last())
        feed.onLinkDown()
        assertTrue(logs.none { it.startsWith("chế độ=POLL") }, "đang POLL sẵn ⇒ không log trùng")
        feed.onMessage(HalEventWire.Msg.Hello(2))
        assertTrue(logs.any { it.startsWith("chế độ=SỰ KIỆN") })
    }

    @Test fun `canh ghe xin su kien ghe va dau van tay, su kien dau duoc log`() {
        val wants = mutableListOf<Pair<HalSignalDemand.Source, Boolean>>()
        val lines = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val got = mutableListOf<Set<String>>()
        val c = cache()
        val feed = CarEventFeed(gw(), c, { s, on -> wants += s to on }, { lines += it }, { got += it }, { logs += it }, count = {})
        feed.seatWatch(setOf("seat_vent_state"))
        assertEquals(listOf(HalSignalDemand.Source.SEAT_WATCH to true), wants)
        assertTrue(lines.last().contains("BYDAutoSettingDevice=156237839,156237858,1335885832"), lines.last())
        feed.onMessage(HalEventWire.Msg.Hello(2))
        feed.onMessage(HalEventWire.Msg.Reg(1023, "s", setOf(156237839, 156237858, 1335885832), emptySet(), ""))
        feed.onMessage(HalEventWire.Msg.Event(1335885832, "s", 1, 1.0))
        assertEquals(listOf(setOf("seat_vent_state")), got)
        feed.onMessage(HalEventWire.Msg.Event(156237839, "s", 0, 0.0))
        assertEquals(1, got.size, "dấu vân tay không phải datum")
        assertTrue(c.signalSeenAt(156237839) != null && c.signalRegistered(156237839))
        assertEquals(2, logs.count { it.startsWith("sự kiện ĐẦU") })
        feed.seatWatch(null)
        assertEquals(HalSignalDemand.Source.SEAT_WATCH to false, wants.last())
    }

    @Test fun `dau van tay`() {
        assertEquals(Signature.UNOBSERVABLE, SeatAutoOffGuard.signatureOf(false, 100_000, 99_000, 90_000))
        assertEquals(Signature.SEEN, SeatAutoOffGuard.signatureOf(true, 100_000, 95_000, 92_000))
        assertEquals(Signature.NOT_SEEN, SeatAutoOffGuard.signatureOf(true, 100_000, null, 90_000))
        assertEquals(Signature.UNOBSERVABLE, SeatAutoOffGuard.signatureOf(true, 100_000, null, 98_000), "dấu chưa kịp tới")
        assertEquals(Signature.UNOBSERVABLE, SeatAutoOffGuard.signatureOf(true, 100_000, null, null), "không thấy lần tắt qua sự kiện ⇒ không kết luận")
    }

    @Test fun `nguon moi dem chung voi nguon cu`() {
        var acquired = 0
        val d = HalSignalDemand(60_000, { _, _ -> {} }, { acquired++ }, {})
        d.set(HalSignalDemand.Source.CAR_EVENTS, true)
        d.set(HalSignalDemand.Source.SEAT_WATCH, true)
        d.set(HalSignalDemand.Source.CAMERA, true)
        assertEquals(1, acquired)
    }
}
