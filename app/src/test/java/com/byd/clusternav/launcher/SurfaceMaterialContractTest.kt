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
        val offenders = launcherSources().mapNotNull { f ->
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
     * không chạm tới lớp mép sáng.
     */
    @Test
    fun `nhanh SUNKEN giu phang va thoat som`() {
        val body = SourceRoots.body(theme(), "fun surface(")
        val sunken = body.substringAfter("SurfaceTone.SUNKEN) return").substringBefore("val active")
        assertTrue("setColor(c(FIELD_SUNKEN))" in sunken, "ô lõm phải là MỘT tô đặc bằng vai fieldSunken")
        assertTrue(
            "Orientation" !in sunken,
            "ô lõm KHÔNG được có gradient — lõm và lồi phải khác nhau ở cơ chế, không chỉ ở con số",
        )
    }

    /** Ba lớp của bề mặt lồi: gradient DỌC · (tuỳ chọn) sắc lĩnh vực · mép sáng ở ĐỈNH, gom bằng `LayerDrawable`. */
    @Test
    fun `be mat loi co du lop va chuyen sac DOC`() {
        val body = SourceRoots.body(theme(), "fun surface(")
        assertTrue("LayerDrawable(" in body, "ba lớp phải gom bằng LayerDrawable, không vẽ tay trong onDraw")
        assertEquals(
            2, Regex("""Orientation\.TOP_BOTTOM""").findAll(body).count(),
            "cả nền LẪN mép sáng phải là chuyển sắc DỌC. Chéo (TL_BR) là nhận diện của 'cái đang được chọn' " +
                "(KachiTheme.gradient) — hai vai đó không được lẫn nhau.",
        )
        assertTrue("Gravity.TOP" in body, "mép sáng phải nằm ở ĐỈNH (một nguồn sáng cho cả hệ)")
        assertTrue("setLayerHeight(" in body, "mép sáng phải bị giới hạn chiều cao, không phủ cả thẻ")
        // [SOÁT Pass 4] Dải mờ dần một mình chỉ đọc ra "hơi sáng ở trên". Nét ĐẶC ở đúng đỉnh mới cho ra mặt vát,
        // và nó phải DÀY HƠN trên thẻ lớn — 2dp trên ô 40dp thì đọc thành viền, không thành ánh sáng.
        assertTrue(
            "KachiSpace.RADIUS_XL" in body && "hair * 2" in body,
            "phải có nét đỉnh ĐẶC dày gấp đôi trên thẻ lớn (bán kính ≥ RADIUS_XL) — xem chú thích trong surface()",
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
