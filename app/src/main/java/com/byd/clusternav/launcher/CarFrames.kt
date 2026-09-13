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
 * Khung TRÊN + 4 bánh (U9 pha 1) + **8 bộ phận mở được** của thân xe (U9 pha 2, cho [DoorBoardView]) — đúng những
 * gì bốn bảng đang vẽ, không hơn. Khung TRƯỚC/SAU, ô kính và ca-pô **cố ý chưa đưa vào**: một hằng không ai gọi thì
 * không bài canh nào phát hiện được khi nó trôi khỏi icon (CLAUDE.md §8 — "compile xanh không có nghĩa là code
 * chạy"), và `CarFramesSourceContractTest` khoá đúng điều đó bằng cách đòi **mọi** hằng ở đây phải có chỗ gọi.
 *
 * ⚠ Ca-pô có icon (`ic_car_top_hood.xml`) nhưng KHÔNG vào đây: nhóm *Cửa & khoang* không có datum nào đọc nắp
 * ca-pô (`CarStatus.Body` không có field, `hood` chỉ là một NÚT ở [ControlRegistry] và cũng không thuộc nhóm) ⇒
 * vẽ nó ra là vẽ một bộ phận **vĩnh viễn chưa đọc được**, tức bảng tự bịa thêm nội dung không có trong model.
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

    // ── U9 pha 2 · BỘ PHẬN MỞ ĐƯỢC (bảng *Cửa & khoang*) — cũng chép NGUYÊN VĂN từ `gen_car4.py` ────────────
    // Vạt cửa: hằng `FLAP` (4 mục). Cốp/nóc/rèm/gương: các ô `ic_car_top_trunk` · `ic_car_top_sunroof` ·
    // `ic_car_top_sunshade` · `ic_car_top_mirror`. Hậu tố cửa theo quy ước THÂN XE `lf·rf·lr·rr` — KHÔNG phải
    // `fl·fr·rl·rr` của TPMS ở trên (kiểm kê U7 §2); enum [CarPart] của `:core` giữ hộ chỗ nối đó.

    /** Vạt CỬA TRƯỚC-TRÁI mở ra ngoài thân, đúng góc bản lề. */
    private const val FLAP_LF = "M7.55,9.1 L3.9,10.4 L3.9,13.1 L7.35,12.5 Z"
    private const val FLAP_RF = "M16.45,9.1 L20.1,10.4 L20.1,13.1 L16.65,12.5 Z"
    private const val FLAP_LR = "M7.35,13.9 L3.9,15.2 L3.9,17.9 L7.6,17.3 Z"
    private const val FLAP_RR = "M16.65,13.9 L20.1,15.2 L20.1,17.9 L16.4,17.3 Z"

    /** Nắp cốp — mảng đuôi xe, nằm TRONG thân. */
    private const val TRUNK = "M9.80,17.30 L14.20,17.30 A0.90,0.90 0 0 1 15.10,18.20 L15.10,18.60 A0.90,0.90 0 0 1 14.20,19.50 L9.80,19.50 A0.90,0.90 0 0 1 8.90,18.60 L8.90,18.20 A0.90,0.90 0 0 1 9.80,17.30 Z"

    /** Ô cửa sổ trời trên nóc. */
    private const val SUNROOF = "M10.60,9.30 L13.40,9.30 A1.00,1.00 0 0 1 14.40,10.30 L14.40,14.30 A1.00,1.00 0 0 1 13.40,15.30 L10.60,15.30 A1.00,1.00 0 0 1 9.60,14.30 L9.60,10.30 A1.00,1.00 0 0 1 10.60,9.30 Z"

    /** Rèm che nắng, mảnh 1: THANH CUỘN ở mép trước nóc. */
    private const val SHADE_ROLL = "M9.85,9.90 L14.15,9.90 A0.55,0.55 0 0 1 14.70,10.45 L14.70,10.55 A0.55,0.55 0 0 1 14.15,11.10 L9.85,11.10 A0.55,0.55 0 0 1 9.30,10.55 L9.30,10.45 A0.55,0.55 0 0 1 9.85,9.90 Z"

    /** Rèm che nắng, mảnh 2: TẤM PHỦ + hai NẾP GẤP khoét rỗng (⇒ phải tô kiểu `EVEN_ODD`, xem [partPaths]). */
    private const val SHADE_SHEET = "M10.20,11.60 L13.80,11.60 A0.50,0.50 0 0 1 14.30,12.10 L14.30,15.50 A0.50,0.50 0 0 1 13.80,16.00 L10.20,16.00 A0.50,0.50 0 0 1 9.70,15.50 L9.70,12.10 A0.50,0.50 0 0 1 10.20,11.60 Z M10.65,12.80 L13.35,12.80 A0.25,0.25 0 0 1 13.60,13.05 L13.60,13.15 A0.25,0.25 0 0 1 13.35,13.40 L10.65,13.40 A0.25,0.25 0 0 1 10.40,13.15 L10.40,13.05 A0.25,0.25 0 0 1 10.65,12.80 Z M10.65,14.20 L13.35,14.20 A0.25,0.25 0 0 1 13.60,14.45 L13.60,14.55 A0.25,0.25 0 0 1 13.35,14.80 L10.65,14.80 A0.25,0.25 0 0 1 10.40,14.55 L10.40,14.45 A0.25,0.25 0 0 1 10.65,14.20 Z"

    /** Hai tai gương chiếu hậu — một chuỗi, hai nhánh `M` (trái rồi phải). */
    private const val MIRROR = "M7.6,8.9 L4.7,9.7 L7.4,10.9 Z M16.4,8.9 L19.3,9.7 L16.6,10.9 Z"

    /**
     * Bộ phận ↔ các mảnh path của nó, khoá theo [CarPart] của `:core`.
     *
     * Một bộ phận có thể gồm **nhiều mảnh** (rèm = thanh cuộn + tấm phủ) — đúng như trong tệp icon, nơi chúng là hai
     * thẻ `<path>` riêng. Gộp chúng thành MỘT [Path] ở đây thay vì để bảng vẽ hai lần: một bộ phận là một trạng thái,
     * nên nó phải là một lượt tô với một màu, không thể có nửa mảnh mang màu khác.
     */
    private val PARTS: Map<CarPart, List<String>> = mapOf(
        CarPart.DOOR_LF to listOf(FLAP_LF),
        CarPart.DOOR_RF to listOf(FLAP_RF),
        CarPart.DOOR_LR to listOf(FLAP_LR),
        CarPart.DOOR_RR to listOf(FLAP_RR),
        CarPart.TAILGATE to listOf(TRUNK),
        CarPart.SUNROOF to listOf(SUNROOF),
        CarPart.SUNSHADE to listOf(SHADE_ROLL, SHADE_SHEET),
        CarPart.MIRROR to listOf(MIRROR),
    )

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

    /**
     * Path của từng bộ phận — đọc MỘT LẦN, và tô kiểu **EVEN_ODD**.
     *
     * `EVEN_ODD` chứ không để mặc định `WINDING`: tấm rèm ([SHADE_SHEET]) khai hai nếp gấp là hai vòng **nằm trong**
     * vòng ngoài, và tệp icon tương ứng khai `android:fillType="evenOdd"` — với `WINDING` hai nếp đó bị tô đặc, tức
     * rèm mất đúng chi tiết đã cứu nó khỏi bị đọc nhầm thành mặt kính ở Pass 5 của U7. Các bộ phận còn lại không có
     * vòng lồng nhau nên hai kiểu tô cho kết quả **y hệt** ⇒ đặt chung một kiểu là an toàn và bớt một nhánh.
     */
    private val partPaths: Map<CarPart, Path> by lazy {
        PARTS.mapValues { (_, pieces) ->
            Path().also { p ->
                pieces.forEach { p.addPath(parse(it)) }
                p.fillType = Path.FillType.EVEN_ODD
            }
        }
    }

    private val partBoxes: Map<CarPart, RectF> by lazy { partPaths.mapValues { boundsOf(it.value) } }

    private val frameBox: RectF by lazy { boundsOf(bodyPath) }

    /**
     * Hộp bao **THÂN + MỌI bộ phận** (hệ icon) — thứ mà bảng cửa phóng vào ô.
     *
     * Khác [frameBounds] (chỉ thân): vạt cửa mở **ra ngoài** thân tới `x 3.9 … 20.1`, nên phóng theo thân sẽ cắt cụt
     * đúng bốn thứ mà bảng đó sinh ra để hiện. Đo bằng phép hợp các hộp bao thật, **không** viết tay bốn số: đổi hình
     * vạt cửa ở script sinh icon là khung tự theo.
     */
    private val openBox: RectF by lazy {
        RectF(frameBox).also { r -> partBoxes.values.forEach { r.union(it) } }
    }

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

    /**
     * Vùng tô của một bộ phận mở được — [Path] dùng chung, xem luật "chỉ đọc" ở KDoc lớp.
     *
     * Trả `null` (không ném) khi `:core` biết một bộ phận mà bộ icon chưa có hình: chỗ gọi bỏ qua bộ phận đó thay vì
     * làm sập cả launcher trên xe. Có bài canh đòi ánh xạ phải **đủ** mọi [CarPart], nên nhánh `null` là lưới an
     * toàn chứ không phải chỗ để im lặng bỏ sót.
     */
    fun part(p: CarPart): Path? = partPaths[p]

    /** Hộp bao của một bộ phận (hệ icon) — chỗ đặt nhãn `%` bám theo chính bộ phận ấy. */
    fun partBounds(p: CarPart, out: RectF): Boolean {
        val box = partBoxes[p] ?: return false
        out.set(box)
        return true
    }

    /** Hộp bao THÂN + mọi bộ phận (xem [openBox]) — khung mà bảng cửa phóng vào ô. */
    fun openFrameBounds(out: RectF) {
        out.set(openBox)
    }

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
