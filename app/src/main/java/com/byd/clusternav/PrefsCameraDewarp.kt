package com.byd.clusternav

import android.content.Context
import com.byd.clusternav.launcher.camera.CameraDefaults
import com.byd.clusternav.launcher.camera.CameraDewarpPrefs
import com.byd.clusternav.launcher.camera.CameraGlUniforms
import com.byd.clusternav.launcher.camera.CameraViewMode
import com.byd.clusternav.launcher.camera.CameraViewPlan

/**
 * ═══ SÁU NÚM NẮN MÉO + công tắc `uTexMatrix` của đường kết xuất `GL` (R8-B · 2.74) ═════════════════════════════
 *
 * `docs/diagnostics/offcar-2026-09-26/camera-dewarp-gl.md`. Hàm mở rộng của [Prefs], **cùng tệp `clusternav_prefs`**
 * với [PrefsAutomation] (dùng lại `autoPrefs` của nó — xem KDoc ở đó về vì sao không mở accessor thứ hai).
 *
 * Tệp riêng vì [PrefsAutomation] đã 323 dòng và bộ này là **một vai khác**: nó không cấu hình một automation nào, nó
 * là bảng tham số quang học của một ống kính. Cùng lẽ `PrefsVoiceV3` tách khỏi `Prefs`.
 *
 * ## Bảy núm nắn + `uTexMatrix` theo XE; `camera_dewarp_amount` theo HỒ SƠ (V-CLUSTER 2026-09-30)
 * Ống kính, cách HAL ghép ảnh 4-in-1, GPU đầu xe — đều là chuyện của **chiếc xe**, không của sở thích người lái. Cùng
 * họ `camera_span`/`camera_render`. Đổi hồ sơ thì bộ số nắn **không** đổi, và đó là hành vi đúng: hai người lái cùng
 * một chiếc xe nhìn cùng một ống kính. Riêng *"có nắn hay không"* (`camera_dewarp_amount` 100/0) là lựa chọn của người
 * lái ⇒ theo hồ sơ. Bảng + lý do từng khoá: `ProfileScopeCluster.CAMERA_DEVICE_KEYS` / `CAMERA_PROFILE_KEYS` (`:core`).
 * ⚠ [ĐO] trước bản này chú thích ghi *"`ProfileScope.DEVICE_KEYS`"* trong khi các khoá chưa từng được khai ở đó
 * (`scopeOf` trả UNKNOWN) — lời nói đi trước mã.
 *
 * ## Mặc định = của HỒ SƠ XE (2.76 · R2), pref đã đặt luôn thắng
 * Khoá VẮNG ⇒ [CameraDefaults.of] (Seal DL3 = bộ owner duyệt trên xe 27/09 `F 55 · K 100 · S 130 · amount 100`; đời xe
 * chưa đo = `CameraProfileDefaults.NEUTRAL` = đúng literal 2.75). Khoá đã ghi trên xe (owner đã dò ba buổi) **không
 * bị đụng**: `getInt(key, fallback)` chỉ dùng fallback khi khoá vắng. Miền hợp lệ + phép suy nằm ở `:core`
 * [CameraDewarpPrefs] — **không một con số nào** viết ở tệp này.
 */

private const val K_DEWARP_AMOUNT = "camera_dewarp_amount"
private const val K_DEWARP_FOCAL = "camera_dewarp_focal"
private const val K_DEWARP_K = "camera_dewarp_k"
private const val K_DEWARP_SCALE = "camera_dewarp_scale"
private const val K_DEWARP_CX = "camera_dewarp_cx"
private const val K_DEWARP_CY = "camera_dewarp_cy"
private const val K_DEWARP_PAN_X = "camera_dewarp_pan_x"
private const val K_DEWARP_PAN_Y = "camera_dewarp_pan_y"
private const val K_GL_TEX_MATRIX = "camera_gl_texmatrix"

// 2.92 · CAMERA-FULL-VIEW (spec `kachi-292-camera-full-view.html`): kiểu hình + thu phóng theo HỒ SƠ (sở thích trình bày,
// `ProfileScopeCluster.CAMERA_PROFILE_KEYS`); ba núm *Thẳng rộng* theo XE (quang học, `CAMERA_DEVICE_KEYS`).
private const val K_PROJECTION = "camera_projection"
private const val K_ZOOM = "camera_zoom"
private const val K_WIDE_KAPPA = "camera_wide_kappa"
private const val K_WIDE_FOCAL = "camera_wide_focal"
private const val K_WIDE_PAN_X = "camera_wide_pan_x"

/**
 * **Kiểu hình camera** — mã trong [CameraViewMode.MODES]. Khoá vắng/lạ ⇒ [CameraViewMode.resolve]: *Nắn thẳng*, trừ khi
 * người lái đã tắt *Nắn hình* (amount 0) trước 2.92 ⇒ *Gương cầu* (người thừa kế đúng ý "ảnh thô").
 */
fun Prefs.cameraProjection(ctx: Context): String =
    CameraViewMode.resolve(autoPrefs(ctx).getString(K_PROJECTION, null), cameraDewarpAmount(ctx))

/** Xem [cameraProjection]. Nhận mã trong [CameraViewMode.MODES]; chuỗi khác ghi được nhưng lượt đọc bỏ qua. */
fun Prefs.setCameraProjection(ctx: Context, v: String) = autoPrefs(ctx).edit().putString(K_PROJECTION, v).apply()

/** **Thu phóng** `%` (50–150, mặc định 100 = khung tự nhiên của kiểu đang chọn). Một khoá cho cả ba kiểu. */
fun Prefs.cameraZoom(ctx: Context): Int =
    pct(ctx, K_ZOOM, CameraViewMode.ZOOM_DEFAULT) { CameraViewMode.isZoomPct(it) }

/** Xem [cameraZoom]. */
fun Prefs.setCameraZoom(ctx: Context, v: Int) = put(ctx, K_ZOOM, v)

/** **κ** của *Thẳng rộng*, `%` tuyệt đối (100–800; mặc định [CameraViewMode.WIDE_KAPPA_PCT_DEFAULT] [ĐOÁN]). */
fun Prefs.cameraWideKappa(ctx: Context): Int =
    pct(ctx, K_WIDE_KAPPA, CameraViewMode.WIDE_KAPPA_PCT_DEFAULT) { CameraDewarpPrefs.isKappaPct(it) }

/** Xem [cameraWideKappa]. */
fun Prefs.setCameraWideKappa(ctx: Context, v: Int) = put(ctx, K_WIDE_KAPPA, v)

/** **F** của *Thẳng rộng*, `%` của bộ suy ra (cùng nghĩa [cameraDewarpFocal]). */
fun Prefs.cameraWideFocal(ctx: Context): Int = pctOf(ctx, K_WIDE_FOCAL, CameraViewMode.WIDE_FOCAL_PCT_DEFAULT)

/** Xem [cameraWideFocal]. */
fun Prefs.setCameraWideFocal(ctx: Context, v: Int) = put(ctx, K_WIDE_FOCAL, v)

/** **Dịch về đuôi** của *Thẳng rộng*, `%` bề ô (âm = về phía đuôi; dấu theo bên như [cameraDewarpPanX]). */
fun Prefs.cameraWidePanX(ctx: Context): Int = pan(ctx, K_WIDE_PAN_X, CameraViewMode.WIDE_PAN_X_PCT_DEFAULT)

/** Xem [cameraWidePanX]. */
fun Prefs.setCameraWidePanX(ctx: Context, v: Int) = put(ctx, K_WIDE_PAN_X, v)

/**
 * **Độ nắn** `%` — `0` = y ảnh thô 2.73, `100` = nắn đủ (mặc định).
 *
 * Khoá vắng/ngoài miền ⇒ mặc định của hồ sơ xe ([CameraDefaults.of], Seal = 100 = [CameraDewarpPrefs.AMOUNT_DEFAULT]), cùng khuôn [Prefs.cameraCirclePct]: prefs sửa tay được qua
 * `prefs_set`, và một giá trị lạ phải cho ra **mặc định biết trước** chứ không phải một biên mà owner tưởng mình chọn.
 */
fun Prefs.cameraDewarpAmount(ctx: Context): Int = pct(ctx, K_DEWARP_AMOUNT, CameraDefaults.of(ctx).amountPct) {
    CameraDewarpPrefs.isAmountPct(it)
}

/** Xem [cameraDewarpAmount]. */
fun Prefs.setCameraDewarpAmount(ctx: Context, v: Int) = put(ctx, K_DEWARP_AMOUNT, v)

/**
 * **Tiêu cự** `F`, tính bằng **`%` của giá trị SUY RA** cho ô đang hiện (`100` = đúng phép suy).
 *
 * Phần trăm, không phải trị tuyệt đối — lý do đầy đủ ở KDoc [CameraDewarpPrefs] (`F` đo bằng *nửa bề ngang ô* nên nó
 * tỉ lệ nghịch với bề ngang ô; một trị tuyệt đối vừa chỉnh đúng sẽ sai ngay khi owner chạm chip *Vùng gương*).
 */
fun Prefs.cameraDewarpFocal(ctx: Context): Int = pctOf(ctx, K_DEWARP_FOCAL, CameraDefaults.of(ctx).focalPct)

/** Xem [cameraDewarpFocal]. */
fun Prefs.setCameraDewarpFocal(ctx: Context, v: Int) = put(ctx, K_DEWARP_FOCAL, v)

/**
 * **K** — hệ số f-theta của ống kính, `%` của giá trị suy ra.
 *
 * Đây là núm *"đúng/sai"*, ba núm kia là *"thẩm mỹ"*: sai `K` thì **đường thẳng vẫn cong** dù đã nắn hết tay
 * (`camera-dewarp-math.md` §4). Thứ tự chỉnh trên xe: tâm → K → tiêu cự → phóng → độ nắn.
 */
fun Prefs.cameraDewarpK(ctx: Context): Int = pctOf(ctx, K_DEWARP_K, CameraDefaults.of(ctx).kPct)

/** Xem [cameraDewarpK]. */
fun Prefs.setCameraDewarpK(ctx: Context, v: Int) = put(ctx, K_DEWARP_K, v)

/**
 * **Phóng** `SCALE`, `%` (mặc định `100` = không phóng thêm).
 *
 * ⚠ Ngược trực giác, và nhãn trên UI phải nói ra: `> 100` = với **sâu hơn** vào ảnh fisheye ⇒ thấy **RỘNG hơn** (vật
 * nhỏ đi). Viền đen ở góc = đã với ra ngoài vòng ảnh ⇒ hạ xuống.
 */
fun Prefs.cameraDewarpScale(ctx: Context): Int = pctOf(ctx, K_DEWARP_SCALE, CameraDefaults.of(ctx).scalePct)

/** Xem [cameraDewarpScale]. */
fun Prefs.setCameraDewarpScale(ctx: Context, v: Int) = put(ctx, K_DEWARP_SCALE, v)

/**
 * **Lệch tâm quang theo x**, `%` bề ô (`0` = đúng tâm đã suy — xem [CameraGlUniforms.sourceCentre]).
 *
 * Là **độ lệch**, không phải vị trí tuyệt đối: tâm suy ra của crop gương nằm *ngoài* ô (`1,25` cho dải 1), nên một
 * *"phần trăm của ô"* tuyệt đối sẽ có mặc định khác nhau cho từng dải ⇒ không có con số nào viết được vào Cài đặt mà
 * đúng cho cả bốn dải. Lý do đầy đủ ở KDoc [CameraDewarpPrefs].
 */
fun Prefs.cameraDewarpCx(ctx: Context): Int = centre(ctx, K_DEWARP_CX, CameraDefaults.of(ctx).centerXPct)

/** Xem [cameraDewarpCx]. */
fun Prefs.setCameraDewarpCx(ctx: Context, v: Int) = put(ctx, K_DEWARP_CX, v)

/** **Lệch tâm quang theo y**, `%` bề ô. Xem [cameraDewarpCx]. */
fun Prefs.cameraDewarpCy(ctx: Context): Int = centre(ctx, K_DEWARP_CY, CameraDefaults.of(ctx).centerYPct)

/** Xem [cameraDewarpCy]. */
fun Prefs.setCameraDewarpCy(ctx: Context, v: Int) = put(ctx, K_DEWARP_CY, v)

/**
 * **Dịch CỬA SỔ theo x**, `%` bề ô, trong ô **CHƯA XOAY** (`0` = khung của 2.74).
 *
 * KHÔNG phải [cameraDewarpCx]: dời tâm quang là đổi chính trục của phép nắn ⇒ *một bên thẳng, bên kia còng*
 * ([ĐO] xe 27/09 — owner bác `cx −10 %`); dịch cửa sổ giữ trục quang đứng yên nên **ảnh vẫn thẳng**. Toán ở
 * KDoc [com.byd.clusternav.launcher.camera.CameraDewarp.panLocal].
 *
 * Dấu: `> 0` ⇒ cửa sổ trượt về phía **+x** của ô ⇒ nội dung trên màn dịch sang **trái**, ở **mọi** góc xoay (ô
 * chưa xoay là hệ quy chiếu).
 *
 * ## Một giá trị = một nghĩa vật lý ở CẢ HAI gương (vá 2.75, soát Opus)
 * [ĐO khung thô xe 27/09 09:58] hai camera gương là **ảnh soi gương của nhau** (thân xe ở mép PHẢI ô gương trái,
 * mép TRÁI ô gương phải; tương quan lật ngang 0,715 vs không lật 0,152). Cộng thẳng pref vào cả hai bên thì một
 * núm kéo hai khung về **hai phía ngược nhau** ⇒ dấu theo bên do [com.byd.clusternav.launcher.camera
 * .CameraDewarpPrefs.panXSign] cấp, gương trái giữ nguyên `+1` (CLAUDE.md §6).
 *
 * ⚠ **[SUY, chưa kiểm trên xe]** chiều tuyệt đối *"âm = về phía đuôi xe"*: nó đọc ra từ một khung tĩnh, và khung
 * ấy cho thấy **thân xe nằm ở mép PHẢI** ô gương trái — ngược với ghi chép ban đầu của làn camera. Chốt bằng G7
 * (`camera-dewarp-gl.md` §E): kéo `−5` một bước trên xe rồi nhìn. Nếu ngược, đổi đúng một dấu ở [panXSign].
 */
fun Prefs.cameraDewarpPanX(ctx: Context): Int = pan(ctx, K_DEWARP_PAN_X, CameraDefaults.of(ctx).panXPct)

/** Xem [cameraDewarpPanX]. */
fun Prefs.setCameraDewarpPanX(ctx: Context, v: Int) = put(ctx, K_DEWARP_PAN_X, v)

/** **Dịch cửa sổ theo y**, `%` bề ô, trong ô CHƯA XOAY. Xem [cameraDewarpPanX]. */
fun Prefs.cameraDewarpPanY(ctx: Context): Int = pan(ctx, K_DEWARP_PAN_Y, CameraDefaults.of(ctx).panYPct)

/** Xem [cameraDewarpPanY]. */
fun Prefs.setCameraDewarpPanY(ctx: Context, v: Int) = put(ctx, K_DEWARP_PAN_Y, v)

/**
 * Áp `SurfaceTexture.getTransformMatrix` vào `uTexMatrix` hay **truyền ma trận đơn vị** — mặc định **BẬT**.
 *
 * Công tắc này là một **phép đo**, không phải một tuỳ chọn thẩm mỹ: RE §7 **Q17 [CHƯA BIẾT]** ma trận thật của camera
 * id 1 trên ROM này. Mặc định BẬT vì đó là thứ AOSP dặn phải làm ([ĐO] `android-10.0.0_r47`
 * `graphics/java/android/graphics/SurfaceTexture.java:44-47`). Tắt ⇒ nếu ảnh **lật dọc** thì chính điều đó là câu
 * trả lời cho Q17 (ma trận thật có chứa phép lật), và dòng `GL uTexMatrix khung đầu` trong `logcat` nói con số.
 */
fun Prefs.cameraGlTexMatrix(ctx: Context): Boolean =
    autoPrefs(ctx).getBoolean(K_GL_TEX_MATRIX, CameraDewarpPrefs.TEX_MATRIX_DEFAULT)

/** Xem [cameraGlTexMatrix]. */
fun Prefs.setCameraGlTexMatrix(ctx: Context, v: Boolean) =
    autoPrefs(ctx).edit().putBoolean(K_GL_TEX_MATRIX, v).apply()

/**
 * Đọc **cả chín khoá một lượt** và dựng bộ uniform cho đường GL — cửa DUY NHẤT mà tầng vẽ đi qua.
 *
 * Một hàm thay vì bảy lượt đọc rải trong `CameraSignalController`: bộ uniform phải **nhất quán** (sáu con số cùng
 * thuộc một lượt chỉnh), và bảy dòng `Prefs.…` ở chỗ gọi là bảy chỗ quên được khi thêm núm thứ tám. Phép hợp thì nằm
 * ở `:core` ([CameraGlUniforms.of]) và test được off-car; hàm này chỉ **đọc đĩa**.
 *
 * 2.92: phép quyết theo KIỂU HÌNH nằm ở `:core` [CameraViewPlan.gl] (có test hình học thật); hàm này chỉ đọc mười một
 * khoá. *Nắn thẳng* ở 100 % ⇒ đúng bộ uniform của 2.91 (bài `CameraViewPlanTest` ghim từng trường).
 *
 * @param mode kiểu đã quy theo đường vẽ ([CameraViewMode.effective]).
 * @param zoomPct thu phóng ([cameraZoom]) do chỗ gọi đọc MỘT lần — cùng giá trị nuôi tỉ lệ ma trận TV/đường rơi
 *   (`CameraViewPlan.tvScale`); đọc lại ở đây là hai lượt tra lệch được (soát 06/10 [P3]).
 * @param crops vùng cắt khung + nội dung đã suy ([CameraViewPlan.crops]) — cùng giá trị truyền cho overlay, KHÔNG tính
 *   lại (hai lượt tính là hai kết quả lệch được).
 * @param strip chỉ số dải đang xem — quyết tâm quang ([CameraGlUniforms.sourceCentre]).
 * @param streamW bề ngang ảnh nguồn (px), ĐO bằng `AVMCamera.getPreviewWidth` ở chỗ gọi. Từ 2.77 chỉ còn **một**
 *   nguồn — khung GHÉP 4-in-1 — nên bề ngang buffer **chính là** bề ngang nội dung; phép chia `/STRIPS` của nguồn
 *   một-kênh (2.75/2.76) đã gỡ cùng nguồn ấy.
 * @param left đang hiện gương TRÁI hay không — **KHÔNG** có mặc định: hai camera gương soi gương nhau nên dấu của
 *   `camera_dewarp_pan_x` phải theo bên ([CameraDewarpPrefs.panXSign]), và một mặc định ở đây là đúng chỗ để quên.
 * @param mirror LẬT GƯƠNG bên này (2.76 L7, [Prefs.cameraMirror]) — đi vào `flipH` = `uSrcRect.z < 0`, tức lật ở
 *   không gian NGUỒN trước xoay/dịch/nắn. Cũng **không** có mặc định, cùng lẽ với [left]: đây là pref theo bên.
 * @param panXSign 2.93 — dấu dịch x của CAMERA đang xem (`CameraWhich.panXSign`: trái +1 · phải −1 · sau/trước 0 ⇒ hai
 *   núm dịch theo bên không áp). KHÔNG mặc định: chỗ gọi duy nhất (`CameraSignalController.openSession`) nói từ camera.
 */
@Suppress("LongParameterList")
fun Prefs.cameraGlUniforms(
    ctx: Context,
    mode: String,
    zoomPct: Int,
    crops: CameraViewPlan.Crops,
    strip: Int,
    rotationDeg: Int,
    streamW: Int,
    streamH: Int,
    left: Boolean,
    mirror: Boolean,
    panXSign: Int,
): CameraGlUniforms = CameraViewPlan.gl(
    mode = mode,
    zoomPct = zoomPct,
    crops = crops,
    strip = strip,
    streamW = streamW,
    streamH = streamH,
    rotationDeg = rotationDeg,
    mirror = mirror,
    left = left,
    knobs = cameraViewKnobs(ctx),
    texMatrix = cameraGlTexMatrix(ctx),
    panXSign = panXSign,
)

/**
 * Mười một núm của một lượt dựng — tám núm *Nắn thẳng* (mặc định của HỒ SƠ XE) + ba núm *Thẳng rộng* (mặc định
 * `:core`). Một lượt đọc, một giá trị: bộ số phải thuộc về CÙNG một lượt chỉnh (xem KDoc [cameraGlUniforms]).
 */
fun Prefs.cameraViewKnobs(ctx: Context): CameraViewPlan.Knobs = CameraViewPlan.Knobs(
    amountPct = cameraDewarpAmount(ctx),
    focalPct = cameraDewarpFocal(ctx),
    kPct = cameraDewarpK(ctx),
    scalePct = cameraDewarpScale(ctx),
    centerXPct = cameraDewarpCx(ctx),
    centerYPct = cameraDewarpCy(ctx),
    panXPct = cameraDewarpPanX(ctx),
    panYPct = cameraDewarpPanY(ctx),
    wideKappaPct = cameraWideKappa(ctx),
    wideFocalPct = cameraWideFocal(ctx),
    widePanXPct = cameraWidePanX(ctx),
)

// Ba hàm dưới nhận `fallback` = trường tương ứng của hồ sơ xe (đã `sane()`, tức đã nằm trong đúng miền của
// [CameraDewarpPrefs.PCT_DEFAULT]/[CameraDewarpPrefs.PAN_DEFAULT]/[CameraDewarpPrefs.CENTER_DEFAULT] và các `is*`).

/** Một khoá `%` của bốn núm tỉ lệ ([CameraDewarpPrefs.isPct]). */
private fun Prefs.pctOf(ctx: Context, key: String, fallback: Int): Int =
    pct(ctx, key, fallback) { CameraDewarpPrefs.isPct(it) }

/** Một khoá dịch cửa sổ ([CameraDewarpPrefs.isPanPct]). */
private fun Prefs.pan(ctx: Context, key: String, fallback: Int): Int =
    pct(ctx, key, fallback) { CameraDewarpPrefs.isPanPct(it) }

/** Một khoá lệch tâm ([CameraDewarpPrefs.isCenterPct]). */
private fun Prefs.centre(ctx: Context, key: String, fallback: Int): Int =
    pct(ctx, key, fallback) { CameraDewarpPrefs.isCenterPct(it) }

/** Đọc một `Int`, ngoài miền ⇒ [fallback]. Một thân hàm cho cả sáu núm — không sáu bản sao của cùng ba dòng. */
private inline fun Prefs.pct(ctx: Context, key: String, fallback: Int, ok: (Int) -> Boolean): Int {
    val raw = autoPrefs(ctx).getInt(key, fallback)
    return if (ok(raw)) raw else fallback
}

private fun Prefs.put(ctx: Context, key: String, v: Int) = autoPrefs(ctx).edit().putInt(key, v).apply()
