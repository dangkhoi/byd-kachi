package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CANH PHONG CÁCH của toàn bộ bộ icon (T2 · spec `kachi-capability-groups.html` §4.5).
 *
 * ## Bệnh nó chữa
 * [ĐO] trước T2: **68 tệp** `ic_*.xml` mang **9 độ dày nét khác nhau** (1.2 · 1.4 · 1.5 · 1.6 · 1.7 · 1.8 · 2 · 2.2 ·
 * 2.4) và **29/98 đường có nét thì thiếu `strokeLineCap`**, **57/98 thiếu `strokeLineJoin`** ⇒ đầu nét cắt vuông,
 * đậm nhạt lẫn lộn khi nhiều icon nằm cạnh nhau trong một lưới. Owner gọi đúng hiện tượng: "thô".
 *
 * ⚠ Con số "0 tệp khai `strokeLineCap`/`strokeLineJoin`" từng được ghi trong ngữ cảnh dự án là **SAI**: 41/68 tệp đã
 * khai ÍT NHẤT một trong hai. Bệnh thật không phải "không ai khai" mà là **khai lẻ tẻ theo từng đường** — nên bài này
 * đếm theo ĐƯỜNG (`<path>`), không theo tệp.
 *
 * ## Vì sao bài test này phải nằm ở `:app`
 * Nó quét **tài nguyên của `:app`**. Bài quét mã/tài nguyên của module X mà đặt ở module Y thì Gradle không coi tệp
 * của X là đầu vào của `Y:test` ⇒ báo `UP-TO-DATE` và **không bao giờ chạy lại** — chính cái bẫy đã cho một dấu xanh
 * sai ở S1. Đặt cùng module với thứ bị quét là điều kiện để bài còn sống.
 */
class IconStyleContractTest {

    /** Độ dày nét DUY NHẤT của cả bộ. Đổi số này = đổi cả bộ, cố ý làm cho việc đó phải tường minh. */
    private val strokeWidth = "1.6"

    /**
     * MÀU RIÊNG được phép giữ — mỗi dòng phải có lý do TẠI CHỖ (theo lệ [SettingsCatalog.NOT_SETTINGS]).
     *
     * Luật chung §4.5: icon **không** giữ màu riêng, nơi dùng sẽ tint. Ngoại lệ chỉ dành cho tệp mà **màu là NGHĨA**
     * hoặc **không có chỗ nào tint được**.
     */
    private val colorExceptions: Map<String, String> = mapOf(
        "ic_bubble_nav.xml" to
            "nút nổi Cast vẽ TRỰC TIẾP tệp này (BubbleRenderer), không có bước tint ⇒ xanh thương hiệu là danh tính Cast",
        "ic_menu_config.xml" to "hàng bảng con nút nổi Cast (bề mặt đã chạy trên xe), không có bước tint",
        "ic_menu_left.xml" to "cùng bảng con nút nổi Cast với ic_menu_config: dựng bằng setImageResource, không tint",
        "ic_menu_right.xml" to "cùng bảng con nút nổi Cast với ic_menu_config: dựng bằng setImageResource, không tint",
        "ic_check_selected.xml" to "dấu tích XANH LÁ = trạng thái \"đang chọn\"; đổi sang trắng là mất nghĩa",
        "ic_chevron_down.xml" to
            "nằm TRONG layer-list @drawable/spinner_bg ⇒ không có View nào để tint; màu phải ở trong tệp",
        "ic_corner_cut.xml" to "#0A0D13 = màu tường; đây là MẶT NẠ che góc vuông, không phải icon",
        "ic_launcher.xml" to "icon app trên OS (manifest + smallIcon thông báo): hệ thống không tint",
    )

    /**
     * Tệp được phép TRÀN khỏi ô quang học 20×20 — vì chúng cố ý phủ kín khung.
     */
    private val fullBleedExceptions: Map<String, String> = mapOf(
        "ic_corner_cut.xml" to "mặt nạ: phải phủ trọn góc, chừa lề là hở góc vuông ra",
        "ic_launcher.xml" to "icon app: nền màu phải phủ kín ô, đó là hình dạng của icon app",
    )

    /**
     * Icon CHƯA có chỗ dùng, giữ lại có lý do. Danh sách này là **nợ nhìn thấy được**, không phải chỗ để cất rác:
     * thêm dòng mới thì phải viết lý do, còn xoá tệp cũng làm bài xanh.
     */
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

    /** Từng phần tử `<path .../>` của một tệp. */
    private fun paths(xml: String): List<String> =
        Regex("<path\\b.*?/>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.value }.toList()

    private fun attr(el: String, name: String): String? =
        Regex("android:$name=\"([^\"]*)\"").find(el)?.groupValues?.get(1)

    private fun transparent(v: String?) = v == null || v.replace("#", "").lowercase() == "00000000"

    /** Đường có VẼ NÉT (chứ không phải chỉ tô). */
    private fun stroked(el: String) = !transparent(attr(el, "strokeColor"))

    // ── 1. một độ dày nét duy nhất ──────────────────────────────────────────────────────────────────

    @Test
    fun `ca bo icon dung DUNG MOT do day net`() {
        val offenders = icons().flatMap { (name, xml) ->
            paths(xml).mapNotNull { attr(it, "strokeWidth") }.filter { it != strokeWidth }.map { "$name → $it" }
        }
        assertTrue(offenders.isEmpty()) {
            "độ dày nét phải là $strokeWidth ở MỌI tệp; lệch: ${offenders.joinToString()}"
        }
        // Và phải có nét thật, không phải xanh vì bộ icon rỗng.
        val total = icons().sumOf { (_, xml) -> paths(xml).count { stroked(it) } }
        assertTrue(total > 100) { "chỉ đếm được $total đường có nét — bài đang quét vùng sai" }
    }

    // ── 2. đầu nét + khớp nét TRÒN trên MỌI đường có nét ────────────────────────────────────────────

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

    /** Đường KHÔNG có nét thì không được để lại thuộc tính nét (rác gây hiểu sai khi đọc tệp). */
    @Test
    fun `duong chi to khong giu thuoc tinh net`() {
        val bad = icons().flatMap { (name, xml) ->
            paths(xml).filterNot { stroked(it) }
                .filter { listOf("strokeWidth", "strokeLineCap", "strokeLineJoin").any { a -> attr(it, a) != null } }
                .map { name }
        }
        assertTrue(bad.isEmpty()) { "đường chỉ tô mà còn thuộc tính nét: ${bad.joinToString()}" }
    }

    // ── 3. cùng một khung vẽ ────────────────────────────────────────────────────────────────────────

    @Test
    fun `moi icon dung khung ve 24`() {
        val bad = icons().mapNotNull { (name, xml) ->
            val w = attr(xml, "viewportWidth")
            val h = attr(xml, "viewportHeight")
            if (w == "24" && h == "24") null else "$name → ${w}x$h"
        }
        assertTrue(bad.isEmpty()) { "khung vẽ phải là 24×24 để nét cùng độ dày ra cùng cảm giác: ${bad.joinToString()}" }
    }

    // ── 4. không giữ màu riêng ──────────────────────────────────────────────────────────────────────

    @Test
    fun `icon khong giu mau rieng ngoai danh sach co ly do`() {
        val bad = mutableListOf<String>()
        icons().forEach { (name, xml) ->
            if (name in colorExceptions) return@forEach
            paths(xml).forEach { el ->
                listOf("fillColor", "strokeColor").forEach { a ->
                    val v = attr(el, a)
                    if (!transparent(v) && v != "#FFFFFF") bad += "$name → $a=$v"
                }
            }
        }
        assertTrue(bad.isEmpty()) {
            "màu phải đến từ chỗ dùng (tint), không nằm trong tệp; nếu thật cần thì khai vào colorExceptions KÈM " +
                "lý do:\n" + bad.joinToString("\n")
        }
    }

    /** Danh sách ngoại lệ phải TỰ RỮA: tệp đã xoá/đã hết màu riêng thì phải rời danh sách. */
    @Test
    fun `danh sach ngoai le mau khong co muc ruaa`() {
        val names = icons().toMap()
        colorExceptions.forEach { (f, why) ->
            assertTrue(f in names) { "$f không còn tồn tại — bỏ khỏi colorExceptions" }
            assertTrue(why.length >= 8) { "$f: lý do quá ngắn, phải nói được VÌ SAO" }
            val keepsColor = paths(names.getValue(f)).any { el ->
                listOf("fillColor", "strokeColor").any { a ->
                    val v = attr(el, a); !transparent(v) && v != "#FFFFFF"
                }
            }
            assertTrue(keepsColor) { "$f nay đã theo chuẩn (không còn màu riêng) — bỏ khỏi colorExceptions" }
        }
        fullBleedExceptions.keys.forEach { assertTrue(it in names) { "$it không còn tồn tại — bỏ khỏi fullBleedExceptions" } }
    }

    // ── 5. không tệp icon mồ côi ────────────────────────────────────────────────────────────────────

    /**
     * Mọi `ic_*.xml` phải được **tham chiếu ở đâu đó trong `app/src/main`** (Kotlin `R.drawable.x` hoặc XML
     * `@drawable/x`), hoặc nằm trong [orphanPending] kèm lý do.
     *
     * [ĐO] trước T2 có **2 tệp mồ côi** không được nhắc tới ở BẤT KỲ đâu trong repo — kể cả tài liệu.
     */
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
        // Tệp icon KHÁC vẫn được coi là chỗ dùng hợp lệ (vd layer-list trỏ vào một icon) — nhưng tự trỏ vào chính
        // mình thì không, nên ở trên đã loại nguyên nhóm ic_* rồi thêm lại từng tệp không phải nó ở dưới.
        val icons = icons()
        val text = haystack.toString()
        val orphans = icons.filter { (name, _) ->
            val id = name.removeSuffix(".xml")
            val others = icons.filter { it.first != name }.joinToString("\n") { it.second }
            val where = text + "\n" + others
            !Regex("R\\.drawable\\.$id\\b").containsMatchIn(where) &&
                !Regex("@drawable/$id\\b").containsMatchIn(where)
        }.map { it.first }

        val undocumented = orphans.filterNot { it in orphanPending }
        assertTrue(undocumented.isEmpty()) {
            "icon mồ côi (không ai dùng, không khai vào orphanPending): ${undocumented.joinToString()}"
        }
        // Chiều ngược: mục trong danh sách chờ-dùng mà đã có chỗ dùng thì phải rời danh sách.
        val stale = orphanPending.keys.filterNot { it in orphans }
        assertTrue(stale.isEmpty()) { "đã có chỗ dùng, bỏ khỏi orphanPending: ${stale.joinToString()}" }
        orphanPending.forEach { (f, why) -> assertTrue(why.length >= 20) { "$f: lý do chờ-dùng quá mỏng" } }
    }

    // ── 6. 12 icon nhóm phải tra ra được ────────────────────────────────────────────────────────────

    /**
     * Giao kèo với T1 (`:core CapabilityGroups`): mỗi nhóm khai một tên `ic-group-…`, và tên đó PHẢI tra ra một
     * drawable thật. Không có bài này thì T1 đổi tên nhóm là icon lặng lẽ về 0 = ô trống icon (sai IM LẶNG — không
     * có ngoại lệ nào được ném, chỉ là ô không có hình).
     *
     * Bài đọc **trực tiếp** `CapabilityGroups.ALL` chứ không so với một danh sách chép tay: hai danh sách song song
     * thì chính chúng lại là thứ phải giữ đồng bộ.
     */
    @Test
    fun `moi icon nhom cua core tra ra duoc mot drawable that`() {
        val declared = CapabilityGroups.ALL.map { it.icon }
        assertEquals(12, declared.size, "spec §4.1 chốt 12 nhóm")
        assertEquals(declared.size, declared.toSet().size, "hai nhóm khai trùng tên icon")

        // `iconRes` cần R.drawable (Android framework) nên off-car ta canh bằng chính bảng nguồn + tệp có thật.
        val table = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiTheme.kt")
        val files = icons().map { it.first }.toSet()
        declared.forEach { n ->
            assertTrue(n.startsWith("ic-group-")) { "$n: icon nhóm phải mang tiền tố ic-group- (khác icon một datum)" }
            val hit = Regex("\"$n\"\\s*->\\s*R\\.drawable\\.(\\w+)").find(table)
            assertTrue(hit != null) { "$n chưa có dòng trong KachiTheme.iconRes ⇒ tra ra 0 = ô nhóm không có icon" }
            val file = hit!!.groupValues[1] + ".xml"
            assertTrue(file in files) { "$n trỏ vào $file nhưng tệp không tồn tại" }
        }
        // Chiều ngược: bảng `:app` không được khai tên nhóm mà `:core` không dùng (icon mồ côi kiểu mới).
        assertEquals(
            declared.toSet(), KachiTheme.GROUP_ICON_NAMES.toSet(),
            "danh sách hợp đồng ở :app lệch với CapabilityGroups ở :core",
        )
    }

    // ── 7. MỌI tên `ic-…` (không chỉ icon nhóm) phải tra ra một drawable có thật ────────────────────

    /**
     * Dòng bảng tra đã có nhưng **không ai truyền tên đó nữa** → lý do giữ lại.
     *
     * Giữ khuôn [orphanPending]: một dòng chết trong bảng `when` không gây lỗi gì thấy được, nên cách duy nhất để nó
     * không tích lại là bắt nó **hiện tên ra kèm lý do**.
     */
    private val unusedMapping: Map<String, String> = mapOf(
        "ic-close" to
            "nút ✕ của thanh đầu ô đã gỡ ở S2 (owner: \"chỉ 1 nút ⇄\"); giữ dòng này để `ic_close.xml` không thành " +
                "tệp mồ côi, và để bày lại nút đóng ở đâu đó là có sẵn đúng hình",
    )

    /**
     * ═══ KHÔNG TÊN `ic-…` NÀO ĐƯỢC TRA RA **0** ═══════════════════════════════════════════════════════════════
     *
     * Bài `moi icon nhom cua core tra ra duoc mot drawable that` phía trên chỉ phủ **12 icon NHÓM**. Tên của 123
     * datum · 64 nút · 9 widget · 4 gói lệnh thì không bài nào phủ — mà `KachiTheme.iconRes` kết bằng `else -> 0`,
     * nên một tên gõ sai (`"ic-temp-out"` ↔ tệp `ic_temp_out.xml`) **không ném gì cả**: `ImageView` chỉ đơn giản
     * không được thêm vào ô. Đúng họ "sai IM LẶNG" mà tệp này sinh ra để chặn.
     *
     * U6 thêm **23 tên mới trong một lượt** — đó là lúc rủi ro gõ sai cao nhất, và cũng là lúc phải khoá lại.
     *
     * Quét theo cùng lệ [SourceRoots.codeOf]: **bỏ chú thích** trước khi tìm, vì KDoc của chính bảng tra và của
     * `CapabilityIcons` nhắc tên icon dày đặc — quét thô sẽ đếm cả văn xuôi.
     */
    @Test
    fun `moi ten icon dung trong ma tra ra duoc mot drawable that`() {
        val table = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiTheme.kt")
        val mapped: Map<String, String> = Regex("\"(ic-[a-z0-9-]+)\"\\s*->\\s*R\\.drawable\\.(\\w+)")
            .findAll(table).associate { it.groupValues[1] to it.groupValues[2] }
        assertTrue(mapped.size > 50) { "chỉ đọc được ${mapped.size} dòng bảng tra — bài đang quét vùng sai" }

        val files = icons().map { it.first.removeSuffix(".xml") }.toSet()
        assertEquals(
            emptyList<String>(),
            mapped.filterValues { it !in files }.map { (n, d) -> "$n → $d.xml (không có tệp)" },
            "bảng tra trỏ vào drawable không tồn tại ⇒ lỗi biên dịch hoặc ô trống",
        )

        // Tên được TRUYỀN VÀO `iconRes` — gom từ mã của cả ba module, trừ chính bảng tra (nó là đích, không phải nguồn).
        val used = mutableMapOf<String, MutableList<String>>()
        SourceRoots.moduleSourceRoots().forEach { root ->
            root.toFile().walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") && it.name != "KachiTheme.kt" }
                .forEach { file ->
                    val code = file.readText()
                        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
                        .lines().joinToString("\n") { it.substringBefore("//") }
                    Regex("\"(ic-[a-z0-9-]+)\"").findAll(code).forEach { m ->
                        used.getOrPut(m.groupValues[1]) { mutableListOf() } += file.name
                    }
                }
        }
        assertTrue(used.size > 50) { "chỉ thấy ${used.size} tên icon được dùng — bài đang quét vùng sai" }

        assertEquals(
            emptyList<String>(),
            used.filterKeys { it !in mapped }.map { (n, where) -> "$n (dùng ở ${where.distinct()})" },
            "tên icon KHÔNG có dòng trong KachiTheme.iconRes ⇒ `else -> 0` ⇒ ô mất icon mà KHÔNG lỗi gì",
        )

        // Chiều ngược, tự rữa hai chiều như [orphanPending]: dòng bảng tra không ai truyền tên tới nữa.
        val dead = mapped.keys.filterNot { it in used || it in unusedMapping }
        assertEquals(emptyList<String>(), dead, "dòng bảng tra đã chết — xoá, hoặc khai vào unusedMapping kèm lý do")
        val revived = unusedMapping.keys.filter { it in used }
        assertEquals(emptyList<String>(), revived, "đã có chỗ dùng lại, bỏ khỏi unusedMapping")
        unusedMapping.forEach { (n, why) ->
            assertTrue(n in mapped) { "$n không còn trong bảng tra — bỏ khỏi unusedMapping" }
            assertTrue(why.length >= 20) { "$n: lý do giữ dòng chết quá mỏng" }
        }
    }
}
