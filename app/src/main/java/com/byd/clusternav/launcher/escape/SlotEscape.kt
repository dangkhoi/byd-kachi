package com.byd.clusternav.launcher.escape

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.byd.clusternav.launcher.DefaultHome
import com.byd.clusternav.modules.clustercast.ClusterProfile
import java.io.IOException
import java.util.concurrent.Executors

/**
 * ═══ 2.98 · R7 (SLOT-ESCAPE-POLICY) — cửa mức TIẾN TRÌNH của "app thoát ô ⇒ freeform đúng khung ô" ════════════════════════
 *
 * Owner duyệt 09/10 (*"ok"*), spec `docs/specs/kachi-298-plan.html` R7, evidence `docs/diagnostics/oncar-freeform-waze-2026-10-09.md`.
 * Quyết định thuần ở `:core` ([SlotEscapePlan] · [SlotEscapeRun] · [EscapeCoverPlan]); tệp này chỉ: bật/tắt theo đời ROM,
 * MỘT luồng nền cho mọi lệnh R7 (xếp hàng, không chồng), dấu bền, và chỗ nối tới màn nhà đang hiện ([SlotEscapeHome]).
 *
 * Bật khi ĐỦ (đọc trong tiến trình, 0 lệnh shell): hồ sơ đời xe có bảng mã ([ClusterProfile.taskBinder]) đúng đời API đang chạy,
 * VÀ freeform có hiệu lực theo cờ (`enable_freeform_support` = 1 — Kachi tự gieo, `FreeformSeedPolicy`) hoặc ROM khai tính
 * năng. Cờ bật mà đầu xe chưa khởi động lại thì freeform chưa có hiệu lực ⇒ lệnh nhận đọc lại không khớp ⇒ tự trả (mode 1).
 */
internal object SlotEscape {

    const val TAG = "KachiEscape"

    /** Nhịp đo vẫn chạy khi màn chính khuất ngay sau khi một ô bắt đầu được đo ([com.byd.clusternav.launcher.SlotLiveProbe]). */
    const val PAUSED_GRACE_SWEEPS = 3

    private const val FREEFORM_FEATURE = "android.software.freeform_window_management"
    private const val FREEFORM_SETTING = "enable_freeform_support"

    /** MỘT luồng cho mọi lệnh R7 — nhận / đối chiếu / hỏi của host không bao giờ chạy chồng nhau. */
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "kachi-slot-escape").apply { isDaemon = true } }

    /** Màn nhà Kachi đang HIỆN (onStart → onStop). Chỉ để biết gửi việc cho ai — không quyết định gì bằng nó (CLAUDE.md §5). */
    @Volatile var current: SlotEscapeHome? = null

    init {
        // Dịch vụ trợ năng nối (lại) — `AccessibilityRebind` tắt/bật dịch vụ khi phím kẹt ⇒ cửa sổ che cũ chết theo token
        // (`EscapeCoverOverlay.drop`); không có mốc này thì lớp che chỉ về lại ở lần đổi vòng đời/ô kế (soát Pass 7). 0 lệnh khi không dấu.
        com.byd.clusternav.modules.navaccess.A11yOverlayPort.onAttach { current?.requestReconcile(refront = false, why = "a11y-up") }
    }

    /** Bảng mã dùng được trên máy này (`null` = R7 tắt, ô đi đường 2.93). Đọc trong tiến trình, rẻ. */
    fun codes(ctx: Context): TaskBinderCodes? {
        val c = ClusterProfile.resolveCached(ctx).taskBinder ?: return null
        if (!c.usableOn(Build.VERSION.SDK_INT)) return null
        val feature = runCatching { ctx.packageManager.hasSystemFeature(FREEFORM_FEATURE) }.getOrDefault(false)
        val flag = Settings.Global.getInt(ctx.contentResolver, FREEFORM_SETTING, 0) != 0
        return c.takeIf { feature || flag }
    }

    fun run(ctx: Context, sh: (String) -> String, codes: TaskBinderCodes, liftPx: Int = 0): SlotEscapeRun =
        SlotEscapeRun(
            sh, codes, ClusterProfile.resolveCached(ctx).cameraSignature, DefaultHome.shownComponents(ctx), EscapeMarkerStore(ctx),
            homeAllowed = homeAllowed(ctx), liftPx = liftPx, tops = tops,
        )

    /** Đỉnh ổn định display 0 đã học (`EscapeFit`) — một bản cho cả tiến trình; RAM, chỉ là gợi ý để đỡ một lượt resize. */
    private val tops = SlotEscapeRun.TopMemory()

    /**
     * Owner 09/10 #2 — đẩy thanh tiêu đề freeform lên trên ô ([EscapeFit.taskRect]) CHỈ khi có lớp che (dịch vụ trợ năng đã nối):
     * không có lớp che thì thanh tiêu đề đè lên đầu màn nhà mà không ai che ⇒ giữ nó trong ô như trước. Đổi trạng thái trợ năng ⇒
     * lượt đối chiếu kế kéo lại khung (`SlotEscapeRun.reconcile` so khung task thật).
     */
    fun liftPx(densityDpi: Int): Int =
        if (com.byd.clusternav.modules.navaccess.A11yOverlayPort.service != null) EscapeCoverPlan.captionPx(densityDpi) else 0

    /**
     * Soát Pass 7: `HomeActivityCmd.GO_HOME` mở home MẶC ĐỊNH của hệ — Kachi cài kiểu thường (alias HOME tắt, chưa "Đặt làm màn hình
     * chính") thì đó là launcher BYD đè lên Kachi. Mọi bước "Home trước" của R7 (trả · bảng mở) chỉ chạy khi Kachi là home mặc định;
     * không đọc được ⇒ coi như không (an toàn). Một lượt `resolveActivity` (PackageManager, trong tiến trình).
     */
    fun homeAllowed(ctx: Context): Boolean = DefaultHome.isCurrent(ctx) == true

    /** Gói còn cài (đối chiếu dấu — gói đã gỡ thì xoá dấu, không lệnh). */
    fun installed(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getApplicationInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Gửi việc xuống luồng R7; lỗi kênh (dadb) ⇒ một dòng log, dấu còn ⇒ lượt sau xử lý tiếp. */
    fun submit(what: String, block: () -> Unit) {
        io.execute {
            try {
                block()
            } catch (e: IOException) {
                Log.w(TAG, "$what hỏng kênh: ${e.javaClass.simpleName}")
            } catch (e: RuntimeException) {
                Log.w(TAG, "$what hỏng: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    /**
     * Host ô [slot] sắp mở [pkg] vào màn ảo — gói đang được quản dưới dạng freeform còn sống thì KHÔNG mở (mở vào màn ảo là kéo
     * task khỏi display 0 và app lại tự thoát). Gọi trên luồng nền của host (chặn, ≤ 1 lệnh đọc; 0 lệnh khi không có dấu).
     * `true` ⇒ host để ô trống phía dưới cửa sổ freeform và màn nhà đang hiện được nhờ đưa app lên lại.
     */
    fun claimsLive(ctx: Context, slot: Int, pkg: String, sh: (String) -> String): Boolean {
        val codes = codes(ctx) ?: return false
        val live = try {
            run(ctx, sh, codes).claims(slot, pkg)
        } catch (e: IOException) {
            false
        } catch (e: RuntimeException) {
            false
        }
        if (live) {
            Log.i(TAG, "ô $slot: $pkg đang ở freeform đúng khung ⇒ host không mở lại vào màn ảo")
            current?.requestReconcile(refront = true, why = "host-claims")
        }
        return live
    }

    /** Kênh shell vừa lên (`LauncherWindows.sweepFloating("shell-up")`) — lượt đối chiếu đầu tiên của tiến trình (quét khởi động). */
    fun shellUp() { current?.requestReconcile(refront = true, why = "shell-up") }

    /** Bảng của Kachi (ngăn kéo / Cài đặt / sửa bố cục) của màn [activity] mở/đóng — `LauncherWindows`. Màn khác ⇒ bỏ qua. */
    fun panels(activity: android.app.Activity, open: Boolean) { current?.takeIf { it.owns(activity) }?.onPanels(open) }

    /**
     * 2.98 · R16 (OQ6 [P3]) — MỘT móc cho mọi hộp thoại Kachi có thể hiện trên màn nhà (gọi ngay sau `show()` ở các cửa dựng hộp thoại
     * dùng chung: `SettingsDialogs` · `UpdateFlow` · `KachiHomeDisclaimer`). [ĐO máy ảo 09/10] bộ chọn hồ sơ (chạm chip QUA lớp che) là
     * cửa sổ `ty=APPLICATION` của task Kachi ⇒ nằm DƯỚI cửa sổ Waze freeform (Window #13 dưới #12), người lái không thấy. Hiện ⇒ như
     * bảng mở (gỡ che + Home qua rào camera: app xuống dưới); đóng (decor rời cửa sổ) ⇒ đối chiếu + đưa app lên lại. Theo dõi bằng
     * `OnAttachStateChangeListener` của decor — không đè listener đóng/huỷ của bên gọi. Màn nhà nhận tin là màn đang hiện LÚC hộp
     * thoại hiện (báo đóng về đúng nó, kể cả khi nó đã khuất). 0 lệnh khi không có app đang được quản.
     */
    fun shade(dialog: android.app.Dialog?): android.app.Dialog? {
        val d = dialog ?: return null
        val decor = d.window?.decorView ?: return d
        var home: SlotEscapeHome? = null
        fun shown(on: Boolean) {
            if (on == (home != null)) return
            if (on) home = current?.takeIf { it.ownsContext(d.context) }?.also { it.onDialog(true) }
            else { home?.onDialog(false); home = null }
        }
        if (d.isShowing) shown(true)
        decor.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) = shown(true)
            override fun onViewDetachedFromWindow(v: android.view.View) = shown(false)
        })
        return d
    }
}
