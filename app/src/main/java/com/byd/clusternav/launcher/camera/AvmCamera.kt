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
        // Dựng: thử constructor(int) rồi static open(int).
        val obj = runCatching { c.getDeclaredConstructor(Int::class.javaPrimitiveType).newInstance(cameraId) }
            .getOrNull()
            ?: runCatching { c.getDeclaredMethod("open", Int::class.javaPrimitiveType).invoke(null, cameraId) }
                .getOrNull()
        if (obj == null) { Log.w(TAG, "không tạo được AVMCamera cameraId=$cameraId"); return false }
        cam = obj
        runCatching { c.getDeclaredMethod("open").invoke(obj) }   // constructor(int) có thể cần open() rời
        runCatching { c.getDeclaredMethod("setCameraFps", Int::class.javaPrimitiveType).invoke(obj, FPS) }
        // addPreviewSurface(Surface, int mode) — thử mode 0..3 như kinex.
        val add = runCatching { c.getDeclaredMethod("addPreviewSurface", Surface::class.java, Int::class.javaPrimitiveType) }.getOrNull()
        var surfaceOk = false
        if (add != null) {
            for (mode in 0..3) {
                if (runCatching { add.invoke(obj, surface, mode); true }.getOrDefault(false)) {
                    Log.i(TAG, "addPreviewSurface ok cameraId=$cameraId mode=$mode"); surfaceOk = true; break
                }
            }
        }
        if (!surfaceOk) {
            // Vài build chỉ có addPreviewSurface(Surface) không mode.
            runCatching { c.getDeclaredMethod("addPreviewSurface", Surface::class.java).invoke(obj, surface); surfaceOk = true }
        }
        val started = runCatching { c.getDeclaredMethod("startPreview").invoke(obj); true }.getOrDefault(false)
        Log.i(TAG, "AVMCamera cameraId=$cameraId surfaceOk=$surfaceOk started=$started")
        return started
    }

    fun close() {
        val obj = cam ?: return
        val c = cls
        runCatching { c?.getDeclaredMethod("stopPreview")?.invoke(obj) }
        runCatching { c?.getDeclaredMethod("close")?.invoke(obj) }
        cam = null
    }

    companion object {
        private const val TAG = "KachiCamera"
        private const val FQN = "android.hardware.AVMCamera"
        private const val DEX = "/system/framework/bmmcamera.jar"
        private const val FPS = 15
    }
}
