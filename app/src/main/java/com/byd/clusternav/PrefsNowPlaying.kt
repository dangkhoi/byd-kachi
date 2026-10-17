package com.byd.clusternav

import android.annotation.SuppressLint
import android.content.Context
import com.byd.clusternav.modules.clustercast.nowplaying.ClusterNowPlayingKeys

/**
 * 3.1.12 — khoá ĐỜI CŨ của bộ ghi nguồn 11 (3.1.11, đã gỡ): chỉ còn đọc dấu bền + xoá, cho lượt dọn lúc khởi động
 * (`ClusterNowPlayingLegacyRestore`). Cùng tệp `clusternav_prefs` ([Prefs.sp]). Gỡ cùng `ClusterNowPlayingLegacy` ở 3.1.13.
 */
fun Prefs.legacyNowPlayingMarker(ctx: Context): String? = sp(ctx).getString(ClusterNowPlayingKeys.MARKER_KEY, null)

/** Có còn công tắc 3.1.11 trong prefs không (để không ghi đĩa khi không có gì để xoá). */
fun Prefs.hasLegacyNowPlayingSwitch(ctx: Context): Boolean = sp(ctx).contains(ClusterNowPlayingKeys.SWITCH_KEY)

fun Prefs.removeLegacyNowPlayingSwitch(ctx: Context) = sp(ctx).edit().remove(ClusterNowPlayingKeys.SWITCH_KEY).apply()

/** `commit()` ĐỒNG BỘ: dấu chỉ được xoá SAU lượt trả nguồn, và phải chắc đã xoá trước khi tiến trình có thể bị giết. */
@SuppressLint("ApplySharedPref")
fun Prefs.clearLegacyNowPlayingMarker(ctx: Context) {
    sp(ctx).edit().remove(ClusterNowPlayingKeys.MARKER_KEY).commit()
}
