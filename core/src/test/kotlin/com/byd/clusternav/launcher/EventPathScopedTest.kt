package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 3.1.10 · EVENT-PATH-SCOPED — một sự kiện HAL chỉ làm mới datum của nó, và chỉ bề mặt bày datum ấy vẽ lại ═══════════════════
 *
 * [ĐO xe 16/10] sự kiện bụi mịn `0x4f600010` ~1 Hz suốt buổi. Trước 3.1.10 mỗi sự kiện: `readEvents` chạy CẢ BA nhịp (dựng lại 8/9 cụm
 * của [CarStatus]) ⇒ ảnh mới ⇒ màn chính đổ lại MỌI ô widget + thanh nút. Doc `docs/diagnostics/perf-3110-event-path-2026-10-16.md`.
 *
 * Thử ĐỎ [ĐO 16/10]: bỏ chọn nhịp ở `readEvents` (chạy lại `readBody(readSlow(readFast(prev)))`) ⇒ bài phát lại ĐỎ (8 cụm thay mỗi sự
 * kiện thay vì 1); bỏ dòng `pm25_value` khỏi `CarStatusDelta` ⇒ bài bảng ĐỎ; `Delta.touches` trả `true` khi không giao ⇒ bài bề mặt ĐỎ.
 */
class EventPathScopedTest {

    /** Gateway trả MỌI lượt đọc bằng [v] (vô hướng) hoặc `[v, v]` (mảng) — để thấy một id đổi khi giá trị đổi. */
    private class Answer(private val v: String, private val array: Boolean) : HalGateway by FakeHalGateway() {
        private fun raw() = if (array) "[$v, $v]" else v
        override fun getter(deviceFqn: String, method: String, arg: Int?): String? = raw()
        override fun featureGet(deviceFqn: String, id: Int): String? = "int=$v"
        override fun settingGet(key: String): String? = v
        override fun localGet(target: String, method: String, arg: Int?): String? = v
    }

    private fun readOnly(id: String, v: String, array: Boolean): CarStatus {
        val a = CarDataAdapter(HalBindingTable(Answer(v, array)), { setOf(id) })
        return a.readBody(a.readSlow(a.readFast(CarStatus())))
    }

    /**
     * Bảng `id → field` của [CarStatusDelta] khớp [CarDataAdapter] — ĐO, không tin: cho adapter đọc ĐÚNG MỘT id (cổng nhu cầu = {id}) ở
     * hai giá trị khác nhau; hai ảnh khác nhau thì [CarStatusDelta.of] phải trả ĐÚNG {id} (thiếu dòng ⇒ rỗng · trỏ nhầm field ⇒ id khác).
     */
    @Test fun `bang id-field khop adapter - doc mot id thi delta dung id ay`() {
        val ids = TelemetryRegistry.ALL.map { it.id }.toSet() + CarStatusDelta.FIELDS
        val pairs = listOf("1" to "2", "0" to "1", "3" to "4", "250" to "260")
        val proved = HashSet<String>()
        for (id in ids) for ((x, y) in pairs) for (arr in listOf(false, true)) {
            val a = readOnly(id, x, arr); val b = readOnly(id, y, arr)
            if (a == b) continue
            assertEquals(setOf(id), CarStatusDelta.of(a, b).datums, "đọc riêng '$id' ($x→$y, mảng=$arr) ⇒ delta phải đúng {$id}")
            proved += id
        }
        println("CarStatusDelta: ${proved.size}/${CarStatusDelta.FIELDS.size} id chứng minh qua adapter; chưa đổi được bằng gateway giả: ${CarStatusDelta.FIELDS - proved}")
        assertTrue(proved.size >= 70, "quá ít id chứng minh được (${proved.size}) — gateway giả không còn đọc được bảng?")
        assertTrue(proved.all { it in CarStatusDelta.FIELDS })
    }

    /** Mọi field của mọi cụm có một id: đổi riêng field ấy ⇒ delta khác rỗng (field thêm sau mà quên dòng ⇒ đỏ). */
    @Test fun `moi field CarStatus deu co id trong bang`() {
        val full = CarStatusPassCopyTest.filled()
        val parts = listOf<Pair<String, (CarStatus) -> Any>>("energy" to { it.energy }, "drivetrain" to { it.drivetrain },
            "climate" to { it.climate }, "tyres" to { it.tyres }, "body" to { it.body }, "lights" to { it.lights },
            "identity" to { it.identity }, "infotainment" to { it.infotainment })
        for ((name, part) in parts) {
            val p = part(full)
            val k = p.javaClass.declaredConstructors.filter { !it.isSynthetic }.maxBy { it.parameterCount }
            val comps = p.javaClass.methods.filter { Regex("component\\d+").matches(it.name) }.sortedBy { it.name.removePrefix("component").toInt() }
            val vals = comps.map { it.invoke(p) }
            assertEquals(k.parameterCount, vals.size)
            for (i in vals.indices) {
                // Bản sao chỉ khác field thứ i (đặt về null — mọi field là kiểu nullable).
                val copy = k.newInstance(*vals.mapIndexed { j, v -> if (j == i) null else v }.toTypedArray())
                val f = comps[i].name
                val s = when (name) {
                    "energy" -> full.copy(energy = copy as CarStatus.Energy); "drivetrain" -> full.copy(drivetrain = copy as CarStatus.Drivetrain)
                    "climate" -> full.copy(climate = copy as CarStatus.Climate); "tyres" -> full.copy(tyres = copy as CarStatus.Tyres)
                    "body" -> full.copy(body = copy as CarStatus.Body); "lights" -> full.copy(lights = copy as CarStatus.Lights)
                    "identity" -> full.copy(identity = copy as CarStatus.Identity); else -> full.copy(infotainment = copy as CarStatus.Infotainment)
                }
                assertFalse(CarStatusDelta.of(full, s).isEmpty, "field $name.$f không có id trong CarStatusDelta ⇒ đổi mà không bề mặt nào vẽ")
            }
        }
        assertEquals(setOf("temp"), CarStatusDelta.of(full, full.copy(controls = full.controls + ("temp" to 23))).controls)
    }

    private val names = mapOf("PM2P5_VALUE_IN" to PM_IN, "PM2P5_VALUE_OUT" to PM_OUT,
        "BODYWORK_LEFT_HAND_FRONT_DOOR" to DOOR_LF, "BODYWORK_RIGHT_HAND_FRONT_DOOR" to 692060170,
        "BODYWORK_LEFT_HAND_REAR_DOOR" to 692060172, "BODYWORK_RIGHT_HAND_REAR_DOOR" to 692060174, "BODYWORK_LUGGAGE_DOOR" to 692060186)

    private val qa2 = HomeUiState(
        workspace = WorkspaceState.of(LayoutPreset.ONE, SlotContent.Widget("w_car3d"), SlotContent.Widget("g_tyres"), SlotContent.Widget("g_windows")),
        dock = DockConfig(enabled = listOf("trunk", "readl", "pm25", "seatc", "temp", "fan", "win_lf"), visible = false),
    )

    private class Replay(val emits: Int, val partsReplaced: Int, val surfacesRefreshed: Int)

    /** Phát lại [n] sự kiện bụi mịn (giá trị ĐỔI mỗi lần) trên màn QA2 (xe 3D) + bụi mịn có trong kế hoạch, qua cache + gateway THẬT. */
    private fun replay(n: Int, fid: Int, value: (Int) -> Int): Replay {
        var now = 1_000_000L
        val g = FakeHalGateway(getters = mapOf("getPM2p5Value" to "[10, 20]"),
            gettersByArg = mapOf("getDoorState" to mapOf(1 to "0", 2 to "0", 3 to "0", 4 to "0", 6 to "0")), featureNames = names)
        val holder = CarDataDemand.Holder().apply { publish(qa2) }
        val demand = holder.get()!! + "pm25_value"
        val c = HalEventCache(clock = { now })
        val plan = (demand + holder.controls().mapNotNull { readPathOf(it)?.id }).mapNotNull { HalEventMap.bindingOf(it, g) }.distinctBy { it.datumId }
        c.setPlan(plan); c.onHello(); c.onReg(plan.flatMap { it.ids }.toSet(), emptySet())
        val a = CarDataAdapter(HalBindingTable(EventCachedGateway(g, c)), { demand }, clock = { now }, controlDemand = holder::controls, covered = c::covers)
        var s = a.readBody(a.readSlow(a.readFast(CarStatus())))
        // Bề mặt mà fan-out của màn chính đổ lại theo trạng thái xe (`WorkspaceView` · `KachiHomeRender`): ô xe 3D + thanh nút.
        val surfaces = listOf(CarDataDemand.interestOf(listOf("w_car3d")), CarDataDemand.interestOf(qa2.dock.enabled))
        var emits = 0; var parts = 0; var refreshed = 0; var bytes = 0L
        val mx = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        for (i in 0 until n) {
            now += 400   // < hạn 30 s của id OUT chưa có sự kiện riêng (`HalEventCache.inferredMs`) cho cả lô — không đi đối soát giữa chừng
            val batch = c.onEvent(fid, value(i), 0.0).datums
            val b0 = mx.getThreadAllocatedBytes(Thread.currentThread().id)
            val next = a.readEvents(s, batch)
            bytes += mx.getThreadAllocatedBytes(Thread.currentThread().id) - b0
            if (next != s) {
                emits++
                parts += listOf<(CarStatus) -> Any>({ it.energy }, { it.drivetrain }, { it.climate }, { it.tyres }, { it.body },
                    { it.lights }, { it.identity }, { it.infotainment }, { it.controls }).count { p -> p(next) !== p(s) }
                val d = CarStatusDelta.of(s, next)
                refreshed += surfaces.count { d.touches(it) }
            }
            s = next
        }
        println("phát lại $n sự kiện 0x${Integer.toHexString(fid)}: phát=$emits · cụm thay=$parts · bề mặt đổ lại=$refreshed · cấp phát readEvents ≈${bytes / n} B/sự kiện")
        return Replay(emits, parts, refreshed)
    }

    @Test fun `xe 16-10 bui min 1 Hz tren man 3D - mot cum, khong be mat nao ve lai`() {
        val r = replay(60, PM_IN) { 30 + it }
        assertEquals(60, r.emits, "giá trị đổi mỗi sự kiện ⇒ mỗi lần một ảnh mới (đúng — chip/ô bụi mịn cần nó)")
        assertEquals(60, r.partsReplaced, "chỉ cụm `climate` thay mỗi sự kiện (trước 3.1.10: 8/9 cụm — ba nhịp dựng lại)")
        assertEquals(0, r.surfacesRefreshed, "xe 3D + thanh nút không bày bụi mịn ⇒ 0 lượt đổ (trước: 2 × 60)")
        val same = replay(60, PM_IN) { 30 }
        assertEquals(1, same.emits, "giá trị KHÔNG đổi ⇒ không ảnh mới (sự kiện đầu mang giá trị mới, sau đó cache không báo)")
    }

    @Test fun `su kien cua cua tren man 3D - xe 3D ve lai, chi cum than xe thay`() {
        val r = replay(4, DOOR_LF) { it % 2 }
        assertEquals(r.emits, r.partsReplaced, "mỗi sự kiện cửa chỉ thay cụm `body`")
        assertEquals(r.emits, r.surfacesRefreshed, "xe 3D bày cửa ⇒ đổ lại; thanh nút (cốp, kính…) không bày cửa trước-trái")
        assertTrue(r.emits >= 3)
    }

    @Test fun `be mat - giao thi ve, khong giao thi khong, khong biet thi ve`() {
        val pm = CarStatusDelta.of(CarStatus(), CarStatus(climate = CarStatus.Climate(pm25ValueUgm3 = 33)))
        assertEquals(setOf("pm25_value"), pm.datums)
        assertFalse(pm.touches(CarDataDemand.interestOf(listOf("w_car3d"))), "xe 3D không bày bụi mịn")
        assertTrue(pm.touches(CarDataDemand.interestOf(listOf("w_pm25"))), "ô bụi mịn bày nó")
        assertNull(CarDataDemand.interestOf(listOf("w_media")), "ô nhạc đọc nguồn ngoài xe ⇒ không biết ⇒ luôn đổ như trước")
        assertTrue(pm.touches(null))
        assertFalse(CarStatusDelta.NONE.touches(null), "không gì đổi ⇒ không bề mặt nào vẽ, kể cả bề mặt không biết")
        val fan = CarStatusDelta.of(CarStatus(), CarStatus(controls = mapOf("fan" to 3)))
        assertTrue(fan.touches(CarDataDemand.interestOf(listOf("w_car3d"))), "hàng chân xe 3D mang nút gió (HOSTED)")
        val tyre = CarStatusDelta.of(CarStatus(), CarStatus(tyres = CarStatus.Tyres(pFlKpa = 250.0)))
        assertTrue(tyre.touches(CarDataDemand.interestOf(listOf("w_car3d"))), "xe 3D luôn tính lốp (công tắc lốp không có ở bề mặt vẽ)")
        val ctlOnly = CarStatusDelta.of(CarStatus(), CarStatus(climate = CarStatus.Climate(acWindAutoRaw = 0)))
        assertTrue(ctlOnly.touches(CarDataDemand.interestOf(listOf("ac_wind"))), "bạn đồng hành (COMPANION) vào tập bề mặt")
    }

    /** Nhu cầu không phụ thuộc trạng thái xe ⇒ lượt state chỉ đổi `carStatus` không tính lại, không báo lại (đèn helper · kế hoạch). */
    @Test fun `cong bo nhu cau - doi trang thai xe khong tinh lai`() {
        var told = 0
        val h = CarDataDemand.Holder { told++ }
        h.publish(qa2)
        h.publish(qa2.copy(carStatus = CarStatus(climate = CarStatus.Climate(pm25ValueUgm3 = 1))))
        h.publish(qa2.copy(carStatus = CarStatus(climate = CarStatus.Climate(pm25ValueUgm3 = 2))))
        assertEquals(1, told, "trạng thái xe đổi ⇒ không báo lại nhu cầu")
        h.publish(qa2.copy(dock = qa2.dock.copy(visible = true)))
        assertEquals(2, told, "bố cục đổi ⇒ tính lại")
        assertTrue("win_lf" in h.controls())
        h.publish(qa2.copy(dock = qa2.dock.copy(visible = true)), hidden = setOf("w_car3d"))
        assertEquals(3, told, "widget khuất ⇒ tính lại")
        h.clear(); h.publish(qa2)
        assertEquals(5, told, "màn rời (clear báo một lần) rồi quay lại ⇒ tính lại")
    }

    /** 3.1.10 · HOLD-EXPIRY-REDRAW — hẹn MỘT lượt vẽ lại ở đích SỚM nhất; báo người vẽ mỗi lần đặt đích. */
    @Test fun `dich giu bao nguoi ve va hen het han som nhat`() {
        val h = LampHold(3_000)
        var puts = 0
        h.onPut = { puts++ }
        assertNull(h.nextExpiryIn(0))
        h.put("trunk", true, 0)       // 10 s (confirmMs của dòng)
        h.put("drl", false, 1_000)    // 3 s ⇒ hết lúc 4 000
        assertEquals(2, puts)
        assertEquals(4_000, h.nextExpiryIn(0), "đích sớm nhất (đèn) — không phải dài nhất")
        assertEquals(false, h.held("drl", true, 3_999))
        assertEquals(ControlRegistry.TRUNK_CONFIRM_MS - 4_000, h.nextExpiryIn(4_000), "đèn đã tới hạn ⇒ hẹn kế là cốp")
        assertNull(h.nextExpiryIn(ControlRegistry.TRUNK_CONFIRM_MS), "hết đích ⇒ không hẹn")
    }

    /**
     * Soát Pass 11 [P2] — hộp đứt giữa chuyến: datum hộp vừa đè đã KHÔNG được poll suốt lúc hộp sống (nhu cầu trừ [LampLive.served]) ⇒
     * số đọc trong [CarStatus] có thể cũ hàng giờ (xi-nhan BẬT đóng băng tới 10 s). [LampLive.clear] báo ĐÚNG tập vừa đè một lần; hộp
     * không đè gì (chưa từng nối / đã báo) ⇒ im. Thử ĐỎ: `clear()` chỉ `flow.value = null`.
     */
    @Test fun `hop socket dut thi bao dung tap datum vua de, mot lan`() {
        val live = LampLive()
        val told = ArrayList<Set<String>>()
        live.onServedDropped = { told += it }
        live.clear()
        assertTrue(told.isEmpty(), "chưa đè gì ⇒ không báo")
        live.publish(LampLive.Lamps(left = true, right = false, drl = true))
        val served = live.served()
        assertEquals(setOf("light_left_turn", "light_right_turn", "light_drl"), served)
        live.clear()
        assertEquals(listOf(served), told, "báo đúng tập vừa đè")
        assertNull(live.state.value)
        live.clear()
        assertEquals(1, told.size, "đã lùi về số đọc ⇒ lần đứt kế không báo lại")
    }

    /** 3.1.10 · TURN-POLL-SERVED — [LampLive.served] đúng bằng tập datum [LampLive.overlay] đè (không thừa, không thiếu). */
    @Test fun `LampLive served dung bang tap overlay de`() {
        val cases = listOf(LampLive.Lamps(true, false), LampLive.Lamps(false, true, lowBeam = true, drl = false),
            LampLive.Lamps(false, false, true, true, true, true, true, true))
        assertTrue(LampLive.served(null).isEmpty())
        for (t in cases) {
            // Hai ảnh gốc khác nhau ở MỌI đèn ⇒ sau khi đè, datum còn khác nhau = datum KHÔNG bị đè.
            val a = CarStatus(lights = CarStatus.Lights(true, true, true, true, 1, true, true, true, true))
            val b = CarStatus(lights = CarStatus.Lights(false, false, false, false, 2, false, false, false, false))
            val notServed = CarStatusDelta.of(LampLive.overlay(a, t), LampLive.overlay(b, t)).datums
            assertEquals(CarStatusDelta.of(a, b).datums - notServed, LampLive.served(t), "served($t)")
        }
    }

    private companion object {
        const val PM_IN = 201
        const val PM_OUT = 202
        const val DOOR_LF = 692060168
    }
}
