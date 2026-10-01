package com.byd.clusternav

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.carexec.Admission
import com.byd.clusternav.carexec.LocalShellAdmission
import com.byd.clusternav.carexec.LedgerOp
import com.byd.clusternav.carexec.ReadyEvent
import com.byd.clusternav.carexec.ShellApprovalLedger
import com.byd.clusternav.carexec.ShellChannelPhase
import com.byd.clusternav.carexec.ShellReadinessPolicy
import com.byd.clusternav.carexec.ShellReadinessState
import com.byd.clusternav.carexec.ShellSessionKind
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * ═══ READY-AT-HOME §4.5/§4.6 — TRẠNG THÁI KÊNH SHELL MỨC TIẾN TRÌNH + móc cổng thi hành ═══════════════════════════
 *
 * Chỉ ĐỔI bởi phép đo trong chính tiến trình (một phiên bắt tay xong / một phiên hỏi bị adbd hỏi lại / lượt sớm hết
 * lượt thử) — luật chuyển ở [ShellReadinessPolicy.next] (thuần, test ở `:car-integration`). Dấu bền ([ShellApprovalStore])
 * chỉ là GỢI Ý cho cổng ở chế độ dự phòng và cho chữ trên ô (CLAUDE.md §5: quyết bằng sự thật, không bằng cờ).
 *
 * Là móc [LocalShellAdmission.Hook]: cài ở `KachiApplication.onCreate` TRƯỚC `A11yLifecycleHeal.install` để phiên tự
 * chữa đầu tiên của tiến trình cũng đi qua cổng. Không chạm đĩa lúc cài.
 *
 * ⚠ Mọi ngoại lệ trong móc phải bị bắt ở đây: móc chạy GIỮA một phiên shell của bên gọi (luồng tự chữa, luồng cửa sổ).
 */
internal object ShellReadiness : LocalShellAdmission.Hook {

    private const val TAG = KachiReadyLog.TAG

    // lint StaticFieldLeak: chỉ giữ `applicationContext` (sống bằng chính tiến trình) — không bao giờ một Activity/View.
    @SuppressLint("StaticFieldLeak")
    @Volatile private var app: Context? = null
    private val lock = ReentrantLock()
    private val settled = lock.newCondition()
    @Volatile private var state: ShellReadinessState = ShellReadinessState.STARTING
    private val listeners = CopyOnWriteArraySet<(ShellReadinessState) -> Unit>()

    /** Hạn duyệt đọc từ máy (một lần mỗi tiến trình, [setWindow]); `-1` = chưa đọc ⇒ mặc định 7 ngày. */
    @Volatile private var windowMs: Long = -1L

    private val store: ShellApprovalStore? get() = app?.let { ShellApprovalStore(it) }

    /** Móc DUY NHẤT, gọi ở `KachiApplication.onCreate` (tiến trình launcher). Không I/O. */
    fun install(ctx: Context) {
        app = ctx.applicationContext
        LocalShellAdmission.install(this)
    }

    fun state(): ShellReadinessState = state
    fun phase(): ShellChannelPhase = state.phase
    fun isUp(): Boolean = state.phase == ShellChannelPhase.UP

    /** Hàng quyền ở Cài đặt nói "việc của người dùng" khi kênh đã ĐO là cần duyệt (§4.9). */
    fun needsApproval(): Boolean = state.phase == ShellChannelPhase.NEEDS_APPROVAL

    fun addListener(l: (ShellReadinessState) -> Unit) { listeners.add(l) }
    fun removeListener(l: (ShellReadinessState) -> Unit) { listeners.remove(l) }

    // ─── Dấu bền (gợi ý) ─────────────────────────────────────────────────────────────────────────────────

    fun ledger(): ShellApprovalLedger = guardedRead { store?.read() } ?: ShellApprovalLedger.None

    fun ledgerFresh(): Boolean {
        val fp = fingerprint() ?: return false
        return ShellReadinessPolicy.fresh(ledger(), fp, System.currentTimeMillis())
    }

    /**
     * Vân tay khoá đang dùng — đọc MỘT lần mỗi tiến trình (đọc + parse khoá + SHA-256 là đắt so với nhịp lệnh cửa sổ).
     * Khoá sinh lại giữa chừng (hiếm) ⇒ vân tay cũ ⇒ tiến trình sau thấy lệch ⇒ không tươi ⇒ đi F4: lệch về phía AN TOÀN.
     */
    @Volatile private var fpCache: String? = null

    fun fingerprint(): String? = fpCache ?: app?.let { AdbKeys.fingerprint(it) }?.also { fpCache = it }

    fun hadRecord(): Boolean = ShellApprovalLedger.hadRecord(ledger())

    /** Hạn duyệt vừa đọc từ máy ⇒ ghi lại dấu với hạn đúng (chỉ khi kênh đang lên). */
    fun setWindow(ms: Long) {
        windowMs = ms
        lock.withLock { if (isUp()) touchLedger() }
    }

    // ─── Chuyển trạng thái ────────────────────────────────────────────────────────────────────────────────

    fun apply(event: ReadyEvent, src: String) {
        val before: ShellReadinessState
        val after: ShellReadinessState
        lock.withLock {
            before = state
            val t = ShellReadinessPolicy.next(before, event, hadRecord())
            when (t.ledger) {
                LedgerOp.MARK_UP -> touchLedger()
                LedgerOp.FORGET -> forgetLedger(src)
                LedgerOp.NONE -> Unit
            }
            state = t.state
            after = t.state
            settled.signalAll()
        }
        if (after == before) return
        if (after.phase == ShellChannelPhase.UP) KachiReadyLog.up(src)
        KachiReadyLog.line(describe(after) + " src=$src")
        listeners.forEach { l ->
            try { l(after) } catch (e: RuntimeException) { Log.e(TAG, "bên nghe trạng thái kênh lỗi", e) }
        }
    }

    fun reportUp(src: String) = apply(ReadyEvent.Up, src)
    fun reportRevoked(src: String) = apply(ReadyEvent.Revoked, src)
    fun reportNeedsApproval(src: String) = apply(ReadyEvent.AwaitingUser, src)
    fun reportEnvironment(reason: com.byd.clusternav.carexec.LocalShellFailure, src: String) =
        apply(ReadyEvent.Environment(reason), src)

    /**
     * Chờ tới khi kênh thôi ở pha STARTING/CHECKING, tối đa [maxMs]. Gọi trên luồng chính ⇒ trả ngay (R-nf3).
     */
    fun awaitSettled(maxMs: Long): ShellReadinessState {
        if (Looper.myLooper() == Looper.getMainLooper()) return state
        val until = SystemClock.elapsedRealtime() + maxMs
        lock.withLock {
            while (state.phase == ShellChannelPhase.STARTING || state.phase == ShellChannelPhase.CHECKING) {
                val left = until - SystemClock.elapsedRealtime()
                if (left <= 0L) break
                try {
                    settled.await(left, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            return state
        }
    }

    /**
     * Chờ tới khi kênh đã được ĐO (thôi ở STARTING/CHECKING/UNKNOWN), tối đa [maxMs]. Chỉ cho việc nền MỘT LẦN, chạy
     * trong FGS và đang chờ màn chính hỏi quyền (vd `KachiAutostart` ở lượt nâng cấp đầu, lúc chưa có dấu duyệt:
     * `UpdateRelaunch` đưa màn chính lên ⇒ F4 đo kênh trong vài giây). Luồng chính ⇒ trả ngay.
     */
    fun awaitMeasured(maxMs: Long): ShellReadinessState {
        if (Looper.myLooper() == Looper.getMainLooper()) return state
        val until = SystemClock.elapsedRealtime() + maxMs
        lock.withLock {
            while (state.phase == ShellChannelPhase.STARTING || state.phase == ShellChannelPhase.CHECKING ||
                state.phase == ShellChannelPhase.UNKNOWN
            ) {
                val left = until - SystemClock.elapsedRealtime()
                if (left <= 0L) break
                try {
                    settled.await(left, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            return state
        }
    }

    // ─── Móc cổng thi hành ────────────────────────────────────────────────────────────────────────────────

    override fun admit(kind: ShellSessionKind, caller: String): Boolean = try {
        val main = Looper.myLooper() == Looper.getMainLooper()
        var a = ShellReadinessPolicy.admit(kind, state.phase, freshIfNeeded(kind), canWait = !main)
        if (a == Admission.WAIT) {
            awaitSettled(ShellReadinessPolicy.EARLY_WAIT_MS)
            a = ShellReadinessPolicy.admit(kind, state.phase, freshIfNeeded(kind), canWait = false)
        } else if (main && kind == ShellSessionKind.BACKGROUND && state.phase != ShellChannelPhase.UP) {
            Log.w(TAG, "phiên shell NỀN gọi từ luồng chính (caller=$caller) — không chờ kênh")
        }
        if (a == Admission.DENY) KachiReadyLog.deny(caller, state.phase.name)
        a == Admission.ALLOW
    } catch (e: RuntimeException) {
        // Fail-safe = CHẶN (global §4.1 "auth default deny"): một lỗi trong cổng không được biến thành "nối bừa" —
        // nối bừa bằng khoá chưa duyệt là dựng hộp hệ thống từ nền. Ghi to, không nuốt câm.
        Log.e(TAG, "cổng thi hành lỗi — chặn phiên ($caller)", e)
        kind == ShellSessionKind.ASK
    }

    override fun onEvent(event: ReadyEvent, src: String) {
        try {
            apply(event, src)
        } catch (e: RuntimeException) {
            Log.e(TAG, "không áp được sự kiện kênh $event ($src)", e)
        }
    }

    // ─── Nội bộ ───────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Dấu chỉ được HỎI khi nó có thể đổi quyết định (phiên NỀN lúc kênh chưa ĐO là lên / cần duyệt) — đường nóng
     * (mọi lệnh cửa sổ khi kênh đã lên) không đọc prefs, không đọc khoá.
     */
    private fun freshIfNeeded(kind: ShellSessionKind): Boolean {
        val p = state.phase
        if (kind == ShellSessionKind.ASK || p == ShellChannelPhase.UP || p == ShellChannelPhase.NEEDS_APPROVAL) return false
        return ledgerFresh()
    }

    /** Gọi khi giữ [lock]. Ghi dấu "đã duyệt" — qua MỘT hàm [ShellApprovalStore.markUp], có tiết chế (§4.4.1). */
    private fun touchLedger() {
        val fp = fingerprint() ?: return
        val now = System.currentTimeMillis()
        val win = if (windowMs < 0) ShellReadinessPolicy.DEFAULT_WINDOW_MS else windowMs
        if (!ShellReadinessPolicy.shouldTouch(ledger(), fp, now, win)) return
        val ok = guardedRead { store?.markUp(fp, now, win) } == true
        Log.i(TAG, "ledger markUp fp=${fp.take(FP_LOG_CHARS)} win=$win ok=$ok")
    }

    /** Gọi khi giữ [lock]. Xoá dấu — qua MỘT hàm [ShellApprovalStore.forget]. */
    private fun forgetLedger(src: String) {
        val ok = guardedRead { store?.forget(System.currentTimeMillis()) } == true
        Log.w(TAG, "ledger forget src=$src ok=$ok")
    }

    private fun describe(s: ShellReadinessState): String = when (s.phase) {
        ShellChannelPhase.UP -> "up"
        ShellChannelPhase.NEEDS_APPROVAL -> "needs ${if (s.lost) "lost" else "never"}"
        ShellChannelPhase.ENVIRONMENT -> "env ${s.reason}"
        ShellChannelPhase.CHECKING -> "checking"
        ShellChannelPhase.UNKNOWN -> "unknown"
        ShellChannelPhase.STARTING -> "starting"
    }

    /** Đọc/ghi prefs — SharedPreferences ném `RuntimeException` (ClassCast/IllegalState) khi tệp hỏng; không để lọt. */
    private inline fun <T> guardedRead(block: () -> T?): T? = try {
        block()
    } catch (e: RuntimeException) {
        Log.e(TAG, "dấu bền kênh shell đọc/ghi lỗi", e)
        null
    }

    private const val FP_LOG_CHARS = 8
}
