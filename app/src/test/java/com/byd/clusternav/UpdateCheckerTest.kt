package com.byd.clusternav

import com.byd.clusternav.carexec.LocalInstallOutcome
import com.byd.clusternav.carexec.LocalShellFailure
import com.byd.clusternav.launcher.Lang as CoreLang
import com.byd.clusternav.launcher.Strings as CoreStrings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** So sánh version — thuần, test off-device. Khoá logic quyết định "có bản mới". */
class UpdateCheckerTest {
    @Test fun `moi hon`() {
        assertTrue(UpdateChecker.cmp("0.57", "0.56") > 0)
        assertTrue(UpdateChecker.cmp("0.56", "0.9") > 0, "0.56 > 0.9 vì 56 > 9 (không phải so chuỗi)")
        assertTrue(UpdateChecker.cmp("1.0", "0.99") > 0)
        assertTrue(UpdateChecker.cmp("0.56.1", "0.56") > 0)
    }
    @Test fun `bang hoac cu hon`() {
        assertEquals(0, UpdateChecker.cmp("0.56", "0.56"))
        assertEquals(0, UpdateChecker.cmp("0.56", "0.56.0"))
        assertTrue(UpdateChecker.cmp("0.55", "0.56") < 0)
    }

    /** L2 — kênh riêng: repo byd-kachi, tên Kachi-<ver>-release.apk, vẫn nhận tên cũ; tên lạ thì bỏ qua. */
    @Test fun `ten tep phat hanh Kachi va ten cu deu doc ra phien ban`() {
        assertEquals("1.41", UpdateChecker.apkVersion("Kachi-1.41-release.apk"))
        assertEquals("1.38", UpdateChecker.apkVersion("ClusterNav-1.38-release.apk"))
        assertEquals(null, UpdateChecker.apkVersion("Kachi-1.41-debug.apk"))
        assertEquals(null, UpdateChecker.apkVersion("Kachi-1.41-abc123-release.apk"), "tên có lát cắt (collector T10) không phải bản OTA")
        assertEquals(null, UpdateChecker.apkVersion("README.md"))
    }

    @Test fun `kenh cap nhat la repo byd-kachi`() {
        val src = java.nio.file.Path.of(System.getProperty("user.dir")).let { d ->
            val f = d.resolve("src/main/java/com/byd/clusternav/UpdateChecker.kt")
            (if (java.nio.file.Files.exists(f)) f else d.resolve("app").resolve("src/main/java/com/byd/clusternav/UpdateChecker.kt")).toFile().readText()
        }
        assertTrue(src.contains("REPO = \"dangkhoi/byd-kachi\""), "OTA phải dò đúng repo của Kachi (remote origin), không phải byd-launcher")
    }

    // ── U11 · LÝ DO CÀI HỎNG → CÂU NÓI ─────────────────────────────────────────────────────────────
    //
    // Bệnh khoá lại: MỌI thất bại đều ra *"cài thất bại (khác chữ ký/phiên bản?)"*. [ĐO] máy ảo 2026-09-13 —
    // thiếu `adb reverse tcp:5555 tcp:5555` ⇒ không có kênh dadb nào, mà câu hiện ra vẫn đổ cho chữ ký ⇒ người
    // đọc đi sửa nhầm bệnh. Ánh xạ lý do→câu là hàm THUẦN nên khoá được off-device, không cần xe.

    @BeforeEach fun vietnamese() { CoreStrings.current = CoreLang.VI }
    @AfterEach fun restore() { CoreStrings.current = CoreLang.VI }

    @Test fun `khong co kenh shell KHONG duoc doc thanh loi chu ky`() {
        val msg = UpdateChecker.installMessage(
            LocalInstallOutcome.NoShellChannel(LocalShellFailure.PORT_CLOSED), "/data/x/update.apk",
        )
        assertTrue(msg.contains("kênh shell"), "phải nói đúng chỗ hỏng: không mở được kênh tới xe — $msg")
        assertTrue(msg.contains("Hệ thống & quyền"), "và chỉ đường tới trang làm được việc đó — $msg")
        assertTrue(msg.contains("PORT_CLOSED"), "kèm lý do đã phân loại để ảnh chụp màn đủ chẩn đoán — $msg")
        assertFalse(msg.contains("chữ ký"), "KHÔNG được đổ cho chữ ký khi pm còn chưa thấy APK — đó là bệnh U11")
        assertTrue(msg.contains("/data/x/update.apk"), "bản đã tải vẫn nằm đó, phải nói chỗ để cài tay")
    }

    /**
     * ⚠ Soát senior 2026-09-13 — **bên trong "không có kênh" cũng phải tách việc phải làm.**
     *
     * Bản đầu của U11 tách tới mức *kênh* vs *pm* rồi lại gộp cả năm lý do kênh vào một câu "xem Cài đặt › Hệ thống
     * & quyền". Ca hay gặp nhất của OTA là [LocalShellFailure.AWAITING_APPROVAL]: hộp thoại *"Cho phép gỡ lỗi USB?"*
     * đang bung NGAY trên màn hình đó ⇒ bảo đi vào Cài đặt là chỉ sai chỗ, đúng họ bệnh U11 sinh ra để chữa.
     * `AssistantLauncher.failureMessage` đã nói đúng việc cho cùng phân loại này từ 2026-08-24.
     */
    @Test fun `dang cho bam Cho phep thi phai noi dung cai nut do, khong day vao Cai dat`() {
        listOf(LocalShellFailure.AWAITING_APPROVAL, LocalShellFailure.AUTH_REJECTED).forEach { reason ->
            val msg = UpdateChecker.installMessage(LocalInstallOutcome.NoShellChannel(reason), "/data/x/update.apk")
            assertTrue(msg.contains("Cho phép"), "$reason: phải chỉ đúng nút đang ở trước mặt — $msg")
            assertTrue(msg.contains("luôn cho phép"), "$reason: nhắc tích ô, vì mỗi lần thử là một kết nối MỚI — $msg")
            assertFalse(msg.contains("Hệ thống & quyền"), "$reason: việc cần làm KHÔNG nằm trong Cài đặt — $msg")
            assertTrue(msg.contains(reason.name), "$reason: mã lý do vẫn phải có để ảnh chụp màn đủ chẩn đoán — $msg")
        }
        CoreStrings.current = CoreLang.EN
        val en = UpdateChecker.installMessage(
            LocalInstallOutcome.NoShellChannel(LocalShellFailure.AWAITING_APPROVAL), "/p.apk",
        )
        assertTrue(en.contains("USB-debugging dialog"), "bản Anh phải là câu Anh thật — $en")
    }

    @Test fun `pm tu choi thi chuyen NGUYEN VAN ly do cua pm`() {
        val msg = UpdateChecker.installMessage(
            LocalInstallOutcome.PmRejected("\tpkg: /data/local/tmp/x.apk\nFailure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]"),
            "/data/x/update.apk",
        )
        assertTrue(msg.contains("pm install"), "phải nói rõ chính pm là bên từ chối — $msg")
        assertTrue(msg.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE"), "mã lỗi thật của pm phải tới được người đọc — $msg")
        assertFalse(msg.contains("kênh shell"), "kênh đã lên rồi thì không được nói ngược lại")
    }

    /** Thành công vẫn đi qua CÙNG một chỗ dựng câu (không có bản sao chuỗi nào ở [UpdateChecker.install]). */
    @Test fun `cai duoc thi van la cau cu`() {
        assertTrue(UpdateChecker.installMessage(LocalInstallOutcome.Ok, "/data/x/update.apk").contains("đã cài"))
    }

    /** pm in nhiều dòng tiến trình rồi mới tới phán quyết ⇒ lấy dòng NÓI LỖI, không lấy dòng đầu. */
    @Test fun `chon dung dong dang doc trong output cua pm`() {
        assertEquals(
            "Failure [INSTALL_FAILED_VERSION_DOWNGRADE]",
            UpdateChecker.pmReason("Performing Streamed Install\nFailure [INSTALL_FAILED_VERSION_DOWNGRADE]\n", vi = true),
        )
        assertEquals("Success-ish tail", UpdateChecker.pmReason("first\n  Success-ish tail  ", vi = true), "không có dòng lỗi ⇒ dòng cuối còn chữ")
        assertEquals("pm không in gì", UpdateChecker.pmReason("   \n\n", vi = true), "pm câm cũng phải nói ra, không bịa nguyên nhân")
    }

    /** Bản Anh phải là câu ANH thật, không phải tiếng Việt lọt lưới (cả hai câu mới của U11). */
    @Test fun `hai cau moi co ban tieng Anh`() {
        CoreStrings.current = CoreLang.EN
        val noChannel = UpdateChecker.installMessage(LocalInstallOutcome.NoShellChannel(LocalShellFailure.IO_ERROR), "/p.apk")
        val rejected = UpdateChecker.installMessage(LocalInstallOutcome.PmRejected("Failure [X]"), "/p.apk")
        assertTrue(noChannel.contains("no shell channel"), noChannel)
        assertTrue(noChannel.contains("System & permissions"), noChannel)
        assertTrue(rejected.contains("pm install rejected"), rejected)
    }
}
