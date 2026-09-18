package com.byd.clusternav.launcher

import com.byd.clusternav.Prefs
import com.byd.clusternav.launcher.voice.VoiceWakeService

/**
 * ═══ W-WAKE — "Hey Kachi" trên cầu Settings (hàm mở rộng của [ClusterNavBridge]) ═════════════════════════════
 *
 * Cùng khuôn `ClusterNavBridgeHome`/`ClusterNavBridgeKeys`: màn Cài đặt KHÔNG ghi `Prefs.set` trực tiếp
 * (`SettingsScreenWiringContractTest` cấm — state trên màn và state bền phải đi qua một cửa). Công tắc "Hey
 * Kachi" đọc/ghi qua đây; ghi xong **đồng bộ luôn FGS** ([VoiceWakeService.sync]) để bật/tắt bộ nghe ngay.
 */
fun ClusterNavBridge.wakeEnabled(): Boolean = Prefs.wakeEnabled(app)

fun ClusterNavBridge.setWakeEnabled(on: Boolean) {
    Prefs.setWakeEnabled(app, on)
    VoiceWakeService.sync(app)
}
