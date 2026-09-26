package com.byd.clusternav.launcher.camera

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/**
 * ═══ NẮN FISHEYE → PHỐI CẢNH THẲNG — mô hình THUẦN, test off-car ═══════════════════════════════════════════════
 *
 * Toán của [CameraDewarpShader]: đây là **bản gốc duy nhất** của công thức, shader GLSL là bản dịch từng dòng
 * (bài `CameraDewarpTest` ghim cả hai lại với nhau). Không có bản sao thứ hai của công thức ở `:app`.
 *
 * ## Nguồn gốc — copy CẤU TRÚC của Electro, KHÔNG copy số
 * `docs/diagnostics/electro-camera-RE-2026-09-26.md` §3.2/§3.3 [ĐO] đọc được nguyên văn shader E của Electro
 * (`br.com.rory.electro` 1.13.0): nguồn được coi là fisheye **đẳng khoảng** (equidistant, `r = f·θ`), đích là
 * **phối cảnh thẳng** (rectilinear/gnomonic, `r = F·tan θ`), nối với nhau bằng đúng ba dòng
 * `theta = atan(pLen, F)` · `r = theta * K` · `mix(local, projected, amount)`. Cả `.rodata` chỉ có **đúng một**
 * `atan(`, không `pow(`/`asin(`/`sqrt(`/`tan(` ⇒ loại trừ vét cạn equisolid/stereographic/đa thức (§3.3).
 *
 * Bốn con số `F`/`K`/`SCALE`/`AMOUNT` của Electro nằm trong bytecode VMP ⇒ **[CHƯA BIẾT]** (§7 Q6). Vì thế Kachi
 * **không** bê số của nó mà (a) suy ra bộ mặc định từ hình học (xem [DewarpParams]), (b) đưa cả bốn ra chip Cài đặt
 * cho owner chỉnh bằng mắt trên xe, (c) truyền bằng **uniform** chứ không nướng vào nguồn GLSL — Electro nướng cứng
 * nên đổi tham số là phải biên dịch lại program (§3.5), Kachi không lặp lại chuyện đó.
 *
 * ## Ba chỗ Kachi làm KHÁC Electro (đều là lỗi/thiếu của Electro, RE §6.2)
 *  1. **Có hiệu chỉnh tỉ lệ khung** ([aspect]): Electro đo `length(p)` trong không gian ô chuẩn hoá và chỉ nhân một
 *     hệ số vô hướng ⇒ với dải 1280×960 (4:3) các đường đồng-θ là **ellipse** theo pixel, không phải tròn (§3.3
 *     ràng buộc 3). Ở đây bán kính được đo trong đơn vị **nửa bề ngang ô**, trục y quy đổi qua [aspect] ⇒ đồng-θ là
 *     tròn thật. `aspect == 1` ⇒ công thức thu về **đúng** Electro, không lệch một phép nhân nào.
 *  2. **Tâm quang tham số hoá** ([DewarpParams.centerX]/[DewarpParams.centerY]): Electro ghim `(0.5, 0.5)` của ô
 *     (§3.3 ràng buộc 2) — sai với Kachi vì crop gương chỉ rộng 0,10 của ảnh 4-in-1 nên **tâm dải nằm NGOÀI crop**
 *     (dải 1 tâm `x = 0.375`, crop `x[0.25, 0.35]` ⇒ `centerX = 1.25`). Kinex làm đúng chỗ này (tâm theo từng dải,
 *     `(0.375, 0.5)` / `(0.625, 0.5)`) và Kachi theo kinex ⇒ **tâm được phép nằm ngoài `[0,1]`**.
 *  3. **Soi gương không cần uniform riêng**: Electro bỏ qua cờ lật ở nhánh pano (§3.1) mà Kachi cần soi gương ⇒ lật
 *     được nhét vào `uSrcRect` bề rộng/cao **ÂM** ([srcRect]) — một phép nhân đã có sẵn, không thêm nhánh nào.
 *
 * ## Quy ước toạ độ (ba không gian, đừng trộn)
 *  • **dst** `(u,v)` ∈ `[0,1]²` — toạ độ trên ô ra (`vTexCoord` của quad). Chưa xoay.
 *  • **local** `(a,b)` — dst sau [rotateDstToLocal]; cũng là không gian mà phép nắn làm việc, và là không gian mà
 *    `uSrcRect` ánh xạ ra texture. Tức **local vừa là đích vừa là nguồn** — đúng thiết kế của Electro (§3.2 bước 4).
 *  • **p-space** — local dời về tâm quang rồi quy đổi đẳng hướng, đơn vị = **nửa bề ngang ô**: `1.0` = nửa bề ngang,
 *    `aspect` nghịch đảo lo phần trục y. Mọi bán kính (`F`, `K·SCALE`, `r_dst`, `r_src`) đều trong đơn vị này.
 *
 * ## Công thức (bản gốc — [FORMULA])
 * ```
 * p      = ((a − cx)·2, (b − cy)·2/aspect)          // p-space, đẳng hướng
 * r_dst  = |p| ;  dir = p/r_dst
 * theta  = atan(r_dst / F)                          // đích là phối cảnh thẳng ⇒ r_dst = F·tan θ
 * r_src  = (K·SCALE) · theta                        // nguồn là fisheye đẳng khoảng ⇒ r_src = f·θ
 * proj   = (cx + dir.x·r_src·0.5, cy + dir.y·r_src·0.5·aspect)
 * out    = mix(local, proj, clamp(amount, 0, 1))
 * ```
 * ⚠ `amount` là một phép **TRỘN**, không phải mô hình: chỉ `amount == 1` mới thật sự là equidistant. Ở giữa,
 * `r_eff = (1−a)·r_dst + a·K·SCALE·atan(r_dst/F)` — không thuộc mô hình quang học có tên nào (RE §3.3). Vì vậy mặc
 * định là `1.0`; thanh trượt tồn tại để owner *hạ* xuống nếu nắn quá tay, không phải để tìm một mô hình khác.
 *
 * ## Mức bằng chứng (CLAUDE.md §2)
 *  • Công thức + thứ tự phép toán: **[ĐO]** (đọc nguyên văn `.rodata` của Electro, RE §3.2).
 *  • Bộ số mặc định: **[ĐOÁN]** — suy từ giả định "ống kính đẳng khoảng ~190°, vòng ảnh ≈ bề cao khung". Chưa có một
 *    khung 5120×960 nào chụp từ xe để kiểm (RE §7 Q1/Q6). Chốt bằng: chụp một khung qua `ClusterDiag` (§11) rồi đo
 *    bán kính vòng ảnh thật + xem mắt.
 *  • BYD AVM ~190° rất có thể là **equisolid**, không phải equidistant ⇒ mô hình này sai nhẹ ở vùng biên dù số có
 *    đúng (RE §3.3 cảnh báo). Đó là lý do `K` phải chỉnh được, không phải hằng.
 */
data class DewarpParams(
    /** Cường độ trộn `0..1`. `0` = không nắn (đúng đường 2.73), `1` = nắn đủ theo mô hình. Chip **Độ nắn**. */
    val amount: Float = DEFAULT_AMOUNT,
    /** `F` — tiêu cự của khung RA, đơn vị nửa bề ngang ô. Nửa-FOV ngang = `atan(1/F)`. Chip **Tiêu cự**. */
    val focal: Float = DEFAULT_FOCAL,
    /** `K` — hệ số f-theta của ống kính: bán kính nguồn (nửa bề ngang ô) trên mỗi radian. Chip **K**. */
    val k: Float = DEFAULT_K,
    /** `SCALE` — phóng thêm. `> 1` = với sâu hơn vào fisheye ⇒ **rộng hơn** (thu nhỏ). Chip **Phóng**. */
    val scale: Float = DEFAULT_SCALE,
    /** Tâm quang theo trục x, trong local. Được phép **ngoài `[0,1]`** (crop hẹp hơn dải — xem KDoc lớp). */
    val centerX: Float = DEFAULT_CENTER,
    /** Tâm quang theo trục y, trong local. */
    val centerY: Float = DEFAULT_CENTER,
) {
    /** `K·SCALE` — hệ số f-theta hiệu dụng, đúng một tham số tỉ lệ như RE §3.3 đã gộp. */
    val gain: Float get() = k * scale

    /**
     * Phép nắn có thật sự chạy hay không — **cùng ba cổng với shader** (`amount > 1e-4` của Electro `<FISH_ON>`,
     * `F > 0` theo RE §3.3 ràng buộc 4 vì `atan2` với đối số thứ hai âm trả góc trong `(π/2, π]`, và `K·SCALE > 0`).
     */
    val enabled: Boolean get() = amount > AMOUNT_EPS && focal > 0f && gain > 0f

    /** Bán kính nguồn lớn nhất còn với tới được khi `amount == 1` — `K·SCALE·π/2`, tức đúng tia 90°. */
    val reachableSrcRadius: Float get() = (gain * PI / 2.0).toFloat()

    /** Nửa-FOV ngang của khung ra (độ) — nhãn cho chip **Tiêu cự**. */
    val outputHalfFovDeg: Float get() = if (focal > 0f) Math.toDegrees(atan(1.0 / focal)).toFloat() else 0f

    /**
     * Bản đã kẹp về miền dùng được. Gọi ở **biên** (đọc prefs / nhận giá trị chip), không gọi trong vòng lặp pixel.
     *
     * Kẹp chứ không nổ: các số này đến từ thanh trượt và từ `Prefs` sửa tay được; một `NaN` lọt vào uniform thì cả
     * khung ra đen mà không ai báo lỗi, còn kẹp thì tệ nhất là ảnh không đẹp.
     */
    fun clamped(): DewarpParams = DewarpParams(
        amount = amount.sane(DEFAULT_AMOUNT).coerceIn(0f, 1f),
        focal = focal.sane(DEFAULT_FOCAL).coerceIn(MIN_FOCAL, MAX_FOCAL),
        k = k.sane(DEFAULT_K).coerceIn(MIN_GAIN, MAX_GAIN),
        scale = scale.sane(DEFAULT_SCALE).coerceIn(MIN_GAIN, MAX_GAIN),
        centerX = centerX.sane(DEFAULT_CENTER).coerceIn(MIN_CENTER, MAX_CENTER),
        centerY = centerY.sane(DEFAULT_CENTER).coerceIn(MIN_CENTER, MAX_CENTER),
    )

    private fun Float.sane(fallback: Float): Float = if (isNaN() || isInfinite()) fallback else this

    companion object {
        /** Ngưỡng bật của Electro (`<FISH_ON> > 0.0001`, RE §3.2) — giữ nguyên để hai bên cùng một cổng. */
        const val AMOUNT_EPS = 1e-4f

        /** `amount` mặc định: nắn đủ. Cả tính năng đã nằm sau một cờ Cài đặt TẮT sẵn (RE §6.2). */
        const val DEFAULT_AMOUNT = 1f

        /**
         * `K` mặc định = **0,452335** — [ĐOÁN], suy ra từ hai giả định, kiểm lại bằng [derive]:
         *  • ống kính đẳng khoảng, FOV toàn phần **190°** ⇒ `θmax = 95° = 1,658063 rad`;
         *  • **vòng ảnh ≈ bề cao khung** ⇒ bán kính vòng ảnh `480 px` trên dải `1280×960`, tức `0,75` đơn vị
         *    nửa-bề-ngang (`480/640`).
         * ⇒ `K = 0,75 / 1,658063 = 0,452335` (bán kính nguồn trên mỗi radian).
         */
        const val DEFAULT_K = 0.452335f

        /**
         * `F` mặc định = `K·SCALE` ⇒ **độ phóng tại tâm = 1**: chính giữa khung, ảnh nắn và ảnh thô **trùng nhau**,
         * nên thanh Độ nắn chỉ đổi vùng biên. Hệ quả: nửa-FOV ngang của khung ra = `atan(1/0,452335) = 65,7°`
         * (FOV ngang ≈ **131°**), và ở góc ô 4:3 phép nắn với tới tia 70,1° ⇒ `r_src = 354 px` — **trong** vòng ảnh
         * 480 px, không lọt vào vùng đen của thấu kính.
         */
        const val DEFAULT_FOCAL = DEFAULT_K

        /** `SCALE` mặc định: không phóng thêm. */
        const val DEFAULT_SCALE = 1f

        /** Tâm mặc định = tâm ô (đúng Electro). Crop hẹp hơn dải thì chỗ gọi PHẢI truyền tâm thật. */
        const val DEFAULT_CENTER = 0.5f

        /** FOV toàn phần giả định của ống kính AVM (độ) — [ĐOÁN], xem KDoc lớp. */
        const val DEFAULT_LENS_FOV_DEG = 190f

        const val MIN_FOCAL = 1e-3f
        const val MAX_FOCAL = 64f
        const val MIN_GAIN = 1e-3f
        const val MAX_GAIN = 64f

        /** Tâm quang được phép ra ngoài ô tới 4 lần bề ô: crop `0,10` của dải `0,25` ⇒ `centerX = 1,25` là thường. */
        const val MIN_CENTER = -4f
        const val MAX_CENTER = 5f

        /**
         * Bộ tham số suy từ **hình học**, không phải từ số của Electro — dùng cho mặc định và cho test.
         *
         * @param rectWidthPx bề ngang ô (= `uSrcRect.z` × bề ngang texture), px.
         * @param rectHeightPx bề cao ô, px. Chỉ dùng cho [aspectOf], không vào `K`.
         * @param imageCircleDiameterPx đường kính vòng ảnh của fisheye, px. Mặc định = bề cao khung (giả định).
         * @param lensFovDeg FOV toàn phần của ống kính, độ.
         * @param outputFovDeg FOV ngang toàn phần muốn có ở khung ra, độ. `<= 0` ⇒ chọn `F = K·SCALE`, tức **độ
         *   phóng tại tâm = 1** (xem [DEFAULT_FOCAL]).
         *
         * ⚠ `K` và `F` **tỉ lệ nghịch với bề ngang ô**: thu ô còn một nửa thì cùng một phép ánh xạ vật lý cần `K`
         * và `F` **gấp đôi** (cả `r_src` lẫn `r_dst` đo bằng nửa-bề-ngang, nửa-bề-ngang giảm một nửa). Đổi crop mà
         * quên đổi hai số này là cách chắc chắn nhất để nắn sai — dùng hàm này thay vì bê hằng.
         */
        fun derive(
            rectWidthPx: Float,
            rectHeightPx: Float,
            imageCircleDiameterPx: Float = rectHeightPx,
            lensFovDeg: Float = DEFAULT_LENS_FOV_DEG,
            outputFovDeg: Float = 0f,
            amount: Float = DEFAULT_AMOUNT,
            scale: Float = DEFAULT_SCALE,
            centerX: Float = DEFAULT_CENTER,
            centerY: Float = DEFAULT_CENTER,
        ): DewarpParams {
            val thetaMax = Math.toRadians(lensFovDeg.toDouble() / 2.0)
            val circleRadiusNorm = imageCircleDiameterPx.toDouble() / rectWidthPx.toDouble()
            val k = if (thetaMax > 0.0 && rectWidthPx > 0f) circleRadiusNorm / thetaMax else DEFAULT_K.toDouble()
            val focal = if (outputFovDeg > 0f && outputFovDeg < 180f) {
                1.0 / tan(Math.toRadians(outputFovDeg.toDouble() / 2.0))
            } else {
                k * scale
            }
            return DewarpParams(amount, focal.toFloat(), k.toFloat(), scale, centerX, centerY).clamped()
        }

        /** Tỉ lệ ô = bề ngang / bề cao **theo pixel NGUỒN**, tức thứ phải truyền vào `uAspect`. */
        fun aspectOf(rectWidthPx: Float, rectHeightPx: Float): Float =
            if (rectWidthPx > 0f && rectHeightPx > 0f) rectWidthPx / rectHeightPx else 1f
    }
}

/**
 * ═══ Các phép ánh xạ thuần của [DewarpParams] ═════════════════════════════════════════════════════════════════
 *
 * Xem KDoc [DewarpParams] cho toán, quy ước toạ độ và mức bằng chứng. Tất cả là hàm thuần, không state, an toàn
 * đa luồng; không có một `import android.*` nào (`LayeringRulesTest` cưỡng chế).
 */
object CameraDewarp {

    /** Bản gốc của công thức — [CameraDewarpShader.FORMULA] trả **cùng** chuỗi này (bài test ghim). */
    const val FORMULA =
        "p=((a-cx)*2,(b-cy)*2/aspect); theta=atan(|p|,F); r_src=(K*SCALE)*theta; " +
            "proj=(cx+dir.x*r_src*0.5, cy+dir.y*r_src*0.5*aspect); out=mix(local,proj,clamp(amount,0,1))"

    /** Dưới ngưỡng này coi như đang đứng đúng tâm quang ⇒ trả về chính nó (giới hạn đúng của công thức). */
    private const val CENTER_EPS = 1e-6f

    /**
     * **dst → local**: xoay [rotationDeg] quanh tâm ô, **trong không gian ô đã chuẩn hoá**.
     *
     * Vì sao chuẩn hoá chứ không phải pixel: đó là đúng thứ [CameraOverlayTransform] đang làm trên xe. Ma trận
     * ở đó = crop → xoay quanh tâm view → bù tỉ lệ `(vw/vh, vh/vw)` khi xoay ±90; nhân ba bước ấy ra thì
     * phần xoay+bù **rút gọn thành một phép xoay trong toạ độ chuẩn hoá**, sạch tỉ lệ: `90° ⇒ (a,b) = (dy, 1−dx)`,
     * `180° ⇒ (1−dx, 1−dy)`, `270° ⇒ (1−dy, dx)`. Giữ nguyên quy ước dấu của
     * [CameraSignalPolicy.rotationDegrees] (**dương = cùng chiều kim đồng hồ** trên màn, trục y hướng xuống) ⇒ ma
     * trận lấy mẫu là **chuyển vị** của phép xoay ấy.
     *
     * ⚠ Chỉ **bội của 90** là phép đẳng hình; góc khác sẽ **xiên** ô không vuông (xoay chuẩn hoá ≠ xoay pixel).
     * [CameraSignalPolicy.rotationDegrees] chỉ sinh −90/0/90/180 nên đây là miền thật; hàm vẫn nhận góc khác để
     * chỗ gọi không phải tự chặn, và bài `xoay goc le thi xien` ghim đúng sự thật ấy.
     */
    fun rotateDstToLocal(u: Float, v: Float, rotationDeg: Int): Pair<Float, Float> {
        val deg = ((rotationDeg % 360) + 360) % 360
        if (deg == 0) return u to v
        val qx = u - 0.5f
        val qy = v - 0.5f
        val (c, s) = when (deg) {
            90 -> 0f to 1f
            180 -> -1f to 0f
            270 -> 0f to -1f
            else -> {
                val r = Math.toRadians(deg.toDouble())
                cos(r).toFloat() to sin(r).toFloat()
            }
        }
        return (0.5f + c * qx + s * qy) to (0.5f + c * qy - s * qx)
    }

    /**
     * **local(đích) → local(nguồn)**: phép nắn, đúng công thức [FORMULA] — bản gốc của shader.
     *
     * @param u,v toạ độ local (đã qua [rotateDstToLocal]), `[0,1]²` trong ca thường.
     * @param aspect bề ngang/bề cao ô **theo pixel nguồn** ([DewarpParams.aspectOf]). `1` ⇒ trùng khít Electro.
     * @return vị trí lấy mẫu trong cùng không gian local; có thể **ra ngoài `[0,1]`** ⇒ chỗ gọi (và shader) trả
     *   **đen đặc**, đúng như Electro làm (RE §3.2, hai chỗ `gl_FragColor = vec4(0,0,0,1)`).
     */
    fun mapDstToSrc(u: Float, v: Float, p: DewarpParams, aspect: Float): Pair<Float, Float> {
        if (!p.enabled) return u to v
        val asp = if (aspect > 0f && !aspect.isNaN() && !aspect.isInfinite()) aspect else 1f
        val px = (u - p.centerX) * 2f
        val py = (v - p.centerY) * 2f / asp
        val rDst = hypot(px.toDouble(), py.toDouble()).toFloat()
        if (rDst <= CENTER_EPS) return u to v
        val dirX = px / rDst
        val dirY = py / rDst
        // atan 2 đối số = atan2; `focal > 0` đã được [DewarpParams.enabled] canh (RE §3.3 ràng buộc 4).
        val theta = atan2(rDst.toDouble(), p.focal.toDouble())
        val rSrc = (p.gain.toDouble() * theta).toFloat()
        val projX = p.centerX + dirX * rSrc * 0.5f
        val projY = p.centerY + dirY * rSrc * 0.5f * asp
        val a = p.amount.coerceIn(0f, 1f)
        return (u + (projX - u) * a) to (v + (projY - v) * a)
    }

    /**
     * Nghịch đảo của [mapDstToSrc]: **local(nguồn) → local(đích)** — điểm nào của ảnh fisheye hiện ra ở đâu trên
     * khung đã nắn. Dùng để dựng ảnh kiểm tra và để chứng minh "đường thẳng vẫn thẳng".
     *
     * Bán kính hiệu dụng `r_eff(r_dst) = (1−a)·r_dst + a·K·SCALE·atan(r_dst/F)` **tăng đơn điệu** nên nghịch đảo là
     * duy nhất; giải bằng công thức đóng khi `a == 1` (`r_dst = F·tan(r_src/(K·SCALE))`), còn lại chia đôi.
     *
     * @return `null` khi bán kính nguồn **không với tới được**: với `a == 1` phép nắn chỉ chạm tới
     *   [DewarpParams.reachableSrcRadius] (`K·SCALE·π/2`, ứng với tia 90°) — vành ngoài của vòng ảnh nằm sau 90°
     *   nên **không có** điểm đích nào chiếu tới, đó là tính chất của phối cảnh thẳng chứ không phải lỗi.
     */
    fun forwardSrcToDst(u: Float, v: Float, p: DewarpParams, aspect: Float): Pair<Float, Float>? {
        if (!p.enabled) return u to v
        val asp = if (aspect > 0f && !aspect.isNaN() && !aspect.isInfinite()) aspect else 1f
        val px = (u - p.centerX) * 2f
        val py = (v - p.centerY) * 2f / asp
        val rSrc = hypot(px.toDouble(), py.toDouble())
        if (rSrc <= CENTER_EPS) return u to v
        val rDst = solveDstRadius(rSrc, p) ?: return null
        val dirX = px / rSrc.toFloat()
        val dirY = py / rSrc.toFloat()
        return (p.centerX + dirX * rDst * 0.5f) to (p.centerY + dirY * rDst * 0.5f * asp)
    }

    /** `r_eff` của [forwardSrcToDst] — tách ra để test đơn điệu trực tiếp trên nó. */
    fun effectiveSrcRadius(rDst: Float, p: DewarpParams): Float {
        val a = p.amount.coerceIn(0f, 1f)
        val bent = p.gain.toDouble() * atan2(rDst.toDouble(), p.focal.toDouble())
        return ((1.0 - a) * rDst + a * bent).toFloat()
    }

    private fun solveDstRadius(rSrc: Double, p: DewarpParams): Float? {
        val a = p.amount.coerceIn(0f, 1f)
        val gain = p.gain.toDouble()
        if (a >= 1f - 1e-6f) {
            val theta = rSrc / gain
            if (theta >= PI / 2.0 - 1e-9) return null
            return (p.focal.toDouble() * tan(theta)).toFloat()
        }
        var hi = 1.0
        var guard = 0
        while (effectiveSrcRadius(hi.toFloat(), p) < rSrc) {
            hi *= 2.0
            if (++guard > 200) return null
        }
        var lo = 0.0
        repeat(80) {
            val mid = 0.5 * (lo + hi)
            if (effectiveSrcRadius(mid.toFloat(), p) < rSrc) lo = mid else hi = mid
        }
        return (0.5 * (lo + hi)).toFloat()
    }

    /**
     * Ống kính đẳng khoảng "lý tưởng" **mô tả đúng bởi [p]**: một điểm của khung phối cảnh thẳng cách quang tâm
     * `(sceneX, sceneY)` (đơn vị: mặt phẳng ở khoảng cách 1, tức `tan` của góc tia) rơi vào đâu trên ảnh nguồn.
     *
     * Đây là **mô hình chụp**, đối xứng với [mapDstToSrc] là **mô hình lấy mẫu**. Vì `K·SCALE` dùng chung, nắn với
     * `amount = 1` sẽ khôi phục mặt phẳng **chính xác** (tỉ lệ `F/2`, xem bài `duong thang van thang`) ⇒ bài test
     * dùng nó làm chuẩn, và [CameraDewarpTestPattern] dùng nó để vẽ ảnh fisheye tổng hợp.
     */
    fun idealEquidistantSource(sceneX: Float, sceneY: Float, p: DewarpParams, aspect: Float): Pair<Float, Float> {
        val asp = if (aspect > 0f) aspect else 1f
        val rho = hypot(sceneX.toDouble(), sceneY.toDouble())
        if (rho <= CENTER_EPS) return p.centerX to p.centerY
        val theta = atan(rho)
        val rSrc = p.gain.toDouble() * theta
        val dirX = sceneX / rho
        val dirY = sceneY / rho
        return (p.centerX + (dirX * rSrc * 0.5).toFloat()) to
            (p.centerY + (dirY * rSrc * 0.5 * asp).toFloat())
    }

    /** Bán kính nguồn (đơn vị nửa-bề-ngang ô) của tia [thetaRad] trên ống kính đẳng khoảng của [p]. */
    fun equidistantSrcRadius(thetaRad: Float, p: DewarpParams): Float = p.gain * thetaRad

    /** Góc tia (rad) ứng với bán kính nguồn [rSrc] — nghịch đảo của [equidistantSrcRadius]. */
    fun equidistantTheta(rSrc: Float, p: DewarpParams): Float = if (p.gain > 0f) rSrc / p.gain else 0f

    /**
     * `uSrcRect` = `(x, y, w, h)` từ `crop` kiểu `(x0, y0, x1, y1)` của [CameraSignalPolicy.CamView].
     *
     * Soi gương đi bằng **bề rộng/cao ÂM**, không bằng uniform mới: `raw = xy + corrected·zw` vốn đã là một phép
     * nhân, cho `zw < 0` là lật — nên shader không cần thêm nhánh nào cho việc Electro làm thiếu (RE §3.1, §6.2).
     * `null` / `crop` ngắn ⇒ nguyên khung.
     */
    fun srcRect(crop: FloatArray?, flipH: Boolean = false, flipV: Boolean = false): FloatArray {
        val x0 = if (crop != null && crop.size >= 4) crop[0] else 0f
        val y0 = if (crop != null && crop.size >= 4) crop[1] else 0f
        val x1 = if (crop != null && crop.size >= 4) crop[2] else 1f
        val y1 = if (crop != null && crop.size >= 4) crop[3] else 1f
        val x = if (flipH) x1 else x0
        val y = if (flipV) y1 else y0
        val w = if (flipH) x0 - x1 else x1 - x0
        val h = if (flipV) y0 - y1 else y1 - y0
        return floatArrayOf(x, y, w, h)
    }

    /** `uSrcRect.xy + local·uSrcRect.zw` — bước cuối của shader (RE §3.2 bước 4), tách ra để test được. */
    fun applySrcRect(u: Float, v: Float, srcRect: FloatArray): Pair<Float, Float> =
        (srcRect[0] + u * srcRect[2]) to (srcRect[1] + v * srcRect[3])

    /**
     * Tâm quang trong toạ độ **local của `crop`**, biết tâm ấy ở đâu trong ảnh NGUỒN chuẩn hoá.
     *
     * Ví dụ dải 1 của ảnh 4-in-1: tâm nguồn `x = 0.375` (RE §2.1, kinex `Y0/C0094o.java:76`), crop gương trái
     * `x[0.25, 0.35]` ⇒ `centerX = (0.375 − 0.25) / 0.10 = 1.25` — **ngoài `[0,1]`**, và đó là bình thường.
     */
    fun centerInCrop(srcCenterX: Float, srcCenterY: Float, crop: FloatArray?): Pair<Float, Float> {
        if (crop == null || crop.size < 4) return srcCenterX to srcCenterY
        val w = span(crop[2] - crop[0])
        val h = span(crop[3] - crop[1])
        return ((srcCenterX - crop[0]) / w) to ((srcCenterY - crop[1]) / h)
    }

    /** Dải crop, chặn dưới bằng **cùng** ngưỡng [CameraOverlayTransform.MIN_SPAN] — hai tầng phải coi cùng một dải
     * suy biến là cùng một con số, nếu không thì tâm tính theo dải này mà ma trận căng theo dải kia. */
    private fun span(raw: Float): Float {
        val minSpan = CameraOverlayTransform.MIN_SPAN
        if (raw.isNaN() || abs(raw) < minSpan) return minSpan
        return raw
    }

    /**
     * Cả chuỗi của một pixel, **đúng thứ tự shader**: xoay → nắn → `uSrcRect` → toạ độ texture chuẩn hoá.
     *
     * @return `null` khi điểm rơi **ngoài** ô sau khi nắn ⇒ shader trả đen đặc. Chỗ gọi off-car (ảnh kiểm tra,
     *   test) dùng `null` làm "pixel đen", không phải làm lỗi.
     */
    fun sample(
        u: Float,
        v: Float,
        rotationDeg: Int,
        p: DewarpParams,
        aspect: Float,
        srcRect: FloatArray,
    ): Pair<Float, Float>? {
        val (a, b) = rotateDstToLocal(u, v, rotationDeg)
        val (ca, cb) = mapDstToSrc(a, b, p, aspect)
        if (ca < 0f || ca > 1f || cb < 0f || cb > 1f) return null
        return applySrcRect(ca, cb, srcRect)
    }
}
