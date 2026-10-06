package com.byd.clusternav.modules.clustercast

import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import java.util.concurrent.CopyOnWriteArraySet

/**
 * ═══ 2.90 · R9/R10 — trạng thái dùng chung của các lớp phủ CỤM trong tiến trình (badge tốc độ, camera) ════════════════════════
 *
 *  • [castLiveId] — id cụm SỐNG do coordinator công bố (`SimpleCastRuntime` → `onCastDisplay`, cùng lượt dò `ClusterDisplayResolver`
 *    mà cổng theme dùng); `null` = chưa dò / đã đóng chiếu (màn ảo cụm vẫn có thể còn — [ĐO 06/10 F1] — nên [resolve] rơi về luật
 *    tên, không bỏ trống).
 *  • [paused] — "dọn cụm" của cổng theme đang chạy ([ClusterLayerExecutor]): lớp phủ của Kachi KHÔNG được gắn lên cụm tới khi TRẢ.
 *    Chỉ RAM, mặc định `false` — tiến trình chết giữa chừng thì lớp phủ dựng lại bình thường (CLAUDE.md §5: không cờ bền nào để kẹt).
 *  • [resolve] — display cụm theo [ClusterOverlayDisplay.pick] (thuần, `:core`): không hằng `1`, không VD riêng tư (ô của Kachi).
 *
 * Bên nghe ([addListener]) được gọi trên luồng đổi trạng thái (executor coordinator / luồng chính) — tự `post` sang luồng của mình.
 */
object ClusterOverlayDisplays {
    private const val TAG = "ClusterOverlayDisplay"

    @Volatile var castLiveId: Int? = null
        private set

    @Volatile var paused: Boolean = false
        private set

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    /** Coordinator công bố id cụm vừa dò (`null` = hụt / đóng chiếu). Chỉ báo bên nghe khi id ĐỔI. */
    fun publishCastDisplay(id: Int?) {
        val v = id?.takeIf { it >= 1 }
        if (v == castLiveId) return
        castLiveId = v
        notifyListeners()
    }

    /** Dọn cụm ([on] = true) / trả cụm ([on] = false). */
    fun setPaused(on: Boolean) {
        if (paused == on) return
        paused = on
        notifyListeners()
    }

    private fun notifyListeners() {
        for (l in listeners) runCatching { l() }.onFailure { Log.w(TAG, "listener lỗi: ${it.message}") }
    }

    /** Display cụm cho lớp phủ, hoặc `null` (chưa có cụm — off-car / chưa dựng). Không bao giờ ném. */
    fun resolve(dm: DisplayManager): Display? = runCatching {
        val all = dm.displays.toList()
        val id = ClusterOverlayDisplay.pick(
            all.map {
                ClusterOverlayDisplay.Candidate(
                    id = it.displayId,
                    name = it.name.orEmpty(),
                    isPrivate = (it.flags and Display.FLAG_PRIVATE) != 0,
                    isPresentation = (it.flags and Display.FLAG_PRESENTATION) != 0,
                )
            },
            castLiveId,
        )
        all.firstOrNull { it.displayId == id }
    }.getOrNull()
}
