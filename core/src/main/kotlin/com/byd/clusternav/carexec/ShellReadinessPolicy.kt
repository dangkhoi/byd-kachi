package com.byd.clusternav.carexec

/**
 * ═══ READY-AT-HOME — CHÍNH SÁCH THUẦN của kênh shell mức TIẾN TRÌNH ═══════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-ready-at-home.html` §4.4–§4.9. Owner 2026-10-01: *"Xe chưa từng cho phép phải hiện popup yêu
 * cầu, không có quyền, không dùng đc app. Xe đã cấp phép thì mình phải tự động nối ngay bước đầu tiên, khi lên HOME
 * thì mọi thứ phải ready, bao gồm kiểm tra binding phím"*.
 *
 * ## Chế độ đang chạy: DỰ PHÒNG (chỉ dấu bền) — §4.4.3 cột phải
 * Cơ chế "dò im lặng" (§4.3) là cơ chế MỚI ở tầng giao thức ⇒ CLAUDE.md §14 đòi bằng chứng shell thô trên máy ảo
 * `ro.adb.secure=1` (T0a) và trên xe (T0b) TRƯỚC khi viết mã. Chưa có ⇒ không có mã cho nó; mọi quyết định ở đây chỉ
 * dựa vào (a) phép đo trong chính tiến trình (một phiên bắt tay xong / hỏng ở bắt tay) và (b) dấu bền theo xe.
 *
 * ## Vì sao "dấu bền" chỉ là GỢI Ý, còn quyết định là PHÉP ĐO (CLAUDE.md §5)
 * Duyệt khoá adb KHÔNG vĩnh viễn [ĐO AOSP android-10.0.0_r47 `AdbDebuggingManager.java:1009-1027, 1123-1141`;
 * `Settings.java:12393` mặc định 604 800 000 ms = 7 ngày; xe 14/09 in `authWindow = 604800000`]: khoá không nối lại
 * trong hạn thì bị gỡ khỏi `adb_keys`. Người dùng cũng có thể thu hồi. Nên dấu chỉ được dùng để chọn **có thử nối sớm
 * không** và **ô nên nói gì trong vài giây đầu**; trạng thái kênh ([ShellChannelPhase]) chỉ đổi bởi một phép đo.
 *
 * ## Hộp "Cho phép gỡ lỗi USB?" bung khi nào [ĐO AOSP — spec §2.5]
 * adbd chỉ hỏi người dùng khi client gửi KHOÁ CÔNG KHAI (`adb.cpp:337-356`, `daemon/auth.cpp:169-185`), và dadb 2.0.0
 * LUÔN gửi khoá công khai sau khi chữ ký bị từ chối [ĐO javap `AdbConnection$Companion.connect`]. ⇒ Mọi lần nối bằng
 * khoá chưa được nhận đều dựng hộp. Vì thế ở chế độ dự phòng, đường NỀN chỉ được nối khi kênh đã ĐO là lên trong tiến
 * trình này, hoặc dấu còn tươi (rủi ro còn lại ghi ở §4.4.3: dấu tươi mà khoá đã bị thu hồi ⇒ một hộp có thể bung lúc
 * HOME chưa ở trước; lượt sớm sẽ nhận ra và xoá dấu).
 */
enum class ShellChannelPhase {
    /** Tiến trình vừa bật, lượt sớm CHƯA quyết có nối hay không (vài ms). Đường nền gặp pha này thì CHỜ (luồng nền). */
    STARTING,

    /** Đã quyết không nối sớm (không có dấu tươi) và chưa có phép đo nào — chờ F4 ở màn chính đo. */
    UNKNOWN,

    /** Lượt sớm đang nối (dấu tươi). Đường nền CHỜ tối đa [ShellReadinessPolicy.EARLY_WAIT_MS]. */
    CHECKING,

    /** Một phiên đã bắt tay xong TRONG tiến trình này — khoá được nhận. */
    UP,

    /** Một phiên HỎI đã hỏng ngay ở bắt tay vì adbd đang hỏi / từ chối — khoá chưa (hoặc không còn) được nhận. */
    NEEDS_APPROVAL,

    /** Lỗi môi trường (cổng câm, đứt) — không phải chuyện người dùng bấm được. */
    ENVIRONMENT,
}

/**
 * Trạng thái kênh mức tiến trình. [lost] chỉ có nghĩa ở [ShellChannelPhase.NEEDS_APPROVAL]: `true` = xe TỪNG duyệt khoá
 * này (có dấu), nay không còn — MẤT DUYỆT; `false` = CHƯA TỪNG. [reason] chỉ có nghĩa ở [ShellChannelPhase.ENVIRONMENT].
 */
data class ShellReadinessState(
    val phase: ShellChannelPhase,
    val lost: Boolean = false,
    val reason: LocalShellFailure? = null,
) {
    companion object {
        val STARTING = ShellReadinessState(ShellChannelPhase.STARTING)
    }
}

/** Sự kiện ĐO được làm đổi trạng thái — xem bảng [ShellReadinessPolicy.next]. */
sealed interface ReadyEvent {
    /** Lượt sớm quyết nối (dấu tươi). */
    object EarlyStart : ReadyEvent

    /** Lượt sớm quyết KHÔNG nối (không có dấu tươi) ⇒ chờ F4. */
    object EarlySkipped : ReadyEvent

    /** Một phiên đã bắt tay xong (lượt sớm, F4, phiên `LocalDeviceShell`, `ShellTransport`, `installApk`). */
    object Up : ReadyEvent

    /** Một phiên HỎI hỏng ngay ở bắt tay với `AWAITING_APPROVAL`/`AUTH_REJECTED` ⇒ xoá dấu. */
    object Revoked : ReadyEvent

    /** F4 phân loại "hệ thống đang hỏi người dùng" (dấu đã do [Revoked] lo). */
    object AwaitingUser : ReadyEvent

    /** Lỗi môi trường đã hết lượt thử (lượt sớm) hoặc F4 phân loại môi trường. */
    data class Environment(val reason: LocalShellFailure) : ReadyEvent
}

/** Việc phải làm với dấu bền sau một lần chuyển trạng thái. */
enum class LedgerOp { NONE, MARK_UP, FORGET }

/** Kết quả [ShellReadinessPolicy.next]. */
data class ReadyTransition(val state: ShellReadinessState, val ledger: LedgerOp)

/** Loại phiên: [ASK] = đường HỎI người dùng (F4, nút bấm) — luôn cho; [BACKGROUND] = mọi đường khác. */
enum class ShellSessionKind { ASK, BACKGROUND }

/** Quyết định của cổng thi hành cho MỘT lần nối. */
enum class Admission { ALLOW, DENY, WAIT }

/** Lượt sớm nên làm gì (chế độ dự phòng). */
enum class EarlyPlan { CONNECT, GATE_NEVER, GATE_LOST, GATE_KEY_CHANGED, GATE_STALE }

/** Sau một lượt dò sớm. */
sealed interface EarlyStep {
    object Up : EarlyStep
    object Revoked : EarlyStep
    data class Retry(val delayMs: Long) : EarlyStep
    data class GiveUp(val reason: LocalShellFailure) : EarlyStep
}

/** Chạm ô khi ô chưa có bộ chiếu. */
enum class SlotTapStep { WAIT, PROMPT }

/** Chữ thay cho "Chạm để mở" trên thẻ ô (§4.9). */
enum class TileHint { CONNECTING, NEEDS_ACCESS, NO_CHANNEL }

/** Biến thể lời của thẻ xin quyền (§4.9). */
enum class AccessCardVariant { NEVER, LOST, ENVIRONMENT }

object ShellReadinessPolicy {

    /** Đường nền gặp [ShellChannelPhase.STARTING]/[ShellChannelPhase.CHECKING] chờ tối đa chừng này (luồng nền). */
    const val EARLY_WAIT_MS = 8_000L

    /** Lượt sớm: tổng số lần thử khi lỗi MÔI TRƯỜNG (giãn 1 s rồi 2 s — R3.3). */
    const val ENV_ATTEMPTS = 3

    /** Giãn cách sau lần hỏng môi trường đầu; lần sau nhân đôi. */
    const val ENV_FIRST_BACKOFF_MS = 1_000L

    /** Không ghi lại dấu mỗi lệnh: chỉ khi vân tay/hạn đổi hoặc mốc đã cũ hơn chừng này. */
    const val TOUCH_GAP_MS = 6 * 3_600_000L

    /** Chạm ô lúc kênh còn đang dò: ô chờ chừng này rồi mới hiện thẻ (R2.4). */
    const val SLOT_WAIT_MS = 10_000L

    /** Hạn duyệt mặc định khi chưa đọc được từ máy — `Settings.java:12393` (7 ngày). */
    const val DEFAULT_WINDOW_MS = 7L * 24 * 3_600_000L

    /** Lề an toàn trước hạn: min(24 h, hạn/2) — thận trọng hơn framework (§4.4.2). */
    private const val MAX_MARGIN_MS = 24L * 3_600_000L

    // ─── Dấu bền ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Dấu đã tươi chưa (§4.4.2). Tươi khi khoá trùng vân tay VÀ (hạn = 0 ⇒ không bao giờ hết — `:1135`; hoặc
     * `0 ≤ now − ok < win − margin`). Giờ tường LÙI (`now < ok`) ⇒ KHÔNG tươi — framework coi là còn hạn
     * (`:1135-1136`), ta thận trọng hơn vì chỉ tốn một lượt F4.
     */
    fun fresh(ledger: ShellApprovalLedger, curFp: String?, nowWallMs: Long): Boolean {
        if (ledger !is ShellApprovalLedger.Approved || curFp.isNullOrEmpty() || ledger.fp != curFp) return false
        val win = if (ledger.windowMs < 0) DEFAULT_WINDOW_MS else ledger.windowMs
        if (win == 0L) return true
        val age = nowWallMs - ledger.okWallMs
        if (age < 0) return false
        val margin = minOf(MAX_MARGIN_MS, win / 2)
        return age < win - margin
    }

    /** Lượt sớm có nối không (chế độ dự phòng: CHỈ khi dấu tươi). */
    fun earlyPlan(ledger: ShellApprovalLedger, curFp: String?, nowWallMs: Long): EarlyPlan = when {
        fresh(ledger, curFp, nowWallMs) -> EarlyPlan.CONNECT
        ledger is ShellApprovalLedger.None -> EarlyPlan.GATE_NEVER
        ledger is ShellApprovalLedger.Lost -> EarlyPlan.GATE_LOST
        ledger is ShellApprovalLedger.Approved && ledger.fp != curFp -> EarlyPlan.GATE_KEY_CHANGED
        else -> EarlyPlan.GATE_STALE
    }

    /** Có cần ghi lại dấu "lên" không — tránh ghi đĩa mỗi lần nối (§4.4.1). */
    fun shouldTouch(ledger: ShellApprovalLedger, curFp: String, nowWallMs: Long, windowMs: Long): Boolean {
        if (ledger !is ShellApprovalLedger.Approved) return true
        if (ledger.fp != curFp || ledger.windowMs != windowMs) return true
        val age = nowWallMs - ledger.okWallMs
        return age < 0 || age >= TOUCH_GAP_MS
    }

    /**
     * Hạn duyệt đọc từ `settings get global adb_allowed_connection_time` (lệnh CHỈ ĐỌC — R-nf1). Không đọc được /
     * `null` / âm ⇒ `null` (bên gọi dùng [DEFAULT_WINDOW_MS]). `0` hợp lệ = không bao giờ hết hạn.
     */
    fun parseWindow(output: String?): Long? {
        val v = output?.trim()?.lines()?.firstOrNull()?.trim()?.toLongOrNull() ?: return null
        return v.takeIf { it >= 0 }
    }

    // ─── Lượt sớm ────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Kết quả MỘT lượt dò sớm ([failure] = `null` ⇒ lên) ⇒ bước tiếp (R3.1–R3.3).
     *  • `AWAITING_APPROVAL`/`AUTH_REJECTED` ⇒ [EarlyStep.Revoked]: xoá dấu, KHÔNG thử lại từ nền (F4 hỏi đúng lúc).
     *  • lỗi môi trường ⇒ thử lại tới [ENV_ATTEMPTS] lần (giãn 1 s, 2 s), hết thì [EarlyStep.GiveUp] — GIỮ dấu.
     *  • `NOT_APPROVED` không thể tới đây (lượt dò là phiên HỎI, luôn được cho) — coi như môi trường, không đoán.
     */
    fun afterEarly(failure: LocalShellFailure?, attempt: Int): EarlyStep = when (failure) {
        null -> EarlyStep.Up
        LocalShellFailure.AWAITING_APPROVAL, LocalShellFailure.AUTH_REJECTED -> EarlyStep.Revoked
        else ->
            if (attempt < ENV_ATTEMPTS) EarlyStep.Retry(ENV_FIRST_BACKOFF_MS shl (attempt - 1).coerceIn(0, 4))
            else EarlyStep.GiveUp(failure)
    }

    // ─── Bảng chuyển trạng thái (§4.5) ───────────────────────────────────────────────────────────────────

    /**
     * @param hadRecord xe đã từng có dấu (sống hoặc bia mộ) — quyết CHƯA TỪNG vs MẤT DUYỆT.
     */
    fun next(state: ShellReadinessState, event: ReadyEvent, hadRecord: Boolean): ReadyTransition = when (event) {
        ReadyEvent.EarlyStart ->
            if (state.phase == ShellChannelPhase.STARTING || state.phase == ShellChannelPhase.UNKNOWN) {
                ReadyTransition(ShellReadinessState(ShellChannelPhase.CHECKING), LedgerOp.NONE)
            } else ReadyTransition(state, LedgerOp.NONE)
        ReadyEvent.EarlySkipped ->
            if (state.phase == ShellChannelPhase.STARTING) {
                ReadyTransition(ShellReadinessState(ShellChannelPhase.UNKNOWN), LedgerOp.NONE)
            } else ReadyTransition(state, LedgerOp.NONE)
        ReadyEvent.Up -> ReadyTransition(ShellReadinessState(ShellChannelPhase.UP), LedgerOp.MARK_UP)
        ReadyEvent.Revoked ->
            ReadyTransition(ShellReadinessState(ShellChannelPhase.NEEDS_APPROVAL, lost = hadRecord), LedgerOp.FORGET)
        ReadyEvent.AwaitingUser ->
            ReadyTransition(ShellReadinessState(ShellChannelPhase.NEEDS_APPROVAL, lost = hadRecord), LedgerOp.NONE)
        // Lỗi lệnh / đứt KHÔNG có nghĩa là mất duyệt: đã UP thì giữ UP (bảng §4.5 hàng cuối).
        // Review lượt 1 [P3]: F4 xếp AUTH_REJECTED vào nhánh MÔI TRƯỜNG (để không hỏi lại người vừa bấm Từ chối) — nhưng
        // chính phiên dò đó đã báo THU HỒI ⇒ NEEDS_APPROVAL. Một lý do thuộc loại DUYỆT không được hạ trạng thái đó xuống
        // MÔI TRƯỜNG: thẻ sẽ nói "cổng gỡ lỗi chưa sẵn sàng" với người vừa từ chối, ô ghi "Chưa có kênh" thay "Cần cấp quyền".
        is ReadyEvent.Environment -> when {
            state.phase == ShellChannelPhase.UP -> ReadyTransition(state, LedgerOp.NONE)
            state.phase == ShellChannelPhase.NEEDS_APPROVAL && approvalReason(event.reason) -> ReadyTransition(state, LedgerOp.NONE)
            else -> ReadyTransition(ShellReadinessState(ShellChannelPhase.ENVIRONMENT, reason = event.reason), LedgerOp.NONE)
        }
    }

    /** Lý do thuộc loại DUYỆT (adbd đang hỏi / từ chối khoá) — khác lỗi môi trường (cổng câm, đứt, không rõ). */
    private fun approvalReason(r: LocalShellFailure): Boolean =
        r == LocalShellFailure.AWAITING_APPROVAL || r == LocalShellFailure.AUTH_REJECTED

    /** F4 gặp MÔI TRƯỜNG ⇒ hẹn dò lại sau chừng này (xem [envReprobeMs]). Cùng nhịp 20 s của vòng F4 chờ người bấm. */
    const val ENV_REPROBE_MS = 20_000L

    /**
     * F4 vừa phân loại MÔI TRƯỜNG ⇒ có hẹn lượt dò sau không (`0` = không). Review lượt 1 [P3] (E2E 02/10 ca 3a): trước
     * READY-AT-HOME, cổng 5555 mở lại thì mọi đường nền tự nối được; nay cổng thi hành CHẶN đường nền khi không có dấu tươi
     * ⇒ F4 là đường DUY NHẤT còn đo lại được, mà F4 không hẹn lượt sau ở nhánh này ⇒ phải bấm *Thử lại* bằng tay.
     * Chỉ [LocalShellFailure.PORT_CLOSED]: không có gì lắng nghe ⇒ không có kết nối nào tới adbd ⇒ không thể dựng hộp, dò
     * bao nhiêu lần cũng không làm phiền ai. [LocalShellFailure.AUTH_REJECTED] KHÔNG (người vừa bấm Từ chối — F4 cố ý
     * không hỏi lại); `IO_ERROR`/`UNKNOWN` KHÔNG (một số ROM đóng socket với khoá lạ — có thể đã dựng hộp).
     *
     * Review lượt 2 [P3]: KHÔNG còn miễn ca "có dấu tươi". Lý do lượt 1 ghi ("đường nền được cho và tự nối khi cổng mở")
     * đúng cho đường NỀN nhưng không đưa màn chính lên: HOME chỉ nối dây qua F4 hoặc `adopt` (khi một phiên báo UP), mà
     * trước khi HOME nối dây không có phiên nền định kỳ nào chắc chắn chạy [ĐO grep: watchdog phím chỉ cấp khi binder nói
     * CHƯA gắn; `scheduleWatchdog`/keep-alive chỉ khởi sau khi kênh đã lên]. ⇒ cổng mở muộn (vd adbd TCP lên sau HOME)
     * mà có dấu tươi thì ô nằm "Chưa có kênh" tới khi người dùng bấm tay. Dò lại PORT_CLOSED vô hại ở cả hai ca.
     */
    fun envReprobeMs(reason: LocalShellFailure): Long =
        if (reason == LocalShellFailure.PORT_CLOSED) ENV_REPROBE_MS else 0L

    /**
     * Một phiên vừa xong ⇒ sự kiện nào (§4.6 "báo kết quả ngược lên"):
     *  • bắt tay xong ⇒ [ReadyEvent.Up] (kể cả khi lệnh sau đó hỏng — khoá ĐÃ được nhận);
     *  • phiên HỎI, ép bắt tay, hỏng TRƯỚC khi gửi lệnh với `AWAITING_APPROVAL`/`AUTH_REJECTED` ⇒ [ReadyEvent.Revoked];
     *  • còn lại ⇒ `null`. Hỏng SAU khi lệnh đã gửi thì không bao giờ xoá dấu: hết hạn đọc giữa một lệnh dài cũng phân
     *    loại là `AWAITING_APPROVAL` (`LocalShellFailures.classify`) dù khoá vẫn đang được nhận.
     */
    fun outcomeEvent(
        kind: ShellSessionKind,
        handshook: Boolean,
        failure: LocalShellFailure?,
        dispatched: Boolean,
        eagerHandshake: Boolean,
    ): ReadyEvent? = when {
        handshook -> ReadyEvent.Up
        kind == ShellSessionKind.ASK && eagerHandshake && !dispatched &&
            (failure == LocalShellFailure.AWAITING_APPROVAL || failure == LocalShellFailure.AUTH_REJECTED) -> ReadyEvent.Revoked
        else -> null
    }

    // ─── Cổng thi hành (§4.6, cột chế độ dự phòng) ───────────────────────────────────────────────────────

    /**
     * @param canWait `false` khi gọi từ luồng chính (R-nf3: không bao giờ chờ trên luồng chính).
     */
    fun admit(kind: ShellSessionKind, phase: ShellChannelPhase, ledgerFresh: Boolean, canWait: Boolean): Admission = when {
        kind == ShellSessionKind.ASK -> Admission.ALLOW
        phase == ShellChannelPhase.UP -> Admission.ALLOW
        phase == ShellChannelPhase.NEEDS_APPROVAL -> Admission.DENY
        canWait && (phase == ShellChannelPhase.STARTING || phase == ShellChannelPhase.CHECKING) -> Admission.WAIT
        ledgerFresh -> Admission.ALLOW
        else -> Admission.DENY
    }

    /** Tính năng cần kênh có được thử ngay không (điểm vào UI — không chờ). Cùng luật với [admit] cho đường nền. */
    fun usable(phase: ShellChannelPhase, ledgerFresh: Boolean): Boolean =
        admit(ShellSessionKind.BACKGROUND, phase, ledgerFresh, canWait = false) == Admission.ALLOW

    // ─── HOME + giao diện (§4.7, §4.9) ───────────────────────────────────────────────────────────────────

    /**
     * HOME nhận kênh đã sẵn ([ShellChannelGate.adopt]) — đủ năm điều kiện, cộng "không có lượt F4 đang bay" (lượt
     * đang bay tự báo về và tự chạy đường nối dây — nhận thêm ở đây là chạy đường đó HAI lần).
     * [interactive] — ở lượt BYD dựng lại Kachi lúc màn tắt, HOME đi create→resume→stop trong 0,3 s [ĐO c2-logcat
     * :3558-3582]; nhận kênh lúc đó là app ô (có tiếng) chạy lúc xe tắt.
     */
    fun adoptAllowed(
        up: Boolean,
        framed: Boolean,
        showing: Boolean,
        interactive: Boolean,
        channelUp: Boolean,
        inFlight: Boolean,
    ): Boolean = up && framed && showing && interactive && !channelUp && !inFlight

    /**
     * Chạm ô chưa có bộ chiếu (R1.3/R2.4). Kênh đã ĐO là không có ⇒ thẻ ngay. Kênh đang dò / chưa đo / vừa lên mà
     * HOME chưa nhận ⇒ CHỜ (state ô đã lưu, kênh lên là `applyEmbedSeam` tự nhúng), quá [SLOT_WAIT_MS] mới hiện thẻ.
     * Không bao giờ mở cửa sổ nổi ở ca nào (owner R1: không quyền thì không dùng được app).
     */
    fun slotTap(phase: ShellChannelPhase, waitedMs: Long): SlotTapStep = when (phase) {
        ShellChannelPhase.NEEDS_APPROVAL, ShellChannelPhase.ENVIRONMENT -> SlotTapStep.PROMPT
        else -> if (waitedMs < SLOT_WAIT_MS) SlotTapStep.WAIT else SlotTapStep.PROMPT
    }

    /** Chữ trên thẻ ô chưa có bộ chiếu. */
    fun tileHint(phase: ShellChannelPhase): TileHint = when (phase) {
        ShellChannelPhase.NEEDS_APPROVAL -> TileHint.NEEDS_ACCESS
        ShellChannelPhase.ENVIRONMENT -> TileHint.NO_CHANNEL
        else -> TileHint.CONNECTING
    }

    /** Biến thể thẻ xin quyền: MẤT DUYỆT khi xe từng có dấu, CHƯA TỪNG khi chưa; môi trường khi không phải chuyện duyệt. */
    fun cardVariant(state: ShellReadinessState, hadRecord: Boolean): AccessCardVariant = when (state.phase) {
        ShellChannelPhase.NEEDS_APPROVAL -> if (state.lost) AccessCardVariant.LOST else AccessCardVariant.NEVER
        ShellChannelPhase.ENVIRONMENT, ShellChannelPhase.UP -> AccessCardVariant.ENVIRONMENT
        else -> if (hadRecord) AccessCardVariant.LOST else AccessCardVariant.NEVER
    }

    /**
     * Thẻ tự hiện (một lần mỗi tiến trình) khi HOME có tiêu điểm — tức hộp hệ thống KHÔNG ở trên — lúc màn TƯƠNG TÁC,
     * và kênh đã ĐO là cần duyệt, hoặc lỗi môi trường mà không có dấu tươi (§4.9). [ĐO máy ảo 01/10] cửa sổ HOME vẫn
     * "có tiêu điểm" lúc màn tắt (lượt BYD dựng lại Kachi) — thiếu điều kiện màn tương tác thì lượt "một lần" bị tiêu
     * ngay lúc xe tắt máy.
     */
    fun autoShowCard(
        phase: ShellChannelPhase,
        ledgerFresh: Boolean,
        focused: Boolean,
        interactive: Boolean,
        shownThisProcess: Boolean,
    ): Boolean =
        focused && interactive && !shownThisProcess &&
            (phase == ShellChannelPhase.NEEDS_APPROVAL || (phase == ShellChannelPhase.ENVIRONMENT && !ledgerFresh))
}
