package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** W1b: định tuyến + parse + sentinel + dispatch của [HalBindingTable] (thuần, gateway giả — off-car). */
class HalBindingTableTest {

    // ── routeOf: 4 đường + None ────────────────────────────────────────────────────────────────────────
    @Test fun `named-method key routes to NamedMethod with derived FQN`() {
        val r = HalBindingTable.routeOf("BYDAutoStatisticDevice.getElecPercentageValue")
        assertTrue(r is BindingRoute.NamedMethod)
        r as BindingRoute.NamedMethod
        assertEquals("android.hardware.bydauto.statistic.BYDAutoStatisticDevice", r.fqn)
        assertEquals("getElecPercentageValue", r.method)
    }

    @Test fun `decimal key routes to Feature`() {
        assertEquals(BindingRoute.Feature(501219340), HalBindingTable.routeOf("501219340"))
    }

    @Test fun `lowercase snake key routes to Setting`() {
        assertEquals(BindingRoute.Setting("unit_temperature"), HalBindingTable.routeOf("unit_temperature"))
    }

    @Test fun `AudioManager key routes to Local`() {
        assertEquals(BindingRoute.Local("AudioManager", "setStreamVolume"), HalBindingTable.routeOf("AudioManager.setStreamVolume"))
    }

    @Test fun `command-wrapper and GPS and empty route to None`() {
        assertEquals(BindingRoute.None, HalBindingTable.routeOf("StartChargingNowCommand"))
        assertEquals(BindingRoute.None, HalBindingTable.routeOf("ADAS_AVH_STATE"))     // UPPER_SNAKE
        assertEquals(BindingRoute.None, HalBindingTable.routeOf("SET_DR_SOC_TARGET"))
        assertEquals(BindingRoute.None, HalBindingTable.routeOf("NaviInfo.lat"))       // GPS needs-car
        assertEquals(BindingRoute.None, HalBindingTable.routeOf(""))
    }

    @Test fun `deviceFqn strips prefix-suffix and lowercases segment`() {
        assertEquals("android.hardware.bydauto.pm2p5.BYDAutoPM2p5Device", HalBindingTable.deviceFqn("BYDAutoPM2p5Device"))
        assertEquals("android.hardware.bydauto.ac.BYDAutoAcDevice", HalBindingTable.deviceFqn("BYDAutoAcDevice"))
    }

    // ── coerce parsers ───────────────────────────────────────────────────────────────────────────────
    @Test fun `coerceInt parses EventValue int-field and plain numbers`() {
        assertEquals(82, HalBindingTable.coerceInt("int=82 float=null buf=-"))
        assertEquals(56, HalBindingTable.coerceInt("56"))
        assertEquals(2, HalBindingTable.coerceInt("2.4"))
        assertNull(HalBindingTable.coerceInt(null))
        assertNull(HalBindingTable.coerceInt("abc"))
    }

    @Test fun `coerceDouble parses EventValue float-field and plain numbers`() {
        assertEquals(2.4, HalBindingTable.coerceDouble("int=2 float=2.4 buf=-"))
        assertEquals(240.0, HalBindingTable.coerceDouble("240"))
    }

    @Test fun `coerceBool parses 1-0 and words`() {
        assertEquals(true, HalBindingTable.coerceBool("1"))
        assertEquals(true, HalBindingTable.coerceBool("true"))
        assertEquals(false, HalBindingTable.coerceBool("0"))
        assertEquals(false, HalBindingTable.coerceBool("off"))
    }

    @Test fun `isSentinelRc flags not-provisioned and invalid`() {
        assertTrue(HalBindingTable.isSentinelRc(-2147482648L))
        assertTrue(HalBindingTable.isSentinelRc(-2147482645L))
        assertTrue(!HalBindingTable.isSentinelRc(0L))
    }

    // ── ĐỌC named-method (đường proven) ─────────────────────────────────────────────────────────────
    @Test fun `readInt reads a named-method telemetry`() {
        val table = HalBindingTable(FakeHalGateway(getters = mapOf("getElecPercentageValue" to "82")))
        assertEquals(82, table.readInt("soc"))   // soc = BYDAutoStatisticDevice.getElecPercentageValue
    }

    @Test fun `sentinel read is reported unavailable (null)`() {
        val table = HalBindingTable(FakeHalGateway(getters = mapOf("getElecPercentageValue" to "int=-2147482648 float=null buf=-")))
        assertNull(table.readInt("soc"))
    }

    @Test fun `off-car read returns null`() {
        assertNull(HalBindingTable(FakeHalGateway()).readInt("soc"))
    }

    @Test fun `per-index getter arg is derived from id suffix`() {
        val gw = FakeHalGateway(getters = mapOf("getWindowOpenPercent" to "98"))
        val table = HalBindingTable(gw)
        assertEquals(98, table.readInt("window_lr"))   // window_lr → arg 3
        assertEquals(3, gw.getterArgs["getWindowOpenPercent"])
    }

    // ── GHI: named / feature-id / multi-arg / sentinel ────────────────────────────────────────────────
    @Test fun `write named-method toggle passes single state arg`() {
        val gw = FakeHalGateway(namedRc = 0L)
        HalBindingTable(gw).write("pm25", 1)   // BYDAutoAcDevice.setAutoCleanAirState
        assertEquals(1, gw.namedCalls.size)
        assertEquals("setAutoCleanAirState", gw.namedCalls[0].method)
        assertEquals(listOf(1), gw.namedCalls[0].args)
        assertEquals("android.hardware.bydauto.ac.BYDAutoAcDevice", gw.namedCalls[0].fqn)
    }

    @Test fun `write feature-id control uses set(id,value) via domain device`() {
        val gw = FakeHalGateway(featureRc = 0L)
        HalBindingTable(gw).write("fan", 4)   // fan = "501219340", domain CLIMATE → AC device
        assertEquals(1, gw.featureSetCalls.size)
        assertEquals(501219340, gw.featureSetCalls[0].id)
        assertEquals(4, gw.featureSetCalls[0].value)
        assertEquals("android.hardware.bydauto.ac.BYDAutoAcDevice", gw.featureSetCalls[0].fqn)
    }

    @Test fun `per-window cover write derives (window,state) args`() {
        val gw = FakeHalGateway(namedRc = 0L)
        HalBindingTable(gw).write("win_rr", 1)   // setBodyWindowCtrlState(window=4, state=1)
        assertEquals(listOf(4, 1), gw.namedCalls[0].args)
        assertEquals("setBodyWindowCtrlState", gw.namedCalls[0].method)
    }

    @Test fun `seat toggle write derives (seatId,state) args`() {
        val gw = FakeHalGateway(namedRc = 0L)
        HalBindingTable(gw).write("seatc", 1)   // setSeatVentilatingState(seatId=1 lái, state=2 mức1)
        assertEquals(listOf(1, 2), gw.namedCalls[0].args)
        HalBindingTable(gw).write("seatc", 0)   // tắt → state=1
        assertEquals(listOf(1, 1), gw.namedCalls[1].args)
    }

    @Test fun `write unknown id returns null`() {
        assertNull(HalBindingTable(FakeHalGateway(namedRc = 0L)).write("khong_ton_tai", 1))
    }

    // ── describeWrite: mô tả THUẦN đường ghi cho cầu kiểm thử (grab-list §9) ────────────────────────────
    @Test fun `describeWrite named-method gives named label plus derived device`() {
        val (route, device) = HalBindingTable.describeWrite(ControlRegistry.byId("win_lf")!!)
        assertEquals("named:setBodyWindowCtrlState", route)
        assertEquals("android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice", device)
    }

    @Test fun `describeWrite feature-id gives hex label plus device by domain`() {
        // fan `501219340` (0x1de0000c) domain CLIMATE → BYDAutoAcDevice; đúng cái owner thấy CHẠY trên xe.
        val (route, device) = HalBindingTable.describeWrite(ControlRegistry.byId("fan")!!)
        assertEquals("feature:0x1de0000c", route)
        assertEquals("android.hardware.bydauto.ac.BYDAutoAcDevice", device)
        // đèn đọc `1330643002` (0x4f50003a): domain LIGHTS route thô tới BYDAutoLightDevice (1004) là SAI —
        // [ĐO] RE 2026-09-14 §4 chứng minh SET_INSIDE_LIGHT_STATE_SET thuộc SETTING (1023), nên `checkDeviceFeatures`
        // trả sentinel ("no permission … device 1004"). Bản vá đặt `halDevice = "BYDAutoSettingDevice"` ⇒ route đúng.
        val (r2, d2) = HalBindingTable.describeWrite(ControlRegistry.byId("readl")!!)
        assertEquals("feature:0x4f50003a", r2)
        assertEquals("android.hardware.bydauto.setting.BYDAutoSettingDevice", d2)
        // ion âm `1337982994`: Domain.CLIMATE route thô tới AC (1000) là SAI — thuộc PM2P5 (1008); override sửa.
        val (r3, d3) = HalBindingTable.describeWrite(ControlRegistry.byId("anion")!!)
        assertEquals("android.hardware.bydauto.pm2p5.BYDAutoPM2p5Device", d3)
        assertEquals("feature:0x4fc00012", r3)
    }

    @Test fun `temp binds real setAcTemperature with (type,value,tempSource,unit) args`() {
        // [ĐO] RE 2026-09-14 §1/§5a: `setTemprature` không tồn tại → reflection trượt. Setter thật 4-arg.
        val gw = FakeHalGateway(namedRc = 0L)
        HalBindingTable(gw).write("temp", 22)
        assertEquals("setAcTemperature", gw.namedCalls[0].method)
        assertEquals(listOf(0, 22, 0, 1), gw.namedCalls[0].args)   // lái=0 · 22°C · tempSource=0 · unit=1 (Celsius)
    }

    @Test fun `describeWrite local and none`() {
        assertEquals("local:AudioManager.setStreamVolume" to "AudioManager", HalBindingTable.describeWrite(ControlRegistry.byId("vol")!!))
        // ⚠ 1.85: ví dụ cũ là `hood` (`BODYWORK_CMD_HOOD`), nay mã đó đã xoá khỏi registry. Thay bằng `headl`
        // — `INSTRUMENT_HEADLIGHT_ON_OFF` cũng là UPPER_SNAKE command-wrapper chưa map, tức cùng ca cần canh:
        // khoá kiểu ấy phải ra `none`, KHÔNG được âm thầm bị đọc thành một feature-id hay một setting key.
        assertEquals("none" to "", HalBindingTable.describeWrite(ControlRegistry.byId("headl")!!))
    }

    @Test fun `describeWrite covers every control without throwing`() {
        // Mọi mã phải mô tả được (không NPE domain nào) — khoá lại khi thêm domain/mã mới.
        ControlRegistry.ALL.forEach { def ->
            val (route, _) = HalBindingTable.describeWrite(def)
            assertTrue(route.isNotBlank(), "route rỗng cho ${def.id}")
        }
    }

    @Test fun `defaultPrimary is on-open-press for toggle-cover-button, default-value for step, zero for select`() {
        assertEquals(1, HalBindingTable.defaultPrimary(ControlRegistry.byId("readl")!!))       // TOGGLE
        assertEquals(1, HalBindingTable.defaultPrimary(ControlRegistry.byId("win_lf")!!))      // COVER
        assertEquals(1, HalBindingTable.defaultPrimary(ControlRegistry.byId("pm25_clean_now")!!)) // BUTTON
        assertEquals(22, HalBindingTable.defaultPrimary(ControlRegistry.byId("temp")!!))       // STEP def.value
        assertEquals(0, HalBindingTable.defaultPrimary(ControlRegistry.byId("headlight_mode")!!))  // SELECT
    }
}
