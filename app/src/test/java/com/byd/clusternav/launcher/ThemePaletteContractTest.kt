package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ T1 — BÀI CANH BẢNG MÀU HAI CHỦ ĐỀ ═══════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-i18n-and-light-theme.html` §3.2 (R4). Ba việc, ba loại bằng chứng khác nhau:
 *
 *  1. **Quét MÃ NGUỒN** — không còn mã màu viết cứng ngoài [KachiPalette], và không còn `Color.WHITE`.
 *  2. **Tính TOÁN từ chính hai bảng** — tương phản WCAG 2.x cho từng cặp mực/nền và từng viền kết cấu.
 *  3. **Đếm dây nối** — đúng một chỗ ghi bảng màu, và mọi vai đều tra được qua [KachiTheme].
 *
 * ## ⚠⚠ Vì sao bài này nằm ở `:app` chứ không ở `:core`
 * Nó quét mã nguồn của `:app` **và** đọc trực tiếp [KachiPalette] (một `data class` thuần, 0 import Android — nên
 * chạy được trong test JVM). Luật đã trả giá hai lần trong dự án: *bài quét mã của module X phải NẰM trong module X,
 * và Gradle phải BIẾT thứ nó quét* — S1 đặt hai bài chống-rữa ở `:core` mà quét `:app` ⇒ `:core:test` báo
 * **UP-TO-DATE** đúng ở ca chúng sinh ra để bắt. `app/build.gradle.kts` đã khai `inputs.dir("src/main/java")`, và T1
 * đã tự chứng minh bằng cách lệch một màu rồi chạy **không** `--rerun-tasks`: bài đỏ đúng chỗ.
 *
 * ## `Color.WHITE` — cái bẫy mà bài "0 hex" MỘT MÌNH không bắt được
 * [ĐO] T1 tìm thấy **22** chỗ dùng `Color.WHITE`. Chúng không phải hex nên bài "0 hex" xanh trơn, nhưng trên bảng
 * SÁNG có ít nhất **9** chỗ trong số đó biến thành **chữ trắng trên nền trắng** (giờ trên thanh trên · nhãn app nổi ·
 * chữ nút −/+ của ô điều khiển · icon trong 4 lưới chọn). Nghĩa là một bài canh "0 hex" xanh vẫn có thể đi kèm một
 * bảng màu sáng dùng không được. Nên bài này chặn CẢ hằng màu của Android.
 */
class ThemePaletteContractTest {

    // ══ (1) QUÉT MÃ NGUỒN ═════════════════════════════════════════════════════════════════════════════════

    /**
     * Tệp được phép chứa mã màu hex, **kèm lý do** (lệ `SettingsCatalog.NOT_SETTINGS`).
     *
     * Đúng một mục: [KachiPalette] *là* bảng màu — mã hex phải sống ở đâu đó, và cả thiết kế của T1 là dồn chúng về
     * một chỗ để hai chủ đề không thể lệch nhau. Bất kỳ tệp thứ hai xuất hiện ở đây trong tương lai đều đang mở lại
     * đường "bảng màu thứ hai" mà `ChipTone` của RW0 đã dạy: bản nháp viết `#37d67a` trong khi bảng là `#34d399`.
     */
    private val hexAllowed: Map<String, String> = mapOf(
        "KachiPalette.kt" to "chính là bảng màu — chỗ DUY NHẤT được khai hex (T1 §3.2)",
    )

    @Test
    fun `0 ma mau viet cung trong tang ve launcher`() {
        val hex = Regex(""""#[0-9a-fA-F]{6,8}"""")
        val offenders = launcherSources()
            .filter { it.fileName.toString() !in hexAllowed }
            .mapNotNull { f ->
                val found = hex.findAll(code(f)).map { it.value }.toList()
                if (found.isEmpty()) null else "${f.fileName}: $found"
            }
        assertEquals(
            emptyList<String>(), offenders,
            "mã màu viết cứng ngoài KachiPalette ⇒ chỗ đó KHÔNG đổi theo chủ đề (bảng sáng sẽ sai ở đúng chỗ đó). " +
                "Thêm một vai vào KachiPalette rồi tra qua KachiTheme; nếu thật sự là ngoại lệ thì khai vào " +
                "hexAllowed KÈM LÝ DO.",
        )
    }

    /**
     * `Color.WHITE`/`Color.BLACK`… cũng là mã màu viết cứng — chỉ là viết bằng chữ.
     *
     * `Color.TRANSPARENT` **được phép**: "không có màu" không phải một vai màu, nó giữ nghĩa y nhau ở cả hai bảng
     * (dùng làm đầu tắt của gradient trong [WallView]).
     */
    @Test
    fun `0 hang mau Android viet cung trong tang ve launcher`() {
        val named = Regex("""\bColor\.(WHITE|BLACK|GRAY|DKGRAY|LTGRAY|RED|GREEN|BLUE|YELLOW|CYAN|MAGENTA)\b""")
        val offenders = launcherSources().mapNotNull { f ->
            val found = named.findAll(code(f)).map { it.value }.toList()
            if (found.isEmpty()) null else "${f.fileName}: ${found.distinct()}"
        }
        assertEquals(
            emptyList<String>(), offenders,
            "hằng màu Android không đổi theo chủ đề. [ĐO] T1: 9/22 chỗ `Color.WHITE` thành CHỮ TRẮNG TRÊN NỀN " +
                "TRẮNG ở bảng sáng. Dùng KachiTheme.ON_ACCENT (chữ trên nền nhấn đặc) hoặc KachiTheme.INK " +
                "(chữ trên nền thường) — hai vai đó khác nhau, chọn đúng vai thì bảng sáng tự đúng.",
        )
    }

    // ══ (2) TƯƠNG PHẢN — tính từ chính hai bảng ═══════════════════════════════════════════════════════════

    /**
     * Nền nào phải đỡ được những mực nào.
     *
     * Khai tường minh thay vì nhân chéo tất-cả-với-tất-cả, vì nhân chéo hỏi một câu SAI: `dim` (nền nút −/+ và vùng
     * radar đang tắt) chưa bao giờ đỡ chữ nào ngoài [KachiPalette.ink], nên đòi nó đỡ `mut2` là bắt bảng màu trả lời
     * một tình huống không tồn tại — rồi lời giải sẽ là nới ngưỡng, tức là làm yếu bài canh vì một ca giả.
     *
     * Đổi lại, danh sách phải **đầy đủ**: có bài [moi nen va moi muc deu duoc khai] đòi mọi vai nền và mọi vai mực
     * đều xuất hiện ở đây, nên không thể lặng lẽ bỏ một nền ra khỏi phép kiểm.
     */
    private val textOn: List<Pair<String, List<String>>> = listOf(
        "bg" to ALL_INKS,
        "card" to ALL_INKS,
        "card2" to ALL_INKS,
        "panel" to ALL_INKS,
        "field" to ALL_INKS,
        "cell" to ALL_INKS,
        "tile" to ALL_INKS,
        "slot" to ALL_INKS,
        "chipOff" to ALL_INKS,
        "headBg" to listOf("ink", "mut"),
        "emptyFill" to listOf("ink", "mut"),
        // Nền nút bước −/+ và vùng radar tắt. Chỉ chữ chính nằm trên nó; vùng radar không có chữ nào.
        "dim" to listOf("ink"),
    )

    @Test
    fun `muc tren nen dat 4_5 to 1 o ca hai bang`() {
        val bad = mutableListOf<String>()
        forEachPalette { name, p ->
            textOn.forEach { (surface, inks) ->
                inks.forEach { ink ->
                    val r = ratio(role(p, ink), role(p, surface))
                    if (r < 4.5) bad += "$name $ink trên $surface = ${fmt(r)}"
                }
            }
        }
        assertEquals(emptyList<String>(), bad, "tương phản mực/nền dưới 4.5:1 (WCAG AA cho chữ thường): $bad")
    }

    /**
     * Chữ trên nền NHẤN — hai ca khác nhau về bản chất, nên hai phép kiểm riêng:
     *
     *  - nền nhấn **ĐẶC** (gradient của pill/nút đang chọn) ⇒ mực là [KachiPalette.onAccent];
     *  - nền nhấn **BÁN TRONG SUỐT** (ô điều khiển đang bật) ⇒ phải TRỘN với nền dưới nó trước khi đo, và mực là
     *    [KachiPalette.inkOnAccent].
     *
     * [ĐO] đây đúng là chỗ bảng sáng dễ sai nhất: cùng vai "chữ trên nền nhấn", nhưng ở bảng sáng nền trộn ra
     * `#c7d3f4` nên chữ trắng chỉ còn **1.49:1** — hướng của mực phải ĐẢO, không phải chỉ đổi độ đậm.
     */
    @Test
    fun `chu tren nen nhan dat 4_5 to 1 o ca hai bang`() {
        val bad = mutableListOf<String>()
        forEachPalette { name, p ->
            listOf("gradFrom", "gradTo").forEach { g ->
                val r = ratio(p.onAccent, role(p, g))
                if (r < 4.5) bad += "$name onAccent trên $g = ${fmt(r)}"
            }
            listOf("tileOnFrom", "tileOnTo").forEach { g ->
                val r = ratio(p.inkOnAccent, over(role(p, g), p.tile))
                if (r < 4.5) bad += "$name inkOnAccent trên $g(trộn trên tile) = ${fmt(r)}"
            }
        }
        assertEquals(emptyList<String>(), bad, "chữ trên nền nhấn dưới 4.5:1: $bad")
    }

    /**
     * Glyph ⇄ trên đầu ô phải thấy được **kể cả khi app phía dưới là TRẮNG TINH**.
     *
     * Đây là bài canh sinh ra từ một lỗi thật của T1: [KachiPalette.scrimBtn] nằm trên **pixel của app đang chiếu**,
     * nên nó là vai màu DUY NHẤT không được phép "theo chủ đề" theo phản xạ. Bản đầu đặt bản sáng 15% đen ⇒ [ĐO trên
     * ảnh máy ảo] trắng trên (217,217,217) = **1.41:1**. Bài này khoá đúng cái giả định đó: nền tệ nhất có thể là
     * trắng, và glyph phải vượt 3:1 trên nền tệ nhất — không phải trên nền của launcher.
     */
    @Test
    fun `glyph tren scrim doc duoc ke ca khi app duoi la trang tinh`() {
        forEachPalette { name, p ->
            val r = ratio(p.onAccent, over(p.scrimBtn, "#ffffff"))
            assertTrue(
                r >= 3.0,
                "$name: glyph ⇄ trên app nền trắng chỉ ${fmt(r)}:1 (cần ≥ 3.0). scrimBtn nằm trên pixel của app, " +
                    "không phải trên nền launcher ⇒ làm nhạt nó theo bảng sáng là làm glyph biến mất.",
            )
        }
    }

    /**
     * Ô điều khiển đang BẬT phải tô icon **và** chữ bằng [KachiPalette.inkOnAccent], không phải [KachiPalette.onAccent].
     *
     * Hai vai này chỉ khác nhau ở **loại nền**, và đó chính là chỗ dễ lẫn: `onAccent` (trắng) dành cho nền nhấn ĐẶC
     * (gradient của pill), còn ô điều khiển dùng `gradientSoft` = nền nhấn **BÁN TRONG SUỐT**. [ĐO trên ảnh máy ảo]
     * bản đầu của T1 tô icon bằng `onAccent` ⇒ trên bảng sáng nền trộn ra (214,217,248) và icon trắng chỉ **1.39:1**.
     *
     * Bài này canh **dây nối**, không canh con số: phép kiểm tương phản ở trên đã chứng minh `inkOnAccent` đủ tương
     * phản trên nền `tileOn*`, nhưng nó không thể biết bộ vẽ có thật sự dùng vai đó hay không.
     */
    @Test
    fun `o dieu khien dang bat dung inkOnAccent chu khong dung onAccent`() {
        val tint = SourceRoots.body(
            SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt"),
            "private fun tint(",
        )
        assertTrue(tint.contains("INK_ON_ACCENT"), "nhánh BẬT phải dùng INK_ON_ACCENT (nền nhấn bán trong suốt)")
        assertTrue(
            !tint.contains("ON_ACCENT") || tint.contains("INK_ON_ACCENT"),
            "không được dùng ON_ACCENT ở đây: nó dành cho nền nhấn ĐẶC",
        )
        assertEquals(
            2, Regex("INK_ON_ACCENT").findAll(tint).count(),
            "cả ICON và CHỮ của ô đang bật đều phải dùng INK_ON_ACCENT — [ĐO] chữ đúng mà icon sai thì bảng sáng " +
                "mất glyph nhưng vẫn còn nhãn, nên lỗi rất dễ lọt qua một lượt nhìn nhanh",
        )
    }

    /** Viền KẾT CẤU — thứ nói *"đây là một thành phần riêng"*. WCAG 1.4.11 (non-text) đòi 3:1. */
    @Test
    fun `vien ket cau dat 3 to 1 o ca hai bang`() {
        val bad = mutableListOf<String>()
        forEachPalette { name, p ->
            listOf("bg", "card", "slot").forEach { s ->
                val r = ratio(over(p.lineStrong, role(p, s)), role(p, s))
                if (r < 3.0) bad += "$name lineStrong trên $s = ${fmt(r)}"
            }
            val e = ratio(over(p.emptyLine, p.emptyFill), p.emptyFill)
            if (e < 3.0) bad += "$name emptyLine trên emptyFill = ${fmt(e)}"
        }
        assertEquals(emptyList<String>(), bad, "viền kết cấu dưới 3:1 = thành phần không có đường bao đọc được: $bad")
    }

    /**
     * Thẻ phải TÁCH ĐƯỢC khỏi nền — bằng **viền ≥ 3:1** HOẶC bằng **bước sáng ≥ 1.10×**.
     *
     * ## ⚠ Vì sao là "hoặc", và vì sao đó không phải nới lỏng
     * Hai bảng tách thẻ bằng hai cơ chế KHÁC nhau, và đó là quyết định thiết kế chứ không phải chỗ chưa làm xong:
     *  - **Tối**: thẻ `#141922` SÁNG hơn nền `#0a0d13` (1.10×) nên tự nổi lên; viền chỉ là hairline trang trí ~1.29:1.
     *    Ép hairline lên 3:1 ở bảng tối cần màu ~`#6b7484` — một đường kẻ xám rõ quanh MỌI thẻ, tức là đổi hẳn thẩm
     *    mỹ prototype mà owner đã duyệt, để chữa một vấn đề bảng tối **không có**.
     *  - **Sáng**: thẻ trắng trên nền sáng chỉ hơn nhau **1.13×** — mắt không đọc ra bước đó ⇒ viền BẮT BUỘC phải
     *    thật (đo được 3.71:1).
     *
     * Bài này khoá **cái tính chất** ("thẻ tách được") thay vì khoá một cơ chế, và nó **tự đảo chiều**: hôm nào ai
     * làm phẳng bước sáng của bảng tối thì nhánh thứ hai hết đúng và bài đòi một viền thật. Đó là điều một danh sách
     * ngoại lệ tĩnh không làm được.
     */
    @Test
    fun `the tach duoc khoi nen o ca hai bang`() {
        forEachPalette { name, p ->
            val border = ratio(over(p.line, p.card), p.card)
            val step = ratio(p.card, p.bg)
            assertTrue(
                border >= 3.0 || step >= 1.10,
                "$name: thẻ KHÔNG tách được khỏi nền — viền ${fmt(border)} (cần ≥ 3.0) và bước sáng ${fmt(step)} " +
                    "(cần ≥ 1.10); phải đạt một trong hai.",
            )
        }
    }

    // ══ (3) ĐỦ HAI BẢN · ĐỦ DÂY NỐI ═══════════════════════════════════════════════════════════════════════

    /**
     * Vai màu **MANG NGHĨA DỮ LIỆU** không được dùng chung một mã cho hai bảng (yêu cầu #3 của T1).
     *
     * Lý do đo được: `#34d399` ("ổn") trên thẻ trắng chỉ **1.8:1**. Dùng chung một mã nghĩa là bảng sáng có một
     * trạng thái mà người dùng **không đọc được**, và nó là đúng nhóm trạng thái cần đọc nhất (cảnh báo lốp, "chưa
     * kiểm trên xe", báo lỗi).
     */
    @Test
    fun `mau mang nghia du lieu co ban rieng cho tung bang`() {
        val shared = MEANING_ROLES.filter { role(KachiPalette.DARK, it) == role(KachiPalette.LIGHT, it) }
        assertEquals(
            emptyList<String>(), shared,
            "vai mang nghĩa dùng CHUNG một mã cho hai bảng ⇒ bảng sáng sẽ có trạng thái không đọc được: $shared",
        )
    }

    /** Hai bảng phải khác nhau ở MỌI vai nền/mực — trùng một vai nghĩa là vai đó chưa được làm cho bảng sáng. */
    @Test
    fun `nen va muc khac nhau giua hai bang`() {
        val shared = (SURFACE_ROLES + ALL_INKS).filter {
            role(KachiPalette.DARK, it) == role(KachiPalette.LIGHT, it)
        }
        assertEquals(emptyList<String>(), shared, "vai nền/mực chưa có bản riêng cho bảng sáng: $shared")
    }

    /** Mọi vai nền và mọi vai mực đều phải nằm trong [textOn] — không có nền nào lặng lẽ ra khỏi phép kiểm. */
    @Test
    fun `moi nen va moi muc deu duoc khai`() {
        assertEquals(
            SURFACE_ROLES.sorted(), textOn.map { it.first }.sorted(),
            "danh sách nền của phép kiểm tương phản lệch với danh sách vai nền",
        )
        val covered = textOn.flatMap { it.second }.toSet()
        assertEquals(emptyList<String>(), ALL_INKS.filterNot { it in covered }, "vai mực không được kiểm ở nền nào")
    }

    /**
     * Mọi thuộc tính của [KachiPalette] phải tra được qua [KachiTheme].
     *
     * Không có bài này thì thêm một vai vào bảng mà quên mở getter ⇒ vai đó **không ai dùng được**, và cách "sửa"
     * nhanh nhất lúc đó lại là viết hex tại chỗ — tức là bài "0 hex" bị bào mòn từ phía sau.
     */
    @Test
    fun `moi vai mau tra duoc qua KachiTheme`() {
        val theme = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiTheme.kt")
        val missing = KachiPalette::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }   // bỏ DARK/LIGHT/Companion
            .map { it.name }
            .filterNot { theme.contains("palette.$it") }
        assertEquals(emptyList<String>(), missing, "vai màu không có getter ở KachiTheme (không ai dùng được): $missing")
    }

    /**
     * ⚠⚠ Ô widget bên thứ ba phải có **nền tối cố định**, không theo chủ đề.
     *
     * [ĐO] 2026-09-12 `emulator-5554`, bảng SÁNG + widget đồng hồ: ô chỉ còn **0.15%** điểm mực tối, chữ giờ gần như
     * biến mất — RemoteViews của app khác dùng chữ TRẮNG theo quy ước "widget nằm trên nền tối", và launcher **không
     * sửa được** màu đó. Cùng ngoại lệ đã ghi cho nút ⇄ (`scrimBtn`): vai nào nằm trên pixel của app khác thì nó
     * phải một mình bảo đảm đọc được.
     */
    @Test
    fun `o widget ben thu ba co nen toi co dinh khong theo chu de`() {
        assertEquals(
            KachiPalette.DARK.widgetBacking, KachiPalette.LIGHT.widgetBacking,
            "vai này CỐ Ý dùng chung mã cho hai bảng — nội dung ô do app khác vẽ, không theo chủ đề của ta",
        )
        // Và nó phải thật sự TỐI: chữ trắng của widget phải đạt 4.5:1 trên nó.
        val r = ratio(KachiPalette.DARK.onAccent, KachiPalette.DARK.widgetBacking)
        assertTrue(r >= 4.5, "chữ trắng của widget trên nền này chỉ ${fmt(r)}:1 — widget sẽ không đọc được")
        // Tầng vẽ phải THẬT SỰ dùng nó ở nhánh widget bên thứ ba (khai một vai mà không ai vẽ = vai chết).
        val view = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt")
        assertTrue(
            "KachiTheme.WIDGET_BACKING" in view,
            "phải vẽ nền tối phía sau widget bên thứ ba, không thì bảng sáng làm widget mất chữ",
        )
        assertTrue(
            "fl.addView(appWidgetBacking(), hostLp)" in view,
            "nền phải nằm DƯỚI view của widget và cùng khung với nó",
        )
    }

    /**
     * ĐÚNG MỘT chỗ ghi bảng màu trong cả `app/src/main`.
     *
     * [KachiTheme.palette] là hình chiếu lúc vẽ của `HomeUiState.themeMode`. Chỗ ghi thứ hai biến nó thành **trạng
     * thái thứ hai** — đúng bẫy "hai bản sao cùng khoá" mà dự án đã sập ba lần (`customLayout` · `unitPrefs` ·
     * `wallpaper`), nhưng lần này bản sao nằm trong một `object` toàn cục nên còn khó thấy hơn.
     */
    @Test
    fun `dung mot cho ap bang mau`() {
        val callers = SourceRoots.moduleSourceRoots()
            .first { it.toString().contains("app") }
            .let { root -> Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() } }
            .filter { code(it).contains("KachiTheme.applyTheme(") }
            .map { it.fileName.toString() }
            .sorted()
        assertEquals(
            listOf("ThemeHost.kt"), callers,
            "phải đúng MỘT chủ sở hữu việc áp bảng màu (ThemeHost); chỗ đọc thì tự do, chỗ GHI thì không. Đang có: $callers",
        )
    }

    // ══ Hạ tầng ═══════════════════════════════════════════════════════════════════════════════════════════

    private fun forEachPalette(block: (String, KachiPalette) -> Unit) {
        block("TỐI", KachiPalette.DARK); block("SÁNG", KachiPalette.LIGHT)
    }

    private fun role(p: KachiPalette, name: String): String =
        KachiPalette::class.java.getDeclaredField(name).apply { isAccessible = true }.get(p) as String

    private fun launcherSources(): List<Path> = SourceRoots
        .moduleSourceRoots().first { it.toString().contains("app") }
        .resolve("com/byd/clusternav/launcher")
        .let { dir -> Files.list(dir).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() } }
        .sortedBy { it.fileName.toString() }

    private fun code(f: Path): String = f.toFile().readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { it.substringBefore("//") }

    private fun fmt(v: Double) = String.format("%.2f", v)

    /** `#RRGGBB` hoặc `#AARRGGBB` → (a, r, g, b). */
    private fun argb(hex: String): IntArray {
        val h = hex.removePrefix("#")
        require(h.length == 6 || h.length == 8) { "mã màu không hợp lệ: $hex" }
        fun at(i: Int) = h.substring(i, i + 2).toInt(16)
        return if (h.length == 6) intArrayOf(255, at(0), at(2), at(4))
        else intArrayOf(at(0), at(2), at(4), at(6))
    }

    /** Trộn [fg] (có thể có kênh trong suốt) lên [bg] → mã đặc `#RRGGBB`. */
    private fun over(fg: String, bg: String): String {
        val f = argb(fg); val b = argb(bg); val a = f[0] / 255.0
        fun mix(i: Int) = (f[i] * a + b[i] * (1 - a)).toInt().coerceIn(0, 255)
        return "#%02x%02x%02x".format(mix(1), mix(2), mix(3))
    }

    /** Độ chói tương đối theo WCAG 2.x. */
    private fun luminance(hex: String): Double {
        val c = argb(hex)
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch(c[1]) + 0.7152 * ch(c[2]) + 0.0722 * ch(c[3])
    }

    /**
     * Tỉ số tương phản WCAG. Màu có kênh trong suốt được TRỘN lên [bg] trước — bỏ bước này thì mọi màu alpha đo ra
     * một con số không tồn tại trên màn.
     */
    private fun ratio(fg: String, bg: String): Double {
        val f = luminance(if (argb(fg)[0] < 255) over(fg, bg) else fg)
        val b = luminance(bg)
        val hi = maxOf(f, b); val lo = minOf(f, b)
        return (hi + 0.05) / (lo + 0.05)
    }

    private companion object {
        /** Vai MỰC — thứ được vẽ dưới dạng chữ hoặc icon một màu. */
        val ALL_INKS = listOf("ink", "ink2", "mut", "mut2", "icon", "accentInk", "green", "amber", "red", "cyan", "orange", "slate")

        /** Vai NỀN — thứ chữ nằm lên. */
        val SURFACE_ROLES = listOf("bg", "card", "card2", "panel", "field", "cell", "tile", "slot", "chipOff", "headBg", "emptyFill", "dim")

        /** Vai màu mang NGHĨA của dữ liệu (ổn · chưa kiểm · cảnh báo · không khí · nhạc · trung tính · nhấn). */
        val MEANING_ROLES = listOf("green", "amber", "red", "cyan", "orange", "slate", "accent", "accent2")
    }
}
