package com.byd.clusternav.launcher.camera

import android.content.Context
import com.byd.clusternav.modules.clustercast.ClusterProfile

/**
 * ═══ Cửa DUY NHẤT của tầng prefs camera vào mặc định theo HỒ SƠ XE (2.76 · R2) ════════════════════════════════
 *
 * `Prefs.cameraSpan/Render/Rotation` (`PrefsAutomation`) và tám núm nắn (`PrefsCameraDewarp`) đọc
 * `pref ?: CameraDefaults.of(ctx).x` — khoá VẮNG mới lấy của hồ sơ, khoá đã đặt trên xe **luôn thắng**. Một hàm thay
 * cho mười lượt `ClusterProfile.resolve(ctx).camera` rải rác: chỗ nào quên `.sane()` là một mã hồ sơ viết sai đi
 * thẳng vào tầng vẽ.
 *
 * Đi qua [ClusterProfile.resolveCached] (đệm theo tiến trình, xoá khi đổi override) vì hàm này được hỏi vài lần mỗi
 * lượt xi-nhan — `resolve` thô mở prefs + reflection `getprop` mỗi lần.
 */
object CameraDefaults {

    /** Mặc định camera của xe đang chạy, đã kiểm từng trường ([CameraProfileDefaults.sane]). */
    fun of(ctx: Context): CameraProfileDefaults = ClusterProfile.resolveCached(ctx).camera.sane()

    /**
     * Số đo DẢI GIỮA của cụm (hình *theo cụm*, R4) theo hồ sơ xe — [ClusterProfile.band]. Cùng cửa với [of] để
     * `CameraSignalController` không tự `resolve` hồ sơ một chỗ thứ hai (2.76 L7, nợ chéo L2).
     */
    fun band(ctx: Context): ClusterBandSpec = ClusterProfile.resolveCached(ctx).band
}
