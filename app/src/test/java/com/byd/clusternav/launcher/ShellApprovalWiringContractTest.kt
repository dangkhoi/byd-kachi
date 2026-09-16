package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ F4 — BÀI CANH DÂY NỐI "LẦN MỞ ĐẦU TRÊN XE" ═══════════════════════════════════════════════════════════════
 *
 * [ĐO] xe DiLink3.0 2026-09-14 (`docs/diagnostics/carlog-kachi-20260914-2044/session-findings.md`):
 * `20:49:16.986 WindowManager removeWindow … UsbDebuggingActivity` **đúng lúc** `KachiHomeActivity` resume toàn màn
 * ⇒ hộp "Cho phép gỡ lỗi USB?" chết trước khi người lái thấy; ổ cắm dadb kẹt `ESTABLISHED` (`Recv-Q` 24→48); hàng
 * quyền nói *"Hạn chế của môi trường"* trong khi sự thật là **đang chờ người dùng bấm**.
 *
 * Quét SOURCE (không dựng được Activity trong JVM thuần), qua [SourceRoots.codeOf] để **bỏ chú thích trước khi
 * kiểm** — viết tên hàm vào comment mà test xanh thì đó là test tự lừa mình. Phần quyết định thuần đã có bài riêng
 * ở `:car-integration` (`FirstOpenApprovalPolicyTest`); bài này canh đúng phần mà chỉ Android mới có: **vòng đời**.
 */
class ShellApprovalWiringContractTest {

    private val gate by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ShellChannelGate.kt") }
    private val act by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val wiring by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt") }
    private val pre by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/PermissionPreflight.kt") }
    private val panels by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/HomePanels.kt") }
    private val req by lazy {
        SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/LauncherRequirements.kt")
    }

    // ── 1 · LẦN DÒ ĐẦU BỊ HOÃN TỚI SAU KHUNG HÌNH ĐẦU ──────────────────────────────────────────────

    /**
     * Bệnh gốc: `onCreate` nối dadb **ngay**, nên hộp thoại hệ thống bung ra giữa lượt resume của màn chính và bị
     * chính lượt đó gỡ đi. Lượt dò đầu nay phải đi qua cổng, và cổng phải neo vào **sự kiện thật của cửa sổ**.
     */
    @Test
    fun `lan do dau di qua cong, khong con goi thang trong onCreate`() {
        assertTrue(act.contains("shellGate = ShellChannelGate("), "màn chính phải dựng cổng F4")
        assertTrue(act.contains("shellGate.arm()"), "dựng xong phải ARM — không ai gọi thì cổng là mã chết")
        assertFalse(
            act.contains("dadb.probe()"),
            "màn chính không được tự dò nữa: lượt dò đầu phải do cổng hẹn (nếu không, hộp thoại lại chết như 14/09)",
        )
        assertTrue(wiring.contains("dadb.probe()"), "đường nối kênh cũ vẫn phải còn nguyên, chỉ dời chỗ")
    }

    @Test
    fun `moc hoan la khung hinh dau cong tieu diem, khong phai mot giac ngu cung`() {
        val arm = SourceRoots.body(gate, "fun arm()")
        assertTrue(arm.contains("decorView.post"), "mốc bắt đầu = cây view đã gắn + lượt vẽ đầu đã lên hàng đợi")
        assertTrue(arm.contains("FirstOpenApproval.SETTLE_MS"), "khoảng yên phải lấy từ chính sách, không viết số")
        val schedule = SourceRoots.body(gate, "private fun schedule(")
        assertTrue(schedule.contains("handler.postDelayed"), "hẹn bằng Handler")
        assertFalse(gate.contains("Thread.sleep"), "cấm ngủ cứng: nó chặn luồng và không huỷ được khi màn khuất")
        assertTrue(gate.contains("fun onFocus("), "phải nghe onWindowFocusChanged")
        assertTrue(act.contains("shellGate.onFocus(hasFocus)"), "và màn chính phải gọi nó")
    }

    // ── 2 · THỬ LẠI ĐỀU ĐẶN, KHÔNG GIỚI HẠN — NHƯNG DỪNG Ở onStop ─────────────────────────────────

    @Test
    fun `thu lai khong gioi han so lan trong khi man con hien`() {
        val settle = SourceRoots.body(gate, "private fun settle(")
        assertTrue(
            settle.contains("schedule(step.retryAfterMs)"),
            "ca chờ-người-bấm phải hẹn lượt kế; bỏ nó là quay lại đúng bệnh F4 (phải tắt/mở lại app)",
        )
        listOf("maxAttempts", "attemptsLeft", "giveUp").forEach {
            assertFalse(gate.contains(it), "cổng KHÔNG được đếm số lần bỏ cuộc ('$it') — người lái bấm lúc nào cũng được")
        }
    }

    @Test
    fun `dung han o onStop, va lich da hen bi go`() {
        assertTrue(
            SourceRoots.body(act, "override fun onStop()").contains("shellGate.onHidden()"),
            "màn khuất mà vòng dò chạy tiếp = dựng hộp thoại hệ thống lên app người lái đang dùng",
        )
        assertTrue(
            SourceRoots.body(act, "override fun onDestroy()").contains("shellGate.onHidden()"),
            "huỷ màn cũng phải gỡ lượt đã hẹn (trước khi tắt thread nền)",
        )
        assertTrue(SourceRoots.body(act, "override fun onStart()").contains("shellGate.onShown()"))
        val hidden = SourceRoots.body(gate, "fun onHidden()")
        assertTrue(hidden.contains("handler.removeCallbacks(attemptRunnable)"), "phải gỡ lượt đã hẹn")
        assertTrue(hidden.contains("showing = false"))
        assertTrue(
            SourceRoots.body(gate, "private fun schedule(").contains("!showing"),
            "hàng rào phải ở chỗ HẸN, không chỉ ở chỗ gỡ — một lượt hẹn lọt qua là một hộp thoại bất ngờ",
        )
    }

    /**
     * Mất tiêu điểm = một cửa sổ khác đang ở trên, và ở ca này rất có thể chính là hộp thoại ta vừa dựng. Mở thêm
     * kết nối lúc ấy là dựng hộp thoại chồng lên cái người dùng đang đọc dở.
     */
    @Test
    fun `mat tieu diem thi bo luot do, nhung khong bo cuoc`() {
        val attempt = SourceRoots.body(gate, "private fun attempt()")
        assertTrue(attempt.contains("FirstOpenApproval.attemptAllowed(showing, focused)"), "phải hỏi chính sách thuần")
        assertTrue(attempt.contains("schedule(FirstOpenApproval.RETRY_EVERY_MS)"), "bỏ lượt này thì hẹn lượt sau")
    }

    // ── 3 · MỖI LƯỢT LÀ MỘT KẾT NỐI MỚI, CÓ HẠN ĐỌC ───────────────────────────────────────────────

    /**
     * `ShellTransport` cố ý **tái dùng** một kết nối và **không đặt hạn đọc** (đúng cho lệnh cửa sổ). Dò bằng nó ở
     * lần mở đầu chính là thứ làm luồng nền treo vĩnh viễn khi adbd im lặng — [ĐO] `Recv-Q` dâng 24→48 trên xe.
     */
    @Test
    fun `luot do mo phien RIENG co han doc, khong di qua ShellTransport`() {
        val probe = SourceRoots.body(gate, "fun probe(ctx: Context)")
        assertTrue(probe.contains("LocalDeviceShell.sessionResult("), "phải mở phiên rời, đóng ngay sau lượt dò")
        assertTrue(probe.contains("FirstOpenApproval.PROBE"), "và phải dùng chính sách lần-mở-đầu (có hạn đọc)")
        listOf("ShellTransport", "DadbShell", "Dadb.create").forEach {
            assertFalse(gate.contains(it), "'$it' tái dùng kết nối / không hạn đọc ⇒ lượt dò sẽ treo, không phân loại được")
        }
    }

    @Test
    fun `cong phan biet CHO NGUOI DUNG voi HAN CHE MOI TRUONG`() {
        val settle = SourceRoots.body(gate, "private fun settle(")
        assertTrue(settle.contains("FirstOpenStep.AwaitingUser"), "ca chờ người bấm")
        assertTrue(settle.contains("FirstOpenStep.Environment"), "ca hạn chế môi trường")
        assertTrue(settle.contains("FirstOpenApproval.step(reason, showing)"), "phân loại do chính sách thuần quyết")
        // Cấm tự đoán: cổng không được tự kết luận "chắc là đang chờ" từ một lý do chưa phân loại (CLAUDE.md §2).
        assertFalse(gate.contains("LocalShellFailure.UNKNOWN ->"), "UNKNOWN không được rẽ vào nhánh chờ người dùng")
    }

    /** Các đường dùng dadb khác **không** bị kéo theo (CLAUDE.md §6: đường đang chạy tốt thì không đụng). */
    @Test
    fun `duong phim mic va duong nen giu nguyen chinh sach cu`() {
        val assistant = SourceRoots.codeOf("src/main/java/com/byd/clusternav/modules/voicekey/AssistantLauncher.kt")
        assertTrue(assistant.contains("LocalShellRetry.AWAIT_ADB_APPROVAL"), "đường phím mic (F2) giữ nguyên")
        assertFalse(assistant.contains("FirstOpenApproval"), "chính sách lần-mở-đầu KHÔNG được lan sang đường khác")
        val autostart = SourceRoots.codeOf("src/main/java/com/byd/clusternav/VietMapAutostart.kt")
        assertTrue(autostart.contains("LocalShellRetry.BACKGROUND_READ_CAP"), "đường nền (F6) giữ nguyên")
        assertFalse(autostart.contains("FirstOpenApproval"))
    }

    // ── 4 · DẢI NHẮC: nói một việc, KHÔNG chặn thao tác ────────────────────────────────────────────

    @Test
    fun `dai nhac dung chu trong res va mau cua KachiTheme`() {
        assertTrue(gate.contains("R.string.kachi_shell_approval_msg"), "chữ phải ở res (VI/EN), không viết cứng")
        assertTrue(gate.contains("R.string.kachi_shell_approval_retry"), "phải có nút Thử lại")
        // ⚠ VISUAL-REFRESH P1 · T3 (2026-09-16): dải nhắc là **thẻ nội dung** nên nó chuyển từ `card()` sang
        // `surface()`. Tính chất cần giữ vẫn y nguyên — *"nền lấy từ hệ thiết kế, không dựng Drawable tại chỗ"* —
        // nên bài nhận CẢ HAI hàm dựng của hệ thay vì khoá đúng một cái. Khoá một cái là cách bài canh biến thành
        // rào cản cho chính việc dọn mà nó muốn.
        assertTrue(
            gate.contains("KachiTheme.surface(") || gate.contains("KachiTheme.card("),
            "nền lấy từ hệ thiết kế (KachiTheme.surface / KachiTheme.card), không dựng GradientDrawable tại chỗ",
        )
        assertFalse(Regex("\"#[0-9a-fA-F]{3,8}\"").containsMatchIn(gate), "cấm mã màu viết cứng — phải qua KachiTheme")
    }

    @Test
    fun `dai nhac KHONG chan thao tac nao`() {
        val show = SourceRoots.body(gate, "private fun showBanner()")
        assertTrue(show.contains("WRAP_CONTENT"), "chỉ chiếm đúng chỗ của nó")
        assertFalse(show.contains("MATCH_PARENT"), "phủ kín màn = chặn người lái chạm vào thứ khác")
        listOf("setOnTouchListener", "WindowManager", "setCancelable", "AlertDialog").forEach {
            assertFalse(gate.contains(it), "'$it' biến một mẩu tin thành một lớp chặn — trên xe đang chạy thì không")
        }
        assertTrue(gate.contains("fun retryNow()"), "nút Thử lại phải có đường thật")
        // ⚠ KHÔNG dùng [SourceRoots.body]: nó cắt thân biểu thức tới khai báo kế tiếp, mà `val padX` bên trong
        // `apply {}` khớp đúng mốc đó ⇒ vùng quét bị cụt trước `addView` (quét cụt = bài canh không thể đỏ).
        val banner = gate.substringAfter("private fun approvalBanner(").substringBefore("internal object")
        assertTrue(banner.contains("setOnClickListener { onRetry() }"), "nút Thử lại phải được nối vào đường thật")
    }

    @Test
    fun `duoc cap thi dai nhac bien mat va duong noi day chay ngay`() {
        val settle = SourceRoots.body(gate, "private fun settle(")
        val up = settle.substringAfter("FirstOpenStep.ChannelUp ->").substringBefore("is FirstOpenStep.AwaitingUser")
        assertTrue(up.contains("hideBanner()"), "kênh lên ⇒ dải nhắc phải biến mất")
        assertTrue(up.contains("onChannelUp()"), "và đường nối dây (gồm vòng tự cấp quyền) chạy ngay")
        assertTrue(up.contains("awaitingApproval = false"), "cờ cho hàng quyền phải trả về đúng")
    }

    // ── 5 · HÀNG QUYỀN NÓI ĐÚNG AI SỬA ĐƯỢC ───────────────────────────────────────────────────────

    @Test
    fun `hang quyen kenh shell doi sang viec cua NGUOI DUNG khi dang duoc hoi`() {
        assertTrue(req.contains("val SHELL_CHANNEL_AWAITING_APPROVAL"), "phải có biến thể 'đang hỏi người dùng'")
        // ⚠ KHÔNG dùng [SourceRoots.body]: với `val X = Y.copy(…)` nó nhảy qua NGUYÊN danh sách tham số (bước
        // "vượt danh sách tham số" dành cho `fun`) rồi cắt từ khai báo SAU đó ⇒ quét nhầm sang mục kế tiếp.
        val variant = req.substringAfter("val SHELL_CHANNEL_AWAITING_APPROVAL =").substringBefore("\n    )")
        assertTrue(variant.contains("fixBy = FixBy.USER"), "đang hỏi người dùng thì KHÔNG phải hạn chế môi trường")
        assertTrue(variant.contains("Cho phép gỡ lỗi USB"), "phải nói đúng tên hộp thoại người dùng đang nhìn")
        assertTrue(variant.contains("Luôn cho phép"), "phải nhắc tích ô 'luôn cho phép' (không tích = chỉ sống 1 kết nối)")
        assertTrue(variant.contains("userActionEn"), "câu việc-cần-làm phải có bản tiếng Anh")
        assertTrue(
            req.contains("fun check(awaitingShellApproval: Boolean = false"),
            "mặc định false = nguyên hành vi cũ; bật cờ phải là quyết định tường minh của chỗ gọi",
        )
    }

    @Test
    fun `man Cai dat doc co tu chinh cong, khong tu doan`() {
        assertTrue(
            panels.contains("awaitingApproval = shellAwaiting()"),
            "bảng quyền phải hỏi cổng F4; tự đoán lý do là đúng thứ CLAUDE.md §2 cấm",
        )
        assertTrue(act.contains("shellAwaiting = { shellGate.awaitingApproval }"), "và cờ đó tới từ cổng thật")
    }

    @Test
    fun `dang hoi nguoi dung thi KHONG toast chong len dai nhac`() {
        val fn = SourceRoots.body(pre, "fun runAndReport(")
        assertTrue(fn.contains("!awaitingApproval &&"), "dải nhắc đã nói câu đúng hơn; toast chung là nhiễu (U8b)")
        assertTrue(fn.contains("check(activity, shellUsable, awaitingApproval)"), "báo cáo + nhật ký vẫn phải chạy")
    }

    // ── 6 · CHỮ SONG NGỮ ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `hai chuoi moi co du hai ban dich`() {
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        listOf("kachi_shell_approval_msg", "kachi_shell_approval_retry").forEach { key ->
            assertTrue(vi.contains("name=\"$key\""), "thiếu $key ở values/")
            assertTrue(en.contains("name=\"$key\""), "thiếu $key ở values-en/ ⇒ người dùng English thấy tiếng Việt")
        }
        val enMsg = Regex("<string name=\"kachi_shell_approval_msg\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .find(en)?.groupValues?.get(1)
        assertNotNull(enMsg)
        assertEquals(
            emptyList<Char>(),
            enMsg!!.filter { it in "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ" }.toList(),
            "bản EN còn dấu tiếng Việt (dịch nửa vời)",
        )
    }
}
