package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ G1 · T5 — BÀI CANH THANG KHOẢNG CÁCH ════════════════════════════════════════════════════════════════════
 *
 * Quét **mã nguồn** tầng vẽ launcher và đỏ khi có số dp viết tại chỗ. Spec
 * `docs/specs/kachi-capability-groups.html` §4.4 (R4).
 *
 * ## ⚠⚠ Bài này quét MÃ NGUỒN ⇒ điều kiện sống của nó là Gradle BIẾT thứ nó quét
 * Đây là họ lỗi đã bắt được **hai lần** trong dự án: (1) S1 — hai bài chống-rữa đặt ở `:core` nhưng quét mã
 * `:app` ⇒ `:core:test` báo **UP-TO-DATE** ngay ở ca chúng sinh ra để bắt; (2) cùng ngày — bài nằm đúng module
 * `:app` mà quét `res/` vẫn UP-TO-DATE khi nội dung đổi. Nên bài này **phải** ở `:app` (module sở hữu mã nó
 * quét) **và** `app/build.gradle.kts` phải khai `inputs.dir("src/main/java")` — đã khai, và T5 đã tự chứng minh
 * bằng cách đổi một `dp(Sp.M)` thành `dp(7)` rồi chạy **không** `--rerun-tasks`: bài đỏ đúng chỗ.
 *
 * ## Phạm vi — nói rõ cái KHÔNG quét, để chỗ trống không bị đọc thành sơ suất
 *  - **Cỡ chữ (`sp`)** không thuộc thang này: nó là typography, có nhịp riêng theo cấp bậc thông tin.
 *  - **Ô vẽ Canvas** ([TyreBoardView], [DoorBoardView]) dùng **toán theo tỉ lệ** (`m * 0.018f`) chứ không dùng
 *    dp — chúng tự đúng với mọi mật độ và mọi cỡ ô, nên đưa vào thang dp sẽ làm chúng *kém* đúng đi.
 *  - **`:core`** không được giữ số dp — có bài riêng bên dưới.
 */
class SpacingScaleContractTest {

    /**
     * Số trần còn được phép trong một lời gọi dp, **kèm lý do**.
     *
     * Theo lệ `SettingsCatalog.NOT_SETTINGS`: mục loại trừ phải nói tại chỗ vì sao nó được loại trừ, không thì
     * danh sách ngoại lệ chỉ là chỗ để nhét thứ mình chưa muốn sửa.
     *
     * **Hiện RỖNG** — T5 chuyển được cả 337 chỗ. Giữ cơ chế lại (chứ không xoá) vì bài canh cần một đường HỢP LỆ
     * cho ngoại lệ có thật; không có nó thì người sau gặp một ca chính đáng sẽ chọn cách dễ hơn là nới bài canh.
     */
    private val allowedRaw: Map<String, String> = emptyMap()

    /** Bán kính truyền dạng `Float` cho helper của [KachiTheme] — họ số trần thứ hai, dễ bị bỏ sót. */
    private val rawRadius = Regex("""\b(?:card|gradient|gradientSoft|topFade)\(\s*[A-Za-z_][A-Za-z0-9_.]*\s*,\s*\d+(?:\.\d+)?f""")

    /**
     * Tên các hàm ĐỔI DP mà bài này phải soi.
     *
     * ## ⚠⚠ [SOÁT G1] Vì sao không chỉ có `dp`/`dpi`
     * [ĐO] lượt soát tìm ra `SettingsRows` giữ helper RIÊNG `fun px(v: Int) = v * density` với **bốn số trần**
     * (`px(14)`, `px(9)`, `px(20)`, `px(1)`) — chúng lọt qua bài canh **hoàn toàn**, vì mẫu tìm chỉ khớp tên
     * `dp(`/`dpi(`. Tức R4 (*"mọi số dp đi qua KachiSpace"*) bị lách chỉ bằng cách **đặt tên khác** cho hàm đổi dp,
     * mà bài canh vẫn xanh và tài liệu vẫn ghi *"T5 chuyển được cả 337 chỗ"*.
     *
     * Nay: (a) `px` đã bị xoá khỏi mã sản phẩm; (b) tên nào cũng bị soi qua danh sách này; (c) có bài
     * [khong duoc dung ham doi dp nao ngoai danh sach] chặn việc **khai** một hàm đổi dp mang tên mới — vì đó mới
     * là nguyên nhân, còn "số trần trong px()" chỉ là hiện tượng.
     */
    private val dpHelperNames = listOf("dp", "dpi", "dpf", "px", "toPx", "dip")

    /**
     * Hai tệp KHAI thang (WP5 tách vì trần 500 dòng) — xem `KachiSpaceBars chi duoc khai hang`.
     *
     * Chúng bị **loại khỏi** vùng quét số-trần (chúng LÀ chỗ số trần được phép sống) nhưng vẫn **nằm trong** phép
     * kiểm lý-do-tại-chỗ. Đó là hai vai khác nhau của cùng một danh sách, nên nó khai một chỗ.
     */
    private val SCALE_FILES = listOf("KachiSpace.kt", "KachiSpaceBars.kt")

    /** Tiền tố hợp lệ khi truyền một hằng cỡ vào `dp(...)` — đúng hai `object` của thang. */
    private val SCALE_PREFIXES = listOf("Sp.", "KachiSpace.", "Bars.", "KachiBars.")

    private fun launcherFiles(): List<Path> {
        val dir = SourceRoots.path("src/main/java/com/byd/clusternav/launcher")
        return Files.list(dir).toList()
            .filter { it.fileName.toString().endsWith(".kt") }
            // ⚠ WP5 — thang nay nằm ở HAI tệp (trần 500 dòng): `KachiSpace.kt` (thang chung) và
            // `KachiSpaceBars.kt` (hình học hai thanh). Cả hai chỉ KHAI hằng, nên chúng không thuộc vùng quét
            // *số trần trong lời gọi dp*; bài `KachiSpaceBars chi duoc khai hang` ở dưới ép tính chất đó.
            .filter { it.fileName.toString() !in SCALE_FILES }
            .sorted()
    }

    /** Bỏ chú thích để không bắt số nằm trong câu giải thích (vd "22dp → 48dp"). */
    private fun codeLines(p: Path): List<Pair<Int, String>> {
        val stripped = p.toFile().readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)) { m ->
                m.value.replace(Regex("[^\n]"), " ")            // giữ số dòng
            }
        return stripped.lines().mapIndexed { i, l -> (i + 1) to l.substringBefore("//") }
    }

    /**
     * Mọi lời gọi `dp(...)`/`dpi(...)` — quét **toàn bộ đối số** bằng cách đếm ngoặc, rồi soi số trần trong đó.
     *
     * ## ⚠⚠ Vì sao không dùng một regex đơn giản
     * Bản đầu của bài này dùng `dpi?\(\s*(ident,)?\d+\)` — chỉ khớp số **đứng một mình** trong ngoặc. Nó **BỎ SÓT**
     * `dpi(context, if (config.isVertical()) 100 else 84)` ở [ControlDockView] (bốn số: 100·84·70·86), vì `[^)]*`
     * không đi qua được dấu `)` của `isVertical()`. Tìm ra khi đọc mã để sửa việc khác, **không phải nhờ bài canh**
     * — nên bài canh phải mạnh hơn: đọc đúng vùng đối số theo ngoặc cân bằng.
     *
     * Số nằm trong định danh (`Sp.RADIUS_S`, `ic_layout_2c`) không bị tính, vì mẫu đòi số **không dính chữ**.
     */
    private fun rawNumbersInDpCalls(code: String): List<String> {
        val found = mutableListOf<String>()
        val call = Regex("""\b(?:${dpHelperNames.joinToString("|")})\(""")
        call.findAll(code).forEach { m ->
            var depth = 0
            var i = m.range.last                 // đứng tại '('
            var end = -1
            while (i < code.length) {
                when (code[i]) {
                    '(' -> depth++
                    ')' -> if (--depth == 0) { end = i; }
                }
                if (end >= 0) break
                i++
            }
            if (end < 0) return@forEach
            val args = code.substring(m.range.last + 1, end)
            // Số trần = chuỗi chữ số KHÔNG dính chữ/gạch dưới/dấu chấm hai bên.
            Regex("""(?<![A-Za-z0-9_.])\d+(?![A-Za-z0-9_])""").findAll(args).forEach { n ->
                found += "dp(… ${n.value} …)  →  ${args.trim().take(60)}"
            }
        }
        return found
    }

    @Test
    fun `moi loi goi dp nhan hang cua thang, khong nhan so tran`() {
        val offenders = mutableListOf<String>()
        launcherFiles().forEach { p ->
            codeLines(p).forEach { (no, line) ->
                // Bỏ khai báo hàm helper (`fun dp(v: Int)`) — nó không phải lời gọi.
                if (Regex("""fun (?:${dpHelperNames.joinToString("|")})\(""").containsMatchIn(line)) return@forEach
                rawNumbersInDpCalls(line).forEach { hit ->
                    val key = "${p.fileName}:$hit"
                    if (allowedRaw[key].isNullOrBlank()) offenders += "${p.fileName}:$no  $hit"
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "Số dp viết tại chỗ (phải dùng hằng của KachiSpace, hoặc khai ngoại lệ KÈM LÝ DO):\n" +
                offenders.joinToString("\n"),
        )
    }

    @Test
    fun `ban kinh khong con truyen so tran dang Float`() {
        val offenders = mutableListOf<String>()
        launcherFiles().forEach { p ->
            codeLines(p).forEach { (no, line) ->
                rawRadius.findAll(line).forEach { offenders += "${p.fileName}:$no  ${it.value}" }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "Bán kính còn là số trần — dùng họ KachiSpace.RADIUS_*:\n" + offenders.joinToString("\n"),
        )
    }

    @Test
    fun `thang khai dung sau bac cua spec`() {
        val src = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiSpace.kt")
        // Spec §4.4 chốt CON SỐ, không chỉ chốt "có sáu bậc". Khoá cả giá trị để không ai đổi lặng lẽ.
        mapOf("XS" to 4, "S" to 8, "M" to 12, "L" to 16, "XL" to 20, "XXL" to 28, "TOUCH" to 48)
            .forEach { (name, v) ->
                assertTrue(
                    Regex("""const val $name = $v\b""").containsMatchIn(src),
                    "KachiSpace phải khai `const val $name = $v` (spec §4.4)",
                )
            }
    }

    @Test
    fun `moi hang ngoai thang phai co ly do tai cho`() {
        // WP5 — quét CẢ HAI tệp thang: nếu chỉ quét `KachiSpace.kt` thì mọi hằng dời sang `KachiSpaceBars.kt`
        // lặng lẽ **ra khỏi** phép kiểm này, tức lượt tách tệp sẽ tự làm yếu bài canh (đúng họ lỗi `px()` đã lách
        // bài số-trần chỉ bằng cách đặt tên khác).
        val src = SCALE_FILES.joinToString("\n") {
            SourceRoots.text("src/main/java/com/byd/clusternav/launcher/$it")
        }
        val scale = setOf("XS", "S", "M", "L", "XL", "XXL")
        val declared = Regex("""const val ([A-Z][A-Z0-9_]*) =""").findAll(src).map { it.groupValues[1] }.toList()
        val offScale = declared.filterNot { it in scale }
        assertTrue(offScale.isNotEmpty(), "KachiSpace phải có hằng ngoài thang (nét, bán kính, đích chạm…)")

        // "Lý do tại chỗ" = có KDoc/chú thích NGAY TRƯỚC khai báo. Quét văn bản gốc (không bỏ chú thích).
        val lines = src.lines()
        val undocumented = offScale.filter { name ->
            val at = lines.indexOfFirst { Regex("""const val $name =""").containsMatchIn(it) }
            if (at < 0) return@filter true
            // Lùi qua dòng trống, đòi dòng liền trước là phần đóng KDoc `*/` hoặc một chú thích `//`.
            var i = at - 1
            while (i >= 0 && lines[i].isBlank()) i--
            i < 0 || !(
                lines[i].trim().let {
                    // `*/` = đóng KDoc nhiều dòng · `/**` = KDoc MỘT dòng · `*` = giữa KDoc · `//` = chú thích thường.
                    it.startsWith("*/") || it.startsWith("/**") || it.startsWith("//") || it.startsWith("*")
                }
                )
        }
        assertEquals(
            emptyList<String>(), undocumented,
            "Hằng ngoài thang thiếu lý do tại chỗ (lệ SettingsCatalog.NOT_SETTINGS): $undocumented",
        )
    }

    /**
     * Bốn chỗ đọc khe giữa các ô **phải** đi qua [KachiSpace.SLOT_GAP].
     *
     * Trước T5 mỗi chỗ giữ một bản sao `dp(10)`. Lệch một chỗ ⇒ màn vẽ ô theo lưới này mà cửa sổ app đặt theo
     * lưới khác = hình dạng P-bug2. Đây là bài chống **bẫy hai-bản-sao**, không phải bài về thẩm mỹ.
     */
    @Test
    fun `khe giua o di qua mot hang duy nhat`() {
        listOf(
            "WorkspaceView.kt" to 1,
            "LauncherWindows.kt" to 1,
            "DockAreaLayout.kt" to 1,
        ).forEach { (file, least) ->
            val code = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/$file")
                .lines().joinToString("\n") { it.substringBefore("//") }
            val hits = Regex("""Sp\.SLOT_GAP""").findAll(code).count()
            assertTrue(
                hits >= least,
                "$file phải lấy khe giữa ô từ Sp.SLOT_GAP (thấy $hits chỗ, cần ≥ $least)",
            )
        }
    }

    /**
     * ⚠⚠ **Chặn NGUYÊN NHÂN, không chỉ hiện tượng**: không được khai một hàm đổi dp mang tên mà bài canh không biết.
     *
     * [ĐO] `SettingsRows.px()` đã lách được bài canh này suốt T5 chỉ vì nó **không tên là `dp`**. Chặn số trần
     * trong `px(...)` mà không chặn việc *tạo ra* `px` thì lần sau ai đó viết `fun d(v: Int)` là lại lọt — đúng
     * họ lỗi *"chặn hiện tượng thay vì chặn nguyên nhân"* mà bản vá P0 ngày 2026-09-11 đã ghi (bài chỉ chặn nhãn
     * chứa `%` nên không bắt được lần thứ hai).
     */
    @Test
    fun `khong duoc khai ham doi dp nao ngoai danh sach`() {
        val offenders = mutableListOf<String>()
        // ⚠ `plusElement`, KHÔNG phải `+`: `java.nio.file.Path` **là** `Iterable<Path>` (nó duyệt qua các thành
        // phần tên), nên `List<Path> + path` chọn nạp chồng `Iterable` và nối vào `..`, `app`, `src`… thay vì nối
        // chính tệp đó. [ĐO] bản đầu của bài này đỏ với `FileNotFoundException: .. (Is a directory)`.
        (launcherFiles().plusElement(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/KachiSpace.kt")))
            .forEach { p ->
            val src = p.toFile().readText()
            // Khai báo hàm mà THÂN nhân với density = một hàm đổi dp, bất kể tên gì.
            Regex("""fun\s+(\w+)\s*\([^)]*\)[^\n=]*=\s*[^\n]*displayMetrics\.density""").findAll(src).forEach { m ->
                val name = m.groupValues[1]
                if (name !in dpHelperNames) offenders += "${p.fileName}: fun $name(...)"
            }
        }
        assertEquals(
            emptyList<String>(), offenders,
            "hàm đổi dp mang tên lạ ⇒ số trần truyền vào nó KHÔNG bị bài canh soi. Dùng KachiTheme.dpi / " +
                "KachiSpace.dp/dpf, hoặc thêm tên vào dpHelperNames (và hiểu là bạn đang mở thêm một cửa vào): " +
                offenders,
        )
    }

    /**
     * ⚠⚠ Hằng cỡ dp phải sống TRONG thang — không được khai ở tầng vẽ rồi truyền vào `dp(...)`.
     *
     * [ĐO] `SettingsPanel.RAIL_DP = 230` từng nằm ngoài [KachiSpace]. Bài canh số-trần **không thấy** nó (nó là
     * định danh, không phải số trần), nên cùng một màn Cài đặt có cột nhãn khai trong thang mà cột rail khai ở tầng
     * vẽ — *"một thang, một chỗ"* chỉ đúng trên giấy. Đây là lỗ cùng họ với `px()`: bài canh chỉ soi **hình dạng**
     * của lời gọi, nên mọi cách viết khác đi đều lọt.
     */
    @Test
    fun `khong duoc truyen hang co ngoai thang vao dp`() {
        val offenders = mutableListOf<String>()
        launcherFiles().forEach { p ->
            codeLines(p).forEach { (no, line) ->
                Regex("""\b(?:${dpHelperNames.joinToString("|")})\(\s*[A-Za-z_][A-Za-z0-9_.]*\s*,\s*([A-Za-z_][A-Za-z0-9_.]*)\s*\)""")
                    .findAll(line).forEach { m ->
                        val arg = m.groupValues[1]
                        // Chỉ soi hằng SCREAMING_CASE (biến thường/tham số là giá trị tính ra, không phải hằng cỡ).
                        val isConst = arg.substringAfterLast('.').all { it.isUpperCase() || it.isDigit() || it == '_' }
                        if (isConst && SCALE_PREFIXES.none { arg.startsWith(it) }) {
                            offenders += "${p.fileName}:$no  ${m.value}"
                        }
                    }
            }
        }
        assertEquals(
            emptyList<String>(), offenders,
            "hằng cỡ dp khai ngoài KachiSpace ⇒ nó không thuộc thang nào và bài canh số-trần không soi tới:\n" +
                offenders.joinToString("\n"),
        )
    }

    /**
     * ⚠⚠ **`KachiSpaceBars.kt` chỉ được KHAI HẰNG** — điều kiện để nó được nhận là một phần của thang.
     *
     * WP5 tách thang ra hai tệp vì trần 500 dòng, và lượt tách đó **nới** hai bài canh: [SCALE_PREFIXES] nay nhận
     * `Bars.`/`KachiBars.`, và phép kiểm lý-do-tại-chỗ quét cả hai tệp. Nới một bài canh mà không ép lại điều kiện
     * thì lần sau ai cũng có thể dựng một tệp tên `KachiSpaceXxx.kt` rồi đặt số trần + mã vẽ vào đó.
     *
     * Điều kiện: 0 lời gọi hàm đổi dp, 0 import Android, 0 `fun` nào ngoài khai hằng. Nếu tệp đó mọc ra một lời
     * gọi `dp(` thì nó đã là **tầng vẽ**, và lối nhận ngoại lệ ở [SCALE_PREFIXES] phải đóng lại.
     */
    @Test
    fun `KachiSpaceBars chi duoc khai hang`() {
        val src = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiSpaceBars.kt")
        val code = src.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .lines().joinToString("\n") { it.substringBefore("//") }
        assertTrue(
            !Regex("""\b(?:${dpHelperNames.joinToString("|")})\(""").containsMatchIn(code),
            "tệp thang không được GỌI hàm đổi dp — gọi tức là nó đang vẽ, không còn là thang",
        )
        assertTrue(!code.contains("import android"), "tệp thang không được import Android")
        assertTrue(!Regex("""\bfun\s+\w+""").containsMatchIn(code), "tệp thang chỉ khai hằng, không khai hàm")
        assertTrue(
            Regex("""const val [A-Z][A-Z0-9_]* =""").findAll(code).count() >= 8,
            "…và nó phải thật sự là một bảng hằng (hiện có ít hơn 8 hằng ⇒ có thể đã bị rút ruột)",
        )
    }

    /**
     * `:core` KHÔNG được giữ số dp — khoảng cách là việc của tầng vẽ.
     *
     * Cùng lập luận `ChipTone` của RW0: `:core` nói *ý nghĩa*, `:app` mới biết *cách trình bày*. Để `:core` giữ
     * dp là mở đường cho **thang thứ hai**, và [ĐO] của RW0 cho thấy hai bảng lệch nhau ngay dòng đầu.
     */
    @Test
    fun `core khong giu so dp`() {
        // Ngoại lệ CÓ LÝ DO (lệ SettingsCatalog.NOT_SETTINGS).
        val allowed = mapOf(
            "BadgeLayout.kt" to
                "Không phải khoảng cách bố cục mà là KHOẢNG HỢP LỆ của một cỡ người dùng tự chọn " +
                "(badge tốc độ trên cụm, 60..240dp, mặc định 120). Phép kẹp phải ở `:core` để kiểm được " +
                "off-car — đó chính là lý do nó không thuộc tầng vẽ. Ngoài ra nó là bề mặt ClusterNav, " +
                "không phải launcher, nên không dùng thang KachiSpace.",
        )
        val roots = SourceRoots.moduleSourceRoots().filter { it.toString().contains("core") }
        assertTrue(roots.isNotEmpty(), "phải tìm được cây nguồn :core")
        val offenders = mutableListOf<String>()
        roots.forEach { root ->
            Files.walk(root).toList().filter { it.toString().endsWith(".kt") }.forEach { p ->
                val name = p.fileName.toString()
                if (!allowed[name].isNullOrBlank()) return@forEach
                val code = p.toFile().readText()
                    .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
                    .lines().joinToString("\n") { it.substringBefore("//") }
                if (Regex("""\b(?:${dpHelperNames.joinToString("|")})\(""").containsMatchIn(code) ||
                    Regex("""\bconst val [A-Z_]*DP\b""").containsMatchIn(code)
                ) {
                    offenders += name
                }
            }
        }
        assertEquals(emptyList<String>(), offenders, "`:core` không được giữ số dp: $offenders")
    }

    /**
     * Đích chạm dưới [KachiSpace.TOUCH] phải nói tại chỗ vì sao không thể đạt 48dp.
     *
     * ⚠ Bài này khoá **giao kèo tài liệu**, KHÔNG đo được cỡ thật (cỡ thật chỉ hiện lúc dựng view). Cỡ thật được
     * kiểm bằng ảnh chụp máy ảo — ghi rõ để không ai đọc dấu xanh này thành "mọi đích chạm đã đạt 48dp".
     */
    @Test
    fun `dich cham duoi muc toi thieu phai co ly do tai cho`() {
        val space = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiSpace.kt")
        // Đòi KDoc của TOUCH_TIGHT SUY từ cỡ ô thật (`DOCK_TILE_W`/`DOCK_TILE_H`), không phải chỉ nhắc một con
        // số. Bản đầu của bài này so chuỗi "84dp" nên đỏ oan ngay khi KDoc viết "84×86dp" — canh chữ thì giòn,
        // canh việc "có dẫn ra hằng nguồn" mới đúng ý.
        val tight = space.substringAfter("const val TOUCH_TIGHT", "")
        val tightDoc = space.substringBefore("const val TOUCH_TIGHT").takeLast(1400)
        assertTrue(tight.isNotEmpty(), "KachiSpace phải khai TOUCH_TIGHT")
        assertTrue(
            tightDoc.contains("DOCK_TILE_W") && tightDoc.contains("DOCK_TILE_H"),
            "KDoc của TOUCH_TIGHT phải suy từ [DOCK_TILE_W]×[DOCK_TILE_H] (trần vật lý của ô), " +
                "không phải tự chọn một con số",
        )
        val ws = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt")
        val head = SourceRoots.body(ws, "private fun headLp()")
        assertTrue(head.contains("Sp.SLOT_HEAD_CLEAR"), "headLp phải lấy độ hở từ Sp.SLOT_HEAD_CLEAR")
        assertTrue(
            ws.substringBefore("private fun headLp()").takeLast(1200)
                .let { it.contains("DƯỚI mức") && it.contains("Sp.TOUCH") },
            "nút ⇄ nổi ở đầu ô nhỏ hơn Sp.TOUCH ⇒ KDoc của headLp phải nêu lý do",
        )
    }
}
