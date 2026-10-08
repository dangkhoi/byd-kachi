package com.byd.clusternav.launcher

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.byd.clusternav.system.StackListSnapshot
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * ═══ NHỊP ĐO "APP TRONG Ô CÒN SỐNG KHÔNG" — MỘT LỆNH CHO TẤT CẢ Ô (H2·2) ══════════════════════════════════════
 *
 * Triệu chứng [ĐO] (`docs/diagnostics/waze-into-slot-research-2026-09-14.md` §5): app trên màn ảo chết thì
 * `SurfaceView` giữ nguyên **khung hình cuối** ⇒ ô trông còn sống. Không có tín hiệu hệ thống nào bắn về host
 * khi điều đó xảy ra (màn ảo vẫn còn, mặt vẽ vẫn còn, chỉ task biến mất), nên phải **đo**.
 *
 * ## Vì sao một bộ đo dùng chung, không phải mỗi host tự hẹn giờ
 * Mỗi lệnh `am stack list` là một lượt dadb chặn. Bốn ô App tự hẹn giờ riêng = 4 lệnh mỗi nhịp cho **một dữ liệu
 * y hệt nhau** — đúng cái bệnh `widgetData()` đã mắc rồi chữa ([SOÁT P2-8]). Ở đây: **một** lệnh mỗi nhịp
 * ([SlotLiveness.PROBE_PERIOD_MS] = 5 s), rồi phát lại kết quả cho từng ô. Không ô nào đăng ký ⇒ **không có nhịp
 * nào chạy** (tắt hẳn ticker, không đốt pin khi màn chính chỉ có widget).
 *
 * Luồng: hẹn giờ trên luồng UI → chạy lệnh trên MỘT luồng nền riêng (không dùng `winExec` của màn chính: bộ đó
 * còn chạy lệnh đặt cửa sổ chặn tới ~3 s, xen vào sẽ làm nhịp đo trôi) → trả kết luận về luồng UI.
 *
 * ⚠ [SOÁT Pass H2 · P2] Luồng nền riêng KHÔNG có nghĩa là "không tranh chấp": mọi lệnh shell của app cuối cùng
 * đều xếp hàng trên **một** chủ duy nhất (`ShellTransport` — một `PrioritySerialExecutor`, một kết nối dadb), nên
 * mỗi nhịp đo vẫn chiếm chỗ trong CÙNG hàng đợi với lệnh đặt cửa sổ. Vì thế nhịp đo phải **ngưng khi màn chính
 * không còn hiển thị** ([pause]/[resume] nối vào `onStop`/`onStart`): người dùng mở một app toàn màn thì màn
 * Kachi vẫn sống (view còn gắn, ô còn đăng ký) và nếu không ngưng thì app sẽ đốt một lượt dadb mỗi 5 giây **suốt
 * chuyến đi** cho một dữ liệu không ai nhìn.
 */
object SlotLiveProbe {

    private const val TAG = "KachiVd"

    /**
     * Một ô đang được theo dõi. [onDead] gọi trên luồng UI, đúng MỘT lần cho mỗi chu kỳ sống; đối số = 2.93 · R3 app RA KHỎI ô
     * mà task còn ở display khác ([SlotLiveness.elsewhere]) — `false` = đã đóng như trước.
     */
    private class Sub(
        val key: String,
        val pkg: String,
        val displayId: Int,
        val shell: (String) -> String,
        val onDead: (Boolean) -> Unit,
        /** Ô 7 (2.89-thử1): màn ảo NHẬN LẠI từ chỗ đỗ — app không còn trên đó ⇒ gọi cái này thay [onDead] (luồng UI). */
        val onMissing: (() -> Unit)? = null,
    ) {
        val liveness = SlotLiveness(adopted = onMissing != null)
    }

    private val subs = CopyOnWriteArrayList<Sub>()
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "kachi-slot-probe").apply { isDaemon = true } }

    @Volatile private var ticking = false
    /** Mốc (đồng hồ [StackListSnapshot.nowMs]) lượt đo trước — bản đọc dùng lại phải chụp SAU mốc này. Chỉ chạm trên luồng `io`. */
    private var lastSweepAt = Long.MIN_VALUE
    @Volatile private var running = false

    /** Màn chính đang khuất ⇒ không nhịp nào chạy. Xem khối ⚠ ở KDoc lớp. */
    @Volatile private var paused = false

    /**
     * Theo dõi ô [key] (gói [pkg] trên màn ảo [displayId]). Gọi lại cùng [key] ⇒ thay bản cũ (idempotent). [onMissing] khác
     * `null` = màn ảo nhận lại từ ô 7 (`SlotLiveness.adopted`): chưa từng thấy app sau đủ nhịp hụt ⇒ [onMissing], không [onDead].
     */
    fun watch(key: String, pkg: String, displayId: Int, shell: (String) -> String, onMissing: (() -> Unit)? = null, onDead: (Boolean) -> Unit) {
        unwatch(key)
        subs.add(Sub(key, pkg, displayId, shell, onDead, onMissing))
        start()
        if (onMissing != null) kick()
    }

    /**
     * PARK-2b — màn ảo vừa nhận lại từ ô 7: đo NGAY một nhịp thay vì chờ nhịp đang lùi (tới [SlotLiveness.PROBE_PERIOD_MAX_MS]),
     * và nhịp kế về sàn 5 s — app đã rời màn ảo lúc đỗ thì ô đen ≈ một lượt `am stack list`, không 10–20 s. Vẫn MỘT chuỗi nhịp
     * (gỡ nhịp đang hẹn rồi hẹn lại ngay). Luồng chính. Màn khuất ⇒ không làm gì (`resume` hẹn lại).
     */
    private fun kick() {
        unchangedSweeps = 0
        if (paused) return
        ui.removeCallbacks(tick)
        ticking = true
        ui.post(tick)
    }

    /**
     * A5 (spec shortcuts-autostart R0.3) — ô [key] đã được ĐO thấy app sống ít nhất một nhịp (chưa kết luận chết). Chỗ
     * dàn dựng BEHIND-HOME chỉ dùng ô đã sống: ô vừa mở (chưa nhịp đo nào) có thể là app chưa bao giờ vào được ô (H1).
     */
    fun seenAlive(key: String): Boolean = subs.firstOrNull { it.key == key }?.liveness?.seenAlive == true

    /**
     * FIX286 · R-SC2 — ô [key] đang được đo (lượt mở app đã xong, chưa kết luận chết). Vắng ⇒ lượt mở còn đang chạy, hoặc
     * đã báo chết (L6: ô đi luật hoàn ô `SlotRevertPlan`). `VdAppHost.reviveInSlot` dùng để không mở lại chồng lượt mở dở.
     */
    fun watching(key: String): Boolean = subs.any { it.key == key }

    /**
     * Thôi theo dõi ô [key] (ô đóng / host nhả / đã báo chết). Không còn ô nào ⇒ ticker tự tắt. Kết luận đang chờ luồng chính của
     * bản bị gỡ ở đây sẽ tự bỏ (`sweep` — Senior review Pass 2).
     */
    fun unwatch(key: String) {
        subs.removeAll(subs.filter { it.key == key })
    }

    /**
     * Số màn Kachi **đang hiển thị**. Đếm chứ không dùng một cờ bật/tắt: [ĐO] của chính H2 là có lúc **bốn**
     * `KachiHomeActivity` cùng sống, và thứ tự vòng đời Android khi màn B thay màn A là
     * `A.onPause → B.onStart → A.onStop` ⇒ một cờ trần sẽ bị `A.onStop` tắt **sau khi** B đã bật, và nhịp đo
     * đứng im vĩnh viễn trong khi màn B đang hiện. Bộ đếm không có ca đó.
     */
    private val visible = AtomicInteger(0)

    /**
     * Một màn Kachi rời tiền cảnh (`onStop`) ⇒ **ngưng** nhịp đo khi không còn màn nào hiện, nhưng GIỮ danh
     * sách ô.
     *
     * Giữ chứ không xoá vì ô vẫn còn đó: xoá rồi thì lúc quay lại phải chờ [SlotLiveness] thấy sống lại từ đầu,
     * mà cái ta cần giữ đúng là *"đã từng thấy sống"* — nếu không, mỗi lần người dùng mở một app toàn màn rồi
     * quay về là một chu kỳ đo mới, và ô chết trong lúc khuất sẽ **không bao giờ** được kết luận.
     */
    fun pause() {
        if (visible.decrementAndGet() > 0) return
        visible.set(0)
        paused = true
    }

    /** Một màn Kachi hiện lên (`onStart`) ⇒ chạy nhịp tiếp. Idempotent theo từng màn. */
    fun resume() {
        visible.incrementAndGet()
        if (!paused) return
        paused = false
        start()
    }

    private fun start() {
        if (ticking || paused) return
        ticking = true
        ui.postDelayed(tick, SlotLiveness.PROBE_PERIOD_MS)
    }

    /**
     * K8 (1.70) — số nhịp liên tiếp mà bức tranh sống/chết của MỌI ô **không đổi**; nhịp kế lấy từ
     * [SlotLiveness.probePeriodMs] (5 → 10 → 15 s). Đổi (ô mới đăng ký, một ô vắng task) ⇒ về 0.
     */
    @Volatile private var unchangedSweeps = 0
    @Volatile private var lastPicture: List<Pair<String, Boolean>> = emptyList()

    private val tick = object : Runnable {
        override fun run() {
            if (subs.isEmpty()) { ticking = false; return }   // hết ô ⇒ dừng hẳn, không hẹn tiếp
            if (paused) { ticking = false; return }           // màn khuất ⇒ ngưng; `resume()` hẹn lại
            if (!running) sweep()
            // Nhịp SAU tính từ bức tranh của nhịp TRƯỚC (nhịp này còn đang chạy trên luồng nền) — SlotLiveness.PROBE_PERIOD_MS là sàn.
            ui.postDelayed(this, SlotLiveness.probePeriodMs(unchangedSweeps))
        }
    }

    /** Một lượt: MỘT `am stack list` trên luồng nền → chia kết quả cho từng ô → báo chết trên luồng UI. */
    private fun sweep() {
        val snapshot = subs.toList()
        val shell = snapshot.firstOrNull()?.shell ?: return
        running = true
        io.execute {
            // 2.96 · R18 — lượt dò repin (đang chiếu, 4 s) vừa đọc CÙNG lệnh ⇒ dùng lại bản đọc chụp SAU nhịp đo trước (0 lệnh); không có
            // ⇒ tự chạy như cũ và ghi lại cho bên kia ([StackListSnapshot]; [ĐO log xe 07/10] 15 + ~4 `am stack list`/phút trùng nhau).
            val since = lastSweepAt
            lastSweepAt = StackListSnapshot.nowMs()
            val out = StackListSnapshot.fresh(notBeforeMs = since) ?: runCatching { shell("am stack list") }
                .onFailure { Log.w(TAG, "đo ô hỏng (am stack list): ${it.javaClass.simpleName}") }
                .getOrNull()?.also { StackListSnapshot.record(it) }
            running = false
            if (out.isNullOrBlank()) return@execute            // không đọc được ⇒ KHÔNG kết luận (nhịp này bỏ qua)
            val picture = snapshot.map { it.key to (FreeformLaunch.parseTaskIdOnDisplay(out, it.pkg, it.displayId) != null) }
            unchangedSweeps = if (picture == lastPicture) unchangedSweeps + 1 else 0
            lastPicture = picture
            val readable = "Stack id=" in out                  // ô 7: màn ảo nhận lại chỉ kết luận "trống" trên bản đọc có tiêu đề stack
            // 2.98 · R3 (SLOT-ELSEWHERE-TWO-HOMES, `SlotProbeScope`): sổ chủ màn ảo — đọc RAM, chỉ khi có ô vắng app, một lần mỗi nhịp.
            val held by lazy(LazyThreadSafetyMode.NONE) { SlotVdOwner.held() }
            snapshot.forEach { sub ->
                if (sub.onMissing != null && !readable) return@forEach
                val alive = picture.first { it.first == sub.key }.second
                // 2.93 · R3 (SLOT-APP-ESCAPE): vắng màn ảo ô mà gói còn task ở display khác ⇒ "ở chỗ khác" — CÙNG bản đọc, cùng phép
                // FIX286 (`SlotPresence`), 0 lệnh thêm. Đọc rỗng/lạ ⇒ UNKNOWN ⇒ không phải "ở chỗ khác".
                // 2.98 · R3 — màn ảo ô của một màn Kachi KHÁC còn sống không phải "chỗ khác": hai màn chính cùng sống, màn mới nhận ô
                // (`SlotVdOwner.adopt` nhả màn ảo của màn cũ) rồi mở app vào màn ảo của nó ⇒ bản đo của màn cũ thấy "rời ô" (đã-thấy-sống
                // ⇒ hoàn ô im lặng như 2.92), không nói "đã rời ô, vẫn mở ngoài ô". Một màn Kachi ⇒ tập rỗng ⇒ như 2.93.
                val away = !alive && SlotPresence.of(out, sub.pkg, sub.displayId,
                    SlotProbeScope.otherHomes(held, sub.key, sub.displayId)) == SlotPresence.ELSEWHERE
                // Senior review Pass 2 [P3] — màn ảo ô còn app KHÁC ⇒ ô chưa trống (luật hoàn ô nhả màn ảo ⇒ cờ 256 kết thúc app đó)
                // ⇒ không tính cho kết luận chưa-từng-thấy-sống (KDoc `SlotLiveness`). Cùng bản đọc, 0 lệnh.
                val othersInSlot = away && SlotLiveness.othersInSlot(out, sub.pkg, sub.displayId)
                if (sub.liveness.observe(alive, away, othersInSlot)) {
                    val elsewhere = sub.liveness.elsewhere
                    Log.i(TAG, "ô ${sub.key}: ${sub.pkg} không còn task trên display ${sub.displayId} ⇒ ${if (elsewhere) "app RA KHỎI ô, task còn ở display khác" else "app đã đóng"}")
                    val missing = sub.onMissing?.takeIf { sub.liveness.missing }
                    // Senior review Pass 2 [P3] — thôi đo ĐÚNG bản này, trên luồng chính, và chỉ báo khi nó CÒN đăng ký. Bản cũ
                    // `unwatch(sub.key)` ở luồng nền: một bản bị gỡ/thay TRONG lúc nhịp đang bay (⇱ `SlotFullscreen.detach` gỡ trước K7 ·
                    // `reviveInSlot` · `watch` cùng khoá) vẫn kết luận ⇒ gỡ nhầm bản MỚI cùng khoá (ô thôi được đo) và gọi `onAppClosed`
                    // của host — host đọc `pkg`/`dead` LÚC báo ⇒ ô đang ra toàn màn / vừa mở lại bị áp luật hoàn ô. Đã kết luận ⇒ `observe`
                    // không báo lại ở nhịp kế.
                    ui.post {
                        if (!subs.remove(sub)) { Log.i(TAG, "ô ${sub.key}: kết luận của lượt đo đã bị thay/gỡ khi nhịp đang chạy — bỏ"); return@post }
                        if (missing != null) missing() else sub.onDead(elsewhere)
                    }
                }
            }
        }
    }
}
