package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ [SOÁT P0-1 + P2-2] KHOÁ THEO HỒ SƠ TÀI XẾ — dây nối phía Android ═══════════════════════════════════════════
 *
 * Tách khỏi `AppWidgetWiringContractTest` khi tệp đó vượt trần 500 dòng: đây là **một chủ đề khác** (`WorkspacePrefs`
 * khoá theo hồ sơ) chỉ tình cờ gặp widget bên thứ ba ở một điểm.
 *
 * ## Vì sao bài này nằm ở `:app`
 * Nó quét **mã nguồn `:app`**. Dự án đã [ĐO] hai lần rằng bài quét mã của module X mà đặt ở module Y thì gradle
 * **không coi tệp của X là đầu vào** ⇒ task báo `UP-TO-DATE` và bài **không bao giờ chạy lại** (dấu xanh giả: S1 với
 * hai bài chống-rữa, rồi 11 ca của bộ niêm phong T11).
 *
 * ## Hai lỗi nó khoá, cả hai đều MẤT DỮ LIỆU và cả hai đều IM LẶNG
 *  - **P0-1**: `HomeUiState` chỉ mang dữ liệu của hồ sơ ĐANG DÙNG, mà id widget là của **HOST** (mọi hồ sơ) ⇒ lượt thu
 *    hồi id đi xoá **vĩnh viễn** widget của hồ sơ khác. [ĐO] `emulator-5554`: đổi hồ sơ ⇒ id 655 mất khỏi host; quay
 *    lại ⇒ ô ra thẻ *"app đã bị gỡ"* trong khi `pm list packages` cho thấy app **vẫn còn cài**.
 *  - **P2-2**: `deleteProfile` chỉ sửa hai khoá danh sách ⇒ mọi `<tên>__*` còn nguyên trên đĩa; `addProfile` không kiểm
 *    khoá cũ ⇒ đặt lại đúng cái tên vừa xoá nạp nguyên cấu hình của hồ sơ đã xoá.
 *
 * ⚠ Phép TÍNH thì ở `:core` (`AppWidgetIdsTest`). Bài này chỉ hỏi một câu mà bài đó **không thể** hỏi: *dữ liệu có
 * thật sự tới được phép tính chưa*. Đó đúng là nửa đã bị bỏ sót hai lần.
 */
class ProfileKeysWiringContractTest {

    private fun prefs() = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/WorkspacePrefs.kt")
    private fun repo() = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/PrefsWorkspaceRepository.kt")

    /** MÃ đã bỏ chú thích — bắt buộc cho mọi phép "chuỗi X không được xuất hiện" (KDoc đầy đủ ở bài widget). */
    private fun code(src: String): String = src
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""//[^\n]*"""), " ")


    /**
     * ⚠⚠⚠ **P0 — phép tính đúng mà DỮ LIỆU KHÔNG TỚI thì lỗi vẫn còn nguyên.**
     *
     * `AppWidgetIds.used` nay cộng thêm [HomeUiState.widgetIdsOtherProfiles], nhưng trường đó **mặc định rỗng**. Nếu
     * `load()` không điền thì mọi bài `:core` vẫn xanh (chúng tự truyền dữ liệu vào) mà trên máy thì lỗi y như cũ —
     * đúng hình dạng đã để lọt lỗi này: bài canh hỏi *"có gọi đúng hàm không"* thay vì *"hàm có thấy đủ dữ liệu chưa"*.
     *
     * Bài này canh **đường dữ liệu**, không canh phép tính.
     */
    @Test
    fun `load dien id widget cua cac ho so KHAC vao state`() {
        val body = SourceRoots.body(code(repo()), "override fun load(): HomeUiState")
        assertTrue(
            "widgetIdsOtherProfiles = prefs.widgetIdsOtherProfiles()" in body,
            "load() PHẢI nạp id widget của các hồ sơ khác — thiếu nó thì đổi hồ sơ vẫn xoá vĩnh viễn widget của hồ " +
                "sơ kia ([ĐO] emulator: id 654 mất khỏi host, quay lại ra thẻ 'app đã bị gỡ' dù app còn cài)",
        )
    }

    /** Và phép dò đó phải quét **mọi hồ sơ**, BỎ hồ sơ đang dùng (nếu gồm cả nó thì id vừa bỏ khỏi ô sẽ rò mãi). */
    @Test
    fun `phep do quet moi ho so va bo ho so dang dung`() {
        val body = SourceRoots.body(code(prefs()), "fun widgetIdsOtherProfiles(): Set<Int>")
        assertTrue("profiles()" in body, "phải quét danh sách hồ sơ, không chỉ hồ sơ đang dùng")
        assertTrue(
            Regex("""filter\s*\{\s*it\s*!=\s*active\s*\}""").containsMatchIn(body),
            "phải BỎ hồ sơ đang dùng: nó đã nằm trong state (bản mới hơn đĩa), và ảnh chụp lúc load sẽ bảo vệ " +
                "vĩnh viễn một id mà người dùng vừa bỏ khỏi ô ⇒ id rác sống mãi",
        )
        assertTrue(
            "AppWidgetIds.idsInStored(" in body,
            "phép GIẢI MÃ phải nằm ở `:core` (kiểm được off-car); tệp này cần Context nên không bài nào chạm tới",
        )
    }

    /**
     * Vùng khai [WorkspacePrefs.PROFILE_SUFFIXES] — **mốc tự kết bằng `{`** nên `SourceRoots.body` cắt theo đếm
     * ngoặc, xác định và nổ nếu mốc mất. Bản đầu của bài này neo vào `val PROFILE_SUFFIXES: List<String>` (không có
     * `{`) và [ĐO] nó cắt ra đúng một lambda `{ "slot_$it" }` ⇒ hai bài đỏ oan. Cùng họ với 44 phép cắt vùng mà lượt
     * soát 2026-09-11 đã phải đi vá: mốc sai thì bài nói về một vùng khác chứ không nói nó sai.
     */
    private fun suffixList() =
        SourceRoots.body(code(prefs()), "val PROFILE_SUFFIXES: List<String> = buildList {")

    /**
     * ⚠⚠ [SOÁT P2-2] Xoá hồ sơ phải xoá **mọi khoá của nó**, và danh sách hậu tố khai ĐÚNG MỘT chỗ.
     *
     * Bản cũ chỉ sửa `profiles` + `active_profile` ⇒ `<tên>__slot_*`, `__scenes`, `__dock_*`… nằm nguyên trên đĩa;
     * [WorkspacePrefs.addProfile] không kiểm khoá cũ nên đặt lại đúng cái tên vừa xoá sẽ **nạp nguyên cấu hình cũ**,
     * kể cả `aw:<id>` của những id đã bị thu hồi ⇒ ô ra thẻ *"app đã bị gỡ"*. Đây cũng là điều làm id widget của hồ
     * sơ bị xoá được nhả đúng lúc (dò đọc đĩa ⇒ hết kể id đó ⇒ lượt thu hồi kế tiếp thấy nó là rác).
     */
    @Test
    fun `xoa ho so xoa moi khoa cua no, danh sach hau to o dung mot cho`() {
        val src = code(prefs())
        val del = SourceRoots.body(src, "fun deleteProfile(name: String)")
        assertTrue(
            "profileKeys(name)" in del && ".remove(" in del,
            "deleteProfile phải xoá mọi khoá của hồ sơ đó (không thì prefs phình vô hạn và tên cũ nạp lại rác)",
        )
        assertEquals(
            1, Regex("""val PROFILE_SUFFIXES""").findAll(src).count(),
            "danh sách hậu tố khai đúng MỘT lần — viết tay hai lần thì bản sau sẽ chỉ cập nhật một nơi",
        )
        // Trần ô phải LẤY TỪ hằng, không chép tay: trần đã đổi một lần (4 → 6) và chỗ chép tay sẽ bỏ sót ô cuối.
        assertTrue("WorkspaceState.SLOT_CAP" in suffixList(), "`slot_*` phải sinh theo WorkspaceState.SLOT_CAP")
    }

    /**
     * ⚠ Và danh sách đó phải **ĐẦY ĐỦ**: mọi lời gọi `key("…")` trong tệp phải có mặt trong `PROFILE_SUFFIXES`.
     *
     * Không có phép kiểm này thì bản sau thêm một khoá theo-hồ-sơ (vd `key("wallpaper")`) và lượt xoá hồ sơ bỏ sót nó
     * — im lặng, chỉ lộ ra khi ai đó tạo lại hồ sơ cùng tên và thấy dữ liệu lạ.
     */
    @Test
    fun `moi khoa theo ho so deu co trong danh sach hau to`() {
        val src = code(prefs())
        val suffixes = suffixList()
        val used = Regex("""key\((?:"([^"]+)"|(K_[A-Z_]+))\)""").findAll(src)
            .map { m -> m.groupValues[1].ifEmpty { m.groupValues[2] } }
            .map { it.substringBefore('$') }   // `slot_$it` / `slot_$i` → `slot_`
            .distinct().toList()
        val missing = used.filterNot { s ->
            if (s == "slot_") "slot_" in suffixes else suffixes.contains("\"$s\"") || suffixes.contains(s)
        }
        assertEquals(
            emptyList<String>(), missing,
            "khoá theo hồ sơ KHÔNG có trong PROFILE_SUFFIXES ⇒ xoá hồ sơ sẽ để nó mồ côi: $missing",
        )
        assertTrue(used.size >= 8, "phép quét phải thấy đủ các khoá (đang thấy ${used.size}) — dưới mức này là nó hỏng")
    }

    /**
     * ⚠ Chỉ khoá THEO HỒ SƠ được vào danh sách. Khoá chung cả máy mà lọt vào thì xoá một hồ sơ sẽ mất lựa chọn đơn
     * vị / chủ đề / hình nền của **cả xe** — lỗi tệ hơn lỗi đang vá.
     */
    @Test
    fun `khoa chung ca may KHONG duoc nam trong danh sach hau to`() {
        val suffixes = suffixList()
        listOf("theme_mode", "unit_prefs", "wallpaper_prefs", "recent_apps", "launcher_autostart", "profiles", "active_profile")
            .forEach {
                assertTrue(
                    "\"$it\"" !in suffixes,
                    "`$it` là khoá CHUNG cả máy — xoá một hồ sơ không được làm mất nó",
                )
            }
    }

    /**
     * ⚠ Và **hồ sơ MỚI phải bắt đầu trống** — chốt cho máy đã chạy bản cũ.
     *
     * `deleteProfile` nay dọn sạch, nhưng máy đang chạy trên xe thì còn nguyên khoá của mọi hồ sơ từng bị xoá bằng bản
     * cũ. Đặt lại đúng cái tên đó mà không dọn thì hồ sơ "mới" nạp cấu hình của một hồ sơ người dùng tưởng đã xoá.
     */
    @Test
    fun `ho so moi bat dau trong`() {
        val body = SourceRoots.body(code(prefs()), "fun addProfile(name: String)")
        assertTrue(
            "profileKeys(clean)" in body && ".remove(" in body,
            "addProfile phải dọn khoá cũ của cái tên đó — máy đã chạy bản cũ còn khoá mồ côi trên đĩa",
        )
        assertTrue(
            Regex("""if\s*\(\s*isNew\s*\)\s*profileKeys""").containsMatchIn(body),
            "và CHỈ dọn khi thật sự mới: tên đã có thì đây là lượt 'chuyển sang', dọn là MẤT cấu hình của hồ sơ đó",
        )
    }
}
