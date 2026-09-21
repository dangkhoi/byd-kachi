package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ VISUAL-REFRESH P1 · T2 — BÀI CANH CHẤT LIỆU BỀ MẶT ══════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-visual-refresh.html` §R3 (AC3.5) + §R5 (AC5.2/AC5.3).
 *
 * ## Vì sao cần một bài RIÊNG, không gộp vào [ThemePaletteContractTest]
 * Bài kia canh **con số** (tương phản tính từ bảng màu). Bài này canh **cách vẽ**: một bảng màu hoàn hảo vẫn đi
 * kèm được một `setShadowLayer` làm tụt khung hình trên GPU TRINKET, và ngược lại. Hai loại lỗi khác nhau ⇒ hai
 * bài, để khi một cái đỏ thì biết ngay phải sửa ở đâu.
 *
 * ## ⚠ Vì sao cấm blur/shadow/elevation lại là ràng buộc KỸ THUẬT chứ không phải thẩm mỹ
 * Owner 2026-09-16 đã **bỏ mọi luật cấm thẩm mỹ** (*"cho gradient hay làm sao cho đẹp được thì làm, không cần
 * rule cấm gì đâu"*). Bốn thứ dưới đây **không** nằm trong nhóm đó: chúng bắt GPU vẽ thêm một lượt off-screen mỗi
 * khung, trên đầu máy DiLink chạy GPU TRINKET. Cảm giác "lồi" của [KachiTheme.surface] đến từ chênh sáng **trong
 * chính gradient** ⇒ 0 chi phí thêm so với một tô đặc.
 */
class SurfaceMaterialContractTest {

    /**
     * Bốn hiệu ứng tốn GPU **không được xuất hiện** trong tầng vẽ launcher.
     *
     * Miễn trừ giữ nguyên cho `speedbadge/` và bong bóng Cast — hai bề mặt đó đã chạy ổn trên xe từ trước, và
     * CLAUDE.md §6 cấm *"đảo thứ tự đường đã chạy tốt ngoài hiện trường"*. Chúng nằm ngoài `launcher/` nên phép
     * quét dưới đây tự nhiên không chạm tới; ghi ra đây để lần sau không ai "dọn cho đều".
     */
    @Test
    fun `0 blur 0 shadow 0 elevation trong tang ve launcher`() {
        val banned = Regex("""\bsetShadowLayer\b|\bBlurMaskFilter\b|\bRenderEffect\b|\bsetMaskFilter\b|\belevation\s*=|\bsetElevation\s*\(""")
        // ⚠ UX-OVERHAUL WP1 · R1.3 — `KachiGlassMode.kt` là ngoại lệ DUY NHẤT: glass THẬT (RenderEffect) là đường
        // opt-in, có công tắc `ui_glass_real` + API-gate 31 + mặc định TẮT + chờ đo trên xe. Glass GIẢ (mặc định,
        // 0 blur) vẫn là con đường của mọi máy và là con đường DUY NHẤT trên xe API 29. Cùng lối miễn trừ
        // `speedbadge/` — bề mặt có lý do riêng, ghi ra để lần sau không "dọn cho đều".
        val exempt = setOf("KachiGlassMode.kt")
        val offenders = launcherSources().filter { it.fileName.toString() !in exempt }.mapNotNull { f ->
            val found = banned.findAll(code(f)).map { it.value.trim() }.toList()
            if (found.isEmpty()) null else "${f.fileName}: ${found.distinct()}"
        }
        assertEquals(
            emptyList<String>(), offenders,
            "hiệu ứng tốn GPU trong tầng launcher (AC5.2). Đầu máy chạy GPU TRINKET: mỗi thứ trong bốn cái này bắt " +
                "vẽ thêm một lượt off-screen MỖI KHUNG. Muốn thẻ 'lồi' thì dùng KachiTheme.surface() — chênh sáng " +
                "trong gradient cho cùng cảm giác với 0 chi phí thêm: $offenders",
        )
    }

    /** Hàm mới phải có **chỗ gọi thật** (CLAUDE.md §8 — `CastShell.evictVd` compile sạch mà chưa ai gọi lần nào). */
    @Test
    fun `surface co cho goi that ngoai dinh nghia`() {
        val callers = launcherSources()
            .filter { it.fileName.toString() != "KachiTheme.kt" }
            .filter { "KachiTheme.surface(" in code(it) || "KachiTheme.surface\n" in code(it) }
            .map { it.fileName.toString() }
        assertTrue(
            callers.size >= 5,
            "KachiTheme.surface() phải có ≥ 5 chỗ gọi ngoài định nghĩa (T3 rà 26 chỗ gọi card()); đang có: $callers",
        )
    }

    /**
     * **MỌI** tone phải có chỗ gọi thật — CLAUDE.md §8, phiên bản chặt hơn bài ở trên.
     *
     * [ĐO · SOÁT Pass 4] Bài *"≥ 5 chỗ gọi"* đếm chỗ gọi `surface(` nói chung, nên nó **xanh** kể cả khi một
     * `SurfaceTone` chưa ai dùng tới. Đó đúng là cách `SurfaceTone.WELL` có thể sinh ra rồi nằm chết — cùng họ lỗi
     * `CastShell.evictVd` mà CLAUDE.md §8 viết ra để chặn. Duyệt theo **tên hằng của enum**, nên thêm một tone mới
     * mà quên nối dây là đỏ ngay, không phải chờ ai nhớ cập nhật một danh sách.
     */
    @Test
    fun `moi SurfaceTone deu co cho goi that`() {
        val callers = launcherSources()
            .filter { it.fileName.toString() != "KachiTheme.kt" }
            .joinToString("\n") { code(it) }
        val unused = SurfaceTone.values().map { it.name }.filterNot { "SurfaceTone.$it" in callers }
        assertEquals(
            emptyList<String>(), unused,
            "SurfaceTone khai ra mà KHÔNG chỗ vẽ nào dùng ⇒ vai chết (CLAUDE.md §8): $unused",
        )
    }

    /**
     * Ô làm việc ở màn chính — **bề mặt lớn nhất** — phải đi qua bộ dựng bề mặt, không dựng nền tại chỗ.
     *
     * [ĐO · SOÁT Pass 4] Đây là chỗ lượt P1 hụt: bảng rà 26 chỗ gọi của §9 đi từ chuỗi `KachiTheme.card(`, còn ô
     * làm việc dựng `GradientDrawable` thẳng tại chỗ nên nó không nằm trong bảng rà — và ảnh máy ảo trước/sau P1
     * cho đúng cùng một giá trị điểm ảnh ở giữa ô. Bài này khoá cái **kết quả** (ô làm việc dùng tone KHAY), không
     * khoá cái quy trình.
     */
    @Test
    fun `o lam viec dung tone KHAY khong dung nen tu dung tai cho`() {
        val src = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt")
        val makeSlot = SourceRoots.body(src, "private fun makeSlot(")
        assertTrue(
            "SurfaceTone.WELL" in makeSlot,
            "nền ô làm việc phải là KachiTheme.surface(..., SurfaceTone.WELL, ...) — nếu không thì bề mặt lớn " +
                "nhất màn hình đứng ngoài hệ chất liệu và cả lượt refresh 'nhìn không ra'",
        )
        // Ô TRỐNG là ngoại lệ có chủ ý (gạch đứt + `emptyFill`), nên chỉ được còn ĐÚNG một `GradientDrawable`.
        assertEquals(
            1, Regex("""GradientDrawable\(\)""").findAll(makeSlot).count(),
            "chỉ ô TRỐNG được dựng nền tại chỗ (gạch đứt); mọi loại ô khác đi qua KachiTheme.surface()",
        )
    }

    /**
     * [SurfaceTone.SUNKEN] phải vẽ bằng **cơ chế khác**, không phải cùng gradient với một màu khác.
     *
     * Lồi và lõm mà chỉ khác nhau ở độ sáng thì ở góc nhìn nghiêng trong cabin (màn 1920 nằm ngang, mắt người lái
     * ở trên) hai vai đọc như một. Nhánh SUNKEN vì thế trả về một `GradientDrawable` tô ĐẶC và **thoát sớm** —
     * không chạm tới chuyển sắc.
     *
     * ## ⚠ Hai MỐC cắt vùng được **khẳng định tồn tại** trước khi cắt
     * Bản trước cắt tới `"val active"`. WP1 gỡ nhánh mép nên biến đó biến mất, mà `substringBefore` **trả nguyên
     * phần còn lại** khi không thấy mốc ⇒ vùng quét trùm cả nhánh gradient bên dưới và bài đỏ ở một dòng chẳng ai
     * hiểu vì sao. Đây đúng họ lỗi "44 phép cắt vùng" đã ghi trong project-context: mốc không tồn tại thì phải NỔ
     * ngay, không được âm thầm nới vùng quét.
     */
    @Test
    fun `nhanh SUNKEN giu phang va thoat som`() {
        val body = SourceRoots.body(theme(), "fun surface(")
        val open = "SurfaceTone.SUNKEN) return"
        val close = "val base"
        listOf(open, close).forEach {
            assertTrue(it in body, "mốc cắt vùng `$it` không còn trong surface() — sửa bài này, đừng để nó quét tràn")
        }
        val sunken = body.substringAfter(open).substringBefore(close)
        assertTrue("setColor(c(FIELD_SUNKEN))" in sunken, "ô lõm phải là MỘT tô đặc bằng vai fieldSunken")
        assertTrue(
            "Orientation" !in sunken,
            "ô lõm KHÔNG được có gradient — lõm và lồi phải khác nhau ở cơ chế, không chỉ ở con số",
        )
        assertTrue("setStroke" !in sunken, "ô lõm cũng không có viền (WP1 · R1.1) — nó lõm bằng MÀU, không bằng kẻ")
    }

    /**
     * Bề mặt lồi: **chỉ** chuyển sắc DỌC + (tuỳ chọn) một lớp sắc lĩnh vực, gom bằng `LayerDrawable`.
     *
     * ## ⚠⚠ UX-OVERHAUL WP1 · iteration (2026-09-20) — GỠ HẲN MÉP + VIỀN
     * Bản WP1 sáng cùng ngày đảo Pass-5 và **cho phép** mép kính 1px (`Gravity.TOP` + `setLayerHeight`), chỉ chặn
     * lớp dày. Owner xem ảnh: *"bị bug gạch trên đầu mỗi khung, bỏ viền đi luôn"* ⇒ bài này đảo lại lần nữa và nay
     * **CẤM** cả mép 1px lẫn mọi `setStroke`. Lý do đầy đủ (vì sao cấm hình dạng thay vì khoá cường độ) ở KDoc
     * [KachiTheme.surface] — tóm gọn: đã thử khoá cường độ, vẫn bị chê, vì cái sai là HÌNH DẠNG.
     *
     * Vẫn giữ: đúng MỘT chuyển sắc DỌC (chéo `TL_BR` là "cái đang được chọn"), gom bằng `LayerDrawable`, 0 blur/
     * bóng/elevation (bài `0 blur 0 shadow 0 elevation`).
     */
    @Test
    fun `be mat loi khong con vien hay mep`() {
        val body = SourceRoots.body(theme(), "fun surface(")
        assertTrue(
            "LayerDrawable(" in body,
            "sắc lĩnh vực phải gom bằng LayerDrawable (lớp thứ hai chồng lên nền), không vẽ tay trong onDraw",
        )
        assertEquals(
            1, Regex("""Orientation\.TOP_BOTTOM""").findAll(body).count(),
            "nền thẻ phải là ĐÚNG MỘT chuyển sắc DỌC (mặt kính). Chéo (TL_BR) là nhận diện của 'cái đang được chọn' " +
                "(KachiTheme.gradient) — hai vai đó không được lẫn nhau.",
        )
        assertEquals(
            0, Regex("""Orientation\.TL_BR""").findAll(body).count(),
            "bề mặt KHÔNG được dùng chuyển sắc chéo — đó là vai của KachiTheme.gradient (cái đang được chọn)",
        )
        // WP1 iteration: KHÔNG mép, KHÔNG viền — bất kể độ dày hay alpha. Ba lượt trước (Pass-4 surfEdge, Pass-5 nét
        // đỉnh, WP1 bevel 1px) đều chết vì cùng một hình dạng: một dải ghim vào cạnh thẻ.
        val banned = listOf(
            "setStroke", "Gravity.TOP", "Gravity.BOTTOM", "setLayerHeight", "setLayerGravity", "setLayerInset",
        ).filter { it in body }
        assertEquals(
            emptyList<String>(), banned,
            "surface() mọc lại viền/mép. Bốn tone phân biệt nhau CHỈ bằng MÀU FILL (surfacePair), chiều nổi do " +
                "chuyển sắc DỌC gánh. Đang có: $banned",
        )
    }

    /**
     * Sắc lĩnh vực chỉ được lấy qua **một** cửa.
     *
     * Chỗ vẽ tự `when (domain)` là cách một bảng màu thứ hai ra đời (CLAUDE.md §7: khác biệt phải lộ ra qua đo
     * đạc/bảng tra, không rải `if` theo tên). [KachiPalette.domainTints] là bảng đó.
     */
    @Test
    fun `sac linh vuc chi tra qua mot cua`() {
        val offenders = launcherSources()
            .filter { it.fileName.toString() !in setOf("KachiTheme.kt", "KachiPalette.kt") }
            .mapNotNull { f ->
                val c = code(f)
                if (Regex("""when\s*\(\s*domain\s*\)""").containsMatchIn(c)) f.fileName.toString() else null
            }
        assertEquals(
            emptyList<String>(), offenders,
            "rẽ nhánh màu theo Domain ngoài KachiPalette.domainTints ⇒ bảng màu thứ hai: $offenders",
        )
    }

    /**
     * Mỗi lượt gọi dựng một `Drawable` MỚI — **không** cache dùng chung giữa các View.
     *
     * `Drawable` dùng chung chia nhau một `ConstantState`: đổi bounds/alpha ở một ô là đổi cả những ô kia. Bài này
     * khoá đúng cái cám dỗ "tối ưu" đó, vì nó compile sạch và hỏng theo kiểu rất khó truy.
     */
    @Test
    fun `surface khong cache Drawable dung chung`() {
        val theme = code(SourceRoots.path("src/main/java/com/byd/clusternav/launcher/KachiTheme.kt"))
        val cached = Regex("""(?:private\s+)?val\s+\w*[Ss]urface\w*\s*(?::\s*\w*Drawable\w*)?\s*=\s*(?:LayerDrawable|GradientDrawable)""")
        assertTrue(
            !cached.containsMatchIn(theme),
            "KachiTheme không được giữ sẵn một Drawable bề mặt dùng chung — xem KDoc surface() về ConstantState",
        )
    }

    private fun theme(): String = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiTheme.kt")

    private fun launcherSources(): List<Path> = SourceRoots
        .moduleSourceRoots().first { it.toString().contains("app") }
        .resolve("com/byd/clusternav/launcher")
        .let { dir -> Files.walk(dir).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() } }
        .sortedBy { it.fileName.toString() }

    private fun code(f: Path): String = f.toFile().readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { it.substringBefore("//") }
}
