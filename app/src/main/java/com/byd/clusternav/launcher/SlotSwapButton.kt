package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Nút ⇄ (đổi app/widget) của một ô — **một bộ dựng cho cả hai đường hiện ô**.
 *
 * Owner 2026-09-12: *"nút chỉ nổi trên khung để đổi, không được ảnh hưởng layout/size khung; CHỈ 1 nút, canh GIỮA
 * trên cùng, KHÔNG thanh nền, KHÔNG nút ✕."* Ô có hai đường vẽ tuỳ lúc mở app: **nhúng** ([WorkspaceView.slotHead],
 * view nằm trong ô) hoặc **freeform + lớp phủ** ([OverlayHeads], cửa sổ `TYPE_APPLICATION_OVERLAY` đặt lên trên
 * cửa sổ app khi kênh dadb có). Trước 2026-09-13 mỗi đường tự vẽ một kiểu: đường nhúng đã là 1 nút ⇄, đường phủ vẫn
 * là thanh cũ (nền đục + chấm + tên + ⇄ + ✕) ⇒ owner thấy "lúc 1 icon lúc 2 icon" trên cùng máy ảo — không phải
 * lỗi ngẫu nhiên, mà là đường nào thắng lúc mở. Một bộ dựng ở đây ⇒ hai đường không thể lệch nhau nữa
 * (`SlotHeadParityContractTest` khoá).
 *
 * Hình: tròn [Sp.ICON_L], scrim [KachiTheme.SCRIM_BTN] + viền [Sp.STROKE] màu [KachiTheme.ON_ACCENT] (soát ảnh pha 2:
 * ruột chỉ hơn nền ô 1.19:1 ⇒ phải có viền 2dp mới tách khỏi nền), icon `ic-swap` tô [KachiTheme.ON_ACCENT]. Không
 * theo chủ đề — nó nằm trên pixel của app đang chiếu.
 */
object SlotSwapButton {

    /** Nút trần (chưa có cha) — [ICON_L]×[ICON_L]. */
    fun build(context: Context, onTap: () -> Unit): ImageView = ImageView(context).apply {
        val r = KachiTheme.iconRes("ic-swap")
        if (r != 0) { setImageResource(r); setColorFilter(Color.parseColor(KachiTheme.ON_ACCENT)) }
        setPadding(KachiTheme.dpi(context, Sp.S), KachiTheme.dpi(context, Sp.S), KachiTheme.dpi(context, Sp.S), KachiTheme.dpi(context, Sp.S))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(Color.parseColor(KachiTheme.SCRIM_BTN))
            setStroke(KachiTheme.dpi(context, Sp.STROKE), Color.parseColor(KachiTheme.ON_ACCENT))
        }
        setOnClickListener { onTap() }
    }

    /**
     * Nút đặt trong một khung TRONG SUỐT, canh giữa trên cùng, cách mép trên [Sp.XS] — đúng hình học mà
     * [WorkspaceView.slotHead] đã được owner duyệt bằng ảnh. Khung cao [Sp.SLOT_HEAD_CLEAR] (= XS + ICON_L + XS).
     */
    fun centered(context: Context, onTap: () -> Unit): FrameLayout = FrameLayout(context).apply {
        // Đích chạm: nút VẼ 32dp (hình đã duyệt) nhưng vùng CHẠM là khung TOUCH×SLOT_HEAD_CLEAR quanh nó — soát ảnh
        // v2 [ĐO] ⌀48px = 32dp < 48dp. Khung trong suốt nhận chạm, nút bên trong không nhận (clickable=false) để một
        // cú chạm không rơi vào hai lớp.
        val visual = build(context) {}.apply { isClickable = false; isFocusable = false }
        val hit = FrameLayout(context).apply {
            isClickable = true
            setOnClickListener { onTap() }
            addView(
                visual,
                FrameLayout.LayoutParams(KachiTheme.dpi(context, Sp.ICON_L), KachiTheme.dpi(context, Sp.ICON_L), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                    .also { it.topMargin = KachiTheme.dpi(context, Sp.XS) },
            )
        }
        addView(
            hit,
            FrameLayout.LayoutParams(KachiTheme.dpi(context, Sp.TOUCH), KachiTheme.dpi(context, Sp.SLOT_HEAD_CLEAR), Gravity.TOP or Gravity.CENTER_HORIZONTAL),
        )
    }

    /**
     * Biến thể cho đường **freeform + lớp phủ** ([OverlayHeads]): cùng nút ⇄ giữa, nhưng khung là một DẢI màu khung ô
     * ([KachiTheme.CELL], bo góc trên) trải hết bề rộng ô.
     *
     * Vì sao có nền ở đây trong khi owner nói "không background": [ĐO 2026-09-13, máy ảo] khi app chạy cửa sổ freeform,
     * **hệ thống tự vẽ caption** (thanh xám + nút ▭ ✕) ở mép trên cửa sổ app; nút ⇄ không nền để caption lộ ra ⇒ mắt
     * vẫn thấy "2 icon" (⇄ của ta + ✕ của hệ). Dải này che caption — nó là **khung ô** kéo lên trên cửa sổ app, không
     * phải thanh tiêu đề (không tên, không chấm, không ✕). Đường nhúng không có caption nên dùng [centered] (trong suốt).
     * [CHƯA BIẾT] ROM xe có vẽ caption không — nếu không, có thể đổi OverlayHeads sang [centered]; đo ở buổi test xe.
     */
    fun strip(context: Context, onTap: () -> Unit): FrameLayout = centered(context, onTap).apply {
        val r = KachiTheme.dpi(context, Sp.RADIUS_L).toFloat()
        background = GradientDrawable().apply {
            setColor(Color.parseColor(KachiTheme.CELL))
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
    }

    /** Bề cao khung = [Sp.SLOT_HEAD_CLEAR]. */
    fun overlayHeightPx(context: Context): Int = KachiTheme.dpi(context, Sp.SLOT_HEAD_CLEAR)

}
