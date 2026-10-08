package com.byd.clusternav.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display

/**
 * 2.98 · R6-C — đọc "màn xe đang SÁNG?" ([ScreenLitRule]) và móc "màn vừa đổi" để vòng đọc HAL thức NGAY khi màn sáng lại.
 * Hai nguồn: `isInteractive` ([ScreenInteractive]) + `Display.getState()` của display 0 (STATE_OFF ⇒ tắt). Không hỏi được ⇒ SÁNG.
 */
object ScreenLit {

    fun read(ctx: Context): Boolean = ScreenLitRule.lit(ScreenInteractive.read(ctx), displayOn(ctx))

    private fun displayOn(ctx: Context): Boolean? = try {
        ctx.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.state?.let { it != Display.STATE_OFF }
    } catch (e: RuntimeException) {
        null
    }

    /**
     * Gọi [onChange] khi màn có thể vừa sáng lại: `ACTION_SCREEN_ON` (đổi tương tác) + đổi trạng thái display 0 (đèn nền/tắt màn
     * kiểu BYD). Trả hàm gỡ — gọi được nhiều lần. Đăng ký hỏng ⇒ trả hàm rỗng (vòng đọc vẫn tự xét lại sau hạn ngủ).
     */
    fun watch(ctx: Context, onChange: () -> Unit): () -> Unit {
        val app = ctx.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onChange()
        }
        // SCREEN_ON là broadcast hệ thống được bảo vệ ⇒ không cần cờ RECEIVER_* kể cả targetSdk 34+ (soát Pass 4, developer.android.com).
        val rOk = runCatching { app.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_ON)) }.isSuccess
        val dm = app.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) { if (displayId == Display.DEFAULT_DISPLAY) onChange() }
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
        }
        val dOk = dm != null && runCatching { dm.registerDisplayListener(listener, Handler(Looper.getMainLooper())) }.isSuccess
        return {
            if (rOk) runCatching { app.unregisterReceiver(receiver) }
            if (dOk) runCatching { dm?.unregisterDisplayListener(listener) }
        }
    }
}
