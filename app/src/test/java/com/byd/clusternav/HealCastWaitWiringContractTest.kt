package com.byd.clusternav

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · READY-RESTART-MID-CAST + CAST-OPEN-TIMEOUT — DÂY NỐI phía `:app` ═══════════════════════════════════════════════
 *
 * Luật thuần ở `HealCastDeferralTest` · `CastRestartHazardTest` · `ClusterThemeBudgetTest` (`:core`). Bài này khoá phần chỉ Android
 * có: lượt chữa phím hỏi chiếu cụm ĐÚNG chỗ (sau KẸT BỀN, trước nấc leo), không dựng coordinator chỉ để hỏi, đi qua MỘT cửa; và
 * lưới an toàn "trả cụm" bám theo hạn RIÊNG của lượt mở.
 *
 * Lỗi khoá [ĐO log xe 06/10]: 15:17:27.751 `theme 31 → SEND` · 29.855 `16` · 30.725 `keys=STUCK(tat-may)->ESCALATE` · 31.392
 * `->RESTARTING` ⇒ tiến trình mới `TOO_SOON` ⇒ phiên "chưa rõ kiểu".
 */
class HealCastWaitWiringContractTest {

    private val heal = SourceRoots.codeOf("src/main/java/com/byd/clusternav/A11yLifecycleHeal.kt")
    private val wait = SourceRoots.codeOf("src/main/java/com/byd/clusternav/HealCastWait.kt")
    private val runtime = SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/simplified/SimpleCastRuntime.kt")
    private val layers = SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/ClusterLayerExecutor.kt")

    private fun order(src: String, vararg tokens: String) {
        val at = tokens.map { t -> src.indexOf(t).also { assertTrue(it >= 0, "thiếu '$t' trong vùng quét") } }
        assertEquals(at.sorted(), at, "thứ tự sai: ${tokens.joinToString(" → ")}")
    }

    /**
     * 2.93 wave 2A · HEAL-DEFER-GATE-RECHECK — ĐỔI GHIM có lý do (spec `kachi-293-wave2a.html` §4.5): lượt chờ và nấc leo nay đi
     * CHUNG một cửa `HealCastWait.escalate` — cổng cuối của nấc leo (trao vào dạng `gate`) hỏi lại mối nguy ở lượt tắt-máy; thứ tự
     * SAU kẹt bền → dòng keys ESCALATE → nấc leo → dòng keys kết quả GIỮ NGUYÊN; pha qua trong lúc chờ ⇒ `null` ⇒ CẮT (return true).
     */
    @Test
    fun `luot chua phim hoi chieu cum SAU ket ben, TRUOC nac leo - pha qua thi CAT nhu nhanh cho do lai`() {
        val fn = SourceRoots.body(heal, "private fun healIfStuck(")
        order(
            fn,
            "AccessibilityHealGates.stuckPersistent(first, second)",
            "val r = HealCastWait.escalate(phase, screenOnAt, note, { stillInPhase(app, phase, screenOnAt) }) { gate ->",
            "KachiReadyLog.keys(\"STUCK(\$note)->ESCALATE\")",
            "NavConnect.escalateOnLifecycle(app, phase) { gate() }",
            "} ?: return true",
            "KachiReadyLog.keys(\"STUCK(\$note)->\$r\")",
        )
    }

    @Test
    fun `MOT cua duy nhat, va chi luot vong doi di qua no`() {
        val calls = SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        }.sumOf { p -> Regex("HealCastWait\\.(await|escalate)\\(").findAll(KotlinSource.stripComments(p.toFile().readText())).count() }
        assertEquals(1, calls, "chỉ healIfStuck (lớp 1/2/khởi động) được chờ — nút Sửa ngay / watchdog không đổi")
    }

    @Test
    fun `HealCastWait chi doc su that trong tien trinh, dung luat thuan, khong tu leo`() {
        val fn = SourceRoots.body(wait, "fun <R> escalate(phase: HealPhase, anchorAt: Long, note: String, inPhase: () -> Boolean, fire: (gate: () -> Boolean) -> R): R? =")
        order(
            fn,
            "HealCastDeferral.escalate(",
            // Review CAST F1 [P2]: chỉ lượt tắt-máy được chờ — luật ở HealCastDeferral.escalate/waitUntil (HealCastDeferralTest).
            "until = HealCastDeferral.waitUntil(phase, anchorAt, SystemClock.elapsedRealtime())",
            "hazard = { runCatching { SimpleCastRuntime.restartHazard()?.name }.getOrNull() }",
            "fire = fire",
        )
        listOf("SimpleCastRuntime.coordinator(", "LocalDeviceShell", "NavConnect.", "force-stop", "Prefs.set").forEach {
            assertFalse(wait.contains(it), "HealCastWait không được '$it' — chỉ hỏi, chờ, trả lời")
        }
        assertTrue(fn.contains("KachiReadyLog.line(\"keys-defer note=\$note hazard="), "buổi xe phải đọc được lượt chờ thật (🚗)")
        assertTrue(fn.contains("KachiReadyLog.line(\"keys-defer note=\$note gate hazard="), "…và lượt cổng cuối từ chối (🚗)")
    }

    @Test
    fun `hoi moi nguy KHONG dung coordinator neu chua co`() {
        assertTrue(runtime.contains("fun restartHazard(): CastRestartHazard? = instance?.restartHazard()"), runtime)
    }

    /** Bản 2.90 ghi cứng 20 s cho hạn 15 s; hạn lượt mở 25 s mà lưới vẫn 20 s ⇒ nó trả cụm GIỮA một lượt mở còn hợp lệ. */
    @Test
    fun `luoi an toan tra cum bam theo han RIENG cua luot mo`() {
        assertTrue(layers.contains("const val PAUSE_MAX_MS = BoundedCastExecutor.OPEN_TIMEOUT_MS + 5_000L"), layers)
        assertFalse(Regex("PAUSE_MAX_MS\\s*=\\s*\\d").containsMatchIn(layers), "không ghi cứng số — bám hằng của executor")
    }
}
