package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá ĐƯỜNG DÂY của bản vá A11Y-BIND-STUCK (2026-09-28), không chỉ khoá logic thuần.
 *
 * Bài học khoá lại: đơn thuốc "force-stop + lắp lại" đã được kê từ `oncar-piper-crash-binding-2026-09-18.md`
 * mà **chưa bao giờ được nối dây** — mã có `force-stop` ở khắp nơi (cứu hộ cụm, mở app) nhưng KHÔNG một dòng
 * nào trong đường chữa Hỗ trợ, nên nút "Sửa ngay" chạy mãi một đường vô hiệu suốt nhiều tháng. Bài này đỏ nếu
 * đường dây đó lại bị nuốt mất (CLAUDE.md §8: compile xanh không có nghĩa là code chạy).
 */
class A11yBindStuckWiringContractTest {

    private val navConnect = SourceRoots.codeOf("src/main/java/com/byd/clusternav/NavConnect.kt")

    /**
     * Thân hàm leo thang, cắt bằng ĐẾM NGOẶC ([SourceRoots.body]).
     *
     * ⚠ Bản đầu của bài này cắt vùng bằng `substringAfter("private fun escalateIfStuck(")` rồi
     * `substringBefore` một mốc là chuỗi mở-KDoc — mà [SourceRoots.codeOf] đã XOÁ hết khối chú thích, nên mốc
     * kết đó KHÔNG CÒN TỒN TẠI trong chuỗi đang quét ⇒ vùng quét tràn tới
     * hết tệp ⇒ mọi `contains` bên dưới chỉ còn hỏi "chuỗi này có ở đâu đó trong NavConnect.kt không" (đúng cái
     * bẫy "quét tràn = test giả" mà KDoc của [SourceRoots.body] mô tả). Nay cắt đúng thân.
     */
    private val escalate = SourceRoots.body(navConnect, "private fun escalateIfStuck(")

    @Test
    fun `nac toggle that bai thi PHAI leo, khong duoc tra ve that bai thang`() {
        assertTrue(
            navConnect.contains("if (forceRebindIfNeeded(keyPair, sh)) GrantResult.BOUND else escalateIfStuck(app, sh, userAsked)"),
            "đường chữa phải nối thẳng nấc toggle sang nấc leo thang; trả NOT_BOUND ngay tại đó là quay lại " +
                "đúng regression cũ (nút Sửa ngay không bao giờ chữa được ca KẸT)",
        )
    }

    @Test
    fun `nac leo thang PHAI hoi bon cong truoc khi giet tien trinh`() {
        val fn = escalate
        assertTrue(fn.contains("AccessibilityRebind.isInBindingServices("), "cổng 1: đúng là ca KẸT mới leo")
        assertTrue(
            fn.contains("wanted = Prefs.voiceKeyEnabled(app),"),
            "cổng 2 (R-nf5): đường TỰ ĐỘNG chỉ leo trong đúng cổng của watchdog 30 s — watchdog là thứ lắp lại " +
                "enabled_accessibility_services nếu nửa sau lệnh tách rời không chạy",
        )
        assertFalse(
            fn.contains("Prefs.accBooster("),
            "KHÔNG được nới cổng đó bằng `|| Prefs.accBooster(app)`: booster mặc định BẬT, nên nới ra là mọi xe " +
                "đều có đường TỰ GIẾT tiến trình trong khi KHÔNG có vòng nào lắp lại dịch vụ sau đó (phím chết hẳn)",
        )
        assertTrue(fn.contains("StackParse.noGuestAppVisible("), "cổng 3: không app khách nào đang hiện (màn chính hoặc trong Ô)")
        assertTrue(fn.contains("Prefs.a11yEscalatedAt(app)"), "cổng 4: mỗi lần nổ máy chỉ leo một lần")
        assertTrue(fn.contains("AccessibilityHealGates.healStep("), "bốn cổng phải đi qua cổng thuần đã có test")
    }

    @Test
    fun `cong app khach PHAI biet O nao la cua minh`() {
        assertTrue(
            escalate.contains("DisplayParse.ownedVirtualDisplayIds("),
            "[ĐO xe 2026-09-15] display 1 = `kachi-slot-0` (màn ảo của CHÍNH launcher), cụm = display 2 ⇒ " +
                "KHÔNG được bỏ qua cả dải display ≥ 1: phải đọc chủ sở hữu thật từ `dumpsys display`, vì đúng " +
                "những màn ảo của mình mới chết theo tiến trình và sinh mảng đen",
        )
        assertTrue(
            escalate.contains("if (displayDump.isBlank()) null"),
            "không đọc được `dumpsys display` ⇒ null ⇒ cổng ĐÓNG (không giết dựa trên bản đọc hỏng)",
        )
    }

    @Test
    fun `luot grant da het gio thi KHONG duoc giet tien trinh sau lung caller`() {
        val guard = escalate.indexOf("Thread.currentThread().isInterrupted")
        val fire = escalate.indexOf("sh(cmd)")
        assertTrue(guard in 0 until fire, "join() hết giờ ⇒ caller đã trả kết quả; thân chạy nốt KHÔNG được tự giết")
    }

    @Test
    fun `moc PHAI duoc ghi TRUOC khi ban lenh tu giet`() {
        val marker = escalate.indexOf("Prefs.setA11yEscalatedAt(app, now)")
        val fire = escalate.indexOf("sh(cmd)")
        assertTrue(marker in 0 until fire, "CLAUDE.md §5: ghi marker TRƯỚC khi đổi state ngoài — tiến trình sắp chết")
    }

    @Test
    fun `lenh tu giet PHAI di qua ham dung lenh co chot goi`() {
        val fn = escalate
        assertTrue(
            fn.contains("AccessibilityRebind.forceStopRebindCommand(cur, app.packageName, ACC_COMP)"),
            "phải dựng lệnh qua hàm có chốt cứng gói (lệch gói ⇒ chuỗi rỗng), không tự ghép chuỗi tại chỗ",
        )
        assertFalse(
            Regex("\"am force-stop [^$]").containsMatchIn(fn),
            "KHÔNG được có literal `am force-stop <gói cố định>` trong đường này — gói phải lấy từ chính app",
        )
    }

    @Test
    fun `cau thong bao khong con do loi cho tai xe`() {
        val vi = SourceRoots.codeOf("src/main/res/values/strings_kachi.xml")
        assertFalse(
            vi.contains("xe đang tải cao"),
            "[ĐO 2026-09-28] lúc báo câu này xe đứng yên, tải bình thường; gốc là lỗ hổng framework ⇒ quy kết sai",
        )
        assertTrue(vi.contains("kachi_bridge_accessibility_restarting"), "phải có câu nói thật việc đang làm")
    }

    // ─── R7 nhật ký bền + ngòi nổ xe-thức ───────────────────────────────────

    private val keepAlive = SourceRoots.codeOf("src/main/java/com/byd/clusternav/VoiceKeyKeepAliveService.kt")

    @Test
    fun `watchdog PHAI ghi nhat ky moi nhip`() {
        assertTrue(
            keepAlive.contains("A11yBindJournalStore.record("),
            "không ghi nhật ký thì khoảnh khắc đứt trong đêm mãi mãi là [CHƯA BIẾT]: [ĐO 2026-09-28] vòng đệm " +
                "log trên xe chỉ còn 32 phút",
        )
        assertTrue(keepAlive.contains("A11yBindJournal.deepSleepMs("), "phải đo ngủ sâu bằng hiệu hai đồng hồ")
    }

    @Test
    fun `mot dot ngu dai PHAI nha cong cho phep chua lai`() {
        assertTrue(
            keepAlive.contains("if (woke) Prefs.setA11yEscalatedAt(app, -1L)"),
            "đầu máy chỉ tắt hẳn sau vài ngày, nên cổng chỉ-nhả-khi-reboot sẽ im vĩnh viễn sau lần chữa đầu; " +
                "mỗi đợt ngủ dài phải được coi là phiên mới",
        )
    }

    @Test
    fun `nga re KET phai duoc ghi lai`() {
        assertTrue(
            navConnect.contains("A11yBindJournal.State.STUCK"),
            "chỗ duy nhất phân biệt được KẸT là nơi có bản dump; không ghi ở đây thì nhật ký thiếu hẳn một trạng thái",
        )
    }

    @Test
    fun `nhat ky PHAI doc duoc trong app, khong bat owner go adb`() {
        val diag = SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/clustercast/DiagActivity.kt")
        assertTrue(
            diag.contains("A11yBindJournalStore.read("),
            "R7 + CLAUDE.md §11: nhật ký chỉ-ghi-không-đọc-được thì owner vẫn phải gõ adb ⇒ mất đúng mục đích. " +
                "Màn Chẩn đoán (Cài đặt › Chiếu cụm › Chẩn đoán) phải in nó ra để anh em chụp màn hình gửi về",
        )
        assertTrue(diag.contains("Prefs.a11yEscalatedAt("), "và cho biết lần nổ máy này đã tự chữa chưa")
    }
}
