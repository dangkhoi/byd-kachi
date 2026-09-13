package com.byd.clusternav.launcher

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import androidx.core.graphics.PathParser

/**
 * ═══ U9 · MỘT CHIẾC XE — khung xe dùng chung cho icon 24dp VÀ ba bảng Canvas cỡ lớn ═══════════════════════════
 *
 * Spec `docs/specs/kachi-car-boards.html` §4 (R1). Nguồn hình: script sinh icon của U7 (hằng `TOP_BODY` ·
 * `TOP_GLASS` · `WHEEL_FILL` trong `gen_car4.py`, xem `docs/diagnostics/icon-set-v2-inventory-2026-09-13.md` §1).
 *
 * ## Vì sao tệp này tồn tại
 * Trước U9 có **HAI chiếc xe** trong cùng một màn hình: bộ icon v2 vẽ thân thuôn mũi + hai vạch kính (path ở
 * `res/drawable/ic_car_top_*.xml`), còn ba bảng lớn ([TyreBoardView] · [RadarBoardView] · [SideBoardView]) mỗi
 * cái tự vẽ một `drawRoundRect` + một `drawLine` bằng tỉ lệ riêng. Kiểm kê U7 §1 đã ghi thẳng nợ đó:
 * *"ba path khung này là cùng hình học mà ba View đang vẽ tay… nên rút ra một hằng dùng chung để bảng lớn và
 * icon nhỏ là MỘT chiếc xe, không phải hai chiếc khác nhau"*.
 *
 * Hệ toạ độ: **ô 24×24** của bộ icon (ô quang học 20×20, nét 1.6) — mọi số trong các chuỗi path dưới đây đọc
 * theo hệ đó, và [fit] là chỗ duy nhất đổi sang hệ màn hình.
 *
 * ## Luật của tệp
 *  • **Chuỗi path chép NGUYÊN VĂN** từ script sinh icon — không làm tròn, không đổi thứ tự lệnh, không "dọn cho
 *    gọn". `CarFramesSourceContractTest` so từng chuỗi ở đây với `android:pathData` trong `res/drawable/` và đỏ
 *    nếu lệch **một ký tự**: đó là thứ duy nhất bảo đảm hai bề mặt thật sự là một chiếc xe.
 *  • **Parse MỘT LẦN** (`by lazy`) — `onDraw` của ba bảng chạy theo nhịp trạng thái xe, đọc chuỗi path mỗi khung
 *    là đúng loại rác bộ nhớ mà KDoc ba bảng hứa là không có.
 *  • [Path] trả về ở đây là **CHỈ ĐỌC theo quy ước**: chỗ gọi phải `Path.set(…)` sang một `Path` riêng rồi mới
 *    `transform(…)`. Biến hình thẳng vào path dùng chung là hỏng cho mọi bảng còn lại trong cùng tiến trình.
 *  • **Không màu** — tệp này chỉ có hình học; màu do bảng quyết định theo [KachiTheme] (luật "0 hex" của
 *    `ThemePaletteContractTest` phủ luôn tệp này vì bài đó quét cả thư mục `launcher/`).
 *
 * ## Bộ phận nào có ở đây, bộ phận nào chưa
 * Chỉ có **khung TRÊN + 4 bánh** — đúng những gì ba bảng đang vẽ. Khung TRƯỚC/SAU và các vùng tô cửa · kính ·
 * cốp · ca-pô **cố ý chưa đưa vào**: một hằng không ai gọi thì không bài canh nào phát hiện được khi nó trôi
 * khỏi icon (CLAUDE.md §8 — "compile xanh không có nghĩa là code chạy"). Bảng nào cần tới bộ phận nào thì chép
 * chuỗi tương ứng từ script sinh icon vào đây **cùng lượt** với chỗ dùng nó.
 */
internal object CarFrames {

    // ── Chuỗi path: chép NGUYÊN VĂN từ `gen_car4.py` (hằng TOP_BODY · TOP_GLASS · WHEEL_FILL) ─────────────
    // Dòng dài có chủ ý: cắt dòng là đổi chuỗi, mà chuỗi phải khớp từng ký tự với `res/drawable/ic_car_top_*.xml`.

    /** Thân xe nhìn từ trên — thuôn mũi, rộng 9.6 (ink 11.2), `x 7.5..16.5 · y 4.6..20.3`. */
    private const val TOP_BODY = "M12,4.6 C9.9,4.6 8.35,5.4 7.95,7.2 L7.5,10.2 C7.2,12.6 7.2,15.6 7.55,18.1 C7.8,19.7 9.0,20.3 12,20.3 C15.0,20.3 16.2,19.7 16.45,18.1 C16.8,15.6 16.8,12.6 16.5,10.2 L16.05,7.2 C15.65,5.4 14.1,4.6 12,4.6 Z"

    /** Hai vạch kính: **kính lái** (cung trên) rồi **kính hậu** (cung dưới) — một chuỗi, hai nhánh `M`. */
    private const val TOP_GLASS = "M8.7,8.7 C9.9,7.9 14.1,7.9 15.3,8.7 M8.7,16.6 C9.9,17.4 14.1,17.4 15.3,16.6"

    private const val WHEEL_FL = "M4.30,6.80 L4.50,6.80 A1.20,1.20 0 0 1 5.70,8.00 L5.70,10.20 A1.20,1.20 0 0 1 4.50,11.40 L4.30,11.40 A1.20,1.20 0 0 1 3.10,10.20 L3.10,8.00 A1.20,1.20 0 0 1 4.30,6.80 Z"
    private const val WHEEL_FR = "M19.50,6.80 L19.70,6.80 A1.20,1.20 0 0 1 20.90,8.00 L20.90,10.20 A1.20,1.20 0 0 1 19.70,11.40 L19.50,11.40 A1.20,1.20 0 0 1 18.30,10.20 L18.30,8.00 A1.20,1.20 0 0 1 19.50,6.80 Z"
    private const val WHEEL_RL = "M4.30,13.60 L4.50,13.60 A1.20,1.20 0 0 1 5.70,14.80 L5.70,17.00 A1.20,1.20 0 0 1 4.50,18.20 L4.30,18.20 A1.20,1.20 0 0 1 3.10,17.00 L3.10,14.80 A1.20,1.20 0 0 1 4.30,13.60 Z"
    private const val WHEEL_RR = "M19.50,13.60 L19.70,13.60 A1.20,1.20 0 0 1 20.90,14.80 L20.90,17.00 A1.20,1.20 0 0 1 19.70,18.20 L19.50,18.20 A1.20,1.20 0 0 1 18.30,17.00 L18.30,14.80 A1.20,1.20 0 0 1 19.50,13.60 Z"

    /**
     * Bốn bánh, khoá theo [TyreCorner] của `:core` — **không** theo bốn chữ `fl/fr/rl/rr` của tên tệp icon.
     *
     * Bộ đăng ký dùng tới **bốn** quy ước hậu tố cho cùng một góc xe (kiểm kê U7 §2: TPMS `fl·fr·rl·rr`, thân xe
     * `lf·rf·lr·rr` **đảo chữ**, ADAS `left·right`…). Đổi chỗ giữa hai quy ước ấy là lỗi im lặng: bảng vẫn vẽ đủ
     * bốn bánh, chỉ là bánh sau-trái mang số của bánh trước-trái. Khoá bằng enum thì trình biên dịch giữ hộ.
     */
    private val WHEELS: Map<TyreCorner, String> = mapOf(
        TyreCorner.FRONT_LEFT to WHEEL_FL,
        TyreCorner.FRONT_RIGHT to WHEEL_FR,
        TyreCorner.REAR_LEFT to WHEEL_RL,
        TyreCorner.REAR_RIGHT to WHEEL_RR,
    )

    // ── Path đã đọc — MỘT LẦN cho cả tiến trình ───────────────────────────────────────────────────────────

    /**
     * Khung TRÊN vẽ bằng NÉT = thân + hai vạch kính, gộp thành **một** [Path].
     *
     * Gộp được vì cả ba nhánh dùng chung một cây cọ (nét, đầu/khớp tròn) ⇒ một `drawPath` thay vì ba, và chỗ gọi
     * chỉ phải chép/biến hình một lần mỗi khung vẽ.
     */
    val topFrame: Path by lazy {
        Path().also { p ->
            p.addPath(bodyPath)
            p.addPath(parse(TOP_GLASS))
        }
    }

    private val bodyPath: Path by lazy { parse(TOP_BODY) }

    private val wheelPaths: Map<TyreCorner, Path> by lazy { WHEELS.mapValues { parse(it.value) } }

    private val frameBox: RectF by lazy { boundsOf(bodyPath) }

    private val wheelBoxes: Map<TyreCorner, RectF> by lazy { wheelPaths.mapValues { boundsOf(it.value) } }

    /**
     * Khoảng cách TÂM hàng bánh trước ↔ hàng bánh sau, trong hệ toạ độ icon.
     *
     * Bảng lốp cần số này để biết ô giá trị cao bao nhiêu thì **vừa lọt giữa hai bánh** — đo từ chính path chứ
     * không viết tay `6.8`, để đổi hình bánh ở script sinh icon là bảng tự xếp lại.
     */
    val wheelRowGap: Float by lazy {
        wheelBoxes.getValue(TyreCorner.REAR_LEFT).centerY() - wheelBoxes.getValue(TyreCorner.FRONT_LEFT).centerY()
    }

    /** Vùng tô của một bánh — [Path] dùng chung, xem luật "chỉ đọc" ở KDoc lớp. */
    fun wheel(corner: TyreCorner): Path = wheelPaths.getValue(corner)

    /** Hộp bao của một bánh **trong hệ toạ độ icon**; chỗ gọi tự `Matrix.mapRect` sang hệ màn hình. */
    fun wheelBounds(corner: TyreCorner, out: RectF) {
        out.set(wheelBoxes.getValue(corner))
    }

    /**
     * Hộp bao của THÂN XE (không kể bánh) trong hệ toạ độ icon — đây là thứ được phóng vào ô, để bốn bánh **thò
     * ra ngoài** thân đúng như trên icon.
     *
     * Lấy thân chứ không lấy cả xe kèm bánh là một quyết định có đo: xe-kèm-bánh rộng 17.8 trên cao 15.7 (gần
     * vuông), phóng vừa một ô gần vuông thì hai hàng bánh chỉ cách nhau ~120px ⇒ hai ô giá trị chồng lên nhau.
     * Lấy thân (9.6 × 15.7, dọc) thì ô vẫn dành phần lớn bề ngang cho bốn ô giá trị, y như bố cục đã duyệt.
     */
    fun frameBounds(out: RectF) {
        out.set(frameBox)
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
     * Hộp bao của một path.
     *
     * `computeBounds(RectF)` một-tham-số chỉ có từ API 34, `minSdk` của dự án là 29 ⇒ vẫn phải giữ nhánh cũ
     * (bản hai-tham-số đã bị đánh dấu bỏ vì tham số `exact` không còn tác dụng — giá trị trả về hai nhánh giống
     * hệt nhau).
     */
    private fun boundsOf(path: Path): RectF = RectF().also { r ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            path.computeBounds(r)
        } else {
            @Suppress("DEPRECATION")
            path.computeBounds(r, true)
        }
    }
}
