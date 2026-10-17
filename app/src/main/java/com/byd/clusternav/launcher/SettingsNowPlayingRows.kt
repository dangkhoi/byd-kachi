package com.byd.clusternav.launcher

import android.content.Context
import android.widget.LinearLayout
import com.byd.clusternav.R
import com.byd.clusternav.modules.clustercast.nowplaying.CastNowPlayingCorner

/**
 * 3.1.12 · CAST-NOW-PLAYING — công tắc *"Tên bài trên cụm khi đang chiếu"* + dãy chip GÓC ở *Cài đặt › Dẫn đường & cụm đồng hồ*
 * (cạnh badge tốc độ — cùng họ lớp phủ cụm). Thay hàng 3.1.11 (bộ ghi nguồn 11, đã gỡ). Tệp riêng: `SettingsSectionsNav` sát
 * trần 500 dòng. Cả hai chỉ ghi prefs qua cầu; lớp phủ tự nghe đổi khoá.
 */
internal class SettingsNowPlayingRows(
    private val context: Context,
    private val rows: SettingsRows,
    private val bridge: () -> ClusterNavBridge,
) {
    fun build(body: LinearLayout) {
        body.addView(rows.checkRow(
            on = bridge().castNowPlayingEnabled(),
            title = context.getString(R.string.kachi_np_title),
            sub = context.getString(R.string.kachi_np_sub),
        ) { on -> bridge().setCastNowPlayingEnabled(on) })
        val labels = mapOf(
            CastNowPlayingCorner.TOP_LEFT to R.string.kachi_np_corner_tl,
            CastNowPlayingCorner.TOP_RIGHT to R.string.kachi_np_corner_tr,
            CastNowPlayingCorner.BOTTOM_LEFT to R.string.kachi_np_corner_bl,
            CastNowPlayingCorner.BOTTOM_RIGHT to R.string.kachi_np_corner_br,
        )
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_np_corner),
            CastNowPlayingCorner.values().map { it.key to context.getString(labels.getValue(it)) },
            bridge().castNowPlayingCorner().key,
            wrap = true,
        ) { key -> bridge().setCastNowPlayingCorner(CastNowPlayingCorner.parse(key)) })
    }
}
