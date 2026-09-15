package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá DÂY NỐI của **màn Cài đặt** (S1 — spec `docs/specs/kachi-settings-screen.html`).
 *
 * Quét SOURCE vì dự án không dựng được Activity/View trong JVM thuần (không dùng Robolectric). Mọi phép quét đi qua
 * [code] để **bỏ chú thích trước khi kiểm**, và mọi phép cắt vùng đi qua [SourceRoots.body] — nó **nổ** nếu mốc không
 * tồn tại, thay vì âm thầm quét tới hết tệp (lượt soát 2026-09-11 [ĐO] 5 bài canh từng xanh nhờ quét tràn).
 *
 * ## Bài nào là bài THẬT ở đây
 * Ba bài đáng giá nhất **không** kiểm màn Cài đặt mà kiểm **giả định** của nó:
 *  - `tu mo khi no may co nguoi DOC that` — cờ có người đọc thì ô tick mới không phải nút chết;
 *  - `chua lam nut gat sang toi vi CHUA ai doc de ve` — quét cả cây `:app` để **chứng minh** tiền đề của quyết định
 *    "chỉ nói, không làm nút"; ngày nào có người đọc thật, bài này đỏ và nhắc làm nút;
 *  - `chon bo cuc san chi co MOT duong` — đếm chỗ gọi, thứ mà đọc mắt không thấy.
 * Các bài còn lại chỉ khoá hình dạng dây nối.
 *
 * ## Bài KHÔNG còn ở đây
 * Bảng đối chiếu **56 mục danh mục → control thật** (R2 · §4.3) đã tách sang [SettingsCatalogControlContractTest]
 * (D2c — tệp này từng 547 dòng, quá trần 500 của CLAUDE.md §4.1). Không đổi một assert nào khi tách.
 */
class SettingsScreenWiringContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    private val panel by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsPanel.kt") }
    private val sections by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt") }
    private val home by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsHome.kt") }
    private val wiring by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt") }
    private val bars by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsBars.kt") }
    private val nav by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsNav.kt") }
    private val cast by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsCast.kt") }
    private val keys by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsKeys.kt") }
    private val car by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsCar.kt") }
    private val places by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsPlaces.kt") }
    private val rows by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsRows.kt") }
    private val panels by lazy { code("src/main/java/com/byd/clusternav/launcher/HomePanels.kt") }
    private val strip by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiTopStrip.kt") }
    private val activity by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val vm by lazy { code("src/main/java/com/byd/clusternav/launcher/HomeViewModel.kt") }
    private val repo by lazy { code("src/main/java/com/byd/clusternav/launcher/PrefsWorkspaceRepository.kt") }
    private val prefs by lazy { code("src/main/java/com/byd/clusternav/launcher/WorkspacePrefs.kt") }
    private val profileChip by lazy { code("src/main/java/com/byd/clusternav/launcher/ProfileChip.kt") }

    // ── R1 · vỏ màn: rail lấy từ danh mục, trang được nhớ lại ────────────────────────────────────

    @Test
    fun `rail lay nhom tu danh muc chu khong tu liet ke`() {
        val fn = SourceRoots.body(panel, "private fun rail()")
        assertTrue(
            fn.contains("SettingsCatalog.GROUPS"),
            "rail phải đọc danh mục ở `:core` — tầng UI tự liệt kê nhóm lần nữa thì phép kiểm phủ khoá của R2 mất " +
                "hiệu lực với chính màn hình mà nó bảo vệ",
        )
        val cell = SourceRoots.body(panel, "private fun railCell(")
        // U5·T3 — phải là `displayLabel`/`displaySub`, KHÔNG phải `label`/`sub` gốc: nhãn gốc luôn tiếng Việt theo
        // giao kèo `Localized.label`, nên đọc thẳng nó làm rail đứng nguyên tiếng Việt khi người dùng chọn English —
        // [ĐO] máy ảo 2026-09-12 chụp được đúng cảnh đó (rail Việt / nội dung Anh trên cùng một màn).
        assertTrue(cell.contains("group.displayLabel") && cell.contains("group.displaySub"),
            "rail phải tự giải thích được VÀ theo ngôn ngữ: nhãn + câu phụ đều lấy từ danh mục qua display*")
    }

    @Test
    fun `moi nhom trong danh muc deu co noi dung`() {
        SettingsGroup.values().forEach { g ->
            assertTrue(
                Regex("""SettingsGroup\.${g.name}\s*->""").containsMatchIn(sections),
                "nhóm ${g.id} không có nhánh dựng nội dung ⇒ rail mở ra một trang trắng",
            )
        }
    }

    @Test
    fun `doi nhom KHONG duoc lam mat cho dang cuon cua nhom khac`() {
        // R1 đòi đúng điều này. Cách duy nhất đạt được mà không phải tự nhớ toạ độ cuộn: giữ chính thực thể
        // ScrollView, chỉ THÁO nó ra khỏi khung nội dung.
        val fn = SourceRoots.body(panel, "fun show(")
        assertTrue(fn.contains("pages.getOrPut("), "trang phải được nhớ lại, không dựng lại mỗi lần bấm rail")
        assertTrue(fn.contains("content.removeAllViews()"), "trang cũ chỉ bị tháo khỏi khung")
        assertFalse(fn.contains("pages.clear()"), "tháo KHÁC xoá — xoá ở đây là mất chỗ đang cuộn và dựng lại cả trang")
        // Và phải có đường bỏ bộ nhớ khi state đổi thật, không thì trang nhớ lại sẽ nói số cũ.
        assertTrue(panel.contains("fun invalidateAll()"), "phải có đường bỏ trang đã nhớ")
        assertTrue(SourceRoots.body(panel, "fun invalidateAll()").contains("pages.clear()"), "và nó phải bỏ thật")
    }

    /**
     * Ràng buộc **"một lưới = một bảng tiles"**: lớp nào giữ bảng tra `mã → view` thì mỗi lượt dựng trang phải là
     * một thực thể MỚI — dùng lại sẽ để view cũ nằm trong bảng tra, đúng bẫy "hai bản sao cùng khoá" đã sinh ba lỗi
     * cùng lúc ở phiên RW0.
     *
     * ⚠ T4 · R-UI (m): lớp giữ bảng tra nay là [TopStripPicker] (trong [SettingsBarsSection]) chứ không còn là
     * `CapabilityGridSection` — lưới 123 ô đã rời khỏi Settings và tệp đó bị xoá.
     */
    @Test
    fun `moi luot dung trang co bo chon RIENG`() {
        listOf(
            "SettingsHomeSection(context, rows, deps).build(body)",
            "SettingsBarsSection(context, rows, deps).build(body)",
        ).forEach {
            assertTrue(sections.contains(it), "trang phải dựng từ một thực thể MỚI mỗi lượt: $it")
        }
        assertFalse(panel.contains("TopStripPicker("), "vỏ bảng không được giữ bộ chọn")
        assertTrue(bars.contains("TopStripPicker("), "bộ chọn chip thuộc về nhóm Thanh trạng thái & thanh nút")
    }

    // ── R6 · tầng UI 0 lần ghi bền trực tiếp ─────────────────────────────────────────────────────

    @Test
    fun `man Cai dat KHONG ghi ben truc tiep`() {
        mapOf(
            "SettingsPanel" to panel, "SettingsSections" to sections, "SettingsSectionsHome" to home,
            "SettingsSectionsBars" to bars, "SettingsSectionsNav" to nav,
            "SettingsSectionsCast" to cast, "SettingsSectionsKeys" to keys, "SettingsSectionsCar" to car,
            // Sổ địa chỉ (docs/specs/kachi-voice-addresses.html) — section MỚI, và là section đầu tiên ghi một
            // khoá **của launcher** (không phải của ClusterNav qua `bridge`), nên nó đúng là loại tệp mà bài này
            // sinh ra để canh: mọi lượt ghi phải đi qua `deps.onSavedPlaces` → ViewModel.
            "SettingsSectionsPlaces" to places,
        ).forEach {
            (name, src) ->
            listOf("WorkspacePrefs", "workspaceRepository", "getSharedPreferences", "Prefs.set").forEach { bad ->
                assertFalse(
                    src.contains(bad),
                    "$name chạm '$bad' — mọi thay đổi phải đi qua intent ViewModel (nếu không thì state trên màn " +
                        "và state đã lưu lệch nhau mà không ai phát hiện)",
                )
            }
        }
    }

    // ── §4.5 · MỘT cửa vào, và hai thứ cố ý ở lại thanh trên ─────────────────────────────────────

    @Test
    fun `thanh tren chi con MOT cua vao cau hinh`() {
        val fn = SourceRoots.body(strip, "private fun build()")
        assertEquals(
            // U5·T3 — nhãn pill đến từ tài nguyên; đếm theo MÃ KHOÁ, tính chất không đổi.
            1, Regex("""pill\([^)]*?R\.string\.kachi_pill_settings""").findAll(fn).count(),
            "đúng một pill mở cấu hình",
        )
        assertFalse(fn.contains("""pill("Tuỳ biến""""), "pill 'Tuỳ biến' đã gộp vào 'Cài đặt'")
        assertFalse(strip.contains("onCustomizeDock"), "không còn cổng riêng cho bảng cũ")
        assertTrue(
            activity.contains("onOpenSettings = { panels.openSettings() }"),
            "pill 'Cài đặt' phải mở màn Cài đặt của launcher, KHÔNG nhảy thẳng sang màn ClusterNav như trước",
        )
    }

    /**
     * ⚠⚠ **BÀI CANH ĐẢO CHIỀU — §4.5 gài, S4 · R7 đảo.** Giữ nguyên lịch sử thay vì viết một bài mới, đúng lối đã
     * làm với bài nút gạt sáng/tối bên dưới.
     *
     * **Chiều CŨ (§4.5)**: 5 nút bố cục *ở lại* thanh trên vì "đổi bố cục là việc hằng ngày, và là bề mặt owner đã
     * duyệt từ prototype". **Chiều MỚI (S4)**: hồ sơ giữ TẤT CẢ ⇒ việc hằng ngày là đổi **cả bộ**, nên nút bố cục
     * rời khỏi thanh trên (owner 2026-09-14: *"bỏ luôn các nút đổi bố cục trên header"*). Pill "Ứng dụng" thì vẫn ở
     * lại — nó không phải cấu hình.
     */
    @Test
    fun `5 nut bo cuc DA ROI thanh tren nhung pill Ung dung o lai`() {
        listOf("ic_layout_1", "ic_layout_2c", "ic_layout_2r", "ic_layout_3", "ic_layout_4").forEach {
            assertFalse(strip.contains(it), "nút bố cục '$it' phải hết khỏi thanh trên (S4 · R7)")
        }
        assertTrue(
            SourceRoots.body(strip, "private fun build()").contains("R.string.kachi_pill_apps"),
            "pill 'Ứng dụng' (U3) cố ý ở lại — nó mở app, không đổi cấu hình",
        )
        assertTrue(home.contains("deps.onPreset("), "và Cài đặt → Màn hình chính phải bày đủ bố cục sẵn")
    }

    @Test
    fun `chip ho so con chạm-de-doi nhung het giu-de-tao`() {
        val fn = SourceRoots.body(strip, "private fun profileChip()")
        assertTrue(fn.contains("onProfileTap()"), "chạm để đổi hồ sơ phải còn — đang ở hồ sơ nào phải thấy liên tục")
        assertFalse(
            fn.contains("setOnLongClickListener"),
            "§4.5: tạo hồ sơ chuyển vào Cài đặt; giữ cả hai đường tạo là hai chỗ phải sửa và sẽ lệch nhau",
        )
        assertFalse(strip.contains("onProfileLongPress"), "cổng giữ-để-tạo phải bị gỡ hẳn, không để treo")
        // S4 · R7 — chip phải nói CẢ TÊN, không chỉ chữ cái: hai hồ sơ "Đi làm"/"Đường trường" cùng chữ `Đ`, mà từ
        // R3 một cú đổi hồ sơ kéo theo cả màn hình.
        assertTrue(fn.contains("profileNameView"), "chip phải hiện TÊN hồ sơ, không chỉ chữ cái đầu")
        assertTrue(strip.contains("fun setProfile("), "và phải có đường đổ tên đó vào chip khi state đổi")
        assertTrue(sections.contains("deps.onDuplicateProfile("), "đường tạo hồ sơ nay ở nhóm Hồ sơ tài xế")
        assertTrue(
            sections.contains("SettingsDialogs.askName("),
            "và nó dùng LẠI hộp thoại hỏi-tên dùng chung, không dựng bản thứ hai",
        )
    }

    @Test
    fun `Back dong dung lop tren cung`() {
        val fn = SourceRoots.body(activity, "override fun onBackPressed()")
        assertTrue(fn.contains("panels.layoutOpen()"), "bảng vẽ bố cục đóng trước")
        assertTrue(fn.contains("panels.settingsOpen()"), "rồi tới màn Cài đặt")
        assertTrue(
            fn.indexOf("layoutOpen") in 0 until fn.indexOf("settingsOpen"),
            "thứ tự phải là lớp phủ TRÊN CÙNG trước — bảng vẽ mở từ trong Cài đặt nên nó nằm trên",
        )
        assertFalse(fn.contains("customizeOpen"), "tên cũ phải hết, không để tham chiếu treo")
    }

    @Test
    fun `chon bo cuc san chi co MOT duong`() {
        assertTrue(activity.contains("private fun selectPreset("), "phải có đúng một hàm chọn bố cục sẵn")
        // S4 · R7 — bề mặt thứ hai (5 nút ở thanh trên) đã gỡ, nên cổng `onSelectPreset` cũng phải hết: để lại một
        // cổng không ai nối là lời mời dựng lại hàng nút đó.
        assertFalse(activity.contains("onSelectPreset"), "cổng chọn bố cục của thanh trên phải bị gỡ hẳn")
        assertTrue(activity.contains("onPreset = { p -> selectPreset(p) }"), "màn Cài đặt là đường DUY NHẤT tới nó")
        assertEquals(
            1, Regex("""viewModel\.setPreset\(""").findAll(activity).count(),
            "đúng MỘT chỗ gọi intent đặt bố cục — hai chỗ là hai bản sao của hành vi 'chọn bố cục sẵn thì bỏ bố " +
                "cục tự vẽ', và bản thứ hai sẽ quên nó",
        )
        val fn = SourceRoots.body(activity, "private fun selectPreset(")
        assertTrue(fn.contains("applyCustomLayout(null)"), "chọn bố cục sẵn phải BỎ bố cục tự vẽ (P9)")
    }

    // ── R3 · hai cấu hình trước đây không có đường tới ───────────────────────────────────────────

    @Test
    fun `tu mo khi no may di duong mot chieu day du`() {
        assertTrue(
            code("src/main/kotlin/com/byd/clusternav/launcher/HomeUiState.kt").contains("val autostart: Boolean"),
            "phải là một field của nguồn sự thật, không phải một lượt đọc prefs trong View",
        )
        assertTrue(
            code("src/main/kotlin/com/byd/clusternav/launcher/WorkspaceRepository.kt")
                .contains("fun autostart(): Boolean = true"),
            "cổng dữ liệu phải có THÂN MẶC ĐỊNH ⇒ bản giả in-memory trong test không phải sửa",
        )
        val f = SourceRoots.body(vm, "fun setAutostart(")
        assertTrue(f.contains("_uiState.update"), "state và lưu bền phải đi trong MỘT lượt")
        assertTrue(f.contains("repository.setAutostart("), "và phải ghi qua cổng dữ liệu")
        assertTrue(
            SourceRoots.body(repo, "override fun load()").contains("autostart = prefs.launcherAutostart()"),
            "nạp trong load() ⇒ mở lại màn là thấy đúng cờ đang lưu",
        )
        assertTrue(sections.contains("deps.onAutostart("), "phải có ô tick thật trong nhóm Hệ thống")
        // T4: khối dựng `HomePanels` chuyển sang `KachiHomeWiring.homePanels(...)` (Activity về ≤ 500 dòng) — chỗ
        // nối intent theo nó, hành vi không đổi.
        assertTrue(wiring.contains("viewModel.setAutostart("), "và nó nối vào intent")
    }

    @Test
    fun `tu mo khi no may co nguoi DOC that - khong phai nut chet`() {
        // Đây là bài quan trọng nhất của ô tick: một công tắc lưu bền mà không ai đọc thì bấm cũng như không.
        val reader = code("src/main/java/com/byd/clusternav/KachiAutostart.kt")
        assertTrue(
            reader.contains("launcherAutostart()"),
            "đường khởi động phải ĐỌC cờ này — nếu không, ô tick là nút chết",
        )
        assertTrue(prefs.contains("fun setLauncherAutostart("), "và phải có đường ghi bền")
    }

    /**
     * ⚠⚠ **BÀI CANH TỰ ĐẢO CHIỀU — S1 gài, T1 đảo.** Đây là bằng chứng cơ chế đó hoạt động, nên giữ lại nguyên văn
     * lịch sử của nó thay vì viết lại thành một bài mới.
     *
     * **Chiều CŨ (S1)**: `themeMode` chỉ có `PrefsWorkspaceRepository` đọc (nạp + ghi lại chính nó), `isNight()` có 0
     * chỗ gọi, `KachiTheme` là 13 `const val` (hằng biên dịch) và có 82 hex viết cứng ở 21 tệp ⇒ nút gạt sẽ là **nút
     * chết**. Bài khi đó đòi `display(` **KHÔNG** được có `setThemeMode` và **phải** có `rows.note(` nói thật hiện
     * trạng; và nó đỏ ngay khi xuất hiện chỗ đọc `themeMode` thứ hai — tức là *"hôm nào có người đọc để vẽ thì đòi
     * làm nút"*.
     *
     * **Chiều MỚI (T1)**: cả ba tiền đề đã bị bỏ — [KachiPalette] có bảng SÁNG, [KachiTheme] tra theo bảng, và
     * [ThemeHost.sync] là người đọc `themeMode` để vẽ. Nên bài nay đòi **ngược lại**: phải CÓ nút thật, và phải có
     * đúng những chỗ đọc `themeMode` mà thiết kế cần — không nhiều hơn (chỗ ghi bảng màu thứ hai bị
     * [ThemePaletteContractTest] chặn riêng).
     */
    @Test
    fun `nay PHAI co nut gat sang toi vi da co nguoi doc de ve`() {
        // Chỗ ĐỌC `themeMode` trong MÃ (đã bỏ chú thích — lần đầu S1 quét thô và bài đỏ vì chính KDoc nhắc tên nó).
        // Ba chỗ, mỗi chỗ một vai: nạp/ghi bền · người đọc-để-vẽ · nút bấm.
        val readers = appSources { it.contains(".themeMode") }
        // T-BRIDGE thêm chỗ đọc thứ TƯ và nó KHÔNG phá hình dạng một chiều: `TestBridgeState` chỉ **chép** giá
        // trị từ `HomeUiState` ra JSON cho lệnh `state` của cầu kiểm thử — không ghi bền, không vẽ, không giữ bản
        // sao. Giữ nó trong danh sách ghim (thay vì nới phép quét) để chỗ đọc thứ NĂM vẫn phải giải trình.
        assertEquals(
            listOf("PrefsWorkspaceRepository.kt", "SettingsSections.kt", "TestBridgeState.kt", "ThemeHost.kt"),
            readers,
            "đường một chiều của chủ đề đã đổi hình: lưu bền (PrefsWorkspaceRepository) → đọc-để-vẽ (ThemeHost) → " +
                "nút bấm (SettingsSections) → ảnh chụp chẩn đoán chỉ-đọc (TestBridgeState). Chỗ đọc hiện tại: $readers",
        )
        assertTrue(
            appSources { it.contains("isNight(") }.isNotEmpty(),
            "isNight() lại thành 0 chỗ gọi ⇒ chế độ \"Tự động\" là lựa chọn chết",
        )

        val fn = SourceRoots.body(sections, "private fun display(")
        assertTrue(fn.contains("deps.onThemeMode("), "nhóm Hiển thị phải có NÚT đổi chủ đề, không phải dòng chữ")
        assertTrue(
            fn.contains("rows.chipRow(") && fn.contains("ThemeMode.values()"),
            "nút phải bày ĐỦ ba lựa chọn của ThemeMode (Sáng / Tối / Tự động), không phải công tắc hai trạng thái",
        )
        assertFalse(
            fn.contains("chỉ có bảng màu TỐI"),
            "dòng thông tin cũ nay NÓI SAI (đã có bảng sáng) — phải xoá, không được để lại cạnh nút",
        )
        // Đường lưu bền + intent GIỮ NGUYÊN: nút mới phải dùng lại chúng, không dựng đường thứ hai.
        assertTrue(vm.contains("fun setThemeMode("), "không được xoá intent đang có")
        assertTrue(prefs.contains("fun setThemeMode("), "không được xoá đường lưu bền đang có")
    }

    /** Tên tệp Kotlin của `:app` mà **MÃ** (đã bỏ chú thích) thoả [match], sắp theo tên. */
    private fun appSources(match: (String) -> Boolean): List<String> {
        val root = SourceRoots.moduleSourceRoots().first { it.toString().contains("app") }
        return Files.walk(root).use { p ->
            p.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .filter { f ->
                    val body = f.toFile().readText()
                        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
                        .lines().joinToString("\n") { line -> line.substringBefore("//") }
                    match(body)
                }
                .map { it.fileName.toString() }.sorted().toList()
        }
    }

    // ── Hồ sơ tài xế: hai ca xoá bị chặn, và NÓI lý do ───────────────────────────────────────────

    /**
     * [SOÁT S1 · P2] §4.5 nói **bỏ đường tạo hồ sơ ở thanh trên**, và bản đầu của S1 chỉ gỡ
     * `setOnLongClickListener` ở avatar. Nhưng `ProfileBar.cycle()` (đường CHẠM avatar) vẫn mở hộp thoại tạo khi chỉ
     * có một hồ sơ — mà đó là trạng thái của **mọi máy mới cài** (một hồ sơ "Mặc định") ⇒ trong ca thường gặp nhất,
     * đường tạo thứ hai còn nguyên.
     *
     * **S4 · R7 đóng hẳn họ lỗi này**: `cycle()` không còn tồn tại, và [ProfileChip.picker] — thứ thay nó — chỉ có
     * đúng hai kết cục: đổi sang một hồ sơ ĐÃ CÓ, hoặc mở màn Cài đặt. Không có nhánh nào dựng hộp thoại. Bài này
     * canh đúng tính chất đó, ở chỗ bài `chip ho so...` phía trên không thể thấy (nó chỉ soi hàm dựng chip).
     */
    @Test
    fun `duong tao ho so CHI con o man Cai dat`() {
        val fn = SourceRoots.body(profileChip, "fun picker(")
        assertFalse(
            fn.contains("AlertDialog") || fn.contains("askName("),
            "bộ chọn ở thanh trên KHÔNG được dựng hộp thoại tạo — tạo hồ sơ chỉ ở Cài đặt (§4.5)",
        )
        assertTrue(fn.contains("onManage()"), "nhưng phải có lối SANG chỗ tạo, không thì cú chạm thành ngõ cụt")
        assertTrue(fn.contains("viewModel.switchProfile("), "và việc chính của nó là đổi hồ sơ, qua intent ViewModel")
        assertTrue(sections.contains("deps.onDuplicateProfile("), "đường tạo duy nhất là nhóm Hồ sơ tài xế")
    }

    @Test
    fun `nut xoa ho so chi hien khi thuc su xoa duoc`() {
        val fn = SourceRoots.body(sections, "private fun profileRow(")
        // ⚠ [SOÁT UI 2026-09-12] ĐỔI giao kèo: trước đây nút Xoá LUÔN hiện rồi bấm ra toast chặn (bản cũ khoá
        // `active ->` / `total <= 1 ->` / `toast(`). Hành vi đúng hơn: nút Xoá CHỈ dựng khi thực sự xoá được — KHÔNG
        // phải hồ sơ đang dùng VÀ còn hồ sơ khác. Ẩn hẳn thì không có affordance để bấm nhầm trên màn xe, nên không
        // cần toast giải thích (khác ca P9: không có kỳ vọng bị chặn im lặng — người dùng đơn giản không thấy nút).
        assertTrue(fn.contains("if (!active && total > 1)"), "nút Xoá phải bọc trong điều kiện xoá-được-thật")
        assertTrue(fn.contains("deps.onDeleteProfile("), "và chỉ trong nhánh đó mới đi tới intent xoá")
        // [ĐO] nơi lưu đã tự chặn ca "hồ sơ cuối cùng" (UI ẩn nút chỉ là lớp ngoài, KHÔNG được là lớp duy nhất).
        assertTrue(
            SourceRoots.body(prefs, "fun deleteProfile(").contains("list.size <= 1"),
            "nơi lưu vẫn phải giữ lưới an toàn cho ca hồ sơ cuối cùng",
        )
    }

    /**
     * [SOÁT S1 · P2] Trang được **nhớ lại** nên nó không tự dựng lại khi state đổi từ bên trong chính nó. Chọn bố cục
     * sẵn thì `selectPreset` BỎ bố cục tự vẽ ⇒ dòng "Đang dùng bố cục tự vẽ: N khung" ngay phía trên thành SAI và
     * không ai sửa. Nói sai ở đúng chỗ này là ca xấu nhất (KDoc của `layoutSummary` cũng nói vậy).
     */
    @Test
    fun `chon bo cuc san thi cau mo ta bo cuc phai duoc sua lai`() {
        val fn = SourceRoots.body(home, "private fun layout(")
        assertTrue(
            fn.contains("summary.text = layoutSummary("),
            "sau khi chọn bố cục sẵn phải cập nhật lại câu mô tả — trang đã nhớ không tự dựng lại",
        )
        assertTrue(
            fn.contains("layoutSummary(deps.state()"),
            "và phải đọc lại từ NGUỒN SỰ THẬT, không dùng ảnh chụp lúc dựng trang",
        )
    }

    /**
     * [SOÁT S1 · P3] `HomePanels.closeAll()` tự nhận là "gọi lúc huỷ màn (lớp phủ giữ view là giữ activity)" nhưng
     * [ĐO] nó không có chỗ gọi nào — mã chết kèm một câu KDoc nói sai. Màn Cài đặt giữ tới 10 trang đã dựng (mỗi
     * trang là một `ScrollView` đầy view, và hai trang nav/cast còn nhúng view tự vẽ) nên nhả sớm là việc đúng.
     */
    @Test
    fun `huy man thi dong moi lop phu`() {
        val fn = SourceRoots.body(activity, "override fun onDestroy()")
        assertTrue(fn.contains("panels.closeAll()"), "huỷ màn phải đóng mọi lớp phủ, không để mã chết")
        assertTrue(panels.contains("fun closeAll()"), "và hàm đó phải còn")
        assertTrue(
            SourceRoots.body(panels, "fun closeAll()").contains("closeSettings()"),
            "closeAll phải đóng CẢ màn Cài đặt, không chỉ bảng vẽ",
        )
    }

    // ── T5 · bảng cũ đã xoá, không còn tham chiếu treo ───────────────────────────────────────────

    @Test
    fun `bang Tuy bien cu da bi xoa han`() {
        assertFalse(
            SourceRoots.exists("src/main/java/com/byd/clusternav/launcher/CustomizePanel.kt"),
            "T5: bảng cũ phải bị xoá, không để hai bề mặt cấu hình song song",
        )
        val offenders = appSources { it.contains("CustomizePanel") }
        assertEquals(
            emptyList<String>(), offenders,
            "còn tham chiếu tới bảng đã xoá: $offenders",
        )
    }

    @Test
    fun `noi dung bang cu chuyen du sang man Cai dat`() {
        // R4: 6 mục của bảng cũ đều còn dùng được (lưới ô nay ở bộ chọn ngăn kéo — R-UI (m)).
        // T4 · R-UI (a): chip thanh trạng thái tách sang nhóm "Thanh trạng thái & thanh nút".
        assertTrue(bars.contains("stripPicker.section("), "chip thanh trạng thái")
        // T4 · R-UI (m): lưới 123 ô KHÔNG còn ở Settings — nó mở bộ chọn của ngăn kéo. Cùng chức năng, một bề mặt.
        assertTrue(bars.contains("deps.openDockPicker("), "đường chọn nút cho thanh nút xe")
        assertTrue(home.contains("rows.checkRow("), "hình nền: ô tick")
        assertTrue(home.contains("Slideshow.INTERVAL_CHOICES_SEC"), "hình nền: chu kỳ đổi ảnh")
        assertTrue(home.contains("ImageFit.values()"), "hình nền: cách phủ")
        assertTrue(home.contains("deps.onOpenLayoutEditor()"), "đường mở bảng vẽ bố cục")
        assertTrue(sections.contains("rows.unitRow("), "đơn vị hiển thị")
        // T4: ô tick lấy gió trong theo nhóm "Tiện nghi xe" sang tệp riêng khi nhóm đó nhận thêm ghế + lọc bụi mịn.
        assertTrue(car.contains("R.string.kachi_recirc_title"), "lấy gió trong khi nổ máy")
        assertTrue(sections.contains("rows.permissionRow("), "quyền còn thiếu")
        assertTrue(rows.contains("fun note(") && rows.contains("fun button("), "hai hàng dùng chung mới")
        // Và đường mở bảng vẽ phải ĐÓNG màn Cài đặt trước: hai lớp phủ chồng nhau thì Back mất nghĩa.
        assertTrue(
            SourceRoots.body(panels, "fun openSettings(group: SettingsGroup? = null)").contains("closeSettings(); openLayoutEditor()"),
            "mở bảng vẽ phải đóng màn Cài đặt trước",
        )
    }

    /**
     * S4 · R6/R8 — ba lambda hồ sơ MỚI của [SettingsDeps] phải được nối THẬT ở `KachiHomeWiring`.
     *
     * [HomePanels] để chúng có **thân mặc định rỗng** cho lượt T3 (agent đó không sở hữu `KachiHomeWiring`), và một
     * mặc định rỗng là đúng hình dạng của nút chết: bấm không lỗi, không đổi gì, không ai đỏ. CLAUDE.md §8 —
     * `CastShell.evictVd` đã dạy đúng bài này. Bài test là thứ duy nhất biến "TODO" thành một việc bắt buộc.
     */
    @Test
    fun `ba lambda ho so moi phai duoc noi that khong con mac dinh rong`() {
        mapOf(
            "onDuplicateProfile" to "thêm hồ sơ = bản sao hồ sơ đang dùng (R8)",
            "bootProfile" to "đọc hồ sơ lúc nổ máy (R6)",
            "onBootProfile" to "ghi hồ sơ lúc nổ máy (R6)",
        ).forEach { (gate, what) ->
            assertTrue(
                Regex("""\b$gate\s*=""").containsMatchIn(wiring),
                "KachiHomeWiring chưa nối '$gate' — $what. HomePanels đang để mặc định rỗng ⇒ điều khiển đó là NÚT " +
                    "CHẾT trên màn Cài đặt",
            )
        }
        assertTrue(vm.contains("fun duplicateProfile("), "và intent nhân bản hồ sơ phải tồn tại ở ViewModel")
        assertTrue(repo.contains("override fun bootProfile("), "cùng đường đọc/ghi bền ở repository")
        assertTrue(repo.contains("override fun setBootProfile("), "cùng đường đọc/ghi bền ở repository")
    }

    @Test
    fun `doi ho so thi trang da nho phai duoc dung lai`() {
        // Đổi hồ sơ nạp lại TOÀN BỘ (bố cục · thanh nút · chip · hình nền · đơn vị) ⇒ mọi trang đã nhớ đều cũ.
        val fn = SourceRoots.body(activity, "private fun render(")
        assertTrue(fn.contains("panels.invalidateSettings()"), "phải bỏ trang đã nhớ khi hồ sơ đổi")
        assertTrue(
            fn.contains("prev.profiles != state.profiles"),
            "phải xét CẢ danh sách hồ sơ: xoá một hồ sơ KHÔNG đang dùng thì `activeProfile` không đổi, và khi đó " +
                "danh sách trên màn vẫn còn hồ sơ vừa xoá",
        )
    }
}
