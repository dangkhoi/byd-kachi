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

    /**
     * Cỡ ảnh preview THẬT mà HAL đang đổ ra, `intArrayOf(w, h)` — hoặc `null` khi không hỏi được.
     *
     * [ĐO RE] hai hàm này CÓ THẬT trong lớp framework: firmware DiLink5.1 `com/byd/dilink51_main/hardware/camera/
     * DiLinkAVMCamera.java` bọc thẳng `AVMCamera.getPreviewWidth()` / `getPreviewHeight()`. [CHƯA BIẾT] trim
     * DiLink3.0 trên xe owner có trả số đúng hay trả 0 — vì vậy `null`/`≤ 0` là một câu trả lời **bình thường**, và
     * chỗ gọi ([CameraOverlayView.onStreamMeasured]) phải sống được với nó (giữ gợi ý đang dùng).
     *
     * Dùng cho CAM-ROT-2: cửa sổ overlay lấy đúng tỉ lệ vùng crop sau xoay ⇒ cần cỡ ảnh nguồn, và cỡ ấy phải **đo**
     * chứ không hardcode (xe khác ghép ảnh 4-in-1 cỡ khác — CLAUDE.md §7).
     */
    fun previewSize(): IntArray? {
        val obj = cam ?: return null
        val c = cls ?: return null
        fun read(name: String): Int? = runCatching {
            c.getDeclaredMethod(name).apply { isAccessible = true }.invoke(obj) as? Int
        }.getOrNull()
        val w = read("getPreviewWidth") ?: return null
        val h = read("getPreviewHeight") ?: return null
        if (w <= 0 || h <= 0) { Log.i(TAG, "previewSize HAL trả ${w}x$h ⇒ coi như chưa biết"); return null }
        Log.i(TAG, "previewSize HAL = ${w}x$h")
        return intArrayOf(w, h)
    }

    /**
     * Nhờ HAL xoay hộ khung hình đổ vào [surface] — đường xoay DUY NHẤT còn lại khi kết xuất bằng `SurfaceView`
     * (không có `setTransform`). Trả `true` khi lời gọi được NHẬN (≠ đã có tác dụng).
     *
     * [ĐO RE] `AVMCamera.setDisplayOrientation(Surface, int)` có trong lớp framework (cùng nguồn [previewSize]).
     * ⚠ [CHƯA BIẾT] ROM này có thi hành hay không — và "nhận" ở đây chỉ có nghĩa *reflection không ném và không trả
     * `false`*. Vì vậy chỗ gọi phải báo kết quả lên tầng vẽ để cửa sổ lấy tỉ lệ ĐÚNG với thứ thật sự xảy ra, thay
     * vì giả định đã xoay (CLAUDE.md §2: cơ chế ≠ quy kết).
     */
    fun setDisplayOrientation(surface: Surface, deg: Int): Boolean {
        val obj = cam ?: return false
        val c = cls ?: return false
        val norm = ((deg % 360) + 360) % 360
        val ok = runCatching {
            val m = c.getDeclaredMethod("setDisplayOrientation", Surface::class.java, Integer.TYPE).apply { isAccessible = true }
            m.invoke(obj, surface, norm) as? Boolean ?: true
        }.getOrDefault(false)
        Log.i(TAG, "setDisplayOrientation($norm) nhận=$ok")
        return ok
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
