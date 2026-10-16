package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.ClusterNowPlaying
import com.byd.clusternav.modules.clustercast.nowplaying.NowPlayingStatus

/**
 * 3.1.11 · CLUSTER-NOW-PLAYING — công tắc *"Tên bài hát lên cụm đồng hồ"* (`cluster_now_playing`, theo XE, mặc định TẮT).
 * Cầu trả dữ liệu, không trả câu (hợp đồng [ClusterNavBridge]); câu ghép ở `SettingsNowPlayingRows`.
 */
fun ClusterNavBridge.nowPlayingEnabled(): Boolean = ClusterNowPlaying.enabled(app)

/** Bật/tắt có hiệu lực ngay: bật ⇒ gắn bộ nghe phiên; tắt ⇒ trả nguồn cụm + gỡ bộ nghe. */
fun ClusterNavBridge.setNowPlayingEnabled(on: Boolean) = ClusterNowPlaying.setEnabled(app, on)

fun ClusterNavBridge.nowPlayingStatus(): NowPlayingStatus = ClusterNowPlaying.status(app)

/**
 * Mặt tiền cho bên KHÔNG cầm [ClusterNavBridge] (cầu kiểm thử `np` — chạy cả khi màn chính chưa lên). Cùng một đường
 * ([ClusterNowPlaying]) với công tắc ở Cài đặt; trả dữ liệu thô ASCII cho lời đáp JSON.
 */
object NowPlayingControl {
    fun enabled(ctx: android.content.Context): Boolean = ClusterNowPlaying.enabled(ctx)

    fun setEnabled(ctx: android.content.Context, on: Boolean) = ClusterNowPlaying.setEnabled(ctx, on)

    /** `kind` · `detail` (rc, không tên bài) · `at_ms` (elapsedRealtime). */
    fun statusFields(ctx: android.content.Context): List<Pair<String, Any?>> {
        val s: NowPlayingStatus = ClusterNowPlaying.status(ctx)
        return listOf("kind" to s.kind.name, "detail" to s.detail, "at_ms" to s.atMs)
    }
}
