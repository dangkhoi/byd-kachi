package com.byd.clusternav.launcher

import android.app.ActivityManager
import android.content.Context

/** 3.01 R25 — đầu máy có kham nổi widget 3D không (tách khỏi [Car3dWidgetView] vì trần 500 dòng). */
internal object Car3dDevice {
    /** Không phải máy RAM thấp và có GLES ≥ 3.0 (Filament backend GLES 3.0). */
    fun capable(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return !am.isLowRamDevice && am.deviceConfigurationInfo.reqGlEsVersion >= GLES_30
    }

    /** MEDIUM (FXAA) chỉ khi máy ≥ [MEDIUM_RAM_BYTES]; đầu xe mặc định LOW (không khử răng cưa). */
    fun mediumTier(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem >= MEDIUM_RAM_BYTES
    }

    /** Ô [v] (cỡ THẬT, px → dp — chiều ngược của hàm đổi dp, không số trần) đủ cho widget [id] ([Car3dSeat.fits]). */
    fun slotFits(v: android.view.View, id: String): Boolean {
        val d = v.resources.displayMetrics.density
        return Car3dSeat.fits(id, (v.width / d).toInt(), (v.height / d).toInt())
    }

    private const val GLES_30 = 0x30000
    private const val MEDIUM_RAM_BYTES = 8L shl 30
}
