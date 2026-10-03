package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.WakeSessionJournal.Entry
import com.byd.clusternav.launcher.voice.WakeSessionJournal.Outcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** FIX286 · VK6 — dạng dòng nhật ký phiên `:wake` cố định (grep được), kết cục suy đúng, không mang dữ liệu người dùng. */
class WakeSessionJournalTest {

    @Test
    fun `dong day du - khoa co dinh, so ms, mo hinh nguoi`() {
        assertEquals(
            "wake-session entry=phim mode=HOLD warm=0 loading=0 load=21345 ready=21890 out=nghe",
            WakeSessionJournal.line(Entry.KEY, VoiceWakeMode.HOLD, warm = false, loading = false, loadMs = 21_345, readyMs = 21_890, outcome = Outcome.HEARD),
        )
    }

    @Test
    fun `truong vang ghi dau gach - khong chu null`() {
        val l = WakeSessionJournal.line(Entry.MIC, VoiceWakeMode.WAKE, warm = true, loading = true, loadMs = null, readyMs = null, outcome = Outcome.CANCELLED)
        assertEquals("wake-session entry=mic mode=WAKE warm=1 loading=1 load=- ready=- out=huy", l)
        assertFalse(l.contains("null"))
    }

    @Test
    fun `ket cuc - huy thang, toi micro la nghe, khong toi ma khong huy la loi`() {
        assertEquals(Outcome.CANCELLED, WakeSessionJournal.outcomeOf(cancelled = true, reachedMic = true))
        assertEquals(Outcome.CANCELLED, WakeSessionJournal.outcomeOf(cancelled = true, reachedMic = false))
        assertEquals(Outcome.HEARD, WakeSessionJournal.outcomeOf(cancelled = false, reachedMic = true))
        assertEquals(Outcome.FAILED, WakeSessionJournal.outcomeOf(cancelled = false, reachedMic = false))
    }

    @Test
    fun `loi vao - ma la hoac vang thanh dau hoi, moi ma deu ASCII khong dau`() {
        Entry.entries.forEach { assertEquals(it, Entry.of(it.code)) }
        assertEquals(Entry.UNKNOWN, Entry.of(null))
        assertEquals(Entry.UNKNOWN, Entry.of("phím"))
        (Entry.entries.map { it.code } + Outcome.entries.map { it.code }).forEach {
            assertEquals(true, it.matches(Regex("[a-z?\\-]+")), "mã '$it' phải ASCII thường — dòng nhật ký grep được")
        }
    }
}
