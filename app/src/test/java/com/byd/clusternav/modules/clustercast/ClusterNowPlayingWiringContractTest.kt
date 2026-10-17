package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.12 — dây nối JVM không dựng được (MediaSession, WindowManager, Application). Khoá ba điều:
 *  1. Bộ ghi nguồn 11 của 3.1.11 ĐÃ GỠ (không còn ghi tên bài/trạng thái/nguồn lên HAL, không cầu `np`) — chỉ còn đường trả lại
 *     lúc khởi động, và đường đó 0 việc khi không có dấu bền.
 *  2. Dải tên bài trên mặt chiếu đi ĐÚNG đường lớp phủ cụm chuẩn (`ClusterOverlayDisplays`), chỉ bám phiên nhạc khi đang chiếu.
 *  3. CLAUDE.md §8: hàm mới có chỗ gọi (Application → install; Cài đặt → cầu → prefs → bộ nghe prefs của lớp phủ).
 */
class ClusterNowPlayingWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$rel")
    private val strip by lazy { code("modules/clustercast/CastNowPlayingOverlay.kt") }
    private val legacy by lazy { code("modules/clustercast/ClusterNowPlayingLegacyRestore.kt") }

    @Test fun `bo ghi nguon 11 da go - khong con tep, khong cau np, khong ghi bytes ten bai`() {
        listOf("modules/clustercast/ClusterNowPlaying.kt", "launcher/testbridge/TestBridgeNowPlaying.kt").forEach {
            assertFalse(SourceRoots.exists("src/main/java/com/byd/clusternav/$it"), "còn tệp đời cũ: $it")
        }
        assertFalse("TestBridgeNowPlaying" in code("launcher/testbridge/TestBridgeNoHome.kt"))
        assertFalse("pushMusicBytes" in code("modules/hal/BydHalContentPush.kt"))
        assertFalse("nowPlaying" in code("modules/clustercast/ClusterProfile.kt"), "hồ sơ không còn số liệu ghi đè nguồn")
        assertFalse("BydHal" in strip || "pushMusic" in strip, "dải tên bài không chạm HAL")
    }

    @Test fun `duong tra lai luc khoi dong - khong dau thi khong luong, con dau thi tra roi don`() {
        val install = SourceRoots.body(legacy, "fun install(ctx: Context)")
        val early = install.indexOf("?: return")
        assertTrue(early > 0 && early < install.indexOf("Thread("), "không dấu ⇒ trả về trước khi dựng luồng: $install")
        assertTrue("ClusterNowPlayingLegacy.restoreAtStartup(marker, sink(a))" in install)
        assertTrue("BydHalContentPush.pushMusicInt(it, id, value)" in legacy, "ghi qua nguyên thuỷ có cache từ chối")
        assertTrue("commit()" in code("PrefsNowPlaying.kt"), "xoá dấu đồng bộ")
    }

    @Test fun `Application goi ca hai install truoc dong chot cuoi`() {
        val app = code("KachiApplication.kt")
        val end = app.indexOf("EarlyShellChannel.start(this)")
        listOf("ClusterNowPlayingLegacyRestore.install(this)", "CastNowPlayingOverlay.install(this)").forEach {
            val at = app.indexOf(it)
            assertTrue(at in 1 until end, "$it phải có và đứng trước dòng chốt cuối")
        }
    }

    @Test fun `lop phu di duong chuan cua cum, chi bam phien khi dang chieu`() {
        val rec = SourceRoots.body(strip, "private fun reconcile()")
        assertTrue("Prefs.castNowPlayingEnabled(a) && ClusterOverlayDisplays.castLiveId != null && !ClusterOverlayDisplays.paused" in rec, rec)
        assertTrue("if (want) watch(a) else { unwatchAll(); apply(plan.onMedia(null," in rec)
        assertTrue("MediaBridge(a).watchAll(main) { evaluate(a) }" in SourceRoots.body(strip, "private fun watch(a: Context)"))
        assertTrue("ClusterOverlayDisplays.resolve(dm)" in strip, "display = cụm sống, không hằng 1")
        assertTrue("TYPE_APPLICATION_OVERLAY" in strip && "FLAG_NOT_TOUCHABLE" in strip)
        assertTrue("ClusterOverlayDisplays.addListener(clusterListener)" in strip)
        assertTrue("registerOnSharedPreferenceChangeListener(prefsListener)" in strip, "Cài đặt / đổi hồ sơ có hiệu lực ngay")
        listOf("scheduleAtFixedRate", "scheduleWithFixedDelay", "sleep(", "\"am ", "\"wm ", "--display").forEach {
            assertFalse(it in strip, "không nhịp / không lệnh shell: $it")
        }
        val apply = SourceRoots.body(strip, "private fun apply(step: CastNowPlayingPlan.Step)")
        assertTrue("if (!step.changed) return" in apply, "sự kiện trùng ⇒ 0 thao tác cửa sổ")
        // Owner 17/10 "đừng cắt, cho chạy marquee all": chữ đầy đủ tới view, marquee chuẩn của TextView, chỉ chạy khi đang phát.
        assertTrue("v.text = CastNowPlayingText.display(c)" in strip)
        assertTrue("TextUtils.TruncateAt.MARQUEE" in strip && "marqueeRepeatLimit = -1" in strip && "setSingleLine(true)" in strip)
        assertFalse("TruncateAt.END" in strip, "không cắt đuôi")
        assertTrue("v.marqueeWanted = c.playing" in strip, "dừng nhạc ⇒ chữ đứng yên")
        val mb = code("launcher/MediaBridge.kt")
        assertTrue("fun watchAll(handler: Handler, onEvent: () -> Unit): (() -> Unit)? =" in mb)
        assertTrue("CastNowPlayingMedia(c.title(), c.artist(), c.playing())" in mb)
    }

    @Test fun `cai dat - cau - prefs, cung khoa voi lop phu`() {
        val rows = code("launcher/SettingsNowPlayingRows.kt")
        assertTrue("bridge().setCastNowPlayingEnabled(on)" in rows && "bridge().setCastNowPlayingCorner(" in rows)
        assertTrue("nowPlaying.build(body)" in code("launcher/SettingsSectionsNav.kt"))
        val prefs = code("PrefsBadge.kt")
        assertTrue("K_CAST_NOW_PLAYING = \"cast_now_playing\"" in prefs && "K_CAST_NOW_PLAYING_CORNER = \"cast_now_playing_corner\"" in prefs)
        assertTrue("key == K_CAST_NOW_PLAYING || key == K_CAST_NOW_PLAYING_CORNER" in strip)
    }
}
