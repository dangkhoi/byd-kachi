package com.byd.clusternav.modules.navaccess

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá lại bài học hiện trường 2026-09-28: phím vô-lăng chết vì dịch vụ Hỗ trợ KẸT trong khối
 * `Binding services` của `dumpsys accessibility`, và **không lệnh ghi settings nào gỡ được** —
 * [ĐO AOSP android-10.0.0_r47 `AccessibilityManagerService.java:1630-1631`] `updateServicesLocked` bỏ qua
 * (`continue`) mọi component đang nằm trong `mBindingServices`, mà dòng đó đứng TRÊN cả `bindLocked()`
 * (`:1642`) lẫn `unbindLocked()` (`:1645`).
 *
 * Fixture lấy NGUYÊN VĂN từ xe (BYD Seal DiLink 3.0, Android 10, Kachi 2.79/180): một bản lúc đang kẹt và
 * một bản ngay sau khi chữa bằng force-stop. Xem `docs/specs/kachi-a11y-bind-stuck-autofix.html`.
 */
class AccessibilityBindingStuckTest {

    private val comp = "com.byd.launcher/com.byd.clusternav.modules.navaccess.NavAccessibilityService"

    /** Nguyên văn xe lúc KẸT — phím chết. */
    private val dumpStuck = """
        ACCESSIBILITY MANAGER (dumpsys accessibility)

        User state[attributes:{id=0, currentUser=true, touchExplorationEnabled=false, installedServiceCount=3}
             Bound services:{Service[label=.custom.StatusBarAcces…, feedbackType[FEEDBACK_SPOKEN], capabilities=43]}
             Enabled services:{{com.byd.vrassistant.xf/com.iflytek.autofly.access.service.AccessibilityServices}, {com.byd.launcher/com.byd.clusternav.modules.navaccess.NavAccessibilityService}, {com.android.systemui/com.android.systemui.custom.StatusBarAccessibilityService}}
             Binding services:{{com.byd.launcher/com.byd.clusternav.modules.navaccess.NavAccessibilityService}}]
    """.trimIndent()

    /**
     * Nguyên văn xe NGAY SAU khi force-stop + lắp lại — phím sống, log `accessibility booster connected`.
     *
     * ⚠ Nhãn PHẢI là nhãn THẬT của dịch vụ (`@string/acc_label` = *"ClusterNav — booster đọc bản đồ"*,
     * `AndroidManifest.xml:325`). Bản đầu của fixture ghi `label=Kachi — booster`: ROM DiLink in khối `Bound`
     * **chỉ có nhãn, không có package** ([isClusterNavBound] KDoc, [ĐO xe 2026-09-24]), nên nhận diện chạy bằng
     * token `clusternav` trong nhãn — một fixture mang nhãn khác nhãn thật thì bản dump "đã chữa" lại bị đọc là
     * CHƯA bound, tức nó khoá ngược điều cần khoá (CLAUDE.md §10: fixture phải là dữ liệu thật mới chịu lực).
     */
    private val dumpHealed = """
        ACCESSIBILITY MANAGER (dumpsys accessibility)

        User state[attributes:{id=0, currentUser=true, installedServiceCount=3}
             Bound services:{Service[label=.custom.StatusBarAcces…, capabilities=43], Service[label=ClusterNav — booster đọc bản đồ, capabilities=9]}
             Enabled services:{{com.byd.vrassistant.xf/com.iflytek.autofly.access.service.AccessibilityServices}, {com.byd.launcher/com.byd.clusternav.modules.navaccess.NavAccessibilityService}}
             Binding services:{}]
    """.trimIndent()

    @Test
    fun `dump ket thi nhan ra dang KET`() {
        assertTrue(
            AccessibilityRebind.isInBindingServices(dumpStuck, comp),
            "dump thật lúc phím chết PHẢI được nhận là kẹt trong Binding services",
        )
    }

    @Test
    fun `dump da chua thi KHONG con ket`() {
        assertFalse(
            AccessibilityRebind.isInBindingServices(dumpHealed, comp),
            "sau force-stop khối Binding rỗng ⇒ không được báo kẹt nữa (nếu không sẽ giết launcher vô cớ)",
        )
    }

    @Test
    fun `dump da chua PHAI duoc doc la DA GAN`() {
        assertTrue(
            AccessibilityRebind.isClusterNavBound(dumpHealed, comp),
            "khối Bound của ROM DiLink chỉ in NHÃN ⇒ nhận diện chạy bằng token `clusternav` trong nhãn thật " +
                "(`@string/acc_label`). Bài này đỏ nếu ai đổi nhãn dịch vụ mà quên token đó: lúc ấy xe đã bound " +
                "thật mà app vẫn tưởng chưa, nút Sửa ngay báo sai và thang chữa chạy vô cớ mỗi 30 s",
        )
    }

    @Test
    fun `chi nhan goi CUA MINH, khong nhan goi khac`() {
        val other = """
             Bound services:{}
             Binding services:{{com.byd.vrassistant.xf/com.iflytek.autofly.access.service.AccessibilityServices}}]
        """.trimIndent()
        assertFalse(
            AccessibilityRebind.isInBindingServices(other, comp),
            "dịch vụ của HÃNG kẹt thì KHÔNG được coi là mình kẹt — nếu không Kachi sẽ tự giết mình vì lỗi app khác",
        )
    }

    @Test
    fun `dump khong doc duoc thi KHONG bao ket`() {
        assertFalse(AccessibilityRebind.isInBindingServices(null, comp), "dump null")
        assertFalse(AccessibilityRebind.isInBindingServices("", comp), "dump rỗng")
        assertFalse(
            AccessibilityRebind.isInBindingServices("ACCESSIBILITY MANAGER\n  Bound services:{}", comp),
            "không có khối Binding ⇒ không khẳng định kẹt (không bao giờ giết tiến trình vì một bản dump lạ)",
        )
    }

    @Test
    fun `hai trang thai TACH BIET nhau`() {
        // (A) đã bật, chưa bound, KHÔNG kẹt — ca thường sau khi nổ máy: toggle chữa được.
        val fresh = """
             Bound services:{}
             Enabled services:{{com.byd.launcher/com.byd.clusternav.modules.navaccess.NavAccessibilityService}}
             Binding services:{}]
        """.trimIndent()
        assertFalse(AccessibilityRebind.isClusterNavBound(fresh, comp), "chưa bound")
        assertFalse(AccessibilityRebind.isInBindingServices(fresh, comp), "nhưng cũng chưa kẹt")
        assertTrue(
            AccessibilityRebind.accessibilityRebindWrites(
                "com.byd.launcher/com.byd.clusternav.modules.navaccess.NavAccessibilityService", false, comp,
            ).isNotEmpty(),
            "ca (A) vẫn dùng đường toggle như cũ",
        )
        // (B) đã bật, chưa bound, ĐANG kẹt — toggle vô ích, phải leo force-stop.
        assertFalse(AccessibilityRebind.isClusterNavBound(dumpStuck, comp), "chưa bound")
        assertTrue(AccessibilityRebind.isInBindingServices(dumpStuck, comp), "và ĐANG kẹt")
    }

    @Test
    fun `lenh chua chay TACH ROI va lap lai dich vu`() {
        val cur = "com.byd.vrassistant.xf/com.iflytek.autofly.access.service.AccessibilityServices"
        val cmd = AccessibilityRebind.forceStopRebindCommand(cur, "com.byd.launcher", comp, pauseSec = 4)
        assertTrue(cmd.startsWith("nohup sh -c '"), "phải tách rời: tiến trình phát lệnh chính là tiến trình bị giết")
        assertTrue(cmd.endsWith("&"), "phải chạy nền")
        assertTrue(cmd.contains(">/dev/null 2>&1 </dev/null"), "đóng cả ba luồng, không chết theo client")
        assertTrue(cmd.contains("am force-stop com.byd.launcher ;"), "giết gói của chính mình")
        assertTrue(cmd.contains("sleep 4 ;"), "chờ hệ dọn xong mới lắp lại")
        assertTrue(
            cmd.contains("settings put secure enabled_accessibility_services \"$cur:$comp\""),
            "[ĐO xe] force-stop XOÁ mục của mình khỏi settings và ghi đĩa ⇒ lắp lại là BẮT BUỘC, và phải giữ " +
                "nguyên dịch vụ của hãng",
        )
    }

    @Test
    fun `khong bao gio dung lenh giet goi cua NGUOI KHAC`() {
        assertEquals(
            "",
            AccessibilityRebind.forceStopRebindCommand("", "com.google.android.youtube", comp),
            "gói lệch với gói của component ⇒ KHÔNG dựng lệnh (chốt cứng ở tầng thi hành, CLAUDE.md §4)",
        )
        assertEquals("", AccessibilityRebind.forceStopRebindCommand("", "", comp), "gói rỗng ⇒ không dựng lệnh")
    }

    @Test
    fun `dang SHORT cua chinh minh cung bi coi la trung`() {
        // `ComponentName.flattenToShortString` chỉ rút gọn khi tên lớp nằm DƯỚI tên gói — component thật của Kachi
        // (`com.byd.launcher/com.byd.clusternav…`) thì không, nên nó không có dạng ngắn. Luật dedup vẫn phải đúng
        // cho ca có: không nhận ra `pkg/.Cls` là cùng component thì danh sách lắp lại mang MÌNH HAI LẦN.
        val own = "com.byd.launcher/com.byd.launcher.Acc"
        val cmd = AccessibilityRebind.forceStopRebindCommand(
            "com.byd.vrassistant.xf/com.iflytek.autofly.access.service.AccessibilityServices:com.byd.launcher/.Acc",
            "com.byd.launcher", own,
        )
        val list = cmd.substringAfter("enabled_accessibility_services \"").substringBefore("\"")
        assertEquals(2, list.split(':').size, "dạng SHORT là cùng một component ⇒ chỉ còn dịch vụ hãng + mình: $list")
        assertFalse(list.contains("/."), "bản SHORT của mình phải bị bỏ, chỉ thêm lại dạng đầy đủ đúng một lần")
    }

    @Test
    fun `thoi gian cho bi KEP trong bien`() {
        assertTrue(
            AccessibilityRebind.forceStopRebindCommand("", "com.byd.launcher", comp, pauseSec = 0).contains("sleep 1 ;"),
            "0 giây ⇒ kẹp lên 1: hệ cần thời gian dọn `mBindingServices` trước khi lắp lại",
        )
        assertTrue(
            AccessibilityRebind.forceStopRebindCommand("", "com.byd.launcher", comp, pauseSec = 9_999).contains("sleep 30 ;"),
            "kẹp xuống 30 giây: lệnh chạy tách rời nên một số ngủ vô lý sẽ treo việc lắp lại rất lâu",
        )
    }

    @Test
    fun `muc co ky tu shell thi TU CHOI dung lenh`() {
        // Giá trị này đọc từ `settings get` (ngoài tầm kiểm soát) rồi ghép vào `sh -c '…'`. Một dấu nháy đơn là
        // thoát khỏi chuỗi lệnh — mà đây đúng là lệnh tự giết tiến trình (CLAUDE.md §4.1 user-input → shell).
        assertEquals(
            "",
            AccessibilityRebind.forceStopRebindCommand("oem/svc';reboot;'", "com.byd.launcher", comp),
            "mục chứa ký tự shell ⇒ KHÔNG dựng lệnh nào cả",
        )
        assertEquals(
            "",
            AccessibilityRebind.forceStopRebindCommand("oem/\$(id)", "com.byd.launcher", comp),
            "`\$` trong dấu nháy kép bị nội suy ⇒ từ chối, không lặng lẽ ghi ra danh sách khác",
        )
        assertTrue(
            AccessibilityRebind.forceStopRebindCommand("com.oem.app/com.oem.app.A11y-Svc_2", "com.byd.launcher", comp)
                .contains("com.oem.app/com.oem.app.A11y-Svc_2"),
            "tên gói/lớp hợp lệ (kể cả `-` và `_`) vẫn phải được GIỮ NGUYÊN, không bị loại oan",
        )
    }

    @Test
    fun `lap lai khong sinh dau hai cham thua va khong nhan doi minh`() {
        val cmd = AccessibilityRebind.forceStopRebindCommand("null", "com.byd.launcher", comp)
        assertTrue(cmd.contains("\"$comp\""), "danh sách rỗng/`null` ⇒ chỉ còn mình, không có dấu ':' thừa")
        val dup = AccessibilityRebind.forceStopRebindCommand("$comp:$comp", "com.byd.launcher", comp)
        assertEquals(1, Regex(Regex.escape(comp)).findAll(dup.substringAfter("enabled_accessibility_services")).count(),
            "mọi bản trùng của mình bị bỏ, chỉ thêm đúng một lần")
    }
}
