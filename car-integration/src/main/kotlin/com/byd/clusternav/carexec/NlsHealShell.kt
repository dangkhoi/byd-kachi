package com.byd.clusternav.carexec

import com.byd.clusternav.navigation.NlsHealPolicy
import com.byd.clusternav.navigation.NlsHealPolicy.Outcome
import com.byd.clusternav.navigation.NlsLiveDump
import dadb.AdbKeyPair

/**
 * ═══ FIX286 R-HUD — MỘT phiên shell của lượt gắn lại nguồn thông báo (tầng lệnh, CLAUDE.md §14 bước 2) ════════════════
 *
 * Chuỗi lệnh (khoá ở `NlsHealShellTest` bằng một `sh` giả, mẫu `SetHomeActivityTest`):
 *  1. `dumpsys notification p <gói>` → [NlsLiveDump.verdict] (mọi lượt). [checkFirst] ⇒ LIVE thì DỪNG, không lệnh ghi
 *     nào; UNREADABLE thì DỪNG (không đoán — CLAUDE.md §2); chỉ NOT_LIVE mới đi tiếp. Lượt TỰ ĐỘNG luôn [checkFirst];
 *     lượt người dùng bấm (công tắc / *Kết nối lại*) giữ hành vi đã chạy ngoài hiện trường: bắn luôn (CLAUDE.md §6).
 *  2. [fireGate] hỏi lại NGAY trước khi ghi (công tắc còn bật? màn còn sáng?) — giữa lần đọc và đây có thể đã trôi.
 *  3. Cặp lệnh TÁCH RỜI [NlsHealPolicy.toggleCommand] (disallow → 1,5 s → allow, một lần gửi).
 *  4. Chờ 1,5 s + [awaitBound] (callback `onListenerConnected` MỚI, tối đa [NlsHealPolicy.VERIFY_WAIT_MS]) rồi ĐỌC LẠI
 *     dump — kết quả báo lên là sự thật của NMS, không phải "đã gửi lệnh" (2.85 in "xong" cả khi phiên bị chặn).
 */
object NlsHealShell {

    /** Kết quả một lượt; [fired] = cặp lệnh đã rời tiến trình (đếm vào trần kể cả khi phiên hỏng sau đó). */
    data class Run(
        val outcome: Outcome,
        val fired: Boolean,
        val before: NlsLiveDump.Verdict?,
        val after: NlsLiveDump.Verdict?,
    )

    /**
     * @param retry `LocalShellRetry.BACKGROUND_READ_CAP` cho lượt tự động (qua cổng READY-AT-HOME), `USER_READ_CAP` cho
     *   lượt người dùng vừa bấm (hộp "Cho phép gỡ lỗi USB?" bung đúng lúc họ đang nhìn — READY-AT-HOME R1.1).
     */
    fun run(
        keys: AdbKeyPair,
        retry: LocalShellRetry,
        component: String,
        ownPackage: String,
        checkFirst: Boolean,
        fireGate: () -> Boolean,
        awaitBound: (Long) -> Boolean,
    ): Run {
        var fired = false
        val result = LocalDeviceShell.sessionResult(keys, retry) { sh ->
            block(component, ownPackage, checkFirst, fireGate, awaitBound, Thread::sleep, { fired = true }, sh)
        }
        return when (result) {
            is LocalShellResult.Ok -> result.value
            is LocalShellResult.Failed -> Run(NlsHealPolicy.fromShellFailure(result.reason), fired, null, null)
        }
    }

    /** Lượt CHỈ ĐỌC (dòng tình trạng ở Cài đặt) — một lệnh `dumpsys`, không bao giờ ghi. */
    fun read(keys: AdbKeyPair, retry: LocalShellRetry, component: String, ownPackage: String): Run =
        when (val r = LocalDeviceShell.sessionResult(keys, retry) { sh -> readBlock(component, ownPackage, sh) }) {
            is LocalShellResult.Ok -> r.value
            is LocalShellResult.Failed -> Run(NlsHealPolicy.fromShellFailure(r.reason), false, null, null)
        }

    internal fun readBlock(component: String, ownPackage: String, sh: (String) -> LocalShellText): Run {
        val v = NlsLiveDump.verdict(sh(NlsLiveDump.command(ownPackage)).output, component)
        val outcome = when (v) {
            NlsLiveDump.Verdict.LIVE -> Outcome.LIVE_ALREADY
            NlsLiveDump.Verdict.NOT_LIVE -> Outcome.NOT_LIVE
            NlsLiveDump.Verdict.UNREADABLE -> Outcome.UNREADABLE
        }
        return Run(outcome, false, v, null)
    }

    internal fun block(
        component: String,
        ownPackage: String,
        checkFirst: Boolean,
        fireGate: () -> Boolean,
        awaitBound: (Long) -> Boolean,
        sleepMs: (Long) -> Unit,
        onFired: () -> Unit,
        sh: (String) -> LocalShellText,
    ): Run {
        val dump = NlsLiveDump.command(ownPackage)
        // LUÔN đọc trước (cả lượt bấm tay): (a) phiên nối lười ⇒ kênh hỏng thì hỏng NGAY ở lệnh chỉ-đọc, cặp lệnh chưa gửi
        // ⇒ [Run.fired] đúng sự thật (đo máy ảo 03/10: bỏ `adb reverse` ⇒ lượt bấm tay báo đã bắn dù chưa gửi gì);
        // (b) nhật ký có trạng thái TRƯỚC. Chỉ lượt tự động ([checkFirst]) mới DỪNG theo kết quả đọc.
        val before = NlsLiveDump.verdict(sh(dump).output, component)
        if (checkFirst) when (before) {
            NlsLiveDump.Verdict.LIVE -> return Run(Outcome.LIVE_ALREADY, false, before, null)
            NlsLiveDump.Verdict.UNREADABLE -> return Run(Outcome.UNREADABLE, false, before, null)
            NlsLiveDump.Verdict.NOT_LIVE -> Unit
        }
        val toggle = NlsHealPolicy.toggleCommand(component, ownPackage)
        if (toggle.isEmpty() || !fireGate()) return Run(Outcome.SKIPPED, false, before, null)
        onFired()
        sh(toggle)
        sleepMs(NlsHealPolicy.TOGGLE_PAUSE_MS + SETTLE_MARGIN_MS)
        awaitBound(NlsHealPolicy.VERIFY_WAIT_MS)
        val after = NlsLiveDump.verdict(sh(dump).output, component)
        val outcome = if (after == NlsLiveDump.Verdict.LIVE) Outcome.HEALED else Outcome.STILL_NOT_LIVE
        return Run(outcome, true, before, after)
    }

    /** Biên sau 1,5 s của lệnh tách rời: `allow` đã chạy rồi mới bắt đầu chờ callback MỚI. */
    private const val SETTLE_MARGIN_MS = 500L
}
