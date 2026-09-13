package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * BẢNG *CỬA & KHOANG* (U9 pha 2) — hình xe nhìn từ trên, **mỗi bộ phận mở được nằm đúng chỗ của nó**: bốn vạt cửa
 * bật ra hai bên, nắp cốp ở đuôi, ô nóc + rèm trên nóc, hai tai gương ở đầu xe; một dòng KẾT LUẬN ở chân bảng.
 *
 * Owner 2026-09-13: *"vẽ hình xe cho những chức năng tổng hợp có hình xe như áp suất lốp, mở cửa, mở cốp"*. Pha 1 đã
 * làm lốp · cảm biến đỗ · ADAS; đây là phần **cửa/cốp** còn thiếu. Spec `docs/specs/kachi-car-boards.html` R7.
 *
 * ## Vì sao nhóm này phải rời dải STRIP
 * [ĐO] nhóm *Cửa & khoang* có **10 ô con dùng chung một icon cửa** ⇒ [GroupBoardModel.iconsDistinguish] trả `false`
 * ⇒ dải STRIP đã phải **bỏ icon**, còn lại mười ô chữ giống hệt nhau xếp hai hàng 5. Câu người lái hỏi ở đây là
 * *"xe tôi kín chưa — cửa NÀO đang mở?"*, tức một câu về **không gian**; mười ô chữ không trả lời được nó, đúng lý do
 * [SideBoardView] đã ra đời cho nhóm ADAS.
 *
 * ## NGỮ PHÁP VẼ — bám nguyên bộ icon v2: *nét = vật thể, vùng tô = bộ phận đang được nói tới*
 *  • **Đang mở / đang gập ⇒ VÙNG TÔ** mang màu sắc thái. Đây là chỗ thông tin nằm, nên nó phải là thứ đậm nhất.
 *  • **Đã đọc, đang đóng ⇒ NÉT MỜ** ([KachiTheme.MUT2]). Chọn nét (không phải bỏ hẳn) vì người xem cần thấy **xe
 *    này CÓ bộ phận đó** thì mới đọc được ý nghĩa của việc nó không sáng; một vạt cửa biến mất khi đóng làm bảng
 *    đổi hình mỗi lần cửa đóng/mở, và lúc đóng thì không phân biệt được với *"xe không có cửa đó"*.
 *  • **Chưa đọc được ⇒ vẫn NÉT, nhưng mờ hơn** ([DIM]). Off-car là ca THƯỜNG, không phải ca lỗi — nhưng nó **khác**
 *    ca "đã đọc, đang đóng", và gộp hai cái đó lại là nói sai với người lái (dòng chân bảng cũng đếm riêng).
 *
 * ## ⚠ THỨ TỰ VẼ có chủ ý — [ĐO] tai gương và vạt cửa trước CHỒNG NHAU
 * Hình tai gương trái `(7.6,8.9)·(4.7,9.7)·(7.4,10.9)` nằm đè lên vạt cửa trước-trái `x 3.9…7.55 · y 9.1…13.1`:
 * tính giao hai đa giác được **2.06 / 2.82 ≈ 73%** diện tích tai gương nằm trong vạt cửa. Trong bộ icon chuyện đó
 * không bao giờ lộ ra vì mỗi ô chỉ vẽ MỘT bộ phận; ở bảng này thì cả tám vẽ cùng lúc.
 *
 * Cách xử: gương vẽ **TRƯỚC** (lớp dưới), vạt cửa vẽ **SAU CÙNG** ⇒ khi cả hai cùng sáng thì **cửa mở thắng chỗ**,
 * vì cửa mở là chuyện an toàn ([GroupTone.ALERT]) còn gương gập chỉ là tiện nghi ([GroupTone.ACTIVE]). Và để việc
 * "thắng chỗ" đó không thành **mất thông tin im lặng**, dòng chân bảng do `:core` dựng **luôn** kể tên mọi khoang
 * đang mở/gập kể cả khi hình của nó bị che (xem `GroupBoard.doorVerdict`).
 *
 * ## Quy ước vẽ — giống hệt ba bảng `BOARD` đã có
 *  • Mọi `Paint` cấp phát MỘT LẦN ở field, màu phân giải MỘT LẦN — KHÔNG cấp phát/parse trong [onDraw].
 *  • Mọi cỡ tính theo cạnh nhỏ nhất, cỡ chữ có **SÀN** ([KachiSpace.BOARD_LABEL_MIN]) ⇒ ô nhỏ thì chữ vẫn đọc được.
 *  • Ô vẽ **không tự quyết định gì**: bộ phận nào, sắc thái nào, mở hay không, câu kết luận là gì — tất cả đến từ
 *    [GroupBoard.doorPlan] ở `:core` (kiểm được off-car). Ở đây không có một mã datum nào, đúng luật
 *    `GroupTileWiringContractTest` (*tầng vẽ không được chép mã thành viên*).
 */
internal class DoorBoardView(context: Context) : View(context) {

    private var plan: DoorBoardPlan? = null

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor(KachiTheme.MUT2)
        // Đầu/khớp nét TRÒN — khung xe là path của bộ icon v2, vốn khai `strokeLineCap/Join="round"`.
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    /** Vùng tô của một bộ phận ĐANG mở — Paint riêng để không phải đổi `style` qua lại trên cùng một cây cọ. */
    private val partFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Nét của bộ phận đang ĐÓNG / chưa đọc — mảnh hơn nét thân xe để thân vẫn là hình chính. */
    private val partStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val noteP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val midP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.parseColor(KachiTheme.MUT)
    }

    // ── Hình xe dùng chung ([CarFrames]) — cấp phát MỘT LẦN, đúng luật "không cấp phát trong onDraw" ──
    private val carMatrix = Matrix()

    /** Bản chép để biến hình — KHÔNG `transform` thẳng vào path dùng chung của [CarFrames]. */
    private val carPath = Path()
    private val srcBox = RectF()
    private val carDst = RectF()
    private val partBox = RectF()

    /**
     * Màu NỀN THẺ — dùng làm mực "khoét" trên vùng tô, và làm đường tách hai vùng tô chồng nhau.
     *
     * [ĐO] ảnh máy ảo 17:34 (bản đo có bơm dữ liệu): nhãn `%` vẽ bằng CHÍNH màu vùng tô ⇒ **không đọc được một chữ
     * nào** (accent trên accent, đỏ trên đỏ), và ô nóc + rèm cùng ACTIVE nên chúng dính thành **một khối xanh**.
     * Nền thẻ đảo chiều đúng trong cả hai chủ đề (tối: mực sẫm trên nền màu · sáng: mực nhạt trên nền màu) nên nó là
     * một lựa chọn, không phải hai.
     */
    private val colCard = Color.parseColor(KachiTheme.CARD2)
    private val colMut = Color.parseColor(KachiTheme.MUT)
    private val colMut2 = Color.parseColor(KachiTheme.MUT2)
    private val colAccent = Color.parseColor(KachiTheme.ACCENT)
    private val colAmber = Color.parseColor(KachiTheme.AMBER)
    private val colRed = Color.parseColor(KachiTheme.RED)

    /** SÀN nét khung xe (R1: không mảnh hơn 1.6dp tương đương) — bậc "nét" của thang, cùng ba bảng kia. */
    private val strokeFloorPx = Sp.dpf(context, Sp.STROKE)

    /** SÀN cỡ chữ quy ra pixel — tính một lần (density không đổi trong đời một View). */
    private val labelFloorPx = Sp.dpf(context, Sp.BOARD_LABEL_MIN)

    /**
     * Đặt dữ liệu. Kế hoạch dựng ở `:core` ngay tại đây (KHÔNG trong [onDraw]): khác [SideBoardView], phép chọn của
     * bảng này **không phụ thuộc bề cao ô** — bộ phận nào cũng phải ở đúng chỗ của nó, không có chuyện bớt hàng.
     */
    fun set(model: GroupBoardModel) {
        plan = GroupBoard.doorPlan(model)
        invalidate()
    }

    /**
     * Sắc thái → màu.
     *
     * [GroupTone.ACTIVE] dùng [KachiTheme.ACCENT] chứ không [KachiTheme.INK] như dải STRIP: ở dải, "đang bật" được
     * nói bằng **nền + viền** accent của ô con nên chữ giữ màu mực; trên Canvas không có ô con nào, chính vùng tô là
     * thứ duy nhất nói được điều đó — tô bằng màu mực thì nóc đang mở trông y hệt một hình trang trí.
     */
    private fun tint(tone: GroupTone): Int = when (tone) {
        GroupTone.ALERT -> colRed
        GroupTone.WARN -> colAmber
        GroupTone.ACTIVE -> colAccent
        GroupTone.NEUTRAL -> colMut2
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val p = plan ?: return
        if (w <= 0f || h <= 0f || p.parts.isEmpty()) return
        val min = minOf(w, h)

        outline.strokeWidth = maxOf(min * OUTLINE_RATIO, strokeFloorPx)
        partStroke.strokeWidth = maxOf(min * PART_STROKE_RATIO, strokeFloorPx * PART_STROKE_FLOOR)
        // SÀN đứng sau tỉ lệ: ô to thì tỉ lệ thắng (chữ lớn theo ô), ô nhỏ thì sàn thắng (chữ vẫn đọc được).
        noteP.textSize = maxOf(min * NOTE_RATIO, labelFloorPx)
        midP.textSize = maxOf(min * MID_RATIO, labelFloorPx)

        // Khung phóng = THÂN **kèm mọi bộ phận** (xem `CarFrames.openFrameBounds`): vạt cửa mở ra NGOÀI thân, nên
        // phóng theo thân không thôi sẽ cắt cụt đúng bốn thứ bảng này sinh ra để hiện.
        val pad = w * PAD_RATIO
        carDst.set(pad, h * TOP_INSET, w - pad, h * FOOTER_TOP)
        // Chừa nửa nét mỗi phía: `Path` là ĐƯỜNG TÂM nét, không phải mép mực.
        carDst.inset(outline.strokeWidth / 2f, outline.strokeWidth / 2f)
        CarFrames.openFrameBounds(srcBox)
        CarFrames.fit(srcBox, carDst, carMatrix)

        // Ba lớp, và thân xe chen vào GIỮA: gương/nóc/rèm/cốp nằm dưới nét thân (chúng là bộ phận CỦA thân), còn bốn
        // vạt cửa nằm trên cùng vì chúng bật ra ngoài thân — và vì cửa mở là thứ phải thắng chỗ (xem KDoc lớp).
        drawLayer(canvas, p, LAYER_UNDER)
        carPath.set(CarFrames.topFrame)
        carPath.transform(carMatrix)
        canvas.drawPath(carPath, outline)
        drawLayer(canvas, p, LAYER_OVER)
        // Nhãn `%` vẽ SAU CÙNG — không thì bộ phận vẽ sau che mất nhãn của bộ phận vẽ trước. [ĐO] ô nóc `y 9.3…15.3`
        // và tấm rèm `y 9.9…16.0` chồng gần trọn lên nhau, nên nhãn của nóc bị chính tấm rèm phủ.
        for (i in p.parts.indices) {
            val s = p.parts[i]
            if (s.note.isNotEmpty()) drawNote(canvas, s)
        }

        midP.color = colMut
        // ⚠⚠ [ĐO] ảnh 17:37 (2 cột): dòng kết luận **TRÀN RA NGOÀI Ô** — `"… Sunroof · 40 %  Sunshade · 80 %  Door
        // mirro…"` chạy đè sang cả ô *Lốp* bên cạnh. `Canvas.drawText` không tự cắt và cũng không tự thu; câu này
        // dài **theo dữ liệu** (càng nhiều khoang đang mở càng dài) nên nó là ca thường, không phải ca biên.
        //
        // Thu trước, cắt sau: thu giữ được TRỌN câu (thứ người lái cần), cắt chỉ là lưới cuối khi thu đã chạm sàn
        // chữ đọc được — cùng thứ tự ưu tiên "đọc được trước, đủ chữ sau" mà `SideBoardView` đã chốt.
        val room = w - pad * 2f
        val need = midP.measureText(p.footer)
        if (need > room) midP.textSize = maxOf(midP.textSize * room / need, labelFloorPx)
        canvas.drawText(clip(p.footer, room, midP), w / 2f, h * FOOTER_BASELINE, midP)
    }

    /**
     * Vẽ mọi bộ phận thuộc [layer], theo **đúng thứ tự khai** của kế hoạch.
     *
     * Duyệt bằng chỉ số chứ không bằng `for (s in list)`: vòng lặp trên `List` cấp phát một `Iterator` mỗi lượt, mà
     * [onDraw] chạy theo nhịp trạng thái xe — cùng lý do mọi `Paint`/`Path` ở đây là field.
     */
    private fun drawLayer(canvas: Canvas, p: DoorBoardPlan, layer: Int) {
        for (i in p.parts.indices) {
            val s = p.parts[i]
            if (layerOf(s.part) != layer) continue
            val shape = CarFrames.part(s.part) ?: continue
            carPath.set(shape)
            carPath.transform(carMatrix)
            if (s.open) {
                partFill.color = tint(s.tone)
                canvas.drawPath(carPath, partFill)
                // ⚠ Đường TÁCH bằng màu nền thẻ: hai vùng tô chồng nhau mà cùng sắc thái thì không có ranh giới nào
                // — [ĐO] ô nóc + tấm rèm cùng ACTIVE dính thành MỘT khối xanh, người xem đếm được một bộ phận thay
                // vì hai. Vẽ ở mọi bộ phận (không chỉ hai cái chồng nhau) để không phải nhớ cặp nào chồng cặp nào.
                partStroke.color = colCard
                partStroke.alpha = OPAQUE
                canvas.drawPath(carPath, partStroke)
            } else {
                partStroke.color = colMut2
                // Đã đọc & đang đóng vs CHƯA đọc được — hai câu khác nhau, nói bằng hai độ mờ.
                partStroke.alpha = if (s.available) OPAQUE else DIM
                canvas.drawPath(carPath, partStroke)
            }
        }
    }

    /**
     * Nhãn `%` vẽ **bên trong** chính bộ phận (cốp · nóc · rèm là ba mảng đủ rộng để chứa một con số).
     *
     * ## ⚠ Vì sao neo theo từng bộ phận chứ không đều là tâm
     * [ĐO] trong hệ toạ độ icon: ô nóc chiếm `y 9.3…15.3`, tấm rèm `y 11.6…16.0` — chúng **chồng nhau** (rèm nằm
     * dưới kính nóc, đúng vật lý). Hai nhãn cùng canh giữa sẽ cách nhau 1.5 đơn vị (~63px ở ô hai cột) trong khi
     * chữ cao ~24px ⇒ dính nhau. Neo nóc lên **mép trên** và rèm xuống **mép dưới** thì khoảng cách thành ~5 đơn vị.
     */
    private fun drawNote(canvas: Canvas, s: CarPartState) {
        if (!CarFrames.partBounds(s.part, partBox)) return
        carMatrix.mapRect(partBox)
        // Màu phải theo thứ NẰM DƯỚI chữ, không theo bộ phận: nhãn đặt TRONG vùng tô thì phải "khoét" bằng màu nền
        // thẻ (tô-trên-tô là không đọc được), nhãn đặt NGOÀI thì nền là thân xe nên dùng chính màu sắc thái.
        noteP.color = when {
            !s.open -> colMut
            noteInside(s.part) -> colCard
            else -> tint(s.tone)
        }
        val y = partBox.top + partBox.height() * anchorOf(s.part)
        canvas.drawText(clip(s.note, partBox.width()), partBox.centerX(), y + noteP.textSize * TEXT_MID_LIFT, noteP)
    }

    /**
     * Cắt [s] cho vừa [room] pixel, có dấu `…`.
     *
     * `Canvas.drawText` **không tự cắt** — chữ dài sẽ vẽ tràn ra khỏi bộ phận và đè lên bộ phận bên cạnh. Vẽ tràn
     * còn tệ hơn cắt: nó trông như một lỗi vẽ chứ không như "còn chữ nữa".
     */
    private fun clip(s: String, room: Float, paint: Paint = noteP): String {
        if (room <= 0f) return ""
        if (paint.measureText(s) <= room) return s
        var n = s.length
        while (n > 0 && paint.measureText(s.take(n) + ELLIPSIS) > room) n--
        return if (n <= 0) "" else s.take(n) + ELLIPSIS
    }

    private companion object {
        /** `Paint.alpha` theo thang 0..255 (khác `View.alpha` 0..1f). */
        const val DIM = 110
        const val OPAQUE = 255

        /** Dấu cắt chữ — ký hiệu, không phải chữ để dịch. */
        const val ELLIPSIS = "…"

        /** Lớp vẽ: bộ phận CỦA thân (gương · nóc · rèm · cốp) nằm dưới nét thân; bốn vạt cửa nằm trên. */
        const val LAYER_UNDER = 0
        const val LAYER_OVER = 1

        /** Xem KDoc lớp về việc vì sao cửa phải là lớp trên cùng (73% tai gương nằm trong vạt cửa trước). */
        fun layerOf(p: CarPart): Int = if (p.isDoor) LAYER_OVER else LAYER_UNDER

        /**
         * Chỗ neo nhãn `%` trong bộ phận, theo phần trăm chiều cao của chính bộ phận đó (0 = mép trên).
         *
         * Chỉ ba bộ phận có số; hai trong ba nằm chồng nhau trên nóc nên phải tách ra (xem [drawNote]).
         */
        fun anchorOf(p: CarPart): Float = when (p) {
            // ÂM = **phía trên** mép trên của bộ phận. Ô nóc `y 9.3…15.3` bị tấm rèm `y 9.9…16.0` phủ gần trọn, nên
            // chỗ duy nhất còn trống cho nhãn của nó là vạt mũi xe (`y 4.6…9.3`, không bộ phận nào vẽ ở đó).
            CarPart.SUNROOF -> -0.30f
            // [ĐO] ảnh 17:36: `0.80` đặt chữ đúng lên NẾP GẤP thứ hai của tấm rèm (`y 14.2…14.8` hệ icon) ⇒ nhãn
            // trông như bị **gạch ngang**. Hộp rèm là `y 9.9…16.0`, hai nếp ở `12.8…13.4` và `14.2…14.8`, nên dải
            // trống rộng nhất còn lại là `14.8…16.0`; `0.91` đưa chữ vào giữa dải đó.
            CarPart.SUNSHADE -> 0.91f
            else -> 0.50f
        }

        /** Nhãn có nằm TRONG vùng tô của chính bộ phận không ⇒ quyết định màu mực (xem [drawNote]). */
        fun noteInside(p: CarPart): Boolean = anchorOf(p) in 0f..1f

        // ── Tỉ lệ hình học (cùng họ số với ba bảng BOARD đã có) ──
        const val OUTLINE_RATIO = 0.018f
        const val PART_STROKE_RATIO = 0.010f

        /** Nét bộ phận được phép mảnh hơn nét thân, nhưng không dưới 60% sàn — dưới mức đó là dưới một pixel mực. */
        const val PART_STROKE_FLOOR = 0.6f
        const val NOTE_RATIO = 0.052f
        const val MID_RATIO = 0.056f
        const val PAD_RATIO = 0.03f
        const val TOP_INSET = 0.02f
        const val FOOTER_TOP = 0.88f
        const val FOOTER_BASELINE = 0.97f

        /** Nâng đường chân chữ lên để chữ canh GIỮA theo mắt (nửa chiều cao nét, trừ phần đuôi chữ). */
        const val TEXT_MID_LIFT = 0.35f
    }
}
