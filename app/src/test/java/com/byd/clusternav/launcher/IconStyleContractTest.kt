package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * CANH PHONG CÁCH của toàn bộ bộ icon — **T10 · VISUAL-REFRESH P2** (spec `kachi-visual-refresh.html` §R2 · §4.2 ·
 * §4.6 · R6 AC6.1). Bản trước (T2 · `kachi-capability-groups.html` §4.5) ghim *một* độ dày nét 1.6 và *không màu
 * riêng*; owner 2026-09-16 bỏ luật cấm màu (*"không cần rule cấm gì đâu"*), nên luật nay là **NHẤT QUÁN**:
 *
 *  • AC2.1 — thang alpha đúng 3 bậc: `1.0` (chính, = không khai) · `0.38` (phụ) · `0.16` (nền);
 *  • AC2.2 — hai độ dày nét: `1.8` (chính) · `1.2` (phụ);
 *  • AC2.3 — cap/join tròn trên MỌI đường có nét (luật cũ giữ nguyên);
 *  • AC2.4 — khung 24×24 (luật cũ) + biến thể `_l` 32dp / `_xl` 48dp cho icon `large` (T9);
 *  • AC2.5 — màu chỉ đến từ **họ màu của lĩnh vực** khai trong `design/icon-grammar.json` (một nơi) — dùng màu ngoài
 *    họ ⇒ đỏ; hình xe `ic_car_*` một tông `#FFFFFF` (T8b: icon 24dp vẫn tint được);
 *  • AC5.1 — trần path: ≤ 6 ở 24dp · ≤ 10 ở 32/48;
 *  • §4.6 luật cứng — **sinh-lại-so-byte**: `gen-icons.py --check` + `gen-car.py --check` phải OK (vá tay một tệp
 *    sinh ⇒ đỏ; [ĐO] thử sửa 1 ký tự `ic_ac.xml` ⇒ script báo LỆCH, exit 1);
 *  • luật cũ còn đúng: không tệp mồ côi · mọi tên `ic-…` tra ra tệp thật · 9 icon nhóm là hợp đồng với `:core`.
 *
 * ## Vì sao bài này phải nằm ở `:app`
 * Nó quét **tài nguyên của `:app`**. Đặt ở module khác thì Gradle không coi tệp của `:app` là đầu vào ⇒ `UP-TO-DATE`
 * và **không bao giờ chạy lại** — cái bẫy đã cho một dấu xanh sai ở S1.
 */
class IconStyleContractTest {

    private val strokeWidths = setOf("1.8", "1.2")
    private val alphas = setOf("0.38", "0.16")

    /**
     * Tệp NGOÀI đường ống glyph/xe — giữ nguyên luật cũ của riêng nó, mỗi dòng một lý do (lệ `SettingsCatalog.NOT_SETTINGS`).
     * Cùng danh sách `EXCLUDED` của `scripts/design/icon-audit.py`.
     */
    private val legacy: Map<String, String> = mapOf(
        "ic_launcher.xml" to "icon app hoa anh đào (R7) — smallIcon thông báo, hợp đồng riêng (một tông, hệ tô lại)",
        "ic_turn_left.xml" to "mũi tên rẽ của màn dẫn đường (bảng NEW_ICON/CAN 2026-08-14), không thuộc launcher",
        "ic_turn_right.xml" to "mũi tên rẽ của màn dẫn đường — như trên",
        "ic_turn_straight.xml" to "mũi tên đi thẳng của màn dẫn đường — như trên",
        "ic_turn_right_g.xml" to "mũi tên rẽ cockpit cũ (turn_tile_bg) — cùng họ dẫn đường",
        // ⚠ WP6 · R6.2 (2026-09-20) — `ic_bubble_nav.xml` (mũi tên xanh `#1565C0`) đã **XOÁ**: nút nổi nay vẽ
        // `launcher_fg` = chính icon app Kachi (owner *"đổi icon nút nổi thành icon app Kachi"*). Dòng legacy phải
        // rời theo, vì bài `danh sach legacy tu rua hai chieu` đòi mọi tệp khai ở đây còn tồn tại thật.
        "ic_menu_config.xml" to "bảng con nút nổi Cast (bề mặt đã chạy trên xe), không có bước tint",
        "ic_menu_left.xml" to "cùng bảng con nút nổi Cast — không tint",
        "ic_menu_right.xml" to "cùng bảng con nút nổi Cast — không tint",
        "ic_check_selected.xml" to "dấu tích XANH LÁ = trạng thái \"đang chọn\"; đổi sang trắng là mất nghĩa",
        "ic_chevron_down.xml" to "nằm TRONG layer-list @drawable/spinner_bg ⇒ không có View nào để tint; màu phải ở trong tệp",
        "ic_corner_cut.xml" to "#0A0D13 = màu tường; đây là MẶT NẠ che góc vuông, không phải icon",
    )

    /** Icon CHƯA có chỗ dùng, giữ lại có lý do — nợ nhìn thấy được, không phải chỗ cất rác. */
    private val orphanPending: Map<String, String> = mapOf(
        "ic_check_selected.xml" to
            "bảng chọn app để chiếu đã bị gỡ khi Cast rút về 4 trạng thái; giữ vì nếu bày lại danh sách app thì cần đúng dấu tích này",
        "ic_corner_cut.xml" to
            "kỹ thuật che góc vuông để lộ mép bo tròn, có ghi cách dùng trong tệp; chưa nối vào ô nào của launcher",
    )

    // ── hạ tầng đọc tệp ─────────────────────────────────────────────────────────────────────────────

    private fun drawableDir(): Path = SourceRoots.path("src/main/res/drawable")

    private fun icons(): List<Pair<String, String>> =
        Files.list(drawableDir()).use { s ->
            s.filter { it.fileName.toString().let { n -> n.startsWith("ic_") && n.endsWith(".xml") } }
                .sorted()
                .map { it.fileName.toString() to it.toFile().readText() }
                .toList()
        }

    /** Icon thuộc ĐƯỜNG ỐNG (glyph + xe) — tức mọi `ic_*` trừ [legacy]. */
    private fun piped(): List<Pair<String, String>> = icons().filterNot { it.first in legacy }

    /** Hình xe theo vị trí = ba mặt `ic_car_top_/front_/rear_` — KHÔNG phải `ic_car(_l/_xl)` (glyph "xe" của lĩnh vực Danh tính). */
    private fun isCar(name: String) = CAR_PREFIXES.any { name.startsWith(it) }
    private fun isVariant(name: String) = !isCar(name) && (name.endsWith("_l.xml") || name.endsWith("_xl.xml"))

    private companion object {
        val CAR_PREFIXES = listOf("ic_car_top_", "ic_car_front_", "ic_car_rear_")
    }

    /** Từng phần tử `<path …/>` hoặc `<path …>…</path>` (có gradient) của một tệp — chỉ phần thuộc tính. */
    private fun paths(xml: String): List<String> =
        Regex("<path\\b[^>]*?(?:/>|>)", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.value }.toList()

    private fun attr(el: String, name: String): String? =
        Regex("android:$name=\"([^\"]*)\"").find(el)?.groupValues?.get(1)

    private fun transparent(v: String?) = v == null || v.replace("#", "").lowercase() == "00000000"

    private fun stroked(el: String) = !transparent(attr(el, "strokeColor")) || Regex("<aapt:attr name=\"android:strokeColor\"").containsMatchIn(el)

    private val grammar: JSONObject by lazy { JSONObject(File("design/icon-grammar.json").readText()) }

    private fun File(rel: String) = SourceRoots.path("../$rel").toFile()

    /** Họ màu của một icon glyph (id không tiền tố/hậu tố) — tra đúng như script: `icons[id].family` hoặc theo `domain`. */
    private fun familyOf(id: String): Set<String> {
        val icon = grammar.getJSONObject("icons").optJSONObject(id) ?: return emptySet()
        val fam = icon.optString("family").ifEmpty { grammar.getJSONObject("domains").getString(icon.getString("domain")) }
        val f = grammar.getJSONObject("families").getJSONObject(fam)
        return listOf("main", "light", "deep").map { f.getString(it).uppercase().removePrefix("#") }.toSet()
    }

    private fun idOf(name: String) = name.removeSuffix(".xml").removePrefix("ic_").removeSuffix("_xl").removeSuffix("_l")

    // ── 1. AC2.2 · hai độ dày nét ───────────────────────────────────────────────────────────────────

    @Test
    fun `bo icon dung DUNG HAI do day net 1_8 va 1_2`() {
        val offenders = piped().flatMap { (name, xml) ->
            paths(xml).mapNotNull { attr(it, "strokeWidth") }.filter { it !in strokeWidths }.map { "$name → $it" }
        }
        assertEquals(emptyList<String>(), offenders, "độ dày nét phải là 1.8 (chính) hoặc 1.2 (phụ) — AC2.2")
        val total = piped().sumOf { (_, xml) -> paths(xml).count { stroked(it) } }
        assertTrue(total > 100) { "chỉ đếm được $total đường có nét — bài đang quét vùng sai" }
    }

    // ── 2. AC2.1 · thang alpha ba bậc ───────────────────────────────────────────────────────────────

    @Test
    fun `thang alpha dung ba bac 1 - 0_38 - 0_16`() {
        val offenders = piped().flatMap { (name, xml) ->
            paths(xml).flatMap { el -> listOfNotNull(attr(el, "fillAlpha"), attr(el, "strokeAlpha")) }
                .filter { it !in alphas }.map { "$name → $it" }
        }
        assertEquals(emptyList<String>(), offenders, "alpha ngoài thang {1.0 · 0.38 · 0.16} — AC2.1")
    }

    // ── 3. AC2.3 · đầu nét + khớp nét TRÒN trên MỌI đường có nét (cả tệp legacy) ────────────────────

    @Test
    fun `moi duong co net phai co dau net va khop net tron`() {
        val bad = mutableListOf<String>()
        icons().forEach { (name, xml) ->
            paths(xml).filter { stroked(it) }.forEachIndexed { i, el ->
                if (attr(el, "strokeLineCap") != "round") bad += "$name[path $i] thiếu strokeLineCap=round"
                if (attr(el, "strokeLineJoin") != "round") bad += "$name[path $i] thiếu strokeLineJoin=round"
            }
        }
        assertTrue(bad.isEmpty()) { "đầu/khớp nét vuông = cảm giác \"thô\":\n" + bad.joinToString("\n") }
    }

    @Test
    fun `duong chi to khong giu thuoc tinh net`() {
        val bad = icons().flatMap { (name, xml) ->
            paths(xml).filterNot { stroked(it) }
                .filter { listOf("strokeWidth", "strokeLineCap", "strokeLineJoin").any { a -> attr(it, a) != null } }
                .map { name }
        }
        assertTrue(bad.isEmpty()) { "đường chỉ tô mà còn thuộc tính nét: ${bad.joinToString()}" }
    }

    // ── 4. AC2.4 · khung 24 · biến thể 32/48 đúng cỡ dp ─────────────────────────────────────────────

    @Test
    fun `moi icon dung khung ve 24 va bien the dung co dp`() {
        val bad = icons().mapNotNull { (name, xml) ->
            val w = attr(xml, "viewportWidth"); val h = attr(xml, "viewportHeight")
            val dp = attr(xml, "width")
            val wantDp = when { !isVariant(name) -> "24dp"; name.endsWith("_xl.xml") -> "48dp"; else -> "32dp" }
            if (w != "24" || h != "24") "$name → khung ${w}x$h"
            else if (name !in legacy && dp != wantDp) "$name → $dp (phải $wantDp)" else null
        }
        assertEquals(emptyList<String>(), bad, "khung vẽ 24×24 cho mọi icon; biến thể chỉ đổi cỡ dp")
    }

    /** T9 — mọi icon `large` trong ngữ pháp có đủ hai biến thể, và `KachiIcons.LARGE` nối đủ (không mồ côi, không thiếu). */
    @Test
    fun `icon large co du bien the 32 va 48 va duoc noi vao KachiIcons`() {
        val ic = grammar.getJSONObject("icons")
        val large = ic.keys().asSequence().filter { ic.getJSONObject(it).optBoolean("large") }.toSet()
        assertTrue(large.size >= 20) { "đọc hụt cờ large ($large)" }
        val files = icons().map { it.first }.toSet()
        val variants = files.filter { isVariant(it) }.map { idOf(it) }.toSet()
        assertEquals(large, variants, "tệp _l/_xl phải là ĐÚNG tập icon large của ngữ pháp")
        large.forEach {
            assertTrue("ic_${it}_l.xml" in files && "ic_${it}_xl.xml" in files) { "$it thiếu biến thể" }
        }
        val kachiIcons = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiIcons.kt")
        val wired = Regex("R\\.drawable\\.(\\w+) to \\(R\\.drawable\\.(\\w+)_l to R\\.drawable\\.(\\w+)_xl\\)")
            .findAll(kachiIcons).map { it.groupValues[1] }.toSet()
        assertEquals(large.map { "ic_$it" }.toSet(), wired, "KachiIcons.LARGE lệch với cờ large của ngữ pháp")
    }

    // ── 5. AC5.1 · trần số path theo cỡ ─────────────────────────────────────────────────────────────

    @Test
    fun `tran so path 6 o 24dp va 10 o 32-48`() {
        val bad = piped().mapNotNull { (name, xml) ->
            val n = paths(xml).size
            val cap = if (isVariant(name)) 10 else 6
            if (n > cap) "$name → $n path (trần $cap)" else null
        }
        assertEquals(emptyList<String>(), bad, "quá trần path — AC5.1 (GPU TRINKET)")
    }

    // ── 6. AC2.5 · màu chỉ từ họ màu của lĩnh vực; hình xe một tông ────────────────────────────────

    @Test
    fun `mau icon chi den tu ho mau cua linh vuc trong ngu phap`() {
        val hex = Regex("#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})\\b")
        val bad = mutableListOf<String>()
        piped().forEach { (name, xml) ->
            val used = hex.findAll(xml).map { it.groupValues[1].uppercase().takeLast(6) }.filter { it != "000000" && it != "FFFFFF" }.toSet()
            if (isCar(name)) {
                if (used.isNotEmpty()) bad += "$name (hình xe phải một tông #FFFFFF) → $used"
            } else {
                val fam = familyOf(idOf(name))
                assertTrue(fam.isNotEmpty()) { "$name không có mục trong design/icon-grammar.json" }
                (used - fam).forEach { bad += "$name → #$it ngoài họ $fam" }
            }
        }
        assertEquals(emptyList<String>(), bad, "màu ngoài họ của lĩnh vực — AC2.5 (nhất quán, không phải cấm)")
    }

    /** Danh sách legacy phải TỰ RỬA: tệp đã xoá thì phải rời danh sách; tệp mới ngoài đường ống thì phải khai. */
    @Test
    fun `danh sach legacy tu rua hai chieu`() {
        val names = icons().map { it.first }.toSet()
        legacy.forEach { (f, why) ->
            assertTrue(f in names) { "$f không còn tồn tại — bỏ khỏi legacy" }
            assertTrue(why.length >= 8) { "$f: lý do quá ngắn, phải nói được VÌ SAO" }
        }
        // Mọi tệp trong đường ống phải có nguồn: glyph (design/glyph/<id>.svg) hoặc xe (bảng ICONS của gen-car.py).
        val glyphs = Files.list(SourceRoots.path("../design/glyph")).use { s -> s.map { it.fileName.toString().removeSuffix(".svg") }.toList().toSet() }
        val genCar = File("scripts/design/gen-car.py").readText()
        val noSource = piped().map { it.first }.filter { n ->
            if (isCar(n)) !genCar.contains("\"${n.removeSuffix(".xml")}\"") else idOf(n) !in glyphs
        }
        assertEquals(emptyList<String>(), noSource, "tệp ic_* không có nguồn sinh — vá tay? khai vào legacy kèm lý do")
    }

    // ── 7. §4.6 luật cứng · sinh-lại-so-byte ────────────────────────────────────────────────────────

    /**
     * Chạy `python3 scripts/design/gen-icons.py --check` và `gen-car.py --check`: sinh lại vào thư mục tạm rồi so BYTE
     * với `res/drawable` (+ `CarFramesGenerated.kt`, `design/car/manifest.json`, `paint.json`). Không có `python3`
     * ⇒ bỏ qua có nhắn (assumption) — không giả xanh.
     *
     * ⚠ Phép dò hỏi **đúng phiên bản** chứ không chỉ hỏi "có chạy được không": hai script dùng cú pháp 3.10
     * (`str | None` trong chữ ký), nên một máy chỉ có Python 3.9 sẽ cho `SyntaxError` ⇒ exit ≠ 0 ⇒ bài đỏ với thông
     * điệp *"vá tay hoặc quên sinh lại"* — sai hẳn nguyên nhân. Máy thiếu bản đủ mới thì BỎ QUA có nhắn, đúng như ca
     * không có Python.
     */
    @Test
    fun `tep sinh khop byte voi nguon - vá tay la do`() {
        val root = SourceRoots.path("..").toFile()
        val probe = "import sys; sys.exit(0 if sys.version_info >= (3, 10) else 1)"
        val python = listOf("python3", "python").firstOrNull {
            runCatching { ProcessBuilder(it, "-c", probe).redirectErrorStream(true).start().waitFor() == 0 }.getOrDefault(false)
        }
        assumeTrue(python != null, "không có python3 ≥ 3.10 trên máy này — bài sinh-lại-so-byte bỏ qua (chạy tay: gen-icons.py --check)")
        listOf(
            listOf(python!!, "scripts/design/gen-icons.py", "--check", "app/src/main/res/drawable"),
            listOf(python, "scripts/design/gen-car.py", "--check"),
        ).forEach { cmd ->
            val pr = ProcessBuilder(cmd).directory(root).redirectErrorStream(true).start()
            val out = pr.inputStream.bufferedReader().readText()
            assertEquals(0, pr.waitFor(), "${cmd[1]} --check ĐỎ — tệp sinh bị vá tay hoặc quên sinh lại:\n$out")
        }
    }

    // ── 8. không tệp icon mồ côi ────────────────────────────────────────────────────────────────────

    @Test
    fun `khong co tep icon mo coi`() {
        val main = SourceRoots.path("src/main")
        val haystack = StringBuilder()
        Files.walk(main).use { s ->
            s.filter { Files.isRegularFile(it) }
                .filter { val n = it.fileName.toString(); n.endsWith(".kt") || n.endsWith(".xml") }
                .filter { it.parent.fileName.toString() != "drawable" || !it.fileName.toString().startsWith("ic_") }
                .forEach { haystack.append(it.toFile().readText()).append('\n') }
        }
        val icons = icons()
        val text = haystack.toString()
        val orphans = icons.filter { (name, _) ->
            val id = name.removeSuffix(".xml")
            val others = icons.filter { it.first != name }.joinToString("\n") { it.second }
            val where = text + "\n" + others
            !Regex("R\\.drawable\\.$id\\b").containsMatchIn(where) && !Regex("@drawable/$id\\b").containsMatchIn(where)
        }.map { it.first }
        val undocumented = orphans.filterNot { it in orphanPending }
        assertTrue(undocumented.isEmpty()) { "icon mồ côi (không ai dùng, không khai vào orphanPending): ${undocumented.joinToString()}" }
        val stale = orphanPending.keys.filterNot { it in orphans }
        assertTrue(stale.isEmpty()) { "đã có chỗ dùng, bỏ khỏi orphanPending: ${stale.joinToString()}" }
        orphanPending.forEach { (f, why) -> assertTrue(why.length >= 20) { "$f: lý do chờ-dùng quá mỏng" } }
    }

    // ── 9. 9 icon nhóm phải tra ra được ─────────────────────────────────────────────────────────────

    @Test
    fun `moi icon nhom cua core tra ra duoc mot drawable that`() {
        val declared = CapabilityGroups.ALL.map { it.icon }
        assertEquals(8, declared.size, "spec §4.1 chốt 8 nhóm (09-16 gỡ ADAS: 12→9 · WP8 gỡ g_ambient: 9→8)")
        assertEquals(declared.size, declared.toSet().size, "hai nhóm khai trùng tên icon")
        val table = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiTheme.kt")
        val files = icons().map { it.first }.toSet()
        declared.forEach { n ->
            assertTrue(n.startsWith("ic-group-")) { "$n: icon nhóm phải mang tiền tố ic-group-" }
            val hit = Regex("\"$n\"\\s*->\\s*R\\.drawable\\.(\\w+)").find(table)
            assertTrue(hit != null) { "$n chưa có dòng trong KachiTheme.iconRes ⇒ tra ra 0 = ô nhóm không có icon" }
            assertTrue(hit!!.groupValues[1] + ".xml" in files) { "$n trỏ vào tệp không tồn tại" }
        }
        assertEquals(declared.toSet(), KachiTheme.GROUP_ICON_NAMES.toSet(), "danh sách hợp đồng ở :app lệch với CapabilityGroups ở :core")
    }

    // ── 10. MỌI tên `ic-…` phải tra ra một drawable có thật ─────────────────────────────────────────

    private val unusedMapping: Map<String, String> = mapOf(
        "ic-door" to
            "gói lệnh 'Mở cửa + đèn' đã gỡ theo control door (1.94, NOT_PROVISIONED); giữ dòng để icon không mồ côi",
        "ic-car-top-door-all" to
            "nút 'Mở khoá cửa' (door) đã gỡ 1.94 (NOT_PROVISIONED trên xe); giữ hình cho lần wire lại nếu trim khác cho",
        "ic-car-top-lock" to
            "nút 'Khoá / mở khoá' (lock) đã gỡ 1.94 (NOT_PROVISIONED); giữ hình cho lần wire lại nếu trim khác cho",
        "ic-close" to
            "nút ✕ của thanh đầu ô đã gỡ ở S2 (owner: \"chỉ 1 nút ⇄\"); giữ dòng này để `ic_close.xml` không thành " +
                "tệp mồ côi, và để bày lại nút đóng ở đâu đó là có sẵn đúng hình",
    )

    @Test
    fun `moi ten icon dung trong ma tra ra duoc mot drawable that`() {
        val table = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiTheme.kt")
        val mapped: Map<String, String> = Regex("\"(ic-[a-z0-9-]+)\"\\s*->\\s*R\\.drawable\\.(\\w+)")
            .findAll(table).associate { it.groupValues[1] to it.groupValues[2] }
        assertTrue(mapped.size > 50) { "chỉ đọc được ${mapped.size} dòng bảng tra — bài đang quét vùng sai" }
        val files = icons().map { it.first.removeSuffix(".xml") }.toSet()
        assertEquals(emptyList<String>(), mapped.filterValues { it !in files }.map { (n, d) -> "$n → $d.xml (không có tệp)" })

        val used = mutableMapOf<String, MutableList<String>>()
        SourceRoots.moduleSourceRoots().forEach { root ->
            root.toFile().walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") && it.name != "KachiTheme.kt" }
                .forEach { file ->
                    val code = file.readText()
                        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
                        .lines().joinToString("\n") { it.substringBefore("//") }
                    Regex("\"(ic-[a-z0-9-]+)\"").findAll(code).forEach { m -> used.getOrPut(m.groupValues[1]) { mutableListOf() } += file.name }
                }
        }
        assertTrue(used.size > 50) { "chỉ thấy ${used.size} tên icon được dùng — bài đang quét vùng sai" }
        assertEquals(emptyList<String>(), used.filterKeys { it !in mapped }.map { (n, where) -> "$n (dùng ở ${where.distinct()})" },
            "tên icon KHÔNG có dòng trong KachiTheme.iconRes ⇒ `else -> 0` ⇒ ô mất icon mà KHÔNG lỗi gì")
        val dead = mapped.keys.filterNot { it in used || it in unusedMapping }
        assertEquals(emptyList<String>(), dead, "dòng bảng tra đã chết — xoá, hoặc khai vào unusedMapping kèm lý do")
        assertEquals(emptyList<String>(), unusedMapping.keys.filter { it in used }, "đã có chỗ dùng lại, bỏ khỏi unusedMapping")
        unusedMapping.forEach { (n, why) -> assertTrue(n in mapped && why.length >= 20) { "$n: unusedMapping sai/mỏng" } }
    }
}
