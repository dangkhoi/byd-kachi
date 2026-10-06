package com.byd.clusternav

import android.content.Context
import android.content.Intent
import android.util.Log
import com.byd.clusternav.launcher.InstalledPackageGate
import com.byd.clusternav.launcher.ResendGate
import com.byd.clusternav.modules.clustercast.ClusterOverlayDisplays
import com.byd.clusternav.modules.clustercast.simplified.ClusterLayerPause
import com.byd.clusternav.system.PackageQueries

/**
 * ═══ 2.90 · R8 — lệnh ẨN/HIỆN bóng nổi của bản mod VietMap trên cụm (owner duyệt 06/10) ══════════════════════════════════════
 *
 * HỢP ĐỒNG với bản mod (cố định — phía mod cài receiver): broadcast [ACTION], gói tường minh `vn.vietmap.live`, extra boolean
 * [EXTRA_SHOW]: `true` = hiện/gắn lại bóng trên display cụm; `false` = GỠ mọi cửa sổ phủ do mod quản (bóng chỉ đường, các ô chạm
 * nhỏ, ô lốp, MiMi) khỏi WindowManager. `VM_BUBBLE_POS` ([VmOverlayPosition]) không đổi. Bản mod không hỗ trợ thì bỏ qua.
 *
 * Vì sao broadcast mà không appop: [ĐO nguồn r47, spec §4.3] appop `SYSTEM_ALERT_WINDOW` chỉ ẨN (`hideLw`), cửa sổ + layer vẫn nằm
 * trên màn ảo cụm ⇒ cổng theme vẫn đếm; chỉ chính tiến trình VietMap mới `removeView` được cửa sổ của nó.
 *
 * Giá trị gửi = [ClusterLayerPause.bubbleWanted] (người lái KHÔNG chủ động ẩn — `vm_bubble_hidden`, mặc định false ⇒ hiện như trước
 * 2.90 — ∧ cổng theme KHÔNG đang dọn cụm). KHÔNG gate theo Cast ON: bóng
 * nằm trên màn ảo cụm cả khi tắt chiếu [ĐO 06/10 F5]. Cùng giá trị chỉ gửi lại sau [RESEND_MIN_MS] ([ResendGate]); `force` bỏ cổng
 * (đổi công tắc, vừa tự mở VietMap, trả cụm). Không có cờ bền "đang ẩn" — sự thật là prefs `vm_bubble_hidden` (CLAUDE.md §5).
 */
object VmBubbleVisibility {
    const val ACTION = "com.byd.clusternav.VM_BUBBLE_VIS"
    const val EXTRA_SHOW = "show"
    private const val VIETMAP_PKG = "vn.vietmap.live"
    private const val TAG = "VmBubbleVis"

    private const val RESEND_MIN_MS = 15_000L
    private const val PKG_TTL_MS = 60_000L
    private val gate = ResendGate(RESEND_MIN_MS)
    private val installedGate = InstalledPackageGate(PKG_TTL_MS)

    /** Bản mod VietMap (gói `vn.vietmap.live`) có cài không — nhớ [PKG_TTL_MS]. */
    fun installed(ctx: Context): Boolean {
        val app = ctx.applicationContext
        return synchronized(installedGate) {
            installedGate.installed(
                System.currentTimeMillis(),
                probe = { PackageQueries.packageInfo(app.packageManager, VIETMAP_PKG) != null },
                onFirstAbsence = { Log.i(TAG, "bỏ gửi VM_BUBBLE_VIS: $VIETMAP_PKG không cài") },
            )
        }
    }

    /** Gửi giá trị đang muốn ([ClusterLayerPause.bubbleWanted]). [force] = bỏ cổng gửi lặp. [why] chỉ để log. */
    fun apply(ctx: Context, why: String, force: Boolean = false) {
        val app = ctx.applicationContext
        if (!installed(app)) return
        val show = ClusterLayerPause.bubbleWanted(Prefs.vmBubbleHidden(app), ClusterOverlayDisplays.paused)
        val go = synchronized(gate) { gate.shouldSend(System.currentTimeMillis(), show) } || force
        if (go) send(app, show, why)
    }

    /**
     * Nhịp làm tươi (2 s, `FloatingBubbleService`): CHỈ giữ ẩn — gửi lại `show=false` (qua cổng 15 s) khi giá trị muốn là ẩn; không
     * gửi `show=true` lặp (bản mod dựng lại bóng ⇒ ẩn lại; còn hiện thì để các lượt có sự kiện — đổi công tắc, trả cụm, tự mở VietMap,
     * dò cụm — lo, tránh [CHƯA BIẾT] mod gắn lại bóng mỗi lần nhận `true`).
     */
    fun keepHidden(ctx: Context) {
        val app = ctx.applicationContext
        if (ClusterLayerPause.bubbleWanted(Prefs.vmBubbleHidden(app), ClusterOverlayDisplays.paused)) return
        apply(app, "nhịp làm tươi (giữ ẩn)")
    }

    /** Gửi thẳng [show] (dọn/trả cụm của cổng theme). Không bao giờ ném. */
    fun send(ctx: Context, show: Boolean, why: String) {
        val app = ctx.applicationContext
        runCatching {
            app.sendBroadcast(Intent(ACTION).setPackage(VIETMAP_PKG).putExtra(EXTRA_SHOW, show))
            synchronized(gate) { gate.shouldSend(System.currentTimeMillis(), show) }   // ghi nhận cho cổng gửi lặp
            Log.i(TAG, "gửi VM_BUBBLE_VIS show=$show → $VIETMAP_PKG ($why)")
        }.onFailure { Log.w(TAG, "gửi VM_BUBBLE_VIS lỗi ($why)", it) }
    }
}
