package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Hardening 2026-09-25 · audit F14 [P2] — wiring (CLAUDE.md §8): `ClusterNavBridgeCast.restoreCluster` hẹn qua
 * `DelayedGatedRun` (luật thuần ở `:core`, canh bởi `DelayedGatedRunTest`) với gate = công tắc cast; nhánh TẮT và
 * `deepRescue` rút hẹn; không còn `Handler.postDelayed` trần.
 */
class ClusterReopenWiringTest {

    @Test fun `ClusterNavBridgeCast noi day dung cho`() {
        val src = source("app/src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeCast.kt")
        assertTrue(src.contains("internal val clusterReopen = DelayedGatedRun("), "một timer cho cả tiến trình")
        val restore = src.substringAfter("fun ClusterNavBridge.restoreCluster()").substringBefore("internal val CAST_CONFLICT_PACKAGES")
        assertTrue(restore.contains("clusterReopen.schedule(2_000, gate = { castEnabled() })"), "restoreCluster phải hẹn qua clusterReopen với gate = công tắc")
        assertFalse(restore.contains("postDelayed"), "không còn Handler.postDelayed trần trong restoreCluster")
        val off = src.substringAfter("fun ClusterNavBridge.setCastEnabled(").substringAfter("} else {").substringBefore("toast(BridgeMsg.CAST_OFF)")
        assertTrue(off.contains("clusterReopen.cancel()"), "nhánh TẮT phải rút hẹn")
        val rescue = src.substringAfter("fun ClusterNavBridge.deepRescue(").substringBefore("val stopped = CAST_CONFLICT_PACKAGES")
        assertTrue(rescue.contains("clusterReopen.cancel()"), "deepRescue 'KHÔNG mở lại chiếu' phải rút hẹn còn treo")
    }

    /**
     * Review 2.89 Pass 3 · cluster-r2-6 — "Áp ngay" bị cổng theme dừng vì khoảng 15 s ⇒ thử lại ĐÚNG MỘT lần: bộ xem
     * `ThemeGapReopen` (luật thuần, `ThemeGapReopenTest`) gắn TRƯỚC `openProjection()`, lượt thử lại đi qua CHÍNH `clusterReopen`
     * (huỷ được, gác công tắc) và chỉ `openProjection()` — không gắn bộ xem mới (không vòng). Thử ĐỎ: lượt thử lại gọi lại
     * `reopenRetryingThemeGap()`.
     */
    @Test fun `Ap ngay - dung vi khoang 15 s thi mo lai mot lan`() {
        val src = source("app/src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeCast.kt")
        val restore = src.substringAfter("fun ClusterNavBridge.restoreCluster()").substringBefore("private fun ClusterNavBridge.reopenRetryingThemeGap()")
        assertTrue(restore.contains("clusterReopen.schedule(2_000, gate = { castEnabled() }) { reopenRetryingThemeGap() }"))
        val fn = src.substringAfter("private fun ClusterNavBridge.reopenRetryingThemeGap()").substringBefore("@Volatile private var gapWatch")
        val add = fn.indexOf("c.addStateListener(listener)")
        assertTrue(add in 0 until fn.indexOf("c.openProjection()\n"), "bộ xem gắn TRƯỚC lượt mở: $fn")
        assertTrue(fn.contains("ThemeGapReopen(gapMs = { c.themeGapRetryMs() })"))
        assertTrue(fn.contains("clusterReopen.schedule(delay, gate = { castEnabled() }) { c.openProjection() }"), "thử lại = chỉ mở chiếu")
        assertFalse(Regex("""schedule\([^)]*\)[^\n]*reopenRetryingThemeGap""").containsMatchIn(fn), "lượt thử lại không gắn bộ xem mới")
        assertTrue(fn.contains("c.removeStateListener(this)"), "bộ xem gỡ khi xong")
    }

    /**
     * Mã nguồn ĐÃ BỎ chú thích — để một dòng bị comment-out không còn làm bài canh xanh giả (thử-làm-đỏ 2026-09-25).
     * 2.93 wave 2C · TEST-STRIP-COPIES: bộ quét có trạng thái dùng chung [KotlinSource.stripComments] thay bản cắt `//` tay
     * (bỏ cả chú thích khối; `//` nằm trong chuỗi không còn cắt mất phần mã phía sau trên cùng dòng).
     */
    private fun source(rel: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val text = listOf(cwd.resolve(rel), cwd.resolve("../$rel"), cwd.resolve(rel.removePrefix("app/"))).first { it.isFile }.readText()
        return KotlinSource.stripComments(text)
    }
}
