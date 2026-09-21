package com.byd.clusternav.modules.clustercast

import android.content.Context
import android.os.Handler
import android.util.Log
import com.byd.clusternav.modules.clustercast.simplified.AppMover
import com.byd.clusternav.modules.clustercast.simplified.ClusterSlotSide
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastIntent
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ Bộ TỰ CHIẾU KHI NỔ MÁY — driver DUY NHẤT (R1), tách khỏi [FloatingBubbleService] ═══════════════════════════
 *
 * **Vì sao ở tệp riêng (WP6, 2026-09-20).** KDoc của [FloatingBubbleService] tự khai nó *"chỉ sở hữu: vòng đời dịch
 * vụ, quản cửa sổ, nối cử chỉ→hành động, bộ nghe trạng thái"* — khối này không thuộc bốn thứ đó, và tệp kia đã
 * **537 dòng > trần 500** (CLAUDE.md §4.1) trước lượt này. Lượt tách **không đổi một bước nào**: cùng cờ nguyên tử,
 * cùng bộ nghe, cùng thứ tự, cùng `Handler` (nên `handler.removeCallbacksAndMessages(null)` của `onDestroy` vẫn huỷ
 * đúng lượt mở-chiếu đang chờ). Điều DUY NHẤT đổi là các trường nay sống CÙNG chỗ với logic đọc chúng.
 *
 * ## Vì sao dịch vụ nổi là driver duy nhất
 * Nó là thứ duy nhất chạy **không cần một Activity nào** (nổ máy → `RebindReceiver` → foreground service). Trước
 * đây đường Activity (`CastAutostart`) cũng bắn ⇒ **HAI driver** ⇒ lượt `CastSlot` thứ hai gặp `SLOT_OCCUPIED` ⇒
 * `setError` xoá sạch [SimpleCastState.CastingSplit] — đúng lỗi R1 đã có thật.
 *
 * ⚠⚠ Bốn tính chất KHÔNG được làm mất khi sửa tệp này:
 *  1. [dispatched] là `AtomicBoolean` + `compareAndSet`, KHÔNG phải kiểm-rồi-gán. `addStateListener` phát trạng
 *     thái hiện tại **trên luồng gọi** trong khi `SimpleCastCoordinator.setState` duyệt bộ nghe **trên luồng
 *     executor** ⇒ cùng một cò có thể nổ từ hai luồng; kiểm-rồi-gán để cả hai đi qua ⇒ bắn chia đôi HAI lần ⇒ đúng
 *     lỗi R1 ở trên. [rightDispatched] cho lượt bắt tay nửa-PHẢI cùng bảo đảm đó.
 *  2. Hai tham chiếu bộ nghe được GIỮ để [detach] gỡ được, cho ca chiếu **không bao giờ** tới `Idle` (hoặc lượt
 *     bắt tay nửa-PHẢI không bao giờ hoàn tất) — bộ nghe sống lâu hơn thứ nó phục vụ là một trong ba họ lỗi lặp
 *     lại của repo này.
 *  3. Nửa PHẢI chỉ bắn **sau khi nửa TRÁI đã đáp xuống thật** (`CastingSplit.left != null`), không phải sau một
 *     `postDelayed(2000)` đoán mò như bản cũ — nửa TRÁI hỏng thì nửa PHẢI đơn giản là không bao giờ bắn.
 *  4. [open] chỉ được lên lịch **khi tự-chiếu đang bật**; nếu không thì mặt cụm không bị đụng tới.
 */
internal class BubbleAutostart(
    private val app: Context,
    private val handler: Handler,
    private val isDestroyed: () -> Boolean,
) {

    private val dispatched = AtomicBoolean(false)
    private val rightDispatched = AtomicBoolean(false)
    @Volatile private var idleListener: ((SimpleCastState) -> Unit)? = null
    @Volatile private var splitRightListener: ((SimpleCastState) -> Unit)? = null

    /**
     * Nổ máy thuần thì KHÔNG có Activity nào mở chiếu, nên trạng thái sẽ không bao giờ tới `Idle` — tức cò ở
     * [dispatch] không bao giờ nổ. Vòng này tự mở chiếu (idempotent, R10) và thử lại có giới hạn vì cầu adb
     * (`localhost:5555`) có thể chưa sẵn ngay lúc máy vừa nổ.
     */
    @Volatile private var openAttempts = 0
    private val open = object : Runnable {
        override fun run() {
            if (isDestroyed() || dispatched.get()) return
            val coordinator = SimpleCastRuntime.coordinator(app)
            when (coordinator.state) {
                is SimpleCastState.Off, is SimpleCastState.Error -> coordinator.openProjection()
                else -> Unit // đang mở / đã Idle / đang chiếu — cò ở [dispatch] sẽ nổ
            }
            if (++openAttempts < OPEN_MAX_ATTEMPTS && !dispatched.get()) handler.postDelayed(this, OPEN_RETRY_MS)
        }
    }

    /**
     * Chờ trạng thái tới [SimpleCastState.Idle] rồi bắn ý-định tự-chiếu đã lưu. [dispatched] chặn cả lượt gọi lặp
     * (`onCreate`/`onStartCommand` chạy lại): một chuỗi đã khởi thì không bao giờ khởi lần hai trong cùng một lượt
     * sống của dịch vụ.
     */
    fun dispatch(coordinator: SimpleCastCoordinator) {
        if (dispatched.get()) return
        val prefs = coordinator.prefs
        val autoFull = prefs.autoStartEnabled()
        val autoSplit = prefs.autoStartSplitEnabled()
        if (!autoFull && !autoSplit) return

        val listener = object : (SimpleCastState) -> Unit {
            override fun invoke(state: SimpleCastState) {
                if (state !is SimpleCastState.Idle) return
                // Gỡ cò và giành cờ một-lần TRƯỚC khi bắn.
                coordinator.removeStateListener(this)
                idleListener = null
                if (!dispatched.compareAndSet(false, true)) return
                if (autoFull) full(coordinator) else split(coordinator)
            }
        }
        idleListener = listener
        coordinator.addStateListener(listener)

        openAttempts = 0
        handler.post(open)
    }

    /** Gỡ mọi bộ nghe còn treo — gọi từ `onDestroy` (xem tính chất (2) ở KDoc lớp). */
    fun detach(coordinator: SimpleCastCoordinator) {
        idleListener?.let { coordinator.removeStateListener(it) }
        splitRightListener?.let { coordinator.removeStateListener(it) }
        idleListener = null
        splitRightListener = null
    }

    /** Tự chiếu TOÀN cụm: đẩy gói đã lưu lên cả mặt cụm. */
    private fun full(coordinator: SimpleCastCoordinator) {
        val pkg = coordinator.prefs.autoStartPackage()
        if (pkg.isNullOrBlank()) return
        Log.i(TAG, "boot auto-cast full: $pkg")
        coordinator.dispatch(SimpleCastIntent.CastFull(pkg, AppMover.classifyApp(pkg)))
    }

    /** Tự chiếu CHIA ĐÔI: nửa TRÁI trước, nửa PHẢI chỉ sau khi TRÁI đáp xuống thật (tính chất (3) ở KDoc lớp). */
    private fun split(coordinator: SimpleCastCoordinator) {
        val leftPkg = coordinator.prefs.autoStartLeftPackage()?.takeIf(String::isNotBlank)
        val rightPkg = coordinator.prefs.autoStartRightPackage()?.takeIf(String::isNotBlank)

        if (leftPkg == null) {
            // Không cấu hình nửa TRÁI → bắn nửa PHẢI (nếu có) thẳng từ Idle.
            rightPkg?.let {
                Log.i(TAG, "boot auto-cast split right-only: $it")
                coordinator.dispatch(SimpleCastIntent.CastSlot(it, ClusterSlotSide.RIGHT))
            }
            return
        }

        Log.i(TAG, "boot auto-cast split left: $leftPkg")
        coordinator.dispatch(SimpleCastIntent.CastSlot(leftPkg, ClusterSlotSide.LEFT))
        if (rightPkg == null) return // chia đôi chỉ-TRÁI

        val listener = object : (SimpleCastState) -> Unit {
            override fun invoke(state: SimpleCastState) {
                // Chỉ nổ khi nửa TRÁI đã đáp xuống KIỂM ĐƯỢC. Bỏ qua Idle/Opening/Error nhất thời (nửa TRÁI hỏng
                // thì không bao giờ tới CastingSplit, nên nửa PHẢI không bao giờ bắn).
                if (state !is SimpleCastState.CastingSplit || state.left == null) return
                coordinator.removeStateListener(this)
                splitRightListener = null
                if (state.right != null) return // nửa PHẢI đã có — không phải làm gì
                if (!rightDispatched.compareAndSet(false, true)) return
                Log.i(TAG, "boot auto-cast split right: $rightPkg")
                coordinator.dispatch(SimpleCastIntent.CastSlot(rightPkg, ClusterSlotSide.RIGHT))
            }
        }
        splitRightListener = listener
        coordinator.addStateListener(listener)
    }

    private companion object {
        const val TAG = "ClusterCastBubble"
        const val OPEN_MAX_ATTEMPTS = 5
        const val OPEN_RETRY_MS = 3_000L
    }
}
