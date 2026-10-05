package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Review 2.89 Pass 1 · safety-5 (spec `docs/specs/kachi-289-field-fixes.html` §R1) — DÂY NỐI gỡ `ClusterBlack` TRONG tiến trình.
 *
 * Lỗi khoá ở đây [ĐO nguồn AOSP fetch 05/10, trích ở KDoc `ClusterThemeGuard`]: `am stack remove` = `removeTaskByIdLocked(…,
 * killProcess = true)` ⇒ `cleanUpRemovedTaskLocked` duyệt MỌI tiến trình của gói, chỉ tha `mHomeProcess` ⇒ `:tts` (không tiền
 * cảnh, không activity) bị giết mỗi lần tắt chiếu / gỡ placeholder trước theme [SUY mạnh, chưa đo trên xe]. Đường trong tiến
 * trình (`finishAndRemoveTask` ⇒ `killProcess = false`) không giết gì. Phần quyết ở `:core` (`ClusterThemeGuardTest`); `:app`
 * không có Robolectric ⇒ bài này canh MÃ (đã bỏ chú thích) cho mắt xích bài thuần không thấy, và có call site (CLAUDE.md §8).
 */
class ClusterBlackInProcessRemovalContractTest {

    private val black by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/ClusterBlackActivity.kt") }
    private val runtime by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt") }
    private val guard by lazy { SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/ClusterThemeGuard.kt") }
    private val coord by lazy { SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/SimpleCastCoordinator.kt") }

    private fun order(src: String, vararg parts: String) {
        var at = -1
        parts.forEach { p ->
            val i = src.indexOf(p, at + 1)
            assertTrue(i > at, "thứ tự sai / thiếu '$p' trong: ${src.take(1200)}")
            at = i
        }
    }

    @Test
    fun `so thuc the song - them o onCreate, bot o onDestroy`() {
        order(SourceRoots.body(black, "override fun onCreate(savedInstanceState: Bundle?) {"), "super.onCreate(savedInstanceState)", "LIVE.add(this)")
        order(SourceRoots.body(black, "override fun onDestroy() {"), "LIVE.remove(this)", "super.onDestroy()")
    }

    @Test
    fun `finishOwn - chi task duoc hoi, finishAndRemoveTask tren luong chinh, khong bao gio am stack remove`() {
        val fn = SourceRoots.body(black, "fun finishOwn(taskIds: Set<Int>): Set<Int> {")
        order(fn, "if (taskIds.isEmpty() || LIVE.isEmpty()) return emptySet()", "state.compareAndSet(PENDING, RUNNING)",
            "for (a in LIVE)", "if (id !in taskIds) continue", "a.finishAndRemoveTask()", "done += id", "latch.countDown()",
            "MAIN.post(work)", "latch.await(FINISH_WAIT_MS, TimeUnit.MILLISECONDS)", "state.compareAndSet(PENDING, CANCELLED)")
        assertFalse(fn.contains("am stack") || fn.contains("Runtime.getRuntime") || fn.contains("killProcess"), "đường trong tiến trình không chạy shell")
        assertFalse(fn.contains(".finish()"), "phải gỡ CẢ task (finishAndRemoveTask), không chỉ activity")
    }

    @Test
    fun `runtime cap cong trong tien trinh, coordinator chuyen cho bo thi hanh cong theme`() {
        order(runtime, "val coordinator = SimpleCastCoordinator(",
            "ownPlaceholder = { ids -> com.byd.clusternav.modules.clustercast.ClusterBlackActivity.finishOwn(ids) }", "return coordinator")
        // B1a: lời gọi dựng guard có thêm sổ theme / đồng hồ / cờ mức B (nhiều dòng) — vẫn phải chuyển ownPlaceholder.
        assertTrue(Regex("""ClusterThemeGuard\(\s*shell,\s*selfPackage,\s*ownPlaceholder,\s*sleepMs = detectSleepMs,""").containsMatchIn(coord),
            "coordinator phải chuyển cổng cho ClusterThemeGuard")
        assertEquals(1, Regex(Regex.escape("ClusterBlackActivity.finishOwn(")).findAll(runtime).count(), "một call site production")
    }

    @Test
    fun `bo thi hanh - hoi trong tien trinh TRUOC, shell chi cho phan con lai (mo coi), sau rao admissible`() {
        val fn = SourceRoots.body(guard, "private fun removeStacks(ids: List<Int>, tasks: List<StackEntry>, vds: Set<Int>): List<Int> {")
        order(fn, "ClusterThemePlan.admissible(id, tasks, vds, selfPackage)", "own.finish(taskIds).containsAll(taskIds)", "continue",
            "FloatingOrphanPlan.removeCmd(id)")
    }
}
