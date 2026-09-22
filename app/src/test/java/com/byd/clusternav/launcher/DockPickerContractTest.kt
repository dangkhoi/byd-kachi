package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ T6 · R-UI (m) — MỘT BỘ CHỌN, HAI LỐI VÀO ════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-settings-ia-v2.html` §3 R-UI **(m)**: bỏ lưới 123 ô khỏi màn Cài đặt; nhóm thanh-nút chỉ
 * còn một nút mở **đúng bộ chọn của ngăn kéo** ở chế độ dock (đa chọn, tô sẵn theo `dock.enabled`).
 *
 * ## Bài này canh cái gì, và vì sao chia làm hai lớp
 *  1. **Phần THUẦN** ([DockSelection]) — chạy thật, không mock: tập người dùng chốt → `DockConfig.enabled`. Đây là
 *     chỗ duy nhất trong đường dây có một quyết định (thứ tự + chiều tắt), nên nó phải được kiểm bằng cách **chạy**,
 *     không bằng cách đọc mã.
 *  2. **Phần NỐI DÂY** — quét mã nguồn `:app`: chế độ thứ ba có thật, nguồn dữ liệu lấy từ `:core` (không chép danh
 *     sách), nút Áp dụng gọi `onApply` với **chính** tập đang chọn, và bảng tự đóng sau đó.
 *
 * ## ⚠ Bài quét mã `:app` ⇒ phải NẰM ở `:app`
 * Luật đã trả giá hai lần (S1 · G1): bài quét mã module X mà đặt ở module Y thì Gradle không coi mã của X là đầu
 * vào ⇒ `UP-TO-DATE` đúng ở ca bài sinh ra để bắt. `app/build.gradle.kts` đã khai `inputs.dir("src/main/java")`.
 */
class DockPickerContractTest {

    private val drawer by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/AppDrawer.kt") }
    private val controller by lazy {
        SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/DrawerController.kt")
    }

    // ══ (1) PHẦN THUẦN — chạy thật ════════════════════════════════════════════════════════════════════════

    @Test
    fun `tap chon tra ve dung tap do`() {
        val base = DockConfig(enabled = listOf("readl", "win_lf", "trunk"))
        val out = DockSelection.apply(base, setOf("readl", "trunk", "fan"))
        assertEquals(setOf("readl", "trunk", "fan"), out.enabled.toSet(), "cấu hình sau khi áp = đúng tập đã chọn")
    }

    /**
     * ⚠⚠ CHIỀU TẮT — bẫy số 1 của KDoc [DockSelection].
     *
     * Cách viết tự nhiên nhất ở chỗ gọi (`selected.forEach { setEnabled(it, true) }`) làm cấu hình **chỉ lớn lên**:
     * bỏ tích một ô rồi bấm Áp dụng mà nút vẫn còn trên thanh, không một lời nào. Bài này đỏ ngay ở ca đó.
     */
    @Test
    fun `bo tich mot o thi o do RA KHOI thanh`() {
        val base = DockConfig(enabled = listOf("readl", "win_lf", "trunk"))
        val out = DockSelection.apply(base, setOf("readl", "trunk"))
        assertFalse("win_lf" in out.enabled, "mã bị bỏ tích phải rời thanh — chỉ gửi chiều BẬT là bỏ qua im lặng")
        assertEquals(listOf("readl", "trunk"), out.enabled)
    }

    /** Bẫy số 2: áp một tập KHÔNG đổi gì thì thứ tự nút trên thanh phải y nguyên (không sắp lại theo catalog). */
    @Test
    fun `ap lai dung tap cu KHONG xao thu tu`() {
        val base = DockConfig(enabled = listOf("trunk", "readl", "win_lf"))
        assertEquals(base.enabled, DockSelection.apply(base, base.enabled.toSet()).enabled)
    }

    /** Mã mới nối vào CUỐI (không chen vào giữa), theo thứ tự khai của catalog — cùng thứ tự bộ chọn đang bày. */
    @Test
    fun `ma moi noi vao cuoi theo thu tu catalog`() {
        val base = DockConfig(enabled = listOf("trunk"))
        val added = CapabilityCatalog.all().map { it.id }.filter { it != "trunk" }.take(2)
        val out = DockSelection.apply(base, (listOf("trunk") + added).toSet())
        assertEquals(listOf("trunk") + added, out.enabled, "phần cũ giữ chỗ, phần mới nối cuối theo thứ tự catalog")
    }

    /** Tập rỗng = "bỏ hết nút khỏi thanh" — một lựa chọn HỢP LỆ, không phải ca phải chặn. */
    @Test
    fun `tap rong bo het nut khoi thanh`() {
        assertEquals(emptyList<String>(), DockSelection.apply(DockConfig(enabled = listOf("readl")), emptySet()).enabled)
    }

    /**
     * S4 · R12 — **thanh nút NHẬN hành động của launcher**, đi qua đúng đường cũ.
     *
     * [DockSelection.apply] không biết gì về loại khả năng (cố ý — "mã nào vào được thanh" chỉ có MỘT chỗ trả lời
     * là `DockConfig.setEnabled`), nên bài này chạy thật để chứng minh cổng đó đã mở cho loại thứ ba: `setEnabled`
     * hỏi `CapabilityCatalog.kindOf(id) == null`, mà `kindOf` nay trả [CapabilityKind.LAUNCHER] cho hai mã này.
     * Không có phép chạy này thì một lần siết cổng thành `kindOf(id) != WRITE` sẽ làm nút *Ứng dụng* im lặng biến
     * mất khỏi thanh của người đã đặt nó.
     */
    @Test
    fun `thanh nut nhan hanh dong cua launcher`() {
        val base = DockConfig(enabled = listOf("readl"))
        val out = DockSelection.apply(base, setOf("readl", LauncherActions.APPS, LauncherActions.SETTINGS))
        assertEquals(
            listOf("readl", LauncherActions.APPS, LauncherActions.SETTINGS), out.enabled,
            "hai mã launcher phải vào được thanh, nối vào CUỐI theo thứ tự catalog",
        )
        // Và bỏ tích vẫn gỡ được (chiều TẮT không được quên loại mới).
        assertEquals(listOf("readl"), DockSelection.apply(out, setOf("readl")).enabled)
    }

    /** Mã lạ vẫn bị `DockConfig.setEnabled` từ chối — [DockSelection] KHÔNG được nhân bản phép kiểm đó. */
    @Test
    fun `ma la khong vao duoc thanh`() {
        val out = DockSelection.apply(DockConfig(enabled = emptyList()), setOf("khong_he_ton_tai"))
        assertEquals(emptyList<String>(), out.enabled)
        assertFalse(
            SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/DockSelection.kt")
                .contains("CapabilityCatalog.kindOf("),
            "phép kiểm 'mã nào vào được thanh' phải ở ĐÚNG MỘT chỗ (DockConfig.setEnabled), không chép sang đây",
        )
    }

    // ══ (2) NỐI DÂY — chế độ thứ ba có thật và đi đúng đường ═══════════════════════════════════════════════

    @Test
    fun `ngan keo co che do chon nut thanh xe`() {
        assertTrue(
            Regex("""enum class Mode \{ ASSIGN_SLOT, OPEN_APP, PICK_DOCK \}""").containsMatchIn(drawer),
            "phải là chế độ thứ ba của CHÍNH bảng này — dựng một bảng thứ hai là quay lại 'hai lưới một tập ô'",
        )
    }

    /**
     * Nút Áp dụng bắn **chính** tập đang chọn, và bắn qua `onApply` chứ không qua `onPickWidgets`.
     *
     * Nếu nó gửi một giá trị dựng lại tại chỗ (vd đọc lại từ `widgetTiles`) thì có hai nguồn sự thật cho một câu
     * hỏi *"người dùng đang chọn gì"* — và chúng sẽ lệch đúng lúc ai đó sửa một nguồn.
     */
    @Test
    fun `nut Ap dung ban dung tap dang chon`() {
        val bar = SourceRoots.body(drawer, "private fun placeBar()")
        assertTrue(
            Regex("""onApply\(selected\.toSet\(\)\)""").containsMatchIn(bar),
            "nút áp ở chế độ dock phải gọi onApply với CHÍNH `selected`",
        )
        assertTrue(bar.contains("onPickWidgets("), "chế độ gán ô vẫn đi đường cũ — một nút, hai đích theo chế độ")
    }

    /** Bảng phải TỰ ĐÓNG sau khi áp: để mở là người dùng không biết cú bấm đã ăn chưa (lỗi "im lặng" quen thuộc). */
    @Test
    fun `ap xong thi bang tu dong`() {
        val fn = SourceRoots.body(controller, "fun openDockPicker(")
        assertTrue(
            Regex("""onApply = \{ ids -> onApply\(ids\); close\(\) \}""").containsMatchIn(fn),
            "openDockPicker phải chuyển tiếp tập rồi đóng bảng",
        )
        assertTrue(
            Regex("""mode = AppDrawer\.Mode\.PICK_DOCK""").containsMatchIn(fn),
            "và phải mở CHÍNH AppDrawer ở chế độ dock (một bộ chọn, hai lối vào)",
        )
    }

    /** Tô sẵn theo tập truyền vào — mở bộ chọn mà không thấy cấu hình hiện tại là bắt người dùng nhớ hộ máy. */
    @Test
    fun `to san theo tap truyen vao`() {
        val fn = SourceRoots.body(controller, "fun openDockPicker(")
        assertTrue(
            Regex("""WidgetRegistry\.ALL,\s*selected\.toList\(\)""").containsMatchIn(fn),
            "tập đang bật phải được truyền làm lựa chọn ban đầu của bảng",
        )
    }

    /**
     * ⚠⚠ NGUỒN DỮ LIỆU — chế độ dock phải bày **ĐÚNG** tập ô mà màn Cài đặt bày, qua CÙNG hàm của `:core`.
     *
     * Đây là lý do tồn tại của R-UI (m): hai bề mặt tự liệt kê thì chúng lệch nhau (đã lệch thật ba lần). Bài canh
     * đòi cả hai gọi `CapabilityPicker.groupPicks()` + `CapabilityPicker.singlesOf(...)` trên
     * `CapabilityCatalog.byDomain()` — và **cấm** ngăn kéo có một danh sách mã viết tay.
     */
    @Test
    fun `nguon du lieu lay tu core, khong chep danh sach`() {
        // T4 · IA v2 R-UI (m): lưới 123 ô đã RỜI khỏi màn Cài đặt — nhóm "Thanh trạng thái & thanh nút" nay
        // mở CHÍNH bộ chọn của ngăn kéo (`AppDrawer.Mode.PICK_DOCK`). Một bộ chọn, một nguồn ⇒ phép so "hai
        // màn phải giống nhau" không còn đối tượng, và `CapabilityGridSection` đã bị xoá.
        // Nên danh sách này còn ĐÚNG MỘT bề mặt — và đó chính là điều R-UI (m) muốn đạt được.
        listOf("AppDrawer.kt" to drawer)
            .forEach { (name, src) ->
                assertTrue(src.contains("CapabilityPicker.groupPicks()"), "$name phải lấy ô NHÓM từ :core")
                assertTrue(src.contains("CapabilityPicker.singlesOf("), "$name phải lọc mục lẻ bằng hàm của :core")
                assertTrue(src.contains("CapabilityCatalog.byDomain()"), "$name phải duyệt lĩnh vực từ :core")
            }
    }

    /**
     * Thân bảng chế độ dock đi qua **cùng** hai hàm mục mà chế độ gán-ô dùng.
     *
     * Nếu nó dựng riêng thì R-UI (m) mới chỉ dời được lưới, chưa gộp được bộ chọn — và hai thân bảng sẽ lệch đúng
     * lúc ai đó thêm một lĩnh vực mới.
     */
    @Test
    fun `che do dock dung chung than bang voi che do gan o`() {
        val init = SourceRoots.body(drawer, "    init {")
        assertTrue(
            Regex("""if \(dock\) \{[\s\S]{0,600}?groupSection\(body\); singlesSection\(body\)""")
                .containsMatchIn(init),
            "chế độ dock phải dùng lại groupSection/singlesSection, không dựng thân bảng thứ hai",
        )
        listOf("groupSection(body)", "singlesSection(body)").forEach {
            assertEquals(
                2, Regex(Regex.escape(it)).findAll(init).count(),
                "$it phải được gọi ở đúng HAI chế độ (gán ô + dock) — nhiều hơn là có bản sao",
            )
        }
    }

    /**
     * Chế độ dock KHÔNG bày widget dựng tay / widget app khác / danh sách app.
     *
     * `DockConfig.setEnabled` chỉ nhận mã có trong [CapabilityCatalog], nên một ô widget trong bảng này sẽ được tô
     * sáng, được đếm vào "Áp dụng (N)", rồi **biến mất im lặng** lúc ghi — đúng họ lỗi mà cả tệp
     * `PickerCapNoticeContractTest` đi dọn.
     */
    @Test
    fun `che do dock khong bay thu khong dat duoc len thanh`() {
        val init = SourceRoots.body(drawer, "    init {")
        val dockBranch = init.substringAfter("if (dock) {").substringBefore("} else if (assign) {")
        listOf("addWidgetGrid(", "apps.grid(", "appWidgetPicks").forEach {
            assertFalse(dockBranch.contains(it), "nhánh dock không được bày `$it` — nó không đặt được lên thanh nút")
        }
    }

    // ══ (3) CHỮ — chế độ mới phải có bộ chữ RIÊNG, song ngữ ═══════════════════════════════════════════════

    @Test
    fun `che do dock co tieu de hint va nut rieng`() {
        listOf("kachi_drawer_title_dock", "kachi_drawer_hint_dock", "kachi_drawer_apply_n").forEach { key ->
            assertTrue(drawer.contains("R.string.$key"), "chế độ dock phải dùng chuỗi riêng `$key`")
            listOf("values", "values-en").forEach { dir ->
                assertTrue(
                    Regex("""<string name="$key">""")
                        .containsMatchIn(SourceRoots.text("src/main/res/$dir/strings_kachi.xml")),
                    "`$key` phải có ở res/$dir/strings_kachi.xml",
                )
            }
        }
        // `%1$d` chứa `$` — dùng `contains` chứ không Regex: trong Regex `$` là NEO CUỐI DÒNG, nên mẫu vẫn khớp
        // khi bản dịch đã gõ cứng con số vào chữ ⇒ bài canh xanh giả đúng ở ca nó sinh ra để bắt.
        val applyVi = Regex("""<string name="kachi_drawer_apply_n">([^<]*)</string>""")
            .find(SourceRoots.text("src/main/res/values/strings_kachi.xml"))?.groupValues?.get(1).orEmpty()
        assertTrue(
            applyVi.contains("%1${'$'}d"),
            "nhãn nút áp phải NHẬN số mục làm tham số (thấy: \"$applyVi\") — viết số vào chữ là hai nguồn sự thật",
        )
    }
}
