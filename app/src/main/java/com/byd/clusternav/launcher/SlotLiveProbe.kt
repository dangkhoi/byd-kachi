package com.byd.clusternav.launcher

import android.os.Handler
import android.os.Looper
import android.util.Log
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

    /** Một ô đang được theo dõi. [onDead] gọi trên luồng UI, đúng MỘT lần cho mỗi chu kỳ sống. */
    private class Sub(
        val key: String,
        val pkg: String,
        val displayId: Int,
        val shell: (String) -> String,
        val onDead: () -> Unit,
    ) {
        val liveness = SlotLiveness()
    }

    private val subs = CopyOnWriteArrayList<Sub>()
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "kachi-slot-probe").apply { isDaemon = true } }

    @Volatile private var ticking = false
    @Volatile private var running = false

    /** Màn chính đang khuất ⇒ không nhịp nào chạy. Xem khối ⚠ ở KDoc lớp. */
    @Volatile private var paused = false

    /** Theo dõi ô [key] (gói [pkg] trên màn ảo [displayId]). Gọi lại cùng [key] ⇒ thay bản cũ (idempotent). */
    fun watch(key: String, pkg: String, displayId: Int, shell: (String) -> String, onDead: () -> Unit) {
        unwatch(key)
        subs.add(Sub(key, pkg, displayId, shell, onDead))
        start()
    }

    /** Thôi theo dõi ô [key] (ô đóng / host nhả / đã báo chết). Không còn ô nào ⇒ ticker tự tắt. */
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
            val out = runCatching { shell("am stack list") }
                .onFailure { Log.w(TAG, "đo ô hỏng (am stack list): ${it.javaClass.simpleName}") }
                .getOrNull()
            running = false
            if (out.isNullOrBlank()) return@execute            // không đọc được ⇒ KHÔNG kết luận (nhịp này bỏ qua)
            val picture = snapshot.map { it.key to (FreeformLaunch.parseTaskIdOnDisplay(out, it.pkg, it.displayId) != null) }
            unchangedSweeps = if (picture == lastPicture) unchangedSweeps + 1 else 0
            lastPicture = picture
            snapshot.forEach { sub ->
                val alive = picture.first { it.first == sub.key }.second
                if (sub.liveness.observe(alive)) {
                    Log.i(TAG, "ô ${sub.key}: ${sub.pkg} không còn task trên display ${sub.displayId} ⇒ app đã đóng")
                    unwatch(sub.key)                            // đã kết luận ⇒ thôi đo (mở lại sẽ đăng ký lượt mới)
                    ui.post { sub.onDead() }
                }
            }
        }
    }
}
