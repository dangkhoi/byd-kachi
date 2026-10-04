package com.byd.clusternav.voicekey

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * ═══ 2.88 · KEY-SOURCE-SPLIT tầng 2 — khoá luật KHỚP THEO NGUỒN của [VoiceKeyMatcher] (spec kachi-288 R3, T2) ═══════════
 *
 * Ba thứ được khoá ở đây (CLAUDE.md §6/§10):
 *  1. **Đường 2.87 không đổi**: mã không có dòng gán theo nguồn ⇒ quyết định TRÙNG KHÍT thuật toán 2.87 (chép nguyên vào
 *     [Legacy287]) trên chuỗi sự kiện ngẫu nhiên, và hàm tra nguồn KHÔNG BAO GIỜ được gọi (gọi ⇒ bài đỏ) — không HAL.
 *  2. **Một lượt tra mỗi lần nhấn**: chỉ DOWN đầu tra; DOWN lặp / UP / OTHER dùng lại quyết định (DOWN nuốt ⇒ UP nuốt,
 *     DOWN để đi ⇒ UP để đi — không UP mồ côi sang hệ thống).
 *  3. **Không biết nguồn ⇒ hành xử như 2.87**: chỉ dòng không nguồn, không thì để phím đi tiếp.
 * Fixture số đo dựng từ bốn ảnh hộp Học phím trên xe owner 04/10 (`docs/diagnostics/key-source-oncar-2026-10-04.md`).
 */
class VoiceKeyMatcherSourceTest {

    private val KNOB = KeySourceKind.CONSOLE_KNOB
    private val WHEEL = KeySourceKind.STEERING_WHEEL
    private val FAN_UP = "ctl:fan_up"
    private val FAN_DOWN = "ctl:fan_down"
    private val KIKI = "ai.zalo.kiki.car"

    /** Hàm tra nguồn KHÔNG được phép gọi — gọi là đường 2.87 vừa bị đổi (đọc HAL cho phím không cần nguồn). */
    private val mustNotLookup: () -> KeySourceLookup = { throw AssertionError("không được tra nguồn cho mã không có dòng gán theo nguồn") }

    /** Hàm tra nguồn giả trả [kind] (hoặc lý do hụt) và ĐẾM số lần được gọi. */
    private class Probe(var kind: KeySourceKind?, var why: String = "timeout") {
        var calls = 0
        val fn: () -> KeySourceLookup = {
            calls++
            kind?.let { KeySourceLookup(it, it.code) } ?: KeySourceLookup(null, "?($why)")
        }
    }

    // ── Fixture số đo xe 04/10 ───────────────────────────────────────────────────────────────────────────────────────

    /**
     * Bốn ảnh owner chụp hộp Học phím (Kachi 2.87, `docs/diagnostics/key-source-oncar-2026-10-04.md` §1): núm xoay
     * giảm/tăng ra AUDIO_VOLUME_CTRL_MODE=1, vô-lăng giảm/tăng ra =2, đọc 1–3 ms, cùng mã 292/291 + scancode 114/115.
     * Khoá: đúng các số ĐO được đó dịch ra đúng nút qua CÙNG [KeySourceProbes.verdict] mà hộp hiện + matcher dùng.
     */
    @Test
    fun `fixture so do xe 04-10 - 291 292 gia tri 1 la num, 2 la vo-lang`() {
        val spec = KeySourceProbes.AUDIO_VOLUME_CTRL_MODE
        data class Photo(val code: Int, val scan: Int, val value: Int, val readMs: Long, val want: KeySourceKind)
        val photos = listOf(
            Photo(292, 114, 1, 1, KNOB),
            Photo(291, 115, 1, 2, KNOB),
            Photo(292, 114, 2, 1, WHEEL),
            Photo(291, 115, 2, 3, WHEEL),
        )
        photos.forEach { p ->
            assertEquals(spec, KeySourceProbes.forKey(p.code), "mã ${p.code} phải thuộc đầu dò AUDIO_VOLUME_CTRL_MODE")
            val reading = KeySourceReading(spec, value = p.value, readMs = p.readMs, ageMs = p.readMs)
            assertEquals(KeySourceVerdict.Source(p.want, p.value), KeySourceProbes.verdict(reading), "ảnh mã ${p.code} = ${p.value}")
            val lookup = KeySourceLookup.of(reading)
            assertEquals(p.want, lookup.kind)
            assertEquals(p.want.code, lookup.reason, "dòng log voice-key ghi src=${p.want.code}")
            assertTrue(p.readMs < KeySourceResolver.SYNC_BUDGET_MS, "số đo ${p.readMs} ms ≪ trần đồng bộ")
        }
    }

    /**
     * Ca owner yêu cầu (03/10): gán núm cho quạt, vô-lăng KHÔNG gán ⇒ xoay núm: quạt đổi + phím bị nuốt (âm lượng không
     * đổi); bấm vô-lăng: phím đi tiếp (âm lượng chạy như xe gốc). UP phải cùng phía với DOWN của chính lần nhấn đó.
     */
    @Test
    fun `num gan quat, vo-lang khong gan - num ban va nuot, vo-lang di tiep, UP theo DOWN`() {
        val cfg = VoiceKeyConfig(true, listOf(VoiceKeyBinding(291, FAN_UP, KNOB), VoiceKeyBinding(292, FAN_DOWN, KNOB)))
        val m = VoiceKeyMatcher()
        val probe = Probe(KNOB)

        val knobDown = m.onKey(cfg, VoiceKeyAction.DOWN, 291, 100, probe.fn)
        assertTrue(knobDown.fire && knobDown.consume)
        assertEquals(FAN_UP, knobDown.targetSpec)
        assertEquals(KNOB, knobDown.source)
        assertEquals("knob", knobDown.reason)
        val knobUp = m.onKey(cfg, VoiceKeyAction.UP, 291, 100, probe.fn)
        assertFalse(knobUp.fire); assertTrue(knobUp.consume, "DOWN đã nuốt ⇒ UP cũng nuốt (không UP mồ côi tới hệ thống)")

        probe.kind = WHEEL
        val wheelDown = m.onKey(cfg, VoiceKeyAction.DOWN, 291, 200, probe.fn)
        assertFalse(wheelDown.fire); assertFalse(wheelDown.consume, "vô-lăng chưa gán ⇒ âm lượng chạy như xe gốc")
        assertNull(wheelDown.targetSpec)
        assertEquals("wheel", wheelDown.reason, "lần nhấn đi tiếp vẫn mang lý do để ghi dòng voice-key pass")
        val wheelUp = m.onKey(cfg, VoiceKeyAction.UP, 291, 200, probe.fn)
        assertFalse(wheelUp.consume, "DOWN đã để đi ⇒ UP cũng để đi")

        probe.kind = KNOB
        assertEquals(FAN_DOWN, m.onKey(cfg, VoiceKeyAction.DOWN, 292, 300, probe.fn).targetSpec)
        assertTrue(m.onKey(cfg, VoiceKeyAction.UP, 292, 300, probe.fn).consume)
        assertEquals(3, probe.calls, "một lượt tra cho MỖI lần nhấn (DOWN đầu), UP không tra lại")
    }

    /** DOWN lặp (giữ nút) và OTHER dùng lại quyết định của DOWN đầu — không tra lại dù HAL đổi giá trị giữa chừng. */
    @Test
    fun `DOWN lap va OTHER dung lai quyet dinh, khong tra lai`() {
        val cfg = VoiceKeyConfig(true, listOf(VoiceKeyBinding(291, FAN_UP, KNOB)))
        val m = VoiceKeyMatcher()
        val probe = Probe(KNOB)
        assertTrue(m.onKey(cfg, VoiceKeyAction.DOWN, 291, 100, probe.fn).fire)
        probe.kind = WHEEL   // nút kia vừa bấm: HAL đã đổi, nhưng lần nhấn NÀY đã quyết xong
        repeat(3) {
            val rep = m.onKey(cfg, VoiceKeyAction.DOWN, 291, 100, probe.fn)
            assertFalse(rep.fire); assertTrue(rep.consume); assertNull(rep.targetSpec)
            assertEquals(KNOB, rep.source)
        }
        assertTrue(m.onKey(cfg, VoiceKeyAction.OTHER, 291, 100, probe.fn).consume)
        assertTrue(m.onKey(cfg, VoiceKeyAction.UP, 291, 100, probe.fn).consume)
        assertEquals(1, probe.calls)
    }

    /**
     * Đọc hụt (quá trần / bận / giá trị lạ) ⇒ KHÔNG BIẾT nguồn ⇒ chỉ dòng không nguồn (y như 2.87); không có thì để đi.
     * Lý do hụt nằm trong quyết định để dòng log ghi `src=?(timeout)`.
     */
    @Test
    fun `khong biet nguon thi chi dung dong khong nguon, khong co thi di tiep`() {
        val knobOnly = VoiceKeyConfig(true, listOf(VoiceKeyBinding(291, FAN_UP, KNOB)))
        val m = VoiceKeyMatcher()
        val probe = Probe(null, "timeout")
        val d = m.onKey(knobOnly, VoiceKeyAction.DOWN, 291, 100, probe.fn)
        assertFalse(d.consume, "không biết nguồn ⇒ KHÔNG đoán là núm — để âm lượng chạy")
        assertEquals("?(timeout)", d.reason)
        assertFalse(m.onKey(knobOnly, VoiceKeyAction.UP, 291, 100, probe.fn).consume)

        val withGeneric = VoiceKeyConfig(true, knobOnly.bindings + VoiceKeyBinding(291, KIKI))
        probe.why = "value=7"
        val g = m.onKey(withGeneric, VoiceKeyAction.DOWN, 291, 200, probe.fn)
        assertTrue(g.fire && g.consume)
        assertEquals(KIKI, g.targetSpec, "giá trị lạ ⇒ dòng không nguồn (hành vi 2.87)")
        assertNull(g.source)
        assertEquals("?(value=7)", g.reason)
        assertTrue(m.onKey(withGeneric, VoiceKeyAction.UP, 291, 200, probe.fn).consume)
    }

    /** Có dòng vô-lăng riêng và dòng không nguồn ⇒ mỗi nút đúng dòng của nó; nút không có dòng riêng lùi về không nguồn. */
    @Test
    fun `dong theo nguon uu tien hon dong khong nguon`() {
        val cfg = VoiceKeyConfig(true, listOf(VoiceKeyBinding(291, KIKI), VoiceKeyBinding(291, FAN_UP, WHEEL)))
        val m = VoiceKeyMatcher()
        assertEquals(FAN_UP, m.onKey(cfg, VoiceKeyAction.DOWN, 291, 1, Probe(WHEEL).fn).targetSpec)
        m.onKey(cfg, VoiceKeyAction.UP, 291, 1)
        assertEquals(KIKI, m.onKey(cfg, VoiceKeyAction.DOWN, 291, 2, Probe(KNOB).fn).targetSpec)
    }

    /**
     * UP/OTHER không thấy DOWN nào (vd dịch vụ vừa nối lại giữa lần nhấn) ⇒ nuốt khi và chỉ khi có dòng (mã, không nguồn)
     * — đúng hành vi 2.87; không tra nguồn (UP không bao giờ tra).
     */
    @Test
    fun `UP mo coi nuot khi va chi khi co dong khong nguon, khong tra nguon`() {
        val knobOnly = VoiceKeyConfig(true, listOf(VoiceKeyBinding(291, FAN_UP, KNOB)))
        val withGeneric = VoiceKeyConfig(true, knobOnly.bindings + VoiceKeyBinding(291, KIKI))
        val m = VoiceKeyMatcher()
        assertEquals(VoiceKeyDecision.IGNORE, m.onKey(knobOnly, VoiceKeyAction.UP, 291, 5, mustNotLookup))
        assertEquals(VoiceKeyDecision.IGNORE, m.onKey(knobOnly, VoiceKeyAction.OTHER, 291, 5, mustNotLookup))
        assertTrue(m.onKey(withGeneric, VoiceKeyAction.UP, 291, 5, mustNotLookup).consume)
        assertTrue(m.onKey(withGeneric, VoiceKeyAction.OTHER, 291, 5, mustNotLookup).consume)
    }

    /** ROM tái dùng downTime sau UP ⇒ lần nhấn MỚI ⇒ tra lại; reset() xoá quyết định dở dang. */
    @Test
    fun `UP xoa dau, downTime tai dung la lan nhan moi, reset xoa het`() {
        val cfg = VoiceKeyConfig(true, listOf(VoiceKeyBinding(291, FAN_UP, KNOB)))
        val m = VoiceKeyMatcher()
        val probe = Probe(KNOB)
        assertTrue(m.onKey(cfg, VoiceKeyAction.DOWN, 291, 100, probe.fn).fire)
        m.onKey(cfg, VoiceKeyAction.UP, 291, 100, probe.fn)
        assertTrue(m.onKey(cfg, VoiceKeyAction.DOWN, 291, 100, probe.fn).fire, "cùng downTime sau UP ⇒ lần nhấn mới")
        m.reset()
        probe.kind = WHEEL
        val afterReset = m.onKey(cfg, VoiceKeyAction.DOWN, 291, 100, probe.fn)
        assertFalse(afterReset.consume, "reset ⇒ DOWN cùng downTime được quyết lại (lần này là vô-lăng)")
        assertEquals(3, probe.calls)
    }

    /** Bất biến hợp đồng trên đường có nguồn: `fire` ⟺ `targetSpec != null`; tắt công tắc ⇒ không tra, không đụng. */
    @Test
    fun `fire tuong duong co dich va cong tac tat khong tra nguon`() {
        val cfg = VoiceKeyConfig(true, listOf(VoiceKeyBinding(291, FAN_UP, KNOB)))
        val m = VoiceKeyMatcher()
        listOf(KNOB, WHEEL, null).forEachIndexed { i, kind ->
            val d = m.onKey(cfg, VoiceKeyAction.DOWN, 291, i.toLong(), Probe(kind).fn)
            assertEquals(d.fire, d.targetSpec != null, "nguồn $kind")
            if (d.fire) assertNotNull(d.targetSpec)
            m.onKey(cfg, VoiceKeyAction.UP, 291, i.toLong())
        }
        assertEquals(VoiceKeyDecision.IGNORE, m.onKey(cfg.copy(enabled = false), VoiceKeyAction.DOWN, 291, 9, mustNotLookup))
    }

    /** Không truyền hàm tra (chỗ gọi cũ) mà mã có dòng theo nguồn ⇒ không biết nguồn ⇒ an toàn như 2.87, không ném. */
    @Test
    fun `khong co ham tra thi coi nhu khong biet nguon`() {
        val cfg = VoiceKeyConfig(true, listOf(VoiceKeyBinding(291, FAN_UP, KNOB)))
        val d = VoiceKeyMatcher().onKey(cfg, VoiceKeyAction.DOWN, 291, 1)
        assertFalse(d.consume)
        assertEquals("?(no_lookup)", d.reason)
    }

    // ── Đường 2.87 KHÔNG ĐỔI — so trùng khít với thuật toán cũ, hàm tra không bao giờ được gọi ─────────────────────────

    /** Thuật toán VoiceKeyMatcher 2.87 (188) chép NGUYÊN VĂN — làm chuẩn so sánh, không dùng ở đâu khác. */
    private class Legacy287 {
        private val firedDownTime = HashMap<Int, Long>()
        fun onKey(cfg: VoiceKeyConfig, action: VoiceKeyAction, keyCode: Int, downTimeMs: Long): VoiceKeyDecision {
            if (!cfg.enabled) return VoiceKeyDecision.IGNORE
            val target = cfg.bindings.firstOrNull { it.keyCode == keyCode }?.targetSpec ?: return VoiceKeyDecision.IGNORE
            return when (action) {
                VoiceKeyAction.DOWN -> {
                    val fire = firedDownTime[keyCode] != downTimeMs
                    if (fire) firedDownTime[keyCode] = downTimeMs
                    VoiceKeyDecision(fire = fire, consume = true, targetSpec = if (fire) target else null)
                }
                VoiceKeyAction.UP -> { firedDownTime.remove(keyCode); VoiceKeyDecision(fire = false, consume = true) }
                VoiceKeyAction.OTHER -> VoiceKeyDecision(fire = false, consume = true)
            }
        }
        fun reset() = firedDownTime.clear()
    }

    /**
     * CLAUDE.md §6 — mã KHÔNG có dòng gán theo nguồn phải ra quyết định Y HỆT 2.87. 20 000 sự kiện ngẫu nhiên (DOWN/lặp/
     * UP/OTHER, mã gán + không gán, downTime trùng/tái dùng, reset, công tắc tắt) trên bốn cấu hình — kể cả cấu hình CÓ
     * dòng theo nguồn ở mã KHÁC (mã 291 núm) để chắc mã thường không bị kéo sang đường mới. Hàm tra ném nếu bị gọi.
     */
    @Test
    fun `ma khong co dong theo nguon - trung khit thuat toan 2_87 va khong bao gio tra nguon`() {
        val configs = listOf(
            VoiceKeyConfig(true, listOf(VoiceKeyBinding(328, KIKI), VoiceKeyBinding(231, "__VOICEKEY231__"), VoiceKeyBinding(87, "com.vietmap.s1"))),
            VoiceKeyConfig(true, listOf(VoiceKeyBinding(291, KIKI), VoiceKeyBinding(292, FAN_DOWN))),
            VoiceKeyConfig(true, emptyList()),
            VoiceKeyConfig(true, listOf(VoiceKeyBinding(291, FAN_UP, KNOB), VoiceKeyBinding(328, KIKI), VoiceKeyBinding(88, KIKI))),
        )
        val keys = intArrayOf(328, 231, 87, 88, 291, 292, 25, 24)
        configs.forEachIndexed { ci, cfg ->
            val rnd = Random(4_10 + ci)
            val now = VoiceKeyMatcher()
            val old = Legacy287()
            repeat(5_000) { step ->
                val key = keys[rnd.nextInt(keys.size)]
                if (VoiceKeyBindings.needsSource(cfg.bindings, key)) return@repeat   // mã đó là việc của các bài trên
                val action = VoiceKeyAction.entries[rnd.nextInt(3)]
                val downTime = rnd.nextLong(0, 6)   // nhỏ ⇒ trùng/tái dùng downTime thường xuyên
                val c = if (rnd.nextInt(50) == 0) cfg.copy(enabled = false) else cfg
                if (rnd.nextInt(200) == 0) { now.reset(); old.reset() }
                assertEquals(
                    old.onKey(c, action, key, downTime), now.onKey(c, action, key, downTime, mustNotLookup),
                    "cấu hình #$ci bước $step: $action mã $key downTime $downTime",
                )
            }
        }
    }
}
