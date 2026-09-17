package com.byd.clusternav.launcher

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import androidx.core.graphics.PathParser

/**
 * ═══ U9 · MỘT CHIẾC XE — khung xe dùng chung cho icon 24dp VÀ các bảng Canvas cỡ lớn ═════════════════════════
 *
 * Spec `docs/specs/kachi-car-boards.html` §4 (R1) · **VISUAL-REFRESH P3** `docs/specs/kachi-visual-refresh.html`
 * §4.3 (một nguồn, hai đích) · §4.6 (đường ống).
 *
 * ## Vì sao tệp này tồn tại
 * Trước U9 có **HAI chiếc xe** trong cùng một màn hình: bộ icon vẽ một hình, các bảng lớn ([TyreBoardView] ·
 * [DoorBoardView]) tự `drawRoundRect` một hình khác. Tệp này là **nơi duy nhất** `:app` đọc hình xe.
 *
 * ## P3 — chuỗi path nay SINH, không gõ tay
 * Mọi chuỗi path sống ở [CarFramesGenerated] (script `scripts/design/gen-car.py` sinh từ `design/car/{top,front,
 * side}.svg`; `--check` so byte). Tệp này **không giữ một literal path nào** nữa — nó chỉ *tra* [CarFramesGenerated.PIECES]
 * theo mặt + tên bộ phận, parse **một lần** (`by lazy`), và giữ hai bảng nối với `:core`: [CarPart] ↔ tên mảnh,
 * [TyreCorner] ↔ tên bánh. `CarFramesSourceContractTest` khoá: (a) SVG nguồn ↔ `android:pathData` ↔ chuỗi Kotlin
 * từng ký tự; (b) tệp này 0 literal; (c) mọi thành viên công khai có chỗ gọi (CLAUDE.md §8).
 *
 * Hệ toạ độ: **ô 24×24** của bộ icon — mọi số đọc theo hệ đó, và [fit] là chỗ duy nhất đổi sang hệ màn hình.
 *
 * ## Luật của tệp
 *  • [Path] trả về ở đây là **CHỈ ĐỌC theo quy ước**: chỗ gọi phải `Path.set(…)` sang một `Path` riêng rồi mới
 *    `transform(…)`. Biến hình thẳng vào path dùng chung là hỏng cho mọi bảng còn lại trong cùng tiến trình.
 *  • **Không màu** — chỉ hình học; màu do `CarPartStyle` (`:core`) quyết định và `CarArtPainter` tra sang [KachiTheme].
 */
internal object CarFrames {

    /** Bộ phận `:core` ↔ tên mảnh trong SVG mặt TRÊN (hậu tố thân xe `lf·rf·lr·rr`, KHÔNG phải TPMS `fl·fr·rl·rr`). */
    private val PART_IDS: Map<CarPart, String> = mapOf(
        CarPart.DOOR_LF to "door_lf",
        CarPart.DOOR_RF to "door_rf",
        CarPart.DOOR_LR to "door_lr",
        CarPart.DOOR_RR to "door_rr",
        CarPart.TAILGATE to "boot",
        CarPart.SUNROOF to "sunroof",
        CarPart.SUNSHADE to "sunshade",
        CarPart.MIRROR to "mirror",
    )

    /**
     * Bốn bánh, khoá theo [TyreCorner] — **không** suy từ tên: bộ đăng ký dùng nhiều quy ước hậu tố cho cùng một góc
     * (kiểm kê U7 §2), đổi chỗ giữa hai quy ước là bảng vẫn vẽ đủ bốn bánh, chỉ là bánh sau-trái mang số của bánh
     * trước-trái. Khoá bằng enum thì trình biên dịch giữ hộ.
     */
    private val WHEEL_IDS: Map<TyreCorner, String> = mapOf(
        TyreCorner.FRONT_LEFT to "wheel_fl",
        TyreCorner.FRONT_RIGHT to "wheel_fr",
        TyreCorner.REAR_LEFT to "wheel_rl",
        TyreCorner.REAR_RIGHT to "wheel_rr",
    )

    /** Tên mảnh của bộ phận `:core` trên mặt TRÊN (tầng vẽ dùng để tra path/hộp bao qua [CarArtSource]). */
    fun pieceIdOf(part: CarPart): String = PART_IDS.getValue(part)

    /** Tên mảnh bánh của một góc TPMS trên mặt TRÊN. */
    fun wheelIdOf(corner: TyreCorner): String = WHEEL_IDS.getValue(corner)

    // ── Path đã đọc — MỘT LẦN cho cả tiến trình ───────────────────────────────────────────────────────────

    /** Mảnh theo (mặt, tên) — [CarFramesGenerated.PIECES] đúng thứ tự vẽ; lớp highlight không phải mảnh riêng. */
    private val byKey: Map<String, CarFramesGenerated.Piece> by lazy {
        CarFramesGenerated.PIECES.associateBy { key(it.face, it.id) }
    }

    private val paths: Map<String, Path> by lazy {
        byKey.mapValues { (_, p) ->
            parse(p.path).also { if (p.evenOdd) it.fillType = Path.FillType.EVEN_ODD }
        }
    }

    /**
     * Hộp bao đo bằng CHÍNH `Path` đã parse (hộp điểm điều khiển của Android, xem [boundsOf]) — không dùng
     * `Piece.bounds` của script, để chỗ đặt nhãn `%`/áp suất bám đúng hình mà Canvas sẽ vẽ.
     */
    private val boxes: Map<String, RectF> by lazy { paths.mapValues { boundsOf(it.value) } }

    private fun key(face: String, id: String) = "$face/$id"

    /** Mảnh của một mặt theo đúng thứ tự vẽ trong SVG (lớp dưới trước). */
    fun pieces(face: CarFace): List<CarFramesGenerated.Piece> =
        CarFramesGenerated.PIECES.filter { it.face == face.name.lowercase() }

    /** Path dùng chung của một mảnh — `null` khi mặt/tên không có (chỗ gọi bỏ qua, không sập launcher trên xe). */
    fun path(face: CarFace, id: String): Path? = paths[key(face.name.lowercase(), id)]

    /** Hộp bao của một mảnh trong hệ icon; `false` = không có mảnh. */
    fun bounds(face: CarFace, id: String, out: RectF): Boolean {
        val b = boxes[key(face.name.lowercase(), id)] ?: return false
        out.set(b)
        return true
    }

    /** Hộp bao THÂN XE của một mặt (không kể bánh/vạt cửa) — thứ được phóng vào ô để bốn bánh **thò ra ngoài**. */
    fun frameBounds(face: CarFace, out: RectF) {
        bounds(face, BODY, out)
    }

    /**
     * Hộp bao THÂN + MỌI bộ phận mở được (hệ icon) — khung mà bảng cửa phóng vào ô.
     *
     * Vạt cửa mở **ra ngoài** thân tới `x 3.9 … 20.1`, phóng theo thân sẽ cắt cụt đúng bốn thứ bảng đó sinh ra để
     * hiện. Đo bằng phép hợp các hộp bao thật — đổi hình vạt cửa ở SVG là khung tự theo.
     */
    fun openFrameBounds(face: CarFace, out: RectF) {
        frameBounds(face, out)
        val tmp = RectF()
        pieces(face).forEach { p ->
            if (p.layer == "doors" || p.layer == "tyres") { bounds(face, p.id, tmp); out.union(tmp) }
        }
    }

    /**
     * Ma trận phóng [src] (hệ icon) vào [dst] (hệ màn hình) — **giữ nguyên tỉ lệ**, canh giữa.
     *
     * `ScaleToFit.CENTER` chứ không `FILL`: kéo giãn một chiều là làm ra chiếc xe THỨ HAI, đúng thứ tệp này sinh
     * ra để bỏ.
     */
    fun fit(src: RectF, dst: RectF, out: Matrix) {
        out.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER)
    }

    private fun parse(data: String): Path = PathParser.createPathFromPathData(data)

    /**
     * `computeBounds(RectF)` một-tham-số chỉ có từ API 34, `minSdk` là 29 ⇒ giữ nhánh cũ (bản hai-tham-số đã bị
     * đánh dấu bỏ vì tham số `exact` không còn tác dụng — hai nhánh trả cùng giá trị).
     */
    private fun boundsOf(path: Path): RectF = RectF().also { r ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            path.computeBounds(r)
        } else {
            @Suppress("DEPRECATION")
            path.computeBounds(r, true)
        }
    }

    /** Tên mảnh THÂN trên mọi mặt (`data-role="paint"` trong SVG) — cũng là đích của lớp highlight. */
    const val BODY = "body"
}
