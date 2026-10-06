package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 2.91 VOICE-APP-NAMES · C7 — bản ghi `alias` của ảnh chụp ngữ pháp (đường tên đã dạy sang `:wake`, spec §4.5). */
class VoiceGrammarSnapshotAliasTest {

    private val names = listOf(
        TaughtName("com.example.flix", TaughtSource.SPEECH, "nep leag", ""),
        TaughtName("com.example.files", TaughtSource.TYPED, "quản lý tệp", ""),
    )

    @Test
    fun `vong tron ban ghi alias giu nguon va dau`() {
        val snap = VoiceGrammarSnapshot(profiles = listOf("Mặc định"), activeProfile = "Mặc định", aliases = names)
        val raw = snap.encode()
        assertTrue(raw.contains("alias\tcom.example.flix\tS\tnep leag\n"), raw)
        val d = VoiceGrammarSnapshot.decode(raw)
        assertEquals(names, d.snapshot.aliases)
        assertEquals(null, d.problem)
    }

    @Test
    fun `tep cu khong co alias doc ra rong va ban ghi khac khong doi`() {
        val old = "kachi-grammar\tv1\t0\nactive\tA\nprofile\tA\n"
        val d = VoiceGrammarSnapshot.decode(old)
        assertTrue(d.snapshot.aliases.isEmpty())
        assertEquals(listOf("A"), d.snapshot.profiles)
        // Không có tên ⇒ không một byte nào khác bản trước.
        assertFalse(VoiceGrammarSnapshot(profiles = listOf("A"), activeProfile = "A").encode().contains("alias"))
    }

    @Test
    fun `dong alias hong bi bo va ly do khong in chu nguoi dung`() {
        val raw = "kachi-grammar\tv1\t0\nalias\tkhong-goi\tS\tbí mật riêng\nalias\tcom.example.a\tX\tbí mật\nalias\tcom.example.b\tS\tnep leag\n"
        val d = VoiceGrammarSnapshot.decode(raw)
        assertEquals(listOf("nep leag"), d.snapshot.aliases.map { it.accented })
        assertNotNull(d.problem)
        assertFalse(d.problem!!.contains("bí mật"), d.problem)
    }
}
