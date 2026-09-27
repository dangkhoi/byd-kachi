package com.byd.clusternav.launcher

import com.byd.clusternav.Prefs
import com.byd.clusternav.cameraChannelFallback
import com.byd.clusternav.launcher.camera.CameraDefaults

/**
 * ═══ Cầu → Cài đặt: hai câu hỏi CHỈ-ĐỌC cho hàng *Nguồn* camera (2.76 · R1/R3) ═══════════════════════════════════
 *
 * Tách khỏi `ClusterNavBridgeAutomation` (tệp ấy là của họ getter/setter prefs; hai hàm này không ghi gì). Màn Cài
 * đặt **không** được chạm `Prefs.`/hồ sơ trực tiếp (`ClusterNavSettingsWiringContractTest`), nên hai sự thật này
 * đi qua cầu như mọi thứ khác của nhóm *Tiện nghi xe* (N2).
 */

/** Hồ sơ xe có bản đồ kênh HAL cho cả hai gương không — không có ⇒ Cài đặt **ẩn** hàng *Nguồn*. */
fun ClusterNavBridge.cameraChannelSupported(): Boolean = CameraDefaults.of(app).hasChannelMap

/** Lý do phiên CHANNEL gần nhất phải lùi về toàn cảnh, rỗng = không lùi (xem `PrefsCameraChannel`). */
fun ClusterNavBridge.cameraChannelFallback(): String = Prefs.cameraChannelFallback(app)
