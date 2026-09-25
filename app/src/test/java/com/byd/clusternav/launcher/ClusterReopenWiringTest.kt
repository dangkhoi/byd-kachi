package com.byd.clusternav.launcher

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

    /** Mã nguồn ĐÃ BỎ comment `//` — để một dòng bị comment-out không còn làm bài canh xanh giả (thử-làm-đỏ 2026-09-25). */
    private fun source(rel: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val text = listOf(cwd.resolve(rel), cwd.resolve("../$rel"), cwd.resolve(rel.removePrefix("app/"))).first { it.isFile }.readText()
        return text.lines().joinToString("\n") { it.substringBefore("//") }
    }
}
