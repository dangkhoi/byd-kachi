package com.byd.clusternav

import android.content.Context
import com.byd.clusternav.launcher.camera.CameraGuide
import com.byd.clusternav.launcher.camera.CameraPanoCrop
import com.byd.clusternav.launcher.camera.CameraSignalPolicy

/**
 * ═══ Khoá của hai AUTOMATION — tách khỏi [Prefs] theo VAI (1.85, trần 500 dòng) ═══════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R1 · R2 · R5. Cùng cách [PrefsVoiceV3] / `PrefsInputd` tách nhóm: hàm
 * mở rộng của [Prefs], **cùng tệp `clusternav_prefs`** (mở tệp thứ hai là dựng cửa thứ hai vào cùng chỗ lưu —
 * thứ [com.byd.clusternav.launcher.SettingsCatalog.PREFS_FILES] sinh ra để bắt).
 *
 * ## Cả ba khoá theo XE (`ProfileScope.DEVICE_KEYS`)
 * Automation là việc của **chiếc xe** (*"khi mưa thì sấy kính"*, *"7–9h thứ Hai thì dẫn đến công ty"*), không
 * phải sở thích đi theo người lái — cùng họ `cast_enabled` / `voice_wake_enabled`. Hệ quả **phải biết**: sổ địa
 * chỉ thì theo **hồ sơ** (`<hồ sơ>__saved_places`), nên một luật trỏ tới mục không có trong hồ sơ đang dùng sẽ
 * **bỏ lượt** (`ScheduledNavApplier.launch` ghi log rồi thôi) — degrade an toàn, không nổ, và không đóng dấu
 * đã-dẫn nên đổi lại hồ sơ trong khung giờ thì lượt đi vẫn còn.
 */

/**
 * `internal` (không `private`) từ 2.74: [PrefsCameraDewarp] dùng lại đúng hàm này.
 *
 * Mở một accessor thứ hai ở tệp kia sẽ chép **tên tệp prefs** lần thứ hai — đúng "cửa thứ hai vào cùng chỗ lưu" mà
 * KDoc trên cảnh báo, và bản chép ấy sẽ lệch vào đúng lần ai đó đổi tên tệp. Một hàm, một literal.
 */
internal fun autoPrefs(ctx: Context) =
    ctx.applicationContext.getSharedPreferences("clusternav_prefs", Context.MODE_PRIVATE)

// ── AUTOMATION #1 · Tự sấy kính khi mưa (R1) ──────────────────────────────────────────────────────
// MẶC ĐỊNH TẮT — cài mới KHÔNG đọc cảm biến, KHÔNG đụng nút sấy tới khi owner tự bật. BẬT ⇒
// `AutomationService` đọc `SETTING_FRONT_RAIN_WIPER_SPEED` ([ĐO xe 2026-09-20]: 1 khô / ≥2 mưa) mỗi ~5 phút và
// bật/tắt sấy TRƯỚC+SAU theo `RainDefrostPolicy`.
// ⚠ Ký ức R1.5 (*"sấy này của tôi"* / *"người lái vừa tự tắt"*) là cờ RAM trong `RainDefrostApplier`, KHÔNG ở
// đây — lý do đầy đủ ở KDoc `RainDefrostState` (nổ máy lại thì quên là hướng sai AN TOÀN).
private const val K_RAIN_DEFROST = "rain_defrost_enabled"

/** AUTOMATION #1 — "Tự sấy kính khi mưa". Mặc định **false**. */
fun Prefs.rainDefrostEnabled(ctx: Context): Boolean = autoPrefs(ctx).getBoolean(K_RAIN_DEFROST, false)

/** Xem [rainDefrostEnabled]. Chỗ gọi phải `AutomationService.sync` sau khi ghi (xem `ClusterNavBridge`). */
fun Prefs.setRainDefrostEnabled(ctx: Context, v: Boolean) =
    autoPrefs(ctx).edit().putBoolean(K_RAIN_DEFROST, v).apply()

// ── V7 (owner 2026-09-25) — CHỌN kính nào được sấy: trước · sau+gương · cả hai ────────────────────
// Owner: *"tách 2 option riêng, user chọn cả 2 hoặc 1 trong 2"*. Hai khoá con, KHÔNG phải một khoá 3 giá trị
// ("front"/"rear"/"both"): ba-giá-trị-trong-một-chuỗi là chỗ sinh ra trạng thái thứ tư không ai định nghĩa khi
// prefs bị sửa tay (`prefs_set` trên xe), và nó cũng không nói được ca "cả hai TẮT".
//
// ⚠ Quan hệ với [K_RAIN_DEFROST]: đó là công tắc CHÍNH (bật/tắt tính năng); hai khoá này chỉ có nghĩa khi chính
// đang bật. Cả hai TẮT ⇒ `RainDefrostApplier` coi như tính năng tắt (không đọc cảm biến, không ghi nút nào) — xem
// KDoc `RainDefrostApplier.selection`.
//
// MẶC ĐỊNH CẢ HAI BẬT = giữ NGUYÊN hành vi của bản trước (1.85 ghi cả hai nút, R1.3): người đã bật tính năng rồi
// nâng cấp lên bản này không được thấy nó lặng lẽ làm ít hơn hôm qua.
private const val K_RAIN_DEFROST_FRONT = "rain_defrost_front"
private const val K_RAIN_DEFROST_REAR = "rain_defrost_rear"

/** V7 — mưa thì bật sấy kính TRƯỚC. Mặc định **true** (hành vi 1.85). */
fun Prefs.rainDefrostFront(ctx: Context): Boolean = autoPrefs(ctx).getBoolean(K_RAIN_DEFROST_FRONT, true)

/** Xem [rainDefrostFront]. Chỗ gọi phải `AutomationService.sync` sau khi ghi (xem `ClusterNavBridge`). */
fun Prefs.setRainDefrostFront(ctx: Context, v: Boolean) =
    autoPrefs(ctx).edit().putBoolean(K_RAIN_DEFROST_FRONT, v).apply()

/** V7 — mưa thì bật sấy kính SAU + gương chiếu hậu (`defrost_rear`). Mặc định **true** (hành vi 1.85). */
fun Prefs.rainDefrostRear(ctx: Context): Boolean = autoPrefs(ctx).getBoolean(K_RAIN_DEFROST_REAR, true)

/** Xem [rainDefrostRear]. */
fun Prefs.setRainDefrostRear(ctx: Context, v: Boolean) =
    autoPrefs(ctx).edit().putBoolean(K_RAIN_DEFROST_REAR, v).apply()

// ── V8 (owner 2026-09-25) — TỰ CẬP NHẬT khi mở app ───────────────────────────────────────────────
// Owner: *"tách auto-update thành 1 toggle riêng ở Hệ thống, KHÔNG gắn với Nav+HUD"*. Trước V8 lượt dò bản mới
// chỉ đi kèm đường Nav+HUD / nút bấm tay, tức ai tắt dẫn đường thì không bao giờ được cập nhật mà không có gì
// nói ra điều đó.
//
// MẶC ĐỊNH TẮT: nó mở một kết nối HTTPS ra GitHub mỗi lần mở launcher và có thể dựng hộp thoại *"cài bản mới?"*
// trước mặt người đang lái. Một tính năng tự-tải-về-rồi-cài-đè phải do chủ xe bật tường minh — cùng lẽ
// `rain_defrost_enabled` / `voice_wake_enabled` mặc định TẮT.
private const val K_AUTO_UPDATE = "auto_update_enabled"

/** V8 — "Tự động cập nhật": mở launcher thì tự dò bản mới trong `apk/`. Mặc định **false**. */
fun Prefs.autoUpdateEnabled(ctx: Context): Boolean = autoPrefs(ctx).getBoolean(K_AUTO_UPDATE, false)

/** Xem [autoUpdateEnabled]. Không có tác dụng phụ nào phải đồng bộ: lượt dò đọc khoá này mỗi lần màn chính lên. */
fun Prefs.setAutoUpdateEnabled(ctx: Context, v: Boolean) =
    autoPrefs(ctx).edit().putBoolean(K_AUTO_UPDATE, v).apply()

// ── CAMERA theo xi-nhan (owner 2026-09-22) — mặc định TẮT ("đang phát triển") ────────────────────
private const val K_CAMERA_SIGNAL = "camera_signal_enabled"

/** Camera theo xi-nhan (xi-nhan → mở camera bên đó, overlay). Mặc định **false** (đang phát triển). */
fun Prefs.cameraSignalEnabled(ctx: Context): Boolean = autoPrefs(ctx).getBoolean(K_CAMERA_SIGNAL, false)
fun Prefs.setCameraSignalEnabled(ctx: Context, v: Boolean) =
    autoPrefs(ctx).edit().putBoolean(K_CAMERA_SIGNAL, v).apply()

private const val K_CAMERA_ON_CLUSTER = "camera_on_cluster"
/** Hiện overlay camera lên MÀN CỤM thay màn chính (owner 2026-09-24). Mặc định false = màn chính. */
fun Prefs.cameraOnCluster(ctx: Context): Boolean = autoPrefs(ctx).getBoolean(K_CAMERA_ON_CLUSTER, false)
fun Prefs.setCameraOnCluster(ctx: Context, v: Boolean) =
    autoPrefs(ctx).edit().putBoolean(K_CAMERA_ON_CLUSTER, v).apply()

// Phương án LVDS/hiển thị camera để thử NHANH trên xe không cần rebuild (findings 2026-09-23, runbook A–J).
// Chuỗi 1 ký tự: "A"(mặc định) · "B"(zOrderMediaOverlay) · "C"(setLVDS trước) · "D"(FULL_SCREEN) · "G"(cụm) ·
// "H"(chờ workState ON). Chỉnh qua prefs_set khi test 10 option, chốt được rồi đặt mặc định.
// ⚠ GỠ HẲN 2026-09-25 (spec `camera-turn-signal-hal-socket.html` R6): mười option A–J là **thử nghiệm LVDS**, và
// [ĐO xe 2026-09-25] đường có HÌNH là AVMCamera đổ frame vào Surface, không phải LVDS thụ động ⇒ cả họ option
// mất lý do tồn tại. Giữ lại một pref chết thì nó sẽ còn được `prefs_set` ghi trên xe và không ai đọc — tệ hơn
// là không có. Chỗ trống này nay là VỊ TRÍ GÓC (dưới đây), thứ owner thật sự cần chỉnh trên xe.

// ── Vị trí GÓC của overlay camera, RIÊNG từng bên xi-nhan (spec R3 · R4) ─────────────────────────
// Hai khoá vì đây là hai lựa chọn ĐỘC LẬP: owner chốt *"trái vẫn có thể hiện bên phải"* (R4) — người lái ngồi
// bên trái nên góc trên-trái có thể bị vành lái/cột A che ở một số cách ngồi. Một khoá dùng chung sẽ buộc hai
// bên đối xứng, tức làm mất đúng thứ yêu cầu xin.
// Mặc định = [CameraSignalPolicy.defaultCorner] (trái→TL, phải→TR) — hằng ở `:core`, KHÔNG chép số vào đây.
private fun cameraGuideKey(left: Boolean) = if (left) "camera_guide_left" else "camera_guide_right"

/**
 * VẠCH CHUẨN khoảng cách cho bên [left] — `OFF` hoặc nấc `1`–`9`. Xem [CameraGuide] (`:core`) cho lý lẽ đầy đủ.
 *
 * Giá trị lạ trên đĩa ⇒ `OFF`, tức **không vẽ gì**. Cố ý không đoán: một vạch nằm sai chỗ còn tệ hơn không có
 * vạch, vì người lái sẽ tin nó khi lùi sát xe bên cạnh.
 */
fun Prefs.cameraGuide(ctx: Context, left: Boolean): String {
    val raw = autoPrefs(ctx).getString(cameraGuideKey(left), null)
    return if (CameraGuide.isValue(raw)) raw!!.trim().uppercase() else CameraGuide.defaultValue()
}

/** Xem [cameraGuide]. */
fun Prefs.setCameraGuide(ctx: Context, left: Boolean, v: String) =
    autoPrefs(ctx).edit().putString(cameraGuideKey(left), v).apply()

private fun cameraPanoKey(left: Boolean) = if (left) "camera_pano_left" else "camera_pano_right"

/**
 * NGUỒN của bên [left] có phải ẢNH GHÉP 4-trong-1 không, và cắt dải nào — xem
 * [CameraPanoCrop.panoStripFor]. Giá trị: `AUTO` (theo bên: trái dải 1, phải dải 2) · `NONE` (nguyên khung) ·
 * `0`–`3` (ép một dải). Giá trị lạ trên đĩa ⇒ `AUTO`.
 */
fun Prefs.cameraPano(ctx: Context, left: Boolean): String {
    val raw = autoPrefs(ctx).getString(cameraPanoKey(left), null)
    return if (CameraPanoCrop.isPanoMode(raw)) raw!!.trim().uppercase() else CameraPanoCrop.PANO_AUTO
}

/** Xem [cameraPano]. */
fun Prefs.setCameraPano(ctx: Context, left: Boolean, v: String) =
    autoPrefs(ctx).edit().putString(cameraPanoKey(left), v).apply()

private fun cameraViewKey(left: Boolean) = if (left) "camera_view_left" else "camera_view_right"

/**
 * GÓC NHÌN camera cho bên xi-nhan [left] — tên một [CameraSignalPolicy.CamView].
 *
 * Mỗi góc mang hai số: bảo HAL xuất hình nào, và mở camera nào. Cặp đúng KHÁC NHAU theo đời xe ([ĐO ảnh owner]
 * fisheye 4-in-1 là id 1 trên Seal, id 0 trên SL6) và chưa đo đủ để đặt cứng vào hồ sơ xe, nên người lái tự dò
 * — owner 2026-09-28 trên SL6: *"không mở được cam phải (cam trái ok)… cho chọn lại cam trong setting"*.
 *
 * Chưa chọn, hoặc tên lạ trên đĩa (bản trước / `prefs_set` gõ tay) ⇒ [CameraSignalPolicy.defaultView] như cũ.
 * KHÔNG trả nguyên văn tên lạ: tầng mở camera sẽ không tìm thấy góc đó và im lặng không hiện gì — đúng cái
 * triệu chứng đang phải chữa.
 */
fun Prefs.cameraView(ctx: Context, left: Boolean): String {
    val fallback = CameraSignalPolicy.defaultView(
        if (left) CameraSignalPolicy.Turn.LEFT else CameraSignalPolicy.Turn.RIGHT,
    )?.name.orEmpty()
    val raw = autoPrefs(ctx).getString(cameraViewKey(left), null)
    return if (CameraSignalPolicy.isView(raw)) raw!!.trim().uppercase() else fallback
}

/** Xem [cameraView]. Tên lạ ghi được nhưng lượt đọc bỏ qua (cùng lẽ với [setCameraPos]). */
fun Prefs.setCameraView(ctx: Context, left: Boolean, v: String) =
    autoPrefs(ctx).edit().putString(cameraViewKey(left), v).apply()

private fun cameraPosKey(left: Boolean) = if (left) "camera_pos_left" else "camera_pos_right"

/**
 * Góc hiện overlay camera cho bên xi-nhan [left] — `"TL"` (trên-trái) hoặc `"TR"` (trên-phải).
 *
 * Giá trị lạ trên đĩa (prefs sửa tay qua `prefs_set`, hoặc dữ liệu của bản trước) ⇒ trả về mặc định thay vì trả
 * nguyên văn: tầng vẽ chỉ biết hai góc, nên một chuỗi thứ ba đi tới đó sẽ thành *"rơi vào nhánh else"* — tức
 * overlay lặng lẽ nằm sai góc mà không ai biết vì sao.
 */
fun Prefs.cameraPos(ctx: Context, left: Boolean): String {
    val fallback = CameraSignalPolicy.defaultCorner(left)
    val raw = autoPrefs(ctx).getString(cameraPosKey(left), fallback) ?: fallback
    return if (CameraSignalPolicy.isCorner(raw)) raw else fallback
}

/** Xem [cameraPos]. Nhận `"TL"`/`"TR"`; chuỗi khác ghi được nhưng lượt đọc sẽ bỏ qua (xem KDoc trên). */
fun Prefs.setCameraPos(ctx: Context, left: Boolean, v: String) =
    autoPrefs(ctx).edit().putString(cameraPosKey(left), v).apply()

// ── XOAY video overlay, RIÊNG từng bên xi-nhan (spec R7 · 2.71) ──────────────────────────────────
// Hai khoá như `camera_pos_*`. 2.67 dùng MỘT khoá `camera_rotation` với chế độ "theo bên" (SIDE/SIDEINV) mã hoá
// sự khác nhau trái/phải; owner trên xe 2026-09-26 bác: *"xoay video cần làm 2 line setting độc lập cho camera
// trái và phải, có thể 2 camera cần xoay khác nhau"* — hai cam gương là hai thiết bị, chiều ghép vào ảnh 4-in-1
// không có luật chung nào để một chế độ diễn tả (RE kinex cũng giữ hai số độc lập). Device-scope (`autoPrefs`):
// chiều ghép ảnh là chuyện của XE, không của hồ sơ tài xế.
private fun cameraRotKey(left: Boolean) = if (left) "camera_rot_left" else "camera_rot_right"

/** Khoá đơn CŨ (2.67–2.70). Chỉ còn để [migrateLegacyCameraRotation] đọc một lần rồi xoá — không ai ghi nữa. */
private const val K_CAMERA_ROTATION_LEGACY = "camera_rotation"

/**
 * Nâng cấp từ 2.67–2.70: khoá cũ còn trên đĩa ⇒ điền khoá mới của BÊN nào chưa có (qua
 * [CameraSignalPolicy.migrateRotation], thuần, đã test 6×2) rồi xoá khoá cũ. Chạy đúng một lần vì lượt sau
 * `getString(legacy)` đã là null. Bên nào đã có khoá mới (owner vừa chọn chip trước khi bên kia được đọc) thì giữ,
 * không ghi đè — thứ owner vừa chọn luôn thắng dữ liệu di cư.
 */
private fun migrateLegacyCameraRotation(p: android.content.SharedPreferences) {
    val old = p.getString(K_CAMERA_ROTATION_LEGACY, null) ?: return
    val e = p.edit()
    listOf(true, false).forEach { side ->
        if (!p.contains(cameraRotKey(side))) e.putString(cameraRotKey(side), CameraSignalPolicy.migrateRotation(old, side))
    }
    e.remove(K_CAMERA_ROTATION_LEGACY).apply()
}

/**
 * Góc xoay video camera cho bên xi-nhan [left] — một trong [CameraSignalPolicy.ROTATIONS] (`"0"`/`"L90"`/`"R90"`/`"180"`).
 *
 * Giá trị lạ trên đĩa ⇒ [CameraSignalPolicy.defaultRotation] của bên đó, cùng khuôn [cameraPos]: tầng vẽ nhận SỐ
 * ĐỘ đã tính ([CameraSignalPolicy.rotationDegrees]) nên một chuỗi lạ đi tới đó không có nhánh nào để rơi vào.
 */
fun Prefs.cameraRotation(ctx: Context, left: Boolean): String {
    val p = autoPrefs(ctx)
    migrateLegacyCameraRotation(p)
    // 2.76 · R2: mặc định theo HỒ SƠ XE (Seal DL3 = `0` cả hai bên, xe chưa đo = `CameraSignalPolicy.defaultRotation`);
    // khoá ĐÃ đặt trên xe (owner chọn ↺90 27/09) thắng — `getString(key, fallback)` chỉ dùng fallback khi khoá vắng.
    val fallback = com.byd.clusternav.launcher.camera.CameraDefaults.of(ctx).rotation(left)
    val raw = p.getString(cameraRotKey(left), fallback) ?: fallback
    return if (CameraSignalPolicy.isRotation(raw)) raw else fallback
}

/** Xem [cameraRotation]. Nhận mã trong [CameraSignalPolicy.ROTATIONS]; chuỗi khác ghi được nhưng lượt đọc bỏ qua. */
fun Prefs.setCameraRotation(ctx: Context, left: Boolean, v: String) =
    autoPrefs(ctx).edit().putString(cameraRotKey(left), v).apply()

// ── LẬT GƯƠNG video TỪNG BÊN (2.76 L7 · research `research-side-camera-orientation-2026-09-27.md` §6.2) ──────────
// Tay gương của ảnh HAL **[CHƯA BIẾT]** (CAM-M1 chưa đo trên xe) ⇒ mặc định TẮT — không đoán. Hai khoá theo bên, cùng
// lẽ `camera_rot_*` (hai cam gương là hai thiết bị, chiều ghép vào ảnh 4-in-1 có thể khác nhau). Device-scope (`autoPrefs`).
private fun cameraMirrorKey(left: Boolean) = if (left) "camera_mirror_left" else "camera_mirror_right"

/**
 * Lật ngang video của bên xi-nhan [left] hay không. Áp ở KHÔNG GIAN NGUỒN, trước xoay — đường GL qua `flipH` của
 * `CameraGlUniforms.of`, đường TV qua `CameraOverlayTransform.matrix(mirror)`; cùng một phép cho cả hai đường.
 */
fun Prefs.cameraMirror(ctx: Context, left: Boolean): Boolean = autoPrefs(ctx).getBoolean(cameraMirrorKey(left), false)

/** Xem [cameraMirror]. */
fun Prefs.setCameraMirror(ctx: Context, left: Boolean, v: Boolean) =
    autoPrefs(ctx).edit().putBoolean(cameraMirrorKey(left), v).apply()

// ── ĐƯỜNG KẾT XUẤT khung hình camera (CLOSE-14 · CAM-LAG) ───────────────────────────────────────
// MỘT khoá cho cả hai bên: đây là câu hỏi về **cách vẽ** (TextureView trong cây view vs SurfaceView layer riêng),
// không phải về cam nào/chiều nào — hai bên không có lý do nào để vẽ khác nhau. Device-scope (`autoPrefs`) vì nó
// là tính chất của ROM/màn, không của hồ sơ tài xế. Mặc định = thứ đang chạy hiện trường (CLAUDE.md §6).
private const val K_CAMERA_RENDER = "camera_render"

/**
 * Đường kết xuất overlay camera — một mã trong [CameraSignalPolicy.RENDERS] (`"TV"` = TextureView, `"SV"` =
 * SurfaceView). Giá trị lạ trên đĩa ⇒ [CameraSignalPolicy.defaultRender], cùng khuôn [cameraRotation].
 */
fun Prefs.cameraRender(ctx: Context): String {
    // 2.76 · R2: mặc định theo HỒ SƠ XE (Seal DL3 = GL; xe chưa đo = `CameraSignalPolicy.defaultRender()` = TV).
    val fallback = com.byd.clusternav.launcher.camera.CameraDefaults.of(ctx).render
    val raw = autoPrefs(ctx).getString(K_CAMERA_RENDER, fallback) ?: fallback
    return if (CameraSignalPolicy.isRender(raw)) raw else fallback
}

/** Xem [cameraRender]. Nhận mã trong [CameraSignalPolicy.RENDERS]; chuỗi khác ghi được nhưng lượt đọc bỏ qua. */
fun Prefs.setCameraRender(ctx: Context, v: String) =
    autoPrefs(ctx).edit().putString(K_CAMERA_RENDER, v).apply()

// ── VÙNG GƯƠNG trong ảnh pano: bề rộng · dải · hình khung · kênh HAL (R8-A · 2.74) ───────────────
// RE `docs/diagnostics/electro-camera-RE-2026-09-26.md` §5 K10: crop của 2.73 chỉ rộng 0.10 = **40 % một dải** ở rìa
// vòng fisheye ⇒ méo như ống. §6.1 (phương án A) nới crop, và vì **dải nào là hướng nào vẫn [CHƯA BIẾT]** (§7 Q1/Q2)
// thì chỉ số dải phải là pref owner dò được trên xe, không phải hằng. Cả năm khoá device-scope (`autoPrefs`): cách
// HAL ghép ảnh 4-in-1 là chuyện của XE, không của hồ sơ tài xế — cùng lẽ `camera_rot_*`/`camera_render`.
// Mặc định của CẢ NĂM = hành vi 2.73 từng pixel (CLAUDE.md §6); hình học ở `:core` [CameraPanoCrop].
private const val K_CAMERA_SPAN = "camera_span"
private const val K_CAMERA_SHAPE = "camera_shape"
private const val K_CAMERA_CIRCLE_PCT = "camera_circle_scale"
private fun cameraStripKey(left: Boolean) = if (left) "camera_strip_left" else "camera_strip_right"

/**
 * Bề rộng vùng gương — mã trong [CameraSignalPolicy.SPANS] (`"NARROW"` = vệt 0.10 của 2.73, `"STRIP"` = trọn dải 0.25).
 *
 * Giá trị lạ trên đĩa ⇒ [CameraSignalPolicy.defaultSpan], cùng khuôn [cameraRender].
 */
fun Prefs.cameraSpan(ctx: Context): String {
    // 2.76 · R2: mặc định theo HỒ SƠ XE (Seal DL3 = STRIP; xe chưa đo = `CameraSignalPolicy.defaultSpan()` = NARROW).
    val fallback = com.byd.clusternav.launcher.camera.CameraDefaults.of(ctx).span
    val raw = autoPrefs(ctx).getString(K_CAMERA_SPAN, fallback) ?: fallback
    return if (CameraSignalPolicy.isSpan(raw)) raw else fallback
}

/** Xem [cameraSpan]. Nhận mã trong [CameraSignalPolicy.SPANS]; chuỗi khác ghi được nhưng lượt đọc bỏ qua. */
fun Prefs.setCameraSpan(ctx: Context, v: String) =
    autoPrefs(ctx).edit().putString(K_CAMERA_SPAN, v).apply()

/**
 * Hình cửa sổ camera — mã trong [CameraSignalPolicy.SHAPES] (`"RECT"` = chữ nhật bo góc của 2.73, `"ROUND"` = vòng
 * tròn hiện trọn vòng ảnh fisheye như app Electro, **không** nắn méo).
 */
fun Prefs.cameraShape(ctx: Context): String {
    val fallback = CameraSignalPolicy.defaultShape()
    val raw = autoPrefs(ctx).getString(K_CAMERA_SHAPE, fallback) ?: fallback
    return if (CameraSignalPolicy.isShape(raw)) raw else fallback
}

/** Xem [cameraShape]. */
fun Prefs.setCameraShape(ctx: Context, v: String) =
    autoPrefs(ctx).edit().putString(K_CAMERA_SHAPE, v).apply()

/**
 * Chỉ số DẢI pano (0..3) cho bên xi-nhan [left] — mặc định [CameraPanoCrop.defaultStrip] (trái 1 / phải 2 = hai dải
 * mà vệt của 2.73 đang nằm trong).
 *
 * Ngoài dải ⇒ mặc định: dải thứ năm không tồn tại, và một chỉ số lạ đi tới tầng hình học sẽ cho một rect ngoài ảnh.
 */
fun Prefs.cameraStrip(ctx: Context, left: Boolean): Int {
    val fallback = CameraPanoCrop.defaultStrip(left)
    val raw = autoPrefs(ctx).getInt(cameraStripKey(left), fallback)
    return if (CameraPanoCrop.isStrip(raw)) raw else fallback
}

/** Xem [cameraStrip]. */
fun Prefs.setCameraStrip(ctx: Context, left: Boolean, v: Int) =
    autoPrefs(ctx).edit().putInt(cameraStripKey(left), v).apply()

/**
 * Phần trăm cạnh ô vuông của hình TRÒN so với chiều cao dải — núm chữa cái [ĐOÁN] *"đường kính vòng ảnh = chiều cao
 * dải"* ngay trên xe. Ngoài `[CIRCLE_PCT_MIN, CIRCLE_PCT_MAX]` ⇒ [CameraSignalPolicy.CIRCLE_PCT_DEFAULT].
 */
fun Prefs.cameraCirclePct(ctx: Context): Int {
    val fallback = CameraSignalPolicy.CIRCLE_PCT_DEFAULT
    val raw = autoPrefs(ctx).getInt(K_CAMERA_CIRCLE_PCT, fallback)
    return if (CameraSignalPolicy.isCirclePct(raw)) raw else fallback
}

/** Xem [cameraCirclePct]. */
fun Prefs.setCameraCirclePct(ctx: Context, v: Int) =
    autoPrefs(ctx).edit().putInt(K_CAMERA_CIRCLE_PCT, v).apply()

// cameraId AVMCamera trái/phải — đổi trên xe để tìm đúng cam (chưa chắc map). Mặc định = [default] (CamView.cameraId).
fun Prefs.cameraCamId(ctx: Context, left: Boolean, default: Int): Int {
    val k = if (left) "camera_cam_left" else "camera_cam_right"
    return autoPrefs(ctx).getInt(k, default)
}
fun Prefs.setCameraCamId(ctx: Context, left: Boolean, v: Int) =
    autoPrefs(ctx).edit().putInt(if (left) "camera_cam_left" else "camera_cam_right", v).apply()


// ── AUTOMATION #2 · Tự dẫn đường theo lịch (R2) ───────────────────────────────────────────────────
// Hai khoá, hai VAI khác nhau — cố ý KHÔNG gộp:
//  • `nav_automation_rules` = CẤU HÌNH (sổ luật người dùng đặt trong Cài đặt › Dẫn đường), mã hoá bởi
//    `NavAutomationBook` (`:core`); rỗng = chưa có luật nào ⇒ engine không làm gì.
//  • `nav_automation_fired` = TRẠNG THÁI CHẠY (`id luật` → ngày đã dẫn, `NavAutomationFired`), thứ thi hành luật
//    "1 lần / khung / ngày" (R2.4). Nó KHÔNG phải cấu hình ⇒ khai ở `SettingsCatalog.NOT_SETTINGS`.
// Gộp hai vai vào một khoá thì một lượt SỬA luật sẽ xoá sạch dấu đã-dẫn: sửa giờ lúc 8h05 ⇒ dẫn lại ngay lần thứ
// hai, ngay trước mặt người đang lái.
private const val K_NAV_AUTOMATION = "nav_automation_rules"
private const val K_NAV_AUTOMATION_FIRED = "nav_automation_fired"

/** AUTOMATION #2 — sổ luật, dạng chuỗi của `NavAutomationBook.encode`. Rỗng = chưa có luật nào. */
fun Prefs.navAutomationRules(ctx: Context): String =
    autoPrefs(ctx).getString(K_NAV_AUTOMATION, "").orEmpty()

/** Xem [navAutomationRules]. Nhận chuỗi ĐÃ mã hoá — phép thêm/sửa/xoá là hàm thuần ở `:core`. */
fun Prefs.setNavAutomationRules(ctx: Context, encoded: String) =
    autoPrefs(ctx).edit().putString(K_NAV_AUTOMATION, encoded).apply()

/** Sổ ĐÃ-DẪN (`id=ngày`), dạng chuỗi của `NavAutomationFired.encode`. Rỗng = chưa dẫn lần nào. */
fun Prefs.navAutomationFired(ctx: Context): String =
    autoPrefs(ctx).getString(K_NAV_AUTOMATION_FIRED, "").orEmpty()

/** Xem [navAutomationFired]. */
fun Prefs.setNavAutomationFired(ctx: Context, encoded: String) =
    autoPrefs(ctx).edit().putString(K_NAV_AUTOMATION_FIRED, encoded).apply()
