package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.camera.CameraCamConfig
import com.byd.clusternav.launcher.camera.CameraDemand
import com.byd.clusternav.launcher.camera.CameraPlacementView
import com.byd.clusternav.launcher.camera.CameraSignalPolicy
import com.byd.clusternav.launcher.camera.CameraViewMode
import com.byd.clusternav.launcher.camera.CameraWhich
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ 2.93 · *Tiện nghi xe › Camera › Từng camera* — bộ chỉnh RIÊNG từng camera + nút *Xem thử* ═══════════════════════
 *
 * Spec `docs/specs/kachi-293-cam.html` R1/R2 · §4.5. Owner 06/10: *"cho chỉnh size và vị trí từng camera không nhỉ?"* ⇒ MỘT
 * hàng chip chọn camera (sau · trái · phải · trước) rồi MỘT bộ hàng cho camera đang chọn — số hàng trên màn KHÔNG nhân bốn
 * (owner 27/09 *"nhiều option quá rối"*):
 *  1. **Xem thử** — bật/tắt chính camera ấy theo yêu cầu (CÙNG đường của nút thanh nút / phím vật lý), để kéo-thả nhìn
 *     thấy ngay trên overlay thật (cửa sổ overlay không nhận chạm ⇒ Cài đặt vẫn bấm được bên dưới);
 *  2. **Góc mặc định** (Trái/Phải) — chọn góc = về góc ấy, bỏ vị trí kéo;
 *  3. **Vị trí** — ô kéo-thả vẽ display ĐÍCH (màn chính hoặc cụm theo *Hiện lên cụm*) + nút *Về góc mặc định*;
 *  4. **Cỡ** 50–150 %; 5. **Hình khung** · 6. **Kiểu hình** riêng (chip đầu = *Theo chung*); 7. **Xoay**; 8. **Lật gương**.
 *
 * Mọi hàng đi qua cầu (N2) — tệp này không chạm `Prefs.`. Hai camera gương ghi đúng sáu khoá cũ (góc · xoay · lật) ⇒ cấu
 * hình đã chỉnh trên xe giữ nguyên; hàng của chúng RỜI khỏi khối chung (một khoá, một hàng — `CameraSettingsIa`).
 */
internal class SettingsCameraPerCamSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    private val bridge get() = deps.bridge
    private var which: CameraWhich = CameraWhich.REAR
    private val editor = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var preview: TextView? = null
    private var placement: CameraPlacementView? = null
    private var unlisten: () -> Unit = {}

    fun build(body: LinearLayout) {
        body.addView(rows.subHeader(context.getString(R.string.kachi_camera_percam_sub)))
        body.addView(rows.note(context.getString(R.string.kachi_camera_percam_note)))
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_percam_row),
            CameraWhich.ALL.map { it.code to CameraSettingsLabels.cameraShort(context, it) },
            which.code,
        ) { code -> CameraWhich.ofCode(code)?.let { which = it; rebuild() } })
        body.addView(editor)
        rebuild()
        // Phím/giọng nói đổi camera theo yêu cầu khi trang đang mở ⇒ nút *Xem thử* nói đúng. Nghe theo vòng đời CỬA SỔ
        // (trang Cài đặt được nhớ lại — `SettingsPanel.pages` — nên dựng một lần, gắn/tháo nhiều lần).
        editor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { listen(); paintPreview(); bindPlacement() }
            override fun onViewDetachedFromWindow(v: View) { unlisten(); unlisten = {} }
        })
    }

    private fun rebuild() {
        editor.removeAllViews()
        val w = which
        preview = (rows.button(context.getString(R.string.kachi_camera_preview_on)) {
            bridge.toggleCameraDemand(w)
            listen()   // lượt bấm đầu có thể vừa DỰNG controller ⇒ giờ mới nghe được
            paintPreview()
        } as? TextView)?.also { editor.addView(it) }
        paintPreview()
        editor.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_corner_row),
            listOf(
                CameraSignalPolicy.CORNER_TOP_LEFT to context.getString(R.string.kachi_pos_tl),
                CameraSignalPolicy.CORNER_TOP_RIGHT to context.getString(R.string.kachi_pos_tr),
            ),
            bridge.cameraCorner(w),
        ) { v -> bridge.setCameraCorner(w, v); bindPlacement() })
        placement = CameraPlacementView(
            context,
            markerLabel = CameraSettingsLabels.cameraShort(context, w),
            hint = context.getString(R.string.kachi_camera_place_hint),
        ) { p -> bridge.setCameraPlace(w, p) }.also { editor.addView(rows.embed(it, Sp.EMBED_M)) }
        bindPlacement()
        editor.addView(rows.button(context.getString(R.string.kachi_camera_place_reset)) {
            bridge.setCameraPlace(w, null); bindPlacement()
        })
        editor.addView(rows.sliderRow(
            label = context.getString(R.string.kachi_camera_size_row),
            positions = CameraCamConfig.SIZE_POSITIONS,
            current = CameraCamConfig.sizePosition(bridge.cameraSize(w)),
            valueText = { pos -> "${CameraCamConfig.sizeAt(pos)}%" },
            describe = { text -> context.getString(R.string.kachi_camera_size_desc, text) },
            onPreview = { pos -> placement?.bind(bridge.cameraPlacementModel(w), bridge.cameraPlace(w), CameraCamConfig.sizeAt(pos)) },
        ) { pos -> bridge.setCameraSize(w, CameraCamConfig.sizeAt(pos)); bindPlacement() })
        editor.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_shape_own_row),
            (listOf(CameraCamConfig.FOLLOW) + CameraSignalPolicy.SHAPES).map { it to CameraSettingsLabels.shape(context, it) },
            bridge.cameraShapeChoice(w),
            wrap = true,
        ) { v -> bridge.setCameraShapeChoice(w, v); bindPlacement() })
        editor.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_projection_own_row),
            (listOf(CameraCamConfig.FOLLOW) + CameraViewMode.MODES).map { it to CameraSettingsLabels.projection(context, it) },
            bridge.cameraProjectionChoice(w),
            wrap = true,
        ) { v -> bridge.setCameraProjectionChoice(w, v); bindPlacement() })
        editor.addView(rows.chipRow(
            context.getString(R.string.kachi_camera_rot_own_row),
            CameraSettingsLabels.rotations(context),
            bridge.cameraRotationOf(w),
            wrap = true,
        ) { v -> bridge.setCameraRotationOf(w, v); bindPlacement() })
        editor.addView(rows.checkRow(
            on = bridge.cameraMirrorOf(w),
            title = context.getString(R.string.kachi_camera_mirror_own_title),
            sub = context.getString(R.string.kachi_camera_mirror_row_sub),
        ) { on -> bridge.setCameraMirrorOf(w, on) })
    }

    /** Ô kéo-thả đọc lại mô hình + chỗ + cỡ ĐÃ LƯU (nguồn sự thật là prefs, không phải biến trong màn). */
    private fun bindPlacement() {
        val w = which
        placement?.bind(bridge.cameraPlacementModel(w), bridge.cameraPlace(w), bridge.cameraSize(w))
    }

    /**
     * Chữ nút *Xem thử* theo trạng thái THẬT của controller (không theo cờ trong màn). *"Đang mở"* = camera theo yêu cầu là
     * camera này VÀ đang hiện — bị camera xi-nhan che thì chạm là ĐƯA LÊN, không tắt (`CameraDemand.next`).
     */
    private fun paintPreview() {
        val on = CameraDemand.isOn(which, bridge.cameraDemanded(), bridge.cameraShowing())   // CÙNG luật sáng của ô thanh nút (wave 2B)
        preview?.text = context.getString(if (on) R.string.kachi_camera_preview_off else R.string.kachi_camera_preview_on)
    }

    /** Nghe đổi trạng thái (gỡ lượt nghe cũ trước — không chồng hai người nghe cho một trang). */
    private fun listen() {
        unlisten()
        unlisten = bridge.onCameraDemandChanged { paintPreview() }
    }
}
