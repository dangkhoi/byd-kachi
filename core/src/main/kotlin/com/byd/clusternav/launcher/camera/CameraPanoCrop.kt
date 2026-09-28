package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraSignalPolicy.CamView

/**
 * ═══ HÌNH HỌC vùng cắt trong ảnh pano 4-in-1 — THUẦN, test off-car ═════════════════════════════════════════════
 *
 * Trả lời đúng một câu: *"với dải nào, bề rộng nào, hình khung nào thì crop là hình chữ nhật nào"* — và trả lời bằng
 * **phép tính từ (chỉ số dải, bề rộng)**, không phải bằng hai con số nướng cứng như 2.73.
 *
 * ## Vì sao phải suy ra chứ không giữ hai rect
 * RE `docs/diagnostics/electro-camera-RE-2026-09-26.md` §2.1 [ĐO]: ảnh pano là **4 dải DỌC bằng nhau**, mỗi dải
 * `0.25` bề ngang, cao trọn khung (`5120×960 = 4 × 1280×960`) — hằng `0.25` nằm thẳng trong shader của Electro
 * (@0x493b5). Hai rect của 2.73 (`x[0.25..0.35]` và `x[0.65..0.75]`, RE kinex `Y0/C0094o.java:70,73`) **nằm trong**
 * hai dải khác nhau: dải 1 = `[0.25, 0.50)` và dải 2 = `[0.50, 0.75)`. Nhưng **dải nào là hướng nào thì
 * [CHƯA BIẾT]** (§7 Q1/Q2: Electro không gán nhãn ngữ nghĩa cho dải ở bất cứ đâu đọc được; dải 0 và 3 chưa ai nhìn)
 * ⇒ chỉ số dải phải là thứ owner **dò được trên xe**, và bề rộng phải suy ra từ nó. Giữ hai rect thì mỗi lần thử một
 * dải khác là một lượt build lại APK (CLAUDE.md §7: khác biệt lộ ra qua đo đạc, không nướng vào code).
 *
 * ## Ba mặc định phải TRÙNG 2.73 từng pixel (CLAUDE.md §6)
 * `(dải 1, [CameraSignalPolicy.SPAN_NARROW], bên TRÁI)` ⇒ `0.25 … 0.35`; `(dải 2, NARROW, bên PHẢI)` ⇒
 * `0.65 … 0.75`. Bài `mac dinh trung 2 rect cua 2 73` ghim đúng hai con số đó — **đổi công thức mà lệch một chữ số
 * là bài đó đỏ**, không phải mắt owner trên xe.
 *
 * ## Vì sao NARROW neo vào mép, không phải vào tâm dải
 * Hai rect của 2.73 **đối xứng gương quanh `x = 0.5`** (0.25↔0.75, 0.35↔0.65) — tức vệt hẹp của bên TRÁI nằm ở **đầu**
 * dải của nó và của bên PHẢI ở **cuối** dải của nó, chứ không cái nào ở giữa dải. Neo theo bên tái lập đúng điều đó
 * cho mọi chỉ số dải; neo vào tâm dải sẽ cho `0.3125…0.4125` — lệch khỏi thứ đang chạy hiện trường.
 *
 * ## Toán làm bằng `Double` rồi mới hạ `Float`
 * `0.25f + 0.1f` và `0.75f - 0.1f` **không** cho đúng `0.35f`/`0.65f` theo cùng một hướng làm tròn; cùng phép đó
 * trong `Double` rồi `toFloat()` thì trùng literal tuyệt đối. Vì mặc định phải trùng 2.73 *từng pixel*, sai số một
 * ulp là thứ phải khử ở đây, không phải thứ để bài test nới dung sai cho qua.
 */
object CameraPanoCrop {

    /** Số dải dọc của khung pano [ĐO RE §2.1 — hằng `0.25` trong shader Electro @0x493b5]. */
    const val STRIPS = 4

    /** Chỉ số dải nhỏ nhất. Dải 0 và [STRIP_MAX] **chưa ai nhìn** (RE §7 Q1) ⇒ có trong tập chọn, không là mặc định. */
    const val STRIP_MIN = 0

    /** Chỉ số dải lớn nhất. */
    const val STRIP_MAX = STRIPS - 1

    /** Bề rộng một dải, chuẩn hoá: `0.25`. */
    const val STRIP_SPAN = 1.0 / STRIPS

    /** Bề rộng vệt HẸP của 2.73: `0.10` bề ngang ảnh = 40 % một dải [ĐO kinex `Y0/C0094o.java:70,73`]. */
    const val NARROW_SPAN = 0.10

    /**
     * Cỡ khung ẢNH GHÉP 4-trong-1 [ĐO xe 2026-09-27, hai PNG khung thô 5120×960] — cũng là hai số mà hai góc
     * Gương đang khai sẵn. Dùng làm **gợi ý cỡ nguồn** khi người lái bảo "nguồn này là ảnh ghép": thiếu nó thì
     * cửa sổ không biết tỉ lệ khung nên trên màn chính vùng cấp là một ô VUÔNG ⇒ khung bẹt bị kéo bẹp.
     */
    const val PANO_W = 5120

    /** Xem [PANO_W]. */
    const val PANO_H = 960

    /** Lựa chọn "để máy tự theo bên" — dải 1 cho trái, dải 2 cho phải. */
    const val PANO_AUTO = "AUTO"

    /** Lựa chọn "nguyên khung" — đường thoát khi một số camera hoá ra là camera ĐƠN, cắt vào là hỏng. */
    const val PANO_NONE = "NONE"

    /** Mọi lựa chọn dải cho một bên — cũng là thứ tự chip trong Cài đặt. */
    val PANO_MODES: List<String> = listOf(PANO_AUTO, PANO_NONE) + (STRIP_MIN..STRIP_MAX).map { it.toString() }

    /** Giá trị đọc lên có dùng được không (prefs sửa tay được qua `prefs_set`). */
    fun isPanoMode(v: String?): Boolean = v != null && PANO_MODES.any { it.equals(v.trim(), ignoreCase = true) }

    /**
     * Dải sẽ cắt cho lượt hiện này, hay `null` = **không coi nguồn là ảnh ghép** (giữ y hệt hành vi cũ).
     *
     * Vì sao cần — [ĐO xe 2026-09-28, ảnh khung thô owner chụp trên Sealion 6] chọn một số camera khác hai góc
     * Gương thì CÓ hình, nhưng ra nguyên khung ghép 4 dải. Gốc: [cropFor] mở đầu bằng `view.crop`, mà chỉ hai
     * góc Gương mang sẵn rect ⇒ sáu góc kia rơi vào nhánh "nguyên khung" với MỌI tổ hợp dải/bề rộng/hình khung.
     *
     * Hai góc Gương **không đi qua đây** (`view.crop != null` ⇒ `null`): đường của chúng đang chạy tốt ngoài
     * hiện trường, không được đụng (CLAUDE.md §6).
     *
     * Mặc định [PANO_AUTO] an toàn vì sáu góc kia **chưa từng mở được** trước 2.80 (controller luôn dùng
     * `defaultView`), nên không có hành vi hiện trường nào để làm hỏng; và vì bố cục dải đã đo là NHƯ NHAU trên
     * hai đời xe: [ĐO Seal 27/09 + ĐO SL6 28/09] dải 0 = sau · **1 = TRÁI** · **2 = PHẢI** · 3 = trước ⇒
     * [defaultStrip] đúng cho cả hai, không cần rẽ nhánh theo dòng xe (CLAUDE.md §7).
     */
    fun panoStripFor(view: CamView, mode: String?, left: Boolean): Int? {
        if (view.crop != null) return null
        val m = mode?.trim().orEmpty()
        return when {
            m.equals(PANO_NONE, ignoreCase = true) -> null
            m.equals(PANO_AUTO, ignoreCase = true) || m.isEmpty() -> defaultStrip(left)
            else -> m.toIntOrNull()?.takeIf { isStrip(it) } ?: defaultStrip(left)
        }
    }

    /** Cỡ ảnh nguồn báo cho tầng vẽ: ảnh ghép ⇒ [PANO_W]×[PANO_H]; còn lại ⇒ gợi ý của chính góc (có thể là 0). */
    fun streamW(view: CamView, panoStrip: Int?): Int = if (panoStrip != null) PANO_W else view.hintW

    /** Xem [streamW]. */
    fun streamH(view: CamView, panoStrip: Int?): Int = if (panoStrip != null) PANO_H else view.hintH

    /** Dải chứa vệt của 2.73: bên TRÁI dùng dải 1 (`[0.25, 0.50)`), bên PHẢI dải 2 (`[0.50, 0.75)`). */
    fun defaultStrip(left: Boolean): Int = if (left) 1 else 2

    /** Chỉ số dải đọc lên có dùng được không (prefs sửa tay được qua `prefs_set`). */
    fun isStrip(v: Int): Boolean = v in STRIP_MIN..STRIP_MAX

    /** Mọi chỉ số dải — cũng là thứ tự chip trong Cài đặt. */
    val STRIPS_ALL: List<Int> = (STRIP_MIN..STRIP_MAX).toList()

    /** Tâm dải [strip] theo trục x chuẩn hoá — dải 1 ⇒ `0.375`, dải 2 ⇒ `0.625` (khớp tâm fisheye của kinex `:76,79`). */
    fun stripCentre(strip: Int): Double = (strip + 0.5) * STRIP_SPAN

    /**
     * Crop `(x0,y0,x1,y1)` chuẩn hoá cho [view] — hoặc `null` = **hiện nguyên khung**, y 2.73.
     *
     * @param view view đang hiện. View KHÔNG có [CamView.crop] dựng sẵn (các view không-pano) giữ `null` như trước,
     *   **trừ** hình tròn: lúc đó lấy ô vuông giữa khung, vì một cửa sổ tròn trên ảnh chữ nhật thì phải cắt vuông
     *   trước, nếu không hình bị bóp theo một trục.
     * @param left bên xi-nhan — quyết [SPAN_NARROW][CameraSignalPolicy.SPAN_NARROW] neo vào mép nào của dải.
     * @param strip chỉ số dải (`camera_strip_left/right`); ngoài dải ⇒ [defaultStrip].
     * @param span mã bề rộng ([CameraSignalPolicy.SPANS]); mã lạ ⇒ [CameraSignalPolicy.defaultSpan].
     * @param shape mã hình khung ([CameraSignalPolicy.SHAPES]); mã lạ ⇒ [CameraSignalPolicy.defaultShape]. Hình TRÒN
     *   **bỏ qua** [span] (ô vuông giữa dải rộng `H/W`, nằm giữa `0.10` và `0.25` — nó là một bề rộng thứ ba).
     *   *"Theo cụm"* ([CameraClusterBand.SHAPE_CLUSTER], 2.76 R4) chỉ đổi **cửa sổ** (dải giữa của cụm), KHÔNG đổi
     *   vùng cắt ⇒ crop y hệt chữ nhật — ghi tường minh ở đây để không ai đọc nhầm thành "chưa xử lý".
     * @param circlePct phần trăm cạnh ô vuông của hình tròn; ngoài dải ⇒ [CameraSignalPolicy.CIRCLE_PCT_DEFAULT].
     */
    fun cropFor(
        view: CamView,
        left: Boolean,
        strip: Int,
        span: String,
        shape: String,
        circlePct: Int,
        panoStrip: Int? = null,
    ): FloatArray? {
        // Góc đã mang rect dựng sẵn (hai góc Gương) thì BỎ QUA dải truyền vào — không chỉ ở chỗ chọn rect mà ở
        // MỌI chỗ dùng `s`. Bỏ sót vế sau thì một lượt gọi lỡ truyền dải khác sẽ lặng lẽ đổi vùng cắt của Seal.
        val pano = if (view.crop != null) null else panoStrip?.takeIf { isStrip(it) }
        val base = view.crop ?: pano?.let { stripCrop(it) }
        // "Theo cụm" = cửa sổ khác, crop như chữ nhật (KDoc trên) — quy về RECT trước mọi phép so.
        val effective = CameraClusterBand.effectiveShape(shape, onCluster = false)
        val round = (if (CameraSignalPolicy.isShape(effective)) effective else CameraSignalPolicy.defaultShape()) ==
            CameraSignalPolicy.SHAPE_ROUND
        val s = pano ?: if (isStrip(strip)) strip else defaultStrip(left)
        val pct = if (CameraSignalPolicy.isCirclePct(circlePct)) circlePct else CameraSignalPolicy.CIRCLE_PCT_DEFAULT
        val wide = if (CameraSignalPolicy.isSpan(span)) span else CameraSignalPolicy.defaultSpan()
        // Tỉ lệ cao/rộng của ẢNH NGUỒN — chỉ hai view GƯƠNG có gợi ý (5120×960 ⇒ 0.1875). `0` = chưa biết.
        val ratio = when {
            pano != null -> PANO_H.toDouble() / PANO_W
            view.hintW > 0 && view.hintH > 0 -> view.hintH.toDouble() / view.hintW
            else -> 0.0
        }
        return when {
            // Không phải view dải pano và không cần cắt vuông ⇒ y 2.73: nguyên khung.
            base == null && !round -> null
            // Tròn trên view nguyên khung: ô vuông giữa KHUNG. Chưa biết tỉ lệ ⇒ vẫn nguyên khung (cửa sổ đã vuông
            // sẵn khi `CameraOverlayFrame` không có cỡ nguồn, nên vòng bo vẫn ra hình tròn — xem KDoc ở đó).
            base == null -> if (ratio > 0.0) squareCrop(0.5, ratio, pct) else null
            round -> if (ratio > 0.0) squareCrop(stripCentre(s), ratio, pct) else stripCrop(s)
            wide == CameraSignalPolicy.SPAN_STRIP -> stripCrop(s)
            else -> narrowCrop(s, left)
        }
    }

    /** Trọn dải [strip]: `[strip·0.25 … (strip+1)·0.25] × y[0..1]`. */
    fun stripCrop(strip: Int): FloatArray {
        val x0 = strip * STRIP_SPAN
        return rect(x0, 0.0, x0 + STRIP_SPAN, 1.0)
    }

    /**
     * Vệt HẸP của 2.73 trong dải [strip]: rộng [NARROW_SPAN], neo vào **đầu** dải khi [left], **cuối** dải khi phải.
     *
     * `(1, true)` ⇒ `0.25…0.35`; `(2, false)` ⇒ `0.65…0.75` — đúng hai rect đang chạy trên xe.
     */
    fun narrowCrop(strip: Int, left: Boolean): FloatArray {
        val lo = strip * STRIP_SPAN
        val hi = lo + STRIP_SPAN
        return if (left) rect(lo, 0.0, lo + NARROW_SPAN, 1.0) else rect(hi - NARROW_SPAN, 0.0, hi, 1.0)
    }

    /**
     * Ô VUÔNG (theo pixel nguồn) cạnh `pct% × chiều cao ảnh`, tâm tại `x = `[cx]`, y = 0.5`.
     *
     * [ratio] = cao/rộng của ảnh nguồn (0.1875 cho 5120×960) ⇒ cạnh theo trục x chuẩn hoá = `ratio · pct/100`, còn
     * theo trục y = `pct/100`. Với [CameraSignalPolicy.CIRCLE_PCT_MAX] = 100 thì bề cao đúng bằng khung, nên **không
     * có ca nào tràn trục y**; trục x vẫn kẹp vào `[0,1]` cho ca dải biên + pct lớn.
     */
    fun squareCrop(cx: Double, ratio: Double, pct: Int): FloatArray {
        val k = pct / 100.0
        val halfX = ratio * k / 2.0
        val halfY = k / 2.0
        return rect(cx - halfX, 0.5 - halfY, cx + halfX, 0.5 + halfY)
    }

    /** Kẹp vào `[0,1]` rồi hạ `Float` — một cửa duy nhất, để không có rect nào ra khỏi ảnh mà tầng vẽ mới phát hiện. */
    private fun rect(x0: Double, y0: Double, x1: Double, y1: Double): FloatArray = floatArrayOf(
        x0.coerceIn(0.0, 1.0).toFloat(),
        y0.coerceIn(0.0, 1.0).toFloat(),
        x1.coerceIn(0.0, 1.0).toFloat(),
        y1.coerceIn(0.0, 1.0).toFloat(),
    )
}
