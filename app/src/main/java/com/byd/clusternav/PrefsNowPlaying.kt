package com.byd.clusternav

import android.annotation.SuppressLint
import android.content.Context
import com.byd.clusternav.modules.clustercast.nowplaying.ClusterNowPlayingKeys

/**
 * 3.1.11 · CLUSTER-NOW-PLAYING — công tắc + dấu bền, hàm mở rộng của [Prefs] trên CÙNG tệp `clusternav_prefs` ([Prefs.sp]),
 * cùng khuôn `PrefsBadge.kt`. Khoá + phạm vi (THEO XE) ở [ClusterNowPlayingKeys].
 */
fun Prefs.nowPlayingEnabled(ctx: Context): Boolean =
    sp(ctx).getBoolean(ClusterNowPlayingKeys.SWITCH_KEY, ClusterNowPlayingKeys.DEFAULT_ON)

fun Prefs.setNowPlayingEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(ClusterNowPlayingKeys.SWITCH_KEY, v).apply()

fun Prefs.nowPlayingMarker(ctx: Context): String? = sp(ctx).getString(ClusterNowPlayingKeys.MARKER_KEY, null)

/** `commit()` ĐỒNG BỘ có chủ ý: dấu phải nằm trên đĩa TRƯỚC lần ghi đè đầu (CLAUDE.md §5) — BYD giết Kachi lúc tắt máy. */
@SuppressLint("ApplySharedPref")
fun Prefs.saveNowPlayingMarker(ctx: Context, v: String): Boolean =
    sp(ctx).edit().putString(ClusterNowPlayingKeys.MARKER_KEY, v).commit()

@SuppressLint("ApplySharedPref")
fun Prefs.clearNowPlayingMarker(ctx: Context) {
    sp(ctx).edit().remove(ClusterNowPlayingKeys.MARKER_KEY).commit()
}
