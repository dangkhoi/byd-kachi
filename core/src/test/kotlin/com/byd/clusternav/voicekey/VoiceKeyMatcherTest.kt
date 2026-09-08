package com.byd.clusternav.voicekey

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VoiceKeyMatcherTest {

    private val KEY = 328
    private val TARGET = "ai.zalo.kiki.car"

    /**
     * F3: cấu hình giờ là DANH SÁCH. Helper giữ nguyên chữ ký cũ (enabled/keyCode) để mọi assertion đã khoá
     * bài học của 1.19 ở dưới KHÔNG bị viết lại — `CLAUDE.md §6`: không đảo đường đang chạy tốt ngoài hiện trường.
     */
    private fun cfg(enabled: Boolean = true, keyCode: Int = KEY) =
        VoiceKeyConfig(enabled, listOf(VoiceKeyBinding(keyCode, TARGET)))

    @Test
    fun `disabled ignores everything (native untouched)`() {
        val m = VoiceKeyMatcher()
        assertEquals(VoiceKeyDecision.IGNORE, m.onKey(cfg(enabled = false), VoiceKeyAction.DOWN, KEY, 0))
        assertEquals(VoiceKeyDecision.IGNORE, m.onKey(cfg(enabled = false), VoiceKeyAction.UP, KEY, 0))
    }

    @Test
    fun `wrong keycode is ignored (never touches other buttons)`() {
        val m = VoiceKeyMatcher()
        assertEquals(VoiceKeyDecision.IGNORE, m.onKey(cfg(), VoiceKeyAction.DOWN, 99, 0))
        assertEquals(VoiceKeyDecision.IGNORE, m.onKey(cfg(), VoiceKeyAction.UP, 99, 0))
    }

    @Test
    fun `fires once on down and consumes the whole press`() {
        val m = VoiceKeyMatcher()
        val down = m.onKey(cfg(), VoiceKeyAction.DOWN, KEY, 100)
        assertTrue(down.fire); assertTrue(down.consume)
        val repeat = m.onKey(cfg(), VoiceKeyAction.DOWN, KEY, 100)   // auto-repeat, same press
        assertFalse(repeat.fire); assertTrue(repeat.consume)
        val up = m.onKey(cfg(), VoiceKeyAction.UP, KEY, 100)
        assertFalse(up.fire); assertTrue(up.consume)                 // swallow the UP of our key
    }

    @Test
    fun `fires regardless of hold duration (no 500ms gate)`() {
        val m = VoiceKeyMatcher()
        assertTrue(m.onKey(cfg(), VoiceKeyAction.DOWN, KEY, 0).fire) // long-held pulse still fires
        assertFalse(m.onKey(cfg(), VoiceKeyAction.UP, KEY, 0).fire)  // late UP only consumed
    }

    @Test
    fun `each distinct press fires again`() {
        val m = VoiceKeyMatcher()
        assertTrue(m.onKey(cfg(), VoiceKeyAction.DOWN, KEY, 100).fire)
        m.onKey(cfg(), VoiceKeyAction.UP, KEY, 100)
        assertTrue(m.onKey(cfg(), VoiceKeyAction.DOWN, KEY, 200).fire)  // new downTime → new press
    }

    @Test
    fun `reset lets the next press fire again`() {
        val m = VoiceKeyMatcher()
        assertTrue(m.onKey(cfg(), VoiceKeyAction.DOWN, KEY, 100).fire)
        m.reset()
        assertTrue(m.onKey(cfg(), VoiceKeyAction.DOWN, KEY, 100).fire) // same downTime but reset cleared
    }

    // ─── F3 (owner 2026-08-24): nhiều phím → nhiều app ──────────────────────────────────────────

    private val many = VoiceKeyConfig(
        enabled = true,
        bindings = listOf(
            VoiceKeyBinding(328, "ai.zalo.kiki.car"),
            VoiceKeyBinding(231, "__VOICEKEY231__"),
            VoiceKeyBinding(87, "com.vietmap.s1"),
        ),
    )

    /** Yêu cầu chính của owner: mỗi nút mở ĐÚNG app của nó, không phải cùng một app. */
    @Test
    fun `moi phim mo dung app cua no`() {
        val m = VoiceKeyMatcher()
        assertEquals("ai.zalo.kiki.car", m.onKey(many, VoiceKeyAction.DOWN, 328, 10).targetSpec)
        assertEquals("__VOICEKEY231__", m.onKey(many, VoiceKeyAction.DOWN, 231, 20).targetSpec)
        assertEquals("com.vietmap.s1", m.onKey(many, VoiceKeyAction.DOWN, 87, 30).targetSpec)
    }

    /** Phím KHÔNG có trong danh sách ⇒ không bắn, KHÔNG nuốt — chức năng gốc của nút giữ nguyên. */
    @Test
    fun `phim ngoai danh sach khong bi dung toi`() {
        val m = VoiceKeyMatcher()
        val d = m.onKey(many, VoiceKeyAction.DOWN, 25, 10)   // 25 = VOLUME_DOWN, chưa gán
        assertEquals(VoiceKeyDecision.IGNORE, d)
        assertFalse(d.consume, "nuốt phím chưa gán = cướp chức năng gốc của xe")
    }

    /** Danh sách RỖNG ⇒ không có gì chạy (owner: "danh sách rỗng thì không có gì chạy"). */
    @Test
    fun `danh sach rong thi khong co gi chay`() {
        val m = VoiceKeyMatcher()
        val empty = VoiceKeyConfig(enabled = true, bindings = emptyList())
        assertEquals(VoiceKeyDecision.IGNORE, m.onKey(empty, VoiceKeyAction.DOWN, 328, 10))
        assertEquals(VoiceKeyDecision.IGNORE, m.onKey(empty, VoiceKeyAction.UP, 328, 10))
    }

    /** Bất biến contract: `fire` ⟺ `targetSpec != null` — tầng app dựa vào đây để khỏi tra prefs lần hai. */
    @Test
    fun `fire tuong duong co dich`() {
        val m = VoiceKeyMatcher()
        val first = m.onKey(many, VoiceKeyAction.DOWN, 328, 10)
        assertTrue(first.fire); assertNotNull(first.targetSpec)
        val repeat = m.onKey(many, VoiceKeyAction.DOWN, 328, 10)   // auto-repeat cùng lần nhấn
        assertFalse(repeat.fire); assertNull(repeat.targetSpec, "không bắn thì không được kèm đích")
        val up = m.onKey(many, VoiceKeyAction.UP, 328, 10)
        assertFalse(up.fire); assertNull(up.targetSpec)
    }

    /**
     * Khoá đúng chỗ phải sửa khi chuyển sang danh sách: ổ khoá chống-bắn-lặp là **(mã phím, downTime)**,
     * không phải downTime đơn lẻ. Hai phím đã gán bấm cùng mốc ms (chord, hoặc đồng hồ thô) mà dùng chung
     * một ô nhớ thì phím thứ hai bị nuốt mất lần bắn — nút câm mà test cũ vẫn xanh.
     */
    @Test
    fun `hai phim trung downTime van bap ban rieng`() {
        val m = VoiceKeyMatcher()
        val a = m.onKey(many, VoiceKeyAction.DOWN, 328, 777)
        val b = m.onKey(many, VoiceKeyAction.DOWN, 231, 777)   // CÙNG downTime, phím khác
        assertTrue(a.fire, "phím thứ nhất phải bắn")
        assertTrue(b.fire, "phím thứ hai KHÔNG được bị ổ khoá của phím thứ nhất chặn")
        assertEquals("__VOICEKEY231__", b.targetSpec)
    }

    /** Tắt công tắc chính ⇒ cả danh sách ngừng, mọi phím pass-through. */
    @Test
    fun `tat cong tac thi ca danh sach ngung`() {
        val m = VoiceKeyMatcher()
        val off = many.copy(enabled = false)
        assertEquals(VoiceKeyDecision.IGNORE, m.onKey(off, VoiceKeyAction.DOWN, 328, 10))
        assertEquals(VoiceKeyDecision.IGNORE, m.onKey(off, VoiceKeyAction.DOWN, 231, 10))
    }

    @Test
    fun `reset xoa o khoa cua moi phim`() {
        val m = VoiceKeyMatcher()
        assertTrue(m.onKey(many, VoiceKeyAction.DOWN, 328, 100).fire)
        assertTrue(m.onKey(many, VoiceKeyAction.DOWN, 231, 100).fire)
        m.reset()
        assertTrue(m.onKey(many, VoiceKeyAction.DOWN, 328, 100).fire)
        assertTrue(m.onKey(many, VoiceKeyAction.DOWN, 231, 100).fire)
    }
}
