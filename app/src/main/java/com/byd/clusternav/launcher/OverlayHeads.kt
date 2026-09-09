package com.byd.clusternav.launcher

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R

/**
 * Dải "slot-head" NỔI (overlay `TYPE_APPLICATION_OVERLAY`) đặt lên TRÊN cửa sổ freeform của app trong ô.
 *
 * Vì sao cần: cửa sổ freeform AOSP có caption (nút phóng to+✕) do hệ vẽ, đè lên slot-head mà launcher vẽ trong
 * view (view nằm DƯỚI cửa sổ app) → không thấy/bấm được nút đổi/đóng. Overlay nằm TRÊN mọi thứ → che caption +
 * hiện chấm màu + tên + ⇄ (đổi app) + ✕ (đóng), luôn bấm được. Đây là đường chạy được cho app **sideload**
 * (freeform + overlay); khi Kachi build vào ROM xe (platform-signed) thì dùng ActivityView nhúng sạch [SlotAppHost].
 *
 * Cần quyền vẽ overlay (SYSTEM_ALERT_WINDOW) — Kachi tự cấp qua dadb (appops) như ClusterNav cấp cho bong bóng.
 */
class OverlayHeads(private val activity: Activity) {

    /** 1 dải: khung ô (toạ độ MÀN HÌNH) + tên + màu chấm + callback ⇄/✕. */
    data class Head(val left: Int, val top: Int, val width: Int, val height: Int, val name: String, val dotColor: String,
                    val onSwap: () -> Unit, val onClose: () -> Unit)

    private val wm = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun clear() {
        ACTIVE.forEach { (v, w) -> runCatching { w.removeViewImmediate(v) } }
        ACTIVE.clear()
    }

    /** Vẽ lại toàn bộ dải theo [heads]. Bỏ qua nếu chưa có quyền vẽ overlay. */
    fun show(heads: List<Head>) {
        clear()
        if (!android.provider.Settings.canDrawOverlays(activity)) return
        val hpx = dp(34)
        heads.forEach { hd ->
            addOverlay(buildBar(hd), hd.width, hpx, hd.left, hd.top, touchable = true)   // header che caption + ⇄/✕
        }
    }

    private fun addOverlay(v: View, w: Int, h: Int, x: Int, y: Int, touchable: Boolean) {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val lp = WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }
        runCatching { wm.addView(v, lp); ACTIVE.add(v to wm) }.onFailure { android.util.Log.i("KACHI", "overlay add fail: $it") }
    }

    private fun buildBar(hd: Head): View {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), 0, dp(8), 0)
            // ĐỤC 100% (bo góc trên) để che KÍN caption freeform + KHỚP MÀU header của widget (khỏi lệch 2 màu). Màu chung = HEADER_BG.
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F1520"))
                cornerRadii = floatArrayOf(dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), 0f, 0f, 0f, 0f)
            }
        }
        bar.addView(View(activity).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(hd.dotColor)) }
        }, LinearLayout.LayoutParams(dp(9), dp(9)).also { it.marginEnd = dp(8) })
        bar.addView(TextView(activity).apply {
            text = hd.name; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(btn("ic-swap", hd.onSwap), btnLp())
        bar.addView(btn("ic-close", hd.onClose), btnLp())
        return bar
    }

    private fun btnLp() = LinearLayout.LayoutParams(dp(24), dp(24)).also { it.marginStart = dp(6) }

    private fun btn(icon: String, onClick: () -> Unit): View = ImageView(activity).apply {
        val r = KachiTheme.iconRes(icon); if (r != 0) { setImageResource(r); setColorFilter(Color.WHITE) }
        setPadding(dp(5), dp(5), dp(5), dp(5))
        background = GradientDrawable().apply { cornerRadius = dp(7).toFloat(); setColor(Color.parseColor("#33000000")) }
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private companion object {
        /** Tĩnh: (view, WM đã add nó) — clear() gỡ bằng đúng WM, bền cả khi Activity bị tạo lại (tránh orphan/leak). */
        private val ACTIVE = ArrayList<Pair<View, WindowManager>>()
    }
}
