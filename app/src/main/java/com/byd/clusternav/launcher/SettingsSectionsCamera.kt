package com.byd.clusternav.launcher

import android.content.Context
import android.widget.LinearLayout
import com.byd.clusternav.R
import com.byd.clusternav.launcher.camera.CameraDewarpPrefs
import com.byd.clusternav.launcher.camera.CameraSignalPolicy

/**
 * ═══ *Tiện nghi xe › Camera theo xi-nhan* — MỘT tầng, đúng 10 hàng người lái (2.77) ══════════════════════════════
 *
 * Tách khỏi [SettingsCarSection] ở 2.76. Danh sách hàng là hợp đồng THUẦN ở `:core` `CameraSettingsIa` — tệp này chỉ
 * **dựng hàng theo đúng danh sách ấy**, và bài canh so hai bên.
 *
 * ## Owner trên xe 27/09 (buổi closing)
 * *"không biết chỉnh đâu, nên chốt theo cái nào best là được, bỏ hết phần nâng cao đi, bỏ luôn nguồn vì chốt là toàn
 * cảnh khung ghép rồi"* · *"bỏ cái 1 cam ra, nhiều option quá rối cho người dùng, bỏ luôn ở phần kỹ thuật"*.
 *
 * ⇒ 2.76 có hai tầng (người lái + khối gập *"Nâng cao (kỹ thuật)"* sau cổng chế độ kiểm thử) và một hàng *Nguồn*.
 * 2.77 **gỡ cả khối gập lẫn hàng Nguồn**. Còn lại **đúng 10 hàng**: bật khi xi-nhan · hiện lên cụm · góc hiện
 * trái/phải · xoay trái/phải · lật gương trái/phải · hình khung · nắn hình bật-tắt. Đổi số ⇒ đổi
 * `CameraSettingsIa.USER_KEYS` + doc `camera-ia-profile.md` §IA + dòng CAM-F1 của runbook (owner **ĐẾM** hàng ấy).
 *
 * ## Mười lăm khoá kia đi đâu — ẨN UI, KHÔNG xoá khoá
 * `camera_render` · `camera_span` · dải · `camera_cam_*` · `camera_gl_texmatrix` · tám núm nắn: **không còn hàng**,
 * nhưng vẫn ghi/đọc được qua `prefs_set` của cầu kiểm thử (`CameraSettingsIa.NO_UI_KEYS`), và mặc định của chúng là
 * bộ owner đã DUYỆT trên xe, khai ở hồ sơ xe (`CameraDefaults` — Seal: `STRIP · GL · F 55 · K 100 · S 130`, dải
 * trái 1 / phải 2, cameraId 1). Pref owner đã đặt trên xe **vẫn thắng mặc định hồ sơ** — [ĐO B0 27/09: prefs giữ
 * nguyên qua nâng cấp] — nên gỡ UI không đổi một pixel nào trên xe của owner.
 *
 * Hai khoá **bị XOÁ hẳn** (không phải ẩn): `camera_source` và `camera_hal_mode` — lý do ĐO ở KDoc
 * `CameraSettingsIa`.
 *
 * ## Vì sao mặc định KHÔNG viết vào nhãn
 * Mặc định theo **hồ sơ xe**; một nhãn *"(mặc định)"* đúng cho xe này và sai cho xe kia ⇒ chip đang chọn đã tự nói.
 *
 * Nhóm này 100 % đi qua cầu (N2) — màn Cài đặt không chạm `Prefs.` trực tiếp.
 */
class SettingsCameraSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    private val bridge get() = deps.bridge

    /** Đúng một tầng từ 2.77 — không còn cổng, không còn khối gập. */
    fun build(body: LinearLayout) {
        cameraUser(body)
    }

    // ── TẦNG NGƯỜI LÁI — đúng thứ tự `CameraSettingsIa.USER_KEYS`, và là TOÀN BỘ màn ────────────────────────

    private fun cameraUser(body: LinearLayout) {
        body.addView(rows.subHeader(context.getString(R.string.kachi_sub_camera_signal)))
        body.addView(rows.checkRow(
            on = bridge.cameraSignal(),
            title = context.getString(R.string.kachi_camera_signal_title),
            sub = context.getString(R.string.kachi_camera_signal_sub),
        ) { on -> bridge.setCameraSignal(on) })
        body.addView(rows.checkRow(
            on = bridge.cameraOnCluster(),
            title = context.getString(R.string.kachi_camera_cluster_title),
            sub = context.getString(R.string.kachi_camera_cluster_sub),
        ) { on -> bridge.setCameraOnCluster(on) })
        // VỊ TRÍ GÓC từng bên (spec `camera-turn-signal-hal-socket.html` R3 · R4). Hai hàng RIÊNG vì owner chốt
        // "trái vẫn có thể hiện bên phải". Mã chip = đúng giá trị lưu bền ("TL"/"TR", hằng ở `:core`).
        val corners = listOf(
            CameraSignalPolicy.CORNER_TOP_LEFT to context.getString(R.string.kachi_pos_tl),
            CameraSignalPolicy.CORNER_TOP_RIGHT to context.getString(R.string.kachi_pos_tr),
        )
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_pos_sub)))
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_pos_left), corners, bridge.cameraPosLeft(),
        ) { v -> bridge.setCameraPos(left = true, v = v) })
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_pos_right), corners, bridge.cameraPosRight(),
        ) { v -> bridge.setCameraPos(left = false, v = v) })
        // XOAY video TỪNG BÊN (spec R7 · 2.71; owner 2026-09-26: "2 line setting độc lập"). Mặc định theo hồ sơ xe
        // (Seal 0/0 — research `research-side-camera-orientation-2026-09-27.md` §6.1), pref đã chọn trên xe thắng.
        val rotations = listOf(
            CameraSignalPolicy.ROTATE_NONE to context.getString(R.string.kachi_camera_rot_none),
            CameraSignalPolicy.ROTATE_LEFT to context.getString(R.string.kachi_camera_rot_left),
            CameraSignalPolicy.ROTATE_RIGHT to context.getString(R.string.kachi_camera_rot_right),
            CameraSignalPolicy.ROTATE_180 to context.getString(R.string.kachi_camera_rot_180),
        )
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_rot_sub)))
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_rot_row_left), rotations, bridge.cameraRotLeft(),
        ) { v -> bridge.setCameraRotation(left = true, v = v) })
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_rot_row_right), rotations, bridge.cameraRotRight(),
        ) { v -> bridge.setCameraRotation(left = false, v = v) })
        // LẬT GƯƠNG từng bên (2.76 L7, research §6.2): tay gương của ảnh HAL [CHƯA BIẾT] tới CAM-M1 ⇒ mặc định TẮT,
        // người lái bật khi ảnh ngược tay so với gương kính. Hai ô tích, cùng khuôn hai hàng xoay ở trên.
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_mirror_sub)))
        body.addView(rows.checkRow(
            on = bridge.cameraMirrorLeft(),
            title = context.getString(R.string.kachi_camera_mirror_row_left),
            sub = context.getString(R.string.kachi_camera_mirror_row_sub),
        ) { on -> bridge.setCameraMirror(left = true, v = on) })
        body.addView(rows.checkRow(
            on = bridge.cameraMirrorRight(),
            title = context.getString(R.string.kachi_camera_mirror_row_right),
            sub = context.getString(R.string.kachi_camera_mirror_row_sub),
        ) { on -> bridge.setCameraMirror(left = false, v = on) })
        // HÌNH KHUNG — chip SINH từ `:core` [CameraSignalPolicy.SHAPES] ⇒ mã `CLUSTER` ("theo cụm", làn L2) tự có chip
        // mà tệp này không phải sửa; nhãn tra theo mã, mã lạ hiện chính mã (không ném, không im).
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_shape_sub)))
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_shape_row),
            CameraSignalPolicy.SHAPES.map { it to shapeLabel(it) },
            bridge.cameraShape(),
        ) { v -> bridge.setCameraShape(v) })
        // NẮN HÌNH — một ô tích: `camera_dewarp_amount` AMOUNT_MAX (nắn đủ) / AMOUNT_MIN (ảnh thô). Tám núm tinh
        // chỉnh KHÔNG còn hàng nào (2.77): bộ số của Seal đã là mặc định hồ sơ và owner đã duyệt bằng mắt trên xe,
        // nên người lái chỉ còn quyết "có nắn hay không"; muốn dò lại thì qua `prefs_set` của cầu kiểm thử.
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_dewarp_on_sub_header)))
        body.addView(rows.checkRow(
            on = bridge.cameraDewarpAmount() > CameraDewarpPrefs.AMOUNT_MIN,
            title = context.getString(R.string.kachi_camera_dewarp_on_title),
            sub = context.getString(R.string.kachi_camera_dewarp_on_sub),
        ) { on -> bridge.setCameraDewarpAmount(if (on) CameraDewarpPrefs.AMOUNT_MAX else CameraDewarpPrefs.AMOUNT_MIN) })
    }

    /** Nhãn chip hình khung theo mã `:core`; mã chưa có chữ ⇒ hiện mã (lộ ra để sửa, không im lặng bỏ chip). */
    private fun shapeLabel(code: String): String = when (code) {
        CameraSignalPolicy.SHAPE_RECT -> context.getString(R.string.kachi_camera_shape_rect)
        CameraSignalPolicy.SHAPE_ROUND -> context.getString(R.string.kachi_camera_shape_round)
        CameraSignalPolicy.SHAPE_CLUSTER -> context.getString(R.string.kachi_camera_shape_cluster)
        else -> code
    }
}
