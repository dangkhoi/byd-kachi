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
 */
class SettingsScreenWiringContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    private val panel by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsPanel.kt") }
    private val sections by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt") }
    private val home by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsHome.kt") }
    private val rows by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsRows.kt") }
    private val panels by lazy { code("src/main/java/com/byd/clusternav/launcher/HomePanels.kt") }
    private val strip by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiTopStrip.kt") }
    private val activity by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val vm by lazy { code("src/main/java/com/byd/clusternav/launcher/HomeViewModel.kt") }
    private val repo by lazy { code("src/main/java/com/byd/clusternav/launcher/PrefsWorkspaceRepository.kt") }
    private val prefs by lazy { code("src/main/java/com/byd/clusternav/launcher/WorkspacePrefs.kt") }
    private val profileBar by lazy { code("src/main/java/com/byd/clusternav/launcher/ProfileBar.kt") }

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
        assertFalse(fn.contains("pages.clear()"), "tháo KHÁC xoá — xoá ở đây là mất chỗ đang cuộn và dựng lại 187 ô")
        // Và phải có đường bỏ bộ nhớ khi state đổi thật, không thì trang nhớ lại sẽ nói số cũ.
        assertTrue(panel.contains("fun invalidateAll()"), "phải có đường bỏ trang đã nhớ")
        assertTrue(SourceRoots.body(panel, "fun invalidateAll()").contains("pages.clear()"), "và nó phải bỏ thật")
    }

    @Test
    fun `moi luot dung trang HOME co luoi RIENG`() {
        // Ràng buộc "một lưới = một bảng tiles": dùng lại một thực thể cho hai lượt dựng sẽ để lại view cũ trong
        // bảng tra ⇒ đúng bẫy "hai bản sao cùng khoá" đã sinh ba lỗi cùng lúc ở phiên RW0.
        assertTrue(
            sections.contains("SettingsHomeSection(context, rows, deps).build(body)"),
            "trang HOME phải dựng từ một thực thể MỚI mỗi lượt",
        )
        assertFalse(panel.contains("CapabilityGridSection("), "vỏ bảng không được giữ lưới")
        assertTrue(home.contains("CapabilityGridSection("), "lưới thuộc về nhóm Màn hình chính")
    }

    // ── R6 · tầng UI 0 lần ghi bền trực tiếp ─────────────────────────────────────────────────────

    @Test
    fun `man Cai dat KHONG ghi ben truc tiep`() {
        mapOf("SettingsPanel" to panel, "SettingsSections" to sections, "SettingsSectionsHome" to home).forEach {
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

    @Test
    fun `5 nut bo cuc va pill Ung dung O LAI thanh tren (sai lech co chu y)`() {
        // §4.5: bố cục đúng là cấu hình, nhưng 5 nút đó là cách đổi nhanh đang dùng hằng ngày và là bề mặt owner đã
        // duyệt từ prototype. Giữ nút, đồng thời bày đủ bố cục trong Cài đặt.
        val seg = SourceRoots.body(strip, "private fun buildSegmented()")
        listOf("ic_layout_1", "ic_layout_2c", "ic_layout_2r", "ic_layout_3", "ic_layout_4").forEach {
            assertTrue(seg.contains(it), "nút bố cục '$it' phải còn ở thanh trên")
        }
        assertTrue(
            SourceRoots.body(strip, "private fun build()").contains("R.string.kachi_pill_apps"),
            "pill 'Ứng dụng' (U3) cũng cố ý ở lại",
        )
    }

    @Test
    fun `avatar ho so con chạm-de-doi nhung het giu-de-tao`() {
        val fn = SourceRoots.body(strip, "private fun profileAvatar()")
        assertTrue(fn.contains("onProfileTap()"), "chạm để đổi hồ sơ phải còn — đang ở hồ sơ nào phải thấy liên tục")
        assertFalse(
            fn.contains("setOnLongClickListener"),
            "§4.5: tạo hồ sơ chuyển vào Cài đặt; giữ cả hai đường tạo là hai chỗ phải sửa và sẽ lệch nhau",
        )
        assertFalse(strip.contains("onProfileLongPress"), "cổng giữ-để-tạo phải bị gỡ hẳn, không để treo")
        assertTrue(sections.contains("deps.onAddProfile()"), "đường tạo hồ sơ nay ở nhóm Hồ sơ tài xế")
        assertTrue(
            activity.contains("onAddProfile = { profileBar.addDialog() }"),
            "và nó dùng LẠI hộp thoại có sẵn, không dựng bản thứ hai",
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
        assertTrue(activity.contains("onSelectPreset = { selectPreset(it) }"), "thanh trên đi qua nó")
        assertTrue(activity.contains("onPreset = { p -> selectPreset(p) }"), "màn Cài đặt cũng đi qua nó")
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
        assertTrue(activity.contains("viewModel.setAutostart("), "và nó nối vào intent")
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
        assertEquals(
            listOf("PrefsWorkspaceRepository.kt", "SettingsSections.kt", "ThemeHost.kt"), readers,
            "đường một chiều của chủ đề đã đổi hình: lưu bền (PrefsWorkspaceRepository) → đọc-để-vẽ (ThemeHost) → " +
                "nút bấm (SettingsSections). Chỗ đọc hiện tại: $readers",
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
     * Bài này khoá đúng chỗ bài `avatar ho so...` phía trên **không thể** thấy: nó chỉ soi hàm dựng avatar, còn
     * đường tạo thì nằm sau một nhánh trong lớp khác.
     */
    @Test
    fun `duong tao ho so CHI con o man Cai dat`() {
        val fn = SourceRoots.body(profileBar, "fun cycle()")
        assertFalse(
            fn.contains("addDialog("),
            "§4.5: chạm avatar KHÔNG được mở hộp thoại tạo — máy mới cài có đúng một hồ sơ nên nhánh đó là đường " +
                "tạo thứ hai trong ca thường gặp nhất",
        )
        assertTrue(fn.contains("toast("), "cú chạm không đổi được hồ sơ thì phải NÓI chỗ tạo, không im lặng")
        assertTrue(profileBar.contains("fun addDialog()"), "hộp thoại tạo vẫn phải còn — Cài đặt dùng LẠI nó")
        assertTrue(sections.contains("deps.onAddProfile()"), "và đường duy nhất tới nó là nhóm Hồ sơ tài xế")
    }

    @Test
    fun `xoa ho so bi chan hai ca va noi ro ly do`() {
        val fn = SourceRoots.body(sections, "private fun profileRow(")
        assertTrue(fn.contains("active ->"), "không cho xoá hồ sơ ĐANG DÙNG")
        assertTrue(fn.contains("total <= 1 ->"), "không cho xoá hồ sơ cuối cùng")
        // [SOÁT S1 · P2] THỨ TỰ: máy mới cài có ĐÚNG MỘT hồ sơ và nó tất nhiên đang dùng ⇒ xét `active` trước thì
        // câu trả lời là "đổi sang hồ sơ khác trước khi xoá" trong khi không có hồ sơ khác nào để đổi sang.
        assertTrue(
            fn.indexOf("total <= 1 ->") in 0 until fn.indexOf("active ->"),
            "ca 'hồ sơ cuối cùng' phải xét TRƯỚC ca 'đang dùng', không thì máy mới cài nhận một lời khuyên bất khả thi",
        )
        assertTrue(
            fn.contains("toast("),
            "cú bấm không có tác dụng thì phải NÓI lý do — im lặng làm người dùng tưởng app hỏng (bài học P9)",
        )
        assertTrue(fn.contains("deps.onDeleteProfile("), "ca hợp lệ mới đi tới intent xoá")
        // [ĐO] nơi lưu đã tự chặn ca "hồ sơ cuối cùng"; ca "đang dùng" thì KHÔNG — nó âm thầm đổi hồ sơ đang dùng
        // sang phần tử đầu. Đọc thẳng mã nguồn thay vì tin lời, vì bản chặn ở UI dựa vào sự thật này.
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
     * [ĐO] nó không có chỗ gọi nào — mã chết kèm một câu KDoc nói sai. Màn Cài đặt giữ 7 trang đã dựng (trang "Màn
     * hình chính" một mình là 187 ô) nên nhả sớm là việc đúng.
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
        // R4: 6 mục cũ + 187 ô đều còn dùng được.
        assertTrue(home.contains("stripPicker.section("), "chip thanh trạng thái")
        assertTrue(home.contains("CapabilityCatalog.byDomain()"), "lưới 187 ô")
        assertTrue(home.contains("rows.checkRow("), "hình nền: ô tick")
        assertTrue(home.contains("Slideshow.INTERVAL_CHOICES_SEC"), "hình nền: chu kỳ đổi ảnh")
        assertTrue(home.contains("ImageFit.values()"), "hình nền: cách phủ")
        assertTrue(home.contains("deps.onOpenLayoutEditor()"), "đường mở bảng vẽ bố cục")
        assertTrue(sections.contains("rows.unitRow("), "đơn vị hiển thị")
        assertTrue(sections.contains("recircRow("), "lấy gió trong khi nổ máy")
        assertTrue(sections.contains("rows.permissionRow("), "quyền còn thiếu")
        assertTrue(rows.contains("fun note(") && rows.contains("fun button("), "hai hàng dùng chung mới")
        // Và đường mở bảng vẽ phải ĐÓNG màn Cài đặt trước: hai lớp phủ chồng nhau thì Back mất nghĩa.
        assertTrue(
            SourceRoots.body(panels, "fun openSettings()").contains("closeSettings(); openLayoutEditor()"),
            "mở bảng vẽ phải đóng màn Cài đặt trước",
        )
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
