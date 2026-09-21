package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.toList

/**
 * ═══ VISUAL-REFRESH P1b · §4.10 — BÀI CANH THẺ KÍNH ═══════════════════════════════════════════════════════════
 *
 * Sáu quyết định của §4.10, mỗi quyết định một bài: (1) ảnh mờ tính MỘT LẦN ở thread nền · (2) thẻ = cửa sổ + lớp
 * che · (3) hai dải che chỉ khi có ảnh · (4) màu trội tính cùng lượt · (5) lớp che theo độ chói đo được (bài số ở
 * `GlassVeilTest`; ở đây đo trên bảng màu THẬT) · (6) 0 blur lúc chạy (`SurfaceMaterialContractTest` đã canh; ở đây
 * canh thêm "không cấp phát trong draw"). Cộng một bất biến quan trọng nhất: **không có ảnh nền thì không đổi gì**.
 */
class WallGlassContractTest {

    private val glass = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WallGlass.kt")
    private val art = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WallArt.kt")
    private val ctrl = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WallpaperController.kt")
    private val wall = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WallView.kt")

    @Test
    fun `khong co anh nen thi nen the la dung KachiTheme surface nhu cu`() {
        val paint = SourceRoots.body(glass, "private fun paint(")
        assertTrue(
            Regex("""if \(art == null[^\n]*\{[\s\S]*?view\.background = KachiTheme\.surface\(ctx, spec\.radius, spec\.tone, spec\.domain\)""")
                .containsMatchIn(paint),
            "không có WallArt ⇒ phải rơi về đúng KachiTheme.surface(radius, tone, domain) — người không dùng hình nền không thấy gì khác",
        )
        assertTrue("SurfaceTone.SUNKEN" in paint, "ô lõm không bao giờ là kính")
    }

    @Test
    fun `moi mat kinh deu di qua KachiGlass va co it nhat bon cho goi`() {
        val callers = launcherSources().filter { it.fileName.toString() != "WallGlass.kt" }
            .filter { "KachiGlass.apply(" in code(it) }.map { it.fileName.toString() }
        assertTrue(callers.size >= 3, "KachiGlass.apply phải có chỗ gọi thật (CLAUDE.md §8): $callers")
        assertTrue("WorkspaceView.kt" in callers, "khay ô làm việc — bề mặt lớn nhất — phải là kính")
        // ⚠ 2026-09-21 — thẻ widget dời tệp: khung ô nén / ô con bảng tổng hợp (`MiniCard`/`BoardCell`) tách khỏi
        // `WidgetViews.kt` sang `WidgetTelemetry.kt` khi tệp đó vượt trần 500 dòng. Đổi MỐC theo tệp mới, KHÔNG nới
        // phép kiểm: vẫn đòi đúng hai bề mặt (thẻ nhóm + thẻ widget) phải là kính.
        assertTrue("GroupTileViews.kt" in callers && "WidgetTelemetry.kt" in callers, "thẻ nhóm + thẻ widget phải là kính")
        val activity = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt")
        assertTrue("KachiGlass.refresh(rootFrame)" in activity, "ảnh đổi ⇒ dựng lại nền kính tại chỗ, không recreate")
    }

    @Test
    fun `anh mo tinh MOT LAN o thread nen, tu chinh anh vua giai ma`() {
        val st = SourceRoots.body(ctrl, "fun step(")
        assertTrue("WallArtBuilder.build(" in st, "step() phải nấu WallArt")
        assertTrue(st.indexOf("submitIo") < st.indexOf("WallArtBuilder.build("), "nấu ảnh mờ phải nằm TRONG khối thread nền")
        assertTrue(st.indexOf("WallArtBuilder.build(") < st.indexOf("onUi {"), "…và TRƯỚC khi về thread chính")
        assertTrue("WallArtStore.swap(" in st && "onArtChanged()" in st, "đổi kho rồi báo để dựng lại nền kính")
        assertTrue(st.indexOf("onArtChanged()") < st.indexOf("oldArt?.blurred?.recycle()"), "nhả ảnh mờ CŨ chỉ sau khi đã dựng lại nền")
        // Chỉ WallpaperController được ghi kho (một chỗ ghi).
        val writers = launcherSources().filter { f ->
            val c = code(f); "WallArtStore.swap(" in c || "WallArtStore.clear(" in c
        }.map { it.fileName.toString() }
        assertEquals(listOf("WallpaperController.kt"), writers, "WallArtStore chỉ có MỘT chỗ ghi")
        assertTrue("CACHE_DIR" in art && "loadCached(" in art && "saveCached(" in art, "phải có bộ đệm đĩa (tính một lần)")
        assertTrue("DominantColors.of(" in art, "màu trội tính CÙNG lượt (§4.10 mục 4)")
    }

    @Test
    fun `khong cap phat trong draw cua cua so kinh`() {
        val draw = SourceRoots.body(glass, "override fun draw(")
        listOf("Paint(", "RectF(", "Matrix(", "IntArray(", "BitmapShader(", "getLocationInWindow").forEach {
            assertFalse(it in draw, "draw() không được cấp phát/đo lại mỗi khung: $it")
        }
        val relocate = SourceRoots.body(glass, "fun relocate(")
        assertTrue("getLocationInWindow" in relocate && "GlassVeil.alphaFor(" in relocate, "đo vị trí + độ chói ở relocate(), không ở draw()")
        // Cổng "không đổi thì không làm gì" phải xét CẢ CỠ: độ chói được đo trên hình chữ nhật `loc … loc + (w, h)`,
        // nên thẻ đổi cỡ TẠI CHỖ nhìn xuống vùng ảnh khác. Cổng chỉ-xét-vị-trí giữ lớp che của vùng cũ, im lặng.
        //
        // ⚠ Đọc CHÍNH điều kiện của lệnh thoát sớm, không chỉ đếm chuỗi trong cả thân hàm: `lastW`/`lastH` cũng xuất
        // hiện ở dòng GÁN, nên phép đếm cả-thân vẫn xanh khi cổng bị hạ về hai biến — [ĐO] thử phá 2026-09-17.
        val guard = Regex("""if \(([^)]*)\) return""").find(relocate)?.groupValues?.get(1).orEmpty()
        listOf("lastX", "lastY", "lastW", "lastH").forEach {
            assertTrue(it in guard, "cổng thoát sớm của relocate() phải xét cả vị trí VÀ cỡ; thiếu $it: «$guard»")
        }
    }

    /**
     * Nền KHÔNG-kính phải đi qua [KachiGlass.plain] — gán thẳng `view.background` để lại thẻ `kachi_glass_spec`.
     *
     * ⚠ Ô con của nhóm **DÙNG LẠI view** và đổi sắc thái theo nhịp trạng thái (1 Hz). Nếu thẻ kính của lượt
     * NEUTRAL/ACTIVE trước còn trên view thì lượt [KachiGlass.refresh] kế tiếp (trình chiếu đổi ảnh mỗi 15 s) đắp
     * kính **đè lên nền đang mang cảnh báo** — và ở WARN/ALERT thì *màu nền chính là thông tin*. Lỗi im lặng, chỉ
     * hiện sau vài chục giây, nên nó phải có bài canh chứ không chỉ có bản vá.
     */
    @Test
    fun `nen khong-kinh cua o con nhom di qua KachiGlass plain`() {
        val plain = SourceRoots.body(glass, "fun plain(")
        assertTrue("setTag(R.id.kachi_glass_spec, null)" in plain, "plain() phải GỠ thẻ kính")
        assertTrue("unbind(view)" in plain, "plain() phải tháo listener layout/cuộn của lượt kính trước")
        assertTrue(plain.indexOf("unbind(view)") < plain.indexOf("view.background = background"), "gỡ trước, đặt nền sau")
        val tiles = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/GroupTileViews.kt")
        val surfaceOf = SourceRoots.body(tiles, "fun surfaceOf(")
        assertTrue("KachiGlass.plain(" in surfaceOf, "nhánh WARN/ALERT phải qua KachiGlass.plain")
        assertFalse(
            Regex("""view\.background\s*=""").containsMatchIn(surfaceOf),
            "không được gán thẳng view.background trong surfaceOf — thẻ kính của lượt trước sẽ ở lại",
        )
    }

    @Test
    fun `hai dai che chi ve khi co anh`() {
        val bands = Regex("""drawBands\(canvas,""").findAll(wall).count()
        assertEquals(1, bands, "drawBands gọi đúng một chỗ")
        val photo = SourceRoots.body(wall, "private fun drawPhoto(")
        assertTrue("drawBands(canvas" in photo, "dải che chỉ nằm trong nhánh có ảnh — nền vẽ sẵn không đổi một pixel")
        val onDraw = SourceRoots.body(wall, "override fun onDraw(")
        assertFalse("drawBands" in onDraw, "nhánh mặc định (không ảnh) không được vẽ dải")
        assertTrue("KachiTheme.BAR_TOP" in wall, "dải che lấy màu từ bảng màu (theo chủ đề)")
        assertTrue("override fun onSizeChanged" in wall && "LinearGradient(" in SourceRoots.body(wall, "override fun onSizeChanged"),
            "shader dải che dựng khi đổi cỡ, không dựng mỗi khung")
    }

    @Test
    fun `phep phu vua khung dung chung giua WallView va WallArtBuilder`() {
        assertTrue("WallFit.map(" in wall && "WallFit.map(" in art, "hai bản phép phủ là hai chỗ để lệch nhau")
        assertFalse("ImageFit.FILL ->" in wall, "WallView không được giữ bản phép phủ riêng")
    }

    /** §4.10 mục (5) đo trên bảng màu THẬT: với mọi độ chói ảnh và mọi tone kính, lớp che đưa mực tệ nhất qua sàn. */
    @Test
    fun `lop che du cho moi do choi anh, moi tone, hai bang`() {
        val bad = mutableListOf<String>()
        listOf("TỐI" to ThemeMode.NIGHT, "SÁNG" to ThemeMode.DAY).forEach { (name, mode) ->
            KachiTheme.applyTheme(mode, 12, ColorChoice.DEFAULT, null)
            val p = KachiTheme.palette
            // Cùng số với `KachiGlass.paint`/`KachiTheme.surfacePair` (không gọi chúng: `Color.parseColor` là Android).
            val veil = ColorMath.parse(p.bg)
            // Cùng hợp đồng mực với `KachiGlass.veilInks`: thẻ thường/khay đỡ ba mực, thẻ BẬT chỉ hứa `ink`.
            fun inksOf(tone: SurfaceTone) = if (tone == SurfaceTone.ACTIVE) intArrayOf(ColorMath.parse(p.ink))
            else intArrayOf(ColorMath.parse(p.mut), ColorMath.parse(p.mut2), ColorMath.parse(p.ink))
            val pairs = mapOf(
                SurfaceTone.NEUTRAL to intArrayOf(ColorMath.parse(p.surfFromOverArt), ColorMath.parse(p.surfToOverArt)),
                SurfaceTone.WELL to intArrayOf(ColorMath.scaleAlpha(ColorMath.parse(p.slot), 0.8), ColorMath.scaleAlpha(ColorMath.parse(p.slotTo), 0.8)),
                SurfaceTone.ACTIVE to intArrayOf(ColorMath.scaleAlpha(ColorMath.parse(p.surfOnFrom), 0.8), ColorMath.scaleAlpha(ColorMath.parse(p.surfOnTo), 0.8)),
            )
            pairs.forEach { (tone, surfaces) ->
                val inks = inksOf(tone)
                var l = 0.0
                while (l <= 1.0) {
                    val a = GlassVeil.alphaFor(l, veil, surfaces, inks)
                    val worst = GlassVeil.worstRatio(a, ColorMath.grayOfLuminance(l), veil, surfaces, inks)
                    if (worst < 4.5) bad += "$name $tone l=${"%.2f".format(l)} alpha=${"%.2f".format(a)} worst=${"%.2f".format(worst)}"
                    l += 0.05
                }
            }
        }
        KachiTheme.applyTheme(ThemeMode.NIGHT, 12, ColorChoice.DEFAULT, null)
        assertEquals(emptyList<String>(), bad, "lớp che tới trần vẫn không đủ cho mực tệ nhất: $bad")
    }

    @Test
    fun `khoa moi color_choice co mat o tung mat xich luu ben`() {
        assertTrue("color_choice" in ProfileScope.LAUNCHER_PERSONAL_SUFFIXES)
        assertEquals(SettingsGroup.DISPLAY, SettingsCatalog.groupOf("color_choice"))
    }

    private fun launcherSources(): List<Path> = SourceRoots
        .moduleSourceRoots().first { it.toString().contains("app") }
        .resolve("com/byd/clusternav/launcher")
        .let { dir -> Files.list(dir).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() } }
        .sortedBy { it.fileName.toString() }

    private fun code(f: Path): String = f.toFile().readText()
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { it.substringBefore("//") }
}
