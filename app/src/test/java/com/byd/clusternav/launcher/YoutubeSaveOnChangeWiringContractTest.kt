package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.97 · YT-SAVE-ON-CHANGE — dây nối: bên lưu nghe sự kiện đổi bài (`MediaController.Callback.onMetadataChanged`) của đúng gói
 * app đích, bám lại khi danh sách phiên đổi, rồi lưu qua CHÍNH `tick` (cùng cổng giữ/đang phát/hồ sơ) sau `CHANGE_SETTLE_MS`.
 * Thử ĐỎ: bỏ `watchChanges(app)` trong `install`.
 */
class YoutubeSaveOnChangeWiringContractTest {

    private val sampler = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/trip/YoutubeResumeSampler.kt")
    private val bridge = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/MediaBridge.kt")

    @Test
    fun `install noi bo nghe doi bai, luu qua tick sau nhip ngan`() {
        assertTrue(SourceRoots.body(sampler, "fun install(ctx: Context)").contains("watchChanges(app)"))
        val watch = SourceRoots.body(sampler, "private fun watchChanges(app: Context)")
        assertTrue(watch.contains("b.watchPackages(YoutubeResume.watchedPackages(), Handler(t.looper), onPlayback = { guarded { lateSeek() } })"), watch)
        assertTrue(watch.contains("YoutubeResume.CHANGE_SETTLE_MS"), watch)
        assertTrue(watch.contains("guarded { tick(app) }"), "đi qua CHÍNH tick — không đường lưu thứ hai")
        assertTrue(watch.contains("changePending.compareAndSet(false, true)"), "gộp nhiều lần đổi liền nhau")
    }

    @Test
    fun `MediaBridge bam theo doi metadata va tu bam lai`() {
        val w = SourceRoots.body(bridge, "fun watchPackages(pkgs: Set<String>, handler: Handler, onPlayback: () -> Unit = {}, onChange: () -> Unit)")
        assertTrue(w.contains("override fun onMetadataChanged(metadata: MediaMetadata?) = onChange()"), w)
        assertTrue(w.contains("addOnActiveSessionsChangedListener(listener, comp, handler)"), w)
        assertTrue(w.contains("it.packageName in pkgs"), "chỉ đúng gói app đích")
        assertTrue(w.contains("catch (e: SecurityException)"), "không quyền ⇒ không nổ, còn nhịp định kỳ")
    }
}
