package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.byd.clusternav.R
import com.byd.clusternav.modules.clustercast.nowplaying.NowPlayingStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 3.1.11 · CLUSTER-NOW-PLAYING — công tắc + MỘT dòng tình trạng (lần ghi cuối, hoặc vì sao chưa chạy) ở *Cài đặt › Dẫn đường
 * & cụm đồng hồ*. Tệp riêng: `SettingsSectionsNav` đã sát trần 500 dòng. Dòng tình trạng đọc lại mỗi lần trang hiện (cùng móc
 * `onViewAttachedToWindow` như `SettingsNavHudRows`) và một lần ngay sau cú gạt (luồng `kachi-np` chạy bất đồng bộ).
 */
internal class SettingsNowPlayingRows(
    private val context: Context,
    private val rows: SettingsRows,
    private val bridge: () -> ClusterNavBridge,
) {
    private lateinit var statusRow: SettingsRows.StatusRow

    fun build(body: LinearLayout) {
        body.addView(rows.checkRow(
            on = bridge().nowPlayingEnabled(),
            title = context.getString(R.string.kachi_np_title),
            sub = context.getString(R.string.kachi_np_sub),
        ) { on ->
            bridge().setNowPlayingEnabled(on)
            statusRow.view.postDelayed({ refresh() }, REFRESH_AFTER_TOGGLE_MS)
        })
        statusRow = rows.statusRow(KachiTheme.MUT2, "")
        body.addView(statusRow.view)
        statusRow.view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = refresh()
            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        refresh()
    }

    fun refresh() {
        val s = bridge().nowPlayingStatus()
        val at = clock(s.atMs)
        val d = s.detail ?: "-"
        val (colour, text) = when (s.kind) {
            NowPlayingStatus.Kind.OFF -> KachiTheme.MUT2 to str(R.string.kachi_np_st_off)
            NowPlayingStatus.Kind.UNSUPPORTED -> KachiTheme.AMBER to str(R.string.kachi_np_st_unsupported)
            NowPlayingStatus.Kind.NO_ACCESS -> KachiTheme.AMBER to str(R.string.kachi_np_st_no_access)
            NowPlayingStatus.Kind.NO_MEDIA -> KachiTheme.MUT2 to str(R.string.kachi_np_st_no_media)
            NowPlayingStatus.Kind.SYSTEM_APP -> KachiTheme.MUT2 to str(R.string.kachi_np_st_system_app)
            NowPlayingStatus.Kind.NO_TITLE -> KachiTheme.MUT2 to str(R.string.kachi_np_st_no_title)
            NowPlayingStatus.Kind.PAUSED -> KachiTheme.MUT2 to str(R.string.kachi_np_st_paused)
            NowPlayingStatus.Kind.POWER_OFF -> KachiTheme.MUT2 to str(R.string.kachi_np_st_power_off)
            NowPlayingStatus.Kind.ALREADY_SHOWN -> KachiTheme.GREEN to context.getString(R.string.kachi_np_st_already_shown, d)
            NowPlayingStatus.Kind.MARKER_FAILED -> KachiTheme.RED to str(R.string.kachi_np_st_marker_failed)
            NowPlayingStatus.Kind.ACTIVE -> KachiTheme.GREEN to context.getString(R.string.kachi_np_st_active, at, d)
            NowPlayingStatus.Kind.RESTORED -> KachiTheme.MUT2 to context.getString(R.string.kachi_np_st_restored, at, d)
        }
        statusRow.update(colour, text)
    }

    private fun str(id: Int): String = context.getString(id)

    /** [NowPlayingStatus.atMs] là `elapsedRealtime` ⇒ đổi sang giờ tường để hiển thị. */
    private fun clock(elapsedMs: Long): String {
        if (elapsedMs <= 0L) return "—"
        val wall = System.currentTimeMillis() - (android.os.SystemClock.elapsedRealtime() - elapsedMs)
        return CLOCK.format(Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault()))
    }

    private companion object {
        val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        const val REFRESH_AFTER_TOGGLE_MS = 600L
    }
}
