package com.byd.clusternav.launcher.camera

import android.util.Log
import android.view.Surface
import java.io.File

/**
 * ═══ AVM CAMERA — lấy hình camera THẬT đổ vào một [Surface] (RE kinex `b1/RunnableC0170d`) ═══════════════════
 *
 * kinex KHÔNG dùng LVDS/`BYDAutoPanoramaDevice` để hiện camera trong app — nó dùng **`android.hardware.AVMCamera`**
 * (nạp từ `/system/framework/bmmcamera.jar`) và đổ preview thẳng vào một `Surface` của chính app:
 * ```
 *   cam = AVMCamera.open(cameraId)   (hoặc new AVMCamera(cameraId))
 *   cam.setCameraFps(15)
 *   cam.addPreviewSurface(surface, mode)   // thử mode 0..3 tới khi được
 *   cam.startPreview()
 *   … cam.stopPreview(); cam.close()
 * ```
 * Đây là lý do LVDS-vào-SurfaceView (cách cũ) ra ĐEN: không có API nào ĐƯA frame vào surface. `addPreviewSurface`
 * mới là đường đó — không cần quyền hệ thống (kinex cũng app thường).
 *
 * Toàn bộ reflection + `runCatching`: off-car / trim không có `AVMCamera` ⇒ mọi hàm no-op, không crash.
 */
internal class AvmCamera(private val classLoaderDex: Boolean = true) {

    private var cam: Any? = null
    private var cls: Class<*>? = null

    /** Nạp lớp AVMCamera (thử addDexPath bmmcamera.jar như kinex nếu classloader thường không thấy). */
    private fun loadClass(): Class<*>? = cls ?: runCatching {
        runCatching { return Class.forName(FQN) }
        if (classLoaderDex) runCatching {
            val cl = javaClass.classLoader
            cl?.javaClass?.getMethod("addDexPath", String::class.java)?.invoke(cl, DEX)
        }
        runCatching { Class.forName(FQN) }.getOrNull()
            ?: ClassLoader.getSystemClassLoader().loadClass(FQN)
    }.getOrNull()?.also { cls = it }

    /** Mở camera [cameraId] + đổ preview vào [surface]. Trả true nếu startPreview OK (một mode nào đó nhận surface). */
    fun open(cameraId: Int, surface: Surface): Boolean {
        val c = loadClass() ?: run { Log.i(TAG, "AVMCamera class không có (off-car/trim khác)"); return false }
        fun m(name: String, vararg types: Class<*>) = runCatching {
            c.getDeclaredMethod(name, *types).apply { isAccessible = true }   // ⚠ kinex setAccessible — method non-public
        }.getOrNull()
        // Dựng: constructor(int) rồi open() (device open) — HOẶC static open(int). setAccessible bắt buộc.
        var obj = runCatching { c.getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }.newInstance(cameraId) }.getOrNull()
        if (obj != null) {
            // constructor(int) xong PHẢI open() device (kinex) — thiếu bước này ⇒ "camera has been not open".
            val opened = runCatching { m("open")?.invoke(obj); true }.getOrDefault(false)
            Log.i(TAG, "AVMCamera(cameraId=$cameraId) + open() device=$opened")
        } else {
            obj = runCatching { m("open", Integer.TYPE)?.invoke(null, cameraId) }.getOrNull()
            Log.i(TAG, "AVMCamera.open($cameraId) static → ${obj != null}")
        }
        if (obj == null) { Log.w(TAG, "không tạo/mở được AVMCamera cameraId=$cameraId"); return false }
        cam = obj
        runCatching { m("setCameraFps", Integer.TYPE)?.invoke(obj, FPS) }   // fps có thể bị từ chối — non-fatal
        // addPreviewSurface(Surface, int mode) — thử mode 0..3 như kinex.
        val add = m("addPreviewSurface", Surface::class.java, Integer.TYPE)
        var surfaceOk = false
        if (add != null) {
            for (mode in 0..3) {
                if (runCatching { add.invoke(obj, surface, mode); true }.getOrDefault(false)) {
                    Log.i(TAG, "addPreviewSurface ok cameraId=$cameraId mode=$mode"); surfaceOk = true; break
                }
            }
        }
        if (!surfaceOk) {
            runCatching { m("addPreviewSurface", Surface::class.java)?.invoke(obj, surface); surfaceOk = true }
        }
        val started = runCatching { m("startPreview")?.invoke(obj); true }.getOrDefault(false)
        Log.i(TAG, "AVMCamera cameraId=$cameraId surfaceOk=$surfaceOk started=$started")
        return started
    }

    fun close() {
        val obj = cam ?: return
        val c = cls
        runCatching { c?.getDeclaredMethod("stopPreview")?.apply { isAccessible = true }?.invoke(obj) }
        runCatching { c?.getDeclaredMethod("close")?.apply { isAccessible = true }?.invoke(obj) }
        cam = null
    }

    companion object {
        private const val TAG = "KachiCamera"
        private const val FQN = "android.hardware.AVMCamera"
        private const val DEX = "/system/framework/bmmcamera.jar"
        private const val FPS = 15
    }
}
