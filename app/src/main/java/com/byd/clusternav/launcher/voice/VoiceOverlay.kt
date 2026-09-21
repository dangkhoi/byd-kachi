package com.byd.clusternav.launcher.voice

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiSpace as Sp
import com.byd.clusternav.launcher.KachiTheme
import com.byd.clusternav.launcher.card
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiType

/**
 * ═══ V1 pha NGHE · TẤM NHỎ Ở GÓC MÀN — *"máy đang nghe · nghe được gì · trả lời gì"* ═════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R11**. Thuần VIEW: không biết micro, không biết Vosk, không biết
 * ý định — nó nhận chữ và vẽ. [VoiceSession] là thứ duy nhất gọi nó.
 *
 * ## Vì sao là CỬA SỔ của hệ thống, không phải một view nhét vào màn chính
 * Phiên nghe mở được từ ba lối (ô *Nói với xe* trên thanh nút · pill mic trên thanh trên · phím vô-lăng), và ở
 * lối thứ ba màn chính có thể đang bị một app chiếm chỗ trong ô chiếu. Một view con của màn chính sẽ nằm **dưới**
 * app đó — tức người lái nói mà không thấy gì. Quyền `SYSTEM_ALERT_WINDOW` thì launcher đã tự cấp từ vòng kiểm
 * (`LauncherRequirements.OVERLAY`), cùng đường mà nút nổi chiếu cụm đang dùng.
 *
 * ## Vì sao PHỦ TOÀN MÀN dù tấm chữ chỉ nằm một góc
 * Hai cử chỉ huỷ phải chạy được: **chạm ra ngoài** và **phím Back**. Cửa sổ `WRAP_CONTENT` không nhận được cú
 * chạm bên ngoài nó, còn `FLAG_NOT_FOCUSABLE` thì không nhận được phím. Nền trong suốt nên nhìn vẫn là "một tấm
 * nhỏ ở góc"; và nó chỉ sống tối đa 8 giây (trần cứng ở [VoiceSession]) nên việc nó chặn chạm trong khoảng ấy
 * là **có chủ ý**: đang nghe thì cú chạm tiếp theo nên là *"thôi, không nói nữa"*.
 */
class VoiceOverlay(
    private val ctx: Context,
    /** Người dùng muốn thoát (chạm ra ngoài / Back). */
    private val onCancel: () -> Unit,
) {

    private val wm = ctx.getSystemService(WindowManager::class.java)

    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var action: TextView
    private var root: View? = null

    /** Đang hiện hay không — [VoiceSession] hỏi để khỏi gỡ hai lần. */
    val showing: Boolean get() = root != null

    fun show() {
        if (root != null) return
        val view = build()
        val type =
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // KHÔNG `FLAG_NOT_FOCUSABLE`: thiếu tiêu điểm thì không có `dispatchKeyEvent` ⇒ mất đường thoát
            // bằng Back. `FLAG_WATCH_OUTSIDE_TOUCH` không cần vì cửa sổ này đã phủ toàn màn.
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        )
        // Không làm tối màn phía dưới: người lái vẫn phải thấy đường và thấy app đang chạy.
        lp.dimAmount = 0f
        if (runCatching { wm.addView(view, lp) }.isFailure) return
        root = view
    }

    fun dismiss() {
        val v = root ?: return
        root = null
        runCatching { wm.removeView(v) }
    }

    /**
     * Vẽ một trạng thái.
     *
     * @param titleRes dòng đầu (*"Đang nghe…"* / *"Đã hiểu"* / *"Không nghe rõ"*).
     * @param text dòng thân — chữ partial đang nghe, hoặc câu trả lời. Rỗng ⇒ ẩn hẳn dòng (không để một dòng
     *   trống co giãn làm tấm chữ nhảy cỡ mỗi lần partial đổi).
     * @param actionText nhãn nút gợi ý (vd *"Mở Cài đặt"*), `null` ⇒ không có nút.
     */
    fun render(titleRes: Int, text: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
        if (root == null) return
        title.setText(titleRes)
        body.text = text
        body.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        action.visibility = if (actionText == null) View.GONE else View.VISIBLE
        action.text = actionText.orEmpty()
        action.setOnClickListener { onAction?.invoke() }
    }

    // ── dựng view ────────────────────────────────────────────────────────────────────────────────

    private fun build(): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = KachiTheme.card(ctx, Sp.RADIUS_XL, KachiTheme.BAR_TOP)   // WP1 · R1.1 — không viền
            val p = dpi(ctx, Sp.L)
            setPadding(p, p, p, p)
        }
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(
            ImageView(ctx).apply {
                KachiTheme.iconRes("ic-mic").let { if (it != 0) setImageResource(it) }
                setColorFilter(c(KachiTheme.ACCENT))
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            },
            LinearLayout.LayoutParams(dpi(ctx, Sp.ICON_M), dpi(ctx, Sp.ICON_M)),
        )
        title = TextView(ctx).apply {
            setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.SECTION, bold = true)
            setPadding(dpi(ctx, Sp.S), 0, 0, 0)
            setText(R.string.kachi_voice_listening)
        }
        head.addView(title)
        card.addView(head)

        body = TextView(ctx).apply {
            setTextColor(c(KachiTheme.INK2)); KachiType.apply(this, KachiType.BODY)
            setPadding(0, dpi(ctx, Sp.S), 0, 0)
            maxLines = MAX_BODY_LINES
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = dpi(ctx, CARD_MAX_W)
            visibility = View.GONE
        }
        card.addView(body)

        action = TextView(ctx).apply {
            setTextColor(c(KachiTheme.ACCENT)); KachiType.apply(this, KachiType.BODY, bold = true)
            minHeight = dpi(ctx, Sp.TOUCH)
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        card.addView(action)

        val hint = TextView(ctx).apply {
            setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
            setPadding(0, dpi(ctx, Sp.XS), 0, 0)
            setText(R.string.kachi_voice_cancel_hint)
        }
        card.addView(hint)

        // Lớp phủ trong suốt bắt cú chạm ra ngoài + phím Back. Xem KDoc lớp về vì sao nó phủ toàn màn.
        return object : FrameLayout(ctx) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    onCancel(); return true
                }
                return super.dispatchKeyEvent(event)
            }

            /**
             * ═══ [ĐO xe 2026-09-18] Lấy TIÊU ĐIỂM là kéo cả thanh hệ thống lên cùng ═══════════════════════
             *
             * Owner: *"overlay kéo taskbar hệ thống lên — không muốn cái này"*. Gốc: cửa sổ này **cố ý** không
             * có `FLAG_NOT_FOCUSABLE` (thiếu tiêu điểm thì mất đường thoát bằng Back — xem KDoc [show]), nhưng
             * cờ ẩn thanh hệ thống thì Android đọc từ **cửa sổ đang có tiêu điểm**. Màn chính giữ cờ đó
             * (`goImmersiveWindow`); overlay không ⇒ giây nó nhận tiêu điểm là giây status/nav/taskbar hiện lại,
             * và nó **ở lại** cả khi tấm chữ đã tắt (màn chính chỉ áp lại cờ khi tiêu điểm quay về).
             *
             * ⇒ overlay mang **cùng bộ cờ** với màn chính. `IMMERSIVE_STICKY` để một cú quệt cạnh chỉ hiện thanh
             * tạm rồi tự ẩn — người lái không mất đường vào thanh hệ thống, chỉ không bị nó **ghim** lên.
             * `dimAmount` vẫn 0 và không có `FLAG_DIM_BEHIND`: đây là việc của thanh hệ thống, không phải một
             * phép làm tối màn.
             *
             * Áp ở CẢ hai mốc, mỗi mốc đóng một khe khác nhau: [onAttachedToWindow] cho lượt đầu (cửa sổ mới
             * thêm), [onWindowFocusChanged] cho mỗi lượt **lấy lại** tiêu điểm (hộp thoại xác nhận / lượt nghe
             * nối / một cửa sổ khác chen vào rồi rút).
             */
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                goImmersive(this)
            }

            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (hasWindowFocus) goImmersive(this)
            }
        }.apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFocusableInTouchMode = true
            goImmersive(this)
            // Thanh hệ thống được hệ thống cho hiện lại (quệt cạnh, một app khác xin) ⇒ áp lại. Điều kiện
            // `FULLSCREEN` chưa bật là thứ chặn vòng lặp: lượt áp lại của chính ta bắn listener lần nữa với cờ
            // ĐÃ bật ⇒ nhánh này không chạy tiếp.
            @Suppress("DEPRECATION")
            setOnSystemUiVisibilityChangeListener { vis ->
                if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) goImmersive(this)
            }
            addView(
                card,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    val m = dpi(ctx, Sp.XXL)
                    marginEnd = m; bottomMargin = m
                },
            )
            setOnTouchListener { _, ev ->
                // Chỉ huỷ khi cú chạm nằm NGOÀI tấm chữ: chạm vào nút "Mở Cài đặt" bên trong phải là bấm nút,
                // không phải huỷ. `ACTION_DOWN` (không phải UP) vì người lái quệt tay là đủ ý "thôi".
                if (ev.action == MotionEvent.ACTION_DOWN && !inside(card, ev)) { onCancel(); true } else false
            }
        }
    }

    private fun inside(v: View, ev: MotionEvent): Boolean {
        val x = ev.x.toInt(); val y = ev.y.toInt()
        return x >= v.left && x <= v.right && y >= v.top && y <= v.bottom
    }

    /**
     * Cùng **đúng** bộ cờ mà màn chính dùng (`KachiHomeWiring.goImmersiveWindow`) — hai bề mặt lệch cờ nhau thì
     * thanh hệ thống hiện/ẩn theo cửa sổ nào đang có tiêu điểm, tức nhấp nháy theo mỗi lượt nói.
     *
     * Xe chạy Android 10 (API 29) ⇒ `systemUiVisibility`; `WindowInsetsController` là API 30+. Deprecated trên
     * SDK biên dịch nhưng nó là API **duy nhất** có tác dụng trên nền tảng đích — cùng lý do đã ghi ở
     * `goImmersiveWindow` và `ClusterNavActivity`.
     */
    @Suppress("DEPRECATION")
    private fun goImmersive(v: View) {
        v.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    private companion object {
        /** Bề rộng tối đa của tấm chữ (dp) — đủ cho một câu trả lời, không lấn nửa màn. */
        const val CARD_MAX_W = 420

        /** Câu trả lời dài (vd gói lệnh báo từng bước) vẫn phải đọc được mà không đẩy tấm chữ cao lên mãi. */
        const val MAX_BODY_LINES = 4
    }
}
