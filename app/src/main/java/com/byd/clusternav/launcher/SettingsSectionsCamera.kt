package com.byd.clusternav.launcher

import android.content.Context
import android.widget.LinearLayout
import com.byd.clusternav.R
import com.byd.clusternav.launcher.camera.CameraDewarpPrefs
import com.byd.clusternav.launcher.camera.CameraPanoCrop
import com.byd.clusternav.launcher.camera.CameraSettingsIa
import com.byd.clusternav.launcher.camera.CameraSignalPolicy
import com.byd.clusternav.launcher.testbridge.TestBridgeStore

/**
 * ═══ *Tiện nghi xe › Camera theo xi-nhan* — HAI TẦNG (2.76 · spec `kachi-276-closing.html` R1) ══════════════════
 *
 * Tách khỏi [SettingsCarSection] (tệp ấy 480/500 dòng, và camera đã là nửa tệp). Hợp đồng hai tầng nằm THUẦN ở
 * `:core` [CameraSettingsIa] — tệp này chỉ **dựng hàng theo đúng danh sách ấy**, và bài canh so hai bên.
 *
 * ## Owner 27/09 (trên xe): *"có quá nhiều setting dùng cho việc test ở màn setting camera, cần bỏ ra… chỉ để lại
 * setting mà user cần, đi theo xe, hoặc hiển thị"*
 *  • [cameraUser] — **8 nhóm · 11 hàng** người lái quyết (bật · cụm · góc ×2 · xoay ×2 · **lật gương ×2** · hình ·
 *    nắn bật/tắt · nguồn). Luôn hiện. Đổi số ⇒ đổi `CameraSettingsIa.USER_KEYS` + doc `camera-ia-profile.md` §IA
 *    + dòng CAM-F1 của runbook (owner ĐẾM hàng ấy trên xe).
 *  • [cameraTech] — mọi móc đo (kết xuất, bề rộng, dải, kênh HAL, cameraId, ma trận, tám núm nắn) trong MỘT khối gập
 *    *"Nâng cao (kỹ thuật)"*, và khối ấy **chỉ được dựng khi chế độ kiểm thử đang mở** — [TestBridgeStore.isOn],
 *    đúng cổng 60 phút của cầu kiểm thử, không sinh cờ mới. Khoá không xoá: `prefs_set` vẫn ghi/đọc, giá trị đã đặt
 *    trên xe vẫn có hiệu lực (KDoc [CameraSettingsIa]). Ẩn ≠ mất — U12 nói *"cắt vì dài không được thành ẩn tính
 *    năng"*, và câu trả lời ở đây là: tính năng của **người lái** không bị cắt gì; thứ ẩn là dụng cụ đo.
 *
 * ## Vì sao mặc định KHÔNG còn viết vào nhãn
 * Mặc định nay theo **hồ sơ xe** (`CameraDefaults` — Seal: STRIP · GL · F55; xe chưa đo: NARROW · TV · 100). Một
 * nhãn *"(mặc định)"* trên chip đúng cho xe này và sai cho xe kia ⇒ bỏ khỏi nhãn, chip đang chọn đã tự nói.
 *
 * Nhóm này 100 % đi qua cầu (N2) — kể cả hai câu hỏi chỉ-đọc cho hàng *Nguồn* (`ClusterNavBridgeCamera.kt`).
 */
class SettingsCameraSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    private val bridge get() = deps.bridge

    fun build(body: LinearLayout) {
        cameraUser(body)
        // Cổng của tầng kỹ thuật = cổng của cầu kiểm thử. Tắt ⇒ không dựng một hàng nào (không phải mờ).
        if (TestBridgeStore.isOn(context)) cameraAdvanced(body)
    }

    // ── TẦNG NGƯỜI LÁI — đúng thứ tự [CameraSettingsIa.USER_KEYS] ────────────────────────────────────────────

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
        // chỉnh ở tầng kỹ thuật; bộ số của Seal đã là mặc định hồ sơ, người lái chỉ còn quyết "có nắn hay không".
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_dewarp_on_sub_header)))
        body.addView(rows.checkRow(
            on = bridge.cameraDewarpAmount() > CameraDewarpPrefs.AMOUNT_MIN,
            title = context.getString(R.string.kachi_camera_dewarp_on_title),
            sub = context.getString(R.string.kachi_camera_dewarp_on_sub),
        ) { on -> bridge.setCameraDewarpAmount(if (on) CameraDewarpPrefs.AMOUNT_MAX else CameraDewarpPrefs.AMOUNT_MIN) })
        cameraSource(body)
    }

    /**
     * NGUỒN (toàn cảnh / một camera) — **chỉ khi hồ sơ xe có bản đồ kênh** (R2: đời chưa đo ⇒ hàng vắng, không mờ:
     * một chip *Một camera* trên xe chưa biết kênh nào là kênh nào là một chip nói dối). Dưới hàng: dấu *"đã lùi về
     * toàn cảnh"* của phiên CHANNEL gần nhất (R3), chỉ khi đang chọn CHANNEL.
     */
    private fun cameraSource(body: LinearLayout) {
        if (!bridge.cameraChannelSupported()) return
        val sources = listOf(
            CameraSignalPolicy.SOURCE_PANO to context.getString(R.string.kachi_camera_source_pano),
            CameraSignalPolicy.SOURCE_CHANNEL to context.getString(R.string.kachi_camera_source_channel),
        )
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_source_sub)))
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_source_row), sources, bridge.cameraSource(),
        ) { v -> bridge.setCameraSource(v) })
        val fell = bridge.cameraChannelFallback()
        if (fell.isNotEmpty() && bridge.cameraSource() == CameraSignalPolicy.SOURCE_CHANNEL) {
            body.addView(rows.note(context.getString(R.string.kachi_camera_channel_fallback_note, fell)))
        }
    }

    /** Nhãn chip hình khung theo mã `:core`; mã chưa có chữ ⇒ hiện mã (lộ ra để sửa, không im lặng bỏ chip). */
    private fun shapeLabel(code: String): String = when (code) {
        CameraSignalPolicy.SHAPE_RECT -> context.getString(R.string.kachi_camera_shape_rect)
        CameraSignalPolicy.SHAPE_ROUND -> context.getString(R.string.kachi_camera_shape_round)
        CameraSignalPolicy.SHAPE_CLUSTER -> context.getString(R.string.kachi_camera_shape_cluster)
        else -> code
    }

    // ── TẦNG KỸ THUẬT — một khối gập, chỉ khi chế độ kiểm thử mở ─────────────────────────────────────────────

    private fun cameraAdvanced(body: LinearLayout) {
        body.addView(rows.disclosureRow(Disclosure(
            title = context.getString(R.string.kachi_camera_advanced_title),
            count = context.getString(R.string.kachi_camera_advanced_count, CameraSettingsIa.TECH_KEYS.size),
        ) { inner -> cameraTech(inner) }))
    }

    /** Thân khối gập — mọi móc đo của 2.71…2.75, y nguyên hành vi, chỉ đổi chỗ đứng. */
    private fun cameraTech(body: LinearLayout) {
        body.addView(rows.note(context.getString(R.string.kachi_camera_advanced_note)))
        // ĐƯỜNG KẾT XUẤT (CLOSE-14 · CAM-LAG). Mã GL đứng CUỐI (CLAUDE.md §6). Nhãn SurfaceView nói THẲNG cái mất.
        val renders = listOf(
            CameraSignalPolicy.RENDER_TEXTURE to context.getString(R.string.kachi_camera_render_texture),
            CameraSignalPolicy.RENDER_SURFACE to context.getString(R.string.kachi_camera_render_surface),
            CameraSignalPolicy.RENDER_GL to context.getString(R.string.kachi_camera_render_gl),
        )
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_render_sub)))
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_render_row), renders, bridge.cameraRender(),
        ) { v -> bridge.setCameraRender(v) })
        // R8-A (2.74): bề rộng vùng gương + dải từng bên — bộ dò trên xe, hình học ở `:core` [CameraPanoCrop].
        val spans = listOf(
            CameraSignalPolicy.SPAN_NARROW to context.getString(R.string.kachi_camera_span_narrow),
            CameraSignalPolicy.SPAN_STRIP to context.getString(R.string.kachi_camera_span_strip),
        )
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_span_sub)))
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_span_row), spans, bridge.cameraSpan(),
        ) { v -> bridge.setCameraSpan(v) })
        val strips = CameraPanoCrop.STRIPS_ALL.map { it.toString() to it.toString() }
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_strip_sub)))
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_strip_left), strips, bridge.cameraStripLeft().toString(),
        ) { v -> v.toIntOrNull()?.let { bridge.setCameraStrip(left = true, v = it) } })
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_strip_right), strips, bridge.cameraStripRight().toString(),
        ) { v -> v.toIntOrNull()?.let { bridge.setCameraStrip(left = false, v = it) } })
        // KÊNH HAL — móc đo §6.3-C1; chip đầu = "tự dò" = đường 2.73.
        val halModes = CameraSignalPolicy.HAL_MODES.map { mode ->
            mode.toString() to if (mode == CameraSignalPolicy.HAL_MODE_AUTO) {
                context.getString(R.string.kachi_camera_hal_auto)
            } else {
                mode.toString()
            }
        }
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_hal_sub)))
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_hal_row), halModes, bridge.cameraHalMode().toString(),
        ) { v -> v.toIntOrNull()?.let { bridge.setCameraHalMode(it) } })
        // cameraId từng bên (owner 2026-09-25: SL6/xe khác tự dò cam nào lên).
        val camIds = listOf("0" to "0", "1" to "1", "2" to "2", "3" to "3", "4" to "4", "5" to "5")
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_pick_sub)))
        body.addView(rows.chipRow(context.getString(R.string.kachi_camera_pick_left), camIds, bridge.cameraCamLeft().toString()) { v -> bridge.setCameraCamLeft(v.toInt()) })
        body.addView(rows.chipRow(context.getString(R.string.kachi_camera_pick_right), camIds, bridge.cameraCamRight().toString()) { v -> bridge.setCameraCamRight(v.toInt()) })
        cameraDewarp(body)
    }

    /**
     * **Nắn méo (GL)** — tám hàng −/+ và một ô tích (R8-B · 2.74), nay ở tầng kỹ thuật.
     *
     * −/+ chứ không chip giá trị sẵn: owner chỉnh **bằng mắt, trên xe đang đỗ** (*"nhích thêm một chút"*). Thứ tự
     * hàng = thứ tự CHỈNH khuyên dùng: **tâm → K → tiêu cự → phóng → độ nắn → dịch** (`camera-dewarp-math.md` §4);
     * hai hàng Dịch đứng CUỐI (CLAUDE.md §6: chúng trượt khung, không chữa bệnh cong). Bước nhảy từ `:core`.
     */
    private fun cameraDewarp(body: LinearLayout) {
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_dewarp_sub)))
        body.addView(rows.checkRow(
            on = bridge.cameraGlTexMatrix(),
            title = context.getString(R.string.kachi_camera_gl_texmatrix_title),
            sub = context.getString(R.string.kachi_camera_gl_texmatrix_sub),
        ) { on -> bridge.setCameraGlTexMatrix(on) })
        knob(body, R.string.kachi_camera_dewarp_cx, CameraDewarpPrefs.CENTER_STEP,
            CameraDewarpPrefs.CENTER_MIN, CameraDewarpPrefs.CENTER_MAX,
            { bridge.cameraDewarpCx() }, { bridge.setCameraDewarpCx(it) })
        knob(body, R.string.kachi_camera_dewarp_cy, CameraDewarpPrefs.CENTER_STEP,
            CameraDewarpPrefs.CENTER_MIN, CameraDewarpPrefs.CENTER_MAX,
            { bridge.cameraDewarpCy() }, { bridge.setCameraDewarpCy(it) })
        knob(body, R.string.kachi_camera_dewarp_k, CameraDewarpPrefs.PCT_STEP,
            CameraDewarpPrefs.PCT_MIN, CameraDewarpPrefs.PCT_MAX,
            { bridge.cameraDewarpK() }, { bridge.setCameraDewarpK(it) })
        knob(body, R.string.kachi_camera_dewarp_focal, CameraDewarpPrefs.PCT_STEP,
            CameraDewarpPrefs.PCT_MIN, CameraDewarpPrefs.PCT_MAX,
            { bridge.cameraDewarpFocal() }, { bridge.setCameraDewarpFocal(it) })
        knob(body, R.string.kachi_camera_dewarp_scale, CameraDewarpPrefs.PCT_STEP,
            CameraDewarpPrefs.PCT_MIN, CameraDewarpPrefs.PCT_MAX,
            { bridge.cameraDewarpScale() }, { bridge.setCameraDewarpScale(it) })
        knob(body, R.string.kachi_camera_dewarp_amount, CameraDewarpPrefs.AMOUNT_STEP,
            CameraDewarpPrefs.AMOUNT_MIN, CameraDewarpPrefs.AMOUNT_MAX,
            { bridge.cameraDewarpAmount() }, { bridge.setCameraDewarpAmount(it) })
        knob(body, R.string.kachi_camera_dewarp_pan_x, CameraDewarpPrefs.PAN_STEP,
            CameraDewarpPrefs.PAN_MIN, CameraDewarpPrefs.PAN_MAX,
            { bridge.cameraDewarpPanX() }, { bridge.setCameraDewarpPanX(it) })
        knob(body, R.string.kachi_camera_dewarp_pan_y, CameraDewarpPrefs.PAN_STEP,
            CameraDewarpPrefs.PAN_MIN, CameraDewarpPrefs.PAN_MAX,
            { bridge.cameraDewarpPanY() }, { bridge.setCameraDewarpPanY(it) })
        body.addView(rows.note(context.getString(R.string.kachi_camera_dewarp_note)))
    }

    /**
     * Một hàng −/+ cho một núm `%`: ghi qua cầu rồi **đọc lại** để hiện (miền hợp lệ do `:core` giữ, lượt đọc tự
     * kẹp — cộng ở đây thì màn nói `405 %` trong khi đĩa là `400`). [max]/[min] chỉ chặn lượt ghi vô nghĩa.
     */
    private fun knob(
        body: LinearLayout,
        labelRes: Int,
        step: Int,
        min: Int,
        max: Int,
        get: () -> Int,
        set: (Int) -> Unit,
    ) {
        lateinit var row: SettingsRows.Stepper
        fun nudge(by: Int) {
            set((get() + by).coerceIn(min, max))
            row.setValue(pct(get()))
        }
        row = rows.stepperRow(
            context.getString(labelRes), pct(get()),
            onMinus = { nudge(-step) }, onPlus = { nudge(step) },
        )
        body.addView(row.view)
    }

    /** `"100 %"` — dấu cách trước `%` theo lối viết tiếng Việt, một chỗ khai cho cả tám hàng. */
    private fun pct(v: Int): String = "$v %"
}
