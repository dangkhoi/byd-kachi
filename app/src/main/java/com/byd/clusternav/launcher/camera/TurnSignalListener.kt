package com.byd.clusternav.launcher.camera

import android.content.Context
import android.hardware.bydauto.light.AbsBYDAutoLightListener
import android.util.Log

/**
 * ═══ NGHE XI-NHAN BẰNG SỰ KIỆN (RE kim `VehicleBridge.createLightListener`) ══════════════════════════════════
 *
 * [ĐO xe 2026-09-25] `BYDAutoLightDevice.getLightStatus(4/5)` LUÔN trả 0 dù đang bật xi-nhan (MCU
 * không phơi state qua getter đó). NHƯNG `AbsBYDAutoLightListener.onLightOn(area)` FIRE đúng: area 4=trái, 5=phải.
 * kim dùng chính listener này. ⇒ nghe SỰ KIỆN thay poll getter.
 *
 * `AbsBYDAutoLightListener` là lớp framework ABSTRACT ⇒ không Proxy được. Ta subclass qua **stub compileOnly**
 * (`libs/bydauto-stubs.jar`, KHÔNG vào APK); lúc chạy lớp THẬT của xe được nạp. Đăng ký qua reflection
 * `BYDAutoLightDevice.getInstance(ctx).registerListener(listener)`. Off-car/emulator không có lớp thật ⇒ mọi
 * bước ném và bị runCatching nuốt — không crash.
 */
internal class TurnSignalListener(
    private val appCtx: Context,
    private val onTurn: (left: Boolean, right: Boolean) -> Unit,
) {
    @Volatile private var left = false
    @Volatile private var right = false
    private var device: Any? = null

    private val listener = object : AbsBYDAutoLightListener() {
        override fun onLightOn(area: Int) {
            when (area) { 4 -> left = true; 5 -> right = true; else -> return }
            Log.i(TAG, "onLightOn area=$area (4=trái 5=phải)")
            emit()
        }
        override fun onLightOff(area: Int) {
            when (area) { 4 -> left = false; 5 -> right = false; else -> return }
            Log.i(TAG, "onLightOff area=$area")
            emit()
        }
    }

    private fun emit() = runCatching { onTurn(left, right) }.onFailure { Log.w(TAG, "onTurn lỗi: ${it.message}") }

    /** Đăng ký listener trên BYDAutoLightDevice (reflection). Idempotent-ish: gọi lại chỉ đăng ký thêm — controller giữ 1 instance. */
    fun register(): Boolean = runCatching {
        val cls = Class.forName(DEVICE)
        val dev = cls.getMethod("getInstance", Context::class.java).invoke(null, appCtx)
        cls.getMethod("registerListener", AbsBYDAutoLightListener::class.java).invoke(dev, listener)
        device = dev
        Log.i(TAG, "đăng ký AbsBYDAutoLightListener OK")
        true
    }.getOrElse { Log.w(TAG, "light listener register FAIL: ${it.javaClass.simpleName}: ${it.message}", it); false }

    fun unregister() {
        val dev = device ?: return
        runCatching { Class.forName(DEVICE).getMethod("unregisterListener", AbsBYDAutoLightListener::class.java).invoke(dev, listener) }
        device = null
    }

    private companion object {
        const val TAG = "KachiCamera"
        const val DEVICE = "android.hardware.bydauto.light.BYDAutoLightDevice"
    }
}
