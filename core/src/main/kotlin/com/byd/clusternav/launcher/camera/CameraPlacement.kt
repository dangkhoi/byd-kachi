package com.byd.clusternav.launcher.camera

import kotlin.math.roundToInt

/**
 * ═══ 2.93 · ĐẶT CỬA SỔ camera theo VỊ TRÍ KÉO-THẢ + CỠ % riêng từng camera — THUẦN, test bằng số ════════════════════
 *
 * Spec `docs/specs/kachi-293-cam.html` R2 · §4.3. Owner 06/10: *"cho chỉnh size và vị trí từng camera không nhỉ?"*.
 *
 * ## Đường 2.73–2.92 KHÔNG đi qua đây (CLAUDE.md §6)
 * Camera chưa kéo (vị trí vắng) **và** cỡ 100 % ⇒ [custom] = `false` ⇒ `CameraOverlayView` đi đúng đường cũ từng byte
 * (vùng vuông góc trên của màn chính · [CameraClusterBand.place] đầu dải trên cụm, kể cả mép cong *Theo cụm*). Chỉ khi
 * người lái ĐÃ kéo hoặc ĐÃ đổi cỡ thì cửa sổ mới đặt bằng phép ở đây.
 *
 * ## Vùng cho phép — hai display, hai vùng, cùng một toạ độ phần nghìn
 *  • **màn chính**: trọn bề ngang × từ [MAIN_TOP_RATIO] (mép dưới thanh trên — lời hứa *"không đè header"* của 2.35 R2)
 *    tới đáy màn ([mainRegion]);
 *  • **cụm**: đúng DẢI GIỮA đo theo hồ sơ xe ([CameraClusterBand.band]) — không đè thanh trên/dưới của cụm.
 * Vị trí lưu là TÂM cửa sổ theo phần nghìn của vùng ([CameraCamConfig.Place]) ⇒ đổi màn chính ⇄ cụm (`camera_on_cluster`)
 * thì camera vẫn ở *"cùng chỗ tương đối"*, không cần hai bộ toạ độ.
 *
 * ## Cỡ — % của VÙNG VUÔNG hôm nay, rồi khung đúng tỉ lệ ảnh nằm VỪA trong đó ([CameraOverlayFrame.fit])
 *  • màn chính: cạnh `[SQUARE_RATIO] × cao màn × cỡ` (100 % = ô 2.73);
 *  • cụm: nửa dải × trọn cao dải × cỡ (tròn: vuông cạnh = cao dải × cỡ) — 100 % = vùng của [CameraClusterBand.place].
 * Vùng luôn bị kẹp trong vùng cho phép ⇒ 150 % trên cụm (dải đã cao trọn) chỉ rộng thêm, không tràn khỏi dải.
 *
 * ⚠ Trên cụm, hình *Theo cụm* ở đường riêng vẽ như CHỮ NHẬT (không mặt nạ cong): mép cong chỉ có nghĩa khi cửa sổ
 * DÍNH mép kính ở đầu dải — đúng chỗ đường cũ đặt. Nút *Đặt lại vị trí* trả về đường cũ (spec §4.3).
 */
object CameraPlacement {

    /** Cạnh vùng vuông của màn chính = 50 % chiều cao màn (2.35 R2 · owner 25/09). Nguồn DUY NHẤT — overlay đọc lại. */
    const val SQUARE_RATIO = 0.50f

    /** Lề trên màn chính = 14 % chiều cao ⇒ nằm hẳn dưới thanh trên. */
    const val MAIN_TOP_RATIO = 0.14f

    /** Lề bên màn chính = 3 % bề rộng (góc mặc định). */
    const val SIDE_MARGIN_RATIO = 0.03f

    /** Cửa sổ đã đặt: toạ độ TUYỆT ĐỐI trên display (gốc trên-trái) + cờ cỡ nguồn đã biết. */
    data class Window(val x: Int, val y: Int, val w: Int, val h: Int, val streamKnown: Boolean) {
        val cx: Int get() = x + w / 2
        val cy: Int get() = y + h / 2
    }

    /** Ảnh đang hiện (để khung đúng tỉ lệ sau xoay) — cùng bốn số mà [CameraOverlayFrame.fit] nhận. */
    data class Stream(val w: Int, val h: Int, val crop: FloatArray?, val rotationDeg: Int)

    /**
     * Mô hình cho ô KÉO-THẢ trong Cài đặt — CÙNG phép đặt chỗ mà overlay dùng ([main]/[cluster]), để ô vẽ đúng chỗ
     * camera sẽ hiện (không có bản sao phép tính thứ hai ở tầng vẽ).
     *
     * @property region vùng cho phép ([mainRegion] hoặc dải cụm đã co theo display).
     * @property corner góc mặc định của camera (vị trí khi chưa kéo).
     */
    data class Model(
        val displayW: Int,
        val displayH: Int,
        val onCluster: Boolean,
        val region: CameraClusterBand.Rect,
        val round: Boolean,
        val corner: String,
        val stream: Stream,
    ) {
        /** Cửa sổ khi đặt ở [place] (`null` = góc mặc định) với cỡ [sizePct]. */
        fun window(place: CameraCamConfig.Place?, sizePct: Int): Window =
            if (onCluster) CameraPlacement.cluster(region, round, corner, place, sizePct, stream)
            else CameraPlacement.main(displayW, displayH, corner, place, sizePct, stream)

        /** Cửa sổ cỡ như [current] mà tâm ở ([cx],[cy]) tuyệt đối — đã kẹp trong [region] (cú kéo đang diễn ra). */
        fun dragTo(current: Window, cx: Int, cy: Int): Window =
            CameraPlacement.clamp(region, cx, cy, CameraOverlayFrame.Frame(current.w, current.h, current.streamKnown))

        /** Vị trí lưu bền của [win] (tâm, phần nghìn của [region]). */
        fun placeOf(win: Window): CameraCamConfig.Place = CameraPlacement.placeOf(region, win.cx, win.cy)
    }

    /** Có phải đi đường đặt chỗ riêng không — vị trí đã kéo HOẶC cỡ ≠ 100 %. */
    fun custom(place: CameraCamConfig.Place?, sizePct: Int): Boolean =
        place != null || sizePct != CameraCamConfig.SIZE_DEFAULT

    /** Vùng cho phép của MÀN CHÍNH — xem KDoc lớp. Display suy biến ⇒ vùng 1×1 (không ném). */
    fun mainRegion(displayW: Int, displayH: Int): CameraClusterBand.Rect {
        val w = displayW.coerceAtLeast(1)
        val h = displayH.coerceAtLeast(1)
        val top = (h * MAIN_TOP_RATIO).toInt().coerceIn(0, h - 1)
        return CameraClusterBand.Rect(0, top, w, h)
    }

    /**
     * Cửa sổ trên MÀN CHÍNH [displayW]×[displayH].
     *
     * @param corner góc mặc định (`"TL"`/`"TR"`) — chỉ dùng khi [place] `null`: vùng vuông dính góc ấy như 2.73 (lề
     *   [SIDE_MARGIN_RATIO] · [MAIN_TOP_RATIO]), chỉ cạnh đổi theo cỡ.
     */
    fun main(
        displayW: Int,
        displayH: Int,
        corner: String,
        place: CameraCamConfig.Place?,
        sizePct: Int,
        stream: Stream,
    ): Window {
        val region = mainRegion(displayW, displayH)
        val side = scaled(displayH * SQUARE_RATIO, sizePct).coerceIn(1, minOf(region.w, region.h))
        val f = CameraOverlayFrame.fit(stream.w, stream.h, stream.crop, stream.rotationDeg, side, side)
        val (cx, cy) = if (place != null) {
            centreOf(region, place)
        } else {
            val margin = (displayW * SIDE_MARGIN_RATIO).toInt()
            val left = corner == CameraSignalPolicy.CORNER_TOP_LEFT
            val boxX = if (left) margin else displayW - margin - side
            boxX + side / 2 to region.y0 + side / 2
        }
        return clamp(region, cx, cy, f)
    }

    /**
     * Cửa sổ trên CỤM, trong [band] (đã co theo display thật — [CameraClusterBand.band]).
     *
     * @param round hình TRÒN ⇒ vùng vuông (cửa sổ tròn không bao giờ là elip — cùng luật [CameraClusterBand.place]).
     * @param corner góc mặc định khi [place] `null`: `"TL"` ⇒ dính mép TRÁI dải, còn lại ⇒ mép PHẢI; giữa theo chiều cao.
     */
    @Suppress("LongParameterList")
    fun cluster(
        band: CameraClusterBand.Rect,
        round: Boolean,
        corner: String,
        place: CameraCamConfig.Place?,
        sizePct: Int,
        stream: Stream,
    ): Window {
        val baseW = if (round) band.h else band.w / 2
        val areaW = scaled(baseW.toFloat(), sizePct).coerceIn(1, band.w.coerceAtLeast(1))
        val areaH = scaled(band.h.toFloat(), sizePct).coerceIn(1, band.h.coerceAtLeast(1))
        val f0 = CameraOverlayFrame.fit(stream.w, stream.h, stream.crop, stream.rotationDeg, areaW, areaH)
        // Tròn: cửa sổ LUÔN vuông — `Outline.setOval` trên chữ nhật là elip.
        val f = if (round) minOf(f0.w, f0.h).let { CameraOverlayFrame.Frame(it, it, f0.streamKnown) } else f0
        val (cx, cy) = if (place != null) {
            centreOf(band, place)
        } else {
            val left = corner == CameraSignalPolicy.CORNER_TOP_LEFT
            (if (left) band.x0 + f.w / 2 else band.x1 - f.w + f.w / 2) to band.y0 + band.h / 2
        }
        return clamp(band, cx, cy, f)
    }

    /** Tâm TUYỆT ĐỐI của [place] (phần nghìn) trong [region]. */
    fun centreOf(region: CameraClusterBand.Rect, place: CameraCamConfig.Place): Pair<Int, Int> =
        region.x0 + (region.w.toLong() * place.x / CameraCamConfig.PLACE_SCALE).toInt() to
            region.y0 + (region.h.toLong() * place.y / CameraCamConfig.PLACE_SCALE).toInt()

    /** Ngược [centreOf]: tâm tuyệt đối ⇒ phần nghìn của [region], đã kẹp — đường lưu của bộ kéo-thả. */
    fun placeOf(region: CameraClusterBand.Rect, cx: Int, cy: Int): CameraCamConfig.Place {
        val rx = if (region.w > 0) ((cx - region.x0).toDouble() * CameraCamConfig.PLACE_SCALE / region.w).roundToInt() else 0
        val ry = if (region.h > 0) ((cy - region.y0).toDouble() * CameraCamConfig.PLACE_SCALE / region.h).roundToInt() else 0
        return CameraCamConfig.place(rx, ry)
    }

    /** Cửa sổ cỡ [f] tâm ([cx],[cy]) — dời cho NẰM TRỌN trong [region] (cửa sổ lớn hơn vùng ⇒ dính mép trên/trái). */
    fun clamp(region: CameraClusterBand.Rect, cx: Int, cy: Int, f: CameraOverlayFrame.Frame): Window {
        val w = f.w.coerceIn(1, region.w.coerceAtLeast(1))
        val h = f.h.coerceIn(1, region.h.coerceAtLeast(1))
        val x = (cx - w / 2).coerceIn(region.x0, (region.x1 - w).coerceAtLeast(region.x0))
        val y = (cy - h / 2).coerceIn(region.y0, (region.y1 - h).coerceAtLeast(region.y0))
        return Window(x, y, w, h, f.streamKnown)
    }

    /** `base × pct/100`, làm tròn, sàn 1 px — cỡ âm/0 không bao giờ ra khỏi đây. */
    private fun scaled(base: Float, pct: Int): Int = (base * pct / 100f).roundToInt().coerceAtLeast(1)
}
