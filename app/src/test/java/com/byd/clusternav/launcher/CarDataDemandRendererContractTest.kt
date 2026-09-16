package com.byd.clusternav.launcher

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ [SOÁT P1-1 · 2026-09-16] BẢNG NHU CẦU KHÔNG ĐƯỢC LỆCH KHỎI BỘ VẼ ════════════════════════════════════════
 *
 * [CarDataDemand.CURATED] là bảng **chép tay** từ `WidgetViews` — và nó là loại dữ liệu nguy hiểm nhất của dự án:
 * Thiếu một dòng thì **không có gì đỏ**, chỉ có một ô im lặng hiện `"—"` trên xe đang chạy. `CarDataDemandTest`
 * (ở `:core`) canh được *"mã trong bảng có thật không"* và *"widget nào chưa khai"*, nhưng KHÔNG canh được câu
 * đắt nhất: *"bộ vẽ đọc field nào"* — vì bộ vẽ sống ở `:app` và nói bằng **field của [CarStatus]**, còn bảng nói
 * bằng **mã datum**.
 *
 * Bài này bắc cây cầu đó bằng cách đọc chính hai tệp nguồn, không chép lại một dòng nào:
 *  1. `TelemetryReadout.kt` là **chỗ DUY NHẤT** map `mã datum → field [CarStatus]` (KDoc của nó tự nhận vai ấy).
 *     Đọc ngược bảng `when` ở đó ⇒ có `field → mã`.
 *  2. `WidgetViews.kt` — với mỗi nhánh `"w_*"` của `build`/`mini`, đi theo các hàm phụ mà nhánh ấy gọi (trong
 *     cùng tệp) và gom mọi lần chạm `car.<nhóm>.<field>`.
 * Hợp hai thứ lại ⇒ tập mã mà bộ vẽ THẬT SỰ cần. Bảng phải **chứa** tập đó.
 *
 * ## Vì sao "chứa" chứ không "bằng"
 * Đọc dư là mất một lượt binder; đọc thiếu là một ô câm mà không ai hiểu vì sao — hai hậu quả không cùng hạng,
 * nên bài canh đúng một chiều. Dư nhiều thì bảng số ở `docs/diagnostics/perf-profile-*` nói ra, không cần bài test.
 *
 * ## Vì sao đọc NGUỒN chứ không dựng view thật
 * Dựng view cần Robolectric + một `Context`, và nó chỉ chứng minh được *"ô này vẽ ra chữ gì"* chứ không phải
 * *"ô này ĐỌC field nào"* — hai câu khác nhau (một field đọc rồi vứt vẫn là một lượt binder). Đọc nguồn trả lời
 * đúng câu đang hỏi, chạy trong mili-giây, và **không thể xanh giả**: mọi bước tra cứu hụt đều `fail` ngay
 * (xem các `assertTrue` có chữ *"không tìm thấy"*), nên một lần đổi tên hàm sẽ làm bài này ĐỎ chứ không làm nó
 * lặng lẽ kiểm một tập rỗng.
 */
class CarDataDemandRendererContractTest {

    // ── Định vị nguồn ───────────────────────────────────────────────────────────────────────────────────
    private val repoRoot: File by lazy {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "settings.gradle.kts").isFile) d = d.parentFile
        assertTrue(d != null, "không tìm thấy gốc repo (settings.gradle.kts) từ ${File("").absolutePath}")
        d!!
    }

    private fun source(rel: String): String {
        val f = File(repoRoot, rel)
        assertTrue(f.isFile, "không tìm thấy tệp nguồn $rel — bài này canh bằng nguồn nên đường dẫn đổi là phải ĐỎ")
        return f.readText()
    }

    // ── 1. field CarStatus → mã datum, đọc ngược từ TelemetryReadout ─────────────────────────────────────
    /** `"soc" -> s.energy.soc?.toString()` ⇒ `energy.soc` → `soc`. */
    private val fieldToId: Map<String, String> by lazy {
        val re = Regex("""^\s*"([a-z0-9_]+)"\s*->\s*s\.(\w+)\.(\w+)""", RegexOption.MULTILINE)
        val m = re.findAll(source("core/src/main/kotlin/com/byd/clusternav/launcher/TelemetryReadout.kt"))
            .associate { "${it.groupValues[2]}.${it.groupValues[3]}" to it.groupValues[1] }
        assertTrue(m.size > 100, "đọc ngược TelemetryReadout chỉ ra ${m.size} field — dạng bảng `when` đã đổi?")
        m
    }

    /** `energy` → mọi mã của nhóm field ấy; dùng khi bộ vẽ chuyền **cả cụm** (vd `TyreBoard.readings(car.tyres)`). */
    private val idsByGroup: Map<String, Set<String>> by lazy {
        fieldToId.entries.groupBy({ it.key.substringBefore('.') }, { it.value }).mapValues { it.value.toSet() }
    }

    // ── 2. Bộ vẽ đọc field nào ──────────────────────────────────────────────────────────────────────────
    private val widgetViews: String by lazy {
        source("app/src/main/java/com/byd/clusternav/launcher/WidgetViews.kt")
    }

    /** Dòng mã đã bỏ chú thích `//` — giữ phép đếm ngoặc khỏi bị một dấu `{` trong câu chữ làm lệch. */
    private fun code(line: String) = line.substringBefore("//")

    /**
     * Thân hàm [name] trong [widgetViews].
     *
     * Hai dạng thân đều có thật trong tệp: `fun f(...) { … }` (đếm ngoặc) và `fun f(...) = <một biểu thức>`
     * (không có ngoặc nào ⇒ thân là chính dòng ấy). `= col(ctx).apply {` rơi vào dạng đầu vì có `{`.
     */
    private fun body(name: String): String? {
        val lines = widgetViews.lines()
        val start = lines.indexOfFirst { Regex("""\bfun\s+$name\s*\(""").containsMatchIn(code(it)) }
        if (start < 0) return null
        val first = code(lines[start])
        if (!first.contains('{')) return first
        val sb = StringBuilder()
        var depth = 0
        for (i in start until lines.size) {
            val c = code(lines[i])
            sb.append(c).append('\n')
            depth += c.count { it == '{' } - c.count { it == '}' }
            if (i > start && depth <= 0) break
            if (i == start && depth <= 0) break
        }
        return sb.toString()
    }

    /** Nhánh `"w_xxx" -> <biểu thức>` của một `when` (mỗi nhánh nằm gọn một dòng trong tệp này). */
    private fun branch(widgetId: String): List<String> =
        widgetViews.lines().map { code(it) }
            .filter { Regex("""^\s*"$widgetId"\s*(->|\s+->)""").containsMatchIn(it) }
            .map { it.substringAfter("->") }

    private val carAccess = Regex("""\bcar\.(\w+)(?:\.(\w+))?""")

    /**
     * Lời gọi hàm **cùng tệp**: tên không được đứng sau dấu chấm.
     *
     * ⚠ Lookbehind `(?<![.\w])` là phần đắt nhất của biểu thức này, và nó ĐÃ bắt lỗi thật khi bài này chạy lần
     * đầu: `MediaWidgetView.build(ctx, data)` khớp `build(` ⇒ bài đi thẳng vào [WidgetViews.build] — tức cái
     * `when` phân phối — và quy MỌI datum của MỌI widget cho `w_media`. Một bài test đọc nguồn mà trèo ngược
     * vào bộ phân phối thì nó không còn đo widget nào cả.
     */
    private val callee = Regex("""(?<![.\w])([a-z]\w*)\s*\(""")

    /** Hai `when` phân phối — đi vào là gom nhu cầu của mọi widget về một widget (xem ⚠ ở [callee]). */
    private val dispatchers = setOf("build", "mini", "buildGrid", "refreshRead")

    /** Mã datum mà đoạn mã [snippet] chạm tới, đi tiếp vào các hàm phụ cùng tệp (sâu tối đa [depth]). */
    private fun idsOf(snippet: String, depth: Int, seen: MutableSet<String>): MutableSet<String> {
        val out = mutableSetOf<String>()
        carAccess.findAll(snippet).forEach { m ->
            val group = m.groupValues[1]
            val field = m.groupValues[2]
            if (field.isEmpty()) {
                // Cả cụm được chuyền đi (vd `TyreBoard.readings(car.tyres)`) ⇒ coi như cần MỌI mã của cụm: từ
                // đây trở đi quyết định nằm trong một lớp khác, và đoán nó đọc field nào là đúng kiểu suy diễn
                // mà CLAUDE.md §2 cấm.
                idsByGroup[group]?.let { out += it }
            } else {
                fieldToId["$group.$field"]?.let { out += it }
            }
        }
        if (depth > 0) {
            callee.findAll(snippet).map { it.groupValues[1] }
                .filter { it !in dispatchers && seen.add(it) }
                .forEach { fn ->
                body(fn)?.let { out += idsOf(it, depth - 1, seen) }
            }
        }
        return out
    }

    private fun renderedIds(widgetId: String): Set<String> {
        val entry = branch(widgetId)
        assertTrue(entry.isNotEmpty(), "không tìm thấy nhánh \"$widgetId\" trong WidgetViews (đổi tên bộ vẽ?)")
        val out = mutableSetOf<String>()
        entry.forEach { out += idsOf(it, depth = 4, seen = mutableSetOf()) }
        return out
    }

    // ── Bài canh ────────────────────────────────────────────────────────────────────────────────────────

    /**
     * ⚠ Bài ĐẮT NHẤT của tệp này. Đỏ ở đây = một ô sắp câm trên xe, không phải một chuyện văn vẻ.
     *
     * Cách chữa khi đỏ: thêm mã còn thiếu vào [CarDataDemand.CURATED] cho đúng widget ấy — **không** nới bài test.
     */
    @Test
    fun `CURATED chua du moi datum ma bo ve doc`() {
        val missing = mutableListOf<String>()
        var covered = 0
        CarDataDemand.CURATED.forEach { (widgetId, declared) ->
            val rendered = renderedIds(widgetId)
            if (rendered.isNotEmpty()) covered++
            (rendered - declared).forEach { missing += "$widgetId missing '$it'" }
        }
        assertTrue(
            missing.isEmpty(),
            "bảng nhu cầu lệch khỏi bộ vẽ ⇒ ô sẽ hiện \"—\" trên xe mà không có gì đỏ: $missing",
        )
        // Chốt CHÍNH BÀI TEST: nếu phép đọc nguồn hụt (đổi tên hàm, đổi dạng `when`) thì nó sẽ tìm ra 0 mã và
        // xanh một cách vô nghĩa. Số 6 = 9 widget trừ `w_media`/`w_photos` (không đọc xe) và trừ một chỗ trống
        // cho ngày ai đó thêm một widget thuần cục bộ nữa.
        assertTrue(covered >= 6, "chỉ suy ra được nhu cầu của $covered widget — phép đọc nguồn đã hụt, không phải bảng đúng")
    }

    /**
     * Hai widget khai nhu cầu RỖNG phải thật sự **không đọc gì của xe** — nếu không thì "rỗng" là một lỗ hổng
     * chứ không phải một sự thật. Bộ vẽ của chúng nằm ở tệp khác nên kiểm thẳng tệp ấy.
     */
    @Test
    fun `widget khai rong that su khong doc gi cua xe`() {
        val external = mapOf(
            "w_media" to "app/src/main/java/com/byd/clusternav/launcher/MediaWidgetView.kt",
            "w_photos" to "app/src/main/java/com/byd/clusternav/launcher/PhotoWidgetView.kt",
        )
        external.forEach { (widgetId, path) ->
            assertTrue(
                CarDataDemand.CURATED[widgetId]?.isEmpty() == true,
                "$widgetId nay khai nhu cầu khác rỗng — bài này phải đổi theo, đừng để nó canh nhầm chuyện",
            )
            val src = source(path)
            assertTrue(
                !src.contains("CarStatus") && !carAccess.containsMatchIn(src),
                "$widgetId khai nhu cầu RỖNG nhưng $path có chạm trạng thái xe ⇒ ô sẽ câm vĩnh viễn",
            )
        }
    }

    /**
     * Ba chip TỔNG HỢP cũng là bảng chép tay ([CarDataDemand.CHIPS]) — cùng hiểm hoạ, nguồn thì ở `:core`
     * (`TopStripChips.chip`), nên soi bằng cùng một cách.
     */
    @Test
    fun `CHIPS chua du moi datum ma chip tong hop doc`() {
        val src = source("core/src/main/kotlin/com/byd/clusternav/launcher/TopStrip.kt")
        val access = Regex("""\bstatus\.(\w+)\.(\w+)""")
        val lines = src.lines().map { code(it) }
        CarDataDemand.CHIPS.forEach { (chipConst, declared) ->
            // `TopStripConfig.PM25 -> { … }` — lấy khối của đúng chip ấy trong `when (id)` của `TopStripChips.chip`.
            val name = when (chipConst) {
                TopStripConfig.PM25 -> "PM25"
                TopStripConfig.TEMP -> "TEMP"
                TopStripConfig.ENERGY -> "ENERGY"
                else -> error("chip lạ $chipConst")
            }
            val start = lines.indexOfFirst { it.contains("TopStripConfig.$name ->") }
            assertTrue(start >= 0, "không tìm thấy nhánh chip $name trong TopStripChips")
            var depth = 0
            val block = StringBuilder()
            for (i in start until lines.size) {
                block.append(lines[i]).append('\n')
                depth += lines[i].count { it == '{' } - lines[i].count { it == '}' }
                if (i > start && depth <= 0) break
            }
            val rendered = access.findAll(block).mapNotNull { fieldToId["${it.groupValues[1]}.${it.groupValues[2]}"] }.toSet()
            assertTrue(
                declared.containsAll(rendered),
                "chip $name đọc ${rendered - declared} mà bảng CHIPS không khai ⇒ chip hiện \"—\" một nửa",
            )
            assertTrue(rendered.isNotEmpty(), "không suy ra được datum nào cho chip $name — phép đọc nguồn đã hụt")
        }
    }
}
