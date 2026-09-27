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
 * bộ Seal (2.77: bộ ấy không còn bản đồ kênh nào để nhận). Tức *"xe chưa đo"* ở đây = *"hồ sơ chưa khai
 * `camera`"* (DL5 · generic · id lạ), **không** = *"chiếc xe chưa ai đo"*. Nợ ấy ghi ở `camera-ia-profile.md` §6 (§3 Q7): cần một seed riêng khi có số đo của đời thứ hai.
 *
 * ## Vì sao [NEUTRAL] KHÔNG phải bộ Seal
 * Bộ Seal (`STRIP · GL · F 55 % · S 130 %`) đúng cho **ống kính + cách HAL ghép ảnh** của Seal. Một đời xe khác ghép
 * ảnh khác chiều, GPU khác trần texture (RE §7 Q13) — đẩy bộ ấy sang xe chưa đo là đúng cái sai *"đoán rồi ship"*
 * mà CLAUDE.md §2/§3 cấm. Xe chưa đo đi đường 2.75 (`NARROW · TV · 100 %`).
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
 *
 * ⚠ 2.77: hai trường `channelLeft`/`channelRight` (bản đồ kênh HAL của nguồn *Một camera*) đã **gỡ** cùng cả
 * nguồn ấy — owner chốt trên xe 27/09 sau phép ĐO *"một kênh KHÔNG nét hơn"* (xem §NGUỒN của
 * [CameraSignalPolicy]). Số đo của Seal (trái 2 · phải 3; 1 = sau · 4 = trước) giữ ở `camera-ia-profile.md`
 * §7 làm **kiến thức**, không còn một trường nào đọc nó.
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
) {

    /** Góc xoay mặc định của bên [left]. */
    fun rotation(left: Boolean): String = if (left) rotLeft else rotRight

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
    )

    companion object {
        /**
         * **Trung tính** = đúng từng literal của 2.75 (`NARROW · TV · 100 % · tâm 0 · dịch 0 · trái ↺ / phải ↻`).
         * Đời xe không có hồ sơ đo lấy cái này ⇒ hành vi không đổi một pixel (CLAUDE.md §6).
         * Bài `CameraProfileDefaultsTest.NEUTRAL bang dung literal 2 75` ghim từng trường.
         */
        val NEUTRAL = CameraProfileDefaults()
    }
}
