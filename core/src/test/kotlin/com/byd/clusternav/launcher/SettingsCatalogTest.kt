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
 * Bài ở đây khoá **ý định** (10 nhóm của IA v2, thứ tự, nhãn không rỗng, phép [SettingsCatalog.orphans] trên dữ liệu tự dựng) —
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
        "preset", "grid_layout", "wallpaper_prefs", "top_strip", "dock_enabled", "dock_edge", "dock_visible",
        "unit_prefs", "profiles", "active_profile", "theme_mode", "launcher_autostart",
        "recirc_on_start_enabled",
    )

    // IA v2 §4.3 — khoá của ClusterNav mà Settings mới phải nhận. Cũng viết tay, cùng lý do như trên; bài đối chiếu
    // với MÃ NGUỒN THẬT (`"<khoá>"` có mặt trong Prefs.kt/SimpleCastRuntime.kt/…) nằm ở `:app`
    // `ClusterNavKeysContractTest` — nó phải ở đó vì `:core:test` báo UP-TO-DATE khi chỉ nguồn `:app` đổi.
    private val clusterNavMustBeOwned = listOf(
        "enabled", "nav_cluster_screen_mode", "marquee",
        "badge_enabled", "show_upcoming_badge", "show_alert_chip", "badge_size_dp", "badge_center_x",
        "vm_bubble_enabled", "vm_bubble_x",
        "cast_enabled", "split_ratio_left_pct", "autostart_enabled", "autostart_package",
        "autostart_split_enabled", "autostart_left_package", "autostart_right_package",
        "voicekey_enabled", "voicekey_bindings", "voicekey_custom_buttons", "voicekey_learn",
        "seat_comfort_enabled", "seat_comfort_mode", "seat_level_0", "pm25_filter_enabled",
        "headless_autostart",
    )

    @Test
    fun `dung muoi nhom theo dung thu tu spec`() {
        assertEquals(10, SettingsCatalog.GROUPS.size, "IA v2 §4.1 chốt đúng 10 nhóm")
        assertEquals(
            listOf("home", "bars", "display", "profiles", "nav", "cast", "keys", "car", "system", "about"),
            SettingsCatalog.GROUPS.map { it.id },
            "thứ tự rail là thứ tự TẦN SUẤT DÙNG (§4.1) — không được đổi khi thêm nhóm",
        )
        assertEquals("Màn hình chính", SettingsGroup.HOME.label)
        // Nhóm `clusternav` của IA v1 BỊ BỎ: ba nhóm thật (nav/cast/keys) thay cho một nút "mở màn kia", và dòng mở
        // màn cũ hạ xuống thành một mục của nhóm Hệ thống.
        assertTrue(
            SettingsCatalog.GROUPS.none { it.id == "clusternav" },
            "nhóm 'clusternav' phải biến mất — giữ nó lại nghĩa là vẫn còn hai màn cấu hình",
        )
        // S3 (2026-09-13): màn cũ bị GỠ HẲN ⇒ mục mở nó cũng biến mất. Bài canh chiều "không mọc lại": một mục
        // trỏ tới một màn không còn tồn tại là một nút bấm-không-làm-gì trên màn xe.
        assertTrue(
            SettingsCatalog.ENTRIES.none { it.id == "system_advanced_screen" || it.id == "clusternav_open" },
            "không còn mục nào mở màn ClusterNav cũ — màn đó đã gỡ (kachi-remove-legacy-screen.html R1)",
        )
    }

    /**
     * Câu phụ rail ≤ [SettingsCatalog.GROUP_SUB_MAX] ký tự — **ràng buộc HÌNH** (R-UI b).
     *
     * [ĐO ảnh 2026-09-12] 2/7 câu của IA v1 bị cắt cụt bằng "…" trên máy thật, và phần bị cắt chính là phần nói
     * *"nhóm này chứa gì"*. Đây là loại lỗi trình biên dịch không thấy và bài test cũ cũng không thấy — nó chỉ hiện
     * ra khi có người chụp màn hình. Ghim số để lần sau viết dài ra là đỏ ngay.
     */
    @Test
    fun `cau phu rail du ngan de khong bi cat`() {
        val tooLong = SettingsCatalog.GROUPS.flatMap { g ->
            listOf(g.id to g.sub, "${g.id}(en)" to g.subEn)
        }.filter { it.second.length > SettingsCatalog.GROUP_SUB_MAX }
        assertTrue(
            tooLong.isEmpty(),
            "câu phụ dài quá ${SettingsCatalog.GROUP_SUB_MAX} ký tự ⇒ rail cắt '…': " +
                tooLong.map { "${it.first}=${it.second.length}" },
        )
    }

    @Test
    fun `moi nhom co it nhat HAI muc`() {
        // IA v2 · R4 siết luật cũ (≥ 1) lên ≥ 2: [ĐO ảnh] nhóm 1 mục ("Tiện nghi xe", "Dẫn đường" của IA v1) mở ra
        // một trang gần như trắng — đi qua rail để thấy đúng một dòng thì cái rail đó đang nói dối về độ sâu.
        val thin = SettingsCatalog.GROUPS.filter { SettingsCatalog.entriesOf(it).size < 2 }
        assertTrue(thin.isEmpty(), "nhóm dưới 2 mục: ${thin.map { "${it.id}=${SettingsCatalog.entriesOf(it).size}" }}")
    }

    @Test
    fun `ENTRIES khai theo dung thu tu nhom`() {
        // Thứ tự khai của danh mục LÀ thứ tự hiện ra, và `entriesOf` lọc theo nhóm — nếu mục của một nhóm nằm rải
        // rác thì danh mục vẫn "đúng" với mọi phép kiểm khác, nhưng người đọc mã không còn thấy được một nhóm gồm
        // những gì. Đây là phép canh cho chính khả năng đọc đó.
        val order = SettingsCatalog.ENTRIES.map { it.group }.distinct()
        assertEquals(SettingsCatalog.GROUPS, order, "mục của một nhóm phải khai liền nhau, theo thứ tự nhóm")
    }

    @Test
    fun `moi khoa ClusterNav co dung mot chu`() {
        clusterNavMustBeOwned.forEach { key ->
            assertNotNull(SettingsCatalog.groupOf(key), "khoá ClusterNav '$key' chưa thuộc mục nào")
        }
        assertTrue(SettingsCatalog.duplicatedKeys().isEmpty(), "không khoá nào được hai mục cùng nhận")
    }

    @Test
    fun `bang khoa ClusterNav tu nhat quan`() {
        // Mỗi khoá trỏ vào một tệp prefs ĐÃ KHAI LÝ DO.
        SettingsCatalog.CLUSTERNAV_KEYS.forEach { (key, file) ->
            assertTrue(
                file in SettingsCatalog.CLUSTERNAV_PREFS_FILES,
                "khoá '$key' trỏ vào tệp prefs chưa khai: '$file'",
            )
        }
        // Khoá ĐI KÈM phải trỏ vào một mục có thật, và không được đồng thời là khoá chính.
        SettingsCatalog.CLUSTERNAV_COMPANION_KEYS.forEach { (key, ownerId) ->
            assertTrue(
                SettingsCatalog.ENTRIES.any { it.id == ownerId },
                "khoá đi kèm '$key' trỏ vào mục không tồn tại: '$ownerId'",
            )
            assertNull(SettingsCatalog.groupOf(key), "'$key' không thể vừa là khoá chính vừa là khoá đi kèm")
        }
        // Khoá ẩn phải có LÝ DO và không được có UI.
        assertTrue(SettingsCatalog.CLUSTERNAV_HIDDEN_KEYS.isNotEmpty(), "danh sách khoá ẩn rỗng = chưa ai trả lời")
        SettingsCatalog.CLUSTERNAV_HIDDEN_KEYS.forEach { (key, why) ->
            assertTrue(why.isNotBlank(), "khoá ẩn '$key' thiếu lý do")
            assertNull(SettingsCatalog.groupOf(key), "khoá ẩn '$key' lại có mục — hai câu trả lời trái nhau")
            assertTrue(key !in SettingsCatalog.CLUSTERNAV_KEYS, "khoá ẩn '$key' lại nằm trong bảng khoá có UI")
        }
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
        assertEquals(SettingsGroup.BARS, SettingsCatalog.groupOf("top_strip"), "chip + thanh nút tách sang nhóm Thanh")
        assertEquals(SettingsGroup.BARS, SettingsCatalog.groupOf("dock_edge"))
        assertEquals(SettingsGroup.NAV, SettingsCatalog.groupOf("badge_size_dp"))
        assertEquals(SettingsGroup.CAST, SettingsCatalog.groupOf("split_ratio_left_pct"))
        assertEquals(SettingsGroup.KEYS, SettingsCatalog.groupOf("voicekey_bindings"))
        assertEquals(SettingsGroup.CAR, SettingsCatalog.groupOf("seat_comfort_mode"))
        assertEquals(SettingsGroup.SYSTEM, SettingsCatalog.groupOf("headless_autostart"))
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
                // `home_grid_editor` = nút mở bảng vẽ (VIỆC LÀM); bố cục vẽ ra nằm ở khoá của `home_grid`
                // (một khoá, một chủ). ⚠ S4 · R1: "home_scene_save" đã XOÁ cùng khái niệm cảnh.
                "home_grid_editor",
                // S4 · R8 — "Thêm hồ sơ (bản sao của «X»)" là một VIỆC LÀM: nó tạo ra một bộ khoá MỚI mang tiền tố
                // tên hồ sơ, chứ bản thân nút không lưu giá trị nào.
                "profiles_add",
                // IA v2: mọi HÀNH ĐỘNG của màn ClusterNav (§4.3, cột "API ghi") — chúng bấm là chạy, không lưu gì.
                "nav_reconnect", "cast_actions", "cast_rescue", "keys_check", "car_pm25_clean",
                "system_permissions",
                // S5 — nút "Đặt Kachi làm màn hình chính" là VIỆC LÀM (gọi `cmd package set-home-activity`), không
                // lưu khoá nào; trạng thái đọc live từ PackageManager. Công tắc `system_keep_home_on_boot` thì CÓ
                // khoá (`keep_home_on_boot`) nên KHÔNG nằm ở đây.
                "system_default_home",
                "system_update", "system_nav_stop",
                "system_vietmap_data", "system_diagnostics",
                "about_version", "about_disclaimer",
            ),
            noKey,
            "mười lăm mục là việc-làm hoặc thông tin, không phải giá trị lưu bền",
        )
        // Rỗng KHÁC null: chuỗi rỗng sẽ lọt vào groupOf("") và biến một khoá không tồn tại thành có chủ.
        assertTrue(SettingsCatalog.ENTRIES.none { it.prefKey == "" }, "dùng null, không dùng chuỗi rỗng")
        assertNull(SettingsCatalog.groupOf(""), "chuỗi rỗng không phải khoá")
    }

    /**
     * Thứ tự trong nhóm "Màn hình chính": **cả bộ trước, rồi khung ra nội dung**.
     *
     * ⚠ S4 · R1 **trả luật này về bản gốc**: ba mục cảnh (`home_scenes` · `home_scene_boot` · `home_scene_save`) đã
     * xoá, nên "cả bộ" không còn đứng trước — *"cả bộ"* nay là chính **hồ sơ tài xế**, và nó có nhóm riêng
     * ([SettingsGroup.PROFILES]) chứ không chen vào trang Màn hình chính. Luật còn lại đúng như trước P7: **bố cục
     * trước, rồi mới tới thứ nằm trong nó**.
     */
    @Test
    fun `entriesOf phu het ENTRIES va giu thu tu khai`() {
        val byGroup = SettingsCatalog.GROUPS.flatMap { SettingsCatalog.entriesOf(it) }
        assertEquals(
            SettingsCatalog.ENTRIES.size, byGroup.size,
            "gộp mục của 10 nhóm phải ra đủ danh mục — thiếu nghĩa là có mục không nhóm nào bày ra",
        )
        val home = SettingsCatalog.entriesOf(SettingsGroup.HOME).map { it.id }
        assertEquals(
            listOf("home_preset", "home_grid", "home_grid_editor"),
            home.take(3),
            "trong nhóm đi từ khung ra nội dung: bố cục trước, rồi mới tới thứ nằm trong nó",
        )
        assertTrue(
            home.none { it.startsWith("home_scene") },
            "S4 · R1 — không còn mục 'cảnh' nào; cả bộ nay là HỒ SƠ và nó có nhóm riêng",
        )
    }
}
