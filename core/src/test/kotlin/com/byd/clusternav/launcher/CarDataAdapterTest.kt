package com.byd.clusternav.launcher

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** W1b: [CarDataAdapter] build [CarStatus] từ telemetry giả (named + feature), gộp 2 nhịp, 6 method cũ, off-car null. */
@OptIn(ExperimentalCoroutinesApi::class)
class CarDataAdapterTest {

    private fun adapter() = CarDataAdapter(
        HalBindingTable(
            FakeHalGateway(
                getters = mapOf(
                    "getElecPercentageValue" to "82",             // soc
                    "getElecDrivingRangeValue" to "418",          // ev_range_km
                    "getCurrentSpeed" to "56",                    // speed
                    "getWindowOpenPercent" to "98",               // window_* (per-index arg)
                    "getAutoVIN" to "LGXCE4CB0N0000001",          // vin
                    "getPM2p5Level" to "2",                       // pm25_level
                    "getOutCarTemperature" to "26",               // ext_temp
                ),
                // Remediation 2026-09-15: áp lốp 4 góc qua MỘT getter `getTyrePressureValue(area)` (area 1..4), đèn cốt
                // qua `getLightStatus(LOW_BEAM=2)` — cùng getter với 5 đèn khác nên fake phải trả theo arg.
                gettersByArg = mapOf(
                    "getTyrePressureValue" to mapOf(1 to "240", 2 to "235", 3 to "230", 4 to "210"),   // kPa
                    "getLightStatus" to mapOf(2 to "1"),                                                   // light_low_beam
                    "getTemprature" to mapOf(1 to "24"),                                                   // cabin_temp [ĐO xe]
                ),
                features = mapOf(
                    1031798832 to "24",    // cabin_temp
                    339738656 to "120",    // motor_power (fast) — số của xe giả, tra qua TÊN ở dưới
                ),
                // V3 · R11 (1.66): `motor_power` nay bind theo **TÊN HẰNG** (`ENGINE_POWER`) vì số thật đổi theo
                // cấu hình xe ([ĐO nguồn fw-dl3]: 339738656 khi CanFD · 353370144 Toyota · 1033203762 còn lại).
                // Xe giả ở đây khai đúng một cấu hình; đường đọc phải đi qua phép tra tên rồi mới tới số.
                featureNames = mapOf("Engine.ENGINE_POWER" to 339738656),
            ),
        ),
    )

    @Test fun `readSlow builds CarStatus across domains`() {
        val s = adapter().readSlow(CarStatus())
        assertEquals(82, s.energy.soc)
        assertEquals(418, s.energy.evRangeKm)
        assertEquals(24, s.climate.cabinTempC)
        assertEquals(240.0, s.tyres.pFlKpa)          // raw kPa in CarStatus
        assertEquals(98, s.body.windowLfPct)
        assertEquals(true, s.lights.lowBeam)
        assertEquals("LGXCE4CB0N0000001", s.identity.vin)
    }

    @Test fun `readFast builds drivetrain and fast fields`() {        val s = adapter().readFast(CarStatus())
        assertEquals(56, s.drivetrain.speedKmh)
        assertEquals(120, s.energy.motorPowerKw)
    }

    @Test fun `slow preserves fast fields (copy-merge boundary)`() {
        val a = adapter()
        val s = a.readSlow(a.readFast(CarStatus()))
        assertEquals(56, s.drivetrain.speedKmh)          // from fast, kept
        assertEquals(120, s.energy.motorPowerKw)         // fast field survives slow energy.copy
        assertEquals(82, s.energy.soc)                   // from slow
    }

    @Test fun `legacy 6 methods map correctly (kPa to bar)`() {
        val a = adapter()
        assertEquals(82, a.batteryPercent())
        assertEquals(418, a.rangeKm())
        assertEquals(listOf(2.4, 2.35, 2.3, 2.1), a.tirePressuresBar())
        assertEquals(2, a.pm25Level())
        assertEquals(56, a.speedKmh())
        assertEquals(26, a.outsideTempC())
    }

    @Test fun `off-car returns all null`() {
        val a = CarDataAdapter(HalBindingTable(FakeHalGateway()))
        val s = a.readSlow(a.readFast(CarStatus()))
        assertNull(s.energy.soc)
        assertNull(s.drivetrain.speedKmh)
        assertNull(s.tyres.pFlKpa)
        assertNull(s.identity.vin)
        assertNull(a.batteryPercent())
        assertNull(a.tirePressuresBar())
    }

    // ═══ controls map — giá trị THẬT của Ô ĐIỀU KHIỂN đang hiện (2026-09-17 · realtime) ══════════════════

    @Test fun `readFast doc gia tri THAT cho nut dang hien (realtime #5)`() {
        // Xe đặt gió mức 3 ở màn BYD gốc → launcher phải NHẬN 3 (không giữ mặc định RAM 4). Đọc qua readState(fan).
        val a = CarDataAdapter(
            HalBindingTable(FakeHalGateway(getters = mapOf("getAcWindLevel" to "3"))),
            controlDemand = { setOf("fan") },
        )
        assertEquals(3, a.readFast(CarStatus()).controls["fan"], "ô Gió phải đọc mức THẬT của xe (nhịp NHANH #5), không dùng RAM")
    }

    @Test fun `fastNeeded bat khi co o control (setpoint realtime #5)`() {
        // Ô nhiệt/gió trên màn ⇒ nhịp NHANH phải chạy để đọc setpoint trong ~1s (không chờ 10s nhịp chậm).
        val a = CarDataAdapter(HalBindingTable(FakeHalGateway()), demand = { emptySet() }, controlDemand = { setOf("temp") })
        assertTrue(a.fastNeeded(), "có ô control có readKey ⇒ nhịp nhanh phải bật")
        val b = CarDataAdapter(HalBindingTable(FakeHalGateway()), demand = { emptySet() }, controlDemand = { emptySet() })
        assertFalse(b.fastNeeded(), "không datum nhanh + không control ⇒ nhịp nhanh nghỉ")
    }

    @Test fun `readFast giu gia tri cu khi khong con trong nhu cau`() {
        // Nút rời khỏi màn một nhịp giao thời ⇒ GIỮ giá trị cũ (không xoá về "—").
        val a = CarDataAdapter(HalBindingTable(FakeHalGateway()), controlDemand = { emptySet() })
        val s = a.readFast(CarStatus(controls = mapOf("fan" to 5)))
        assertEquals(5, s.controls["fan"])
    }

    @Test fun `readSlow bo nut doc khong ra khoi map (khong bia)`() {
        // Off-car / getter chưa provision ⇒ readState null ⇒ KHÔNG vào map ⇒ ô lùi về RAM, không hiện số bịa.
        val a = CarDataAdapter(HalBindingTable(FakeHalGateway()), controlDemand = { setOf("fan") })
        assertNull(a.readSlow(CarStatus()).controls["fan"])
    }

    // ═══ K1b (2026-09-25) — readControls đi qua HalAbsentCache; fastNeeded ngủ khi mọi nút nguội ═════════════════
    //
    // [ĐO máy ảo clusternav10 · 2.65 · màn chính đứng yên · KachiPerf 3 cửa sổ 60 s]: `HAL đọc = 420/phút` trong khi
    // vòng 09-16 đo idle = 0/phút — dock mặc định 10 nút bị `readState` 1 Hz mãi mãi vì hàm ấy KHÔNG qua cache vắng.
    // Các bài dưới dựng bằng đồng hồ giả + gateway đếm lượt, không hardcode tên nút: tập nút = mọi nút có `readKey`
    // trong ControlRegistry (đúng bộ lọc `CarDataDemand.controlsOf`).

    /** Gateway giả bọc [FakeHalGateway], đếm MỌI lượt đọc đi tới HAL (getter/feature/setting/local). */
    private class CountingGateway(private val inner: HalGateway = FakeHalGateway()) : HalGateway by inner {
        var reads = 0
        override fun getter(deviceFqn: String, method: String, arg: Int?): String? { reads++; return inner.getter(deviceFqn, method, arg) }
        override fun featureGet(deviceFqn: String, id: Int): String? { reads++; return inner.featureGet(deviceFqn, id) }
        override fun settingGet(key: String): String? { reads++; return inner.settingGet(key) }
        override fun localGet(target: String, method: String, arg: Int?): String? { reads++; return inner.localGet(target, method, arg) }
    }

    /** Mọi nút có đường đọc — tập mà `CarDataDemand.controlsOf` sẽ đưa vào `controlDemand` khi chúng ở dock/slot. */
    private val readableControls: Set<String> =
        ControlRegistry.ALL.filter { it.readKey.isNotBlank() }.map { it.id }.toSet()

    private class Clock { var now = 0L }

    private fun k1bAdapter(gw: CountingGateway, clock: Clock, cache: HalAbsentCache, controls: Set<String> = readableControls) =
        CarDataAdapter(
            HalBindingTable(gw),
            demand = { emptySet() },          // màn không bày datum nhanh nào ⇒ chỉ còn nút quyết định vòng nhanh
            absent = cache,
            clock = { clock.now },
            controlDemand = { controls },
        )

    /** (a) off-car: sau 3 nhịp null ⇒ nguội ⇒ 0 lượt HAL mỗi nhịp cho tới mốc thử lại; tới mốc ⇒ đọc lại. */
    @Test fun `K1b a - nut off-car nguoi sau 3 nhip, 0 luot HAL toi moc thu lai`() {
        assertTrue(readableControls.size >= 10, "registry phải có ≥10 nút có readKey (có ${readableControls.size})")
        val gw = CountingGateway(); val clock = Clock()
        val cache = HalAbsentCache(missesBeforeCold = 3, firstRetryMs = 60_000, maxRetryMs = 600_000)
        val a = k1bAdapter(gw, clock, cache)
        var s = CarStatus()
        repeat(3) { s = a.readFast(s); clock.now += 1_000 }
        val perTick = gw.reads / 3
        assertTrue(perTick >= 1, "3 nhịp đầu phải thật sự hỏi HAL (reads=${gw.reads})")
        assertEquals(readableControls.size, cache.coldCount(clock.now), "mọi nút off-car phải nguội sau 3 lần null")

        val before = gw.reads
        repeat(10) { s = a.readFast(s); clock.now += 1_000 }   // t = 3 s … 13 s < 60 s
        assertEquals(0, gw.reads - before, "đang nguội mà vẫn hỏi HAL — cổng vắng không áp cho readControls")

        clock.now = 3_000 + 60_000                              // tới hạn thử lại của lần nguội đầu
        s = a.readFast(s)
        assertEquals(perTick, gw.reads - before, "tới mốc thử lại phải đọc lại đủ một lượt (không khoá vĩnh viễn)")
    }

    /** (b) nút có giá trị THẬT ⇒ vẫn đọc MỖI nhịp (#5 realtime GIỮ NGUYÊN) và fastNeeded luôn true. */
    @Test fun `K1b b - nut co gia tri van doc moi nhip (#5 khong doi)`() {
        val gw = CountingGateway(FakeHalGateway(getters = mapOf("getAcWindLevel" to "3"))); val clock = Clock()
        val a = k1bAdapter(gw, clock, HalAbsentCache(), controls = setOf("fan"))
        var s = CarStatus()
        repeat(5) {
            assertTrue(a.fastNeeded(), "nút sống ⇒ vòng nhanh phải chạy (nhịp $it)")
            s = a.readFast(s); clock.now += 1_000
            assertEquals(3, s.controls["fan"])
        }
        assertEquals(5, gw.reads, "5 nhịp = 5 lượt HAL, không được cache giá trị sống")
    }

    /** (c) người dùng BẤM nút đang nguội ⇒ [CarDataAdapter.forgetAbsentControl] ⇒ nhịp kế đọc lại NGAY. */
    @Test fun `K1b c - bam nut dang nguoi thi nhip ke doc lai ngay`() {
        val gw = CountingGateway(); val clock = Clock()
        val a = k1bAdapter(gw, clock, HalAbsentCache(missesBeforeCold = 3, firstRetryMs = 60_000), controls = setOf("fan"))
        var s = CarStatus()
        repeat(3) { s = a.readFast(s); clock.now += 1_000 }
        val before = gw.reads
        s = a.readFast(s); clock.now += 1_000
        assertEquals(0, gw.reads - before, "phải đang nguội trước khi bấm")

        a.forgetAbsentControl("fan")                            // = WakeOnWriteControl.wake sau lệnh ghi
        s = a.readFast(s)
        assertEquals(1, gw.reads - before, "bấm rồi mà nhịp kế không đọc lại")
    }

    /** (d) fastNeeded: false khi MỌI nút nguội và không datum nhanh; true lại khi tới hạn thử lại (theo đồng hồ). */
    @Test fun `K1b d - fastNeeded ngu khi moi nut nguoi, day lai theo dong ho`() {
        val gw = CountingGateway(); val clock = Clock()
        val a = k1bAdapter(gw, clock, HalAbsentCache(missesBeforeCold = 3, firstRetryMs = 60_000))
        var s = CarStatus()
        assertTrue(a.fastNeeded(), "chưa nguội ⇒ true (hành vi cũ)")
        repeat(3) { s = a.readFast(s); clock.now += 1_000 }
        assertFalse(a.fastNeeded(), "mọi nút nguội + không datum nhanh ⇒ vòng nhanh phải được ngủ")
        clock.now = 3_000 + 60_000
        assertTrue(a.fastNeeded(), "tới hạn thử lại mà vẫn false ⇒ tự khoá vĩnh viễn (CLAUDE.md §3)")
        // một nút sống giữa bầy nguội ⇒ vẫn true (không ngủ oan nút đang sống)
        val gw2 = CountingGateway(FakeHalGateway(getters = mapOf("getAcWindLevel" to "2"))); val c2 = Clock()
        val b = k1bAdapter(gw2, c2, HalAbsentCache(missesBeforeCold = 3, firstRetryMs = 60_000))
        var t = CarStatus(); repeat(3) { t = b.readFast(t); c2.now += 1_000 }
        assertTrue(b.fastNeeded(), "còn một nút đọc được ⇒ vòng nhanh phải chạy")
    }

    /**
     * (e) TOÀN ĐƯỜNG với [CarStatusRepository] thật + đồng hồ ảo của runTest: vòng nhanh ngủ khi mọi nút nguội,
     * và **tự dậy** sau hạn thử lại mà không ai đánh thức — đường phục hồi không bị gate bằng dữ liệu chỉ chính
     * nó làm mới (CLAUDE.md §3). Nhịp chậm không đọc gì (demand rỗng) nên mọi lượt HAL đếm được là của vòng nhanh.
     */
    @Test fun `K1b e - vong nhanh ngu roi tu day sau han thu lai (repo that)`() = runTest {
        val gw = CountingGateway()
        val cache = HalAbsentCache(missesBeforeCold = 3, firstRetryMs = 5_000, maxRetryMs = 5_000)
        val a = CarDataAdapter(HalBindingTable(gw), demand = { emptySet() }, absent = cache,
            clock = { testScheduler.currentTime }, controlDemand = { readableControls })
        val repo = CarStatusRepository(a, backgroundScope, fastMs = 1_000, slowMs = 10_000)
        repo.start(); runCurrent()                        // t=0: nhịp 1
        advanceTimeBy(2_001); runCurrent()                // t=1000, 2000: nhịp 2, 3 ⇒ nguội tới t=7000
        val afterCold = gw.reads
        assertTrue(afterCold > 0)
        advanceTimeBy(9_000); runCurrent()                // t≈11 s: fastNeeded false ở t=3 s ⇒ ngủ tới t=13 s
        assertEquals(0, gw.reads - afterCold, "đang nguội mà vòng nhanh vẫn hỏi HAL")
        advanceTimeBy(3_000); runCurrent()                // t≈14 s: đã qua 13 s ⇒ hỏi lại ⇒ shouldRead true ⇒ đọc
        assertTrue(gw.reads > afterCold, "qua hạn thử lại + một nhịp chậm mà vòng nhanh không tự dậy")
        repo.stop()
    }
}
