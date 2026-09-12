package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.byd.clusternav.R
import com.byd.clusternav.modules.clustercast.simplified.ClusterSlotSide
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState

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

    /** Ba nút chọn app (full · trái · phải) — giữ để đổi CHỮ tại chỗ sau khi chọn, không dựng lại trang. */
    private val appButtons = HashMap<Slot, TextView>()

    /** Ba ô "app nào" của phần tự chiếu. Dùng làm khoá bảng trên và làm tham số cho [pickAutostartApp]. */
    private enum class Slot { FULL, LEFT, RIGHT }

    fun build(body: LinearLayout) {
        master(body)
        autostart(body)
        castNow(body)
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
    }

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
    }
}
