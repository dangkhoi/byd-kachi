package com.byd.clusternav.launcher.automation

import com.byd.clusternav.launcher.automation.RainGlass.FRONT
import com.byd.clusternav.launcher.automation.RainGlass.REAR
import com.byd.clusternav.launcher.automation.RainStatusTone.FAIL
import com.byd.clusternav.launcher.automation.RainStatusTone.IDLE
import com.byd.clusternav.launcher.automation.RainStatusTone.OK
import com.byd.clusternav.launcher.automation.RainStatusTone.WAIT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ kachi-automation V8.1 · DÒNG TÌNH TRẠNG từng kính (R-V8.7) — mọi ca, chuỗi NGUYÊN VĂN ═══════════════════
 *
 * Kết quả được SINH bằng [RainDefrostGlasses.tick] thật trên một xe giả (không dựng tay `RainGlassOutcome`) ⇒ nếu
 * luật một làn đổi mà dòng tình trạng không đổi theo, bài ở đây đỏ. Bảng chữ [vi] trùng từng chữ với
 * `values/strings_kachi.xml` (`RainDefrostStatusWiringTest` ở `:app` dựng lại bảng từ CHÍNH tệp XML và so cùng ca).
 */
class RainDefrostStatusTest {

    private val vi = RainStatusWords(
        front = "Kính trước", rear = "Kính sau + gương",
        never = "chưa kiểm lần nào từ lúc mở xe", next = "nhịp kế ~{t}",
        rain = "mưa (gạt {n})", dry = "khô (gạt {n})", sensorError = "cảm biến mưa không đọc được ⇒ để nguyên",
        carOn = "xe báo: bật", carOff = "xe báo: tắt", carOffAfterOn = "xe báo: vẫn tắt sau lệnh bật {t}",
        carError = "xe báo: không đọc được",
        turnOnOk = "Kachi bật: tới xe ✓", turnOnFail = "lệnh bật: không tới xe ✗",
        turnOffOk = "Kachi tắt: tới xe ✓", turnOffFail = "lệnh tắt: không tới xe ✗",
        skipped = "bỏ lệnh (vừa đổi lựa chọn)",
        keepOn = "đang sấy, để nguyên", nothingToDo = "không cần sấy", notOurs = "Kachi không quản, để nguyên",
    )

    /** Đồng hồ tường giả: mốc = phút trong ngày ⇒ `"HH:mm"`, không phụ thuộc múi giờ máy chạy test. */
    private val clock: (Long) -> String = { ms -> val m = (ms / 60_000) % 1440; "%02d:%02d".format(m / 60, m % 60) }
    private fun at(h: Int, m: Int) = (h * 60L + m) * 60_000L

    /**
     * Xe giả tối thiểu cho [RainGlassIo]. [obeys] = xe có THẬT SỰ đổi trạng thái khi lệnh tới (rc hợp lệ) không —
     * `false` là dấu hiệu OC4 / OQ-V8.1: *"ghi=ok mà nhịp sau đọc=tắt"*.
     */
    private class Car(var choice: RainDefrostChoice, var rain: Int?) : RainGlassIo {
        val on = mutableMapOf<RainGlass, Boolean?>(FRONT to false, REAR to false)
        val writeOk = mutableMapOf(FRONT to true, REAR to true)
        val obeys = mutableMapOf(FRONT to true, REAR to true)
        var onRead: (RainGlass) -> Unit = {}
        override fun choice() = choice
        override fun readRain() = rain
        override fun readGlass(glass: RainGlass): Boolean? = on[glass].also { onRead(glass) }
        override fun writeGlass(glass: RainGlass, on: Boolean): Boolean =
            writeOk.getValue(glass).also { if (it && obeys.getValue(glass)) this.on[glass] = on }
        override fun log(line: String) = Unit
    }

    private val memory = RainGlassMemory()
    private val results = RainGlassLastResults()
    private var seq = 0L

    private fun tick(car: Car, atWallMs: Long) = results.record(atWallMs, RainDefrostGlasses.tick(++seq, memory, car))

    private fun lines(choice: RainDefrostChoice, next: Long? = null) =
        RainDefrostStatus.lines(choice, results::of, next, vi, clock)

    private val both = RainDefrostChoice(front = true, rear = true)
    private val rearOnly = RainDefrostChoice(front = false, rear = true)
    private val frontOnly = RainDefrostChoice(front = true, rear = false)

    private fun one(choice: RainDefrostChoice): RainStatusLine = lines(choice).single()

    // ══ Chưa có nhịp ════════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `chua kiem lan nao - co va khong co gio nhip ke`() {
        assertEquals(
            listOf(
                RainStatusLine(FRONT, "Kính trước · chưa kiểm lần nào từ lúc mở xe · nhịp kế ~14:07", IDLE),
                RainStatusLine(REAR, "Kính sau + gương · chưa kiểm lần nào từ lúc mở xe · nhịp kế ~14:07", IDLE),
            ),
            lines(both, next = at(14, 7)),
        )
        assertEquals("Kính sau + gương · chưa kiểm lần nào từ lúc mở xe", lines(rearOnly).single().text)
    }

    @Test
    fun `khong chon kinh nao thi khong dong nao`() {
        tick(Car(both, 3), at(14, 2))
        assertTrue(lines(RainDefrostChoice.NONE).isEmpty())
    }

    // ══ Cảm biến mưa / đọc kính ═════════════════════════════════════════════════════════════════════════════

    @Test
    fun `cam bien mua khong doc duoc - null va sentinel`() {
        listOf(null, 65535, -10011).forEach { raw ->
            tick(Car(rearOnly, raw), at(14, 2))
            assertEquals(
                RainStatusLine(REAR, "Kính sau + gương · 14:02 · cảm biến mưa không đọc được ⇒ để nguyên", WAIT),
                one(rearOnly), "raw=$raw",
            )
        }
    }

    @Test
    fun `xe khong bao duoc trang thai kinh`() {
        val car = Car(rearOnly, 3).apply { on[REAR] = null }
        tick(car, at(14, 2))
        assertEquals(RainStatusLine(REAR, "Kính sau + gương · 14:02 · mưa (gạt 3) · xe báo: không đọc được", WAIT), one(rearOnly))
    }

    // ══ Mưa ═════════════════════════════════════════════════════════════════════════════════════════════════

    /** Ca owner hỏi: chỉ chọn sau + gương, mưa, xe tắt ⇒ Kachi bật, lệnh tới xe. */
    @Test
    fun `chi sau, mua, xe tat - Kachi bat, toi xe`() {
        tick(Car(rearOnly, 3), at(14, 2))
        assertEquals(
            RainStatusLine(REAR, "Kính sau + gương · 14:02 · mưa (gạt 3) · xe báo: tắt → Kachi bật: tới xe ✓", OK),
            one(rearOnly),
        )
    }

    @Test
    fun `mua, lenh bat khong toi xe`() {
        val car = Car(rearOnly, 3).apply { writeOk[REAR] = false }
        tick(car, at(14, 2))
        assertEquals(
            RainStatusLine(REAR, "Kính sau + gương · 14:02 · mưa (gạt 3) · xe báo: tắt → lệnh bật: không tới xe ✗", FAIL),
            one(rearOnly),
        )
    }

    @Test
    fun `mua, kinh dang bat - de nguyen`() {
        val car = Car(frontOnly, 4).apply { on[FRONT] = true }
        tick(car, at(9, 5))
        assertEquals(RainStatusLine(FRONT, "Kính trước · 09:05 · mưa (gạt 4) · xe báo: bật → đang sấy, để nguyên", OK), one(frontOnly))
    }

    /** Người dùng bỏ/tích lại ô đó GIỮA nhịp ⇒ lệnh không phát (gác `isCurrent`) ⇒ dòng nói thật "bỏ lệnh". */
    @Test
    fun `lenh bi bo vi vua doi lua chon giua nhip`() {
        val car = Car(rearOnly, 3).apply { onRead = { memory.forget(it) } }
        tick(car, at(14, 2))
        assertEquals(
            RainStatusLine(REAR, "Kính sau + gương · 14:02 · mưa (gạt 3) · xe báo: tắt → bỏ lệnh (vừa đổi lựa chọn)", WAIT),
            one(rearOnly),
        )
    }

    // ══ Lệnh bật tới xe mà xe vẫn tắt (soát V8.1 Pass 8 · D17) ═══════════════════════════════════════════════

    /**
     * Dấu hiệu OC4 / OQ-V8.1 (*"ghi=ok mà nhịp sau đọc=tắt"*) phải lộ trên MỘT ảnh chụp: không có D17 thì mọi nhịp đều
     * là *"xe báo: tắt → Kachi bật: tới xe ✓"* xanh — lần bật đầu và lần thứ mười trông y hệt nhau.
     */
    @Test
    fun `lenh bat toi xe ma nhip sau xe van tat - vang, noi gio lenh truoc`() {
        val car = Car(rearOnly, 3).apply { obeys[REAR] = false }
        tick(car, at(14, 2))
        assertEquals(
            RainStatusLine(REAR, "Kính sau + gương · 14:02 · mưa (gạt 3) · xe báo: tắt → Kachi bật: tới xe ✓", OK),
            one(rearOnly), "lần bật đầu: chưa có gì để nghi",
        )
        tick(car, at(14, 7))
        assertEquals(
            RainStatusLine(REAR, "Kính sau + gương · 14:07 · mưa (gạt 3) · xe báo: vẫn tắt sau lệnh bật 14:02 → Kachi bật: tới xe ✓", WAIT),
            one(rearOnly),
        )
        tick(car, at(14, 12))
        assertEquals(
            "Kính sau + gương · 14:12 · mưa (gạt 3) · xe báo: vẫn tắt sau lệnh bật 14:07 → Kachi bật: tới xe ✓",
            one(rearOnly).text, "giờ lệnh bật NGAY TRƯỚC, nhịp nào cũng tính lại",
        )
        car.writeOk[REAR] = false
        tick(car, at(14, 17))
        assertEquals(
            RainStatusLine(REAR, "Kính sau + gương · 14:17 · mưa (gạt 3) · xe báo: vẫn tắt sau lệnh bật 14:12 → lệnh bật: không tới xe ✗", FAIL),
            one(rearOnly), "đỏ giữ đỏ",
        )
        tick(car, at(14, 22))
        assertEquals(
            "Kính sau + gương · 14:22 · mưa (gạt 3) · xe báo: tắt → lệnh bật: không tới xe ✗",
            one(rearOnly).text, "lệnh trước KHÔNG tới xe ⇒ không có gì để nói về nó",
        )
    }

    /** Kính kia không bị kéo theo: dấu hiệu là của CHÍNH kính đó (D12). */
    @Test
    fun `xe khong lam theo o kinh sau khong lay sang kinh truoc`() {
        val car = Car(both, 3).apply { obeys[REAR] = false }
        tick(car, at(14, 2))
        tick(car, at(14, 7))
        assertEquals(
            listOf(
                RainStatusLine(FRONT, "Kính trước · 14:07 · mưa (gạt 3) · xe báo: bật → đang sấy, để nguyên", OK),
                RainStatusLine(REAR, "Kính sau + gương · 14:07 · mưa (gạt 3) · xe báo: vẫn tắt sau lệnh bật 14:02 → Kachi bật: tới xe ✓", WAIT),
            ),
            lines(both),
        )
    }

    /** Xe tự tắt sau một lúc (~14′, [ĐO log 22/09]): giữa hai lệnh có nhịp "xe báo: bật" ⇒ KHÔNG nghi (xanh như cũ). */
    @Test
    fun `xe tu tat sau mot luc thi bat lai binh thuong, khong nghi`() {
        val car = Car(rearOnly, 3)
        tick(car, at(14, 0))
        tick(car, at(14, 5))                                    // xe báo bật ⇒ Leave
        car.on[REAR] = false                                    // xe tự tắt
        tick(car, at(14, 10))
        assertEquals(
            RainStatusLine(REAR, "Kính sau + gương · 14:10 · mưa (gạt 3) · xe báo: tắt → Kachi bật: tới xe ✓", OK),
            one(rearOnly),
        )
    }

    /** Kachi thôi giữ kính giữa hai nhịp (đổi ô ⇒ quên, D8) ⇒ không nối hai lệnh qua khoảng trống đó. */
    @Test
    fun `quen ky uc giua hai nhip thi khong noi lenh cu`() {
        val car = Car(rearOnly, 3).apply { obeys[REAR] = false }
        tick(car, at(14, 2))
        memory.forget(REAR)                                     // bỏ/tích lại ô (cầu gọi `forget`)
        tick(car, at(14, 7))
        assertEquals(
            RainStatusLine(REAR, "Kính sau + gương · 14:07 · mưa (gạt 3) · xe báo: tắt → Kachi bật: tới xe ✓", OK),
            one(rearOnly),
        )
    }

    /**
     * Hợp đồng của [RainGlassLast.follow] — ba điều kiện, mỗi cái tự đứng. Qua `tick` thật thì *"lệnh trước hỏng"* luôn
     * kéo theo `owned = false` (`unclaim`) nên điều kiện 1 bị điều kiện 3 che; ở đây gọi thẳng để điều kiện 1 vẫn có
     * bài riêng nếu luật nhả chủ quyền đổi sau này.
     */
    @Test
    fun `follow - ba dieu kien, moi cai tu dung`() {
        // Dữ liệu khớp luật V3 lúc mưa (gạt 3): TurnOn ⟺ xe báo TẮT, Leave ⟺ xe báo BẬT (soát V8.1 Pass 9 · P3).
        fun outcome(action: RainDefrostAction, writeOk: Boolean?, owned: Boolean) = RainGlassOutcome(
            RainGlassPlan(REAR, 3, action == RainDefrostAction.Leave, RainDefrostState(owned = owned), RainDefrostStep(action, RainDefrostState(owned = true))),
            writeOk, RainDefrostState(owned = true), committed = true,
        )
        val on = RainDefrostAction.TurnOn
        val prevOk = RainGlassLast(at(14, 2), outcome(on, writeOk = true, owned = false))
        assertEquals(at(14, 2), RainGlassLast.follow(prevOk, at(14, 7), outcome(on, true, owned = true)).offAfterOnAtWallMs)
        listOf(
            RainGlassLast.follow(null, at(14, 7), outcome(on, true, owned = true)) to "không có nhịp trước",
            RainGlassLast.follow(RainGlassLast(at(14, 2), outcome(on, false, false)), at(14, 7), outcome(on, true, true)) to "lệnh trước không tới xe",
            RainGlassLast.follow(RainGlassLast(at(14, 2), outcome(on, null, false)), at(14, 7), outcome(on, true, true)) to "lệnh trước bị bỏ",
            RainGlassLast.follow(RainGlassLast(at(14, 2), outcome(RainDefrostAction.Leave, null, true)), at(14, 7), outcome(on, true, true)) to "nhịp trước không bật",
            RainGlassLast.follow(prevOk, at(14, 7), outcome(RainDefrostAction.Leave, null, owned = true)) to "nhịp này không bật lại",
            RainGlassLast.follow(prevOk, at(14, 7), outcome(on, true, owned = false)) to "Kachi đã thôi giữ kính",
        ).forEach { (last, why) -> assertNull(last.offAfterOnAtWallMs, why) }
    }

    // ══ Khô ═════════════════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `kho, xe tat - khong can say`() {
        tick(Car(frontOnly, 1), at(14, 2))
        assertEquals(RainStatusLine(FRONT, "Kính trước · 14:02 · khô (gạt 1) · xe báo: tắt → không cần sấy", IDLE), one(frontOnly))
    }

    @Test
    fun `kho, kinh cua Kachi - tat, toi xe va khong toi xe`() {
        val car = Car(frontOnly, 3)
        tick(car, at(14, 0))                                    // mưa ⇒ Kachi bật (owned)
        car.rain = 1
        tick(car, at(14, 5))
        assertEquals(RainStatusLine(FRONT, "Kính trước · 14:05 · khô (gạt 1) · xe báo: bật → Kachi tắt: tới xe ✓", OK), one(frontOnly))

        car.rain = 3
        tick(car, at(14, 10))
        car.rain = 1
        car.writeOk[FRONT] = false
        tick(car, at(14, 15))
        assertEquals(
            RainStatusLine(FRONT, "Kính trước · 14:15 · khô (gạt 1) · xe báo: bật → lệnh tắt: không tới xe ✗", FAIL),
            one(frontOnly),
        )
    }

    @Test
    fun `kho, kinh nguoi lai tu bat - khong dung`() {
        val car = Car(rearOnly, 1).apply { on[REAR] = true }
        tick(car, at(14, 2))
        assertEquals(
            RainStatusLine(REAR, "Kính sau + gương · 14:02 · khô (gạt 1) · xe báo: bật → Kachi không quản, để nguyên", IDLE),
            one(rearOnly),
        )
    }

    /**
     * Soát V8.1 Pass 7 · P3 — `owned = false` KHÔNG có nghĩa "người lái bật": Kachi bật, rồi lệnh TẮT không tới xe
     * (hết mưa ⇒ ký ức xoá, `RainDefrostOwner.step`) ⇒ nhịp sau kính vẫn bật mà Kachi không còn giữ. Chữ cũ *"không do
     * Kachi bật"* nói sai nguồn gốc đúng ở ca này (và ở ca bỏ/tích lại ô, D8) ⇒ chữ chỉ được nói điều mã BIẾT: Kachi
     * không quản kính này.
     */
    @Test
    fun `lenh tat khong toi xe thi nhip sau noi Kachi khong quan, khong noi nguoi lai bat`() {
        val car = Car(frontOnly, 3)
        tick(car, at(14, 0))                                    // mưa ⇒ Kachi bật (owned)
        car.rain = 1
        car.writeOk[FRONT] = false
        tick(car, at(14, 5))                                    // khô ⇒ TurnOff hỏng ⇒ kính vẫn bật, ký ức xoá
        tick(car, at(14, 10))
        assertEquals(
            RainStatusLine(FRONT, "Kính trước · 14:10 · khô (gạt 1) · xe báo: bật → Kachi không quản, để nguyên", IDLE),
            one(frontOnly),
        )
    }

    // ══ Mỗi dòng chỉ nhìn kính của CHÍNH nó (D12) ═══════════════════════════════════════════════════════════

    /** Hai kính khác kết quả trong CÙNG một nhịp ⇒ mỗi dòng nói đúng kính mình (phá "đọc nhầm kính" phải đỏ ở đây). */
    @Test
    fun `hai kinh khac ket qua - moi dong dung kinh minh`() {
        val car = Car(both, 3).apply { on[FRONT] = true; writeOk[REAR] = false }
        tick(car, at(14, 2))
        assertEquals(
            listOf(
                RainStatusLine(FRONT, "Kính trước · 14:02 · mưa (gạt 3) · xe báo: bật → đang sấy, để nguyên", OK),
                RainStatusLine(REAR, "Kính sau + gương · 14:02 · mưa (gạt 3) · xe báo: tắt → lệnh bật: không tới xe ✗", FAIL),
            ),
            lines(both),
        )
    }

    /** Kính vừa tích thêm chưa có nhịp ⇒ "chưa kiểm", không mượn kết quả của kính kia. */
    @Test
    fun `kinh vua tich them chua co nhip thi khong muon ket qua kinh kia`() {
        tick(Car(frontOnly, 3), at(14, 2))
        val out = lines(both, next = at(14, 3))
        assertEquals("Kính trước · 14:02 · mưa (gạt 3) · xe báo: tắt → Kachi bật: tới xe ✓", out[0].text)
        assertEquals(RainStatusLine(REAR, "Kính sau + gương · chưa kiểm lần nào từ lúc mở xe · nhịp kế ~14:03", IDLE), out[1])
    }

    /** Chỉ kính ĐANG chọn có dòng — kết quả cũ của kính đã bỏ tích không hiện. */
    @Test
    fun `chi kinh dang chon moi co dong`() {
        tick(Car(both, 3), at(14, 2))
        assertEquals(listOf(REAR), lines(rearOnly).map { it.glass })
    }

    // ══ Ký ức kết quả ═══════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `nhip rong khong xoa ket qua cu, nhip moi de len`() {
        tick(Car(frontOnly, 3), at(14, 2))
        results.record(at(14, 7), emptyList())
        assertEquals(at(14, 2), results.of(FRONT)?.atWallMs, "nhịp rỗng (không chọn/lỗi) không xoá")
        assertNull(results.of(REAR))
        tick(Car(frontOnly, 1).apply { on[FRONT] = true }, at(14, 7))
        assertEquals(at(14, 7), results.of(FRONT)?.atWallMs)
    }

    // ══ Tiếng Anh — cùng cấu trúc, chữ khác ═════════════════════════════════════════════════════════════════

    @Test
    fun `ban tieng Anh cung cau truc`() {
        val en = vi.copy(
            rear = "Rear + mirrors", rain = "rain (wiper {n})", carOff = "car says: off",
            turnOnOk = "Kachi turned it on: reached the car ✓",
        )
        tick(Car(rearOnly, 3), at(14, 2))
        assertEquals(
            "Rear + mirrors · 14:02 · rain (wiper 3) · car says: off → Kachi turned it on: reached the car ✓",
            RainDefrostStatus.lines(rearOnly, results::of, null, en, clock).single().text,
        )
    }
}
