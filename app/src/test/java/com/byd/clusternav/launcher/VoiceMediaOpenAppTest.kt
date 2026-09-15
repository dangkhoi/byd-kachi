package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceMediaOp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ KHOÁ LỖI [P1] L2 CỦA LƯỢT E2E 2026-09-15 — *"phát nhạc trên YT Music"* không mở app nào ══════════════════
 *
 * [ĐO] `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 **L2** (ca t46/t50 của `voice-cases.tsv`): ý định
 * phân tích ĐÚNG (`Media · Phát nhạc trên YouTube Music`, tức `app=ytmusic` đã ra) nhưng
 * `VoiceDispatcher.runMedia` mở đầu bằng `if (i.op != QUERY) { runTransport(i); return }` ⇒ trường `app` bị vứt,
 * `MediaBridge` không có phiên nào nên trả lời *"chưa có phiên nhạc nào"* và **không app nào lên màn**.
 *
 * Bốn tính chất được khoá ở đây, cái thứ ba và thứ tư là hai ca dễ hỏng nhất khi sửa:
 *  1. PLAY + nêu đích danh app ⇒ **mở app đó**;
 *  2. PLAY + không phiên nào ⇒ mở app nhạc đầu bảng;
 *  3. PLAY + app đó **đang phát** ⇒ **transport**, không mở đè (một lượt chuyển màn thừa lúc đang lái);
 *  4. PAUSE/NEXT/PREV ⇒ vẫn transport, và câu *"chưa có phiên nhạc"* ở đó là câu ĐÚNG.
 */
class VoiceMediaOpenAppTest {

    private companion object {
        const val YT_MUSIC = "com.google.android.apps.youtube.music"
        const val SPOTIFY = "com.spotify.music"
    }

    /** Bộ giàn tối thiểu — cùng hình dạng với `VoiceHandoverTest.Rig`, thêm một [MediaBridge] giả đếm lượt. */
    private class Rig(
        labels: Map<String, String> = mapOf("YouTube Music" to YT_MUSIC, "Spotify" to SPOTIFY),
        private val playing: String? = null,
    ) {
        val said = ArrayList<String>()
        val opened = ArrayList<String>()
        val sent = ArrayList<VoiceAppIntents.Handoff>()
        val transport = ArrayList<String>()

        /** Cầu nhạc giả: ghi lại lệnh và luôn trả `false` — đúng ca *"chưa có phiên nào"* của máy ảo. */
        private val bridge = object : MediaTransport {
            override fun play(): Boolean { transport += "play"; return false }
            override fun pause(): Boolean { transport += "pause"; return false }
            override fun next(): Boolean { transport += "next"; return false }
            override fun prev(): Boolean { transport += "prev"; return false }
        }

        val dispatcher = VoiceDispatcher(
            control = { error("bài này không chạm nút xe") },
            state = { HomeUiState() },
            media = { bridge },
            appsByLabel = { labels },
            openApp = { pkg -> opened += pkg; true },
            openAppList = {},
            openSettings = {},
            onSwitchProfile = {},
            onListen = {},
            confirm = { _, y, _ -> y() },
            say = { said += it },
            assignAppToSlot = { _, _ -> true },
            sendToApp = { h -> sent += h; true },
            geocode = { null },
            mediaPackage = { playing },
            background = { it() },
            onUi = { it() },
        )

        fun run(i: VoiceIntent) = dispatcher.execute(listOf(i))
    }

    @Test
    fun `phat nhac tren app duoc goi ten thi MO dung app do`() {
        val r = Rig()
        r.run(VoiceIntent.Media(VoiceMediaOp.PLAY, app = "ytmusic"))
        assertEquals(listOf(YT_MUSIC), r.opened, "câu nêu đích danh mà không app nào lên màn")
        assertTrue(r.transport.isEmpty(), "chưa có phiên nào thì bắn transport là bắn vào chỗ trống")
        assertTrue(r.said.single().startsWith("✓"), "đã mở được app thì phải báo đã làm; nhận được: ${r.said}")
        assertTrue(r.said.single().contains("YouTube Music"), "phải gọi tên app: ${r.said}")
    }

    @Test
    fun `phat nhac khong neu app va chua co phien thi mo app nhac dau bang`() {
        val r = Rig()
        r.run(VoiceIntent.Media(VoiceMediaOp.PLAY))
        assertEquals(listOf(YT_MUSIC), r.opened)
    }

    @Test
    fun `app duoc goi ten dang phat thi dieu khien phien do, khong mo de`() {
        val r = Rig(playing = YT_MUSIC)
        r.run(VoiceIntent.Media(VoiceMediaOp.PLAY, app = "ytmusic"))
        assertEquals(listOf("play"), r.transport, "đang phát mà không đi đường transport")
        assertTrue(r.opened.isEmpty(), "mở đè lên chính app đang phát là một lượt chuyển màn thừa")
    }

    @Test
    fun `phat nhac khi da co phien khac thi van la transport`() {
        val r = Rig(playing = SPOTIFY)
        r.run(VoiceIntent.Media(VoiceMediaOp.PLAY))
        assertEquals(listOf("play"), r.transport, "có phiên đang chạy mà lại mở một app khác đè lên")
        assertTrue(r.opened.isEmpty())
    }

    @Test
    fun `dung va chuyen bai van la transport, va cau chua co phien nhac giu nguyen`() {
        listOf(VoiceMediaOp.PAUSE, VoiceMediaOp.NEXT, VoiceMediaOp.PREV).forEach { op ->
            val r = Rig()
            r.run(VoiceIntent.Media(op))
            assertTrue(r.opened.isEmpty(), "$op mà lại MỞ một app nhạc — đó là việc khác hẳn")
            assertEquals(1, r.transport.size, "$op phải đi transport")
            assertTrue(r.said.single().startsWith("✗"), "$op không có phiên ⇒ phải nói thẳng; nhận được: ${r.said}")
        }
    }

    @Test
    fun `app duoc goi ten ma chua cai thi noi chua cai, khong doi app khac`() {
        val r = Rig(labels = mapOf("YouTube Music" to YT_MUSIC))
        r.run(VoiceIntent.Media(VoiceMediaOp.PLAY, app = "spotify"))
        assertTrue(r.opened.isEmpty(), "đã lặng lẽ đổi sang app khác")
        assertTrue(r.said.single().contains("Spotify"), "phải gọi tên app còn thiếu; nhận được: ${r.said}")
    }

    @Test
    fun `tim bai hat van di duong giao chu nhu cu`() {
        val r = Rig()
        r.run(VoiceIntent.Media(VoiceMediaOp.QUERY, "diễm xưa", "ytmusic"))
        assertEquals(YT_MUSIC, r.sent.single().pkg, "nhánh QUERY không được đổi hành vi")
    }
}
