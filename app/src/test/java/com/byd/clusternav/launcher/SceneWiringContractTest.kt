package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ P7 + P6 — BÀI CANH DÂY NỐI CHO CẢNH ═══════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-scenes-and-widgets.html` §3.1 / T1–T3. `SceneBookTest` (:core) đã chốt **luật**; bài này
 * chốt **dây nối** — thứ mà một model đúng vẫn không giao được nếu nối sai:
 *
 *  1. sổ cảnh là **field của nguồn sự thật** + có **thân mặc định** ở cổng dữ liệu;
 *  2. nạp **trong `load()`**, cùng lượt với mọi thứ khác (⇒ đổi hồ sơ tự đúng);
 *  3. mọi thay đổi đi qua **intent** — tầng UI 0 lần ghi bền trực tiếp;
 *  4. **R4**: gọi cảnh là MỘT phép `copy` qua `withScene`, không phải ba intent rời;
 *  5. cảnh **đặt được từ tay người dùng** (luật *"vẽ được ≠ đặt được"* của RW0) và **nói ra** khi bị từ chối;
 *  6. mọi chữ đi qua **tài nguyên**; khoá khai trong [SettingsCatalog].
 *
 * ## ⚠ Vì sao bài này nằm ở `:app`
 * Nó quét mã nguồn `:app`. Luật của dự án (đã trả giá hai lần): *bài quét mã module X phải NẰM trong module X*, và
 * nửa còn lại (G1) là *Gradle phải BIẾT thứ bài đó quét* — `app/build.gradle.kts` đã khai `inputs.dir("src/main/java")`
 * và `inputs.dir("src/main/res")`, nên sửa mã hoặc sửa tài nguyên đều làm task chạy lại.
 */
class SceneWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)

    private val state by lazy { code("src/main/kotlin/com/byd/clusternav/launcher/HomeUiState.kt") }
    private val port by lazy { code("src/main/kotlin/com/byd/clusternav/launcher/WorkspaceRepository.kt") }
    private val core by lazy { code("src/main/kotlin/com/byd/clusternav/launcher/SceneBook.kt") }
    private val repo by lazy { code("src/main/java/com/byd/clusternav/launcher/PrefsWorkspaceRepository.kt") }
    private val prefs by lazy { code("src/main/java/com/byd/clusternav/launcher/WorkspacePrefs.kt") }
    private val vm by lazy { code("src/main/java/com/byd/clusternav/launcher/HomeViewModel.kt") }
    private val section by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSceneSection.kt") }
    private val controller by lazy { code("src/main/java/com/byd/clusternav/launcher/SceneController.kt") }
    private val home by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsHome.kt") }
    private val activity by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }

    // ── 1 · nguồn sự thật + cổng dữ liệu ─────────────────────────────────────────────────────────

    @Test
    fun `so canh la field cua nguon su that, khong phai mot luot doc prefs trong View`() {
        assertTrue(
            state.contains("val scenes: SceneBook"),
            "danh sách cảnh được RENDER (màn Cài đặt vẽ nó) ⇒ phải nằm trong HomeUiState, không thì bề mặt cấu " +
                "hình và màn hình lệch nhau — đúng bẫy hai-bản-sao dự án đã trả giá bốn lần",
        )
        assertTrue(
            port.contains("fun sceneBook(): SceneBook = SceneBook.EMPTY"),
            "cổng dữ liệu phải có THÂN MẶC ĐỊNH ⇒ bản giả in-memory trong test không phải sửa, và mặc định phải " +
                "KHỚP nơi lưu bền (chưa lưu gì ⇒ sổ rỗng)",
        )
        assertTrue(port.contains("fun setSceneBook("), "và phải có đường ghi qua cổng")
    }

    /** Lưu theo HỒ SƠ (như bố cục), không phải chung cả máy: cảnh là cách bố trí của một người. */
    @Test
    fun `so canh va canh khoi dong luu THEO HO SO`() {
        listOf("K_SCENES", "K_BOOT_SCENE").forEach { k ->
            assertTrue(
                Regex("""key\($k\)""").containsMatchIn(prefs),
                "$k phải đi qua key(...) (tiền tố tên hồ sơ) — không theo hồ sơ thì hai tài xế dùng chung một bộ cảnh",
            )
        }
        assertTrue(prefs.contains("""K_SCENES = "scenes""""), "tên khoá phải khớp thứ khai trong SettingsCatalog")
        assertTrue(prefs.contains("""K_BOOT_SCENE = "boot_scene""""))
    }

    // ── 2 · nạp trong load() ─────────────────────────────────────────────────────────────────────

    @Test
    fun `so canh nap trong load, cung luot voi moi thu khac`() {
        val fn = SourceRoots.body(repo, "override fun load()")
        assertTrue(
            fn.contains("scenes = prefs.sceneBook()"),
            "nạp cùng lượt ⇒ ca ĐỔI HỒ SƠ tự đúng (switchProfile gọi lại load()). Nạp riêng ở tầng UI thì sẽ có " +
                "lần quên — đúng lỗi hồ sơ B hiện bố cục của A",
        )
    }

    /**
     * **T3** — cảnh lúc nổ máy được áp ở lượt `load()` ĐẦU của tiến trình, và **đúng một lần**.
     *
     * Cờ một-lần là phần không được bỏ: `load()` còn được gọi lại cho `switchProfile`/`addProfile`/`deleteProfile`,
     * nên thiếu nó thì bấm sang hồ sơ B sẽ bị cảnh khởi động của B ghi đè ngay lên bố cục vừa nạp của B.
     */
    @Test
    fun `canh khoi dong duoc ap o luot load dau cua tien trinh`() {
        val fn = SourceRoots.body(repo, "override fun load()")
        // ⚠ Đòi ĐÚNG phép gác, không chỉ đòi thấy tên cờ: [ĐO] thử phá — đổi điều kiện thành `if (false) return base`
        // mà bài này **vẫn XANH**, vì chữ `coldStartDone` còn xuất hiện ở dòng gán ngay dưới. Một chốt như thế là
        // trang trí; cùng họ với bài `toldW` của T4 và với 44 phép cắt vùng `substringAfter` mà dự án đã phải đi vá.
        assertTrue(
            Regex("""if\s*\(\s*coldStartDone\s*\)\s*return\s+base""").containsMatchIn(fn),
            "phải THOÁT SỚM khi đã qua lượt nạp đầu — thiếu phép gác đó thì đổi hồ sơ cũng bị áp cảnh khởi động, " +
                "ghi đè ngay lên bố cục vừa nạp của hồ sơ mới",
        )
        assertTrue(fn.contains("coldStartDone = true"), "và phải đóng cờ lại, không thì phép gác không bao giờ đóng")
        assertTrue(fn.contains("bootScene()"), "phải đọc cảnh khởi động qua bootScene() (tự bỏ con trỏ treo)")
        assertTrue(fn.contains("withScene("), "và áp bằng CÙNG hàm mà intent gọi-cảnh dùng")
        assertTrue(
            fn.contains("persist(applied)") && fn.contains("setGridLayout(applied.customLayout)"),
            "áp cảnh phải GHI BỀN cả workspace/dock lẫn bố cục (bố cục ở khoá riêng) — chỉ đổi trong bộ nhớ thì " +
                "màn hình và đĩa nói hai chuyện khác nhau",
        )
        assertTrue(
            repo.contains("@Volatile private var coldStartDone"),
            "cờ bị đọc/ghi từ nhiều thread (Activity trên main; KachiAutostart.logBootPlan trên thread nền của FGS)",
        )
    }

    // ── 3 · mọi thay đổi đi qua intent, tầng UI không ghi bền ────────────────────────────────────

    @Test
    fun `bon intent canh di duong mot chieu day du`() {
        listOf("fun saveScene(", "fun setBootScene(", "fun deleteScene(", "fun renameScene(").forEach { sig ->
            assertTrue(vm.contains(sig), "thiếu intent $sig")
        }
        // ĐÚNG MỘT chỗ ghi bền sổ cảnh: bốn lần lặp "update rồi ghi" là bốn chỗ có thể quên nửa sau.
        assertEquals(
            1, Regex("""repository\.setSceneBook\(""").findAll(vm).count(),
            "setSceneBook phải được gọi ở ĐÚNG MỘT chỗ trong ViewModel (gom về mutateScenes)",
        )
        val fn = SourceRoots.body(vm, "private fun mutateScenes(")
        assertTrue(fn.contains("_uiState.updateAndGet"), "state và lưu bền phải đi trong MỘT lượt")
        assertTrue(fn.contains("repository.setSceneBook("), "và phải ghi qua cổng dữ liệu")
    }

    @Test
    fun `tang UI KHONG ghi ben truc tiep`() {
        listOf("SettingsSceneSection.kt" to section, "SceneController.kt" to controller).forEach { (name, src) ->
            assertFalse(
                src.contains("WorkspacePrefs(") || src.contains("repository."),
                "$name phải đi qua intent của ViewModel — tầng UI 0 lần ghi bền trực tiếp (luật kiến trúc)",
            )
        }
        assertFalse(
            section.contains("viewModel"),
            "bộ dựng view chỉ được biết SettingsDeps; chạm thẳng ViewModel là mở đường ghi đi vòng",
        )
    }

    // ── 4 · R4 (C5) — gọi cảnh KHÔNG được làm app trong ô mở lại ─────────────────────────────────

    /**
     * ⚠⚠ Bài **quan trọng nhất** của dây nối. `SceneBookTest` chứng minh *luật* (bộ quyết định không đòi dựng lại);
     * bài này chặn *cách nối sai* — chia thành ba intent rời sẽ phát ba lượt state, và một trạng thái trung gian có số
     * ô khác là đủ để bộ quyết định trả "dựng lại tất cả" ⇒ app đang chiếu bị nhả/gắn lại (P-bug1/R3).
     */
    @Test
    fun `applyScene la MOT phep copy, khong phai ba intent roi`() {
        val fn = SourceRoots.body(vm, "fun applyScene(")
        assertTrue(fn.contains("withScene("), "phải đi qua hàm thuần duy nhất ở :core")
        assertEquals(
            1, Regex("""_uiState\.updateAndGet""").findAll(fn).count(),
            "đúng MỘT lần cập nhật state ⇒ một lượt render (R4/C5)",
        )
        listOf("setPreset(", "withPreset(", "assignWidgets(", "withSlot(", "setCustomLayout(").forEach { bad ->
            assertFalse(fn.contains(bad), "không được gọi lại đường đặt-từng-phần ($bad) — đó là ba lượt render")
        }
        // Và `withScene` chỉ được chạm ba trường; hình nền/chip/đơn vị/giao diện KHÔNG thuộc cảnh (§6 OQ1).
        val ws = SourceRoots.body(core, "fun HomeUiState.withScene(")
        listOf("workspace =", "dock =", "customLayout =").forEach {
            assertTrue(ws.contains(it), "withScene phải áp $it")
        }
        listOf("wallpaper", "topStrip", "unitPrefs", "themeMode", "langMode", "activeProfile", "autostart").forEach {
            assertFalse(
                ws.contains(it),
                "cảnh là cách bố trí VÙNG LÀM VIỆC — kéo '$it' vào làm 'gọi cảnh' thành 'đổi cả giao diện' (§6 OQ1)",
            )
        }
    }

    /** Sổ cảnh đổi ⇒ danh sách trong Cài đặt phải vẽ lại, không thì lưu/xoá là "màn hình không đổi gì". */
    @Test
    fun `so canh doi thi trang Cai dat phai dung lai`() {
        val fn = SourceRoots.body(activity, "private fun render(state: HomeUiState)")
        assertTrue(
            fn.contains("prev.scenes != state.scenes"),
            "render phải nhận ra sổ cảnh đổi — trang Cài đặt được NHỚ LẠI nên nó không tự dựng lại",
        )
        val i = fn.indexOf("prev.scenes != state.scenes")
        assertTrue(
            fn.substring(i, (i + 120).coerceAtMost(fn.length)).contains("invalidateSettings()"),
            "và phải dựng lại trang đang xem",
        )
    }

    // ── 5 · đặt được từ tay người dùng + nói ra khi bị từ chối ───────────────────────────────────

    /**
     * Luật **"vẽ được ≠ đặt được"** (RW0): một khả năng chỉ tính là xong khi có đường **đi từ tay người dùng** tới nó.
     * Năm việc của [SceneActions] phải đều có chỗ gọi trong bề mặt cài đặt — kể cả *đổi tên*, thứ dễ bị để lại thành
     * một hàm ở `:core` mà không ai chạm tới được.
     */
    @Test
    fun `nam viec voi canh deu co duong tu tay nguoi dung`() {
        mapOf(
            "deps.scenes.save()" to "lưu cảnh hiện tại",
            "deps.scenes.apply(" to "gọi lại cảnh",
            "deps.scenes.setBoot(" to "đánh dấu cảnh lúc nổ máy",
            "deps.scenes.rename(" to "đổi tên cảnh",
            "deps.scenes.delete(" to "xoá cảnh",
        ).forEach { (call, what) ->
            assertTrue(section.contains(call), "không có đường nào để người dùng $what — 'vẽ được' ≠ 'đặt được'")
        }
        // Và mục Cảnh thật sự được dựng trong trang "Màn hình chính".
        assertTrue(
            SourceRoots.body(home, "fun build(body: LinearLayout)").contains("SettingsSceneSection("),
            "mục Cảnh phải được dựng trong nhóm Màn hình chính, không thì nó là mã không ai thấy",
        )
        // Chuỗi từ SceneActions tới ViewModel phải liền: controller gọi đúng bốn intent.
        listOf("viewModel.saveScene(", "viewModel.applyScene(", "viewModel.setBootScene(",
            "viewModel.deleteScene(", "viewModel.renameScene(").forEach {
            assertTrue(controller.contains(it), "SceneController phải chuyển tiếp tới $it")
        }
    }

    /**
     * Ba ca bị `:core` **từ chối** đều phải NÓI RA: tên rỗng · đủ trần · tên đã thuộc cảnh khác. Im lặng ở đây là cú
     * bấm biến mất — đúng họ lỗi dự án đã vá ba lần (`DockConfig.setEnabled` · trần 8 mục ngăn kéo · nút bố cục sẵn).
     */
    @Test
    fun `ba ca bi tu choi deu duoc noi ra`() {
        assertTrue(
            SourceRoots.body(controller, "override fun save()").contains("kachi_scenes_full"),
            "đủ trần phải nói ra ngay lúc người dùng bấm Lưu",
        )
        assertTrue(
            SourceRoots.body(controller, "private fun askName(").contains("kachi_scene_name_empty"),
            "tên rỗng phải nói ra — 'Lưu' mà không có gì xảy ra là lúc người dùng nghi app hỏng",
        )
        assertTrue(
            SourceRoots.body(controller, "override fun rename(").contains("kachi_scene_name_taken"),
            "tên đã thuộc cảnh khác phải nói ra",
        )
        // Câu đếm luôn hiện sẵn PHẢI đổi hình khi đủ trần (chỉ đổi chữ thì trông y như không có gì xảy ra).
        val build = SourceRoots.body(section, "fun build(body: LinearLayout)")
        assertTrue(build.contains("book.full"), "trang phải hỏi sổ đã đủ trần chưa")
        assertTrue(build.contains("KachiTheme.AMBER"), "và đổi màu, không chỉ đổi chữ")
    }

    /**
     * Hợp đồng của **cảnh lúc nổ máy** phải được nói thẳng trên màn — đây là chỗ trả giá cho ngữ nghĩa đã chọn
     * (áp lúc khởi động nguội, xem KDoc `PrefsWorkspaceRepository.load`). Người dùng phải biết TRƯỚC.
     */
    @Test
    fun `hop dong cua canh luc no may duoc noi thang tren man`() {
        assertTrue(section.contains("kachi_scene_boot_contract"), "phải nói khi ĐÃ chọn cảnh khởi động")
        assertTrue(
            section.contains("kachi_scene_boot_none"),
            "và cả khi CHƯA chọn — không nói thì người dùng không biết dấu đó tồn tại, cũng không biết mặc định là gì",
        )
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val contract = Regex("""<string name="kachi_scene_boot_contract">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(vi)?.groupValues?.get(1)
            ?: error("không có chuỗi kachi_scene_boot_contract — bài test đang quét vùng không tồn tại")
        assertTrue(
            contract.contains("mỗi lần khởi động"),
            "câu phải nói rõ nó áp MỖI LẦN khởi động, không chỉ 'lúc nổ máy'",
        )
        assertTrue(
            contract.contains("sẽ bị thay"),
            "và phải nói rõ cách bố trí chưa lưu SẼ BỊ THAY — đây là cái giá của ngữ nghĩa đã chọn, không được im",
        )
        // Có đường BỎ dấu (không phải xoá cảnh) — nếu không thì người dùng bị kẹt với cảnh khởi động.
        assertTrue(
            SourceRoots.body(section, "private fun sceneRow(").contains("if (boot) null else scene.id"),
            "chạm lại dấu đang bật phải BỎ dấu",
        )
    }

    // ── 6 · chữ qua tài nguyên + khoá khai trong danh mục ────────────────────────────────────────

    @Test
    fun `hai khoa canh khai trong SettingsCatalog`() {
        assertEquals(SettingsGroup.HOME, SettingsCatalog.groupOf("scenes"), "sổ cảnh thuộc nhóm Màn hình chính")
        assertEquals(SettingsGroup.HOME, SettingsCatalog.groupOf("boot_scene"))
        assertTrue(SettingsCatalog.orphans(setOf("scenes", "boot_scene")).isEmpty())
        // Nút "Lưu cảnh hiện tại…" là VIỆC LÀM ⇒ không sở hữu khoá nào (một khoá, một chủ).
        assertEquals(null, SettingsCatalog.ENTRIES.first { it.id == "home_scene_save" }.prefKey)
    }

    @Test
    fun `moi chu cua muc Canh di qua tai nguyen`() {
        // Bài `LauncherI18nContractTest` đã quét toàn tầng vẽ; ở đây chốt riêng hai tệp mới bằng phép đếm tối thiểu,
        // để nếu ai đó thêm một dòng chữ trần vào ĐÚNG hai tệp này thì có một bài nói đúng tên nó.
        listOf("SettingsSceneSection.kt" to section, "SceneController.kt" to controller).forEach { (name, src) ->
            assertTrue(src.contains("R.string.kachi_"), "$name phải lấy chữ từ tài nguyên")
            val vn = Regex("[ăâđêôơưàáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ]")
            val bad = Regex(""""(?:\\.|[^"\\\n])*"""").findAll(src).map { it.value.trim('"') }
                .filter { vn.containsMatchIn(it) }.toList()
            assertEquals(emptyList<String>(), bad, "$name còn chuỗi tiếng Việt viết cứng")
        }
    }
}
