package com.byd.clusternav.launcher

import android.animation.ObjectAnimator
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.Toast
import com.byd.clusternav.AppContainer
import com.byd.clusternav.R
import java.util.concurrent.CopyOnWriteArraySet

/**
 * ═══ 3.01 · owner 13/10 *"Chặn MỞ cốp khi xe chạy (Khuyên dùng)"* — phần `:app` của cổng CHẠM ═══════════════════════════
 *
 * Phép quyết ở `:core` ([CtlSafetyPolicy.blocked] — MỘT phép cho chạm · giọng nói · phím, owner 13/10);
 * nơi gọi DUY NHẤT là [ControlTileFactory] (`gated`) — mọi đường chạm (ô nút thanh/giữa màn · ô nhóm Cửa & khoang · chấm cốp
 * trên hình xe Kính, vốn `performClick()` chính ô nút đó) đều qua đó. Tệp này chỉ: (1) đọc chuyển động TƯƠI, (2) báo khi chặn.
 */
internal object TileSafetyGate {

    private const val TAG = "KachiCtl"
    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()
    private var lastToast: Toast? = null

    /** Bảng hình xe nghe "mã [id] vừa bị chặn" (luồng chính) để chấm của nó lắc — ô nút thật của chấm đang `GONE`. */
    fun listen(l: (String) -> Unit) { listeners += l }
    fun unlisten(l: (String) -> Unit) { listeners -= l }

    /**
     * Tốc độ + số TƯƠI (làn ghi nền — có I/O HAL): [AppContainer.readFresh] lùi ảnh chụp, qua [CtlSafetyPolicy.readMotion] —
     * CÙNG phép đọc của giọng nói/phím (`VoiceControlDispatch.motion`).
     */
    fun motion(ctx: Context): CtlSafetyPolicy.Motion {
        val c = runCatching { AppContainer.get(ctx) }.getOrNull() ?: return CtlSafetyPolicy.Motion(null, null)
        val m = CtlSafetyPolicy.readMotion(fresh = c::readFresh, snapshot = { c.carStatusRepository.status.value })
        Log.i(TAG, "tap-gate motion speed=${m.speedKmh} gear=${m.gear}")
        return m
    }

    /** Đã chặn MỞ [def] (luồng chính): không lệnh nào đi; ô lắc nhẹ, một câu ngắn (toast + đọc to), báo bảng hình xe. */
    fun refuse(view: View, def: ControlDef) {
        Log.i(TAG, "${def.id}: MỞ bị chặn — xe chưa chứng minh đứng yên (cổng chạm)")
        val text = view.context.getString(R.string.kctl_stop_to_open_trunk)
        if (view.isShown) shake(view)
        runCatching {
            lastToast?.cancel()
            lastToast = Toast.makeText(DockScaleContext.unscaled(view.context), text, Toast.LENGTH_SHORT).also { it.show() }
        }
        view.rootView?.announceForAccessibility(text)
        listeners.forEach { it(def.id) }
    }

    /** Lắc ngang một nhịp ngắn (≈ 240 ms) — "không" bằng cử chỉ, không đổi trạng thái. */
    fun shake(v: View) {
        val a = v.resources.displayMetrics.density * SHAKE_DP
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, -a, a, -a / 2, a / 2, 0f).setDuration(SHAKE_MS).start()
    }

    private const val SHAKE_DP = 4f
    private const val SHAKE_MS = 240L
}
