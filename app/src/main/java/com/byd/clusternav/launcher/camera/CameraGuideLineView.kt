package com.byd.clusternav.launcher.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * Lớp vẽ **vạch chuẩn khoảng cách** đè lên hình camera. Quyết định *vạch ở đâu* nằm ở [CameraGuide] (`:core`,
 * thuần, có test); lớp này chỉ vẽ.
 *
 * ## Vì sao ra tệp riêng thay vì thêm vào `CameraOverlayView`
 * Tệp đó đã **đúng 500 dòng**, tức sát trần cứng của CLAUDE.md §4.1. Thêm vào đấy là đẩy nó qua trần và bài canh
 * kích thước đỏ ngay — đã xảy ra đúng như vậy với `WorkspacePrefs.kt` trong cùng phiên này.
 *
 * ## Vì sao viền đen quanh vạch trắng
 * Hình camera hông là bê tông xám, vạch kẻ vàng, thân xe sáng — một vạch trắng trơn sẽ **biến mất** trên nền
 * sáng đúng lúc cần nhất (đỗ sát tường sáng). Vẽ nét đen dày hơn trước rồi nét trắng đè lên cho ra viền tương
 * phản ở cả hai chiều, mẹo cũ của phụ đề. Rẻ hơn hẳn việc đo độ sáng nền rồi đổi màu.
 *
 * ## Không chặn chạm
 * `View` này nằm TRÊN video nên nếu nó nhận sự kiện chạm thì mọi thao tác lên overlay chết theo. Đặt
 * `isClickable = false` và không cài `onTouchListener` ⇒ sự kiện rơi xuyên xuống lớp dưới.
 */
class CameraGuideLineView(context: Context) : View(context) {

    /** Vị trí vạch theo chiều cao, `0f..1f`; `null` = không vẽ gì. Xem [CameraGuide.positionFor]. */
    var position: Float? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 0, 0)
        style = Paint.Style.STROKE
    }

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
    }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        val p = position ?: return
        if (width <= 0 || height <= 0) return
        val d = resources.displayMetrics.density
        line.strokeWidth = LINE_DP * d
        halo.strokeWidth = (LINE_DP + HALO_DP * 2) * d
        val y = height * p.coerceIn(0f, 1f)
        // Chừa hai đầu một khoảng: vạch chạm sát mép trông như viền của khung chứ không như một mốc đo.
        val inset = INSET_DP * d
        val x0 = inset
        val x1 = width - inset
        if (x1 <= x0) return
        canvas.drawLine(x0, y, x1, y, halo)
        canvas.drawLine(x0, y, x1, y, line)
    }

    private companion object {
        const val LINE_DP = 2f
        const val HALO_DP = 1f
        const val INSET_DP = 6f
    }
}
