package com.byd.clusternav.launcher.trip

import android.os.Handler
import android.os.Looper
import com.byd.clusternav.launcher.behind.BehindHomePlan
import com.byd.clusternav.launcher.behind.BehindHomeRunner
import com.byd.clusternav.launcher.behind.BehindHomeSequence
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * ═══ F2/F3 — CẦU từ chuyến lên xe (mức TIẾN TRÌNH, luồng `kachi-trip`) tới MÀN CHÍNH đang sống ═══════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` §4.5. Chuyến chạy ở mức tiến trình (móc chuỗi SẴN), nhưng
 * ba thứ nó cần chỉ màn chính biết: (1) ô app nào đang hiện + màn ảo nào đang sống (`VdAppHost` của từng ô — chỗ dàn
 * dựng R0.3), (2) kênh shell của màn (`launcherSeam`, có cổng ownership theo `--display`), (3) bên thi hành BEHIND-HOME
 * (`KachiHomeSlots.startBehind` — cùng mutex `kachi-behind` với lối tắt). Cầu này KHÔNG giữ state nào để quyết: mỗi lượt
 * hỏi lại màn ([Host.view] trên luồng chính) và mỗi lệnh đổi cửa sổ vẫn tự đọc `am stack list` ngay trước (CLAUDE.md §5).
 *
 * ## Một màn, một chủ — giữ YẾU (cùng lẽ `ShortcutHub`)
 * [ĐO] H2 2026-09-14 có lúc nhiều `KachiHomeActivity` cùng sống ⇒ lấy màn ĐĂNG KÝ SAU CÙNG còn sống ([current]). Giữ
 * `WeakReference` để chuyến không bao giờ níu một Activity đã chết.
 */
internal object TripHub {

    /** Ảnh chụp màn chính cho chuyến: gói của ô app ĐANG HIỆN (lớp lưu + lớp tạm) + ứng viên dàn dựng (A5). */
    data class HomeView(val appSlots: List<String>, val stages: List<BehindHomePlan.Stage>)

    interface Host {
        /** Activity còn sống (chưa finish/destroy). */
        fun alive(): Boolean

        /** Kênh shell của màn chính; `null` = chưa nối (chuyến chờ). Gọi từ luồng nền. */
        fun shell(): ((String) -> String)?

        /** Ảnh chụp — CHỈ gọi trên luồng chính (đọc cây view). */
        fun view(): HomeView

        /** R0.3 qua bên thi hành của màn. `null` = không có ô sống (0 lệnh). [done] chạy trên luồng chính. */
        fun startBehind(pkg: String, stages: List<BehindHomePlan.Stage>, done: (BehindHomeSequence.Outcome) -> Unit): BehindHomePlan.Stage?

        /**
         * L4 — một chuỗi tuỳ ý (màn ảo ẩn D2(a), K4-VIEW D3(ii)) trên CÙNG bên thi hành/mutex `kachi-behind` của màn. [done]
         * chạy trên luồng chính, đúng một lần (kể cả khi không kênh / đã tắt: kết quả `NO_CHANNEL` / `DISABLED`).
         */
        fun behindChain(what: String, body: (BehindHomeRunner.Kit) -> BehindHomeSequence.Outcome, done: (BehindHomeSequence.Outcome) -> Unit)
    }

    @Volatile private var last: WeakReference<Host>? = null
    private val ui = Handler(Looper.getMainLooper())

    /** Màn chính vừa dựng nhận vai chủ của chuyến (màn sau thay màn trước). Luồng chính. */
    fun bind(host: Host) { last = WeakReference(host) }

    fun current(): Host? = last?.get()?.takeIf { runCatching { it.alive() }.getOrDefault(false) }

    /**
     * Chạy [block] trên luồng chính và CHỜ kết quả tối đa [timeoutMs] (gọi từ luồng nền). Hết hạn / ném ⇒ `null` — chuyến
     * coi như "màn chưa sẵn" và chờ nhịp sau, không bao giờ treo luồng `kachi-trip`.
     */
    fun <T : Any> onMain(timeoutMs: Long, block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching(block).getOrNull()
        val out = AtomicReference<T?>(null)
        val latch = CountDownLatch(1)
        ui.post { try { out.set(runCatching(block).getOrNull()) } finally { latch.countDown() } }
        return try {
            if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) out.get() else null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }
}
