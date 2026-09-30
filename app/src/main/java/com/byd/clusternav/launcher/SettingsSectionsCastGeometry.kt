package com.byd.clusternav.launcher

import android.content.Context
import android.widget.LinearLayout
import com.byd.clusternav.R
import com.byd.clusternav.modules.clustercast.simplified.CastBounds
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

    fun build(body: LinearLayout) {
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        holder = box
        body.addView(box)
        rebuild()
    }

    fun rebuild() {
        val holder = holder ?: return
        holder.removeAllViews()
        val targets = bridge.geometryTargets()
        if (targets.isEmpty()) return
        val target = targets.firstOrNull { it.side == side } ?: targets.first()
        side = target.side

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
        addEdgeSteppers(holder, target)
        holder.addView(rows.chipRow(
            label = context.getString(R.string.kachi_cast_geom_dpi),
            options = bridge.geometryDensityOptions().map { it.toString() to it.toString() },
            current = bridge.geometryDensity(target).toString(),
        ) { code -> code.toIntOrNull()?.let { bridge.setGeometryDensity(it) } })
        holder.addView(rows.note(context.getString(R.string.kachi_cast_geom_dpi_note)))
        profileNote(target)?.let { holder.addView(rows.note(it)) }
        holder.addView(rows.button(context.getString(R.string.kachi_cast_geom_reset)) {
            bridge.resetGeometry(target)
            rebuild()
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
     * Giá trị hiển thị đọc **lại từ cầu** sau mỗi lần bấm (cầu kẹp vào dải của ô), chứ không cộng dồn một biến
     * trong màn: kẹp im lặng mà màn vẫn đếm tiếp thì hai bên lệch nhau ngay ở cú bấm thứ hai — đúng bệnh mà
     * `syncBadge` ở nhóm *Dẫn đường* đã phải chữa.
     */
    private fun addEdgeSteppers(holder: LinearLayout, target: CastGeometryTarget) {
        data class Edge(val labelRes: Int, val read: (CastBounds) -> Int, val write: (CastBounds, Int) -> CastBounds)
        val edges = listOf(
            Edge(R.string.kachi_cast_geom_edge_left, { it.left }, { b, v -> b.copy(left = v) }),
            Edge(R.string.kachi_cast_geom_edge_top, { it.top }, { b, v -> b.copy(top = v) }),
            Edge(R.string.kachi_cast_geom_edge_right, { it.right }, { b, v -> b.copy(right = v) }),
            Edge(R.string.kachi_cast_geom_edge_bottom, { it.bottom }, { b, v -> b.copy(bottom = v) }),
        )
        val steppers = HashMap<Int, SettingsRows.Stepper>()
        fun nudge(edge: Edge, delta: Int) {
            val now = bridge.geometryBounds(target)
            bridge.setGeometryBounds(target, edge.write(now, edge.read(now) + delta))
            val after = bridge.geometryBounds(target)
            edges.forEach { steppers[it.labelRes]?.setValue(px(it.read(after))) }
        }
        edges.forEach { edge ->
            val stepper = rows.stepperRow(
                context.getString(edge.labelRes), px(edge.read(bridge.geometryBounds(target))),
                onMinus = { nudge(edge, -GEOMETRY_STEP_PX) }, onPlus = { nudge(edge, GEOMETRY_STEP_PX) },
            )
            steppers[edge.labelRes] = stepper
            holder.addView(stepper.view)
        }
    }

    private fun slotLabel(side: ClusterSlotSide?): String = context.getString(
        if (side == ClusterSlotSide.RIGHT) R.string.kachi_cast_geom_right else R.string.kachi_cast_geom_left,
    )

    private fun px(value: Int): String = context.getString(R.string.kachi_px_value, value)
}
