package com.byd.clusternav.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Outline
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout

/**
 * NHÚNG một app THẬT vào ô workspace bằng **`android.app.ActivityView`** (hidden API, kiểu AAOS CarLauncher).
 *
 * App chạy trên một **virtual display cỡ đúng ô** → nó tưởng mình FULLSCREEN nên **KHÔNG có caption freeform**,
 * lọt khít khung; [ActivityView] tự **chuyển tiếp cảm ứng** vào display đó (khác VirtualDisplay tay phải tự inject).
 * Bo góc bằng `clipToOutline`. Kachi vẽ slot-head (⇄/✕) ĐÈ LÊN (view anh em, luôn thấy/bấm được).
 *
 * ⚠ Cần Kachi chạy như **launcher HỆ THỐNG** (priv-app) để nhúng app bất kỳ (display tin cậy + INTERNAL_SYSTEM_WINDOW).
 * Không đủ quyền → [available] = false / [embed] thất bại → caller (WorkspaceView) fallback thẻ/freeform.
 * Reflection toàn bộ (ActivityView @hide) nên KHÔNG cần compile-time API; hidden-api policy nới trên emulator/xe.
 */
class SlotAppHost(context: Context, private val cornerRadiusPx: Float) : FrameLayout(context) {

    private var activityView: View? = null
    private val h = Handler(Looper.getMainLooper())
    private var embedded = false

    init {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, cornerRadiusPx) }
        }
        runCatching {
            val cls = Class.forName("android.app.ActivityView")
            val av = cls.getConstructor(Context::class.java).newInstance(context) as View
            addView(av, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            activityView = av
        }
    }

    fun available(): Boolean = activityView != null

    /** ID của virtual display mà ActivityView tạo (reflect field `mVirtualDisplay`), null nếu chưa sẵn. */
    fun virtualDisplayId(): Int? = runCatching {
        val av = activityView ?: return null
        val f = av.javaClass.getDeclaredField("mVirtualDisplay").apply { isAccessible = true }
        val vd = f.get(av) ?: return null
        val disp = vd.javaClass.getMethod("getDisplay").invoke(vd)
        disp.javaClass.getMethod("getDisplayId").invoke(disp) as? Int
    }.getOrNull()

    /**
     * Chiếu [pkg] lên virtual display của ActivityView **qua SHELL** (`am start --display <vd> --windowingMode 1`) —
     * KHÔNG dùng `ActivityView.startActivity` (bị chặn `INTERNAL_SYSTEM_WINDOW` khi sideload). App chạy FULLSCREEN
     * trên VD → **không caption, không scale**, khít khung. Poll tới khi VD sẵn sàng. [sh] = dadb uid-shell.
     */
    fun embedViaShell(pkg: String, sh: (String) -> String) {
        if (embedded || activityView == null) return
        val comp = context.packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToShortString() ?: return
        tryShellStart(comp, sh, 40)
    }

    private fun tryShellStart(comp: String, sh: (String) -> String, tries: Int) {
        if (embedded || tries <= 0) return
        val vd = virtualDisplayId()
        if (vd != null && vd > 0) {
            embedded = true
            Thread { runCatching { sh("am start --display $vd --windowingMode 1 -n '$comp'") } }.start()
        } else {
            h.postDelayed({ tryShellStart(comp, sh, tries - 1) }, 150)
        }
    }

    /** Nhúng [pkg]; retry `startActivity` tới khi virtual display của ActivityView sẵn sàng (surface tạo xong). */
    fun embed(pkg: String) {
        val av = activityView ?: return
        if (embedded) return
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return
        tryStart(av, intent, 30)
    }

    private fun tryStart(av: View, intent: Intent, tries: Int) {
        if (embedded || tries <= 0) return
        val ok = runCatching {
            av.javaClass.getMethod("startActivity", Intent::class.java).invoke(av, intent); true
        }.getOrDefault(false)
        if (ok) embedded = true else h.postDelayed({ tryStart(av, intent, tries - 1) }, 150)
    }

    /** Giải phóng virtual display + task nhúng. */
    fun release() {
        embedded = false
        runCatching { activityView?.javaClass?.getMethod("release")?.invoke(activityView) }
    }

    override fun onDetachedFromWindow() { release(); super.onDetachedFromWindow() }

    companion object {
        /** ActivityView có tồn tại trên nền tảng không (Android 10 có; bị gỡ ở Android 12+). */
        fun classAvailable(): Boolean = runCatching { Class.forName("android.app.ActivityView"); true }.getOrDefault(false)

        /**
         * Nhúng THẬT dùng được không: cần ActivityView + quyền [INTERNAL_SYSTEM_WINDOW] ĐÃ CẤP (chỉ có khi Kachi
         * **platform-signed** = build vào ROM xe). App sideload / emulator Google KHÔNG có → trả false → caller
         * fallback freeform. (Đã xác minh: emulator google_apis không cấp perm signature này.)
         */
        fun embeddingUsable(ctx: Context): Boolean =
            classAvailable() && ctx.checkSelfPermission("android.permission.INTERNAL_SYSTEM_WINDOW") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

        /**
         * Nhúng-qua-SHELL dùng được không: chỉ cần **ActivityView tồn tại** (để tạo VD + nhận surface). Việc mở app
         * lên VD do dadb uid-shell làm (`am start --display`), KHÔNG cần `INTERNAL_SYSTEM_WINDOW`. Đúng đường "cast":
         * app chạy fullscreen trên 1 display (ảo) → không caption, không scale. Emulator android-29 có ActivityView.
         */
        fun shellEmbedUsable(): Boolean = classAvailable()
    }
}
