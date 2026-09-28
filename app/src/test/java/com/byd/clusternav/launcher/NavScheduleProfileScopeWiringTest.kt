package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá ĐƯỜNG DÂY của việc chuyển *tự dẫn đường theo lịch* sang phạm vi HỒ SƠ (owner 2026-09-28:
 * *"lịch theo profile luôn nhé, ví dụ tôi chuyển sang profile Trip Đà Lạt, thì chắc chắn sẽ cần địa chỉ khác,
 * lịch trình khác với việc đi làm hàng ngày chứ?"*).
 *
 * Phần logic thuần đã có `ProfileScopeTest` canh. Bài này canh bốn thứ mà logic thuần không nhìn thấy, và cả bốn
 * đều là đường **mất dữ liệu của người dùng** nếu hỏng.
 *
 * ⚠ Mọi phép cắt vùng ở đây dùng [SourceRoots.body] (đếm ngoặc, NỔ nếu mốc không tồn tại) chứ không
 * `substringAfter`/`substringBefore` — xem KDoc của hàm đó: mốc sai thì phép cắt kia quét tràn tới hết tệp và bài
 * canh gần như không thể đỏ. Dự án đã [ĐO] ít nhất 5 bài mắc đúng lỗi ấy.
 */
class NavScheduleProfileScopeWiringTest {

    private val scope = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ProfileScope.kt")
    private val profilePrefs =
        SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WorkspacePrefsProfile.kt")
    private val repo = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/PrefsWorkspaceRepository.kt")

    private fun migration(): String =
        SourceRoots.body(profilePrefs, "internal fun WorkspacePrefs.migrateNavScheduleOnce()")

    @Test
    fun `hai khoa lich KHONG con nam o danh sach theo-xe`() {
        val deviceBlock = SourceRoots.body(scope, "val DEVICE_KEYS: Map<String, String> = buildMap {")
        listOf("nav_automation_rules", "nav_automation_fired").forEach {
            assertFalse(
                "\"$it\"" in deviceBlock,
                "$it còn trong danh sách theo-XE thì `scopeOf` trả DEVICE và ảnh chụp không mang nó theo hồ sơ",
            )
        }
    }

    @Test
    fun `so da-dan duoc chup tu dung tep ma dich vu doc`() {
        assertTrue(
            scope.contains("\"nav_automation_fired\" to \"clusternav_prefs\""),
            "khai sai tệp thì `scopeOf` vẫn trả PROFILE và mọi bài canh vẫn xanh, nhưng lượt áp ghi vào tệp khác " +
                "⇒ sổ đã-dẫn đọc RỖNG ở hồ sơ mới ⇒ dẫn lại lần hai trong cùng khung giờ, im lặng",
        )
    }

    @Test
    fun `di tru chay TRUOC luot doc dau tien`() {
        val init = SourceRoots.body(repo, "    init {")
        assertTrue(
            "migrateNavScheduleOnce()" in init,
            "phải chạy trong khối khởi tạo, TRƯỚC `load()` — vì chính `load` kéo theo lượt áp ảnh chụp, mà lượt " +
                "áp ấy là chỗ ảnh chụp cũ (chưa có hai khoá) làm các hồ sơ lệch nhau",
        )
    }

    @Test
    fun `di tru chi DIEN VAO CHO TRONG, khong ghi de`() {
        val fn = migration()
        assertTrue(
            "if (k !in shot)" in fn,
            "ghi đè ở đây là xoá lựa chọn mà người dùng vừa đặt sau khi nâng cấp — chỉ được điền vào khoá VẮNG",
        )
        assertTrue(
            fn.contains("live[it]") && fn.contains("profiles().forEach"),
            "phải RÓT giá trị đang sống xuống MỌI hồ sơ, không phải bốc của một hồ sơ lên",
        )
    }

    @Test
    fun `dau da-di-tru ghi CUNG mot luot voi du lieu`() {
        val fn = migration()
        assertTrue(
            fn.contains("e.putBoolean(K_MIGRATED_NAV_SCHEDULE, true).apply()"),
            "dấu phải nằm trên CHÍNH `Editor` đã ghi dữ liệu. Tách hai lượt thì một lần chết máy giữa chừng cho " +
                "lượt sau chạy lại trên dữ liệu đã chuyển — đúng bài học của `migrateScenesOnce`",
        )
        assertFalse(
            fn.contains("sp.edit().putBoolean(K_MIGRATED_NAV_SCHEDULE"),
            "một `edit()` thứ hai riêng cho cái dấu là đúng cái bẫy vừa nói",
        )
    }

    /**
     * ═══ KHOÁ LẠI LỖI CỦA LƯỢT SOÁT 2026-09-28 — [P1] sổ đã-dẫn TRÀN giữa hồ sơ ════════════════════════════════
     *
     * Bản đầu của phép rót lọc `null` ra (`.filterValues { it != null }`) cho "gọn". Nhưng [applyClusterNav] phân
     * biệt **hai** trạng thái: khoá mang `null` tường minh ⇒ `e.remove(k)` = *"chưa ai đặt"*; khoá **VẮNG khỏi ảnh**
     * ⇒ **không chạm tới** ⇒ **giữ nguyên giá trị của hồ sơ vừa rời**.
     *
     * Ca thật, im lặng: lúc di trú `nav_automation_fired` **thường chưa tồn tại** (chưa lịch nào bắn) ⇒ lọc đi là
     * nó vắng ở ảnh của **mọi** hồ sơ. Hồ sơ A bắn xong hôm nay; đổi sang B thì sổ đã-dẫn của A **vẫn sống**, mà
     * luật của B là **bản sao cùng `id`** (chính lượt rót này chép xuống) ⇒ B bị coi là **đã bắn** và **bỏ đúng một
     * lượt dẫn**. Không log, không lỗi, không ai thấy — chỉ là sáng hôm đó xe không tự dẫn.
     *
     * Đây cũng đúng hợp đồng đã ghi sẵn ở KDoc `snapshotClusterNav` và là lý do thẻ `n` của `PrefSnapshot` tồn tại.
     */
    @Test
    fun `rot ca khoa DANG VANG, duoi dang null tuong minh`() {
        val fn = migration()
        assertTrue(
            "val carry: Map<String, Any?> = keys.associateWith { live[it] }" in fn,
            "phép rót phải mang MỌI khoá của tính năng, kể cả khoá đang vắng (giá trị `null`) — xem KDoc bài này",
        )
        assertFalse(
            "filterValues" in fn,
            "lọc `null` ra là bỏ khoá ấy khỏi ảnh của mọi hồ sơ; `applyClusterNav` khi đó GIỮ giá trị của hồ sơ " +
                "vừa rời ⇒ sổ đã-dẫn tràn sang hồ sơ khác ⇒ bỏ một lượt dẫn, im lặng",
        )
        // Đầu bên kia của cùng hợp đồng: `null` phải dịch thành `remove`, không phải bị bỏ qua.
        val apply = SourceRoots.body(profilePrefs, "internal fun WorkspacePrefs.applyClusterNav(profile: String)")
        assertTrue(
            "null -> e.remove(k)" in apply,
            "không có nhánh này thì `null` tường minh rơi vào `else -> Unit` và phép rót ở trên thành vô nghĩa",
        )
    }

    @Test
    fun `chay DUNG MOT LAN`() {
        assertTrue(
            migration().contains("if (sp.getBoolean(K_MIGRATED_NAV_SCHEDULE, false)) return"),
            "không có cổng này thì mỗi lần mở app lại quét lại toàn bộ hồ sơ",
        )
    }
}
