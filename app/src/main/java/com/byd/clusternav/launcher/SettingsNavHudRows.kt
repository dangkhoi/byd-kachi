package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import com.byd.clusternav.NlsHeal
import com.byd.clusternav.R
import com.byd.clusternav.navigation.HudWriteRecord
import com.byd.clusternav.navigation.HudWriteSummary
import com.byd.clusternav.navigation.NavHudStatus
import com.byd.clusternav.navigation.NlsHealPolicy.Outcome
import com.byd.clusternav.navigation.NlsHealPolicy.Trigger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ═══ FIX286 R-HUD (S4) — ba dòng tình trạng HUD dưới công tắc *Dẫn đường lên cụm đồng hồ* ═══════════════════════════
 *
 * Câu hỏi anh em phải trả lời được bằng MỘT ảnh chụp màn (CLAUDE.md §11), TRƯỚC khi gạt gì:
 *  1. nguồn thông báo (nguồn duy nhất của HUD) có đang GẮN không — theo sự thật NMS, đọc lúc trang HIỆN;
 *  2. Kachi ghi khung tới xe lần cuối lúc nào, HAL nhận bao nhiêu mục, đang dẫn bằng app nào;
 *  3. lượt gắn lại gần nhất (tự động / công tắc / nút) ra kết quả gì — không còn "xong" khi hỏng (S2).
 * Thêm một câu cố định: HUD chỉ nhận Google Maps (VietMap/Waze không lên HUD — `NavApps.NOTIFICATION`).
 *
 * Trang Cài đặt được NHỚ LẠI (`SettingsPanel.pages`) ⇒ "đọc lúc mở trang" = móc `onViewAttachedToWindow` của dòng đầu
 * (mỗi lần trang được gắn lại vào màn) — gọi [onShown] của chỗ dựng (làm tươi mọi dòng trạng thái của mục).
 */
internal class SettingsNavHudRows(
    private val context: Context,
    private val rows: SettingsRows,
    private val bridge: () -> ClusterNavBridge,
) {
    private lateinit var sourceRow: SettingsRows.StatusRow
    private lateinit var writeRow: SettingsRows.StatusRow
    private lateinit var lastRow: SettingsRows.StatusRow

    fun build(body: LinearLayout, onShown: () -> Unit) {
        sourceRow = rows.statusRow(KachiTheme.MUT2, "")
        writeRow = rows.statusRow(KachiTheme.MUT2, "")
        lastRow = rows.statusRow(KachiTheme.MUT2, "")
        body.addView(sourceRow.view)
        body.addView(writeRow.view)
        body.addView(lastRow.view)
        body.addView(rows.note(context.getString(R.string.kachi_nav_hud_note)))
        sourceRow.view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                onShown()
                bridge().readNavHudTruth { refresh() }
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        refresh()
    }

    fun refresh() {
        val s = bridge().navHudStatus()
        val at = clock(s.sourceAtWallMs)
        when (s.source) {
            NavHudStatus.Source.OFF -> sourceRow.update(KachiTheme.MUT2, str(R.string.kachi_nav_hud_off))
            NavHudStatus.Source.NO_ACCESS -> sourceRow.update(KachiTheme.AMBER, str(R.string.kachi_nav_hud_no_access))
            NavHudStatus.Source.LIVE -> sourceRow.update(KachiTheme.GREEN, context.getString(R.string.kachi_nav_hud_live, at))
            NavHudStatus.Source.NOT_LIVE -> sourceRow.update(KachiTheme.RED, context.getString(R.string.kachi_nav_hud_not_live, at))
            NavHudStatus.Source.BOUND_IN_PROCESS ->
                sourceRow.update(KachiTheme.GREEN, context.getString(R.string.kachi_nav_hud_bound, at))
            NavHudStatus.Source.UNREADABLE ->
                sourceRow.update(KachiTheme.AMBER, context.getString(R.string.kachi_nav_hud_unreadable, at))
            NavHudStatus.Source.UNKNOWN -> sourceRow.update(KachiTheme.AMBER, str(R.string.kachi_nav_hud_unknown))
        }
        writeRow.update(writeColour(s.lastWrite), writeText(s.lastWrite, s.navApp))
        val last = s.last
        if (last == null) {
            lastRow.update(KachiTheme.MUT2, str(R.string.kachi_nav_hud_last_none))
        } else {
            lastRow.update(
                if (last.outcome.ok) KachiTheme.GREEN else KachiTheme.AMBER,
                context.getString(R.string.kachi_nav_hud_last, clock(last.wallMs), triggerText(last.trigger), outcomeText(last.outcome)),
            )
        }
    }

    /**
     * Kết quả THẬT của lượt bấm vừa xong (công tắc / *Kết nối lại*) — một câu, không còn "xong" khi hỏng. Chỉ lượt bắt đầu
     * từ [sinceWallMs] trở đi (đường cấp quyền `selfGrant` không ghi `NlsHeal.lastAction` ⇒ không nói lại kết quả CŨ).
     */
    fun toastUserResultSince(sinceWallMs: Long) {
        val last = NlsHeal.lastAction ?: return
        if (last.wallMs < sinceWallMs || (last.trigger != Trigger.SWITCH && last.trigger != Trigger.BUTTON)) return
        Toast.makeText(context, context.getString(R.string.kachi_nav_hud_toast, outcomeText(last.outcome)), Toast.LENGTH_LONG).show()
    }

    private fun writeText(w: HudWriteRecord.Write?, app: NavSourceView?): String {
        if (w == null) return str(R.string.kachi_nav_hud_write_none)
        val c = HudWriteSummary.of(w.rc)
        if (c.noDevice) return context.getString(R.string.kachi_nav_hud_write_nodev, clock(w.wallMs))
        return context.getString(
            R.string.kachi_nav_hud_write, clock(w.wallMs), kindText(w.kind), c.ok, c.rejected, c.skipped,
            app?.brand ?: str(R.string.kachi_nav_hud_app_none),
        )
    }

    private fun writeColour(w: HudWriteRecord.Write?): String = when {
        w == null -> KachiTheme.MUT2
        HudWriteSummary.of(w.rc).let { it.noDevice || it.ok == 0 } -> KachiTheme.RED
        else -> KachiTheme.GREEN
    }

    private fun kindText(k: HudWriteRecord.Kind): String = str(
        when (k) {
            HudWriteRecord.Kind.GUIDE -> R.string.kachi_nav_hud_kind_guide
            HudWriteRecord.Kind.KEEPALIVE -> R.string.kachi_nav_hud_kind_keepalive
            HudWriteRecord.Kind.CLEAR -> R.string.kachi_nav_hud_kind_clear
            HudWriteRecord.Kind.MODE_OFF -> R.string.kachi_nav_hud_kind_mode_off
        },
    )

    private fun triggerText(t: Trigger): String = str(
        when (t) {
            Trigger.READY, Trigger.WATCHDOG -> R.string.kachi_nav_hud_trig_auto
            Trigger.SWITCH -> R.string.kachi_nav_hud_trig_switch
            Trigger.BUTTON -> R.string.kachi_nav_hud_trig_button
            Trigger.SETTINGS_READ -> R.string.kachi_nav_hud_trig_read
        },
    )

    private fun outcomeText(o: Outcome): String = str(
        when (o) {
            Outcome.LIVE_ALREADY -> R.string.kachi_nav_hud_o_live_already
            Outcome.BOUND_IN_PROCESS -> R.string.kachi_nav_hud_o_bound
            Outcome.HEALED -> R.string.kachi_nav_hud_o_healed
            Outcome.STILL_NOT_LIVE -> R.string.kachi_nav_hud_o_still
            Outcome.NOT_LIVE -> R.string.kachi_nav_hud_o_not_live
            Outcome.UNREADABLE -> R.string.kachi_nav_hud_o_unreadable
            Outcome.SKIPPED -> R.string.kachi_nav_hud_o_skipped
            Outcome.NO_SHELL -> R.string.kachi_nav_hud_o_no_shell
            Outcome.SHELL_FAILED -> R.string.kachi_nav_hud_o_shell_failed
            Outcome.BUSY -> R.string.kachi_nav_hud_o_busy
        },
    )

    private fun str(id: Int): String = context.getString(id)

    private fun clock(wallMs: Long): String =
        if (wallMs <= 0L) "—" else CLOCK.format(Instant.ofEpochMilli(wallMs).atZone(ZoneId.systemDefault()))

    private companion object {
        /** Giờ:phút:giây — mẫu định dạng, không phải chữ hiển thị (đồng hồ 24 giờ như thanh trên). */
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
