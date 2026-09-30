package com.byd.clusternav.launcher.automation

import com.byd.clusternav.launcher.automation.RainDefrostAction.Leave
import com.byd.clusternav.launcher.automation.RainDefrostAction.TurnOff
import com.byd.clusternav.launcher.automation.RainDefrostAction.TurnOn
import com.byd.clusternav.launcher.automation.RainGlass.FRONT
import com.byd.clusternav.launcher.automation.RainGlass.REAR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ kachi-automation V8 · MƯA → SẤY: HAI KÍNH ĐỘC LẬP — bảng ca C1–C19 (spec §V8.4) ═══════════════════════
 *
 * Chạy THẬT [RainDefrostGlasses.tick] trên một xe giả ([FakeCar]): đọc/ghi từng kính, rc từng kính, ký ức từng
 * kính. Mỗi ca ghi cột *"V7"* trong spec — ca nào V7 làm sai thì bài ở đây đỏ nếu ai khôi phục mỏ neo
 * (`pick.first()`), tắt-cả-khi-khô, hay rc-chỉ-của-kính-đầu.
 *
 * "Kachi bật" = kính do nhịp trước ghi bật thành công (`owned = true`); "người lái bật" = kính đã bật khi Kachi
 * chưa từng ghi nó (`owned = false`).
 */
class RainDefrostGlassesTest {

    private val rain = RainDefrostPolicy.RAIN_ON
    private val dry = RainDefrostPolicy.DRY
    private val both = RainDefrostChoice(front = true, rear = true)
    private val frontOnly = RainDefrostChoice(front = true, rear = false)
    private val rearOnly = RainDefrostChoice(front = false, rear = true)

    /** Xe giả: trạng thái hai kính, cảm biến mưa, rc từng kính; ghi lại mọi lượt đọc/ghi/log. */
    private class FakeCar(
        var choice: RainDefrostChoice,
        var rain: Int?,
        front: Boolean? = false,
        rear: Boolean? = false,
    ) : RainGlassIo {
        val on = mutableMapOf(FRONT to front, REAR to rear)
        val writeOk = mutableMapOf(FRONT to true, REAR to true)
        val reads = mutableListOf<RainGlass>()
        val writes = mutableListOf<Pair<RainGlass, Boolean>>()
        val lines = mutableListOf<String>()
        var rainReads = 0
        var onRead: (RainGlass) -> Unit = {}

        override fun choice() = choice
        override fun readRain(): Int? = rain.also { rainReads++ }
        override fun readGlass(glass: RainGlass): Boolean? {
            reads += glass
            onRead(glass)
            return on[glass]
        }
        override fun writeGlass(glass: RainGlass, on: Boolean): Boolean {
            writes += glass to on
            val ok = writeOk.getValue(glass)
            if (ok) this.on[glass] = on
            return ok
        }
        override fun log(line: String) {
            lines += line
        }

        fun clear() {
            reads.clear(); writes.clear(); lines.clear(); rainReads = 0
        }
    }

    private var seq = 0L
    private val memory = RainGlassMemory()

    private fun tick(car: FakeCar): Map<RainGlass, RainGlassOutcome> {
        car.clear()
        return RainDefrostGlasses.tick(++seq, memory, car).associateBy { it.plan.glass }
    }

    private fun Map<RainGlass, RainGlassOutcome>.action(g: RainGlass) = getValue(g).plan.action

    // ══ Chọn MỘT kính ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `C1 chi truoc, mua - bat truoc, khong cham kinh sau`() {
        val car = FakeCar(frontOnly, rain)
        val out = tick(car)
        assertEquals(setOf(FRONT), out.keys)
        assertEquals(TurnOn, out.action(FRONT))
        assertEquals(listOf(FRONT), car.reads, "kính không tích: không một lượt đọc")
        assertEquals(listOf(FRONT to true), car.writes, "kính không tích: không một lượt ghi")
    }

    /** Ca owner hỏi: bỏ kính trước, chỉ chọn sau + gương ⇒ PHẢI chạy. */
    @Test
    fun `C2 chi sau, mua - bat sau, khong doc khong ghi kinh truoc`() {
        val car = FakeCar(rearOnly, rain)
        val out = tick(car)
        assertEquals(setOf(REAR), out.keys)
        assertEquals(TurnOn, out.action(REAR))
        assertEquals(listOf(REAR), car.reads)
        assertEquals(listOf(REAR to true), car.writes)
        assertTrue(out.getValue(REAR).after.owned)
    }

    @Test
    fun `C3 chi sau, nguoi lai da bat truoc - bat sau, kinh truoc khong ai dung`() {
        val car = FakeCar(rearOnly, rain, front = true, rear = false)
        val out = tick(car)
        assertEquals(TurnOn, out.action(REAR))
        assertFalse(FRONT in car.reads || car.writes.any { it.first == FRONT })
    }

    // ══ Chọn CẢ HAI ═════════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `C4 ca hai, mua, ca hai tat - moi kinh mot lenh, rc rieng, chu quyen rieng`() {
        val car = FakeCar(both, rain)
        val out = tick(car)
        assertEquals(TurnOn, out.action(FRONT))
        assertEquals(TurnOn, out.action(REAR))
        assertEquals(listOf(FRONT to true, REAR to true), car.writes)
        assertEquals(1, car.rainReads, "cảm biến mưa là đầu vào CHUNG — đọc đúng một lần mỗi nhịp")
        assertEquals(listOf(FRONT, REAR), car.reads, "mỗi kính đọc đúng một lần, chính nó")
        assertTrue(memory.snapshot().stateOf(FRONT).owned && memory.snapshot().stateOf(REAR).owned)
    }

    /**
     * ⚠⚠ K1 — lỗi thật của V7. [ĐO log 22/09] BYD tự tắt sấy sau ~14′ giữa mưa. V7: mỏ neo = kính trước (đang bật)
     * ⇒ Leave ⇒ kính sau tắt tới hết mưa. V8: kính sau tự đọc chính nó ⇒ bật lại.
     */
    @Test
    fun `C5 ca hai, mua, truoc Kachi bat, sau bi BYD tu tat - bat lai RIENG kinh sau`() {
        val car = FakeCar(both, rain)
        tick(car)
        car.on[REAR] = false                       // BYD tự tắt kính sau (~14′)
        val out = tick(car)
        assertEquals(Leave, out.action(FRONT), "kính trước vẫn bật — không đụng")
        assertEquals(TurnOn, out.action(REAR), "kính sau bị xe tắt ⇒ bật lại, bất kể kính trước")
        assertEquals(listOf(REAR to true), car.writes, "kính trước không bị ghi lại")
        assertEquals(true, car.on[REAR])
    }

    @Test
    fun `C6 ca hai, mua, sau do nguoi lai bat - bat truoc, sau khong nhan chu quyen`() {
        val car = FakeCar(both, rain, front = false, rear = true)
        val out = tick(car)
        assertEquals(TurnOn, out.action(FRONT))
        assertEquals(Leave, out.action(REAR))
        assertFalse(out.getValue(REAR).after.owned, "kính người lái bật ⇒ không phải của Kachi")
        assertEquals(listOf(FRONT to true), car.writes)
    }

    /** K2 — V7 chỉ kiểm rc của mỏ neo; kính sau ghi hỏng không ai biết. */
    @Test
    fun `C7 ca hai, ghi sau hong - chi sau mat chu quyen, nhip sau thu lai, log ghi=hong`() {
        val car = FakeCar(both, rain)
        car.writeOk[REAR] = false
        val out = tick(car)
        assertTrue(out.getValue(FRONT).after.owned, "rc trước ok ⇒ giữ chủ quyền trước")
        assertFalse(out.getValue(REAR).after.owned, "rc sau hỏng ⇒ CHỈ sau mất chủ quyền")
        assertEquals(false, out.getValue(REAR).writeOk)
        assertTrue(car.lines.any { " SAU " in it && "ghi=hỏng" in it }, "hỏng phải hiện trong log: ${car.lines}")

        car.writeOk[REAR] = true
        val retry = tick(car)
        assertEquals(Leave, retry.action(FRONT))
        assertEquals(TurnOn, retry.action(REAR), "nhịp sau đọc kính sau tắt ⇒ thử lại")
        assertEquals(listOf(REAR to true), car.writes)
    }

    /** K3 — V7 `TurnOff` ghi mọi kính đã chọn ⇒ tắt luôn kính sau người lái tự bật. */
    @Test
    fun `C8 ca hai, het mua, truoc Kachi bat, sau nguoi lai bat - chi tat kinh truoc`() {
        val car = FakeCar(both, rain, front = false, rear = true)
        tick(car)                                   // trước: Kachi bật · sau: người lái đã bật
        car.rain = dry
        val out = tick(car)
        assertEquals(TurnOff, out.action(FRONT))
        assertEquals(Leave, out.action(REAR), "kính người lái tự bật — Kachi không đụng")
        assertEquals(listOf(FRONT to false), car.writes)
        assertEquals(true, car.on[REAR])
    }

    @Test
    fun `C9 ca hai, het mua, truoc nguoi lai bat, sau Kachi bat - chi tat kinh sau`() {
        val car = FakeCar(both, rain, front = true, rear = false)
        tick(car)
        car.rain = dry
        val out = tick(car)
        assertEquals(Leave, out.action(FRONT))
        assertEquals(TurnOff, out.action(REAR))
        assertEquals(listOf(REAR to false), car.writes)
        assertEquals(true, car.on[FRONT])
    }

    /** Luật V3 giữ nguyên, áp riêng từng kính: tắt tay giữa mưa ⇒ bật lại đúng kính đó. */
    @Test
    fun `C10 ca hai, mua, nguoi lai tat truoc giua mua - bat lai truoc, sau khong bi ghi`() {
        val car = FakeCar(both, rain)
        tick(car)
        car.on[FRONT] = false
        val out = tick(car)
        assertEquals(TurnOn, out.action(FRONT))
        assertEquals(Leave, out.action(REAR))
        assertEquals(listOf(FRONT to true), car.writes)
    }

    @Test
    fun `C11 mua khong doc duoc - khong doc kinh nao, khong ghi, ky uc giu nguyen`() {
        val car = FakeCar(both, rain)
        tick(car)
        for (bad in listOf(null, 65535, -10011, 0)) {
            car.rain = bad
            val out = tick(car)
            assertEquals(Leave, out.action(FRONT), "raw=$bad")
            assertEquals(Leave, out.action(REAR), "raw=$bad")
            assertTrue(car.reads.isEmpty() && car.writes.isEmpty(), "raw=$bad: không đọc/ghi kính nào")
            assertTrue(memory.snapshot().stateOf(FRONT).owned && memory.snapshot().stateOf(REAR).owned, "raw=$bad: đọc lỗi ≠ trời khô")
        }
    }

    @Test
    fun `C12 doc truoc loi - truoc giu nguyen, sau van chay`() {
        val car = FakeCar(both, rain, front = null, rear = false)
        val out = tick(car)
        assertEquals(Leave, out.action(FRONT))
        assertEquals(RainDefrostState(), out.getValue(FRONT).after)
        assertEquals(TurnOn, out.action(REAR), "V7 bỏ cả nhịp khi mỏ neo null — V8 kính kia vẫn chạy")
        assertTrue(car.lines.any { " TRƯỚC đọc=lỗi" in it })
    }

    /** K6 — `unclaim` V7 giữ `owned = true` cho một kính đang TẮT khi ghi-bật-lại hỏng. */
    @Test
    fun `C13 chi sau, ghi bat lai hong roi nguoi lai tu bat - het mua khong tat kinh nguoi lai`() {
        val car = FakeCar(rearOnly, rain)
        tick(car)                                   // Kachi bật sau
        car.on[REAR] = false                        // BYD tự tắt
        car.writeOk[REAR] = false
        val fail = tick(car)
        assertEquals(TurnOn, fail.action(REAR))
        assertFalse(fail.getValue(REAR).after.owned, "ghi hỏng ⇒ không còn là kính của Kachi (D5)")
        car.writeOk[REAR] = true
        car.on[REAR] = true                         // người lái tự bật
        tick(car)
        car.rain = dry
        val out = tick(car)
        assertEquals(Leave, out.action(REAR), "kính người lái bật — hết mưa không được tắt")
        assertTrue(car.writes.isEmpty())
    }

    @Test
    fun `C14 khong chon gi - khong doc ca cam bien, khong ghi, khong log, ky uc sach`() {
        val car = FakeCar(both, rain)
        tick(car)
        car.choice = RainDefrostChoice.NONE
        val out = tick(car)
        assertTrue(out.isEmpty())
        assertEquals(0, car.rainReads)
        assertTrue(car.reads.isEmpty() && car.writes.isEmpty() && car.lines.isEmpty())
        assertEquals(RainDefrostState(), memory.snapshot().stateOf(FRONT))
        assertEquals(RainDefrostState(), memory.snapshot().stateOf(REAR))
    }

    /** C16 (phần ký ức) — bỏ tích một kính: quên RIÊNG nó, không ghi nó; ký ức kính kia còn nguyên. */
    @Test
    fun `C16 bo tich sau luc Kachi dang giu ca hai - sau quen khong ghi, truoc con nguyen`() {
        val car = FakeCar(both, rain)
        tick(car)
        car.choice = frontOnly
        memory.forget(REAR)                         // đường cầu: đổi ô ⇒ forget(glass)
        val out = tick(car)
        assertEquals(setOf(FRONT), out.keys)
        assertEquals(Leave, out.action(FRONT))
        assertTrue(memory.snapshot().stateOf(FRONT).owned, "ký ức kính KHÔNG đổi phải còn nguyên")
        assertEquals(RainDefrostState(), memory.snapshot().stateOf(REAR))
        assertTrue(car.writes.isEmpty(), "D8: bỏ tích không phát lệnh HAL ngầm")
        car.rain = dry
        val off = tick(car)
        assertEquals(TurnOff, off.action(FRONT), "kính trước vẫn được tắt hộ khi hết mưa")
        assertEquals(listOf(FRONT to false), car.writes)
    }

    /** Kính KHÔNG chọn tự bị quên mỗi nhịp — chịu được lượt đổi prefs không qua cầu (hạ/nâng cấp, sửa tay tệp). */
    @Test
    fun `kinh khong con duoc chon thi nhip tu quen ky uc`() {
        val car = FakeCar(both, rain)
        tick(car)
        car.choice = frontOnly                      // đổi prefs KHÔNG qua cầu ⇒ không ai gọi forget
        tick(car)
        assertEquals(RainDefrostState(), memory.snapshot().stateOf(REAR))
    }

    // ══ Đổi lựa chọn GIỮA nhịp — thế hệ thắng ═══════════════════════════════════════════════════════════════

    @Test
    fun `forget giua nhip thi bo luot ghi va bo ky uc cua nhip do`() {
        val car = FakeCar(rearOnly, rain)
        car.onRead = { g -> memory.forget(g) }      // người dùng chạm ô đúng lúc nhịp đang đọc HAL
        val out = tick(car)
        val o = out.getValue(REAR)
        assertEquals(TurnOn, o.plan.action)
        assertEquals(null, o.writeOk, "lựa chọn vừa đổi ⇒ không ghi theo lựa chọn cũ")
        assertFalse(o.committed)
        assertTrue(car.writes.isEmpty())
        assertEquals(RainDefrostState(), memory.snapshot().stateOf(REAR))
        assertTrue(car.lines.any { "bỏ ký ức nhịp này" in it })
    }

    @Test
    fun `commit chi ghi khi the he chua doi`() {
        val m = RainGlassMemory()
        val snap = m.snapshot()
        assertTrue(m.commit(FRONT, snap.epochOf(FRONT), RainDefrostState(owned = true)))
        m.forget(REAR)
        assertFalse(m.commit(REAR, snap.epochOf(REAR), RainDefrostState(owned = true)), "REAR đã bị quên")
        assertTrue(m.snapshot().stateOf(FRONT).owned, "forget REAR không chạm FRONT")
        m.forgetAll()
        assertFalse(m.commit(FRONT, snap.epochOf(FRONT), RainDefrostState(owned = true)))
        assertEquals(RainDefrostState(), m.snapshot().stateOf(FRONT))
    }

    // ══ Prefs cũ ⇄ lựa chọn (D2) ═══════════════════════════════════════════════════════════════════════════

    /** C17 — cài mới / V7 tắt `(false, true, true)`: hiện tắt cả hai; tích sau ⇒ kính trước KHÔNG sống lại. */
    @Test
    fun `C17 prefs cu cong tac chinh tat - tich sau khong lam song lai kinh truoc`() {
        val c = RainDefrostChoice.fromKeys(enabled = false, front = true, rear = true)
        assertEquals(RainDefrostChoice.NONE, c)
        assertEquals(RainDefrostKeys(enabled = true, front = false, rear = true), c.with(REAR, true).toKeys())
    }

    /** C18 — lựa chọn V7 (chỉ sau) giữ nguyên qua V8. */
    @Test
    fun `C18 prefs V7 chi sau thi V8 hien dung chi sau`() {
        assertEquals(rearOnly, RainDefrostChoice.fromKeys(enabled = true, front = false, rear = true))
        assertEquals(both, RainDefrostChoice.fromKeys(enabled = true, front = true, rear = true))
        assertEquals(RainDefrostChoice.NONE, RainDefrostChoice.fromKeys(enabled = true, front = false, rear = false))
    }

    @Test
    fun `bang tuong thich prefs - moi thao tac ghi dung ba khoa`() {
        assertEquals(RainDefrostKeys(true, false, true), both.with(FRONT, false).toKeys())
        assertEquals(RainDefrostKeys(false, false, false), frontOnly.with(FRONT, false).toKeys(), "hết kính ⇒ enabled=false")
        assertEquals(RainDefrostKeys(true, true, true), rearOnly.with(FRONT, true).toKeys())
        assertEquals(RainDefrostKeys(true, true, false), RainDefrostChoice.NONE.with(FRONT, true).toKeys())
        // Vòng tròn: ghi rồi đọc lại ra đúng lựa chọn, cho cả 4 trạng thái.
        listOf(RainDefrostChoice.NONE, frontOnly, rearOnly, both).forEach { c ->
            val k = c.toKeys()
            assertEquals(c, RainDefrostChoice.fromKeys(k.enabled, k.front, k.rear))
        }
    }

    /**
     * D2 · soát V8 Pass 3 — ĐỦ 8 tổ hợp 3 khoá cũ trên đĩa (V7 / 1.85 / sửa tay). Với mỗi tổ hợp: (1) V8 hiện đúng
     * lựa chọn V7 đang chạy thật (`enabled && con`, bảng V7 `selection()` khi công tắc chính bật, rỗng khi tắt);
     * (2) mọi cú chạm MỘT ô ghi xuống ba khoá mà đọc lại ra đúng ô vừa chạm + ô kia giữ nguyên HIỆU LỰC cũ — tức
     * không tổ hợp cũ nào làm một kính người dùng không chọn "sống lại".
     */
    @Test
    fun `du 8 to hop khoa cu - hien dung V7, cham mot o khong song lai o kia`() {
        val bools = listOf(false, true)
        for (e in bools) for (f in bools) for (r in bools) {
            val shown = RainDefrostChoice.fromKeys(enabled = e, front = f, rear = r)
            assertEquals(RainDefrostChoice(front = e && f, rear = e && r), shown, "($e,$f,$r)")
            for (g in RainGlass.entries) for (on in bools) {
                val k = shown.with(g, on).toKeys()
                val back = RainDefrostChoice.fromKeys(k.enabled, k.front, k.rear)
                val other = RainGlass.entries.single { it != g }
                assertEquals(on, back.has(g), "($e,$f,$r) chạm $g=$on")
                assertEquals(shown.has(other), back.has(other), "($e,$f,$r) chạm $g=$on: $other sống lại/mất")
                assertEquals(back.any, k.enabled, "($e,$f,$r) chạm $g=$on: enabled phải = còn kính nào")
            }
        }
    }

    /**
     * D2 · soát V8 Pass 5 — nhịp nền ĐỌC LẪN cũ/mới giữa một lượt ghi không sinh kính "ma". `apply()` thay bộ nhớ
     * nguyên khối ([ĐO nguồn AOSP r47] `commitToMemory`) nhưng `Prefs.rainDefrostChoice` đọc bằng BA `getBoolean` ⇒
     * bản đọc lẫn là *"k khoá đầu cũ, phần sau mới"* theo thứ tự đọc. Đọc `enabled` TRƯỚC (khoá ở
     * `RainDefrostV8WiringTest`) ⇒ mỗi kính luôn ra giá trị cũ hoặc mới của chính nó. Đọc con trước thì không: cài mới
     * `(false, true, true)` + tích "sau" ⇒ `front` cũ `true` + `enabled` mới `true` ⇒ Kachi bật kính trước không ai chọn.
     */
    @Test
    fun `doc lan cu moi, enabled doc truoc - khong kinh ma`() {
        val bools = listOf(false, true)
        for (e in bools) for (f in bools) for (r in bools) for (g in RainGlass.entries) for (on in bools) {
            val old = RainDefrostKeys(e, f, r)
            val before = RainDefrostChoice.fromKeys(e, f, r)
            val new = before.with(g, on).toKeys()
            val after = RainDefrostChoice.fromKeys(new.enabled, new.front, new.rear)
            listOf(old, old.copy(rear = new.rear), new.copy(enabled = old.enabled), new).forEach { m ->
                val seen = RainDefrostChoice.fromKeys(m.enabled, m.front, m.rear)
                RainGlass.entries.forEach { x ->
                    assertTrue(seen.has(x) == before.has(x) || seen.has(x) == after.has(x), "$old chạm $g=$on, đọc $m: $x ma")
                }
            }
        }
    }

    @Test
    fun `hai o doc lap - doi o nay khong doi o kia`() {
        RainGlass.entries.forEach { g ->
            listOf(RainDefrostChoice.NONE, frontOnly, rearOnly, both).forEach { c ->
                val other = RainGlass.entries.single { it != g }
                assertEquals(c.has(other), c.with(g, true).has(other))
                assertEquals(c.has(other), c.with(g, false).has(other))
                assertTrue(c.with(g, true).has(g))
                assertFalse(c.with(g, false).has(g))
            }
        }
    }

    // ══ Log (R-V8.6) ═══════════════════════════════════════════════════════════════════════════════════════

    /** C19 — hai nhịp cùng dữ liệu cách nhau < 10 s phải ra hai dòng KHÁC nhau (khoá `LogLineThrottle`). */
    @Test
    fun `C19 moi dong mang seq nen hai nhip giong nhau khong trung dong`() {
        val car = FakeCar(both, rain, front = true, rear = true)
        tick(car)
        val first = car.lines.toList()
        tick(car)
        val second = car.lines.toList()
        assertEquals(3, first.size, "1 dòng đầu + 1 dòng/kính: $first")
        first.zip(second).forEach { (a, b) -> assertNotEquals(a, b) }
        (first + second).forEach { assertTrue(Regex("^#\\d+ ").containsMatchIn(it), "thiếu #seq: $it") }
    }

    @Test
    fun `dinh dang dong log du de doc lai mot nhip`() {
        val car = FakeCar(both, 3, front = true, rear = false)
        seq = 40
        tick(car)
        assertEquals(
            listOf(
                "#41 mưa raw=3 → 3 (mưa) · chọn trước=có sau=có",
                "#41 TRƯỚC đọc=bật owned=false ⇒ Leave · ghi=— ⇒ owned=false",
                "#41 SAU đọc=tắt owned=false ⇒ TurnOn · ghi=ok ⇒ owned=true",
            ),
            car.lines,
        )
        car.rain = 65535
        tick(car)
        assertEquals("#42 mưa raw=65535 → không đọc được ⇒ giữ nguyên mọi kính · chọn trước=có sau=có", car.lines[0])
        assertTrue(car.lines[1].startsWith("#42 TRƯỚC đọc=— "))
        assertEquals(
            "đổi lựa chọn #c3: trước=có sau=không ⇒ ghi enabled=true front=true rear=false · quên SAU · nhịp mưa ≤60 s",
            RainDefrostGlasses.choiceLine(3, frontOnly, REAR),
        )
    }

    // ══ plan / settle — hai mảnh thuần ═════════════════════════════════════════════════════════════════════

    @Test
    fun `plan loc lai sentinel du cho goi quen loc`() {
        val p = RainDefrostGlasses.plan(FRONT, RainDefrostState(), rainSpeed = 65535, read = false)
        assertEquals(Leave, p.action, "65535 > ngưỡng mưa — lọt qua là bật sấy giữa trời nắng")
        assertEquals(null, p.rainSpeed)
    }

    @Test
    fun `settle - bat hong thi nha, tat hong van sach, Leave giu`() {
        val owned = RainDefrostState(owned = true)
        val on = RainDefrostGlasses.plan(REAR, owned, rain, read = false)
        assertEquals(TurnOn, on.action)
        assertFalse(RainDefrostGlasses.settle(on, writeOk = false).owned)
        assertFalse(RainDefrostGlasses.settle(on, writeOk = null).owned, "không ghi được = không phải của mình")
        assertTrue(RainDefrostGlasses.settle(on, writeOk = true).owned)
        val off = RainDefrostGlasses.plan(REAR, owned, dry, read = true)
        assertEquals(TurnOff, off.action)
        assertEquals(RainDefrostState(), RainDefrostGlasses.settle(off, writeOk = false))
        val hold = RainDefrostGlasses.plan(REAR, owned, rain, read = true)
        assertEquals(owned, RainDefrostGlasses.settle(hold, writeOk = null))
    }
}
