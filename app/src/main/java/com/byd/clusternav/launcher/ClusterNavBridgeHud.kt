package com.byd.clusternav.launcher

import android.os.SystemClock
import com.byd.clusternav.NavNotificationListener
import com.byd.clusternav.NlsHeal
import com.byd.clusternav.Prefs
import com.byd.clusternav.navigation.HudWriteRecord
import com.byd.clusternav.navigation.NavHudStatus

/**
 * ═══ FIX286 R-HUD (S4) — dữ liệu THÔ của dòng tình trạng HUD ở *Cài đặt › Dẫn đường* ════════════════════════════════
 *
 * Tách tệp theo trần 500 dòng (cùng khuôn `ClusterNavBridgeCast.kt` / `ClusterNavBridgeSystem.kt`). Cầu trả dữ liệu,
 * không trả câu (hợp đồng [ClusterNavBridge]); câu ghép ở `SettingsNavHudRows` bằng tài nguyên.
 */
internal data class NavHudStatusView(
    val source: NavHudStatus.Source,
    /** Giờ tường để hiển thị: của lượt đọc dump (LIVE/NOT_LIVE) hoặc của callback (BOUND_IN_PROCESS); `0` = không có. */
    val sourceAtWallMs: Long,
    /** Lượt HÀNH ĐỘNG gần nhất (tự động / công tắc / nút) — không phải lượt chỉ-đọc. */
    val last: NlsHeal.Last?,
    val lastWrite: HudWriteRecord.Write?,
    val navApp: NavSourceView?,
)

internal fun ClusterNavBridge.navHudStatus(): NavHudStatusView {
    val connectedAtWall = NavNotificationListener.connectedAtElapsed.let { at ->
        if (at <= 0L) 0L else System.currentTimeMillis() - (SystemClock.elapsedRealtime() - at)
    }
    val truth = NlsHeal.lastTruth
    val source = NavHudStatus.source(
        navEnabled = Prefs.enabled(app),
        granted = notificationAccessGranted(),
        boundInProcess = NavNotificationListener.connected,
        connectedAtWallMs = connectedAtWall,
        truth = truth?.verdict,
        truthAtWallMs = truth?.wallMs ?: 0L,
    )
    val at = when (source) {
        NavHudStatus.Source.LIVE, NavHudStatus.Source.NOT_LIVE, NavHudStatus.Source.UNREADABLE -> truth?.wallMs ?: 0L
        NavHudStatus.Source.BOUND_IN_PROCESS -> connectedAtWall
        else -> 0L
    }
    return NavHudStatusView(source, at, NlsHeal.lastAction, HudWriteRecord.last, navSource())
}

/** Đọc sự thật NMS lúc trang hiện (chỉ đọc; cổng công tắc + kênh ở `NlsHeal.readForSettings`). [onDone] trên luồng vẽ. */
internal fun ClusterNavBridge.readNavHudTruth(onDone: () -> Unit) = NlsHeal.readForSettings(app) { ui(Runnable(onDone)) }
