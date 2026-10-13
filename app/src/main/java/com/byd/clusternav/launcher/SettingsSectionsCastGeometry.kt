package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import com.byd.clusternav.R
import com.byd.clusternav.modules.clustercast.simplified.CastBounds
import com.byd.clusternav.modules.clustercast.simplified.CastEdge
import com.byd.clusternav.modules.clustercast.simplified.CastGeometryIntent
import com.byd.clusternav.modules.clustercast.simplified.CastGeometrySteps
import com.byd.clusternav.modules.clustercast.simplified.CastResizeOutcome
import com.byd.clusternav.modules.clustercast.simplified.CastResizeResult
import com.byd.clusternav.modules.clustercast.simplified.ClusterSlotSide

/**
 * ═══ Khối **"Khung và DPI (khi đang chiếu)"** của nhóm Chiếu cụm ([SettingsCastSection]) ═══════════════════════════
 *
 * Tách khỏi `SettingsSectionsCast.kt` ở V-CLUSTER (2026-09-30, spec `kachi-profiles-are-everything.html` §11.4.9 — trần
 * 500 dòng) **trước** khi thêm dòng phụ "hồ sơ này khác phiên". Thân [rebuild]/[addEdgeSteppers] dời nguyên; chỉ thêm
 * dòng phụ [profileNote] (VC-R9).
 *
 * Bộ chỉnh **khung + DPI** của màn cũ (`CastGeometryEditor`, đã gỡ 2026-09-13), dựng lại bằng các hàng chuẩn.
 *
 * ## Vì sao thành bốn thanh −/+, không phải khung kéo-thả
 * Bản cũ cho kéo một hình chữ nhật (`CastResizeView`). Kéo thì nhanh khi đã quen, nhưng nó không nói **số**,
 * mà việc thật ở đây là *"đẩy mép phải vào 20px cho khỏi che đồng hồ"* — một việc cần độ chính xác, làm trên
 * màn xe, có thể đang đỗ giữa đường. Thanh −/+ có đích chạm 48 và nói rõ toạ độ đang ở đâu; khung kéo-thả nếu
 * cần vẫn còn ở nhóm *Dẫn đường* cho biển báo (nơi vị trí là cảm tính, không phải số).
 *
 * ## Ẩn hẳn khi không chiếu — không "hiện mà bấm không ăn"
 * `bridge.geometryTargets()` rỗng ⇒ chưa chiếu, hoặc đang chiếu CarPlay/Android Auto (resize không ăn, đã
 * chứng minh). Bản cũ cũng ẩn (`updateVisibility`); bốn thanh trơ ra mà bấm không đổi gì còn tệ hơn không có.
 *
 * ## V-CLUSTER — số trên màn là số của PHIÊN
 * Bốn thanh và chip DPI đọc bản GHIM của phiên (`ClusterNavBridgeGeometry`), không đọc prefs: đổi hồ sơ giữa lúc chiếu
 * thì prefs đã mang giá trị của hồ sơ mới mà cụm chưa đổi (VC-R5). Giá trị của hồ sơ chỉ hiện ở một dòng phụ, và chỉ
 * khi nó khác — một dòng phụ luôn hiện là một dòng người lái học cách bỏ qua.
 *
 * ## 3.1 · R1–R4, R7 — ý định ở màn, sự thật ở phiên (spec `kachi-301-cast-steps-dialog-ia.html` §4.1)
 * Lệnh áp chạy bất đồng bộ (hàng gộp `CastResizeQueue` trên executor của coordinator); bản ghim chỉ đổi khi `am task resize` xong.
 * Màn giữ [intent] mỗi ô, cộng dồn ±[step] trên đó và kẹp ĐỒNG BỘ bằng `bridge.geometryClamp` ⇒ số đổi ngay, đúng bước. Khi
 * hàng gộp báo ô đã rảnh ([onOutcome]) hoặc bản ghim đổi ([sync], gọi từ bộ nghe trang qua `geometryKey`) thì bỏ ý định ⇒ số về
 * sự thật; lệnh HỎNG thêm một dòng báo. Nhấn giữ lặp bước ([SettingsRepeatTouch]); bước 5/20/50 theo hồ sơ (`cast_frame_step`).
 */
class SettingsCastGeometryBlock(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    private val bridge get() = deps.bridge

    /**
     * Hộp của khối — nó phụ thuộc **trạng thái chiếu đang chạy**, mà trang Cài đặt thì được nhớ lại
     * ([SettingsPanel.pages]) chứ không dựng lại. Nên khối này nằm trong một hộp riêng được [rebuild]
     * dựng lại sau mỗi hành động đổi trạng thái — cùng khuôn với `rebuildAutostart`, vì cùng lý do.
     */
    private var holder: LinearLayout? = null

    /** Nửa đang chỉnh khi cụm chia đôi (`null` = toàn cụm). Nhớ lại giữa hai lần dựng để không nhảy về ô trái. */
    private var side: ClusterSlotSide? = null

    /** 3.1 · R1 — ý định của từng ô (sống qua các lần [rebuild]: ô còn lệnh chờ thì không mất các bước vừa bấm). */
    private val intent = CastGeometryIntent<CastGeometryTarget>()

    /** Ô đang vẽ bốn thanh (`null` = khối đang ẩn) + bốn thanh của nó — [sync] đặt lại SỐ tại chỗ, không dựng lại. */
    private var shown: CastGeometryTarget? = null
    private val steppers = HashMap<CastEdge, SettingsRows.Stepper>()

    /** 3.1 · R4 — bước hiện tại (px), đọc lại từ hồ sơ mỗi lần [rebuild]. */
    private var step = CastGeometrySteps.DEFAULT

    /** 3.1 · R6 — hàng "Dịch nội dung" (cùng chip Bước với bốn mép; chỉ hiện khi đo được — [SettingsCastShiftRow]). */
    private val shiftRow = SettingsCastShiftRow(context, rows, deps, { step }, { rebuild() })

    fun build(body: LinearLayout) {
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        holder = box
        body.addView(box)
        watchOutcomes(box)
        rebuild()
    }

    /**
     * 3.1 · R2 — nghe kết cục lệnh khung khi khối ĐANG trên màn (gắn lúc lên màn, gỡ lúc rời — không rò người nghe theo trang
     * được nhớ). Kết cục tới trên luồng coordinator ⇒ `post` về luồng chính.
     */
    private fun watchOutcomes(anchor: View) {
        val watcher = object : View.OnAttachStateChangeListener {
            private var unsubscribe: (() -> Unit)? = null
            private var unsubscribeShift: (() -> Unit)? = null
            override fun onViewAttachedToWindow(v: View) {
                unsubscribe?.invoke()
                unsubscribeShift?.invoke()
                unsubscribe = bridge.observeGeometryOutcome { outcome -> v.post { if (v.isAttachedToWindow) onOutcome(outcome) } }
                // 3.1 · R6 — kết cục hàng "Dịch nội dung" (cùng vòng đời gắn/gỡ).
                unsubscribeShift = bridge.observeContentShift { _, r -> v.post { if (v.isAttachedToWindow) shiftRow.onOutcome(r) } }
            }
            override fun onViewDetachedFromWindow(v: View) {
                unsubscribe?.invoke()
                unsubscribeShift?.invoke()
                unsubscribe = null
                unsubscribeShift = null
            }
        }
        anchor.addOnAttachStateChangeListener(watcher)
        if (anchor.isAttachedToWindow) watcher.onViewAttachedToWindow(anchor)
    }

    /** Kết cục của MỘT yêu cầu khung: hỏng ⇒ một dòng báo; rồi đồng bộ số về sự thật nếu ô đã rảnh. */
    private fun onOutcome(outcome: CastResizeOutcome) {
        val target = shown ?: return
        if (outcome.request.side != target.side) return
        if (outcome.result == CastResizeResult.FAILED) {
            Toast.makeText(context, R.string.kachi_cast_geom_resize_failed, Toast.LENGTH_SHORT).show()
        }
        sync()
    }

    /**
     * 3.1 · R2 — số của bốn thanh = sự thật của phiên, KHÔNG dựng lại khối. Ô còn lệnh chờ/đang áp ⇒ giữ ý định (sự thật sắp đổi
     * — đồng bộ lúc này là kéo số lùi về giữa chuỗi bấm). Gọi từ bộ nghe trang (`SettingsCastSection.liveStatus`, khi `geometryKey`
     * đổi) và từ [onOutcome].
     */
    fun sync() {
        shiftRow.sync()   // 3.1 · R6 — độ dịch theo bản ghim của phiên (geometryKey mang cả overscan)
        val target = shown ?: return
        if (bridge.geometryTargets().none { it == target }) return   // ô không còn — trang tự dựng lại theo statusKey
        if (bridge.geometryResizeBusy(target)) return
        intent.forget(target)
        show(bridge.geometryBounds(target))
    }

    private fun show(b: CastBounds) {
        CastEdge.values().forEach { edge -> steppers[edge]?.setValue(px(edge.read(b))) }
    }

    fun rebuild() {
        val holder = holder ?: return
        holder.removeAllViews()
        steppers.clear()
        shiftRow.detach()
        shown = null
        val targets = bridge.geometryTargets()
        if (targets.isEmpty()) return
        val target = targets.firstOrNull { it.side == side } ?: targets.first()
        side = target.side
        shown = target
        step = bridge.geometryStep()
        if (!bridge.geometryResizeBusy(target)) intent.forget(target)

        holder.addView(rows.subHeader(context.getString(R.string.kachi_sub_cast_geometry)))
        if (targets.size > 1) {
            holder.addView(rows.chipRow(
                label = context.getString(R.string.kachi_cast_geom_slot),
                options = targets.map { it.side!!.name to slotLabel(it.side) },
                current = target.side?.name.orEmpty(),
            ) { code ->
                side = ClusterSlotSide.values().firstOrNull { it.name == code }
                rebuild()
            })
        }
        holder.addView(rows.chipRow(
            label = context.getString(R.string.kachi_cast_frame_step),
            options = CastGeometrySteps.OPTIONS.map { it.toString() to px(it) },
            current = step.toString(),
        ) { code ->
            step = CastGeometrySteps.normalize(code.toIntOrNull())
            bridge.setGeometryStep(step)
        })
        addEdgeSteppers(holder, target)
        holder.addView(rows.note(context.getString(R.string.kachi_cast_geom_overflow_note)))
        shiftRow.addTo(holder, target)   // 3.1 · R6 — "Dịch nội dung" (âm = lên), ẩn khi không đo được/không nhận
        holder.addView(rows.chipRow(
            label = context.getString(R.string.kachi_cast_geom_dpi),
            options = bridge.geometryDensityOptions().map { it.toString() to it.toString() },
            current = bridge.geometryDensity(target).toString(),
        ) { code -> code.toIntOrNull()?.let { bridge.setGeometryDensity(it) } })
        holder.addView(rows.note(context.getString(R.string.kachi_cast_geom_dpi_note)))
        profileNote(target)?.let { holder.addView(rows.note(it)) }
        holder.addView(rows.button(context.getString(R.string.kachi_cast_geom_reset)) {
            intent.set(target, bridge.resetGeometry(target))
            show(intent.shown(target, bridge.geometryBounds(target)))
        })
    }

    /**
     * V-CLUSTER · VC-R9 — *"Hồ sơ này: DPI 240, khung 1920×720, góc trên-trái 0,0 — áp dụng từ lần chiếu sau"*, CHỈ khi hồ sơ đang
     * dùng lưu khác thứ phiên đang hiện (vừa đổi hồ sơ giữa lúc chiếu). `null` ⇒ không vẽ gì.
     *
     * Mang cả GÓC TRÊN-TRÁI (senior review Pass 1): [ClusterNavBridge.geometryProfileDiffers] so cả vị trí, nên hai khung
     * cùng W×H mà lệch chỗ vẫn ra dòng phụ — thiếu vị trí thì dòng đó in đúng con số đang hiện, người lái không biết khác gì.
     */
    private fun profileNote(target: CastGeometryTarget): String? {
        val profile = bridge.geometryProfileDiffers(target) ?: return null
        val b = profile.bounds
        return context.getString(R.string.kachi_cast_geom_profile_differs, profile.density, b.width, b.height, b.left, b.top)
    }

    /**
     * Bốn mép của ô, mỗi mép một hàng −/+.
     *
     * 3.1 · R1: cú bấm cộng dồn trên Ý ĐỊNH ([intent]) rồi kẹp ĐỒNG BỘ bằng `bridge.geometryClamp` — cùng phép kẹp cầu áp trước
     * khi gửi ⇒ số trên màn = số gửi, kể cả khi kẹp ở biên (bài học `syncBadge` của nhóm *Dẫn đường*: không đếm tiếp sau khi bị
     * kẹp). Bản cũ đọc lại bản ghim NGAY sau lệnh bất đồng bộ ⇒ trễ một nhịp, nhảy đôi, nuốt bước (spec §2.1).
     */
    private fun addEdgeSteppers(holder: LinearLayout, target: CastGeometryTarget) {
        val labels = mapOf(
            CastEdge.LEFT to R.string.kachi_cast_geom_edge_left,
            CastEdge.TOP to R.string.kachi_cast_geom_edge_top,
            CastEdge.RIGHT to R.string.kachi_cast_geom_edge_right,
            CastEdge.BOTTOM to R.string.kachi_cast_geom_edge_bottom,
        )
        val now = intent.shown(target, bridge.geometryBounds(target))
        CastEdge.values().forEach { edge ->
            val minus = { nudge(edge, -1) }
            val plus = { nudge(edge, 1) }
            val stepper = rows.stepperRow(context.getString(labels.getValue(edge)), px(edge.read(now)), onMinus = minus, onPlus = plus)
            SettingsRepeatTouch.attachToStepper(stepper.view, minus, plus)   // R3: nhấn giữ lặp bước
            steppers[edge] = stepper
            holder.addView(stepper.view)
        }
    }

    /** Một bước ([sign] = ±1 × [step]) trên mép [edge] của ô đang vẽ: hiện ngay, gửi khung đã kẹp; đứng ở biên ⇒ không gửi. */
    private fun nudge(edge: CastEdge, sign: Int) {
        val target = shown ?: return
        val truth = bridge.geometryBounds(target)
        val next = intent.nudge(target, truth, edge, sign * step) { bridge.geometryClamp(target, it) } ?: return
        show(next)
        bridge.setGeometryBounds(target, next)
    }

    private fun slotLabel(side: ClusterSlotSide?): String = context.getString(
        if (side == ClusterSlotSide.RIGHT) R.string.kachi_cast_geom_right else R.string.kachi_cast_geom_left,
    )

    private fun px(value: Int): String = context.getString(R.string.kachi_px_value, value)
}
