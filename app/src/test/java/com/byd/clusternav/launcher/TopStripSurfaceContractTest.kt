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
 *  3. **khoá lưu bền** — bảng cổng→khoá phải khớp danh sách cho phép ở `:core`.
 */
class TopStripSurfaceContractTest {

    private val strip by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiTopStrip.kt") }
    private val activity by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val vm by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/HomeViewModel.kt") }

    /**
     * Cổng vào của thanh trên → khoá lưu bền mà cổng đó được phép chạm (`null` = **không lưu gì**, chỉ mở một màn).
     *
     * Bảng này là phần "ý định" của phép kiểm, nên nó phải ở đây chứ không suy ra từ mã: mã chỉ nói *cổng nào đang
     * tồn tại*, còn *cổng đó được phép làm gì* là quyết định của §4.5.
     */
    private val gates: Map<String, String?> = mapOf(
        "onSelectPreset" to "preset",            // 5 nút bố cục — cách đổi bố cục hằng ngày (§4.5 giữ lại)
        "onProfileTap" to "active_profile",      // avatar — "đang ở hồ sơ nào" phải thấy liên tục
        "onOpenSettings" to null,                // mở màn Cài đặt: không tự đổi gì
        "onOpenAppList" to null,                 // U3 mở app toàn màn: không phải cấu hình
    )

    @Test
    fun `bang cong cua thanh tren dung nhu §4_5 cho phep`() {
        val ctor = ctorParams(strip, "class KachiTopStrip(")
        val found = Regex("""\bprivate val (on[A-Z]\w*)\s*:""").findAll(ctor).map { it.groupValues[1] }.toSortedSet()
        assertEquals(
            gates.keys.toSortedSet(), found,
            "cổng vào của thanh trên đã đổi. Mỗi bề mặt bấm được cần một callback, nên một cổng MỚI nghĩa là một bề " +
                "mặt mới trên thanh trên. §4.5 chỉ cho phép ${gates.keys}: bố cục (đổi hằng ngày) và hồ sơ (phải thấy " +
                "liên tục). Cấu hình khác thuộc màn Cài đặt — xem KDoc SettingsCatalog.TOP_STRIP_ALLOWED_KEYS",
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

    @Test
    fun `thanh tren chi ghi ben hai khoa preset va active_profile`() {
        val persistent = gates.values.filterNotNull().toSortedSet()
        assertEquals(
            SettingsCatalog.TOP_STRIP_ALLOWED_KEYS.toSortedSet(), persistent,
            "bảng cổng→khoá của bài này lệch với danh sách cho phép ở `:core`",
        )
        // Và danh sách đó phải đúng hai khoá — nới nó là một quyết định, phải viết lý do ở `:core`, không lặng lẽ.
        assertEquals(
            sortedSetOf("active_profile", "preset"), SettingsCatalog.TOP_STRIP_ALLOWED_KEYS.toSortedSet(),
            "nới danh sách khoá của thanh trên = đưa cấu hình ra khỏi màn Cài đặt (trái §4.5 + chỉ thị owner). " +
                "Nếu owner đổi ý thì sửa KDoc TOP_STRIP_ALLOWED_KEYS kèm lý do rồi sửa bài này",
        )
    }

    /**
     * Danh sách pill — lớp chặn cho ca *"pill mới dùng LẠI callback cũ"*, mà bài bảng-cổng không thể thấy.
     */
    @Test
    fun `thanh tren chi con hai pill va khong pill nao doi cau hinh tai cho`() {
        val fn = SourceRoots.body(strip, "private fun build()")
        // U5·T3 — nhãn pill nay đến từ tài nguyên (`getString`), nên phép đếm đọc MÃ KHOÁ thay vì đọc chữ. Tính chất
        // được canh KHÔNG đổi: đúng hai pill, đúng thứ tự đó, và không pill nào đổi cấu hình tại chỗ.
        val pills = Regex("""pill\([^)]*?R\.string\.(\w+)""").findAll(fn).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf("kachi_pill_apps", "kachi_pill_settings"), pills,
            "thanh trên chỉ được có hai pill: 'Ứng dụng' (mở app) và 'Cài đặt' (MỘT cửa vào cấu hình). Thêm pill nào " +
                "cũng là thêm một bề mặt lỉ tỉ — pill 'Thanh' vừa bị bỏ đúng vì lý do đó",
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
            SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SettingsSectionsHome.kt")
                .contains("deps.onDockEdge("),
            "và Cài đặt → Màn hình chính phải bày nó ra (không thì bỏ pill là mất chức năng)",
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
