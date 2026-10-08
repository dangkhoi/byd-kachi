package com.byd.clusternav.perf

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · R6 follow-up D/E/F/G/H — dây nối (CLAUDE.md §8: hàm mới phải có call site thật). Luật thuần ở `:core`:
 * `ShellLogGateTest` · `CastPrefsPruneTest` · `FileByteBudgetTest` · `UpdateApkSweepTest`.
 * Bằng chứng từng mục: docs/diagnostics/perf-inventory-2026-10-08.md §4.
 */
class PerfR6FollowupWiringContractTest {

    private fun code(p: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$p")

    /** D — lệnh chỉ-đọc qua cổng; lệnh đổi trạng thái vẫn log `shell:` TRƯỚC khi chạy (pháp y), lỗi vẫn log. */
    @Test
    fun `D lenh chi-doc qua ShellLogGate, lenh doi trang thai log du`() {
        val shell = SourceRoots.body(code("modules/clustercast/simplified/SimpleCastRuntime.kt"), "private class DadbSimpleCastShell")
        assertTrue(shell.contains("val readOnly = ShellLogGate.isReadOnly(command)"))
        assertTrue(shell.contains("if (!readOnly) android.util.Log.i(\"SimpleCast\", \"shell: \$command\")"))
        assertTrue(shell.contains("READ_LOG_GATE.decide(command, result.exitCode, result.stdout, result.stderr"))
        assertTrue(shell.contains("\"shell FAIL: command=\$command"), "ngoại lệ vẫn log kèm lệnh")
        assertTrue(shell.contains("logPlacementEvidence(command, shellResult)"), "bằng chứng đặt app giữ nguyên")
        // Lỗi (exit ≠ 0) của lệnh chỉ-đọc lên mức W ⇒ KachiLog không tiết chế, xả ngay.
        assertTrue(shell.contains("android.util.Log.w(\"SimpleCast\", line)"))
    }

    /** F — hai writer CSV chẩn đoán có trần từng tệp, ghi nhận byte sau mỗi dòng. */
    @Test
    fun `F nav notif csv xoay theo FileByteBudget`() {
        listOf("NavNotifLog.kt", "NavNotifRawLog.kt").forEach { f ->
            val src = code(f)
            assertTrue(src.contains("private val budget = FileByteBudget()"), f)
            assertTrue(src.contains("budget.mustRotateBefore(rowBytes)"), f)
            assertTrue(src.contains("budget.startFile("), f)
            assertTrue(src.contains("budget.record(n)"), f)
            assertTrue(src.contains("ensureLocked(ctx, n)"), f)
            assertTrue(src.contains("old.close()"), "$f: tệp cũ phải được đóng khi xoay")
        }
    }

    /** E/G/H — một lượt dọn mỗi tiến trình, cài ở Application TRƯỚC dòng chốt cuối `EarlyShellChannel.start`. */
    @Test
    fun `EGH don mot lan moi tien trinh tu KachiApplication`() {
        val app = code("KachiApplication.kt")
        val at = app.indexOf("com.byd.clusternav.housekeeping.StartupHousekeeping.install(this)")
        val gate = app.indexOf("if (isBackgroundVoiceProcess()) return")
        val last = app.indexOf("EarlyShellChannel.start(this)")
        assertTrue(at > gate, "chỉ tiến trình launcher (sau cổng :tts/:wake)")
        assertTrue(at in 0 until last, "trước dòng chốt cuối EarlyShellChannel.start")

        val hk = code("housekeeping/StartupHousekeeping.kt")
        assertTrue(hk.contains("started.compareAndSet(false, true)"), "một lần mỗi tiến trình")
        assertTrue(hk.contains("Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)"))
        assertTrue(hk.contains("step(\"diag-cap\") { DiagStorageCap.enforce(app) }"), "G")
        assertTrue(hk.contains("step(\"ota-apk\") { UpdateApkHousekeeping.sweep(app) }"), "H")
        assertTrue(hk.contains("step(\"cast-prefs\") { CastPrefsHousekeeping.prune(app) }"), "E")
        assertFalse(Regex("""scheduleAtFixedRate|postDelayed|AlarmManager""").containsMatchIn(hk), "không nhịp định kỳ mới")
    }

    /** H — dọn APK OTA theo phán quyết thuần; cùng thư mục với đường tải. */
    @Test
    fun `H xoa APK OTA theo UpdateApkSweep`() {
        val sweep = code("housekeeping/UpdateApkHousekeeping.kt")
        assertTrue(sweep.contains("File(app.filesDir, UpdateChecker.UPDATE_DIR)"))
        assertTrue(sweep.contains("UpdateApkSweep.verdict("))
        assertTrue(sweep.contains("PackageQueries.archiveInfo(pm, f.absolutePath)"))
        assertTrue(sweep.contains("PackageQueries.packageInfo(pm, app.packageName)?.longVersionCode"))
        assertTrue(code("UpdateChecker.kt").contains("File(ctx.applicationContext.filesDir, UPDATE_DIR)"), "một nguồn tên thư mục")
    }

    /** E — fail-safe: chỉ NameNotFound là "không cài"; lỗi khác ⇒ null ⇒ không gỡ gì; tính cả gói gỡ-giữ-dữ-liệu. */
    @Test
    fun `E go khoa chieu qua CastPrefsPrune, fail-safe`() {
        val e = code("housekeeping/CastPrefsHousekeeping.kt")
        assertTrue(e.contains("PackageQueries.packageInfo(pm, p, PackageManager.MATCH_UNINSTALLED_PACKAGES) != null"))
        assertTrue(e.contains("catch (e: RuntimeException)"))
        assertTrue(e.contains("if (plan.aborted) return"))
        assertTrue(e.contains("listOf(ProfileScopeCluster.SIMPLE_CAST_FILE, ProfileScopeCluster.CAST_CATALOG_FILE)"))
    }
}
