package com.byd.clusternav.launcher

import android.content.Context
import android.widget.LinearLayout
import android.widget.Toast
import com.byd.clusternav.R
import com.byd.clusternav.modules.clustercast.simplified.CastContentShift
import com.byd.clusternav.modules.clustercast.simplified.CastShiftResult

/**
 * ═══ 3.1 · R6 — hàng **"Dịch nội dung"** trong khối Khung (Cài đặt › Chiếu cụm), dưới bốn thanh mép ═══════════════════════════
 *
 * Cùng khuôn với bốn thanh mép ([SettingsCastGeometryBlock]): bấm cộng dồn trên Ý ĐỊNH (số đổi ngay, đúng bước của chip Bước
 * 5/20/50), gửi qua hàng gộp, nhấn giữ lặp ([SettingsRepeatTouch]); khi hàng rảnh hoặc bản ghim đổi ⇒ số về sự thật. Âm = kéo nội
 * dung LÊN (ca Waze dư ~20 px trên đỉnh: chọn Bước 5 hoặc 20 rồi bấm − tới −20).
 *
 * Chỉ dựng khi [ClusterNavBridge.contentShiftAvailable]. Kết cục "app không nhận" (đã đo trên xe, cụm đã được trả ngay) ⇒ một
 * dòng báo + khối dựng lại (hàng biến mất cho app này — phán quyết bền theo xe).
 */
internal class SettingsCastShiftRow(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
    /** Bước hiện tại của khối (chip Bước, px). */
    private val step: () -> Int,
    /** Dựng lại cả khối (hàng ẩn đi khi app bị đo là không nhận). */
    private val rebuild: () -> Unit,
) {
    private val bridge get() = deps.bridge
    private var target: CastGeometryTarget? = null
    private var stepper: SettingsRows.Stepper? = null

    /** Ý định của màn (`null` = hiện sự thật của phiên) — sống qua các lần dựng khi còn lệnh chờ. */
    private var intent: Int? = null

    /** Khối vừa xoá hết hàng — quên hàng cũ (kết cục tới muộn không vẽ lên view đã gỡ). */
    fun detach() {
        target = null
        stepper = null
    }

    fun addTo(holder: LinearLayout, t: CastGeometryTarget) {
        detach()
        if (!bridge.contentShiftAvailable(t)) return
        target = t
        if (!bridge.contentShiftBusy()) intent = null
        val minus = { nudge(-1) }
        val plus = { nudge(1) }
        val s = rows.stepperRow(context.getString(R.string.kshift_label), px(intent ?: bridge.contentShift(t)), onMinus = minus, onPlus = plus)
        s.view.contentDescription = context.getString(R.string.kshift_a11y)
        SettingsRepeatTouch.attachToStepper(s.view, minus, plus)
        stepper = s
        holder.addView(s.view)
        holder.addView(rows.note(context.getString(R.string.kshift_note)))
    }

    /** Số về sự thật của phiên — trừ khi còn lệnh chờ/đang áp (đồng bộ lúc đó là kéo số lùi giữa chuỗi bấm). */
    fun sync() {
        val t = target ?: return
        if (bridge.contentShiftBusy()) return
        intent = null
        stepper?.setValue(px(bridge.contentShift(t)))
    }

    /** Kết cục một lượt (đã `post` về luồng chính). */
    fun onOutcome(result: CastShiftResult) {
        if (target == null) return
        when (result) {
            CastShiftResult.INEFFECTIVE -> {
                Toast.makeText(context, R.string.kshift_ineffective, Toast.LENGTH_LONG).show()
                intent = null
                rebuild()
                return
            }
            CastShiftResult.FAILED -> Toast.makeText(context, R.string.kshift_failed, Toast.LENGTH_SHORT).show()
            else -> Unit
        }
        sync()
    }

    private fun nudge(sign: Int) {
        val t = target ?: return
        val now = intent ?: bridge.contentShift(t)
        val next = CastContentShift.clamp(now + sign * step())
        if (next == now) return   // đứng ở biên ⇒ không gửi
        intent = next
        stepper?.setValue(px(next))
        bridge.setContentShift(t, next)
    }

    private fun px(value: Int): String = context.getString(R.string.kachi_px_value, value)
}
