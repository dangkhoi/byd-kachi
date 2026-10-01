package com.byd.clusternav.carexec

import com.byd.clusternav.carexec.ShellChannelPhase.CHECKING
import com.byd.clusternav.carexec.ShellChannelPhase.ENVIRONMENT
import com.byd.clusternav.carexec.ShellChannelPhase.NEEDS_APPROVAL
import com.byd.clusternav.carexec.ShellChannelPhase.STARTING
import com.byd.clusternav.carexec.ShellChannelPhase.UNKNOWN
import com.byd.clusternav.carexec.ShellChannelPhase.UP
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * READY-AT-HOME (spec `docs/specs/kachi-ready-at-home.html` §4.4–§4.9) — bảng THUẦN của kênh shell mức tiến trình,
 * chế độ DỰ PHÒNG (chỉ dấu bền; dò im lặng chờ cổng §14 T0). Mỗi nhóm khoá một hồi quy owner yêu cầu 2026-10-01:
 *  • nối sớm CHỈ khi xe đã duyệt khoá NÀY (dấu tươi) — không dấu = có thể khoá chưa duyệt = hộp bung từ nền (F4 14/09);
 *  • lượt sớm gặp AWAITING_APPROVAL ⇒ xoá dấu + KHÔNG thử lại từ nền (đường nền bị chặn tới khi F4 đo lại);
 *  • chưa duyệt ⇒ chạm ô hiện thẻ, KHÔNG BAO GIỜ mở cửa sổ nổi; đã duyệt mà kênh chưa lên ⇒ chạm ô vào hàng chờ.
 */
class ShellReadinessPolicyTest {

    private val fp = "1a2b3c4d5e6f70819a2b3c4d5e6f70819a2b3c4d5e6f70819a2b3c4d5e6f7081"
    private val day = 24L * 3_600_000L
    private val t0 = 1_800_000_000_000L
    private fun approved(ok: Long = t0, win: Long = 7 * day, f: String = fp) = ShellApprovalLedger.Approved(f, ok, win)

    // ── Dấu tươi (§4.4.2) ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `dau tuoi khi cung van tay va con trong han tru le`() {
        assertTrue(ShellReadinessPolicy.fresh(approved(), fp, t0 + 1_000))
        // 7 ngày − lề 24 h = 6 ngày: còn tươi ngay trước, hết tươi đúng tại mốc.
        assertTrue(ShellReadinessPolicy.fresh(approved(), fp, t0 + 6 * day - 1))
        assertFalse(ShellReadinessPolicy.fresh(approved(), fp, t0 + 6 * day))
    }

    @Test
    fun `khoa sinh lai, chua tung, bia mo, gio lui thi KHONG tuoi`() {
        assertFalse(ShellReadinessPolicy.fresh(approved(f = "ff".repeat(32)), fp, t0), "khoá mới = chưa duyệt")
        assertFalse(ShellReadinessPolicy.fresh(ShellApprovalLedger.None, fp, t0))
        assertFalse(ShellReadinessPolicy.fresh(ShellApprovalLedger.Lost(t0), fp, t0))
        assertFalse(ShellReadinessPolicy.fresh(approved(), fp, t0 - 1), "giờ tường lùi ⇒ thận trọng hơn framework")
        assertFalse(ShellReadinessPolicy.fresh(approved(), null, t0), "không đọc được khoá ⇒ không đoán")
    }

    @Test
    fun `han 0 nghia la khong bao gio het (AdbDebuggingManager 1135), han ngan dung le mot nua`() {
        assertTrue(ShellReadinessPolicy.fresh(approved(win = 0), fp, t0 + 365 * day))
        val hour = 3_600_000L
        assertTrue(ShellReadinessPolicy.fresh(approved(win = 2 * hour), fp, t0 + hour - 1))
        assertFalse(ShellReadinessPolicy.fresh(approved(win = 2 * hour), fp, t0 + hour))
        assertTrue(ShellReadinessPolicy.fresh(approved(win = -1), fp, t0 + day), "thiếu hạn ⇒ mặc định 7 ngày")
    }

    // ── Lượt sớm: nối CHỈ khi có dấu tươi ───────────────────────────────────────────────────────────────────

    @Test
    fun `noi som chi khi co dau tuoi`() {
        assertEquals(EarlyPlan.CONNECT, ShellReadinessPolicy.earlyPlan(approved(), fp, t0 + 1))
        assertEquals(EarlyPlan.GATE_NEVER, ShellReadinessPolicy.earlyPlan(ShellApprovalLedger.None, fp, t0))
        assertEquals(EarlyPlan.GATE_LOST, ShellReadinessPolicy.earlyPlan(ShellApprovalLedger.Lost(t0), fp, t0))
        assertEquals(EarlyPlan.GATE_KEY_CHANGED, ShellReadinessPolicy.earlyPlan(approved(f = "ab".repeat(32)), fp, t0))
        assertEquals(EarlyPlan.GATE_STALE, ShellReadinessPolicy.earlyPlan(approved(), fp, t0 + 8 * day))
    }

    @Test
    fun `luot som gap AWAITING hoac AUTH_REJECTED thi thu hoi, KHONG thu lai tu nen`() {
        assertEquals(EarlyStep.Revoked, ShellReadinessPolicy.afterEarly(LocalShellFailure.AWAITING_APPROVAL, 1))
        assertEquals(EarlyStep.Revoked, ShellReadinessPolicy.afterEarly(LocalShellFailure.AUTH_REJECTED, 1))
        val t = ShellReadinessPolicy.next(ShellReadinessState(CHECKING), ReadyEvent.Revoked, hadRecord = true)
        assertEquals(ShellReadinessState(NEEDS_APPROVAL, lost = true), t.state)
        assertEquals(LedgerOp.FORGET, t.ledger, "thu hồi ⇒ xoá dấu (bia mộ)")
        assertEquals(
            Admission.DENY,
            ShellReadinessPolicy.admit(ShellSessionKind.BACKGROUND, t.state.phase, ledgerFresh = true, canWait = true),
            "sau thu hồi mọi đường nền bị chặn — kể cả khi dấu cũ còn đọc là tươi",
        )
        assertEquals(
            Admission.ALLOW,
            ShellReadinessPolicy.admit(ShellSessionKind.ASK, t.state.phase, ledgerFresh = false, canWait = true),
            "F4 / nút bấm vẫn hỏi được",
        )
    }

    @Test
    fun `loi moi truong thu lai 3 lan gian 1s roi 2s, giu dau`() {
        assertEquals(EarlyStep.Up, ShellReadinessPolicy.afterEarly(null, 1))
        assertEquals(EarlyStep.Retry(1_000), ShellReadinessPolicy.afterEarly(LocalShellFailure.PORT_CLOSED, 1))
        assertEquals(EarlyStep.Retry(2_000), ShellReadinessPolicy.afterEarly(LocalShellFailure.IO_ERROR, 2))
        assertEquals(EarlyStep.GiveUp(LocalShellFailure.UNKNOWN), ShellReadinessPolicy.afterEarly(LocalShellFailure.UNKNOWN, 3))
        val t = ShellReadinessPolicy.next(ShellReadinessState(CHECKING), ReadyEvent.Environment(LocalShellFailure.PORT_CLOSED), true)
        assertEquals(ENVIRONMENT, t.state.phase)
        assertEquals(LedgerOp.NONE, t.ledger, "lỗi môi trường KHÔNG xoá dấu")
    }

    // ── Bảng chuyển trạng thái (§4.5) ──────────────────────────────────────────────────────────────────────

    @Test
    fun `bang chuyen trang thai`() {
        val s0 = ShellReadinessState.STARTING
        assertEquals(CHECKING, ShellReadinessPolicy.next(s0, ReadyEvent.EarlyStart, false).state.phase)
        assertEquals(UNKNOWN, ShellReadinessPolicy.next(s0, ReadyEvent.EarlySkipped, false).state.phase)
        ShellChannelPhase.entries.forEach { p ->
            val up = ShellReadinessPolicy.next(ShellReadinessState(p), ReadyEvent.Up, false)
            assertEquals(UP, up.state.phase, "mọi pha + bắt tay xong ⇒ UP ($p)")
            assertEquals(LedgerOp.MARK_UP, up.ledger)
        }
        val up = ShellReadinessState(UP)
        assertEquals(up, ShellReadinessPolicy.next(up, ReadyEvent.Environment(LocalShellFailure.IO_ERROR), true).state,
            "lỗi lệnh / đứt KHÔNG có nghĩa là mất duyệt")
        assertEquals(up, ShellReadinessPolicy.next(up, ReadyEvent.EarlyStart, true).state)
        assertEquals(ShellReadinessState(NEEDS_APPROVAL, lost = false),
            ShellReadinessPolicy.next(ShellReadinessState(UNKNOWN), ReadyEvent.AwaitingUser, hadRecord = false).state,
            "chưa từng có dấu ⇒ CHƯA TỪNG")
        assertEquals(LedgerOp.NONE, ShellReadinessPolicy.next(up, ReadyEvent.AwaitingUser, true).ledger)
    }

    /**
     * Review lượt 1 [P3]: F4 xếp AUTH_REJECTED vào nhánh MÔI TRƯỜNG, nhưng chính phiên dò đó đã báo THU HỒI. Lý do loại
     * DUYỆT không được hạ NEEDS_APPROVAL xuống ENVIRONMENT (thẻ nói "cổng chưa sẵn sàng" với người vừa bấm Từ chối).
     */
    @Test
    fun `ly do loai DUYET khong ha CAN DUYET xuong MOI TRUONG`() {
        listOf(false, true).forEach { lost ->
            val needs = ShellReadinessState(NEEDS_APPROVAL, lost = lost)
            listOf(LocalShellFailure.AUTH_REJECTED, LocalShellFailure.AWAITING_APPROVAL).forEach { r ->
                val t = ShellReadinessPolicy.next(needs, ReadyEvent.Environment(r), hadRecord = lost)
                assertEquals(needs, t.state, "$r giữ CẦN DUYỆT (lost=$lost)")
                assertEquals(LedgerOp.NONE, t.ledger)
            }
            listOf(LocalShellFailure.PORT_CLOSED, LocalShellFailure.IO_ERROR, LocalShellFailure.UNKNOWN).forEach { r ->
                assertEquals(ShellReadinessState(ENVIRONMENT, reason = r),
                    ShellReadinessPolicy.next(needs, ReadyEvent.Environment(r), hadRecord = lost).state, "$r là môi trường thật")
            }
        }
        assertEquals(ShellReadinessState(ENVIRONMENT, reason = LocalShellFailure.AUTH_REJECTED),
            ShellReadinessPolicy.next(ShellReadinessState(UNKNOWN), ReadyEvent.Environment(LocalShellFailure.AUTH_REJECTED), false).state,
            "chưa có phép đo duyệt nào thì giữ nguyên hành vi cũ")
    }

    /**
     * Review lượt 1 [P3] (E2E 02/10 ca 3a): cổng 5555 đóng + đường nền bị chặn ⇒ F4 là đường duy nhất đo lại được ⇒ hẹn
     * dò lại. CHỈ PORT_CLOSED (không kết nối tới adbd ⇒ không thể dựng hộp). Review lượt 2 [P3]: cả khi có dấu tươi —
     * dấu tươi chỉ mở đường NỀN, không đưa màn chính lên (HOME chỉ nối dây qua F4 / `adopt`).
     */
    @Test
    fun `F4 tu do lai khi cong dong, co hay khong co dau tuoi`() {
        assertEquals(ShellReadinessPolicy.ENV_REPROBE_MS, ShellReadinessPolicy.envReprobeMs(LocalShellFailure.PORT_CLOSED))
        LocalShellFailure.entries.filter { it != LocalShellFailure.PORT_CLOSED }.forEach { r ->
            assertEquals(0L, ShellReadinessPolicy.envReprobeMs(r), "$r có thể đã chạm adbd / người vừa từ chối")
        }
    }

    @Test
    fun `ket qua phien - chi phien HOI hong o bat tay moi xoa dau`() {
        assertEquals(ReadyEvent.Up, ShellReadinessPolicy.outcomeEvent(ShellSessionKind.BACKGROUND, true, LocalShellFailure.IO_ERROR, true, false),
            "bắt tay xong rồi lệnh hỏng ⇒ khoá VẪN được nhận")
        assertEquals(ReadyEvent.Revoked, ShellReadinessPolicy.outcomeEvent(ShellSessionKind.ASK, false, LocalShellFailure.AWAITING_APPROVAL, false, true))
        assertNull(ShellReadinessPolicy.outcomeEvent(ShellSessionKind.ASK, false, LocalShellFailure.AWAITING_APPROVAL, true, true),
            "hỏng SAU khi lệnh đã gửi (hạn đọc giữa lệnh dài cũng là AWAITING) ⇒ không xoá dấu")
        assertNull(ShellReadinessPolicy.outcomeEvent(ShellSessionKind.BACKGROUND, false, LocalShellFailure.AWAITING_APPROVAL, false, true),
            "phiên NỀN không có quyền kết luận thu hồi")
        assertNull(ShellReadinessPolicy.outcomeEvent(ShellSessionKind.ASK, false, LocalShellFailure.PORT_CLOSED, false, true))
        assertNull(ShellReadinessPolicy.outcomeEvent(ShellSessionKind.ASK, false, LocalShellFailure.AUTH_REJECTED, false, false),
            "không ép bắt tay ⇒ không biết lỗi nổ trước hay sau lệnh")
    }

    // ── Cổng thi hành (§4.6, chế độ dự phòng) ──────────────────────────────────────────────────────────────

    @Test
    fun `cong thi hanh - bang day du`() {
        val bg = ShellSessionKind.BACKGROUND
        ShellChannelPhase.entries.forEach { p ->
            assertEquals(Admission.ALLOW, ShellReadinessPolicy.admit(ShellSessionKind.ASK, p, false, false), "ASK luôn cho ($p)")
        }
        assertEquals(Admission.ALLOW, ShellReadinessPolicy.admit(bg, UP, false, true))
        assertEquals(Admission.DENY, ShellReadinessPolicy.admit(bg, NEEDS_APPROVAL, true, true))
        assertEquals(Admission.WAIT, ShellReadinessPolicy.admit(bg, CHECKING, true, canWait = true))
        assertEquals(Admission.WAIT, ShellReadinessPolicy.admit(bg, STARTING, false, canWait = true))
        assertEquals(Admission.ALLOW, ShellReadinessPolicy.admit(bg, CHECKING, true, canWait = false), "luồng chính không chờ")
        assertEquals(Admission.DENY, ShellReadinessPolicy.admit(bg, CHECKING, false, canWait = false))
        assertEquals(Admission.ALLOW, ShellReadinessPolicy.admit(bg, UNKNOWN, true, true))
        assertEquals(Admission.DENY, ShellReadinessPolicy.admit(bg, UNKNOWN, false, true), "chưa đo + không dấu = CHẶN (R1.1)")
        assertEquals(Admission.ALLOW, ShellReadinessPolicy.admit(bg, ENVIRONMENT, true, true))
        assertEquals(Admission.DENY, ShellReadinessPolicy.admit(bg, ENVIRONMENT, false, true))
        assertTrue(ShellReadinessPolicy.usable(UP, false))
        assertFalse(ShellReadinessPolicy.usable(NEEDS_APPROVAL, true))
        assertFalse(ShellReadinessPolicy.usable(UNKNOWN, false))
    }

    // ── HOME + giao diện ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `HOME nhan kenh san chi khi du nam dieu kien va khong co luot F4 dang bay`() {
        assertTrue(ShellReadinessPolicy.adoptAllowed(up = true, framed = true, showing = true, interactive = true, channelUp = false, inFlight = false))
        assertFalse(ShellReadinessPolicy.adoptAllowed(false, true, true, true, false, false), "kênh chưa lên ⇒ F4 như cũ")
        assertFalse(ShellReadinessPolicy.adoptAllowed(true, false, true, true, false, false), "chưa có khung đầu")
        assertFalse(ShellReadinessPolicy.adoptAllowed(true, true, false, true, false, false), "màn đã khuất")
        assertFalse(ShellReadinessPolicy.adoptAllowed(true, true, true, false, false, false), "màn TẮT (lượt tắt máy) ⇒ app ô không chạy")
        assertFalse(ShellReadinessPolicy.adoptAllowed(true, true, true, true, true, false), "đã nhận rồi")
        assertFalse(ShellReadinessPolicy.adoptAllowed(true, true, true, true, false, true), "F4 đang bay tự chạy onChannelUp")
    }

    @Test
    fun `chua duyet thi cham o hien the ngay, KHONG mo noi`() {
        assertEquals(SlotTapStep.PROMPT, ShellReadinessPolicy.slotTap(NEEDS_APPROVAL, 0))
        assertEquals(SlotTapStep.PROMPT, ShellReadinessPolicy.slotTap(ENVIRONMENT, 0))
        // Kiểu trả về chỉ có hai nhánh — không có nhánh nào "mở cửa sổ nổi".
        assertEquals(setOf(SlotTapStep.WAIT, SlotTapStep.PROMPT), SlotTapStep.entries.toSet())
    }

    @Test
    fun `da duyet ma kenh chua len thi cham o vao hang cho, qua 10s moi hien the`() {
        listOf(CHECKING, STARTING, UNKNOWN, UP).forEach { p ->
            assertEquals(SlotTapStep.WAIT, ShellReadinessPolicy.slotTap(p, 0), "$p")
            assertEquals(SlotTapStep.WAIT, ShellReadinessPolicy.slotTap(p, ShellReadinessPolicy.SLOT_WAIT_MS - 1))
            assertEquals(SlotTapStep.PROMPT, ShellReadinessPolicy.slotTap(p, ShellReadinessPolicy.SLOT_WAIT_MS))
        }
        assertEquals(TileHint.CONNECTING, ShellReadinessPolicy.tileHint(CHECKING))
        assertEquals(TileHint.NEEDS_ACCESS, ShellReadinessPolicy.tileHint(NEEDS_APPROVAL))
        assertEquals(TileHint.NO_CHANNEL, ShellReadinessPolicy.tileHint(ENVIRONMENT))
    }

    @Test
    fun `bien the the va tu hien mot lan khi HOME co tieu diem`() {
        assertEquals(AccessCardVariant.NEVER, ShellReadinessPolicy.cardVariant(ShellReadinessState(NEEDS_APPROVAL, lost = false), false))
        assertEquals(AccessCardVariant.LOST, ShellReadinessPolicy.cardVariant(ShellReadinessState(NEEDS_APPROVAL, lost = true), true))
        assertEquals(AccessCardVariant.ENVIRONMENT, ShellReadinessPolicy.cardVariant(ShellReadinessState(ENVIRONMENT), true))
        assertEquals(AccessCardVariant.LOST, ShellReadinessPolicy.cardVariant(ShellReadinessState(UNKNOWN), hadRecord = true))
        assertTrue(ShellReadinessPolicy.autoShowCard(NEEDS_APPROVAL, false, focused = true, interactive = true, shownThisProcess = false))
        assertFalse(ShellReadinessPolicy.autoShowCard(NEEDS_APPROVAL, false, focused = false, interactive = true, shownThisProcess = false),
            "không tiêu điểm = hộp hệ thống đang ở trên")
        assertFalse(ShellReadinessPolicy.autoShowCard(NEEDS_APPROVAL, false, focused = true, interactive = false, shownThisProcess = false),
            "[ĐO máy ảo 01/10] HOME vẫn có tiêu điểm lúc màn tắt — không tiêu lượt 'một lần' lúc xe tắt máy")
        assertFalse(ShellReadinessPolicy.autoShowCard(NEEDS_APPROVAL, false, true, true, shownThisProcess = true), "một lần mỗi tiến trình")
        assertFalse(ShellReadinessPolicy.autoShowCard(ENVIRONMENT, ledgerFresh = true, focused = true, interactive = true, shownThisProcess = false))
        assertTrue(ShellReadinessPolicy.autoShowCard(ENVIRONMENT, ledgerFresh = false, focused = true, interactive = true, shownThisProcess = false))
        assertFalse(ShellReadinessPolicy.autoShowCard(UNKNOWN, false, true, true, false), "chưa đo ⇒ chưa nói")
    }

    // ── Dấu bền: mã hoá + tiết chế ghi ─────────────────────────────────────────────────────────────────────

    @Test
    fun `ma hoa dau ben khu hoi va gia tri hong thi coi nhu chua tung`() {
        val a = approved()
        assertEquals(a, ShellApprovalLedger.decode(ShellApprovalLedger.encode(a)))
        val l = ShellApprovalLedger.Lost(t0)
        assertEquals(l, ShellApprovalLedger.decode(ShellApprovalLedger.encode(l)))
        listOf(null, "", "rác", "v=2;fp=$fp;ok=1;win=1", "v=1;fp=KHONGHEX;ok=1;win=1", "v=1;fp=$fp;ok=x;win=1").forEach {
            assertEquals(ShellApprovalLedger.None, ShellApprovalLedger.decode(it), "'$it'")
        }
        assertEquals(ShellApprovalLedger.None, ShellApprovalLedger.forgotten(ShellApprovalLedger.None, t0), "chưa từng ⇒ vẫn chưa từng")
        assertEquals(ShellApprovalLedger.Lost(t0), ShellApprovalLedger.forgotten(a, t0))
        assertTrue(ShellApprovalLedger.hadRecord(l))
    }

    @Test
    fun `khong ghi dau moi lan noi - chi khi doi van tay, doi han hoac cu hon 6 gio`() {
        val win = 7 * day
        assertTrue(ShellReadinessPolicy.shouldTouch(ShellApprovalLedger.None, fp, t0, win))
        assertFalse(ShellReadinessPolicy.shouldTouch(approved(), fp, t0 + 1_000, win))
        assertTrue(ShellReadinessPolicy.shouldTouch(approved(), fp, t0 + ShellReadinessPolicy.TOUCH_GAP_MS, win))
        assertTrue(ShellReadinessPolicy.shouldTouch(approved(), "ab".repeat(32), t0 + 1, win))
        assertTrue(ShellReadinessPolicy.shouldTouch(approved(), fp, t0 + 1, 0L), "hạn mới đọc từ máy ⇒ ghi lại")
        assertTrue(ShellReadinessPolicy.shouldTouch(approved(), fp, t0 - 1, win), "giờ lùi ⇒ ghi lại mốc")
    }

    @Test
    fun `doc han duyet tu settings get`() {
        assertEquals(604_800_000L, ShellReadinessPolicy.parseWindow("604800000\n"))
        assertEquals(0L, ShellReadinessPolicy.parseWindow("0"))
        assertNull(ShellReadinessPolicy.parseWindow("null"))
        assertNull(ShellReadinessPolicy.parseWindow(null))
        assertNull(ShellReadinessPolicy.parseWindow("-5"))
    }
}
