package com.byd.clusternav.voicekey

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * QA 2.87 [P3] — học một phím media làm YT Music bật lên ([ĐO máy ảo `learn88-orphan-up.log` (bằng chứng phiên, ngoài repo)]: `learned voice keycode=88`
 * rồi 192 ms sau `MediaSessionService Sending KeyEvent { action=ACTION_UP, keyCode=KEYCODE_MEDIA_PREVIOUS … } to … youtube.music`).
 * Khoá: phần còn lại của CHÍNH lần nhấn đã học (cùng mã + cùng `downTime`) bị nuốt; lần nhấn khác không bị đụng.
 */
class KeyLearnTailTest {

    /**
     * Dòng sự kiện của log QA, chạy qua đúng thứ tự của `NavAccessibilityService.onKeyEvent` (đuôi học → cờ học → matcher).
     * Trả "nuốt" cho từng sự kiện. Phím 88 KHÔNG gán ⇒ matcher trả IGNORE ⇒ không có [KeyLearnTail] thì UP lọt tới hệ thống.
     */
    private fun route(tail: KeyLearnTail?, events: List<Triple<VoiceKeyAction, Int, Long>>): List<Boolean> {
        var learning = true
        val matcher = VoiceKeyMatcher()
        val cfg = VoiceKeyConfig(enabled = true, bindings = emptyList())
        return events.map { (action, key, down) ->
            when {
                tail?.swallow(action, key, down) == true -> true
                learning -> { if (action == VoiceKeyAction.DOWN) { learning = false; tail?.learned(key, down) }; true }
                else -> matcher.onKey(cfg, action, key, down).consume
            }
        }
    }

    private val learn88 = listOf(
        Triple(VoiceKeyAction.DOWN, 88, 1_000L),
        Triple(VoiceKeyAction.DOWN, 88, 1_000L),   // lặp khi giữ (repeatCount > 0, cùng downTime)
        Triple(VoiceKeyAction.UP, 88, 1_000L),
    )

    @Test
    fun `hoc phim media - nuot ca DOWN lap va UP cua chinh lan nhan do`() {
        assertEquals(listOf(true, true, true), route(KeyLearnTail(), learn88))
        // Ca hỏng của bản cũ (không có đuôi học): DOWN lặp + UP lọt tới hệ thống ⇒ app media nhận UP mồ côi.
        assertEquals(listOf(true, false, false), route(null, learn88), "đúng ca QA đo được — nếu bài này xanh ở dòng trên mà đỏ ở đây thì fixture sai")
    }

    @Test
    fun `lan nhan SAU khong bi nuot - phim duoc tra ve cho he thong`() {
        val tail = KeyLearnTail()
        val next = learn88 + listOf(Triple(VoiceKeyAction.DOWN, 88, 5_000L), Triple(VoiceKeyAction.UP, 88, 5_000L))
        assertEquals(listOf(true, true, true, false, false), route(tail, next), "UP đã xoá dấu ⇒ lần nhấn kế đi đường thường")
    }

    @Test
    fun `phim khac va lan nhan moi cung ma thi khong nuot`() {
        val tail = KeyLearnTail()
        tail.learned(88, 1_000L)
        assertFalse(tail.swallow(VoiceKeyAction.UP, 24, 1_000L), "phím khác")
        assertTrue(tail.swallow(VoiceKeyAction.DOWN, 88, 1_000L), "dấu còn sau phím khác")
        // UP bị mất (service mất bound giữa chừng) ⇒ DOWN của lần nhấn MỚI cùng mã xoá dấu, không bị nuốt.
        assertFalse(tail.swallow(VoiceKeyAction.DOWN, 88, 2_000L))
        assertFalse(tail.swallow(VoiceKeyAction.UP, 88, 1_000L), "dấu đã xoá")
        tail.learned(88, 3_000L)
        tail.reset()
        assertFalse(tail.swallow(VoiceKeyAction.UP, 88, 3_000L), "reset (service nối lại) ⇒ không nuốt")
    }
}
