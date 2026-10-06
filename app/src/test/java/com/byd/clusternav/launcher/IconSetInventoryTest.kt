package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        LauncherActions.placeable.forEach { a -> n++; resolves(a.icon)?.let { bad += "hành động ${a.id}: $it" } }
        // Sàn 150 → 120 sau UX-OVERHAUL WP8 2026-09-20 (purge 29 datum + 8 nút + 1 nhóm ⇒ [ĐO] 136 mục).
        // → 112 sau lượt gỡ 7 datum CHẾT 2026-09-25 ([ĐO] 119 mục). Sàn = số thật trừ ~6 %, để bắt "đọc hụt
        // registry" mà không đỏ vì một lượt xoá có chủ ý.
        assertTrue(n >= 112) { "đọc hụt registry (thấy $n mục)" }
        assertEquals(emptyList<String>(), bad, "id có trong registry mà tra ra 0 = ô trống icon, KHÔNG lỗi gì")
    }

    /**
     * UX8 — **hình theo TRẠNG THÁI** ([CapabilityIcons.stateIconTable]) là một đường gán icon THỨ HAI, và bài trên
     * không với tới nó: nó chỉ hỏi [CapabilityIcons.forTelemetry] (hình *khái niệm*). Một chế độ gõ sai tên hình
     * ⇒ `iconRes` trả 0 ⇒ chip **mất icon** đúng lúc chế độ ấy đang chạy trên xe, và **không lỗi gì** — đúng loại
     * im lặng mà cả hai chiều của bài này sinh ra để chặn.
     */
    @Test
    fun `moi hinh theo trang thai cua datum hai che do tra ra tep that`() {
        val table = CapabilityIcons.stateIconTable()
        assertTrue(table.isNotEmpty()) { "bảng hình theo trạng thái rỗng — lượt UX8 đã bị gỡ mất?" }
        val bad = table.flatMap { (id, s) ->
            (s.icons.entries.map { (k, ic) -> "$id[mã $k]" to ic } + ("$id[chưa đọc]" to s.unknown))
                .mapNotNull { (where, ic) -> resolves(ic)?.let { "$where: $it" } }
        }
        assertEquals(emptyList<String>(), bad, "tên hình trạng thái không tra ra drawable ⇒ chip mất icon, im lặng")
    }

    /**
     * 2.76 (R8/R9) — mỗi cặp hình theo trạng thái phải là **hai tệp khác nhau về NỘI DUNG**, không chỉ khác tên: hai
     * dòng bảng tra trỏ vào hai tệp y hệt (chép nhầm khi sinh) là chip "đổi hình" mà mắt không thấy gì đổi.
     */
    @Test
    fun `hai trang thai cua mot datum la hai hinh khac nhau ve noi dung`() {
        val dir = SourceRoots.path("src/main/res/drawable")
        fun body(icon: String) = Files.readString(dir.resolve(mapped.getValue(icon) + ".xml")).substringAfter("-->")
        CapabilityIcons.stateIconTable().forEach { (id, s) ->
            val bodies = s.icons.values.map(::body)
            assertEquals(bodies.size, bodies.toSet().size, "$id: hai trạng thái vẽ CÙNG một hình (${s.icons.values})")
        }
    }

    /**
     * 2.76 (R9, owner: *"ghế lái/phụ chỉ khác nhau bởi lật gương"*) — ghế LÁI mang **một dấu thêm** (chấm vô-lăng)
     * so với ghế PHỤ ⇒ tệp sinh của ghế lái có đúng **+1 path** so với bản ghế phụ cùng chế độ, và `ic_seat_left` nay
     * là icon SINH (không còn `<group scaleX=-1>` vá tay).
     */
    @Test
    fun `ghe lai mang dau vo-lang - hon ghe phu dung mot path, va ic_seat_left la icon sinh`() {
        val dir = SourceRoots.path("src/main/res/drawable")
        fun paths(name: String) = Regex("<path\\b").findAll(Files.readString(dir.resolve("$name.xml"))).count()
        listOf("ic_seat_heat" to "ic_seat_heat", "ic_seat_vent" to "ic_seat_vent").forEach { (l, r) ->
            assertEquals(paths("${r}_right") + 1, paths("${l}_left"), "$l: ghế lái phải có đúng một dấu thêm")
            // 2.76 L7: bản MỨC 1 (`_1`) của cùng họ giữ đúng quy ước — ghế lái vẫn hơn ghế phụ một chấm.
            assertEquals(paths("${r}_right_1") + 1, paths("${l}_left_1"), "${l}_1: ghế lái mức 1 cũng mang chấm vô-lăng")
        }
        assertEquals(paths("ic_seat") + 1, paths("ic_seat_left"), "ghế lái trơn cũng mang chấm vô-lăng")
        val left = Files.readString(dir.resolve("ic_seat_left.xml"))
        assertTrue(left.contains("SINH BỞI scripts/design/gen-icons.py"), "ic_seat_left phải đi qua đường ống, không vá tay")
        assertFalse(left.contains("<group"), "không còn lật bằng <group scaleX=-1>")
    }

    /**
     * 2.76 L7 — **hình theo MỨC** ([CapabilityIcons.levelIconTable]) là đường gán icon THỨ BA (sau khái niệm và trạng
     * thái), cùng lẽ với bài trên: một mức gõ sai tên ⇒ chip/ô nút mất icon đúng lúc ghế đang chạy, im lặng. Mọi mức
     * 1..n × 4 họ (sưởi/mát × lái/phụ) phải tra ra tệp thật, và hai mức là **hai tệp khác nội dung** (không chép nhầm).
     */
    @Test
    fun `moi hinh theo muc cua bon ho ghe tra ra tep that va hai muc la hai hinh khac nhau`() {
        val table = CapabilityIcons.levelIconTable()
        assertEquals(4, table.size) { "bốn họ ghế — bảng mức đã bị gỡ hoặc mọc thêm mà bài này chưa biết" }
        val bad = table.flatMap { (concept, icons) ->
            (icons.byLevel.mapIndexed { i, ic -> "$concept[mức ${i + 1}]" to ic } + ("$concept[khái niệm]" to concept))
                .mapNotNull { (where, ic) -> resolves(ic)?.let { "$where: $it" } }
        }
        assertEquals(emptyList<String>(), bad, "tên hình mức không tra ra drawable ⇒ chip/ô mất icon, im lặng")
        val dir = SourceRoots.path("src/main/res/drawable")
        fun body(icon: String) = Files.readString(dir.resolve(mapped.getValue(icon) + ".xml")).substringAfter("-->")
        table.forEach { (concept, icons) ->
            val bodies = icons.byLevel.map(::body)
            assertEquals(bodies.size, bodies.toSet().size, "$concept: hai mức vẽ CÙNG một hình (${icons.byLevel})")
        }
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
                val code = KotlinSource.stripComments(f.toFile().readText())
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
