package com.byd.clusternav.launcher

import com.byd.clusternav.Prefs
import com.byd.clusternav.castNowPlayingCorner
import com.byd.clusternav.castNowPlayingEnabled
import com.byd.clusternav.modules.clustercast.nowplaying.CastNowPlayingCorner
import com.byd.clusternav.setCastNowPlayingCorner
import com.byd.clusternav.setCastNowPlayingEnabled

/**
 * 3.1.12 · CAST-NOW-PLAYING — công tắc *"Tên bài trên cụm khi đang chiếu"* (`cast_now_playing`, theo HỒ SƠ, mặc định BẬT) + góc
 * (`cast_now_playing_corner`). Cầu trả dữ liệu, không trả câu (hợp đồng [ClusterNavBridge]); câu ghép ở `SettingsNowPlayingRows`.
 * Chỉ ghi prefs: `CastNowPlayingOverlay` nghe đổi khoá (cùng một đường với đổi hồ sơ) ⇒ có hiệu lực ngay.
 */
fun ClusterNavBridge.castNowPlayingEnabled(): Boolean = Prefs.castNowPlayingEnabled(app)

fun ClusterNavBridge.setCastNowPlayingEnabled(on: Boolean) = Prefs.setCastNowPlayingEnabled(app, on)

fun ClusterNavBridge.castNowPlayingCorner(): CastNowPlayingCorner = Prefs.castNowPlayingCorner(app)

fun ClusterNavBridge.setCastNowPlayingCorner(c: CastNowPlayingCorner) = Prefs.setCastNowPlayingCorner(app, c)
