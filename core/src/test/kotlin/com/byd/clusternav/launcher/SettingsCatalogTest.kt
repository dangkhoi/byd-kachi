package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * S1 · T1 — danh mục cài đặt (phần **thuần `:core`**).
 *
 * ## Bài nào là bài THẬT
 * Bài ở đây khoá **ý định** (7 nhóm, thứ tự, nhãn không rỗng, phép [SettingsCatalog.orphans] trên dữ liệu tự dựng) —
 * tức là chúng so danh mục với **chính nó**. Hai bài đáng giá nhất (đối chiếu danh mục với **nơi lưu THẬT**) nằm ở
 * `:app` [SettingsCoverageContractTest], vì chúng quét nguồn của `:app` và [ĐO] `:core:test` báo **UP-TO-DATE** khi
 * chỉ nguồn `:app` đổi — chi tiết ở KDoc lớp đó. Nếu bộ test này chỉ có loại "so với chính nó" thì nó là bộ test
 * **tự khen**; phép kiểm chống rữa của R2 sống ở `:app`.
 *
 * ⚠ Nhiều tính chất ở đây còn được `SettingsCatalog.init` chốt lúc nạp lớp, nên phá chúng sẽ làm **cả tệp** nổ
 * `ExceptionInInitializerError` thay vì đỏ một bài ([ĐO] thử phá "khoá hai chủ" ⇒ 12/12 bài đỏ). Vẫn viết bài canh vì
 * `init` có thể bị nới trong tương lai, và khi đó đây là lưới thứ hai.
 */
class SettingsCatalogTest {

    // Đúng bộ khoá mà spec §4.1 nói phải có chủ. Viết tay ở đây để bài test còn nói được điều gì đó độc lập với
    // bộ quét — bài quét mã nguồn phía dưới mới là bài chống rữa.
    private val mustBeOwned = listOf(
        "preset", "grid_layout", "wallpaper_prefs", "top_strip", "dock_enabled", "dock_edge",
        "unit_prefs", "profiles", "active_profile", "theme_mode", "launcher_autostart",
        "recirc_on_start_enabled",
    )

    @Test
    fun `dung bay nhom theo dung thu tu spec`() {
        assertEquals(7, SettingsCatalog.GROUPS.size, "spec §4.1 chốt đúng 7 nhóm")
        assertEquals(
            listOf("home", "display", "profiles", "car", "system", "clusternav", "about"),
            SettingsCatalog.GROUPS.map { it.id },
            "thứ tự rail là thứ tự người dùng đọc — không được đổi khi thêm nhóm",
        )
        assertEquals("Màn hình chính", SettingsGroup.HOME.label)
        assertEquals("Dẫn đường · Cụm · Phím", SettingsGroup.CLUSTERNAV.label)
    }

    @Test
    fun `moi nhom co it nhat mot muc`() {
        val empty = SettingsCatalog.GROUPS.filter { SettingsCatalog.entriesOf(it).isEmpty() }
        assertTrue(empty.isEmpty(), "nhóm rỗng thì rail mở ra một trang trắng — nhóm rỗng: ${empty.map { it.id }}")
    }

    @Test
    fun `khong khoa nao thuoc hai nhom`() {
        assertTrue(
            SettingsCatalog.duplicatedKeys().isEmpty(),
            "hai mục cùng nhận một khoá = hai nơi sửa một giá trị: ${SettingsCatalog.duplicatedKeys()}",
        )
    }

    @Test
    fun `groupOf tra dung nhom cho tung khoa`() {
        assertEquals(SettingsGroup.HOME, SettingsCatalog.groupOf("preset"))
        assertEquals(SettingsGroup.HOME, SettingsCatalog.groupOf("top_strip"))
        assertEquals(SettingsGroup.HOME, SettingsCatalog.groupOf("wallpaper_prefs"))
        assertEquals(SettingsGroup.DISPLAY, SettingsCatalog.groupOf("unit_prefs"))
        assertEquals(SettingsGroup.DISPLAY, SettingsCatalog.groupOf("theme_mode"))
        assertEquals(SettingsGroup.PROFILES, SettingsCatalog.groupOf("active_profile"))
        assertEquals(SettingsGroup.CAR, SettingsCatalog.groupOf("recirc_on_start_enabled"))
        assertEquals(SettingsGroup.SYSTEM, SettingsCatalog.groupOf("launcher_autostart"))
        assertNull(SettingsCatalog.groupOf("khong_ton_tai"), "khoá lạ phải trả null, không được đoán bừa một nhóm")
    }

    @Test
    fun `moi khoa spec doi phai co dung mot chu`() {
        mustBeOwned.forEach { key ->
            assertNotNull(SettingsCatalog.groupOf(key), "khoá '$key' chưa thuộc nhóm nào")
        }
        assertTrue(
            SettingsCatalog.orphans(mustBeOwned.toSet()).isEmpty(),
            "không khoá nào trong danh sách spec được phép mồ côi",
        )
    }

    @Test
    fun `orphans phat hien dung khoa la`() {
        val lạ = setOf("khoa_moi_ai_do_them", "wiper_speed_pref")
        assertEquals(
            lạ,
            SettingsCatalog.orphans(lạ + mustBeOwned.toSet()),
            "phải nêu ĐÍCH DANH khoá chưa gom, và chỉ những khoá đó",
        )
    }

    @Test
    fun `orphans khong bao khoa nam trong NOT_SETTINGS`() {
        assertTrue(
            SettingsCatalog.orphans(SettingsCatalog.NOT_SETTINGS.keys).isEmpty(),
            "khoá đã có lý do loại là 'cố ý không gom', không phải 'quên gom'",
        )
        assertNull(SettingsCatalog.groupOf("recent_apps"), "khoá bị loại không được đồng thời có chủ")
        assertTrue(
            SettingsCatalog.NOT_SETTINGS.getValue("recent_apps").isNotBlank(),
            "phải kèm lý do, không thì đây là chỗ làm im bài test",
        )
    }

    @Test
    fun `orphans tha ho khoa noi dung o ke ca dang chua noi suy`() {
        // Khoá ô là khoá dựng động trong vòng lặp (`key("slot_$it")`), nên bộ quét mã nguồn có thể trích ra dạng
        // CHƯA nội suy. Tha theo tiền tố để bản sau nới số ô không làm đỏ oan.
        assertTrue(SettingsCatalog.orphans(setOf("slot_0", "slot_5")).isEmpty(), "khoá ô thật phải được tha")
        assertTrue(SettingsCatalog.orphans(setOf("slot_\$it")).isEmpty(), "dạng chưa nội suy cũng phải được tha")
        assertTrue(
            SettingsCatalog.orphans(setOf("slots_all")).isNotEmpty(),
            "tha theo tiền tố KHÔNG được nới rộng thành tha mọi khoá bắt đầu bằng 'slot'",
        )
    }

    /**
     * ⚠ Hai bài **chống rữa** (đối chiếu danh mục với nơi lưu THẬT) nằm ở `:app` [SettingsCoverageContractTest] —
     * xem KDoc của lớp này để biết vì sao.
     */
    @Test
    fun `nhan va cau phu khong rong`() {
        SettingsCatalog.GROUPS.forEach {
            assertTrue(it.label.isNotBlank(), "nhóm ${it.id} thiếu nhãn")
            assertTrue(it.sub.isNotBlank(), "nhóm ${it.id} thiếu câu phụ — rail phải tự giải thích được")
        }
        SettingsCatalog.ENTRIES.forEach {
            assertTrue(it.label.isNotBlank(), "mục ${it.id} thiếu nhãn")
        }
    }

    @Test
    fun `ma nhom va ma muc khong trung`() {
        val groupIds = SettingsCatalog.GROUPS.map { it.id }
        assertEquals(groupIds.size, groupIds.distinct().size, "mã nhóm trùng: $groupIds")
        val entryIds = SettingsCatalog.ENTRIES.map { it.id }
        assertEquals(entryIds.size, entryIds.distinct().size, "mã mục trùng")
    }

    @Test
    fun `muc khong luu ben thi khai prefKey null`() {
        val noKey = SettingsCatalog.ENTRIES.filter { it.prefKey == null }.map { it.id }
        assertEquals(
            listOf(
                // P7/P6: nút "Lưu cảnh hiện tại…" là một VIỆC LÀM; cảnh lưu ra thì nằm ở khoá của `home_scenes`
                // (một khoá, một chủ — cùng lối với cặp `home_grid_editor` / `home_grid`).
                "home_scene_save",
                "home_grid_editor", "system_permissions", "clusternav_open", "about_version",
            ),
            noKey,
            "năm mục là việc-làm hoặc thông tin, không phải giá trị lưu bền",
        )
        // Rỗng KHÁC null: chuỗi rỗng sẽ lọt vào groupOf("") và biến một khoá không tồn tại thành có chủ.
        assertTrue(SettingsCatalog.ENTRIES.none { it.prefKey == "" }, "dùng null, không dùng chuỗi rỗng")
        assertNull(SettingsCatalog.groupOf(""), "chuỗi rỗng không phải khoá")
    }

    /**
     * Thứ tự trong nhóm "Màn hình chính": **cả bộ trước, rồi khung ra nội dung**.
     *
     * ⚠ P7/P6 **mở rộng** luật cũ, không bỏ nó. Luật cũ là *"bố cục trước, rồi mới tới thứ nằm trong nó"* và nó vẫn
     * được chốt nguyên vẹn ở phép so thứ hai dưới đây. Cảnh chen lên đầu vì nó không phải một *phần* của bố cục mà là
     * **cả bộ** (bố cục + nội dung ô + thanh nút), và vì gọi lại một cảnh là việc làm **thường xuyên nhất** ở trang
     * này — để nó sau mục thanh nút thì phải cuộn qua **lưới 187 ô** mới tới, tức "gọi lại nhanh" mất nghĩa. Cùng lập
     * luận đã đặt "viền thanh nút" trước lưới 187 ô: thứ tự danh mục là thứ tự **dùng được**, không phải thứ tự nghe
     * hợp lý khi đọc danh sách.
     */
    @Test
    fun `entriesOf phu het ENTRIES va giu thu tu khai`() {
        val byGroup = SettingsCatalog.GROUPS.flatMap { SettingsCatalog.entriesOf(it) }
        assertEquals(
            SettingsCatalog.ENTRIES.size, byGroup.size,
            "gộp mục của 7 nhóm phải ra đủ danh mục — thiếu nghĩa là có mục không nhóm nào bày ra",
        )
        val home = SettingsCatalog.entriesOf(SettingsGroup.HOME).map { it.id }
        assertEquals(
            listOf("home_scenes", "home_scene_boot", "home_scene_save"),
            home.take(3),
            "cả bộ (cảnh) đứng trước các phần rời — nó là đường tắt của mọi thứ bên dưới",
        )
        assertEquals(
            listOf("home_preset", "home_grid", "home_grid_editor"),
            home.drop(3).take(3),
            "trong nhóm đi từ khung ra nội dung: bố cục trước, rồi mới tới thứ nằm trong nó",
        )
    }
}
