package com.byd.clusternav.launcher.camera

/**
 * ═══ MẶC ĐỊNH CAMERA THEO HỒ SƠ XE — THUẦN (`:core`), một giá trị cho một đời DiLink ═══════════════════════════
 *
 * 2.76 (spec `kachi-276-closing.html` R2). Tới 2.75 bộ mặc định camera nằm rải ở **ba** chỗ: hằng `default*()` của
 * [CameraSignalPolicy], hằng `*_DEFAULT` của [CameraDewarpPrefs], và **kênh HAL ghim thẳng trên `CamView`**
 * (`MIRROR_LEFT.channel = 2`) — [P3] review Pass 2: đó là số đo của **một** chiếc xe (Seal DL3) viết vào một enum
 * dùng chung cho mọi xe (CLAUDE.md §7: khác biệt đời xe phải nằm trong hồ sơ, không rải trong code).
 *
 * Lớp này gom **toàn bộ** mặc định mà một hồ sơ xe có thể nói khác đi, để:
 *  1. `ClusterProfile` (`:app`, theo đời DiLink) **khai** bộ đo được của xe ấy — Seal DL3 [ĐO 27/09];
 *  2. tầng prefs đọc `pref ?: profile` — **pref đã đặt tường minh luôn thắng**, chỉ khoá VẮNG mới lấy của hồ sơ;
 *  3. đời xe chưa đo lấy [NEUTRAL] = **đúng từng literal của 2.75** ⇒ không đổi một pixel nào trên xe chưa đo
 *     (CLAUDE.md §6).
 *
 * ⚠ [P3 · soát Opus 2026-09-27] Vế 3 nói về **hồ sơ**, không phải về **đời xe thật**: `ClusterProfile.detectSeed` hiện gom
 * **mọi** head-unit BYD không-DL5 vào `SEAL_DL3` (nó chỉ dò chuỗi `"byd"`), nên một SL6/Atto chạy DiLink 3–4 **cũng** nhận
 * bộ Seal, kể cả bản đồ kênh. Tức *"xe chưa đo"* ở đây = *"hồ sơ chưa khai `camera`"* (DL5 · generic · id lạ), **không** =
 * *"chiếc xe chưa ai đo"*. Nợ ấy ghi ở `camera-ia-profile.md` §6 (§3 Q7): cần một seed riêng khi có số đo của đời thứ hai.
 *
 * ## Vì sao [NEUTRAL] KHÔNG phải bộ Seal
 * Bộ Seal (`STRIP · GL · F 55 % · S 130 %`) đúng cho **ống kính + cách HAL ghép ảnh** của Seal. Một đời xe khác ghép
 * ảnh khác chiều, GPU khác trần texture (RE §7 Q13) — đẩy bộ ấy sang xe chưa đo là đúng cái sai *"đoán rồi ship"*
 * mà CLAUDE.md §2/§3 cấm. Xe chưa đo đi đường 2.75 (`NARROW · TV · 100 %`), và **không có bản đồ kênh** ⇒ tuỳ chọn
 * *Một camera* ẩn đi thay vì hiện ra rồi dò `0..3` mù (xem [hasChannelMap]).
 *
 * ## Vì sao **chuỗi/số**, không enum
 * Cùng lẽ `CORNER_*`/`ROTATE_*`: đây là **đúng giá trị lưu bền** mà `Prefs.camera*` trả về khi khoá vắng — một enum
 * ở đây là bảng đổi mã thứ hai. Mọi trường được [sane] kiểm bằng đúng phép kiểm của prefs, nên hồ sơ viết sai một
 * mã cũng rơi về [NEUTRAL] từng trường, không ném.
 *
 * @property span mã [CameraSignalPolicy.SPANS] — bề rộng vùng gương khi nguồn là khung ghép.
 * @property render mã [CameraSignalPolicy.RENDERS].
 * @property amountPct · [focalPct] · [kPct] · [scalePct] · [centerXPct] · [centerYPct] · [panXPct] · [panYPct] —
 *   tám núm nắn, miền ở [CameraDewarpPrefs].
 * @property rotLeft · [rotRight] mã [CameraSignalPolicy.ROTATIONS] từng bên.
 * @property channelLeft · [channelRight] kênh HAL (`VIEW_CHANNEL_n`, `1..`[CameraSignalPolicy.HAL_MODE_MAX]) của
 *   gương trái/phải ở nguồn [CameraSignalPolicy.SOURCE_CHANNEL]; **`0` = chưa đo**.
 */
data class CameraProfileDefaults(
    val span: String = CameraSignalPolicy.defaultSpan(),
    val render: String = CameraSignalPolicy.defaultRender(),
    val amountPct: Int = CameraDewarpPrefs.AMOUNT_DEFAULT,
    val focalPct: Int = CameraDewarpPrefs.PCT_DEFAULT,
    val kPct: Int = CameraDewarpPrefs.PCT_DEFAULT,
    val scalePct: Int = CameraDewarpPrefs.PCT_DEFAULT,
    val centerXPct: Int = CameraDewarpPrefs.CENTER_DEFAULT,
    val centerYPct: Int = CameraDewarpPrefs.CENTER_DEFAULT,
    val panXPct: Int = CameraDewarpPrefs.PAN_DEFAULT,
    val panYPct: Int = CameraDewarpPrefs.PAN_DEFAULT,
    val rotLeft: String = CameraSignalPolicy.defaultRotation(left = true),
    val rotRight: String = CameraSignalPolicy.defaultRotation(left = false),
    val channelLeft: Int = CHANNEL_UNKNOWN,
    val channelRight: Int = CHANNEL_UNKNOWN,
) {

    /** Góc xoay mặc định của bên [left]. */
    fun rotation(left: Boolean): String = if (left) rotLeft else rotRight

    /** Kênh HAL của gương bên [left] — [CHANNEL_UNKNOWN] khi hồ sơ chưa đo. */
    fun channel(left: Boolean): Int = if (left) channelLeft else channelRight

    /**
     * Hồ sơ có **bản đồ kênh** cho CẢ hai gương không.
     *
     * Không có ⇒ Cài đặt **ẩn** hàng *Nguồn* (chọn *Một camera* mà không biết kênh nào là kênh nào thì
     * `channelFor` rơi về AUTO = dò `0..3` mù — tức một chip trông như có tác dụng mà không). Một bên đo, một bên
     * chưa cũng coi là chưa: hai gương là một cặp, không bày một nửa tính năng.
     */
    val hasChannelMap: Boolean
        get() = isChannel(channelLeft) && isChannel(channelRight)

    /**
     * Bản đã kiểm từng trường: trường nào ngoài miền ⇒ giá trị của [NEUTRAL] cho trường ấy (không kẹp, không ném) —
     * cùng luật *"mặc định biết trước"* của [CameraDewarpPrefs.apply]. Chỗ đọc hồ sơ gọi đúng một lần.
     */
    fun sane(): CameraProfileDefaults = CameraProfileDefaults(
        span = if (CameraSignalPolicy.isSpan(span)) span else NEUTRAL.span,
        render = if (CameraSignalPolicy.isRender(render)) render else NEUTRAL.render,
        amountPct = if (CameraDewarpPrefs.isAmountPct(amountPct)) amountPct else NEUTRAL.amountPct,
        focalPct = if (CameraDewarpPrefs.isPct(focalPct)) focalPct else NEUTRAL.focalPct,
        kPct = if (CameraDewarpPrefs.isPct(kPct)) kPct else NEUTRAL.kPct,
        scalePct = if (CameraDewarpPrefs.isPct(scalePct)) scalePct else NEUTRAL.scalePct,
        centerXPct = if (CameraDewarpPrefs.isCenterPct(centerXPct)) centerXPct else NEUTRAL.centerXPct,
        centerYPct = if (CameraDewarpPrefs.isCenterPct(centerYPct)) centerYPct else NEUTRAL.centerYPct,
        panXPct = if (CameraDewarpPrefs.isPanPct(panXPct)) panXPct else NEUTRAL.panXPct,
        panYPct = if (CameraDewarpPrefs.isPanPct(panYPct)) panYPct else NEUTRAL.panYPct,
        rotLeft = if (CameraSignalPolicy.isRotation(rotLeft)) rotLeft else NEUTRAL.rotLeft,
        rotRight = if (CameraSignalPolicy.isRotation(rotRight)) rotRight else NEUTRAL.rotRight,
        channelLeft = if (isChannel(channelLeft)) channelLeft else CHANNEL_UNKNOWN,
        channelRight = if (isChannel(channelRight)) channelRight else CHANNEL_UNKNOWN,
    )

    companion object {
        /** Kênh chưa đo — `0` trùng `VIEW_DEFAULT` (khung ghép) nên **không bao giờ** là một kênh đơn hợp lệ. */
        const val CHANNEL_UNKNOWN = 0

        /** `v` là một kênh đơn `VIEW_CHANNEL_n` thật (`1..`[CameraSignalPolicy.HAL_MODE_MAX]). */
        fun isChannel(v: Int): Boolean = v in 1..CameraSignalPolicy.HAL_MODE_MAX

        /**
         * **Trung tính** = đúng từng literal của 2.75 (`NARROW · TV · 100 % · tâm 0 · dịch 0 · trái ↺ / phải ↻ ·
         * kênh chưa đo`). Đời xe không có hồ sơ đo lấy cái này ⇒ hành vi không đổi một pixel (CLAUDE.md §6).
         * Bài `CameraProfileDefaultsTest.NEUTRAL bang dung literal 2 75` ghim từng trường.
         */
        val NEUTRAL = CameraProfileDefaults()
    }
}
