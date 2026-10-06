package com.byd.clusternav.modules.clustercast

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.VmBubbleVisibility
import com.byd.clusternav.modules.clustercast.simplified.BoundedCastExecutor
import com.byd.clusternav.modules.clustercast.simplified.BubbleOldModMemo
import com.byd.clusternav.modules.clustercast.simplified.ClusterLayerPort
import com.byd.clusternav.vmBubbleHidden
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ═══ 2.90 · R9 — bộ thi hành "dọn cụm / trả cụm" của `:app` cho cổng theme ([ClusterLayerPort], luật ở `ClusterLayerPause`) ══════
 *
 * Chạy trên executor của coordinator (không phải luồng chính). Mọi thao tác cửa sổ của Kachi là TRONG tiến trình — không lệnh
 * `am`/`wm` nào: [pauseOwn] bật [ClusterOverlayDisplays.paused] ⇒ badge tốc độ tự gỡ trên luồng chính (`SpeedBadgeOverlay.reconcile`)
 * và camera mới mở rơi về màn chính; chờ luồng chính chạy xong lượt gỡ (≤ [FLUSH_MS]) để bản đọc `dumpsys window windows` kế tiếp
 * của cổng đã thấy sự thật. Bóng VietMap: [VmBubbleVisibility] (broadcast tường minh tới bản mod). Không bao giờ ném.
 */
class ClusterLayerExecutor(context: Context) : ClusterLayerPort {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /**
     * Review Pass 3 [P2] — lưới an toàn cho "TRẢ luôn chạy": TRẢ nằm trong `finally` của lượt mở (kể cả khi executor `cancel(true)`
     * lúc quá hạn), nhưng một lệnh shell KHÔNG ngắt được có thể giữ luồng đó lâu hơn ⇒ quá [PAUSE_MAX_MS] (> hạn cứng của lượt
     * mở) thì tự trả lớp của Kachi + gửi lại `VM_BUBBLE_VIS` (hiện, trừ khi người lái chủ động ẩn). Lượt TRẢ thật tới sau vẫn idempotent.
     */
    private val failsafe = Runnable {
        if (!ClusterOverlayDisplays.paused) return@Runnable
        Log.w(TAG, "trả cụm (lưới an toàn): lượt mở giữ 'dọn cụm' quá $PAUSE_MAX_MS ms ⇒ tự trả lớp Kachi + bóng theo công tắc")
        ClusterOverlayDisplays.setPaused(false)
        VmBubbleVisibility.apply(app, "trả cụm (lưới an toàn)", force = true)
    }

    override fun pauseOwn() {
        ClusterOverlayDisplays.setPaused(true)
        main.removeCallbacks(failsafe)
        main.postDelayed(failsafe, PAUSE_MAX_MS)
        flushMain()
    }

    override fun resumeOwn(clusterId: Int) {
        main.removeCallbacks(failsafe)
        if (clusterId >= 1) ClusterOverlayDisplays.publishCastDisplay(clusterId)
        ClusterOverlayDisplays.setPaused(false)
    }

    override fun bubbleInstalled(): Boolean = VmBubbleVisibility.installed(app)

    override fun bubbleHiddenByUser(): Boolean = Prefs.vmBubbleHidden(app)

    override fun sendBubble(show: Boolean) = VmBubbleVisibility.send(app, show, if (show) "trả cụm" else "dọn cụm")

    // ── 2.93 wave 2A · VM-BUBBLE-OLDMOD-MEMO — sổ ở tệp `clustercast` (phạm vi XE, cạnh sổ theme; `ProfileScopeCluster.DEVICE_KEYS`) ──

    override fun bubbleInstallToken(): String? = VmBubbleVisibility.installToken(app)

    override fun oldModMemo(): String? = memoPrefs().getString(BubbleOldModMemo.KEY, null)

    /** `commit()` đồng bộ (luồng executor, không luồng chính) — lượt mở kế phải đọc được đúng thứ vừa ghi. */
    override fun writeOldModMemo(value: String?): Boolean {
        val e = memoPrefs().edit()
        if (value == null) e.remove(BubbleOldModMemo.KEY) else e.putString(BubbleOldModMemo.KEY, value)
        return e.commit()
    }

    private fun memoPrefs() = app.getSharedPreferences(ClusterProfile.PREF, Context.MODE_PRIVATE)

    /** Bên nghe đã `post` lượt gỡ lên luồng chính (FIFO) ⇒ một lượt `post` nữa chạy xong = lượt gỡ đã chạy xong. */
    private fun flushMain() {
        if (Looper.myLooper() == Looper.getMainLooper()) return
        val done = CountDownLatch(1)
        main.post { done.countDown() }
        val ran = try {
            done.await(FLUSH_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            // Review Pass 3 [P2]: executor `cancel(true)` (quá hạn / Stop ưu tiên) — KHÔNG nuốt cờ ngắt, lượt mở phải dừng.
            Thread.currentThread().interrupt()
            return
        }
        if (!ran) Log.w(TAG, "dọn cụm: luồng chính chưa chạy lượt gỡ sau $FLUSH_MS ms — cổng đọc lại sẽ tự chờ")
    }

    private companion object {
        const val TAG = "ClusterLayer"
        const val FLUSH_MS = 500L
        /**
         * > hạn cứng của lượt mở + đệm 5 s. 2.93 · CAST-OPEN-TIMEOUT: lượt mở có hạn RIÊNG ([BoundedCastExecutor.OPEN_TIMEOUT_MS],
         * 25 s) ⇒ lưới an toàn bám theo hằng đó (bản 2.90: 20 s cho hạn 15 s) — không thì nó trả cụm GIỮA một lượt mở còn hợp lệ.
         */
        const val PAUSE_MAX_MS = BoundedCastExecutor.OPEN_TIMEOUT_MS + 5_000L
    }
}
