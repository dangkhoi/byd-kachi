package com.byd.clusternav.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

/**
 * ═══ VISUAL-REFRESH P3 · §4.8 mức tả thực (1) — VẼ HÌNH XE "có khối, có ánh sáng, có bóng" lên Canvas ═══════════
 *
 * Bốn chỗ tạo chiều sâu, **tất cả là gradient + tô phẳng** — 0 blur · 0 shadow layer · 0 elevation (AC5.2,
 * `SurfaceMaterialContractTest` quét cả tầng `launcher/`): thân = chuyển sắc DỌC màu sơn ([KachiCarPaint]) · kính =
 * chuyển sắc chéo lam nhạt→sẫm · đèn = quầng toả tròn về trong suốt · bóng đổ = ellipse toả. Mọi `Shader`/`Paint`
 * dựng **một lần ở [layout]** (khi ô đổi cỡ), KHÔNG dựng trong `onDraw` (AC5.3 — nhịp trạng thái xe 1 Hz).
 *
 * Tầng này **không phán xét**: cách tô từng mảnh đến từ [CarPartStyle] (`:core`) qua [PartLook]; ở đây chỉ dịch
 * TÊN token ([CarInk]) sang màu/shader của [KachiTheme] ([inkColor]). Nguồn hình đi qua [CarArtSource] (AC1.6).
 */
internal class CarArtPainter(private val src: CarArtSource = VectorCarArt) {

    private val matrix = Matrix()
    private val path = Path()
    private val box = RectF()
    private val srcBox = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    /** Shader theo tên mảnh — dựng ở [layout] vì gradient phụ thuộc hộp bao ĐÃ PHÓNG. */
    private val shaders = HashMap<String, Shader>()
    private var face = CarFace.TOP
    private var paintLook: CarPaintLook = KachiCarPaint.look()

    // Màu phân giải MỘT LẦN (theo bảng màu lúc dựng View; đổi chủ đề thì màn dựng lại View, painter dựng lại).
    private val colors: Map<CarInk, Int> = mapOf(
        CarInk.NONE to Color.TRANSPARENT,
        CarInk.PART_LINE to c(KachiTheme.PART_LINE), CarInk.PART_FILL to c(KachiTheme.PART_FILL),
        CarInk.DIM to c(KachiTheme.DIM), CarInk.MUT2 to c(KachiTheme.MUT2),
        CarInk.ACCENT to c(KachiTheme.ACCENT), CarInk.ACCENT_SOFT to c(KachiTheme.ACCENT_SOFT),
        CarInk.ACCENT_WASH to c(KachiTheme.ACCENT_WASH), CarInk.ACCENT_LINE to c(KachiTheme.ACCENT_LINE),
        CarInk.AMBER to c(KachiTheme.AMBER), CarInk.AMBER_SOFT to c(KachiTheme.AMBER_SOFT),
        CarInk.RED to c(KachiTheme.RED), CarInk.RED_SOFT to ColorMath.withAlpha(c(KachiTheme.RED), RED_SOFT_ALPHA),
        CarInk.LAMP_ON to c(KachiTheme.LAMP_ON), CarInk.TAIL_ON to c(KachiTheme.TAIL_ON),
        CarInk.DECOR to c(KachiTheme.PART_FILL),
        // Ba vai dưới là SHADER (dựng ở layout); màu phẳng chỉ là đường lùi khi mảnh không có hộp bao.
        CarInk.PAINT to c(KachiTheme.PART_FILL), CarInk.GLASS to c(KachiTheme.GLASS_TO),
        CarInk.LAMP_GLOW to c(KachiTheme.LAMP_GLOW), CarInk.SHADOW to c(KachiTheme.CAR_SHADOW),
    )

    /**
     * Đặt mặt + khung đích. [openFrame] = phóng theo THÂN + bánh + vạt cửa (bảng cửa) thay vì chỉ thân (bảng lốp —
     * bánh cố ý thò ra ngoài khung). [strokePx] = nét thân đã có sàn ([KachiSpace.STROKE]) do chỗ gọi tính.
     */
    fun layout(face: CarFace, dst: RectF, openFrame: Boolean, strokePx: Float) {
        this.face = face
        paintLook = KachiCarPaint.look()
        if (openFrame) src.openFrameBounds(face, srcBox) else src.frameBounds(face, srcBox)
        CarFrames.fit(srcBox, dst, matrix)
        stroke.strokeWidth = strokePx
        shaders.clear()
        src.parts(face).forEach { p ->
            if (!src.bounds(face, p.id, box)) return@forEach
            matrix.mapRect(box)
            when (p.role) {
                CarPartRole.PAINT -> shaders[p.id] = LinearGradient(
                    box.left, box.top, box.left, box.bottom, paintLook.from, paintLook.to, Shader.TileMode.CLAMP,
                )
                CarPartRole.GLASS -> shaders[p.id] = LinearGradient(
                    box.left, box.top, box.left + box.width() * GLASS_SLANT, box.bottom,
                    c(KachiTheme.GLASS_FROM), c(KachiTheme.GLASS_TO), Shader.TileMode.CLAMP,
                )
                CarPartRole.GLOW -> shaders[p.id] = radial(box, c(KachiTheme.LAMP_GLOW))
                CarPartRole.GLOWTAIL -> shaders[p.id] = radial(box, ColorMath.withAlpha(c(KachiTheme.TAIL_ON), GLOW_ALPHA))
                CarPartRole.SHADOW -> shaders[p.id] = radial(box, c(KachiTheme.CAR_SHADOW))
                else -> Unit
            }
        }
    }

    /**
     * Vẽ cả mặt, mảnh nào cũng qua [CarPartStyle]: [toneOf] trả sắc thái của mảnh (`null` = NEUTRAL), [availableOf]
     * trả đã đọc được chưa (mặc định `true` — mảnh không mang dữ liệu thì luôn "có"). Thứ tự = thứ tự lớp SVG.
     */
    fun draw(
        canvas: Canvas,
        toneOf: (CarArtPart) -> GroupTone? = { null },
        availableOf: (CarArtPart) -> Boolean = { true },
        include: (CarArtPart) -> Boolean = { true },
    ) {
        val parts = src.parts(face)
        for (i in parts.indices) {
            val p = parts[i]
            // [include] = bảng chỉ vẽ bộ phận THUỘC câu hỏi của nó (bảng lốp không vẽ vạt cửa; widget xe không vẽ gương)
            // — cùng lẽ KDoc `CarFrames` cũ: vẽ một bộ phận không có dữ liệu là bảng tự bịa thêm nội dung.
            if (!include(p)) continue
            drawPart(canvas, p, CarPartStyle.look(p.role, toneOf(p) ?: GroupTone.NEUTRAL, availableOf(p), paintLook.paint))
        }
        // Lớp 6 — highlight: viền thân theo tone NẶNG NHẤT trong các mảnh có tone (ALERT thắng, luật (c) §4.4).
        // Vòng lặp chứ không `mapNotNull{}.maxByOrNull{}`: hai phép đó cấp phát một danh sách MỖI KHUNG vẽ, đúng thứ
        // KDoc lớp hứa là không có (`GroupTone.NEUTRAL` là bậc 0 nên khởi tạo từ nó cho cùng kết quả với `max`).
        var worst = GroupTone.NEUTRAL
        for (i in parts.indices) {
            val t = toneOf(parts[i]) ?: continue
            if (t.ordinal > worst.ordinal) worst = t
        }
        drawById(canvas, src.highlightRef(face), CarPartStyle.look(CarPartRole.HIGHLIGHT, worst, true, paintLook.paint), evenOdd = false)
    }

    /** Vẽ MỘT mảnh theo cách tô đã quyết ở `:core` (bảng lốp tô từng bánh, bảng cửa tô từng vạt). */
    fun drawPart(canvas: Canvas, p: CarArtPart, look: PartLook) = drawById(canvas, p.id, look, p.evenOdd, p.strokeOnly)

    private fun drawById(canvas: Canvas, id: String, look: PartLook, evenOdd: Boolean, strokeOnly: Boolean = false) {
        if (!look.visible && !look.outline) return
        val shape = src.path(face, id) ?: return
        path.set(shape)
        path.fillType = if (evenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
        path.transform(matrix)
        if (look.fill != CarInk.NONE && !strokeOnly) {
            // Vai CHẤT LIỆU (sơn · kính · quầng · bóng) lấy shader đã dựng cho ĐÚNG mảnh này; vai phẳng thì shader = null.
            // ⚠ [ĐO máy ảo 2026-09-17] `Paint.color` có kênh alpha NHÂN vào shader: đặt màu lùi 20 % rồi gắn shader thì
            // thân sơn ngọc trai ra XÁM MỜ trên cả hai bảng. Có shader ⇒ alpha 255, shader tự mang độ đục của nó.
            val shader = if (look.fill in SHADED) shaders[id] else null
            if (shader != null) { fill.shader = shader; fill.alpha = OPAQUE } else { fill.shader = null; fill.color = colors.getValue(look.fill) }
            canvas.drawPath(path, fill)
            fill.shader = null
        }
        // Nét: vai nét của bảng, hoặc VIỀN BẮT BUỘC (`partLine`) khi màu sơn chạm nền / sơn đỏ + ALERT (§4.8 luật (a)).
        val strokeInk = when {
            look.stroke != CarInk.NONE -> look.stroke
            look.outline || (id == src.highlightRef(face) && paintLook.outline && look.fill == CarInk.PAINT) -> CarInk.PART_LINE
            strokeOnly -> CarInk.PART_LINE
            else -> CarInk.NONE
        }
        if (strokeInk != CarInk.NONE) {
            stroke.color = colors.getValue(strokeInk)
            canvas.drawPath(path, stroke)
        }
    }

    /** Hộp bao một mảnh **đã phóng sang hệ màn hình** — chỗ đặt số áp suất, nhãn `%`. */
    fun mapBounds(id: String, out: RectF): Boolean {
        if (!src.bounds(face, id, out)) return false
        matrix.mapRect(out)
        return true
    }

    /** Biến hình một `Path` RIÊNG của chỗ gọi (đã `set` từ nguồn) sang hệ màn hình — ma trận phóng chỉ dựng ở đây. */
    fun transform(own: Path) = own.transform(matrix)

    /** Hệ số phóng của ma trận (giữ tỉ lệ nên một số là đủ) — bảng lốp tính chiều cao ô giá trị từ đây. */
    fun mapRadius(r: Float): Float = matrix.mapRadius(r)

    private fun radial(b: RectF, center: Int): Shader = RadialGradient(
        b.centerX(), b.centerY(), max(b.width(), b.height()) / 2f, center, Color.TRANSPARENT, Shader.TileMode.CLAMP,
    )

    private fun c(hex: String): Int = Color.parseColor(hex)

    private companion object {
        /** Kính: chuyển sắc nghiêng nhẹ (điểm cuối lệch 35 % bề rộng) — cùng công thức script `gen-car.py` dùng cho mặt 48dp. */
        const val GLASS_SLANT = 0.35f
        const val GLOW_ALPHA = 230
        const val OPAQUE = 255
        /** `RED` α .35–.40 (§4.4) — một số cho cả kính/vạt cửa, khác biệt nhỏ hơn một bậc thị giác. */
        const val RED_SOFT_ALPHA = 0x66

        /** Token có SHADER riêng theo mảnh (dựng ở [layout]); `TAIL_ON` ở đây là quầng đèn hậu (`glowtail`). */
        val SHADED = setOf(CarInk.PAINT, CarInk.GLASS, CarInk.LAMP_GLOW, CarInk.SHADOW, CarInk.TAIL_ON)
    }
}

/** Màu sơn đã quyết cho lượt vẽ: hai đầu gradient + có phải bật viền thân không. */
internal class CarPaintLook(val paint: CarPaint, val from: Int, val to: Int, val outline: Boolean)

/**
 * R8 AC8.3/AC8.5 — màu sơn theo hồ sơ ([KachiTheme.carPaint]) + luật viền §4.8 (a): đo **cả hai đầu** gradient so với
 * nền hiện tại; đầu tệ nhất dưới [CarPaint.OUTLINE_FLOOR] ⇒ viền `partLine` bật tự động, **không cấm chọn**.
 * `CarPaintContrastContractTest` sinh bảng đo cho 5 màu × 2 bảng và khoá kết quả với `design/car/paint.json`.
 */
internal object KachiCarPaint {
    fun look(paint: CarPaint = KachiTheme.carPaint, bgHex: String = KachiTheme.BG): CarPaintLook {
        val (fromHex, toHex) = KachiPaletteSeeds.CAR_PAINTS.getValue(paint)
        val from = ColorMath.parse(fromHex)
        val to = ColorMath.parse(toHex)
        val bg = ColorMath.parse(bgHex)
        val worst = min(ColorMath.ratio(from, bg), ColorMath.ratio(to, bg))
        return CarPaintLook(paint, from, to, CarPaint.outlineNeeded(worst))
    }

    /** Màu ô chọn ở Cài đặt = điểm giữa gradient (một màu đại diện, không phải một bảng màu thứ hai). */
    fun swatch(paint: CarPaint): Int {
        val (fromHex, toHex) = KachiPaletteSeeds.CAR_PAINTS.getValue(paint)
        return ColorMath.mix(ColorMath.parse(fromHex), ColorMath.parse(toHex), 0.5)
    }
}
