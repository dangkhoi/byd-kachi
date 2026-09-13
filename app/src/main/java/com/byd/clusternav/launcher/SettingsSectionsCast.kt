package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.byd.clusternav.R
import com.byd.clusternav.modules.clustercast.simplified.CastBounds
import com.byd.clusternav.modules.clustercast.simplified.ClusterSlotSide
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState
import com.byd.clusternav.ui.ClusterPreviewView
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Nhóm **"Chiếu màn lên cụm"** (IA v2 §4.1 nhóm 6) — công tắc chính · tỉ lệ chia đôi · tự chiếu khi nổ máy ·
 * chiếu ngay · cứu hộ. Mọi đường ghi/hành động đi qua [ClusterNavBridge] (spec R2 · N2).
 *
 * ## Vì sao "hành động" và "cấu hình" nằm CHUNG một trang
 * Bốn nút *Chiếu ngay* không lưu gì cả — chúng là **việc làm**. Đặt chúng ở đây (thay vì chỉ để ở nút nổi) vì
 * người dùng vào trang này đúng lúc muốn thử: bật công tắc → chọn tỉ lệ → chiếu thử → thấy sai thì Dừng. Bắt họ
 * đóng Cài đặt, tìm nút nổi, rồi quay lại là ba cú chạm thừa cho một vòng thử.
 *
 * ## Danh sách app: HAI danh sách khác nhau, cố ý
 * `bridge.autostartAppOptions()` loại thêm mọi launcher (guard của `CastAutostart`), còn
 * `bridge.installedCastApps()` thì không. Màn cũ đang như vậy và [ĐO] lý do có thật (tự chiếu một launcher lên
 * cụm là tự khoá mình). Giữ nguyên hai danh sách thay vì hợp nhất — CLAUDE.md §6: không đảo đường đang chạy tốt.
 */
class SettingsCastSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    private val bridge get() = deps.bridge

    private lateinit var statusRow: SettingsRows.StatusRow

    /** Gương CHỈ-ĐỌC của cụm (R2b) — giữ tham chiếu để [refreshStatus] vẽ lại, không dựng lại trang. */
    private var preview: ClusterPreviewView? = null

    /**
     * Khối "khung và DPI" (R2a) — nó phụ thuộc **trạng thái chiếu đang chạy**, mà trang Cài đặt thì được nhớ lại
     * ([SettingsPanel.pages]) chứ không dựng lại. Nên khối này nằm trong một hộp riêng được [rebuildGeometry]
     * dựng lại sau mỗi hành động đổi trạng thái — cùng khuôn với [rebuildAutostart], vì cùng lý do.
     */
    private var geometryHolder: LinearLayout? = null

    /** Nửa đang chỉnh khi cụm chia đôi (`null` = toàn cụm). Nhớ lại giữa hai lần dựng để không nhảy về ô trái. */
    private var geometrySide: ClusterSlotSide? = null

    /** Ba nút chọn app (full · trái · phải) — giữ để đổi CHỮ tại chỗ sau khi chọn, không dựng lại trang. */
    private val appButtons = HashMap<Slot, TextView>()

    /** Ba ô "app nào" của phần tự chiếu. Dùng làm khoá bảng trên và làm tham số cho [pickAutostartApp]. */
    private enum class Slot { FULL, LEFT, RIGHT }

    fun build(body: LinearLayout) {
        master(body)
        autostart(body)
        castNow(body)
        geometry(body)
        rescue(body)
    }

    // ── Công tắc chính + tỉ lệ chia ──────────────────────────────────────────────────────────────

    private fun master(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_cast)))
        body.addView(rows.checkRow(
            on = bridge.castEnabled(),
            title = context.getString(R.string.kachi_cast_enabled_title),
            sub = context.getString(R.string.kachi_cast_enabled_sub),
        ) { on ->
            bridge.setCastEnabled(on)
            refreshStatus()
        })
        body.addView(rows.chipRow(
            label = context.getString(R.string.kachi_cast_split),
            options = bridge.splitPctOptions().map { it.toString() to ClusterNavSettingsModel.splitRatioLabel(it) },
            current = bridge.splitPct().toString(),
        ) { code -> code.toIntOrNull()?.let { bridge.setSplitPct(it) } })
    }

    // ── Tự chiếu khi nổ máy ──────────────────────────────────────────────────────────────────────

    /**
     * Hai công tắc **loại trừ nhau** (toàn màn ⟂ chia đôi) + ba nút chọn app.
     *
     * Phép loại trừ là hàm thuần [ClusterNavSettingsModel.autostartExclusive] ở `:core` chứ không phải hai dòng
     * `if` viết tay ở đây: [ĐO] `CastAutostart.kt:32–61` có tính **bất đối xứng** dễ chép sai — bật cái này thì
     * tắt cái kia, nhưng TẮT thì không đụng gì (cả hai cùng tắt là trạng thái hợp lệ). Một bản sao viết tay sẽ
     * quên vế thứ hai, và hậu quả là hai bộ tự-chiếu cùng giành cụm (cuộc đua `SLOT_OCCUPIED` đã có thật).
     *
     * ⚠ Công tắc kia phải **vẽ lại** khi bị tắt theo — [SettingsRows.checkRow] giữ trạng thái trong closure nên
     * không có đường "đặt lại dấu tích" từ ngoài. Nên hai ô tick này nằm trong một khối con được [rebuild] dựng
     * lại, thay vì cả trang (giữ chỗ đang cuộn — xem [SettingsPanel]).
     */
    private fun autostart(body: LinearLayout) {
        body.addView(rows.subHeader(context.getString(R.string.kachi_sub_cast_autostart)))
        val holder = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body.addView(holder)
        rebuildAutostart(holder)
    }

    private fun rebuildAutostart(holder: LinearLayout) {
        holder.removeAllViews()
        appButtons.clear()
        val full = bridge.autostartFull()
        val split = bridge.autostartSplit()
        holder.addView(rows.checkRow(
            on = full,
            title = context.getString(R.string.kachi_cast_autostart_title),
            sub = context.getString(R.string.kachi_cast_autostart_sub),
        ) { on ->
            val (nextFull, nextSplit) = ClusterNavSettingsModel.autostartExclusive(
                on, split, ClusterNavSettingsModel.AutostartToggle.FULL,
            )
            bridge.setAutostartFull(nextFull)
            if (!nextSplit) bridge.setAutostartSplit(false)
            rebuildAutostart(holder)
        })
        holder.addView(appButton(Slot.FULL, R.string.kachi_cast_app_full, bridge.autostartPkg()))

        holder.addView(rows.checkRow(
            on = split,
            title = context.getString(R.string.kachi_cast_autostart_split_title),
            sub = context.getString(R.string.kachi_cast_autostart_split_sub),
        ) { on ->
            val (nextFull, nextSplit) = ClusterNavSettingsModel.autostartExclusive(
                full, on, ClusterNavSettingsModel.AutostartToggle.SPLIT,
            )
            bridge.setAutostartSplit(nextSplit)
            if (!nextFull) bridge.setAutostartFull(false)
            rebuildAutostart(holder)
        })
        holder.addView(appButton(Slot.LEFT, R.string.kachi_cast_app_left, bridge.autostartLeftPkg()))
        holder.addView(appButton(Slot.RIGHT, R.string.kachi_cast_app_right, bridge.autostartRightPkg()))
    }

    /**
     * Một nút "chọn app cho ô này" — nhãn nút **mang luôn app đang chọn** thay vì để nó ở một dòng phụ riêng.
     *
     * Lý do: ô chưa chọn app là ca hỏng im lặng của màn cũ (tự chiếu bật mà không có app ⇒ nổ máy không có gì
     * xảy ra). Nhãn nói thẳng "— chưa chọn —" thì nhìn một cái là thấy.
     */
    private fun appButton(slot: Slot, labelRes: Int, pkg: String?): View {
        val button = rows.button(appButtonText(labelRes, pkg)) { pickAutostartApp(slot, labelRes) }
        appButtons[slot] = button as TextView
        return button
    }

    private fun appButtonText(labelRes: Int, pkg: String?): String = context.getString(
        labelRes,
        pkg?.let { p -> bridge.autostartAppOptions().firstOrNull { it.second == p }?.first ?: p }
            ?: context.getString(R.string.kachi_cast_app_none),
    )

    private fun pickAutostartApp(slot: Slot, labelRes: Int) {
        val apps = bridge.autostartAppOptions()
        SettingsDialogs.pick(
            context,
            context.getString(R.string.kachi_cast_pick_app),
            apps.map { it.first },
            context.getString(R.string.kachi_cast_no_apps),
        ) { index ->
            val pkg = apps[index].second
            when (slot) {
                Slot.FULL -> bridge.setAutostartPkg(pkg)
                Slot.LEFT -> bridge.setAutostartLeftPkg(pkg)
                Slot.RIGHT -> bridge.setAutostartRightPkg(pkg)
            }
            appButtons[slot]?.text = appButtonText(labelRes, pkg)
        }
    }

    // ── Chiếu ngay ───────────────────────────────────────────────────────────────────────────────

    private fun castNow(body: LinearLayout) {
        body.addView(rows.subHeader(context.getString(R.string.kachi_sub_cast_now)))
        // Ô XEM TRƯỚC cụm — chuyển từ `hero_cast_preview` của màn cũ (đã gỡ 2026-09-13; hành vi cập nhật ở
        // `MainActivity.updateHeroStrip`). Đặt NGAY TRÊN dòng trạng thái vì hai thứ trả lời cùng một câu hỏi
        // ("cụm đang hiện cái gì") bằng hai giác quan: hình cho cái liếc, chữ cho cái đọc.
        preview = ClusterPreviewView(context).apply {
            // Màu do Kachi cấp (xem KDoc [ClusterPreviewView.setPalette]): launcher có công tắc sáng/tối RIÊNG,
            // nên bảng màu của nhánh ClusterNav vẽ ra hai mảng nhạt giữa một trang tối. Nền lấy `FIELD` để ô cụm
            // tách khỏi nền thẻ `CELL`, hai nửa lấy hai sắc nhấn của launcher.
            setPalette(
                face = KachiTheme.c(KachiTheme.FIELD), line = KachiTheme.c(KachiTheme.LINE_STRONG),
                left = KachiTheme.c(KachiTheme.ACCENT_WASH), right = KachiTheme.c(KachiTheme.ACCENT_SOFT),
                split = KachiTheme.c(KachiTheme.ACCENT), ink = KachiTheme.c(KachiTheme.MUT),
            )
        }
        body.addView(rows.embed(preview!!, Sp.EMBED_PREVIEW))
        statusRow = rows.statusRow(KachiTheme.MUT2, "")
        body.addView(statusRow.view)
        refreshStatus()
        body.addView(rows.button(context.getString(R.string.kachi_cast_now_full)) { pickAndCast(null) })
        body.addView(rows.button(context.getString(R.string.kachi_cast_now_left)) { pickAndCast(ClusterSlotSide.LEFT) })
        body.addView(rows.button(context.getString(R.string.kachi_cast_now_right)) { pickAndCast(ClusterSlotSide.RIGHT) })
        body.addView(rows.button(context.getString(R.string.kachi_cast_now_stop)) {
            bridge.castStop()
            refreshStatus()
        })
    }

    /** `side == null` ⇒ chiếu TOÀN cụm; còn lại là một nửa. Một hàm cho ba nút — không ba bản sao. */
    private fun pickAndCast(side: ClusterSlotSide?) {
        val apps = bridge.installedCastApps()
        SettingsDialogs.pick(
            context,
            context.getString(R.string.kachi_cast_pick_app),
            apps.map { it.first },
            context.getString(R.string.kachi_cast_no_apps),
        ) { index ->
            val pkg = apps[index].second
            when (side) {
                null -> bridge.castFull(pkg)
                ClusterSlotSide.LEFT -> bridge.castLeft(pkg)
                ClusterSlotSide.RIGHT -> bridge.castRight(pkg)
            }
            refreshStatus()
        }
    }

    /**
     * Dòng trạng thái chiếu — dịch `SimpleCastState` (state THÔ của `:core`) sang câu bằng tài nguyên launcher.
     *
     * `when` vét cạn trên `sealed interface` ⇒ thêm một trạng thái mới ở `:core` là **không biên dịch được**,
     * chứ không phải một dòng chữ trống trên màn xe.
     */
    private fun refreshStatus() {
        val state = bridge.castState()
        val text = when (state) {
            is SimpleCastState.Off -> context.getString(R.string.kachi_cast_state_off)
            is SimpleCastState.Opening -> context.getString(R.string.kachi_cast_state_opening)
            is SimpleCastState.Idle -> context.getString(R.string.kachi_cast_state_idle)
            is SimpleCastState.CastingFull ->
                context.getString(R.string.kachi_cast_state_full, shortName(state.targetPkg))
            is SimpleCastState.CastingSplit -> context.getString(
                R.string.kachi_cast_state_split,
                state.left?.pkg?.let { shortName(it) } ?: DASH,
                state.right?.pkg?.let { shortName(it) } ?: DASH,
            )
            is SimpleCastState.Stopping -> context.getString(R.string.kachi_cast_state_stopping)
            is SimpleCastState.Closing -> context.getString(R.string.kachi_cast_state_closing)
            is SimpleCastState.Error -> context.getString(R.string.kachi_cast_state_error, state.message)
        }
        val colour = when (state) {
            is SimpleCastState.CastingFull, is SimpleCastState.CastingSplit -> KachiTheme.GREEN
            is SimpleCastState.Opening, is SimpleCastState.Stopping, is SimpleCastState.Closing -> KachiTheme.AMBER
            is SimpleCastState.Idle -> KachiTheme.AMBER
            is SimpleCastState.Error -> KachiTheme.RED
            else -> KachiTheme.MUT2
        }
        statusRow.update(colour, text)
        refreshPreview(state)
        rebuildGeometry()
    }

    /**
     * Vẽ lại ô xem trước — lặp lại `MainActivity.updateHeroStrip` (đã gỡ): ô **không bao giờ trơn**. Chưa chiếu
     * thì vẫn vẽ dải chia mờ ([IDLE_PREVIEW_FRACTION], không nhãn) như bản cũ, vì một hình chữ nhật rỗng không
     * nói được gì về cụm; đang chiếu TOÀN cụm thì là một mặt liền mang tên app — bản cũ vẽ dải chia cả ở ca này,
     * tức nó vẽ SAI cái đang có thật trên cụm.
     *
     * Khác bản cũ đúng một chỗ, có chủ ý: nhãn ghép từ **tên gói thật** đang chiếu chứ không phải chuỗi cứng
     * "GMaps · VietMap" — [ĐO] `MainActivity.kt:529` ghi sẵn hai cái tên đó bất kể đang chiếu app nào, nên trên
     * xe nó nói sai mỗi khi người dùng chiếu cặp khác.
     */
    private fun refreshPreview(state: SimpleCastState) {
        val view = preview ?: return
        when (state) {
            is SimpleCastState.CastingSplit -> {
                val leftPct = bridge.splitPct()
                view.setSplit(
                    leftPct / 100f,
                    context.getString(
                        R.string.kachi_cast_preview_split,
                        state.left?.pkg?.let { shortName(it) } ?: DASH,
                        state.right?.pkg?.let { shortName(it) } ?: DASH,
                        leftPct, 100 - leftPct,
                    ),
                )
            }
            is SimpleCastState.CastingFull -> view.setFull(shortName(state.targetPkg))
            else -> view.setSplit(IDLE_PREVIEW_FRACTION, null)
        }
    }

    // ── Khung và DPI của ô đang chiếu (R2a) ──────────────────────────────────────────────────────

    /**
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
     */
    private fun geometry(body: LinearLayout) {
        val holder = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        geometryHolder = holder
        body.addView(holder)
        rebuildGeometry()
    }

    private fun rebuildGeometry() {
        val holder = geometryHolder ?: return
        holder.removeAllViews()
        val targets = bridge.geometryTargets()
        if (targets.isEmpty()) return
        val target = targets.firstOrNull { it.side == geometrySide } ?: targets.first()
        geometrySide = target.side

        holder.addView(rows.subHeader(context.getString(R.string.kachi_sub_cast_geometry)))
        if (targets.size > 1) {
            holder.addView(rows.chipRow(
                label = context.getString(R.string.kachi_cast_geom_slot),
                options = targets.map { it.side!!.name to slotLabel(it.side) },
                current = target.side?.name.orEmpty(),
            ) { code ->
                geometrySide = ClusterSlotSide.values().firstOrNull { it.name == code }
                rebuildGeometry()
            })
        }
        addEdgeSteppers(holder, target)
        holder.addView(rows.chipRow(
            label = context.getString(R.string.kachi_cast_geom_dpi),
            options = bridge.geometryDensityOptions().map { it.toString() to it.toString() },
            current = bridge.geometryDensity(target).toString(),
        ) { code -> code.toIntOrNull()?.let { bridge.setGeometryDensity(it) } })
        holder.addView(rows.note(context.getString(R.string.kachi_cast_geom_dpi_note)))
        holder.addView(rows.button(context.getString(R.string.kachi_cast_geom_reset)) {
            bridge.resetGeometry(target)
            rebuildGeometry()
        })
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

    /** Tên ngắn của app đang chiếu — `substringAfterLast('.')`, **y như màn cũ** để hai màn nói cùng một tên. */
    private fun shortName(pkg: String): String = pkg.substringAfterLast('.')

    // ── Cứu hộ ───────────────────────────────────────────────────────────────────────────────────

    /**
     * Ba đường thoát, xếp theo mức **nặng dần**: trả cụm về đồng hồ (mở lại chiếu sau 2 s) → dọn sạch cụm
     * (force-stop app tranh chấp + reset VD, KHÔNG mở lại) → chẩn đoán.
     *
     * ⚠ "Dọn sạch cụm" **hỏi lại**: nó force-stop app của người khác và reset cấu hình VD — [ĐO]
     * `CastDeepRescueAction` cũng hỏi lại ở màn cũ, và đây là loại việc mà một cú chạm nhầm trên màn xe không
     * hoàn lại được (CLAUDE.md §4: "hoàn tác kiểu gì nếu nửa chừng hỏng?").
     */
    private fun rescue(body: LinearLayout) {
        body.addView(rows.subHeader(context.getString(R.string.kachi_sub_cast_rescue)))
        body.addView(rows.button(context.getString(R.string.kachi_cast_restore)) {
            bridge.restoreCluster()
            refreshStatus()
        })
        body.addView(rows.button(context.getString(R.string.kachi_cast_deep)) {
            bridge.deepRescue(
                onConfirm = { proceed ->
                    SettingsDialogs.confirm(
                        context,
                        context.getString(R.string.kachi_cast_deep),
                        context.getString(R.string.kachi_cast_deep_confirm),
                        context.getString(R.string.kachi_cast_deep_ok),
                        proceed,
                    )
                },
                onDone = { stopped ->
                    val who = if (stopped.isEmpty()) context.getString(R.string.kachi_cast_deep_none)
                    else stopped.joinToString(", ")
                    Toast.makeText(
                        context, context.getString(R.string.kachi_cast_deep_done, who), Toast.LENGTH_LONG,
                    ).show()
                    refreshStatus()
                },
            )
        })
        body.addView(rows.button(context.getString(R.string.kachi_diagnostics)) { bridge.openDiagnostics() })
    }

    private companion object {
        /** Ô chia đôi còn trống — cùng ký hiệu với mọi chỗ "chưa có số" của launcher. */
        const val DASH = "—"

        /**
         * Tỉ lệ của dải chia khi **chưa** chiếu — y như màn cũ (`MainActivity.kt:531`: `setSplit(0.4f, null)`).
         * Không phải 0.5: một ô chia đôi cân đối trông như một trạng thái THẬT, còn lệch thì đọc ra là "mẫu".
         */
        const val IDLE_PREVIEW_FRACTION = 0.4f
    }
}
