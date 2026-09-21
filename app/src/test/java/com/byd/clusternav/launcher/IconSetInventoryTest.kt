package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VISUAL-REFRESH P2 · AC6.2 — ĐI TỪ REGISTRY VÀO: mọi id có hình phải tra ra một drawable ≠ 0 ═══════════════
 *
 * `IconStyleContractTest` mục 10 đi **từ bảng tra ra** (mọi tên `ic-…` trong mã có dòng). Bài này đi **từ registry
 * vào**: với MỌI mục của `TelemetryRegistry ∪ ControlRegistry ∪ WidgetRegistry ∪ CapabilityGroups ∪ ActionMacros ∪
 * LauncherActions`, tên icon mà `:core` gán (kể cả đường lùi theo lĩnh vực của [CapabilityIcons]/[WidgetCatalog]) phải
 * có dòng trong `KachiTheme.iconRes` **và** dòng đó trỏ vào tệp thật. Hai chiều bắt hai loại lỗi khác nhau: một
 * datum mới gán `"ic-tyre"` (gõ thiếu chữ) thì mục 10 không thấy (vì tên đó chưa được "dùng" ở :app) — bài này thấy.
 *
 * `iconRes` cần `R.drawable` (Android) nên off-car ta canh bằng chính bảng nguồn + tệp có thật, cùng lối mục 9/10.
 */
class IconSetInventoryTest {

    private val table by lazy { SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiTheme.kt") }
    private val mapped: Map<String, String> by lazy {
        Regex("\"(ic-[a-z0-9-]+)\"\\s*->\\s*R\\.drawable\\.(\\w+)").findAll(table).associate { it.groupValues[1] to it.groupValues[2] }
    }
    private val files: Set<String> by lazy {
        Files.list(SourceRoots.path("src/main/res/drawable")).use { s -> s.map { it.fileName.toString().removeSuffix(".xml") }.toList().toSet() }
    }

    private fun resolves(icon: String): String? {
        val d = mapped[icon] ?: return "$icon: không có dòng trong KachiTheme.iconRes"
        return if (d in files) null else "$icon → $d.xml không tồn tại"
    }

    @Test
    fun `moi datum nut widget nhom macro va hanh dong launcher deu co hinh that`() {
        val bad = mutableListOf<String>()
        var n = 0
        TelemetryRegistry.ALL.forEach { t -> n++; resolves(CapabilityIcons.forTelemetry(t.id, t.domain))?.let { bad += "datum ${t.id}: $it" } }
        ControlRegistry.ALL.forEach { c -> n++; resolves(c.icon)?.let { bad += "nút ${c.id}: $it" } }
        WidgetRegistry.ALL.forEach { w -> n++; resolves(w.icon)?.let { bad += "widget ${w.id}: $it" } }
        CapabilityGroups.ALL.forEach { g -> n++; resolves(g.icon)?.let { bad += "nhóm ${g.id}: $it" } }
        ActionMacros.ALL.forEach { m -> n++; resolves(m.icon)?.let { bad += "macro ${m.id}: $it" } }
        LauncherActions.ALL.forEach { a -> n++; resolves(a.icon)?.let { bad += "hành động ${a.id}: $it" } }
        // Sàn 150 → 120 sau UX-OVERHAUL WP8 2026-09-20 (purge 29 datum + 8 nút + 1 nhóm ⇒ [ĐO] 136 mục).
        assertTrue(n >= 120) { "đọc hụt registry (thấy $n mục)" }
        assertEquals(emptyList<String>(), bad, "id có trong registry mà tra ra 0 = ô trống icon, KHÔNG lỗi gì")
    }

    /** Đường lùi theo lĩnh vực ([WidgetCatalog.iconFor]) cũng phải có hình — đó là lưới cuối của mọi id chưa gán icon. */
    @Test
    fun `icon dai dien cua moi linh vuc tra ra tep that`() {
        val bad = Domain.values().mapNotNull { d -> resolves(WidgetCatalog.iconFor(d))?.let { "$d: $it" } }
        assertEquals(emptyList<String>(), bad)
    }

    /**
     * P2 · AC2.6 — icon của một ô CHỌN phải mang trạng thái chọn của chính nó.
     *
     * ⚠ [SOÁT 2026-09-17] `PickerBadge.icon(…, selected = false)` là mặc định, và `KachiIcons.tint` ở bảng TỐI cỡ
     * ≥ [KachiSpace.ICON_L] áp bộ lọc *chưa chọn* (bão hoà 35 % · mờ 72 %). Một chỗ gọi để mặc định mà **không**
     * gọi `PickerBadge.retint` sau đó ⇒ ô đang bật vẫn mang icon mờ, và lưới dựng lại sau mỗi cú bấm nên nó không
     * bao giờ tự đúng lại ([ĐO] `TopStripPicker` — lưới 88 ô, đúng chỗ cần phân biệt nhất). Bài này canh NGUYÊN
     * NHÂN: mỗi tệp gọi `PickerBadge.icon(` phải hoặc truyền đối số thứ năm, hoặc có `PickerBadge.retint(`.
     */
    @Test
    fun `moi o chon truyen trang thai chon cho icon cua no`() {
        val bad = mutableListOf<String>()
        var callers = 0
        Files.list(SourceRoots.path("src/main/java/com/byd/clusternav/launcher")).use { s ->
            s.filter { it.toString().endsWith(".kt") }.forEach { f ->
                val name = f.fileName.toString()
                if (name == "PickerBadge.kt") return@forEach
                val code = f.toFile().readText().lines().joinToString("\n") { it.substringBefore("//") }
                Regex("""PickerBadge\.icon\(([^\n]*)\)""").findAll(code).forEach { m ->
                    callers++
                    val args = m.groupValues[1].split(',').size
                    if (args < 5 && "PickerBadge.retint(" !in code) {
                        bad += "$name: PickerBadge.icon($args đối số) mà không có retint ⇒ ô đang bật mang icon mờ"
                    }
                }
            }
        }
        assertTrue(callers >= 2) { "đọc hụt chỗ gọi PickerBadge.icon (thấy $callers)" }
        assertEquals(emptyList<String>(), bad, "AC2.6: chọn/không-chọn phải nhìn ra được: $bad")
    }
}
