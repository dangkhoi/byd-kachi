package com.byd.clusternav

import android.content.Context
import android.content.Intent
import android.util.Log
import com.byd.clusternav.launcher.InstalledPackageGate
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.system.PackageQueries

/**
 * Điều khiển VỊ TRÍ bong bóng VietMap-mod trên CỤM (item 4, spec `docs/specs/vietmap-overlay-position-ui.html`).
 *
 * VietMap bản MOD dời bong bóng nav sang cụm (khi Cluster Cast ON). Bong bóng đó là cửa sổ của VietMap nên
 * ClusterNav KHÔNG tự đặt vị trí được — thay vào đó bản mod được inject 1 `BroadcastReceiver` (EXPORTED, action
 * [ACTION]) gọi setter vị trí sẵn có của bong bóng (`vn.vietmap.live` `b.E(x,y)` — đặt LayoutParams x/y +
 * `updateViewLayout`). File này là phía GỬI: lưu x/y (Prefs) + bắn broadcast tới `vn.vietmap.live`.
 *
 * Gate: chỉ có nghĩa khi Cluster Cast ON (cụm mới "live" cho bong bóng lên). UI disable khi Cast OFF.
 */
object VmOverlayPosition {
    const val ACTION = "com.byd.clusternav.VM_BUBBLE_POS"
    private const val VIETMAP_PKG = "vn.vietmap.live"
    private const val K_X = "vm_bubble_x"
    private const val K_Y = "vm_bubble_y"
    private const val TAG = "VmOverlayPos"

    // Cụm 1920×720; bong bóng ~371×158. [ĐO calib 2026-08-28] mod receiver đặt gravity TOP|LEFT +
    // FLAG_LAYOUT_NO_LIMITS ⇒ E(x,y) = toạ độ TUYỆT ĐỐI góc-trên-trái (left=x, top=y), map 1-1 placement view.
    private const val CLUSTER_W = 1920
    private const val CLUSTER_H = 720
    private const val BUBBLE_W = 371
    private const val BUBBLE_H = 158
    // Public mirrors cho placement view kéo-thả (VmBubblePlacementView) dùng CÙNG kích thước — một nguồn sự thật.
    const val CLUSTER_WIDTH = CLUSTER_W
    const val CLUSTER_HEIGHT = CLUSTER_H
    const val BUBBLE_WIDTH = BUBBLE_W
    const val BUBBLE_HEIGHT = BUBBLE_H
    // Biên góc-trên-trái để bong bóng còn trong cụm.
    private val X_MAX = (CLUSTER_W - BUBBLE_W).coerceAtLeast(0)   // 1549
    private val Y_MAX = (CLUSTER_H - BUBBLE_H).coerceAtLeast(0)   // 562

    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("clusternav_prefs", Context.MODE_PRIVATE)

    /** x/y đã lưu = TOẠ ĐỘ TUYỆT ĐỐI góc-trên-trái bong bóng (cluster px); mặc định = preset nửa-phải. */
    fun x(ctx: Context): Int = sp(ctx).getInt(K_X, rightHalfX())
    fun y(ctx: Context): Int = sp(ctx).getInt(K_Y, rightHalfY())

    fun rightHalfX(): Int = (CLUSTER_W - BUBBLE_W) * 3 / 4        // left ~1162: bong bóng ở nửa phải
    fun rightHalfY(): Int = (CLUSTER_H - BUBBLE_H) / 2           // giữa theo chiều dọc

    /** Đặt tuyệt đối góc-trên-trái (cluster px) + lưu + bắn (nếu Cast ON). */
    fun set(ctx: Context, newX: Int, newY: Int, sendNow: Boolean = true) {
        val cx = newX.coerceIn(0, X_MAX)
        val cy = newY.coerceIn(0, Y_MAX)
        sp(ctx).edit().putInt(K_X, cx).putInt(K_Y, cy).apply()
        if (sendNow) send(ctx)
    }

    /** Preset: đặt bong bóng ở nửa PHẢI cụm. */
    fun presetRightHalf(ctx: Context) = set(ctx, rightHalfX(), rightHalfY())

    /** Góc-trên-trái bong bóng (cluster px) — nay LƯU trực tiếp tuyệt đối, cho placement view kéo-thả. */
    fun absLeftX(ctx: Context): Int = x(ctx)
    fun absTopY(ctx: Context): Int = y(ctx)

    /** Đặt theo toạ độ TUYỆT ĐỐI góc-trên-trái bong bóng (cluster px) — trực quan cho UI kéo-thả, map 1-1. */
    fun setAbsoluteTopLeft(ctx: Context, absX: Int, absY: Int) = set(ctx, absX, absY)

    /** Cluster Cast có đang bật không (cụm mới live cho bong bóng). */
    fun castOn(ctx: Context): Boolean = runCatching {
        SimpleCastRuntime.coordinator(ctx.applicationContext).prefs.castEnabled()
    }.getOrDefault(false)

    /**
     * Bắn broadcast vị trí hiện tại tới VietMap mod. No-op nếu Cast OFF (cụm chưa live).
     *
     * ## LOG-41KB (2026-09-26) — bắn thì vẫn bắn, LOG thì thôi lặp
     * [ĐO xe 26/09] `VmOverlayPos` ghi **3,8 dòng/phút** cùng một câu `x=1339 y=100` suốt phiên (nhịp 2 s của
     * `FloatingBubbleService` + [gate] 15 s). Lượt **bắn** phải giữ nguyên: bản mod dựng lại bong bóng giữa chuyến
     * thì chỉ lượt bắn lặp ấy đưa nó về đúng chỗ (K8, xem [applyOnOpen]). Nhưng dòng log thứ hai trở đi cho cùng
     * toạ độ **không mang thêm dữ kiện nào** ⇒ qua [logGate]: toạ độ ĐỔI (người dùng kéo) ghi ngay, toạ độ cũ chỉ
     * ghi lại sau [LOG_REPEAT_MS] để log còn một mốc thời gian chứng minh nhịp vẫn chạy.
     * Dùng lại [ResendGate] — đúng bài toán nó sinh ra để giải, chỉ khác là khoá lên dòng log thay vì lượt bắn.
     */
    fun send(ctx: Context) {
        if (!castOn(ctx)) { Log.i(TAG, "bỏ gửi vị trí: Cluster Cast OFF (cụm chưa live)"); return }
        val app = ctx.applicationContext
        runCatching {
            app.sendBroadcast(
                Intent(ACTION).setPackage(VIETMAP_PKG)
                    .putExtra("x", x(app)).putExtra("y", y(app)),
            )
            val msg = "gửi VM_BUBBLE_POS x=${x(app)} y=${y(app)} → $VIETMAP_PKG"
            if (synchronized(logGate) { logGate.shouldSend(System.currentTimeMillis(), msg) }) Log.i(TAG, msg)
        }.onFailure { Log.w(TAG, "gửi VM_BUBBLE_POS lỗi", it) }
    }

    /**
     * Gọi lúc mở app / bong bóng dựng lại: áp vị trí đã lưu (nếu Cast ON).
     *
     * K8 (1.70): nhịp làm tươi 2 s của `FloatingBubbleService` gọi vào đây ⇒ [ĐO xe 2026-09-17] một broadcast
     * + một dòng log mỗi 2 s cho cùng toạ độ. Qua [ResendGate]: cùng toạ độ chỉ gửi lại sau
     * [RESEND_MIN_MS]; toạ độ đổi (người dùng kéo) gửi ngay — [set] gọi thẳng [send] nên không qua cổng này.
     */
    fun applyOnOpen(ctx: Context) {
        if (!castOn(ctx)) return
        val app = ctx.applicationContext
        // F6 (B1 2026-09-25): [ĐO máy ảo] broadcast + log mỗi 16 s tới VietMap KHÔNG cài. Gate "gói có cài" (PackageManager,
        // nhớ 60 s) TRƯỚC ResendGate — không gửi, không ghi nhận, log "bỏ" đúng một lần.
        if (!vietMapInstalled(app)) return
        val xy = x(app) to y(app)
        val go = synchronized(gate) { gate.shouldSend(System.currentTimeMillis(), xy) }
        if (go) send(app)
    }

    private fun vietMapInstalled(app: Context): Boolean = synchronized(installedGate) {
        installedGate.installed(
            System.currentTimeMillis(),
            probe = { PackageQueries.packageInfo(app.packageManager, VIETMAP_PKG) != null },
            onFirstAbsence = { Log.i(TAG, "bỏ gửi vị trí: $VIETMAP_PKG không cài (dò lại mỗi ${PKG_TTL_MS / 1000}s)") },
        )
    }

    private const val RESEND_MIN_MS = 15_000L
    private val gate = com.byd.clusternav.launcher.ResendGate(RESEND_MIN_MS)

    /** Cùng một dòng log về cùng toạ độ chỉ ghi lại sau 5 phút — xem khối LOG-41KB ở KDoc [send]. */
    private const val LOG_REPEAT_MS = 300_000L
    private val logGate = com.byd.clusternav.launcher.ResendGate(LOG_REPEAT_MS)
    private const val PKG_TTL_MS = 60_000L
    private val installedGate = InstalledPackageGate(PKG_TTL_MS)
}
