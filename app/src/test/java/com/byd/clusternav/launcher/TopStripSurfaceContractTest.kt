package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * BỀ MẶT CẤU HÌNH TRÊN **THANH TRÊN** — khoá §4.5 sau chỉ thị của owner *"không để cấu hình nằm lỉ tỉ"*.
 *
 * ## Bệnh nó chữa
 * [ĐO] lượt soát 2026-09-11: thanh trên có pill **"Thanh"** → `HomeViewModel.cycleDockEdge()` → ghi bền `dock_edge`.
 * Đó là bề mặt cấu hình **thứ ba** trên thanh trên, tồn tại nhiều phiên mà **không bài test nào phản đối** — vì luật
 * *"thanh trên chỉ được có một cửa vào cấu hình"* chỉ nằm trong văn xuôi của spec, và văn xuôi không chặn được ai.
 * Bài `SettingsScreenWiringContractTest.thanh tren chi con MOT cua vao cau hinh` cũng không thấy: nó chỉ đếm pill
 * *mở cấu hình* (`"Cài đặt"`), còn pill "Thanh" thì **đổi cấu hình tại chỗ** nên không lọt vào phép đếm đó.
 *
 * ## Vì sao kiểm theo KHOÁ LƯU BỀN, không theo số pill
 * "Bao nhiêu pill" là con số dễ lách (đổi pill thành icon, gộp vào ô segmented, gắn cử chỉ giữ…). Điều owner thật sự
 * yêu cầu là **thanh trên không phải chỗ đặt cấu hình** ⇒ phép kiểm đúng là *"thanh trên được chạm khoá lưu bền nào"*,
 * và câu trả lời nằm ở [SettingsCatalog.TOP_STRIP_ALLOWED_KEYS] (`:core`, có lý do cho từng khoá). Ai muốn nới phải
 * sửa `:core` — tức phải viết lý do cạnh hai lý do đã có, không thể thêm lặng lẽ một dòng vào tầng UI.
 *
 * Ba lớp chặn, cố ý chồng nhau vì mỗi lớp bịt một đường lách khác nhau:
 *  1. **cổng vào** ([`bang cong cua thanh tren…`]) — mỗi bề mặt bấm được cần một callback; callback lạ ⇒ đỏ;
 *  2. **danh sách pill** — thêm pill nào cũng đỏ, kể cả khi nó dùng lại callback cũ;
 *  3. **khoá lưu bền** — bảng cổng→khoá không được vượt danh sách cho phép ở `:core`.
 *
 * ## ⚠⚠ S4 · R7 — §4.5 BỊ ĐẢO MỘT NỬA, và bài này đi theo
 * §4.5 cho thanh trên giữ HAI thứ: 5 nút bố cục + avatar hồ sơ. S4 gỡ nhóm thứ nhất (owner 2026-09-14: *"bỏ luôn
 * các nút đổi bố cục trên header"*) vì hồ sơ nay giữ **tất cả** lựa chọn ⇒ việc hằng ngày là đổi cả bộ, không phải
 * đổi riêng bố cục. Nhóm thứ hai ở lại nhưng đổi hình: avatar một-chữ-cái xoay vòng → **chip tên hồ sơ** mở bộ
 * chọn ([ProfileChip]). Ba lớp chặn trên giữ nguyên; chỉ **nội dung** bảng cổng hẹp lại.
 */
class TopStripSurfaceContractTest {

    private val strip by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiTopStrip.kt") }
    private val chip by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ProfileChip.kt") }
    private val activity by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val vm by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/HomeViewModel.kt") }

    /**
     * Cổng vào của thanh trên → khoá lưu bền mà cổng đó được phép chạm (`null` = **không lưu gì**, chỉ mở một màn).
     *
     * Bảng này là phần "ý định" của phép kiểm, nên nó phải ở đây chứ không suy ra từ mã: mã chỉ nói *cổng nào đang
     * tồn tại*, còn *cổng đó được phép làm gì* là quyết định của §4.5.
     */
    private val gates: Map<String, String?> = mapOf(
        // ⚠⚠ S4 · R7 — `onSelectPreset` ĐÃ RỜI khỏi bảng này. §4.5 giữ 5 nút bố cục vì "đổi bố cục là việc hằng
        // ngày"; S4 đảo quyết định đó (owner 2026-09-14: *"bỏ luôn các nút đổi bố cục trên header"*) vì hồ sơ nay
        // giữ TẤT CẢ, nên việc hằng ngày là đổi CẢ BỘ. Bố cục sẵn còn đúng một bề mặt: Cài đặt → Màn hình chính.
        "onProfileTap" to "active_profile",      // chip hồ sơ — "đang ở hồ sơ nào" phải thấy liên tục
        "onOpenSettings" to null,                // mở màn Cài đặt: không tự đổi gì
        "onOpenAppList" to null,                 // U3 mở app toàn màn: không phải cấu hình
        // V1 pha NGHE (R12 b) — mở một PHIÊN NGHE. Vào bảng này được vì nó giống hệt hai cổng `null` ở trên về
        // mặt §4.5: **không chạm một khoá lưu bền nào**, chỉ mở một bề mặt rồi đóng lại. Và nó không phải một
        // "bề mặt cấu hình lỉ tỉ" thứ tư — nó là lối tắt tới chính những việc mà hai cổng kia mở ra từng cái một.
        // Công tắc bật/tắt của nó nằm ở Cài đặt › Thanh trạng thái, đúng chỗ mọi lựa chọn thanh trên đang ở.
        "onVoice" to null,
    )

    @Test
    fun `bang cong cua thanh tren dung nhu §4_5 cho phep`() {
        val ctor = ctorParams(strip, "class KachiTopStrip(")
        val found = Regex("""\bprivate val (on[A-Z]\w*)\s*:""").findAll(ctor).map { it.groupValues[1] }.toSortedSet()
        assertEquals(
            gates.keys.toSortedSet(), found,
            "cổng vào của thanh trên đã đổi. Mỗi bề mặt bấm được cần một callback, nên một cổng MỚI nghĩa là một bề " +
                "mặt mới trên thanh trên. Sau S4 · R7 + V1 pha NGHE chỉ còn ${gates.keys}: hồ sơ (phải thấy liên " +
                "tục) + hai lối mở màn + lối nói. Cấu hình khác thuộc màn Cài đặt — xem KDoc " +
                "SettingsCatalog.TOP_STRIP_ALLOWED_KEYS",
        )
    }

    /**
     * DANH SÁCH THAM SỐ của hàm dựng — **không** dùng `SourceRoots.body` được ở đây: hàm đó trả **thân** khối, mà
     * tham số của hàm dựng chính (primary constructor) nằm NGOÀI thân lớp. [ĐO] bản đầu của tôi dùng `body()` và tìm
     * ra 0 cổng — bài đỏ vì chính phép cắt, không vì mã sai.
     *
     * Vẫn giữ tính chất quan trọng nhất của `body()`: **nổ nếu mốc không tồn tại**, thay vì âm thầm quét vùng rỗng.
     */
    private fun ctorParams(src: String, marker: String): String {
        val at = src.indexOf(marker)
        require(at >= 0) { "không tìm thấy '$marker' — bài test đang quét vùng KHÔNG tồn tại (quét tràn = test giả)" }
        val open = at + marker.length - 1
        var depth = 0
        for (i in open until src.length) {
            when (src[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return src.substring(open + 1, i)
            }
        }
        error("danh sách tham số của '$marker' không đóng ngoặc")
    }

    /**
     * S4 · R7 — thanh trên nay chỉ còn ghi bền **một** khoá: `active_profile`.
     *
     * ⚠ [SettingsCatalog.TOP_STRIP_ALLOWED_KEYS] ở `:core` vẫn khai **hai** khoá (`preset` + `active_profile`). Đó là
     * **trần trên**, và bài này canh thanh trên không được vượt trần; phần hẹp hơn (`preset` không còn ai chạm) thì
     * assert thẳng ở dòng đầu. Vì sao chưa siết `:core`: tệp `SettingsCatalog.kt` thuộc lượt T1/T2 của S4 (một tệp
     * một chủ tại một thời điểm — CLAUDE.md §2). **TODO S4·T4**: bỏ `"preset"` khỏi danh sách đó + sửa KDoc của nó,
     * rồi đổi hai assert dưới thành một phép so bằng như trước.
     */
    @Test
    fun `thanh tren chi con ghi ben khoa active_profile`() {
        val persistent = gates.values.filterNotNull().toSortedSet()
        assertEquals(
            sortedSetOf("active_profile"), persistent,
            "sau S4 · R7 thanh trên chỉ được chạm `active_profile`: 5 nút bố cục (khoá `preset`) đã gỡ, và mọi cấu " +
                "hình khác thuộc màn Cài đặt",
        )
        assertTrue(
            SettingsCatalog.TOP_STRIP_ALLOWED_KEYS.containsAll(persistent),
            "thanh trên đang chạm khoá KHÔNG có trong danh sách cho phép ở `:core`: " +
                "${persistent - SettingsCatalog.TOP_STRIP_ALLOWED_KEYS}. Nới danh sách đó là một quyết định, phải " +
                "viết lý do cạnh các lý do đã có, không lặng lẽ thêm một dòng vào tầng UI",
        )
    }

    /**
     * S4 · R7 — **không còn một mẩu `LayoutPreset` nào** trên thanh trên.
     *
     * Vì sao kiểm cả ba dấu vết chứ không chỉ bảng cổng ở trên: bảng cổng chỉ thấy *callback* mới. Một bản vá sau
     * này hoàn toàn có thể dựng lại hàng nút bố cục mà **dùng lại** cổng có sẵn (vd nhét vào `onProfileTap`), hoặc
     * gọi thẳng một intent — đúng loại lách mà KDoc lớp này liệt kê. Ba dấu vết dưới đây là thứ hàng nút đó KHÔNG
     * thể thiếu: kiểu `LayoutPreset`, bộ icon `ic_layout_*`, và hàm tô sáng ô đang chọn.
     */
    @Test
    fun `thanh tren khong con mot mau LayoutPreset nao`() {
        listOf("LayoutPreset", "ic_layout_", "fun selectPreset(", "buildSegmented").forEach {
            assertFalse(
                strip.contains(it),
                "thanh trên còn dấu vết chọn bố cục ('$it') — S4 · R7 chuyển hẳn việc đó về Cài đặt → Màn hình chính",
            )
        }
        assertFalse(chip.contains("LayoutPreset"), "bộ chọn hồ sơ cũng không được là chỗ giấu lại hàng nút bố cục")
        // Đường THAY THẾ phải còn: gỡ nút mà không còn chỗ nào chọn bố cục sẵn là mất tính năng, không phải dọn dẹp.
        assertTrue(
            SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SettingsSectionsHome.kt")
                .contains("deps.onPreset("),
            "Cài đặt → Màn hình chính phải là bề mặt chọn bố cục sẵn (nay là bề mặt duy nhất)",
        )
    }

    /**
     * S4 · R7 — **xoay vòng hồ sơ bằng cú chạm đã hết hẳn**, `ProfileBar` bị xoá, không để mã chết.
     *
     * Cùng lẽ với bài "đường xoay vòng viền thanh nút" ngay dưới: một intent còn nằm đó là một lời mời nối lại. Ở
     * đây còn nặng hơn — `cycle()` đổi **cả bộ cấu hình** sang một hồ sơ người dùng không chọn tên, giữa lúc lái.
     */
    @Test
    fun `khong con duong xoay vong ho so - ProfileBar da xoa`() {
        assertFalse(
            SourceRoots.exists("src/main/java/com/byd/clusternav/launcher/ProfileBar.kt"),
            "ProfileBar phải bị xoá — việc duy nhất còn lại của nó (mở bộ chọn) nay ở ProfileChip",
        )
        val offenders = appSourcesContaining("ProfileBar")
        assertEquals(emptyList<String>(), offenders, "còn tham chiếu tới lớp đã xoá: $offenders")
        assertEquals(
            emptyList<String>(), appSourcesContaining(".cycle()"),
            "cú chạm chip hồ sơ KHÔNG được xoay vòng: nó phải mở bộ chọn có TÊN từng hồ sơ (ProfileChip.picker)",
        )
        assertTrue(chip.contains("fun picker("), "và bộ chọn đó phải tồn tại")
        assertTrue(
            chip.contains("SettingsDialogs.pick("),
            "bộ chọn phải dùng LẠI hộp thoại danh sách dùng chung, không dựng AlertDialog thứ hai",
        )
        assertTrue(
            chip.contains("R.string.kachi_profile_manage"),
            "bộ chọn phải có lối 'Quản lý hồ sơ…' sang Cài đặt — tạo/xoá hồ sơ vẫn chỉ ở đó (§4.5)",
        )
        // CLAUDE.md §8 — hàm mới phải CÓ chỗ gọi. `CastShell.evictVd` viết cẩn thận, compile sạch và chưa từng
        // chạy lần nào vì một lần viết lại đã nuốt mất call site; ở đây rủi ro y hệt (lần viết lại nằm ở tệp của
        // một agent khác). Không có hai dòng dưới thì chip hồ sơ có thể im lặng thành nút chết.
        assertEquals(
            // `[({]` vì chỗ gọi thật truyền lambda ở **cuối** (`picker { … }`) — dạng Kotlin chuẩn cho tham số cuối
            // là hàm. Mẫu chỉ nhận `\.picker\(` sẽ đỏ oan với đúng cách viết mà ktlint đòi.
            1, Regex("""\.picker\s*[({]""").findAll(activity).count(),
            "màn chính phải nối cú chạm chip vào ProfileChip.picker — đúng MỘT chỗ",
        )
        assertTrue(
            activity.contains("SettingsGroup.PROFILES"),
            "và 'Quản lý hồ sơ…' phải mở đúng nhóm Hồ sơ tài xế (panels.openSettings(SettingsGroup.PROFILES))",
        )
        assertTrue(
            activity.contains("topStrip.setProfile("),
            "tên trên chip phải được đổ lại khi state đổi — thiếu nó thì chip đứng nguyên tên hồ sơ cũ",
        )
    }

    /**
     * Danh sách pill — lớp chặn cho ca *"pill mới dùng LẠI callback cũ"*, mà bài bảng-cổng không thể thấy.
     */
    @Test
    fun `thanh tren chi con ba pill va khong pill nao doi cau hinh tai cho`() {
        val fn = SourceRoots.body(strip, "private fun build()")
        // U5·T3 — nhãn pill nay đến từ tài nguyên (`getString`), nên phép đếm đọc MÃ KHOÁ thay vì đọc chữ. Tính chất
        // được canh KHÔNG đổi: đúng hai pill, đúng thứ tự đó, và không pill nào đổi cấu hình tại chỗ.
        val pills = Regex("""pill\([^)]*?R\.string\.(\w+)""").findAll(fn).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf("kachi_pill_voice", "kachi_pill_apps", "kachi_pill_settings"), pills,
            "thanh trên chỉ được có ba pill: 'Nói với xe' (V1 pha NGHE — lối tắt tới mọi việc), 'Ứng dụng' (mở app) " +
                "và 'Cài đặt' (MỘT cửa vào cấu hình). Thêm pill nào nữa cũng là thêm một bề mặt lỉ tỉ — pill " +
                "'Thanh' vừa bị bỏ đúng vì lý do đó. Thứ tự cũng được canh: nút nói đứng đầu.",
        )
    }

    /**
     * ═══ S4 · R12 (a) — MỌI PILL CHỈ CÒN **ICON** (V1 pha NGHE thêm pill thứ ba, cùng luật) ════════════════════════════════════════════════════════════
     *
     * Owner 2026-09-14: *"đổi chữ Ứng Dụng, Cài Đặt thành icon luôn cho gọn"*. Ba tính chất, và cả ba đều là chỗ
     * mà một bản vá "cho gọn" dễ làm hỏng:
     *  1. **Không còn CHỮ**: `pill(...)` không được đặt `text`/`KachiType` nữa — chữ ở đây ăn ≈ 200dp mà R11 vừa
     *     giao trọn phần còn lại của thanh cho hàng chip.
     *  2. **Chữ chuyển sang `contentDescription`, vẫn từ `R.string`** (VI+EN): bỏ chữ khỏi màn KHÔNG được phép là
     *     bỏ luôn cái tên — người dùng TalkBack và bài canh i18n đều còn phải đọc ra nó.
     *  3. **Đích chạm không co theo icon**: bỏ chữ thì bề NGANG tụt xuống cỡ hình (20dp) nếu chỉ dựa vào lề trong
     *     ⇒ phải khai cả `minimumWidth` lẫn `minimumHeight` bằng [KachiSpace.TOUCH].
     */
    @Test
    fun `ba pill thanh tren chi con icon, chu chuyen sang contentDescription`() {
        val fn = SourceRoots.body(strip, "private fun pill(")
        assertFalse(
            Regex("""\btext\s*=""").containsMatchIn(fn),
            "pill không còn vẽ CHỮ (R12 a) — chỗ đó là chỗ của hàng chip",
        )
        assertTrue(fn.contains("contentDescription = activity.getString("), "tên phải còn, dưới dạng mô tả nội dung")
        assertTrue(fn.contains("KachiTheme.iconRes("), "hình lấy qua bảng tra icon dùng chung, không R.drawable thô")
        assertTrue(
            fn.contains("minimumWidth = dp(Sp.TOUCH)") && fn.contains("minimumHeight = dp(Sp.TOUCH)"),
            "đích chạm phải ≥ Sp.TOUCH theo CẢ HAI chiều — icon-only làm bề ngang tụt xuống cỡ hình",
        )
        // Và hai lời gọi vẫn truyền đúng hai khoá chuỗi cũ (không đẻ thêm khoá `*_desc` song song).
        val build = SourceRoots.body(strip, "private fun build()")
        assertEquals(
            listOf("ic-mic", "ic-apps", "ic-settings"),
            Regex("pill\\(\"([a-z-]+)\"").findAll(build).map { it.groupValues[1] }.toList(),
            "đúng ba pill, mỗi cái một hình của bộ icon v2 (`ic_mic` vẽ mới theo chuẩn — KHÔNG dùng `ic_mic_g` " +
                "của màn ClusterNav cũ, nó mang màu riêng và tỉ lệ khác)",
        )
    }

    /**
     * Pill "Thanh" và đường xoay vòng của nó phải **hết hẳn**, không để mã chết.
     *
     * Dự án đã xoá mã chết nhiều lần (`LauncherRequirements.notice`, `photoPaths`): một intent còn nằm đó là một lời
     * mời nối lại, và bảng cổng ở trên sẽ không bắt được nếu ai nối nó từ chỗ khác (vd bảng vẽ bố cục).
     */
    @Test
    fun `duong xoay vong vien thanh nut da xoa han khong con ma chet`() {
        assertFalse(strip.contains("onCycleDock"), "cổng xoay vòng ở thanh trên phải bị gỡ, không để treo")
        assertFalse(activity.contains("cycleDockEdge"), "chỗ nối ở màn chính phải bị gỡ")
        assertFalse(
            vm.contains("fun cycleDockEdge("),
            "intent xoay vòng phải bị XOÁ: sau khi gỡ pill nó không còn chỗ gọi nào ⇒ mã chết",
        )
        val offenders = appSourcesContaining("cycleDockEdge")
        assertEquals(
            emptyList<String>(), offenders,
            "còn tham chiếu tới đường xoay vòng đã xoá: $offenders",
        )
        // Đường THAY THẾ phải còn và phải là đặt-thẳng: bỏ pill mà không có đường khác là mất tính năng.
        assertTrue(vm.contains("fun setDockEdge("), "phải còn đường đặt THẲNG một viền")
        assertTrue(
            SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SettingsSectionsBars.kt")
                .contains("deps.onDockEdge("),
            "và Cài đặt → Thanh trạng thái & thanh nút phải bày nó ra (T4 · R-UI a tách khỏi Màn hình chính; " +
                "không bày ra thì bỏ pill là mất chức năng)",
        )
    }

    /** Thanh trên là VIEW THUẦN: nó không được tự biết chỗ lưu, cũng không được gọi intent. */
    @Test
    fun `thanh tren khong tu cham noi luu cung khong goi intent`() {
        listOf("WorkspacePrefs", "getSharedPreferences", "workspaceRepository", "Prefs.set", "viewModel.").forEach {
            assertFalse(
                strip.contains(it),
                "KachiTopStrip chạm '$it' — thanh trên là view thuần, mọi thay đổi đi qua callback → intent " +
                    "ViewModel; tự ghi ở đây là mở lại đúng cửa mà pill 'Thanh' vừa bị bỏ vì nó",
            )
        }
    }

    /** Tên tệp Kotlin của `:app` mà **MÃ** (đã bỏ chú thích) chứa [token]. */
    private fun appSourcesContaining(token: String): List<String> {
        val root = SourceRoots.moduleSourceRoots().first { it.toString().contains("app") }
        return Files.walk(root).use { p ->
            p.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .filter { f ->
                    f.toFile().readText()
                        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
                        .lines().joinToString("\n") { line -> line.substringBefore("//") }
                        .contains(token)
                }
                .map { it.fileName.toString() }.sorted().toList()
        }
    }
}
