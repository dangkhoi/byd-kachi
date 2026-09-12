package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CANH nguồn duy nhất của bố cục (P9) và **nguồn sự thật duy nhất của state bố cục** (lượt soát 2026-09-11).
 *
 * ## Vì sao cần canh
 * [ĐO] trước P9 bước 2 có **sáu chỗ** tự suy ra số ô / khung pixel từ bố cục sẵn: ba ở màn chính (dựng ô, đo cỡ, đặt
 * chỗ) và ba ở bộ sắp cửa sổ app. Thêm bố cục tự vẽ mà bỏ sót một chỗ thì **màn hình vẽ theo bố cục mới nhưng cửa sổ
 * app đặt theo bố cục cũ** ⇒ app nằm lệch khỏi ô — đúng hình dạng P-bug2.
 *
 * ## Ba điểm mù của bản canh cũ (lượt soát độc lập tìm ra, nay đã bịt)
 *  1. **danh sách tệp cứng 2 mục** ⇒ tệp MỚI ở phía app (bảng vẽ bố cục, màn chính…) tự suy ra số ô thì không bài nào
 *     đỏ. Nay quét **mọi** tệp trong `launcher/`.
 *  2. **regex khoảng-ô-cứng chỉ khớp đúng một khoảng trắng và không biết `..<`** ⇒ `0 until  6` / `0..<6` lọt.
 *  3. **đếm số lần gán biến** ⇒ chỉ chặn được "nhiều nơi ghi", KHÔNG chặn được nguyên nhân gốc là **có bản sao**.
 *     Nay canh trực tiếp: tầng UI không được có field riêng, và không được ghi bền trực tiếp.
 */
class GridSeamGuardTest {

    private fun strip(src: String): String = src
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { it.substringBefore("//") }

    private fun code(relative: String): String = strip(SourceRoots.text(relative))

    /** Dùng helper CHUNG (core testFixtures) — trước đây mỗi tệp test có bản riêng, dễ lệch nhau. */
    private fun body(src: String, signature: String) = SourceRoots.body(src, signature)

    /** Hai tệp BẮT BUỘC phải THỰC SỰ dùng nguồn duy nhất (chúng vẽ ô và đặt cửa sổ app). */
    private val files = listOf(
        "src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt",
        "src/main/java/com/byd/clusternav/launcher/LauncherWindows.kt",
    )

    /** MỌI tệp Kotlin trong `launcher/` phía app → nội dung đã bỏ chú thích. Không danh sách cứng để bỏ sót. */
    private fun everyLauncherFile(): List<Pair<String, String>> {
        val dir = SourceRoots.path("src/main/java/com/byd/clusternav/launcher")
        Files.list(dir).use { stream ->
            return stream.filter { it.fileName.toString().endsWith(".kt") }
                .map { it.fileName.toString() to strip(it.toFile().readText()) }
                .toList()
        }
    }


    // ── Nguồn duy nhất cho HÌNH HỌC bố cục ───────────────────────────────────────────────────────

    @Test
    fun `khong ai duoc tu tinh khung pixel tu bo cuc san`() {
        everyLauncherFile().forEach { (name, src) ->
            val n = Regex("WorkspaceLayout\\.slots\\(").findAll(src).count()
            assertEquals(
                0, n,
                "$name phải đi qua EffectiveLayout.rects(...) — tự gọi WorkspaceLayout.slots là bỏ qua bố cục tự vẽ",
            )
        }
    }

    @Test
    fun `khong ai duoc tu doc so o tu bo cuc san`() {
        everyLauncherFile().forEach { (name, src) ->
            val n = Regex("preset\\.slotCount").findAll(src).count()
            assertEquals(
                0, n,
                "$name phải đi qua EffectiveLayout.slotCount(...) — đọc preset.slotCount là bỏ qua bố cục tự vẽ",
            )
        }
    }

    @Test
    fun `khong ai duoc viet CUNG khoang o`() {
        // [SOÁT P1-3] bản canh trước chỉ chặn hai TÊN hàm, nên vòng lặp viết cứng `0..3` vẫn lọt và app ở khung 5/6
        // không được sắp lại. Regex nay khớp cả `0..5`, `0..<6`, `0 until 6`, `0 until  6`.
        val bad = Regex("0\\s*\\.\\.<?\\s*[0-9]+|0\\s+until\\s+[0-9]+")
        everyLauncherFile().forEach { (name, src) ->
            val hits = bad.findAll(src).map { it.value }.toList()
            assertTrue(
                hits.isEmpty(),
                "$name có khoảng ô viết cứng $hits — phải dùng WorkspaceState.SLOT_CAP hoặc số ô thực tế",
            )
        }
    }

    @Test
    fun `ca hai phia deu THUC SU dung nguon duy nhat`() {
        // Chặn cách "đạt test" bằng việc xoá lệnh gọi đi mà không thay bằng gì.
        files.forEach { f -> assertTrue(code(f).contains("EffectiveLayout."), "$f phải dùng nguồn duy nhất") }
    }

    @Test
    fun `bo sap cua so doc bo cuc tu ve qua HAM, khong phai gia tri chup san`() {
        // Nhận giá trị chụp sẵn thì đổi bố cục xong app bị đặt theo bố cục CŨ.
        val src = code("src/main/java/com/byd/clusternav/launcher/LauncherWindows.kt")
        assertTrue(
            Regex("custom:\\s*\\(\\)\\s*->\\s*GridLayout\\?").containsMatchIn(src),
            "phải là hàm () -> GridLayout? để luôn đọc giá trị mới nhất",
        )
    }

    // ── Nguồn sự thật duy nhất cho STATE bố cục (lượt soát 2026-09-11) ───────────────────────────

    @Test
    fun `doi bo cuc tu ve phai SAP LAI cua so app`() {
        // Thiếu bước này thì ô vẽ đúng chỗ mới nhưng cửa sổ app vẫn ở khung cũ. Sau khi bố cục về `HomeUiState`,
        // việc áp-xuống-view + sắp-lại-cửa-sổ nằm ở `render()` theo diff state (một chiều).
        val act = code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt")
        val render = body(act, "private fun render(state: HomeUiState)")
        assertTrue(
            render.contains("prev?.customLayout != state.customLayout"),
            "render phải nhận ra bố cục tự vẽ đổi (đây cũng là đường đúng cho ca đổi hồ sơ)",
        )
        val i = render.indexOf("prev?.customLayout != state.customLayout")
        val branch = render.substring(i, (i + 300).coerceAtMost(render.length))
        assertTrue(branch.contains("workspace.setCustomLayout("), "phải áp cho màn hình")
        assertTrue(branch.contains("reflow()"), "phải sắp lại cửa sổ app theo khung mới")
    }

    /**
     * Dải bố-cục-sẵn ở thanh trên cũng phải hỏi **nguồn duy nhất**, không tự suy ra bằng `customLayout != null`.
     *
     * Đây là chỗ thứ BẢY từng tự suy ra bố cục đang hiệu lực (sáu chỗ kia đã dọn ở P9). Tự suy ra thì bố cục tự vẽ
     * **lưu rồi mà không dùng được** (đè nhau / vượt trần ô — có thật khi hạ cấp bản) làm màn vẽ bố cục sẵn trong
     * khi bộ chọn không sáng ô nào. Hàm thuần [EffectiveLayout.highlightedPreset] có test riêng ở `:core`.
     */
    @Test
    fun `o preset sang di qua nguon duy nhat, khong suy ra tai cho`() {
        val act = code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt")
        val render = body(act, "private fun render(state: HomeUiState)")
        assertTrue(
            render.contains("EffectiveLayout.highlightedPreset("),
            "render phải lấy ô preset sáng từ EffectiveLayout (nguồn duy nhất), không tự suy ra",
        )
        val i = render.indexOf("EffectiveLayout.highlightedPreset(")
        assertTrue(
            render.substring(i).contains("topStrip.selectPreset("),
            "và phải đẩy chính giá trị đó xuống thanh trên",
        )
        assertEquals(
            0, Regex("""selectPreset\(\s*(?:if\s*\(|state\.preset\s*\))""").findAll(render).count(),
            "KHÔNG được dựng lại quyết định 'ô nào sáng' ngay tại chỗ gọi",
        )
    }

    @Test
    fun `bo cuc tu ve khong con BAN SAO o tang UI`() {
        // ⚠ [ĐO] tôi từng xoá riêng biến ở màn chính mà quên khung vẽ giữ BẢN SAO ⇒ cấu hình đã xoá nhưng màn hình
        // vẫn hiện bố cục tự vẽ. Lần đó vá bằng QUY ƯỚC "mọi thay đổi đi qua một hàm" — vẫn còn hai bản sao.
        // Nay đóng bằng KIẾN TRÚC: nguồn sự thật = HomeUiState.customLayout; tầng UI không giữ bản riêng.
        val act = code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt")
        assertEquals(
            0, Regex("private\\s+var\\s+customLayout").findAll(act).count(),
            "màn chính KHÔNG được giữ bản sao (var) của bố cục — phải đọc từ HomeUiState",
        )
        assertEquals(
            0, Regex("private\\s+var\\s+unitPrefs").findAll(act).count(),
            "lựa chọn đơn vị cũng vậy — trước đây có 4 bản sao đồng bộ bằng tay",
        )
        val state = code("src/main/kotlin/com/byd/clusternav/launcher/HomeUiState.kt")
        assertTrue(state.contains("customLayout: GridLayout?"), "bố cục tự vẽ phải là field của HomeUiState")
        assertTrue(state.contains("unitPrefs: UnitPrefs"), "đơn vị phải là field của HomeUiState")
        assertTrue(state.contains("wallpaper: WallpaperPrefs"), "hình nền phải là field của HomeUiState")
    }

    @Test
    fun `chi ViewModel duoc ghi ben, tang UI thi khong`() {
        val act = code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt")
        val vm = code("src/main/java/com/byd/clusternav/launcher/HomeViewModel.kt")
        assertEquals(
            0, Regex("workspaceRepository\\.set").findAll(act).count(),
            "tầng UI KHÔNG được ghi bền trực tiếp — phải đi qua intent của ViewModel (một chiều)",
        )
        listOf("setGridLayout", "setUnitPrefs", "setWallpaperPrefs").forEach { setter ->
            assertEquals(
                1, Regex("repository\\.$setter\\(").findAll(vm).count(),
                "$setter phải được gọi ở ĐÚNG MỘT chỗ trong ViewModel",
            )
        }
    }

    @Test
    fun `doi ho so phai NAP LAI bo cuc cua ho so do`() {
        // [SOÁT P1-4] bố cục lưu THEO HỒ SƠ; đọc một lần lúc khởi động thì đổi hồ sơ vẫn thấy bố cục hồ sơ cũ — và
        // mở bảng vẽ rồi Lưu sẽ GHI ĐÈ mất bố cục riêng của hồ sơ mới. Nay bảo đảm ở TẦNG DỮ LIỆU: `load()` (mà
        // `switchProfile` gọi lại) nạp bố cục vào state ⇒ không cần nhánh riêng ở tầng UI.
        val repo = code("src/main/java/com/byd/clusternav/launcher/PrefsWorkspaceRepository.kt")
        val load = body(repo, "override fun load()")
        assertTrue(
            load.contains("customLayout = prefs.gridLayout()"),
            "lượt nạp state phải gồm bố cục tự vẽ ⇒ đổi hồ sơ tự đúng",
        )
        assertTrue(
            load.contains("unitPrefs = prefs.unitPrefs()") && load.contains("wallpaper = prefs.wallpaperPrefs()"),
            "đơn vị + hình nền cũng phải nạp vào state (cùng lý do)",
        )
    }

    @Test
    fun `so o giu nguyen thi CHI dat lai cho, khong dung lai o`() {
        // Dựng lại ô là nhả/gắn lại bộ chiếu app trong ô (C5) — chỉ đổi hình dạng thì không cần.
        val src = code("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt")
        val fn = body(src, "fun setCustomLayout(")
        assertTrue(fn.contains("if (before != after) rebuild()"), "đổi số ô mới được dựng lại")
        assertTrue(fn.contains("requestLayout()"), "số ô giữ nguyên thì chỉ đặt lại chỗ")
    }
}
