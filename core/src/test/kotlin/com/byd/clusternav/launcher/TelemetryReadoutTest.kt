package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * W1c: [TelemetryReadout] — biên CarStatus → hiển thị. Field có ⇒ chữ đã format; null/off-car ⇒ "—" (available=false);
 * tier OVERDRIVE/DASHCAST ⇒ needsBadge; PROVEN ⇒ không. Thuần (không Android) → ở :core.
 */
class TelemetryReadoutTest {

    @Test fun `soc co gia tri thi hien so + available + shape RING + PROVEN khong badge`() {
        val v = TelemetryReadout.of("soc", CarStatus(energy = CarStatus.Energy(soc = 82)))!!
        assertEquals("82", v.display)
        assertTrue(v.available)
        assertEquals(WidgetShape.RING, v.shape)
        assertEquals(EvidenceTier.PROVEN, v.tier)
        assertFalse(v.needsBadge)
        assertEquals("82 %", v.displayWithUnit())
    }

    @Test fun `soc null off-car thi hien dash + not available`() {
        val v = TelemetryReadout.of("soc", CarStatus())!!
        assertEquals("—", v.display)
        assertFalse(v.available)
        assertNull(v.valueText)
        assertEquals("—", v.displayWithUnit())
    }

    @Test fun `tier OVERDRIVE can badge`() {
        val v = TelemetryReadout.of("motor_power", CarStatus(energy = CarStatus.Energy(motorPowerKw = 40)))!!
        assertEquals(EvidenceTier.OVERDRIVE, v.tier)
        assertTrue(v.needsBadge)
        assertEquals("40", v.display)
    }

    @Test fun `bool format co ngu nghia`() {
        assertEquals("Bật", TelemetryReadout.of("ac_on", CarStatus(climate = CarStatus.Climate(acOn = true)))!!.display)
        assertEquals("Tắt", TelemetryReadout.of("ac_on", CarStatus(climate = CarStatus.Climate(acOn = false)))!!.display)
        assertEquals("Có", TelemetryReadout.of("is_charging", CarStatus(energy = CarStatus.Energy(isCharging = true)))!!.display)
        assertEquals("Mở", TelemetryReadout.of("door_lf", CarStatus(body = CarStatus.Body(doorLfOpen = true)))!!.display)
        assertEquals("Đóng", TelemetryReadout.of("tailgate_status", CarStatus(body = CarStatus.Body(tailgateOpen = false)))!!.display)
    }

    @Test fun `double format lam tron`() {
        assertEquals("241", TelemetryReadout.of("tyre_p_fl", CarStatus(tyres = CarStatus.Tyres(pFlKpa = 240.6)))!!.display)  // kPa round
        assertEquals("12.4", TelemetryReadout.of("volt_12v", CarStatus(energy = CarStatus.Energy(volt12v = 12.42)))!!.display)
    }

    // ⚠ Bài `radar_zones ghep list` đã gỡ 2026-09-16 cùng datum `radar_zones` (owner gỡ toàn bộ ADAS/an toàn).

    @Test fun `string field passthrough`() {
        assertEquals("P", TelemetryReadout.of("gear", CarStatus(drivetrain = CarStatus.Drivetrain(gear = "P")))!!.display)
    }

    @Test fun `genuine NEEDS_CAR (GPS - binding None) van co case va tra dash`() {
        // gps_lat/gps_elevation bindingKey = NaviInfo.* → BindingRoute.None (BLOCKED-BY-DESIGN: quyền location đã
        // retire) → không có đường đọc → luôn "—". (of() vẫn KHÁC null vì id có trong registry — có case format.)
        listOf("gps_lat", "gps_lon", "gps_elevation", "gps_heading", "target_soc").forEach { id ->
            val v = TelemetryReadout.of(id, CarStatus())
            assertTrue(v != null, "of($id) phải khác null (id trong registry)")
            assertFalse(v!!.available, "$id (binding None) phải là —")
            assertEquals("—", v.display)
        }
    }

    @Test fun `datum moi noi (trip_km, cell_temp_high) hien gia tri khi CarStatus co field`() {
        // Stage 5: trip_km / cell_temp_high từng "—" cả trên xe (thiếu field). Nay có field → hiện giá trị.
        val s = CarStatus(energy = CarStatus.Energy(tripKm = 12.4, cellTempHighC = 31))
        assertEquals("12.4", TelemetryReadout.of("trip_km", s)!!.display)
        assertTrue(TelemetryReadout.of("trip_km", s)!!.available)
        assertEquals("31", TelemetryReadout.of("cell_temp_high", s)!!.display)
    }

    @Test fun `id la khong co trong registry tra null`() {
        assertNull(TelemetryReadout.of("khong_ton_tai", CarStatus()))
    }

    @Test fun `moi telemetry id deu of duoc (khong crash) va dash khi rong`() {
        // Off-car: mọi datum trả TelemetryView với "—" (available=false), KHÔNG null (id có trong registry).
        val empty = CarStatus()
        TelemetryRegistry.ALL.forEach { spec ->
            val v = TelemetryReadout.of(spec.id, empty)
            assertTrue(v != null, "of(${spec.id}) phải khác null (id trong registry)")
            assertEquals("—", v!!.display, "${spec.id} off-car phải là —")
        }
    }

    @Test fun `FULL WIRE - moi binding resolve duoc thi chay gia tri end-to-end qua adapter`() {
        // Dựng gateway giả trả "1" cho MỌI binding resolve được (feature-id số / named-method / car-setting),
        // chạy adapter thật (fast+slow) rồi đọc lại qua TelemetryReadout. Đây là bằng chứng E2E: registry →
        // HalBindingTable → CarStatus → TelemetryReadout không đứt ở đâu cho datum nối được.
        val getters = mutableMapOf<String, String?>()
        val features = mutableMapOf<Int, String?>()
        val settings = mutableMapOf<String, String?>()
        val names = mutableMapOf<String, Int>()
        TelemetryRegistry.ALL.forEach { spec ->
            when (val r = HalBindingTable.routeOf(spec.bindingKey)) {
                is BindingRoute.NamedMethod -> getters[r.method] = "1"
                is BindingRoute.Feature -> features[r.id] = "1"
                is BindingRoute.Setting -> settings[r.key] = "1"
                is BindingRoute.Local -> {}          // không có telemetry Local
                // V3 · R11 — bind theo TÊN HẰNG: xe giả cấp cho mỗi tên một số **tự đặt** rồi mồi giá trị vào
                // số ấy. Đó chính là điều phải canh: giá trị KHÔNG được viết cứng trong registry nữa, nên
                // đường đọc phải đi qua phép tra tên; ai gỡ phép tra đi thì mấy datum này lại ra "—".
                is BindingRoute.FeatureName -> {
                    val fake = fakeId(r.constName)
                    names[r.constName] = fake
                    features[fake] = "1"
                }
                BindingRoute.None -> {}              // GPS/target_soc → không đường đọc
            }
        }
        // `int[] getChargeRestTime()` trả mảng [giờ, phút] (gateway thật: BydHal.arrayToStr) — mục eta_min lấy [1].
        getters["getChargeRestTime"] = "[1, 1]"
        val adapter = CarDataAdapter(
            HalBindingTable(
                FakeHalGateway(getters = getters, features = features, settings = settings, featureNames = names),
            ),
        )
        val status = adapter.readSlow(adapter.readFast(CarStatus()))

        // Datum còn "—" sau khi nối đủ = ĐÚNG tập binding None (genuine NEEDS_CAR), không hơn.
        val blanks = TelemetryRegistry.ALL
            .filter { !TelemetryReadout.of(it.id, status)!!.available }.map { it.id }.toSet()
        val expectedNone = TelemetryRegistry.ALL
            .filter { HalBindingTable.routeOf(it.bindingKey) == BindingRoute.None }.map { it.id }.toSet()
        assertEquals(expectedNone, blanks, "CHỈ binding None mới được '—' sau khi nối đủ; còn lại phải có giá trị")

        // Và mọi datum resolve được PHẢI available (không còn 'pickable-but-blank' trên xe).
        TelemetryRegistry.ALL.forEach { spec ->
            if (HalBindingTable.routeOf(spec.bindingKey) != BindingRoute.None) {
                assertTrue(
                    TelemetryReadout.of(spec.id, status)!!.available,
                    "${spec.id} (binding ${spec.bindingKey}) phải chảy giá trị end-to-end",
                )
            }
        }
    }

    @Test fun `wired-tier telemetry KHONG con khe trong (moi tier != NEEDS_CAR co case va nap duoc field)`() {
        // Mọi telemetry tier wired (PROVEN/OVERDRIVE/DASHCAST) đều resolve binding + có field CarStatus.
        val getters = mutableMapOf<String, String?>()
        val features = mutableMapOf<Int, String?>()
        val settings = mutableMapOf<String, String?>()
        val names = mutableMapOf<String, Int>()
        TelemetryRegistry.ALL.filter { it.tier.wired }.forEach { spec ->
            when (val r = HalBindingTable.routeOf(spec.bindingKey)) {
                is BindingRoute.NamedMethod -> getters[r.method] = "1"
                is BindingRoute.Feature -> features[r.id] = "1"
                is BindingRoute.Setting -> settings[r.key] = "1"
                // V3 · R11 — xem chú thích cùng ca ở bài FULL WIRE.
                is BindingRoute.FeatureName -> {
                    val fake = fakeId(r.constName)
                    names[r.constName] = fake
                    features[fake] = "1"
                }
                else -> {}
            }
        }
        getters["getChargeRestTime"] = "[1, 1]"   // mảng [giờ, phút] — xem bài FULL WIRE.
        val adapter = CarDataAdapter(
            HalBindingTable(
                FakeHalGateway(getters = getters, features = features, settings = settings, featureNames = names),
            ),
        )
        val status = adapter.readSlow(adapter.readFast(CarStatus()))
        TelemetryRegistry.ALL.filter { it.tier.wired }.forEach { spec ->
            assertTrue(
                TelemetryReadout.of(spec.id, status)!!.available,
                "wired-tier ${spec.id} phải nạp được field từ gateway giả",
            )
        }
    }

    /**
     * Số feature giả cho một tên hằng — **ổn định theo tên**, và cố ý nằm ngoài dải id thật của bảng ở trên.
     *
     * Dùng `hashCode` chứ không phải một bộ đếm: bài test bơm bảng ở một chỗ và đọc ở chỗ khác, nên hai lượt
     * phải ra cùng số. Dải âm (`or Int.MIN_VALUE`) thì không bao giờ đụng id thật nào đang khai trong registry.
     */
    private fun fakeId(constName: String): Int = constName.hashCode() or Int.MIN_VALUE
}
