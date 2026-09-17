package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * BẢNG ÁP SUẤT LỐP 4 BÁNH (W4 · vẽ lại ở lượt kiểm toán UX 2026-09-12) — hình xe nhìn từ trên, **mỗi bánh một ô
 * giá trị đặt đúng chỗ bánh đó**, kèm một dòng KẾT LUẬN.
 *
 * Spec `docs/specs/kachi-unified-capability-tile.html` §4.4 (R6–R8) + kiểm toán UX mục 1.
 *
 * ## ⚠⚠ Vì sao phải vẽ lại — [ĐO] ảnh máy ảo 2026-09-12 (bản trước)
 * Bản trước vẽ số **trần** ở bốn góc khung, cách thân xe rất xa và không có ô chứa:
 *  • **thứ bậc chữ BỊ ĐẢO**: chữ viết tắt vị trí (`TT`/`TP`/`ST`/`SP`) có mực **cao 34px** trong khi *giá trị* —
 *    thứ người ta mở ô này để xem — chỉ là dấu gạch **cao 11px**. Tức chữ to nhất trong ô là chữ ít giá trị nhất;
 *  • **số rời khỏi bánh**: mép số cách thân xe **158px** (trái) / **162px** (phải), với ~246px trống ngoài rìa ⇒
 *    mắt không ghép được "số này là bánh nào", đúng thứ mà cách xếp theo không gian phải giải quyết;
 *  • **đơn vị chỉ có ở chân bảng**: muốn biết `2.4` là bar hay psi phải đọc xuống dòng cuối;
 *  • **không có kết luận**: ô hứa trả lời *"lốp tôi ổn không"* mà người xem vẫn phải tự so bốn số;
 *  • **hứa 8 mục, hiện 4**: nhiệt độ từng bánh nằm chung một dòng phụ với chữ viết tắt và lý do, nên khi có cả ba
 *    thì dòng đó dài quá và bị cắt.
 *
 * Khuôn mới bám nguyên khuôn `BOARD` mà kiểm toán gọi là đúng nhất: **thân xe ở giữa · các ô giá trị áp sát thân
 * theo đúng vị trí không gian · một dòng chân bảng**. Mọi bảng `BOARD` nhìn ra ngay là cùng một họ — nay còn
 * [DoorBoardView] (hai bảng radar/sơ-đồ-bên đã xoá 2026-09-16 cùng toàn bộ ADAS/an toàn, owner).
 *
 * ## U9 (2026-09-13) — hình xe nay là CHÍNH path của bộ icon v2 · P3 (2026-09-17) — mức tả thực (1)
 * Thân + kính + bốn bánh lấy từ [CarArtSource] (chuỗi path SINH từ `design/car/top.svg`, cùng nguồn với icon), thay
 * cho `drawRoundRect` + `drawLine` + bốn vệt bo góc tự vẽ; P3 tô thân bằng chuyển sắc MÀU SƠN, kính có phản chiếu,
 * đèn có quầng — bằng gradient, không blur ([CarArtPainter]). Hai hệ quả đo được:
 *  • bảng lớn và icon 24dp là **một chiếc xe** — trước đó là hai hình khác nhau cạnh nhau trên cùng màn;
 *  • bánh thành **vùng tô mang màu trạng thái** đúng chỗ bánh thật (thò ra ngoài thân), và ô giá trị bám vào
 *    hộp bao của chính cái bánh ấy chứ không vào mép thân ⇒ liên hệ "số này là bánh nào" do hình học bảo đảm.
 *
 * ## Quy ước vẽ (giữ nguyên từ bản W4 — phần này vốn đã đúng)
 *  • Mọi `Paint` cấp phát MỘT LẦN ở field, màu phân giải MỘT LẦN — KHÔNG cấp phát/parse trong [onDraw].
 *  • Cỡ chữ/nét tính theo cạnh nhỏ nhất ⇒ bất biến với cỡ ô (ô 1/4 màn hay ô full đều đúng tỉ lệ).
 *  • Màu đọc từ [KachiTheme], **không** hard-code hex tại chỗ ⇒ đổi bảng màu là đổi theo.
 *  • Ô vẽ **không tự quyết định gì**: trạng thái + kết luận đến từ [TyreBoard] (`:core`, test off-car), chuỗi số và
 *    đơn vị do chỗ gọi format qua lớp đơn vị. Ở đây không có một ngưỡng nào — đó là điều kiện để chỉ có MỘT nơi
 *    định nghĩa non/căng/lệch (bản trước bản W4 từng có `t[i] < 2.2` viết tại chỗ = ngưỡng thứ ba của dự án).
 */
class TyreBoardView(context: Context) : View(context) {

    /** 4 bánh theo thứ tự [TyreCorner]; rỗng = chưa có dữ liệu (vẽ 4 ô với dấu gạch). */
    private var readings: List<TyreReading> = emptyList()

    /** Chuỗi số đã format sẵn theo đơn vị người dùng chọn (song song [readings]); `null` = "—". */
    private var values: List<String?> = emptyList()

    /** Nhãn đơn vị (vd "bar" / "psi") — vẽ **ngay cạnh từng số**, không còn nằm một mình ở chân bảng. */
    private var unitLabel: String = ""

    /** Nhiệt độ TỪNG BÁNH đã format sẵn kèm đơn vị (song song [readings]); `null` = chưa đọc. */
    private var temps: List<String?> = emptyList()

    /** Dòng KẾT LUẬN ở chân bảng — do [TyreBoard.verdict] quyết định, chỗ gọi có thể nối thêm ghi chú bằng chứng. */
    private var verdict: String = ""

    /**
     * VISUAL-REFRESH P3 — hình xe mức tả thực (1) ([CarArtPainter] qua [CarArtSource]): thân chuyển sắc màu sơn,
     * kính có phản chiếu, đèn có quầng; bốn bánh tô theo [CarPartStyle] (`:core`). Nét thân có SÀN [strokeFloorPx].
     * Trước P3 bảng này vẽ nét thân bằng `outline` MUT2 và tô bánh bằng một `Paint` tại chỗ.
     */
    private val painter = CarArtPainter()

    /** Nền ô giá trị. Paint RIÊNG — KHÔNG đổi màu của [tyrePaint] rồi trả lại (quên trả một lần là sai màu im lặng). */
    private val cellFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.parseColor(KachiTheme.CARD2)
    }

    /** Viền ô giá trị — mang màu trạng thái, nên bánh sai nhìn ra được cả khi chưa đọc con số. */
    private val cellStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** Số — thứ TO NHẤT trong ô. Align LEFT vì số và đơn vị xếp thành một cặp phải tự canh giữa (xem [drawPair]). */
    private val bigP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT; isFakeBoldText = true; color = Color.parseColor(KachiTheme.INK)
    }

    /** Đơn vị — ngay cạnh số, nhỏ hơn hẳn để không tranh chỗ với con số. */
    private val unitP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT; color = Color.parseColor(KachiTheme.MUT)
    }
    private val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.parseColor(KachiTheme.MUT)
    }
    private val midP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; color = Color.parseColor(KachiTheme.MUT)
    }
    private val body = RectF()
    private val cell = RectF()

    /** Khung đã `layout` cho painter — chỉ dựng lại shader khi kích thước đổi, KHÔNG mỗi khung vẽ (AC5.3). */
    private val laidOut = RectF()

    // ── Hình xe dùng chung ([CarFrames] qua [CarArtSource]) — cấp phát MỘT LẦN ở field ────────────────────
    private val wheelBox = RectF()

    /** Mảnh bánh của từng góc, tra một lần (tên mảnh ↔ [TyreCorner] khoá ở [CarFrames.wheelIdOf]). */
    private val wheelParts: Map<TyreCorner, CarArtPart> =
        TyreCorner.values().associateWith { corner ->
            val id = CarFrames.wheelIdOf(corner)
            VectorCarArt.parts(CarFace.TOP).first { it.id == id }
        }

    /** Khoảng cách TÂM hàng bánh trước ↔ sau (hệ icon) — đo từ hình, không viết tay `6.8`. */
    private val wheelRowGap: Float = RectF().let { a ->
        val b = RectF()
        VectorCarArt.bounds(CarFace.TOP, CarFrames.wheelIdOf(TyreCorner.REAR_LEFT), a)
        VectorCarArt.bounds(CarFace.TOP, CarFrames.wheelIdOf(TyreCorner.FRONT_LEFT), b)
        a.centerY() - b.centerY()
    }

    /**
     * SÀN nét khung xe quy ra pixel — R1 (*"không mảnh hơn 1.6dp tương đương"*). Lấy [KachiSpace.STROKE] (2dp)
     * chứ không đẻ một số riêng: 2dp là bậc "nét" của thang, và nó ≥ 1.6dp nên thoả sàn ở mọi mật độ.
     */
    private val strokeFloorPx = Sp.dpf(context, Sp.STROKE)

    // Màu PHÂN GIẢI MỘT LẦN. `Color.parseColor` cắt chuỗi + parse số mỗi lần gọi; [onDraw] chạy đều đặn theo nhịp
    // trạng thái xe ⇒ để trong onDraw là rác bộ nhớ đúng chỗ KDoc trên hứa là không có.
    private val colInk = Color.parseColor(KachiTheme.INK)
    private val colMut = Color.parseColor(KachiTheme.MUT)
    private val colMut2 = Color.parseColor(KachiTheme.MUT2)
    private val colRed = Color.parseColor(KachiTheme.RED)
    private val colAmber = Color.parseColor(KachiTheme.AMBER)

    /**
     * Trần cỡ chữ của dòng kết luận, quy ra **pixel** — tính MỘT LẦN (density không đổi trong đời một View), đúng
     * cùng lối với các màu phân giải một lần ở trên: [onDraw] chạy theo nhịp trạng thái xe nên không được tra
     * `displayMetrics` mỗi khung.
     *
     * `applyDimension(COMPLEX_UNIT_SP, …)` chứ không nhân tay với `scaledDensity`: trường đó **đã bị đánh dấu bỏ**
     * (trình biên dịch cảnh báo trên bản này) và nó là một đường đổi sp→px thứ hai nằm ngoài mọi thang.
     */
    private val verdictCapPx =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, VERDICT_CAP_SP, resources.displayMetrics)

    /**
     * Đặt dữ liệu. [values]/[temps] **song song** với [readings] (đã đổi đơn vị + format ở chỗ gọi — ô vẽ KHÔNG tự
     * đổi đơn vị để chỉ có một nơi làm việc đó), [verdict] là kết luận đã quyết định ở `:core`.
     *
     * Độ dài được CHUẨN HOÁ về đúng `readings.size` ngay ở đây: chỗ gọi đưa danh sách lệch độ dài thì mọi bánh thiếu
     * hiện "—" một cách nhất quán, thay vì lệch chỉ số làm số của bánh này nhảy sang bánh khác.
     */
    fun set(
        readings: List<TyreReading>,
        values: List<String?>,
        unitLabel: String,
        temps: List<String?>,
        verdict: String,
    ) {
        this.readings = readings
        this.values = List(readings.size) { values.getOrNull(it) }
        this.unitLabel = unitLabel
        this.temps = List(readings.size) { temps.getOrNull(it) }
        this.verdict = verdict
        invalidate()
    }

    /** Màu theo trạng thái: non/căng = đỏ (nguy), lệch = hổ phách (để ý), bình thường = mực, chưa biết = mờ. */
    private fun colorFor(s: TyreStatus): Int = when (s) {
        TyreStatus.LOW, TyreStatus.HIGH -> colRed
        TyreStatus.UNEVEN -> colAmber
        TyreStatus.OK -> colInk
        TyreStatus.UNKNOWN -> colMut2
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val m = minOf(w, h)

        val outlinePx = maxOf(m * 0.018f, strokeFloorPx)
        cellStroke.strokeWidth = m * 0.014f
        // ── HÌNH XE = MỘT chiếc xe với bộ icon ([CarArtSource]), phóng giữ tỉ lệ ────────────────────────────
        // Thân xe (KHÔNG kể bánh) được phóng vào đúng khung `body` — bố cục ngang đã duyệt (thân hẹp, bề ngang dành
        // cho bốn ô giá trị) giữ nguyên; bốn bánh **thò ra ngoài** thân đúng như trên icon, và chúng mới là thứ ô
        // giá trị bám vào. `layout` chỉ dựng lại shader khi kích thước đổi (AC5.3).
        val bodyW = w * 0.24f
        val bodyH = h * 0.58f
        val cy = h * 0.45f
        body.set((w - bodyW) / 2f, cy - bodyH / 2f, (w + bodyW) / 2f, cy + bodyH / 2f)
        // Chừa nửa nét mỗi phía: `Path` là ĐƯỜNG TÂM nét, không phải mép mực ⇒ không chừa thì mũi xe bị ô cắt cụt.
        body.inset(outlinePx / 2f, outlinePx / 2f)
        if (body != laidOut) { painter.layout(CarFace.TOP, body, openFrame = false, strokePx = outlinePx); laidOut.set(body) }
        // Mặt "nghỉ": thân sơn + kính + đèn tắt — KHÔNG vạt cửa/gương/nắp (bảng lốp không nói về chúng); bánh vẽ ở đây
        // ở tone NEUTRAL rồi được vẽ ĐÈ bằng tone thật ở dưới — bánh trạng thái nằm trên là đúng thứ tự lớp §4.3.
        painter.draw(canvas, include = { p -> p.role != CarPartRole.FLAP && p.role != CarPartRole.MIRROR && p.role != CarPartRole.PANEL })

        // Ô giá trị cao bao nhiêu là do CHÍNH hình xe quyết: nó phải lọt giữa hai hàng bánh, nếu không hai ô của
        // cùng một bên chồng lên nhau ở ô hẹp. `mapRadius` trả đúng hệ số phóng vì ma trận giữ tỉ lệ.
        val wheelSpan = painter.mapRadius(wheelRowGap)
        val cellH = minOf(h * 0.24f, wheelSpan - m * 0.035f)

        // Cỡ chữ: tỉ lệ cạnh ô như trước, **nhưng không được cao hơn ô chứa nó**. Ở ô gần vuông, thân xe hẹp ⇒
        // hai hàng bánh gần nhau ⇒ ô thấp; giữ nguyên `m * 0.150` ở đó là chữ tràn ra ngoài ô giá trị.
        bigP.textSize = minOf(m * 0.150f, cellH * 0.58f)
        unitP.textSize = bigP.textSize * 0.45f
        // Dòng phụ = **cỡ NHÃN**, nhỏ hơn số 3.5 lần. [ĐO] vòng đầu tôi để 0.062 (46px với ô này) ⇒ chữ viết tắt
        // vị trí vẫn ra mực **cao 28px** trong khi giá trị off-car chỉ là dấu gạch dày 11px — tức thứ bậc VẪN đảo
        // đúng như kiểm toán đo được ở bản trước. Hạ về 0.042 (31px) cho nó đọc ra là một chú thích.
        subP.textSize = minOf(m * 0.042f, cellH * 0.22f)
        // ⚠ [SOÁT UI 2026-09-12] Dòng KẾT LUẬN (verdict) — gồm câu no-data "chưa đọc được áp suất · nhiệt chưa kiểm"
        // off-car — có TRẦN tuyệt đối. `m * 0.072` một mình cho ~43px ở ô Lốp lớn ⇒ câu "chưa có dữ liệu" thành chữ
        // TO NHẤT màn, to hơn cả tiêu đề nhóm (đảo thứ bậc). Trần theo sp (bất biến ô) giữ nó ở cỡ một câu kết luận;
        // ô nhỏ thì tỉ lệ vẫn thắng.
        //
        // ⚠⚠ [SOÁT ĐỘC LẬP 2026-09-12] …nhưng trần TUYỆT ĐỐI một mình lại đảo thứ bậc theo chiều NGƯỢC LẠI: dòng phụ
        // của từng bánh (`subP`, `m * 0.042`) KHÔNG bị trần, nên ở ô lớn nó VƯỢT dòng kết luận. [ĐO] mật độ 1.5 ⇒
        // trần = 24px, mà `sub` đạt 24px khi `m ≈ 571px` — và ô Lốp ở bố cục 1 ô có `m ≈ 900px` ⇒ **sub 37.8px vs
        // verdict 24px**, tức chú thích to gấp 1.6 lần kết luận. Cùng họ lỗi mà chính trần này sinh ra để chữa.
        // Nên trần có SÀN: kết luận không bao giờ nhỏ hơn chú thích nó kết luận về.
        midP.textSize = maxOf(minOf(m * 0.072f, verdictCapPx), subP.textSize)

        // Bốn ô giá trị ÁP SÁT **chính cái bánh** nó nói về: hai cột (trái/phải) × hai hàng (trước/sau). Khoảng
        // cách `gap` nhỏ có chủ ý, vì chính khoảng cách này là thứ nói "ô này là bánh đó" (bản trước để 158px nên
        // liên hệ đó mất).
        val gap = m * 0.045f
        val pad = w * 0.025f

        // Chưa có dữ liệu (off-car) ⇒ vẫn vẽ đủ 4 ô với "—" để bố cục không nhảy khi số về. Dùng CHÍNH toạ độ ở
        // trên (không hardcode lại) ⇒ hai nhánh không thể lệch nhau khi chỉnh bố cục.
        val rows = if (readings.size < 4) PLACEHOLDER_CORNERS else readings.map { it.corner }
        rows.forEachIndexed { i, corner ->
            // Vị trí ô ĐO TỪ HÌNH, không suy từ tên góc: bánh vẽ ở đâu thì ô giá trị đứng cạnh đúng ở đó. Chép tay
            // "trước-trái ⇒ trên-trái" là chỗ duy nhất lệch được giữa hình và số (kiểm kê U7 §2: bốn quy ước hậu
            // tố khác nhau cho cùng một góc xe — bảng sẽ vẫn vẽ đủ bốn ô, chỉ là gắn nhầm bánh).
            painter.mapBounds(CarFrames.wheelIdOf(corner), wheelBox)
            val cyc = wheelBox.centerY()
            if (wheelBox.centerX() < w / 2f) cell.set(pad, cyc - cellH / 2f, wheelBox.left - gap, cyc + cellH / 2f)
            else cell.set(wheelBox.right + gap, cyc - cellH / 2f, w - pad, cyc + cellH / 2f)
            val rd = readings.getOrNull(i)
            drawCell(canvas, m, corner, rd, values.getOrNull(i), temps.getOrNull(i))
            // Bánh = VÙNG TÔ mang màu trạng thái (*nét = vật thể, vùng tô = bộ phận đang được nói tới*): tone do
            // `:core` quyết ([CarPartStyle.tyreTone]), cách tô do bảng §4.4 quyết — bảng này KHÔNG chọn màu.
            val st = rd?.status ?: TyreStatus.UNKNOWN
            painter.drawPart(canvas, wheelParts.getValue(corner), CarPartStyle.look(CarPartRole.TYRE, CarPartStyle.tyreTone(st), st != TyreStatus.UNKNOWN))
        }

        midP.color = colMut
        canvas.drawText(verdict, w / 2f, h * 0.965f, midP)
    }

    /**
     * Một ô giá trị: **số (to nhất) + đơn vị ngay cạnh** ở dòng trên, **nhiệt độ / lý do sai + vị trí** ở dòng dưới.
     *
     * Chữ viết tắt vị trí xuống dòng phụ và dùng cỡ nhãn (bản trước nó là chữ to nhất ô — xem KDoc lớp): vị trí đã
     * được nói bằng **chỗ đặt ô**, chữ chỉ để xác nhận, nên nó không được to hơn con số.
     */
    private fun drawCell(canvas: Canvas, m: Float, corner: TyreCorner, rd: TyreReading?, value: String?, temp: String?) {
        val st = rd?.status ?: TyreStatus.UNKNOWN
        val col = colorFor(st)
        val radius = m * 0.035f
        canvas.drawRoundRect(cell, radius, radius, cellFill)
        cellStroke.color = if (st.alert) col else colMut2
        canvas.drawRoundRect(cell, radius, radius, cellStroke)

        val hasValue = value != null
        val numText = value ?: TelemetryView.PLACEHOLDER
        bigP.color = col
        // Đơn vị chỉ có nghĩa khi có số: "— bar" đọc như thể đơn vị là dữ liệu, mà nó không phải.
        val unit = if (hasValue) unitLabel else ""
        val numBase = cell.centerY() + bigP.textSize * 0.10f - subP.textSize * 0.60f
        drawPair(canvas, m, numText, unit, numBase)

        // Dòng phụ: LUÔN có nội dung. Thứ tự: vị trí · lý do sai · nhiệt độ — lý do trước nhiệt vì nó là thứ phải
        // xử lý ngay. Nhiệt độ nằm ở đây chính là 4 mục còn lại của lời hứa "8 mục".
        subP.color = if (st.alert) col else colMut
        val why = st.reason
        val sub = listOfNotNull(corner.shortLabel, why, temp).joinToString(" · ")
        canvas.drawText(sub, cell.centerX(), numBase + subP.textSize * 1.35f, subP)
    }

    /**
     * Vẽ cặp `số + đơn vị` **canh giữa theo cả cặp**, không canh giữa từng phần.
     *
     * Canh giữa riêng lẻ sẽ làm con số lệch khỏi tâm ô một nửa bề rộng đơn vị, và độ lệch đó KHÁC nhau giữa bốn
     * bánh khi số có số chữ khác nhau (`2.4` vs `2.45`) ⇒ bốn ô trông như bị đặt lệch nhau.
     */
    private fun drawPair(canvas: Canvas, m: Float, number: String, unit: String, baseline: Float) {
        val numW = bigP.measureText(number)
        val gapU = if (unit.isEmpty()) 0f else m * 0.018f
        val unitW = if (unit.isEmpty()) 0f else unitP.measureText(unit)
        var x = cell.centerX() - (numW + gapU + unitW) / 2f
        canvas.drawText(number, x, baseline, bigP)
        if (unit.isEmpty()) return
        x += numW + gapU
        canvas.drawText(unit, x, baseline, unitP)
    }

    private companion object {
        /** Trần cỡ chữ (sp) cho dòng kết luận — chặn ô lớn phình câu no-data thành chữ to nhất màn (SOÁT UI 2026-09-12). */
        const val VERDICT_CAP_SP = 16f
        /**
         * Thứ tự bánh dùng khi CHƯA có dữ liệu — đúng thứ tự khai của [TyreCorner].
         *
         * Suy từ `TyreCorner.values()` chứ không viết bốn tên: thêm/đổi bánh ở `:core` là bảng tự theo, và hai
         * nhánh (có dữ liệu / chưa) không thể lệch thứ tự.
         */
        val PLACEHOLDER_CORNERS: List<TyreCorner> = TyreCorner.values().toList()
    }
}
