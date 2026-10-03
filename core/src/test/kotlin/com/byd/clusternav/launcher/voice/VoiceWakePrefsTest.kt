package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FIX286 · VK4 — prefs tươi cho `:wake` đi chung tệp ảnh chụp ngữ pháp ([VoiceGrammarSnapshot.wake]).
 * Khoá: round-trip · tệp ≤ 2.85 (không có bản ghi mới) đọc ra "không mang" để `:wake` lùi về đường cũ · cờ hỏng không
 * bị đoán · chế độ quyết từ ảnh chụp, không từ prefs cũ của `:wake`.
 */
class VoiceWakePrefsTest {

    private val base = VoiceGrammarSnapshot(profiles = listOf("Mặc định"), activeProfile = "Mặc định", writtenAtMs = 5L)
    private val prefs = VoiceWakePrefs(
        wakeSwitch = false,
        keyHold = true,
        confirmIds = setOf("control:sunroof", "control:trunk"),
        navDefault = "vietmap",
        musicDefault = "",
    )

    @Test
    fun `round-trip du nam truong - ke ca nhac tu chon rong va tap hoi rong`() {
        val s = base.copy(wake = prefs)
        val d = VoiceGrammarSnapshot.decode(s.encode())
        assertEquals(s, d.snapshot)
        assertNull(d.problem)
        val empty = base.copy(wake = prefs.copy(confirmIds = emptySet()))
        assertEquals(emptySet<String>(), VoiceGrammarSnapshot.decode(empty.encode()).snapshot.wake.confirmIds, "tập rỗng CÓ ghi ≠ không mang")
    }

    @Test
    fun `tep 2_85 khong co ban ghi moi - doc ra EMPTY va khong doi mot byte cua ban ghi cu`() {
        val old = base.encode()
        assertEquals(VoiceWakePrefs.EMPTY, VoiceGrammarSnapshot.decode(old).snapshot.wake)
        assertTrue(base.copy(wake = prefs).encode().startsWith(old), "bản ghi mới chỉ NỐI vào sau — ba loại bản ghi cũ y nguyên")
    }

    @Test
    fun `co hong khong bi doan - truong giu null va co ly do`() {
        val text = base.encode() + "wake\tyes\nkeyhold\t2\n"
        val d = VoiceGrammarSnapshot.decode(text)
        assertNull(d.snapshot.wake.wakeSwitch)
        assertNull(d.snapshot.wake.keyHold)
        assertNotNull(d.problem)
    }

    @Test
    fun `che do quyet tu anh chup - cong tac trong anh thang prefs cu cua wake, cau chi tuoi van thang`() {
        // Ảnh chụp nói "Hey Kachi" BẬT trong khi cache prefs của `:wake` còn TẮT (nó mở tệp trước khi người dùng gạt).
        val on = VoiceWakePrefs(wakeSwitch = true, keyHold = false)
        assertEquals(VoiceWakeMode.WAKE, on.mode(ownWakeEffective = false, fuseTripped = false))
        assertEquals(VoiceWakeMode.OFF, on.mode(ownWakeEffective = true, fuseTripped = true), "cầu chì (tệp marker, đọc tươi) thắng công tắc")
        // Ảnh chụp nói TẮT + có phím ⇒ HOLD, kể cả khi cache cũ của `:wake` còn BẬT.
        assertEquals(VoiceWakeMode.HOLD, VoiceWakePrefs(wakeSwitch = false, keyHold = true).mode(ownWakeEffective = true, fuseTripped = false))
        // Không mang gì (tệp ≤ 2.85 / chưa ghi): hành vi 2.85 — wake theo prefs của `:wake`, không bao giờ tự HOLD.
        assertEquals(VoiceWakeMode.OFF, VoiceWakePrefs.EMPTY.mode(ownWakeEffective = false, fuseTripped = false))
        assertEquals(VoiceWakeMode.WAKE, VoiceWakePrefs.EMPTY.mode(ownWakeEffective = true, fuseTripped = false))
        assertFalse(VoiceWakePrefs.EMPTY.mode(ownWakeEffective = false, fuseTripped = false).modelInWake)
    }
}
