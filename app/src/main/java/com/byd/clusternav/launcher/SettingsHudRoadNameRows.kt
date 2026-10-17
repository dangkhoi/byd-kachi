package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.byd.clusternav.R
import com.byd.clusternav.navigation.ClusterDebugRoadName.Availability
import com.byd.clusternav.navigation.HudRoadNameMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 3.1.12 · HUD-ROADNAME-ALT-PATH — hàng *"Đường gửi tên đường lên HUD"* (A · A48 · B · C) + MỘT dòng tình trạng + hai câu giải
 * thích, ở *Cài đặt › Dẫn đường + HUD*. Tệp riêng: `SettingsSectionsNav` sát trần 500 dòng.
 *
 * B/C cần receiver ClusterDebug hệ thống (đọc PackageManager MỚI mỗi lần trang hiện): không có ⇒ hai chip đó mờ + không bấm được,
 * và dòng lý do nói vì sao. Đường ghi vẫn tự từ chối B/C ([ClusterNavBridge.setHudRoadNameMode]) — chip mờ chỉ là lớp UI.
 */
internal class SettingsHudRoadNameRows(
    private val context: Context,
    private val rows: SettingsRows,
    private val bridge: () -> ClusterNavBridge,
) {
    private lateinit var statusRow: SettingsRows.StatusRow
    private lateinit var availRow: SettingsRows.StatusRow
    private var chips: View? = null

    private val modes = HudRoadNameMode.values().toList()

    fun build(body: LinearLayout) {
        chips = rows.chipRow(
            label = context.getString(R.string.kachi_hudroad_title),
            options = modes.map { it.code to label(it) },
            current = bridge().hudRoadNameMode().code,
        ) { code ->
            val mode = HudRoadNameMode.of(code) ?: return@chipRow
            bridge().setHudRoadNameMode(mode)?.let { why ->
                Toast.makeText(context, context.getString(R.string.kachi_hudroad_refused, reason(why)), Toast.LENGTH_LONG).show()
            }
            refresh()
        }
        body.addView(chips)
        availRow = rows.statusRow(KachiTheme.MUT2, "")
        statusRow = rows.statusRow(KachiTheme.MUT2, "")
        body.addView(availRow.view)
        body.addView(statusRow.view)
        body.addView(rows.note(context.getString(R.string.kachi_hudroad_modes)))
        body.addView(rows.note(context.getString(R.string.kachi_hudroad_help_sl6)))
        statusRow.view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = refresh()
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        refresh()
    }

    fun refresh() {
        val avail = bridge().hudRoadNameAvailability()
        gateChips(avail == Availability.OK)
        availRow.update(
            if (avail == Availability.OK) KachiTheme.GREEN else KachiTheme.MUT2,
            if (avail == Availability.OK) str(R.string.kachi_hudroad_cd_ok)
            else context.getString(R.string.kachi_hudroad_cd_off, reason(avail)),
        )
        val s = bridge().hudRoadNameStatus()
        val degraded = s.mode.clusterDebug && avail != Availability.OK
        val (colour, text) = when {
            degraded -> KachiTheme.AMBER to context.getString(R.string.kachi_hudroad_st_degraded, label(s.mode))
            s.lastAtMs <= 0L -> KachiTheme.MUT2 to context.getString(R.string.kachi_hudroad_st_idle, label(s.mode))
            else -> KachiTheme.GREEN to context.getString(
                R.string.kachi_hudroad_st_sent, label(s.lastMode ?: s.mode), clock(s.lastAtMs), s.lastUnits, s.lastRc ?: "-",
            )
        }
        statusRow.update(colour, text)
    }

    /**
     * Chip B/C mờ + bỏ bấm khi không dùng được. [SettingsRows.chipRow] (`wrap = false`) dựng nhãn ở con 0 rồi chip theo đúng
     * thứ tự `options` — sai khuôn đó thì bỏ qua lớp mờ (đường ghi vẫn từ chối, không có gì hỏng).
     */
    private fun gateChips(cdOk: Boolean) {
        val row = chips as? ViewGroup ?: return
        if (row.childCount != modes.size + 1) return
        modes.forEachIndexed { i, mode ->
            if (!mode.clusterDebug) return@forEachIndexed
            val chip = row.getChildAt(i + 1)
            chip.alpha = if (cdOk) 1f else GATE_ALPHA
            chip.isEnabled = cdOk
        }
    }

    private fun label(mode: HudRoadNameMode): String = str(
        when (mode) {
            HudRoadNameMode.A -> R.string.kachi_hudroad_a
            HudRoadNameMode.A48 -> R.string.kachi_hudroad_a48
            HudRoadNameMode.B -> R.string.kachi_hudroad_b
            HudRoadNameMode.C -> R.string.kachi_hudroad_c
        },
    )

    private fun reason(a: Availability): String = str(
        when (a) {
            Availability.OK -> R.string.kachi_hudroad_cd_ok
            Availability.NOT_INSTALLED -> R.string.kachi_hudroad_why_missing
            Availability.NOT_SYSTEM -> R.string.kachi_hudroad_why_not_system
            Availability.DISABLED -> R.string.kachi_hudroad_why_disabled
            Availability.NO_RECEIVER -> R.string.kachi_hudroad_why_no_receiver
            Availability.PROTECTED -> R.string.kachi_hudroad_why_protected
            Availability.UNKNOWN_VERSION -> R.string.kachi_hudroad_why_version
        },
    )

    private fun str(id: Int): String = context.getString(id)

    /** `lastAtMs` là `elapsedRealtime` ⇒ đổi sang giờ tường để hiển thị. */
    private fun clock(elapsedMs: Long): String {
        val wall = System.currentTimeMillis() - (android.os.SystemClock.elapsedRealtime() - elapsedMs)
        return CLOCK.format(Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault()))
    }

    private companion object {
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        const val GATE_ALPHA = 0.4f
    }
}
