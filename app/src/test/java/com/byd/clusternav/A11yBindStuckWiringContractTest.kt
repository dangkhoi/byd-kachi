package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
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

    /**
     * 2.83 (owner chốt 2026-09-29, quyết định A) — cổng "không app khách" và hạn mức một-lần-mỗi-lần-nổ-máy của 2.79
     * RỜI khỏi quyết định leo: [ĐO xe 29/09] cổng app khách chặn đúng ca cần chữa (ô đã có app khi lượt chữa tới nơi)
     * và kẹt sinh ra ở MỖI lần tắt máy. Thay bằng PHA ([AccessibilityHealGatesTest] khoá bảng ca; lớp 1/2 nối dây ở
     * [A11yLifecycleHealWiringContractTest]). Hai cổng còn lại của bản cũ (kẹt thật · R-nf5) giữ NGUYÊN chuỗi canh.
     */
    @Test
    fun `nac leo thang PHAI hoi cong truoc khi giet tien trinh`() {
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
        assertTrue(
            fn.contains("phase = phase,"),
            "cổng 3 (2.83): healStep PHẢI biết pha — thiếu nó thì hoặc lớp 3 lại tự giết launcher giữa lúc lái, hoặc " +
                "lớp 1/2 không bao giờ leo",
        )
        assertTrue(fn.contains("AccessibilityHealGates.healStep("), "mọi cổng phải đi qua cổng thuần đã có test")
        assertTrue(
            fn.contains("StackParse.noGuestAppVisible("),
            "app khách VẪN phải được ĐO và ghi vào log: đó là bằng chứng lượt giết có chạm app nào trong ô không",
        )
    }

    @Test
    fun `luot leo PHAI hoi lai pha ngay truoc khi ban`() {
        val gate = escalate.indexOf("!fireGate()")
        val marker = escalate.indexOf("Prefs.setA11yEscalatedAt(app, now)")
        val fire = escalate.indexOf("sh(cmd)")
        assertTrue(gate in 0 until marker, "cổng cuối (pha còn đúng không) phải đứng TRƯỚC marker và lệnh bắn")
        assertTrue(marker in 0 until fire)
        assertTrue(
            Regex("Thread\\.currentThread\\(\\)\\.isInterrupted").findAll(escalate).count() >= 2,
            "cờ interrupt phải được hỏi cả ở đầu hàm LẪN ngay trước khi bắn: lượt có thể bị bỏ GIỮA chừng",
        )
    }

    @Test
    fun `o cua minh PHAI do theo chu so huu that`() {
        assertTrue(
            escalate.contains("DisplayParse.ownedVirtualDisplayIds("),
            "[ĐO xe 2026-09-15] display 1 = `kachi-slot-0` (màn ảo của CHÍNH launcher), cụm = display 2 ⇒ " +
                "KHÔNG được bỏ qua cả dải display ≥ 1: phải đọc chủ sở hữu thật từ `dumpsys display`, vì đúng " +
                "những màn ảo của mình mới chết theo tiến trình và sinh mảng đen",
        )
        assertTrue(
            escalate.contains("if (displayDump.isBlank()) null"),
            "không đọc được `dumpsys display` ⇒ null ⇒ log nói 'không biết', không nói bừa ô rỗng",
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
        // 2.83-B (owner 29/09): lời gọi thêm tham số đuôi về nhà — chuỗi canh giữ NGUYÊN ba đối số cũ (gói lấy từ
        // chính app, component của mình) và khoá thêm tham số mới, không nới.
        // 2.93 CODE-FIX-AFTER-283 (6): thêm tham số dấu camera của ĐỜI XE — giữ nguyên ba đối số cũ + đuôi, khoá thêm tham số mới.
        assertTrue(
            fn.contains(
                "AccessibilityRebind.forceStopRebindCommand(\n" +
                    "            cur, app.packageName, ACC_COMP, homeTail = AccessibilityRebind.homeTailFor(userAsked),\n" +
                    "            cameraSig = camSig ?: AccessibilityRebind.CAMERA_SCREEN_SIGNATURE,\n" +
                    "        )",
            ),
            "phải dựng lệnh qua hàm có chốt cứng gói (lệch gói ⇒ chuỗi rỗng), không tự ghép chuỗi tại chỗ",
        )
        assertTrue(fn.contains("ClusterProfile.resolveCached(app).cameraSignature"), "dấu camera lấy từ hồ sơ ĐỜI XE (§7)")
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

    @Test
    fun `sau khi tu chua PHAI cham diem ket qua vao nhat ky`() {
        assertTrue(
            keepAlive.contains("firstTick && Prefs.a11yEscalatedAt(app) >= 0L"),
            "nhịp đầu của tiến trình mà mốc leo thang còn nguyên = tiến trình vừa bị CHÍNH MÌNH giết để chữa; " +
                "không chấm điểm ở đây thì nhật ký chỉ nói 'đã leo' chứ không nói 'leo xong có ăn không'",
        )
        assertTrue(keepAlive.contains("\"sau-chua-ON\""), "ghi rõ ca chữa ĂN")
        assertTrue(keepAlive.contains("\"sau-chua-VAN-TAT\""), "và ca chữa KHÔNG ăn — nói dối một nửa là vô dụng")
    }

    /**
     * Khoá lỗi [ĐO máy ảo 29/09, E2E 2.83 ca 4]: kẹt lúc đang chạy ⇒ cặp NOT_BOUND (watchdog) / STUCK (grant) mỗi 30 s
     * ăn hết trần 200 dòng sau ~50 phút. Watchdog CHỈ hỏi binder ⇒ phải khai `binderOnly = true`; các chỗ ghi có
     * bản dump hoặc là lượt CHẤM ĐIỂM (lớp 1/2, grant, `scoreAfterHeal`) thì KHÔNG được khai — khai nhầm là nuốt mất
     * dòng `sau-chua-VAN-TAT` / bước KẸT→CHƯA-GẮN thật.
     */
    @Test
    fun `watchdog ghi nhat ky dang quan sat CHI binder, cho khac thi khong`() {
        val tick = SourceRoots.body(keepAlive, "private val watchdog = object : Runnable {")
        assertEquals(1, Regex("A11yBindJournalStore\\.record\\(").findAll(tick).count(), "watchdog ghi đúng MỘT chỗ")
        assertEquals(1, Regex("binderOnly = true").findAll(tick).count(), "và chỗ đó khai quan sát CHỈ binder")
        val heal = SourceRoots.codeOf("src/main/java/com/byd/clusternav/A11yLifecycleHeal.kt")
        for ((name, code) in listOf("NavConnect" to navConnect, "A11yLifecycleHeal" to heal)) {
            assertTrue(code.contains("A11yBindJournalStore.record("), "$name vẫn phải ghi nhật ký")
            assertFalse(code.contains("binderOnly"), "$name ghi quan sát CÓ dump / chấm điểm ⇒ không được nuốt bước đổi")
        }
    }

    @Test
    fun `cham diem KHONG duoc xoa moc han muc`() {
        // ⚠ Cắt bằng ĐẾM NGOẶC ([SourceRoots.body]), KHÔNG `substringAfter`/`substringBefore`: mốc kết `"\n    }"`
        // là chuỗi xuất hiện ở khắp tệp, và nếu mốc đầu đổi một ký tự thì `substringAfter` trả NGUYÊN tệp ⇒ vùng
        // quét sai chỗ, số đếm dưới đây thành vô nghĩa (đúng bẫy "quét tràn = test giả" ở KDoc [SourceRoots.body]).
        val tick = SourceRoots.body(keepAlive, "private val watchdog = object : Runnable {")
        val clears = Regex("setA11yEscalatedAt\\(app, -1L\\)").findAll(tick).count()
        assertEquals(
            1, clears,
            "chỉ ĐÚNG MỘT chỗ được nhả hạn mức, và đó là nhánh `woke`. Thêm một chỗ xoá nữa là mở đường giết " +
                "launcher lặp: chữa xong → xoá mốc → lần sau lại leo, vòng vô hạn",
        )
    }
}
