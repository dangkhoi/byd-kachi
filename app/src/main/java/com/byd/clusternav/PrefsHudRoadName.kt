package com.byd.clusternav

import android.content.Context
import com.byd.clusternav.navigation.HudRoadNameKeys
import com.byd.clusternav.navigation.HudRoadNameMode

/**
 * 3.1.12 · HUD-ROADNAME-ALT-PATH — "Đường gửi tên đường lên HUD", hàm mở rộng của [Prefs] trên CÙNG tệp `clusternav_prefs`
 * ([Prefs.sp]), cùng khuôn `PrefsBadge.kt`. Khoá + phạm vi (THEO XE) ở [HudRoadNameKeys]. Mã lạ/vắng ⇒ [HudRoadNameMode.DEFAULT].
 */
fun Prefs.hudRoadNameMode(ctx: Context): HudRoadNameMode =
    HudRoadNameMode.of(sp(ctx).getString(HudRoadNameKeys.MODE_KEY, null)) ?: HudRoadNameMode.DEFAULT

fun Prefs.setHudRoadNameMode(ctx: Context, mode: HudRoadNameMode) =
    sp(ctx).edit().putString(HudRoadNameKeys.MODE_KEY, mode.code).apply()
