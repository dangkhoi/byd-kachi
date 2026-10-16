package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.modules.clustercast.nowplaying.ClusterNowPlayingKeys
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.11 · CLUSTER-NOW-PLAYING — dây nối mà JVM không dựng được (MediaSession, HAL, Application). Khoá CLAUDE.md §8: hàm mới
 * phải CÓ chỗ gọi — `MediaBridge.watchAll` → `evaluate` → `ClusterNowPlayingPlan.onMedia` → ghi qua đường HAL chuẩn; công tắc
 * mặc định TẮT; TẮT thì `install` không dựng luồng; cầu `np` đi đúng đường của công tắc Cài đặt.
 */
class ClusterNowPlayingWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$rel")
    private val rt by lazy { code("modules/clustercast/ClusterNowPlaying.kt") }

    @Test fun `ngu - mac dinh TAT, TAT va khong dau thi install khong dung luong`() {
        assertFalse(ClusterNowPlayingKeys.DEFAULT_ON)
        val install = SourceRoots.body(rt, "fun install(ctx: Context)")
        val early = install.indexOf("if (marker == null && !on) return")
        assertTrue(early > 0, install)
        assertTrue(early < install.indexOf("post(a)"), "cổng TẮT phải đứng trước lượt dựng luồng")
        assertTrue("ClusterNowPlayingPlan.restoreAtStartup(marker, sink(a))" in install, "dấu bền trả lúc khởi động")
    }

    @Test fun `Application goi install truoc dong chot cuoi`() {
        val app = code("KachiApplication.kt")
        val at = app.indexOf("ClusterNowPlaying.install(this)")
        assertTrue(at > 0)
        assertTrue(at < app.indexOf("EarlyShellChannel.start(this)"))
    }

    @Test fun `phien nhac di qua MediaBridge toi plan`() {
        val start = SourceRoots.body(rt, "private fun start(a: Context)")
        assertTrue("MediaBridge(a).watchAll(h) { evaluate(a) }" in start, start)
        assertTrue("registerShutdown(a)" in start)
        val ev = SourceRoots.body(rt, "private fun evaluate(a: Context)")
        assertTrue("MediaBridge(a).nowPlaying()" in ev && "p.onMedia(media, { powerLevel(a) }" in ev, ev)
        assertTrue("h.postDelayed(pauseCheck" in ev, "một lượt hẹn khi tạm dừng")
        assertFalse("scheduleWithFixedDelay" in rt || "sleep(" in rt, "không nhịp định kỳ")
        val mb = code("launcher/MediaBridge.kt")
        assertTrue("fun watchAll(handler: Handler, onEvent: () -> Unit): (() -> Unit)? =" in mb)
        assertTrue("watchWhere({ true }, handler, onEvent, onEvent, onListChanged = onEvent)" in mb)
    }

    @Test fun `ghi qua duong HAL chuan va de dong nhat ky`() {
        val sink = rt.substring(rt.indexOf("private fun sink(a: Context): NowPlayingSink"))
        assertTrue("BydHalContentPush.pushMusicInt(it, id, value)" in sink && "BydHalContentPush.pushMusicBytes(it, id, bytes)" in sink, sink)
        val push = code("modules/hal/BydHalContentPush.kt")
        assertTrue("fun pushMusicInt(instr: Any, id: Int, value: Int): String = cachedSetInt(instr, id, value)" in push)
        assertTrue("BydHal.device(BydHal.INSTRUMENT" in sink)
        assertTrue("CtlJournalStore.journal(a).write(line)" in sink)
        assertTrue("Prefs.saveNowPlayingMarker(a, marker)" in sink)
        listOf("\"am ", "\"wm ", "service call", "--display").forEach { assertFalse(it in rt, "không lệnh shell/display: $it") }
        assertTrue("commit()" in code("PrefsNowPlaying.kt"), "dấu bền ghi đồng bộ")
    }

    @Test fun `cong tac va cau np di cung mot duong`() {
        val set = SourceRoots.body(rt, "fun setEnabled(ctx: Context, on: Boolean)")
        assertTrue("post(a) { if (on) start(a) else stopWatching() }" in set, "bật có hiệu lực ngay, không chờ khởi động lại")
        val np = code("launcher/testbridge/TestBridgeNowPlaying.kt")
        assertTrue("NowPlayingControl.setEnabled(app, true)" in np && "cmd.autoConfirm" in np && "AUTO-CONFIRM" in np)
        assertTrue("TestBridgeNowPlayingCommands.NP -> TestBridgeNowPlaying.run(app, cmd, reply)" in code("launcher/testbridge/TestBridgeNoHome.kt"))
        assertTrue("bridge().setNowPlayingEnabled(on)" in code("launcher/SettingsNowPlayingRows.kt"))
        assertTrue("nowPlaying.build(body)" in code("launcher/SettingsSectionsNav.kt"))
    }
}
