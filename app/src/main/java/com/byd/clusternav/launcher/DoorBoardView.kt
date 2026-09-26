package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * BẢNG *CỬA & KHOANG* — **WP3-v5**: hình xe nay là **ẢNH bitmap** ([CarImageLayer]) + **chấm màu** tại vị trí từng
 * bộ phận đang mở ([CarLayout]), thay hình vector cũ (owner bỏ vector car — chê xấu/tốn token).
 *
 * ## Ranh giới giữ nguyên (vẫn đúng luật `GroupTileWiringContractTest`)
 * Ô vẽ **không phán xét**: bộ phận nào đang mở / sắc thái / câu kết luận đều do [GroupBoard.doorPlan] (`:core`,
 * kiểm off-car) quyết; ở đây không có một mã datum (`door_*`) hay ngưỡng nào. Chỗ nối là enum [CarPart].
 *
 * ## Vì sao bảng này KHÔNG vẽ chữ nào
 * Chữ trên ẢNH (màu bất kỳ do người dùng thay) khó đọc, nên overlay chỉ có **chấm vị trí** (câu hỏi *"khoang NÀO
 * đang mở"* là câu về không gian). Dòng kết luận [DoorBoardPlan.footer] từng nằm ở chân bảng nhưng owner đã gỡ
 * dòng kết luận khỏi widget tổng hợp (2026-09-23, `84f91e6`).
 *
 * ## 2.74 · UX7 — hai thứ đã dọn (cùng lượt soát bốn ô vẽ Canvas)
 *  • **đường chết**: `footerP`/`clip()`/`FOOTER_BASELINE`/`FOOTER_RATIO`/sàn cỡ chữ vẫn được dựng và tính mỗi lượt
 *    vẽ, còn `drawText` thì 0 chỗ — đúng bệnh CLAUDE.md §8 mà bảng lốp vừa gặp ở `TyreBoard.verdict`. Đã xoá; muốn
 *    trả dòng kết luận về thì nó phải quay lại bằng một quyết định của owner, không phải bằng mã còn sót.
 *  • **dải trống đáy**: khung ảnh dừng ở `0.86 × cao` để chừa chỗ cho đúng dòng chữ đã gỡ ⇒ 14 % chiều cao ô bỏ
 *    không và hình xe nhỏ hơn cần thiết. Nay dùng tới [CAR_BOTTOM] — **cùng số 0.98 của bảng lốp** (task 3 owner
 *    2026-09-25: *"dùng gần hết chiều cao, bớt dải trống đáy"*), để hai bảng dùng chung ảnh xe trông cùng cỡ.
 *
 * WP1/WP3-v5: 0 viền · 0 blur · 0 shadow. Mọi `Paint` cấp phát MỘT LẦN; màu phân giải một lần.
 */
internal class DoorBoardView(context: Context) : View(context) {

    private var plan: DoorBoardPlan? = null
    private val car = CarImageLayer(context) { invalidate() }

    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val carDst = RectF()
    private val content = RectF()

    private val colMut = Color.parseColor(KachiTheme.MUT)
    private val colAccent = Color.parseColor(KachiTheme.ACCENT)
    private val colAmber = Color.parseColor(KachiTheme.AMBER)
    private val colRed = Color.parseColor(KachiTheme.RED)

    fun set(model: GroupBoardModel) {
        val next = GroupBoard.doorPlan(model)
        val unchanged = plan == next   // (owner 2026-09-25 giật): chỉ vẽ lại khi plan ĐỔI
        plan = next
        if (!unchanged) invalidate()
    }

    /** Màu theo sắc thái — cùng thang với các bảng khác (đỏ nguy · hổ phách để ý · nhấn đang-mở · mờ nghỉ). */
    private fun tint(tone: GroupTone): Int = when (tone) {
        GroupTone.ALERT -> colRed
        GroupTone.WARN -> colAmber
        GroupTone.ACTIVE -> colAccent
        GroupTone.NEUTRAL -> colMut
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val p = plan ?: return
        if (w <= 0f || h <= 0f || p.parts.isEmpty()) return

        // Ảnh xe dùng gần hết chiều cao ô (UX7 — xem KDoc lớp: dải trống đáy là di sản dòng kết luận đã gỡ).
        val pad = w * PAD_RATIO
        carDst.set(pad, h * TOP_INSET, w - pad, h * CAR_BOTTOM)
        car.ensure(width, (h * CAR_BOTTOM).toInt())
        car.draw(canvas, carDst)

        // Chấm màu tại từng bộ phận ĐANG MỞ, theo neo chuẩn hoá trên đúng khung ảnh đã vẽ.
        car.contentRect(carDst, content)
        val r = minOf(content.width(), content.height()) * DOT_RATIO
        for (i in p.parts.indices) {
            val s = p.parts[i]
            if (!s.open) continue
            val a = CarLayout.part(s.part)
            dot.color = tint(s.tone)
            canvas.drawCircle(content.left + a.x * content.width(), content.top + a.y * content.height(), r, dot)
        }
    }

    fun release() = car.release()

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        car.release()
    }

    private companion object {
        const val PAD_RATIO = 0.04f
        const val TOP_INSET = 0.03f
        /** Mép DƯỚI khung ảnh — **cùng số với [TyreBoardView] `CAR_BOTTOM`** (xem KDoc lớp, UX7). */
        const val CAR_BOTTOM = 0.98f
        /** Bán kính chấm theo cạnh nhỏ của khung xe. */
        const val DOT_RATIO = 0.065f
    }
}
